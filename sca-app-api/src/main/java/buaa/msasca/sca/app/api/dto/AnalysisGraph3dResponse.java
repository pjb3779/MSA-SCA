package buaa.msasca.sca.app.api.dto;

import java.util.List;

import buaa.msasca.sca.core.domain.enums.Severity;

/**
 * 3D 드릴다운 그래프 응답 DTO.
 * 서비스 -> 메서드 -> 코드 위치 계층을 표현한다.
 */
public record AnalysisGraph3dResponse(
    Long analysisRunId,
    List<Node> nodes,
    List<Edge> edges
) {
  public record Node(
      String id,
      String label,
      String kind, // service | method | code
      Severity severity
  ) {}

  public record Edge(
      String source,
      String target,
      long weight
  ) {}
}

