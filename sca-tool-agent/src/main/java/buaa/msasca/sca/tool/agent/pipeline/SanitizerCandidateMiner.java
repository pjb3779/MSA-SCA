package buaa.msasca.sca.tool.agent.pipeline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import buaa.msasca.sca.core.port.out.persistence.CodeqlFindingPort;

/**
 * Sanitizer 후보 수집기 (Miner).
 *
 * <p><b>역할:</b> "sanitizer일 가능성이 있는 메서드" 목록을 수집한다. LLM 호출 없음.</p>
 *
 * <p><b>Phase 1 - 패턴 스캔:</b></p>
 * <ul>
 *   <li>소스 루트의 모든 .java 파일을 순회</li>
 *   <li>메서드명에 sanitize, escape, validate, normalize, filter, clean, check, guard 포함 시 후보</li>
 *   <li>또는 @valid, @pattern 어노테이션 있는 파일 내 메서드 후보</li>
 *   <li>중복 제거: filePath::methodName 기준 Map으로 관리</li>
 * </ul>
 *
 * <p><b>Phase 2 - CodeQL SARIF 보강:</b></p>
 * <ul>
 *   <li>각 finding의 flow step (file, line)에서 해당 라인 메서드 추출</li>
 *   <li>primary location에서도 HINTS 포함 메서드면 추출</li>
 *   <li>기존 후보와 겹치면 codeql/codeql-flow 신호만 병합, 없으면 새로 추가</li>
 * </ul>
 *
 * <p>CodeQL finding이 없으면(예: MSCAN_ONLY) Phase 1만 수행한다.</p>
 */
public class SanitizerCandidateMiner {

    /** Java 메서드 시그니처 파싱용 정규식 */
    private static final Pattern METHOD_SIG_PATTERN = Pattern.compile(
        "(public|protected|private)?\\s*(static\\s+)?[\\w<>\\[\\],.?\\s]+\\s+(\\w+)\\s*\\(([^)]*)\\)"
    );

    /** 메서드명에 포함되면 sanitizer 후보로 간주하는 키워드 */
    private static final List<String> HINTS = List.of(
        "sanitize", "escape", "validate", "normalize", "filter", "clean", "check", "guard"
    );

    /** flow step label에 포함되면 codeql-flow 신호 추가 (Judge 신뢰도 가산) */
    private static final List<String> FLOW_LABEL_HINTS = List.of(
        "sanitize", "escape", "validate", "filter", "clean"
    );

    /**
     * 소스 루트에서 sanitizer 후보를 수집한다.
     *
     * @param sourceRoot 소스 루트 경로
     * @param codeqlFindings CodeQL finding 목록 (null/empty 시 패턴만 사용)
     * @return 수집된 sanitizer 후보 목록 (패턴 + SARIF 기반)
     */
    public List<SanitizerCandidate> mine(
        Path sourceRoot,
        List<CodeqlFindingPort.CodeqlFindingView> codeqlFindings
    ) {
        // (filePath, methodName) -> candidate (중복 시 CodeQL 맥락으로 보강)
        Map<String, SanitizerCandidate> byKey = new java.util.LinkedHashMap<>();

        // Phase 1: 패턴 기반 마이닝
        mineByPattern(sourceRoot, byKey);

        // Phase 2: SARIF 기반 보강
        if (codeqlFindings != null && !codeqlFindings.isEmpty()) {
            mineFromCodeql(sourceRoot, codeqlFindings, byKey);
        }

        return new ArrayList<>(byKey.values());
    }

    /** CodeQL 없이 패턴만으로 후보 수집 (기존 호환용). */
    public List<SanitizerCandidate> mine(Path sourceRoot) {
        return mine(sourceRoot, List.of());
    }

    private void mineByPattern(Path sourceRoot, Map<String, SanitizerCandidate> byKey) {
        try (Stream<Path> s = Files.walk(sourceRoot)) {
            s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        String txt = Files.readString(p, StandardCharsets.UTF_8);
                        String lower = txt.toLowerCase(Locale.ROOT);
                        String relPath = sourceRoot.relativize(p).toString().replace("\\", "/");
                        Matcher m = METHOD_SIG_PATTERN.matcher(txt);
                        while (m.find()) {
                            String methodName = m.group(3);
                            if (methodName == null) continue;
                            String methodLower = methodName.toLowerCase(Locale.ROOT);
                            Set<String> signals = new LinkedHashSet<>();
                            if (HINTS.stream().anyMatch(methodLower::contains)) {
                                signals.add("name");
                            }
                            if (lower.contains("@valid") || lower.contains("@pattern")) {
                                signals.add("annotation");
                            }
                            if (signals.isEmpty()) continue;

                            String key = keyOf(relPath, methodName);
                            byKey.putIfAbsent(key, SanitizerCandidate.ofPattern(
                                methodName, relPath, signals));
                        }
                    } catch (Exception ignored) {
                        // 파일 단위 스킵
                    }
                });
        } catch (Exception ignored) {
            // 스캔 실패 시 빈 결과
        }
    }

    private void mineFromCodeql(
        Path sourceRoot,
        List<CodeqlFindingPort.CodeqlFindingView> findings,
        Map<String, SanitizerCandidate> byKey
    ) {
        for (CodeqlFindingPort.CodeqlFindingView f : findings) {
            String moduleRoot = (f.serviceModuleRootPath() != null)
                ? f.serviceModuleRootPath().replace("\\", "/").strip()
                : "";
            if (!moduleRoot.isEmpty() && !moduleRoot.endsWith("/")) {
                moduleRoot = moduleRoot + "/";
            }

            // Flow step에서 (file, line) 추출 후 해당 라인의 메서드 탐색
            if (f.flowSteps() != null) {
                for (CodeqlFindingPort.FlowStepView step : f.flowSteps()) {
                    if (step.filePath() == null || step.filePath().isBlank()) continue;
                    String normPath = resolvePath(moduleRoot, step.filePath());
                    Path fullPath = sourceRoot.resolve(normPath);
                    if (!Files.exists(fullPath) || !Files.isRegularFile(fullPath)) continue;
                    String methodName = extractMethodAtLine(fullPath, step.lineNumber());
                    if (methodName == null) continue;
                    String relPath = sourceRoot.relativize(fullPath).toString().replace("\\", "/");
                    addOrMergeCodeql(byKey, relPath, methodName, f, step);
                }
            }

            // Primary location에서도 후보 추출
            if (f.primaryFile() != null && !f.primaryFile().isBlank()) {
                String normPath = resolvePath(moduleRoot, f.primaryFile());
                Path fullPath = sourceRoot.resolve(normPath);
                if (Files.exists(fullPath) && Files.isRegularFile(fullPath)) {
                    String methodName = extractMethodAtLine(fullPath, f.primaryLine());
                    if (methodName != null && HINTS.stream()
                        .anyMatch(h -> methodName.toLowerCase(Locale.ROOT).contains(h))) {
                        String relPath = sourceRoot.relativize(fullPath).toString().replace("\\", "/");
                        addOrMergeCodeql(byKey, relPath, methodName, f, null);
                    }
                }
            }
        }
    }

    private String resolvePath(String moduleRoot, String fileFromSarif) {
        String p = fileFromSarif.replace("\\", "/").strip();
        if (p.startsWith("/")) p = p.substring(1);
        return moduleRoot.isEmpty() ? p : moduleRoot + p;
    }

    /**
     * 기존 후보와 동일 key면 CodeQL 맥락으로 병합, 없으면 새로 추가한다.
     */
    private void addOrMergeCodeql(
        Map<String, SanitizerCandidate> byKey,
        String relPath, String methodName,
        CodeqlFindingPort.CodeqlFindingView f,
        CodeqlFindingPort.FlowStepView step
    ) {
        List<String> labels = (f.flowSteps() != null)
            ? f.flowSteps().stream()
                .map(CodeqlFindingPort.FlowStepView::label)
                .filter(l -> l != null && !l.isBlank())
                .limit(5)
                .toList()
            : List.<String>of();
        Set<String> signals = new LinkedHashSet<>();
        signals.add("codeql");
        if (step != null && step.label() != null
            && FLOW_LABEL_HINTS.stream().anyMatch(h -> step.label().toLowerCase(Locale.ROOT).contains(h))) {
            signals.add("codeql-flow");
        }
        String key = keyOf(relPath, methodName);
        SanitizerCandidate existing = byKey.get(key);
        if (existing != null) {
            Set<String> merged = new LinkedHashSet<>(existing.signals());
            merged.addAll(signals);
            byKey.put(key, SanitizerCandidate.ofCodeql(
                methodName, relPath, merged,
                f.findingId(), f.ruleId(), f.message(), labels));
        } else {
            byKey.put(key, SanitizerCandidate.ofCodeql(
                methodName, relPath, signals,
                f.findingId(), f.ruleId(), f.message(), labels));
        }
    }

    /**
     * 지정 라인을 포함하는 메서드의 이름을 반환한다.
     * 해당 라인 이전에 선언된 마지막 메서드를 기준으로 한다.
     */
    private String extractMethodAtLine(Path file, Integer lineNumber) {
        if (lineNumber == null || lineNumber < 1) return null;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lineNumber > lines.size()) return null;
            String lastMethod = null;
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = METHOD_SIG_PATTERN.matcher(lines.get(i));
                if (m.find()) {
                    lastMethod = m.group(3);
                }
                if (i + 1 == lineNumber) {
                    return lastMethod;
                }
            }
            return lastMethod;
        } catch (Exception e) {
            return null;
        }
    }

    private String keyOf(String filePath, String methodName) {
        return filePath + "::" + methodName;
    }
}
