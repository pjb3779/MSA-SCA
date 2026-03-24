package buaa.msasca.sca.core.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;

public class CreateAnalysisRunService implements CreateAnalysisRunUseCase {

    private final AnalysisRunCommandPort analysisRunCommandPort;
    private final ProjectVersionSourceCachePort sourceCachePort;

    private final ObjectMapper om = new ObjectMapper();

    public CreateAnalysisRunService(
        AnalysisRunCommandPort analysisRunCommandPort,
        ProjectVersionSourceCachePort sourceCachePort
    ) {
        this.analysisRunCommandPort = analysisRunCommandPort;
        this.sourceCachePort = sourceCachePort;
    }

    /**
     * analysis_run 생성 진입점
     * - (옵션) source cache 사전 검증
     * - active run 중복 방지
     * - configJson 정규화 + 필수값 검증(mscan)
     */
    @Override
    public AnalysisRun handle(Command command) {
        if (command.requireSourceCache()) {
            sourceCachePort.findValidByProjectVersionId(command.projectVersionId())
                .orElseThrow(() -> new IllegalStateException(
                    "Source cache is not ready for projectVersionId=" + command.projectVersionId()
                ));
        }

        if (analysisRunCommandPort.existsActiveRun(command.projectVersionId())) {
            // 지금 스타일 유지: 중복이면 null 반환
            return null;
        }

        JsonNode normalized = normalizeConfig(command.projectVersionId(), command.configJson());

        return analysisRunCommandPort.createPending(
            command.projectVersionId(),
            normalized,
            command.triggeredBy()
        );
    }

    /**
     * configJson을 실행 가능한 형태로 정규화한다.
     * - mscan.name: 없으면 pv-{projectVersionId}로 채움
     * - mscan.classpathKeywords: 없으면 즉시 예외(필수)
     * - jvmArgs 기본값(16GB 환경 고려): -Xmx6g -XX:MaxMetaspaceSize=1g
     * - reuse 기본값 false
     * - openAiApiKey 같은 키는 제거(키는 worker application.yml로 주입)
     */
    private JsonNode normalizeConfig(Long projectVersionId, JsonNode raw) {
        ObjectNode root = (raw != null && raw.isObject())
            ? ((ObjectNode) raw).deepCopy()
            : om.createObjectNode();

        ObjectNode mscan = root.with("mscan");

        if (isBlank(mscan.path("name").asText(null))) {
            mscan.put("name", "pv-" + projectVersionId);
        }

        if (isBlank(mscan.path("classpathKeywords").asText(null))) {
            throw new IllegalArgumentException(
                "Missing configJson: mscan.classpathKeywords (ex: \".youlai.\")"
            );
        }

        if (!mscan.has("reuse")) mscan.put("reuse", false);

        // 16GB 환경 기본값
        if (isBlank(mscan.path("jvmArgs").asText(null))) {
            mscan.put("jvmArgs", "-Xmx6g -XX:MaxMetaspaceSize=1g");
        }

        // 키는 config_json에 저장하지 않음(보안/정합)
        mscan.remove("openAiApiKey");
        mscan.remove("OPENAI_API_KEY");

        return root;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}