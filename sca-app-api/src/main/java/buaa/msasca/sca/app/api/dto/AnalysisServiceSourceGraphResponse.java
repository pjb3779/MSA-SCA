package buaa.msasca.sca.app.api.dto;

import java.util.List;

/**
 * 서비스 모듈 소스 트리(디렉터리/파일) 그래프.
 * 소스 캐시 경로 + service_module.root_path 기준으로 실제 폴더 구조를 반환한다.
 */
public record AnalysisServiceSourceGraphResponse(
    Long analysisRunId,
    String moduleName,
    /** graphRoot 기준 상대 경로 (예: src/main/java) */
    String graphRootRelativePath,
    /** 트리 루트 폴더 노드 id (service 노드와 연결용) */
    String rootFolderNodeId,
    List<Node> nodes,
    List<Edge> edges
) {
  public record Node(String id, String label, String kind) {
    /** kind: folder | file */
  }

  public record Edge(String source, String target) {}
}
