package buaa.msasca.sca.app.api.support;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import buaa.msasca.sca.app.api.dto.AnalysisServiceSourceGraphResponse;

/**
 * 소스 루트 아래 서비스 모듈 디렉터리를 순회해 folder/file 노드 그래프를 만든다.
 */
public final class ServiceSourceGraphBuilder {

  private static final int DEFAULT_MAX_DEPTH = 14;
  private static final int DEFAULT_MAX_NODES = 2500;

  private ServiceSourceGraphBuilder() {}

  public static AnalysisServiceSourceGraphResponse build(
      Long analysisRunId,
      String moduleName,
      Path moduleRoot
  ) {
    return build(analysisRunId, moduleName, moduleRoot, DEFAULT_MAX_DEPTH, DEFAULT_MAX_NODES);
  }

  public static AnalysisServiceSourceGraphResponse build(
      Long analysisRunId,
      String moduleName,
      Path moduleRoot,
      int maxDepth,
      int maxNodes
  ) {
    Path normalizedModule = moduleRoot.normalize();
    if (!Files.isDirectory(normalizedModule)) {
      return new AnalysisServiceSourceGraphResponse(
          analysisRunId,
          moduleName,
          "",
          "",
          List.of(),
          List.of()
      );
    }

    Path graphRoot = pickGraphRoot(normalizedModule);
    String graphRootRel = posixRelative(normalizedModule, graphRoot);

    List<AnalysisServiceSourceGraphResponse.Node> nodes = new ArrayList<>();
    List<AnalysisServiceSourceGraphResponse.Edge> edges = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    String rootId = folderId(moduleName, "");
    String rootLabel = (graphRootRel != null && !graphRootRel.isBlank())
        ? graphRootRel
        : labelForRoot(graphRoot);
    nodes.add(new AnalysisServiceSourceGraphResponse.Node(rootId, rootLabel, "folder"));
    seen.add(rootId);

    walk(
        graphRoot,
        graphRoot,
        moduleName,
        "",
        0,
        maxDepth,
        maxNodes,
        nodes,
        edges,
        seen
    );

    return new AnalysisServiceSourceGraphResponse(
        analysisRunId,
        moduleName,
        graphRootRel,
        rootId,
        nodes,
        edges
    );
  }

  private static String labelForRoot(Path graphRoot) {
    Path name = graphRoot.getFileName();
    return name != null ? name.toString() : graphRoot.toString();
  }

  /**
   * 가능하면 {@code src/main} 한 루트 아래에 java/kotlin/resources 등을 한 트리로 묶는다.
   * 없으면 기존 후보 순으로 단일 루트를 고른다.
   */
  private static Path pickGraphRoot(Path moduleRoot) {
    Path srcMain = moduleRoot.resolve("src/main");
    if (Files.isDirectory(srcMain)) {
      return srcMain.normalize();
    }
    Path[] candidates = new Path[] {
        moduleRoot.resolve("src/main/java"),
        moduleRoot.resolve("src/main/kotlin"),
        moduleRoot.resolve("src/main"),
        moduleRoot.resolve("src"),
        moduleRoot
    };
    for (Path p : candidates) {
      if (Files.isDirectory(p)) {
        return p.normalize();
      }
    }
    return moduleRoot;
  }

  private static void walk(
      Path current,
      Path graphRoot,
      String moduleName,
      String relFromRoot,
      int depth,
      int maxDepth,
      int maxNodes,
      List<AnalysisServiceSourceGraphResponse.Node> nodes,
      List<AnalysisServiceSourceGraphResponse.Edge> edges,
      Set<String> seen
  ) {
    if (depth > maxDepth || nodes.size() >= maxNodes) {
      return;
    }
    String parentId = folderId(moduleName, relFromRoot);

    List<Path> children;
    try (Stream<Path> stream = Files.list(current)) {
      children = stream
          .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
          .toList();
    } catch (IOException e) {
      return;
    }

    List<Path> dirs = new ArrayList<>();
    List<Path> files = new ArrayList<>();
    for (Path p : children) {
      String name = p.getFileName().toString();
      if (Files.isDirectory(p)) {
        if (skipDir(name)) continue;
        dirs.add(p);
      } else if (isSourceLikeFile(name)) {
        files.add(p);
      }
    }

    for (Path dir : dirs) {
      if (nodes.size() >= maxNodes) break;
      String childRel = joinRel(relFromRoot, dir.getFileName().toString());
      String childId = folderId(moduleName, childRel);
      if (seen.add(childId)) {
        nodes.add(new AnalysisServiceSourceGraphResponse.Node(childId, dir.getFileName().toString(), "folder"));
      }
      edges.add(new AnalysisServiceSourceGraphResponse.Edge(parentId, childId));
      walk(dir, graphRoot, moduleName, childRel, depth + 1, maxDepth, maxNodes, nodes, edges, seen);
    }

    for (Path file : files) {
      if (nodes.size() >= maxNodes) break;
      String rel = joinRel(relFromRoot, file.getFileName().toString());
      String fileId = fileId(moduleName, rel);
      if (seen.add(fileId)) {
        nodes.add(new AnalysisServiceSourceGraphResponse.Node(fileId, file.getFileName().toString(), "file"));
      }
      edges.add(new AnalysisServiceSourceGraphResponse.Edge(parentId, fileId));
    }
  }

  private static boolean skipDir(String name) {
    return switch (name) {
      case ".git", ".github", ".idea", ".gradle", "node_modules", "target", "build", "out", "dist", ".svn" -> true;
      default -> name.startsWith(".");
    };
  }

  private static boolean isSourceLikeFile(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    return lower.endsWith(".java")
        || lower.endsWith(".kt")
        || lower.endsWith(".kts")
        || lower.endsWith(".xml")
        || lower.endsWith(".gradle")
        || lower.endsWith(".properties")
        || lower.endsWith(".yml")
        || lower.endsWith(".yaml");
  }

  private static String joinRel(String base, String seg) {
    if (base == null || base.isBlank()) return seg;
    return base + "/" + seg;
  }

  static String folderId(String moduleName, String relFromGraphRoot) {
    String body = (relFromGraphRoot == null || relFromGraphRoot.isBlank())
        ? "root"
        : relFromGraphRoot.replace('\\', '/');
    return "d:" + moduleName + ":" + body;
  }

  static String fileId(String moduleName, String relFromGraphRoot) {
    return "f:" + moduleName + ":" + relFromGraphRoot.replace('\\', '/');
  }

  private static String posixRelative(Path root, Path child) {
    try {
      Path rel = root.relativize(child.normalize());
      return rel.toString().replace('\\', '/');
    } catch (Exception e) {
      return "";
    }
  }

  public static Path toLocalPath(String storagePath) {
    if (storagePath == null || storagePath.isBlank()) {
      throw new IllegalArgumentException("storagePath is empty");
    }
    String s = storagePath.trim();
    if (s.startsWith("file:")) {
      return Path.of(URI.create(s));
    }
    return Path.of(s);
  }
}
