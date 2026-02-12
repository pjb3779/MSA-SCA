package buaa.msasca.sca.core.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;

public class EnqueueAnalysisRunOnSourceReadyService {

    private final AnalysisRunCommandPort analysisRunCommandPort;
    private final ObjectMapper om;

    public EnqueueAnalysisRunOnSourceReadyService(AnalysisRunCommandPort analysisRunCommandPort, ObjectMapper om) {
        this.analysisRunCommandPort = analysisRunCommandPort;
        this.om = om;
    }

    /**
     * 소스 준비 완료 시점에 analysis_run(PENDING)을 자동 생성한다.
     * - 같은 project_version에 PENDING/RUNNING이 있으면 중복 생성하지 않는다.
     *
     * @param projectVersionId project_version id
     * @param triggeredBy 트리거 주체(예: "api")
     * @return 생성된 run, 없으면 null
     */
    public AnalysisRun enqueueIfAbsent(Long projectVersionId, String triggeredBy) {
        if (analysisRunCommandPort.existsActiveRun(projectVersionId)) {
        return null;
        }

        ObjectNode config = om.createObjectNode();
        config.put("pipeline", "default");
        config.put("stage", "build"); // 지금은 BUILD만 돌리는 모드면 여기로 표시

        return analysisRunCommandPort.createPending(projectVersionId, config, triggeredBy);
    }

    /**
     * 외부에서 config를 주입하고 싶을 때 사용.
     *
     * @param projectVersionId project_version id
     * @param configJson config
     * @param triggeredBy triggered_by
     * @return 생성된 run, 없으면 null
     */
    public AnalysisRun enqueueIfAbsent(Long projectVersionId, JsonNode configJson, String triggeredBy) {
        if (analysisRunCommandPort.existsActiveRun(projectVersionId)) {
        return null;
        }
        return analysisRunCommandPort.createPending(projectVersionId, configJson, triggeredBy);
    }
}