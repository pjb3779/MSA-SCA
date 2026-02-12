package buaa.msasca.sca.app.worker.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import buaa.msasca.sca.core.domain.enums.BuildTool;
import buaa.msasca.sca.core.port.out.tool.ServiceModuleScannerPort;

public class FileSystemServiceModuleScannerAdapter implements ServiceModuleScannerPort {

    /**
     * 소스 루트 하위에서 Maven/Gradle 모듈을 스캔한다.
     *
     * @param sourceRootPath 소스 루트 경로
     * @param maxDepth 탐색 깊이
     * @return 탐지된 모듈 목록
     */
    @Override
    public List<DetectedServiceModule> scan(String sourceRootPath, int maxDepth) {
        Path sourceRoot = Path.of(sourceRootPath);
        if (!Files.isDirectory(sourceRoot)) {
        throw new IllegalStateException("sourceRoot is not a directory: " + sourceRoot);
        }

        List<Path> buildFiles = findBuildFiles(sourceRoot, maxDepth);

        // build file이 존재하는 디렉토리를 모듈 루트 후보로 본다.
        Set<Path> moduleDirs = new HashSet<>();
        for (Path f : buildFiles) {
        Path dir = f.getParent();
        if (dir == null) continue;
        if (isSkippableDir(sourceRoot, dir)) continue;
        moduleDirs.add(dir);
        }

        // 상대경로 depth 짧은 것부터 처리(루트 가까운 모듈 우선)
        List<Path> sorted = moduleDirs.stream()
            .sorted(Comparator.comparingInt(p -> sourceRoot.relativize(p).getNameCount()))
            .toList();

        List<DetectedServiceModule> detected = new ArrayList<>();
        for (Path moduleDir : sorted) {
        DetectedServiceModule one = detectOne(sourceRoot, moduleDir);
        if (one != null) detected.add(one);
        }

        // rootPath 기준 중복 제거
        Map<String, DetectedServiceModule> uniq = new LinkedHashMap<>();
        for (DetectedServiceModule m : detected) {
        uniq.putIfAbsent(m.rootPath(), m);
        }
        return uniq.values().stream().toList();
    }

    /**
     * build 파일(pom.xml, build.gradle, build.gradle.kts)을 찾는다.
     *
     * @param sourceRoot 소스 루트
     * @param maxDepth 최대 깊이
     * @return build 파일 리스트
     */
    private List<Path> findBuildFiles(Path sourceRoot, int maxDepth) {
        try (Stream<Path> s = Files.find(
            sourceRoot,
            maxDepth,
            (p, attr) -> attr.isRegularFile() && isBuildFile(p.getFileName().toString())
        )) {
        return s.toList();
        } catch (Exception e) {
        throw new IllegalStateException("Failed to scan build files: " + e.getMessage(), e);
        }
    }

    /**
     * 단일 모듈 디렉토리에서 build tool/jdk/gateway 힌트를 추출한다.
     *
     * @param sourceRoot 소스 루트
     * @param moduleDir 모듈 디렉토리
     * @return 탐지 결과(해당 없으면 null)
     */
    private DetectedServiceModule detectOne(Path sourceRoot, Path moduleDir) {
        Path pom = moduleDir.resolve("pom.xml");
        Path gradle = moduleDir.resolve("build.gradle");
        Path gradleKts = moduleDir.resolve("build.gradle.kts");

        boolean hasPom = Files.isRegularFile(pom);
        boolean hasGradle = Files.isRegularFile(gradle) || Files.isRegularFile(gradleKts);

        if (!hasPom && !hasGradle) return null;

        BuildTool tool = hasGradle ? BuildTool.GRADLE : BuildTool.MAVEN;

        String relPath = normalizeRelPath(sourceRoot.relativize(moduleDir));
        String name = relPath.isBlank() ? "root" : relPath;

        String jdk = hasPom ? tryParseJdkFromPom(pom) : tryParseJdkFromGradle(moduleDir);
        boolean gateway = hasPom ? hasGatewayDependencyInPom(pom) : hasGatewayHintInGradle(moduleDir);

        return new DetectedServiceModule(name, relPath, tool, jdk, gateway);
    }

    /**
     * build 파일인지 판별한다.
     *
     * @param filename 파일명
     * @return build 파일이면 true
     */
    private boolean isBuildFile(String filename) {
        return "pom.xml".equals(filename)
            || "build.gradle".equals(filename)
            || "build.gradle.kts".equals(filename);
    }

    /**
     * 스캔에서 제외할 디렉토리인지 판별한다.
     *
     * @param sourceRoot 소스 루트
     * @param dir 후보 디렉토리
     * @return 제외면 true
     */
    private boolean isSkippableDir(Path sourceRoot, Path dir) {
        Path rel = sourceRoot.relativize(dir);
        for (Path p : rel) {
        String n = p.getFileName().toString();
        if (n.equals(".git")
            || n.equals("node_modules")
            || n.equals("target")
            || n.equals("build")
            || n.equals(".gradle")
            || n.equals(".idea")) {
            return true;
        }
        }
        return false;
    }

    /**
     * 상대 경로를 문자열(Unix 스타일)로 정규화한다.
     *
     * @param rel 상대 경로
     * @return 정규화된 문자열
     */
    private String normalizeRelPath(Path rel) {
        if (rel == null) return "";
        String s = rel.toString().replace('\\', '/');
        if (s.startsWith("/")) s = s.substring(1);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /**
     * pom.xml에서 JDK 버전 힌트를 파싱한다.
     * - maven.compiler.release
     * - maven.compiler.source
     * - java.version
     *
     * @param pom pom.xml 경로
     * @return 버전 문자열 또는 null
     */
    private String tryParseJdkFromPom(Path pom) {
        try {
        String xml = Files.readString(pom);
        String v = firstMatch(xml,
            "<maven\\.compiler\\.release>([^<]+)</maven\\.compiler\\.release>",
            "<maven\\.compiler\\.source>([^<]+)</maven\\.compiler\\.source>",
            "<java\\.version>([^<]+)</java\\.version>"
        );
        return (v == null) ? null : v.trim();
        } catch (Exception e) {
        return null;
        }
    }

    /**
     * Gradle에서 JDK 버전 힌트를 파싱한다.
     * - JavaLanguageVersion.of(x)
     * - sourceCompatibility = '17'
     * - JavaVersion.VERSION_17
     *
     * @param moduleDir 모듈 디렉토리
     * @return 버전 문자열 또는 null
     */
    private String tryParseJdkFromGradle(Path moduleDir) {
        try {
        Path g1 = moduleDir.resolve("build.gradle");
        Path g2 = moduleDir.resolve("build.gradle.kts");
        Path f = Files.isRegularFile(g1) ? g1 : (Files.isRegularFile(g2) ? g2 : null);
        if (f == null) return null;

        String txt = Files.readString(f);

        String v = firstMatch(txt,
            "JavaLanguageVersion\\.of\\((\\d+)\\)",
            "sourceCompatibility\\s*=\\s*['\\\"]?(\\d+)['\\\"]?",
            "JavaVersion\\.VERSION_(\\d+)"
        );
        return (v == null) ? null : v.trim();
        } catch (Exception e) {
        return null;
        }
    }

    /**
     * pom.xml에 spring-cloud-starter-gateway 문자열이 있으면 gateway로 판단한다.
     *
     * @param pom pom.xml 경로
     * @return gateway 힌트가 있으면 true
     */
    private boolean hasGatewayDependencyInPom(Path pom) {
        try {
        String xml = Files.readString(pom);
        return xml.contains("spring-cloud-starter-gateway");
        } catch (Exception e) {
        return false;
        }
    }

    /**
     * gradle 파일에 spring-cloud-starter-gateway 문자열이 있으면 gateway로 판단한다.
     *
     * @param moduleDir 모듈 디렉토리
     * @return gateway 힌트가 있으면 true
     */
    private boolean hasGatewayHintInGradle(Path moduleDir) {
        try {
        Path g1 = moduleDir.resolve("build.gradle");
        Path g2 = moduleDir.resolve("build.gradle.kts");
        Path f = Files.isRegularFile(g1) ? g1 : (Files.isRegularFile(g2) ? g2 : null);
        if (f == null) return false;

        String txt = Files.readString(f);
        return txt.contains("spring-cloud-starter-gateway");
        } catch (Exception e) {
        return false;
        }
    }

    /**
     * 여러 패턴 중 첫 매칭 그룹1 값을 반환한다.
     *
     * @param text 텍스트
     * @param patterns 정규식 패턴들
     * @return 매칭값 또는 null
     */
    private String firstMatch(String text, String... patterns) {
        for (String p : patterns) {
        var m = Pattern.compile(p, Pattern.DOTALL).matcher(text);
        if (m.find()) return m.group(1);
        }
        return null;
    }
}