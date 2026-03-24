package buaa.msasca.sca.tool.agent.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Sanitizer 판정 결과 검증기 (Verifier).
 *
 * <p><b>역할:</b> Judge의 LLM 판정을 보수적 규칙으로 재검증해 최종 상태를 부여한다. LLM 호출 없음.</p>
 *
 * <p><b>규칙:</b></p>
 * <ul>
 *   <li>메서드명에 sanitize/escape/validate + confidence≥0.75 → CONFIRMED</li>
 *   <li>위 메서드명 + confidence≥0.55 → CONDITIONAL</li>
 *   <li>위 메서드명 + confidence&lt;0.55 → NEEDS_REVIEW</li>
 *   <li>위 메서드명 없음 + confidence≥0.6 → NEEDS_REVIEW</li>
 *   <li>그 외 → REJECTED</li>
 * </ul>
 *
 * <p><b>최종 상태:</b> CONFIRMED / CONDITIONAL / NEEDS_REVIEW / REJECTED</p>
 */
public class SanitizerCandidateVerifier {

    public List<VerifiedSanitizerCandidate> verify(List<JudgedSanitizerCandidate> judged) {
        List<VerifiedSanitizerCandidate> out = new ArrayList<>();
        for (JudgedSanitizerCandidate j : judged) {
            SanitizerStatus status = decide(j);
            out.add(new VerifiedSanitizerCandidate(
                j.candidate(),
                status,
                j.actionType(),
                j.vulnTypes(),
                j.reasoning()
            ));
        }
        return out;
    }

    /** Judge 결과를 규칙으로 재검증해 최종 SanitizerStatus를 결정한다. */
    private SanitizerStatus decide(JudgedSanitizerCandidate j) {
        String name = j.candidate().methodName().toLowerCase();
        // 메서드명에 sanitize/escape/validate가 있으면: confidence 기준으로 등급 부여
        if (name.contains("sanitize") || name.contains("escape") || name.contains("validate")) {
            if (j.confidence() >= 0.75) return SanitizerStatus.CONFIRMED;
            if (j.confidence() >= 0.55) return SanitizerStatus.CONDITIONAL;
            return SanitizerStatus.NEEDS_REVIEW;
        }
        // 위 키워드 없으면: confidence 높아도 NEEDS_REVIEW, 낮으면 REJECTED
        if (j.confidence() >= 0.6) return SanitizerStatus.NEEDS_REVIEW;
        return SanitizerStatus.REJECTED;
    }
}

