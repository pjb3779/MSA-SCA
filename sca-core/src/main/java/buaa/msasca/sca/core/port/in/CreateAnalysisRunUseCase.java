package buaa.msasca.sca.core.port.in;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.model.AnalysisRun;

public interface CreateAnalysisRunUseCase {

    AnalysisRun handle(Command command);

    /**
     * 특정 projectVersion에 대해 새로운 analysis_run을 생성
     * requireSourceCache=true 인 경우, 실행 전에 유효한 source cache가 존재여부 검증
     * 검증 통과 시 analysis_run을 PENDING 상태로 생성
     */
    record Command(
        Long projectVersionId,
        JsonNode configJson,
        String triggeredBy,
        boolean requireSourceCache
    ) {}
}
