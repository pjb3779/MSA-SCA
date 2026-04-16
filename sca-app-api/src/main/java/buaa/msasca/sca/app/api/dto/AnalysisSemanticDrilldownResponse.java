package buaa.msasca.sca.app.api.dto;

import java.util.List;

/**
 * 서비스 내부 의미 계층: 파일 → 클래스 → 메서드 → 스텝(CodeQL/MScan 근거).
 */
public record AnalysisSemanticDrilldownResponse(
    Long analysisRunId,
    String moduleName,
    List<Node> roots
) {
  public record Node(
      String id,
      String kind,
      String label,
      Long unifiedRecordId,
      Integer stepIndex,
      String filePath,
      String className,
      String methodName,
      Integer lineNumber,
      String codeSnippet,
      String role,
      String vulnerabilityType,
      String recordTitle,
      List<Node> children
  ) {}
}
