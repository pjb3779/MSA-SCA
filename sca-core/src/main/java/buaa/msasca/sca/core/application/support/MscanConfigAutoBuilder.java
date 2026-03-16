package buaa.msasca.sca.core.application.support;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * ZIP/GIT 업로드 후, sourceRootPath 기반으로 mscan config_json을 자동 생성한다.
 * - src/main/java 하위 *.java의 "package ..."를 스캔해서 classpathKeywords 추정
 * - 멀티모듈이면 하위에서 첫 번째 "src/main/java"를 찾아서 스캔
 */
public class MscanConfigAutoBuilder {

    private static final ObjectMapper om = new ObjectMapper();

    private static final Pattern PACKAGE_PATTERN =
        Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_\\.]+)\\s*;", Pattern.MULTILINE);

    private MscanConfigAutoBuilder() {}

    public static ObjectNode buildDefaultConfig(Long projectVersionId, String sourceRootPath) {
        ObjectNode root = om.createObjectNode();
        ObjectNode mscan = root.putObject("mscan");

        mscan.put("name", "pv-" + projectVersionId);

        String keywords = detectClasspathKeywords(Path.of(sourceRootPath))
            .orElseThrow(() -> new IllegalStateException(
                "Failed to auto-detect mscan.classpathKeywords. " +
                "Please run the helper API (pattern A) to set mscan.classpathKeywords manually."
            ));

        mscan.put("classpathKeywords", keywords);

        // 16GB 기본값
        mscan.put("jvmArgs", "-Xmx2g -XX:MaxMetaspaceSize=512m");
        mscan.put("reuse", false);

        return root;
    }

    private static Optional<String> detectClasspathKeywords(Path sourceRoot) {
        Path javaRoot = sourceRoot.resolve("src").resolve("main").resolve("java");
        if (!Files.isDirectory(javaRoot)) {
        Optional<Path> found = findFirstJavaRootUnder(sourceRoot);
        if (found.isEmpty()) return Optional.empty();
        javaRoot = found.get();
        }

        Map<String, Integer> top2Count = new HashMap<>();
        Map<String, Integer> fullCount = new HashMap<>();

        int maxFiles = 400; // 너무 많이 읽지 않도록 제한
        int[] seen = new int[] {0};

        try (Stream<Path> s = Files.walk(javaRoot, 12)) {
        s.filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().endsWith(".java"))
        .takeWhile(p -> seen[0]++ < maxFiles)
        .forEach(p -> extractPackage(p).ifPresent(pkg -> {
            String full = ensureDotSuffix(pkg);
            fullCount.merge(full, 1, Integer::sum);

            String top2 = top2Prefix(pkg).map(MscanConfigAutoBuilder::ensureDotSuffix).orElse(null);
            if (top2 != null) top2Count.merge(top2, 1, Integer::sum);
        }));
        } catch (Exception e) {
        return Optional.empty();
        }

        if (fullCount.isEmpty()) return Optional.empty();

        String bestFull = fullCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        String bestTop2 = top2Count.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(bestFull);

        if (bestFull == null || bestTop2 == null) return Optional.empty();

        // CSV로 2개 제공(중복이면 1개만)
        if (bestFull.equals(bestTop2)) return Optional.of(bestTop2);
        return Optional.of(bestTop2 + "," + bestFull);
    }

    private static Optional<Path> findFirstJavaRootUnder(Path sourceRoot) {
        try (Stream<Path> s = Files.walk(sourceRoot, 6)) {
        return s.filter(Files::isDirectory)
            .filter(p -> p.endsWith(Path.of("src", "main", "java")))
            .findFirst();
        } catch (Exception e) {
        return Optional.empty();
        }
    }

    private static Optional<String> extractPackage(Path javaFile) {
        try {
        // 비용 줄이기: 파일 전체 읽기(간단). 필요하면 head만 읽도록 최적화 가능.
        String text = Files.readString(javaFile, StandardCharsets.UTF_8);
        Matcher m = PACKAGE_PATTERN.matcher(text);
        if (m.find()) return Optional.of(m.group(1));
        return Optional.empty();
        } catch (Exception e) {
        return Optional.empty();
        }
    }

    private static Optional<String> top2Prefix(String pkg) {
        if (pkg == null) return Optional.empty();
        String[] parts = pkg.split("\\.");
        if (parts.length >= 2) return Optional.of(parts[0] + "." + parts[1]);
        if (parts.length == 1) return Optional.of(parts[0]);
        return Optional.empty();
    }

    private static String ensureDotSuffix(String s) {
        if (s == null) return "";
        return s.endsWith(".") ? s : (s + ".");
    }
}