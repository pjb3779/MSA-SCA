package buaa.msasca.sca.tool.agent.pipeline;

import java.util.List;
import java.util.Set;

/**
 * Sanitizer 후보.
 *
 * <p>메서드명/파일경로/신호(signals)를 기본으로 하며, CodeQL SARIF 결과와 연관되면
 * codeqlFindingId, codeqlRuleId, codeqlMessage, flowStepLabels가 세팅된다.</p>
 */
public record SanitizerCandidate(
    String methodName,
    String filePath,
    Set<String> signals,
    /** CodeQL finding ID (SARIF 연관 시) */
    Long codeqlFindingId,
    /** CodeQL rule ID (예: java/sql-injection) */
    String codeqlRuleId,
    /** CodeQL 메시지 (Judge LLM 프롬프트용 맥락) */
    String codeqlMessage,
    /** flow step label 요약 (Judge용 taint flow 맥락) */
    List<String> flowStepLabels
) {
    /** 패턴 기반 마이닝용 생성자. CodeQL과 무관한 후보(이름/어노테이션 힌트 기반). */
    public static SanitizerCandidate ofPattern(String methodName, String filePath, Set<String> signals) {
        return new SanitizerCandidate(methodName, filePath, signals, null, null, null, null);
    }

    /** CodeQL SARIF 연관 후보 생성자. Judge에서 신뢰도 가산 및 맥락 활용. */
    public static SanitizerCandidate ofCodeql(
        String methodName, String filePath, Set<String> signals,
        Long codeqlFindingId, String codeqlRuleId, String codeqlMessage, List<String> flowStepLabels
    ) {
        return new SanitizerCandidate(
            methodName, filePath, signals,
            codeqlFindingId, codeqlRuleId, codeqlMessage, flowStepLabels
        );
    }
}

