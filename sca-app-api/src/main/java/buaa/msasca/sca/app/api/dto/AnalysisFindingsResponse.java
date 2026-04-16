package buaa.msasca.sca.app.api.dto;

import java.util.List;

/**
 * 파인딩 목록 조회 응답 DTO.
 * 필터/페이징 결과를 포함한다.
 */
public record AnalysisFindingsResponse(
    Long analysisRunId,
    long total,
    int page,
    int size,
    List<AnalysisResultSummaryResponse.Finding> items
) {}

