package buaa.msasca.sca.app.api.dto;

import java.util.List;

import buaa.msasca.sca.core.domain.enums.Severity;

/**
 * 2D 전파 그래프 응답 DTO.
 * - 노드: 서비스(모듈)
 * - 엣지: source -> sink 오염 전파 경로
 */
public record AnalysisGraph2dResponse(
    Long analysisRunId,
    List<Node> nodes,
    List<Edge> edges
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
      /** MScan(또는 폴백 시 전체) 기준 흐름 건수 */
      long count,
      Severity maxSeverity,
      /** 동일 경로에 대한 CodeQL finding 건수(0이면 CodeQL 오버레이 없음) */
      long codeqlCount,
      /** codeqlCount > 0 일 때 링크 강조용 */
      Severity codeqlMaxSeverity
  ) {}
}

