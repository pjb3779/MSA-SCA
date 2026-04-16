package buaa.msasca.sca.app.api.dto;

import java.util.List;

import buaa.msasca.sca.core.domain.enums.Severity;

public record AnalysisResultSummaryResponse(
    Long analysisRunId,
    Totals totals,
    List<SeverityBucket> severityBuckets,
    List<VulnerabilityTypeBucket> vulnerabilityTypeBuckets,
    List<ModuleBucket> moduleBuckets,
    List<FlowEdge> flowEdges,
    List<FlowEdge> mscanFlowEdges,
    List<FlowEdge> codeqlFlowEdges,
    List<Finding> findings
) {
  public record Totals(long all, long codeqlOnly, long mscanOnly, long matched) {}

  public record SeverityBucket(Severity severity, long count) {}

  public record VulnerabilityTypeBucket(String vulnerabilityType, long count) {}

  public record ModuleBucket(String moduleName, long count) {}

  public record FlowEdge(String sourceModule, String sinkModule, long count, Severity maxSeverity) {}

    public record Finding(
      Long unifiedRecordId,
      Severity severity,
      String vulnerabilityType,
      String title,
      String sourceFilePath,
      Integer sourceLine,
      String sourceMethod,
      String sinkFilePath,
      Integer sinkLine,
      String sinkMethod,
      String sourceModule,
      String sinkModule,
      /** CodeQL run detail / MScan 기준 소속 서비스 (그래프·필터용) */
      Long scopeServiceModuleId,
      String scopeServiceModuleName
  ) {}
}

