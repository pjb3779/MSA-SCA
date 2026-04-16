package buaa.msasca.sca.app.api.dto;

import java.util.List;

import buaa.msasca.sca.core.domain.enums.Severity;

/**
 * 서비스 단위 그래프: 등록 모듈 노드 + 관측 흐름 엣지(베이스) + MScan/CodeQL 지표 + 레이아웃용 와이어링.
 */
public record AnalysisServiceGraphResponse(
    Long analysisRunId,
    List<Node> nodes,
    List<Edge> edges,
    List<WiringEdge> wiringEdges
) {
  public record Node(
      String id,
      String label,
      Severity maxSeverity,
      boolean tainted
  ) {}

  public record Edge(
      String pathId,
      String source,
      String target,
      long flowCount,
      Severity flowMaxSeverity,
      long mscanCount,
      Severity mscanMaxSeverity,
      long codeqlCount,
      Severity codeqlMaxSeverity
  ) {}

  public record WiringEdge(String source, String target) {}
}
