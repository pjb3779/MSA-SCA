package buaa.msasca.sca.tool.agent.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import buaa.msasca.sca.core.domain.model.ServiceModule;

/**
 * 1단계 경량 모듈 선별기.
 *
 * 목적:
 * - 빌드/분석 전에 명백히 불필요한 모듈을 약한 규칙으로 1차 제외한다.
 *
 * 원칙:
 * - 오탐으로 중요한 모듈을 버리지 않도록 보수적으로 동작한다.
 * - gateway 모듈은 항상 유지한다.
 */
public class ModulePrefilter {

    public record PrefilterDecision(
        ServiceModule module,
        boolean selected,
        String reason
    ) {}

    /**
     * 1단계: 이름/경로 기반 무조건 제외 패턴.
     * 사용자 요청 패턴을 그대로 반영한다.
     */
    private static final List<Pattern> HARD_EXCLUDE_PATTERNS = List.of(
        Pattern.compile(".*test.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*tests.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*test-fixture.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*fixture.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*doc.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*docs.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*javadoc.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*source.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*sources.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*example.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*demo.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*sample.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*benchmark.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*coverage.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*report.*", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 빌드 보조/의존성 집계 모듈 제외 패턴.
     * - mscan 분석 가치가 낮고 빌드 부하만 키우는 모듈들을 우선 제거한다.
     */
    private static final List<Pattern> SUPPORT_MODULE_EXCLUDE_PATTERNS = List.of(
        Pattern.compile(".*add-deps.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*build-tools.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*dependencies-parent.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*-dependencies.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*-bom.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*-parent.*", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 2단계: 남은 모듈에서 분석 신호를 탐지한다.
     * - endpoint annotation
     * - HTTP client / MQ / gRPC API
     * - sink API
     * - gateway route target
     */
    private static final List<String> SIGNAL_PATTERNS = List.of(
        // endpoint annotation
        "@requestmapping", "@getmapping", "@postmapping", "@putmapping", "@deletemapping", "@patchmapping",
        "@path", "@restcontroller", "@controller", "@kafkalistener", "@rabbitlistener", "@jmslistener",
        // HTTP client / MQ / gRPC API
        "@feignclient", "resttemplate.", "webclient.", "java.net.http.httpclient", "okhttpclient",
        "kafkatemplate", "kafkaproducer", "kafkaconsumer", "rabbittemplate", "basicpublish", "basicconsume",
        "managedchannel", "grpc", "blockingstub", "futurestub",
        // sink API
        "runtime.getruntime().exec", "new processbuilder(", "statement.executequery(", "statement.executeupdate(",
        "preparedstatement", "groovyshell.evaluate(", "spel", "scriptenginemanager", "files.write(",
        // gateway route target
        "spring.cloud.gateway", "routes:", "predicates:", "path=", "uri:", "lb://"
    );

    private static final int MAX_SCAN_FILES = 300;
    private static final int MAX_SCAN_FILE_BYTES = 1_000_000; // 1MB
    private static final List<String> SCAN_EXTS = List.of(
        ".java", ".kt", ".groovy", ".xml", ".yml", ".yaml", ".properties", ".gradle", ".kts"
    );

    private static final List<String> SERVER_HINT_TOKENS = List.of(
        "server", "servers"
    );

    public List<ServiceModule> filter(List<ServiceModule> modules, String sourcePath) {
        List<ServiceModule> kept = new ArrayList<>();
        for (ServiceModule m : modules) {
            // gateway는 후속 단계(MScan entry 해석)에 중요하므로 항상 유지
            if (m.isGateway()) {
                kept.add(m);
                continue;
            }
            String joined = (safeLower(m.name()) + " " + safeLower(m.rootPath())).trim();
            String hardPattern = firstHardExcludePattern(joined);
            if (hardPattern != null) {
                continue;
            }
            String supportPattern = firstSupportExcludePattern(joined);
            if (supportPattern != null) {
                continue;
            }
            if (hasServerHint(joined)) {
                kept.add(m);
                continue;
            }
            ModuleSignalHit hit = findAnySignal(m, sourcePath);
            if (hit.hit()) {
                kept.add(m);
            }
        }
        return kept;
    }

    public List<PrefilterDecision> decide(List<ServiceModule> modules, String sourcePath) {
        List<PrefilterDecision> out = new ArrayList<>();
        for (ServiceModule m : modules) {
            if (m.isGateway()) {
                out.add(new PrefilterDecision(m, true, "FORCED_GATEWAY"));
                continue;
            }
            String joined = (safeLower(m.name()) + " " + safeLower(m.rootPath())).trim();
            String hardPattern = firstHardExcludePattern(joined);
            if (hardPattern != null) {
                out.add(new PrefilterDecision(m, false, "HARD_EXCLUDE_PATTERN:" + hardPattern));
                continue;
            }
            String supportPattern = firstSupportExcludePattern(joined);
            if (supportPattern != null) {
                out.add(new PrefilterDecision(m, false, "HARD_EXCLUDE_SUPPORT_MODULE:" + supportPattern));
                continue;
            }
            if (hasServerHint(joined)) {
                out.add(new PrefilterDecision(m, true, "INCLUDED_BY_SERVER_HINT"));
                continue;
            }
            ModuleSignalHit hit = findAnySignal(m, sourcePath);
            if (!hit.hit()) {
                out.add(new PrefilterDecision(m, false, "NO_SIGNAL_AFTER_SCAN"));
            } else {
                out.add(new PrefilterDecision(m, true, "INCLUDED_BY_SIGNAL:" + hit.signal()));
            }
        }
        return out;
    }

    private String safeLower(String s) {
        return (s == null) ? "" : s.toLowerCase(Locale.ROOT);
    }

    private String firstHardExcludePattern(String text) {
        if (text == null) return null;
        for (Pattern p : HARD_EXCLUDE_PATTERNS) {
            if (p.matcher(text).matches()) {
                return p.pattern();
            }
        }
        return null;
    }

    private String firstSupportExcludePattern(String text) {
        if (text == null) return null;
        for (Pattern p : SUPPORT_MODULE_EXCLUDE_PATTERNS) {
            if (p.matcher(text).matches()) {
                return p.pattern();
            }
        }
        return null;
    }

    private boolean hasServerHint(String text) {
        if (text == null || text.isBlank()) return false;
        for (String t : SERVER_HINT_TOKENS) {
            if (text.contains(t)) return true;
        }
        return false;
    }

    private ModuleSignalHit findAnySignal(ServiceModule module, String sourcePath) {
        Path modulePath = resolveModulePath(module, sourcePath);
        if (modulePath == null || !Files.exists(modulePath)) {
            return new ModuleSignalHit(false, "MISSING_MODULE_PATH");
        }

        try (Stream<Path> s = Files.walk(modulePath, 6)) {
            int[] scanned = new int[] {0};
            return s.filter(Files::isRegularFile)
                .filter(this::isScannableFile)
                .limit(MAX_SCAN_FILES)
                .map(p -> {
                    scanned[0]++;
                    return scanFileForSignal(p);
                })
                .filter(ModuleSignalHit::hit)
                .findFirst()
                .orElse(new ModuleSignalHit(false, "NO_SIGNAL"));
        } catch (Exception e) {
            // 스캔 실패 시 보수적으로 포함(신호 있다고 간주)하지 않고, 규칙대로 제외한다.
            return new ModuleSignalHit(false, "SCAN_ERROR");
        }
    }

    private Path resolveModulePath(ServiceModule module, String sourcePath) {
        try {
            Path root = (sourcePath == null || sourcePath.isBlank()) ? null : Path.of(sourcePath);
            String rel = module.rootPath();
            if (rel == null || rel.isBlank()) {
                return root;
            }
            Path relPath = Path.of(rel);
            if (relPath.isAbsolute()) {
                return relPath;
            }
            return (root == null) ? relPath : root.resolve(relPath).normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isScannableFile(Path p) {
        String n = safeLower(p.getFileName() == null ? "" : p.getFileName().toString());
        for (String ext : SCAN_EXTS) {
            if (n.endsWith(ext)) return true;
        }
        return false;
    }

    private ModuleSignalHit scanFileForSignal(Path p) {
        try {
            if (Files.size(p) > MAX_SCAN_FILE_BYTES) {
                return new ModuleSignalHit(false, "SKIP_LARGE_FILE");
            }
            String txt = safeLower(Files.readString(p, StandardCharsets.UTF_8));
            for (String sig : SIGNAL_PATTERNS) {
                if (txt.contains(sig)) {
                    return new ModuleSignalHit(true, sig);
                }
            }
            return new ModuleSignalHit(false, "NO_SIGNAL");
        } catch (IOException e) {
            return new ModuleSignalHit(false, "READ_ERROR");
        }
    }

    private record ModuleSignalHit(boolean hit, String signal) {}
}

