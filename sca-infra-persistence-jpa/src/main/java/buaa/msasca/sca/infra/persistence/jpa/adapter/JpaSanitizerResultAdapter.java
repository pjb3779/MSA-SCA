package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import buaa.msasca.sca.core.domain.enums.SanitizerDecision;
import buaa.msasca.sca.core.port.out.persistence.SanitizerResultCommandPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.sanitizer.SanitizerCandidateEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.sanitizer.SanitizerRegistryEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.AgentRunDetailEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.AgentRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.SanitizerCandidateJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.SanitizerRegistryJpaRepository;

public class JpaSanitizerResultAdapter implements SanitizerResultCommandPort {

    private final AgentRunDetailJpaRepository agentDetailRepo;
    private final ProjectVersionJpaRepository projectVersionRepo;
    private final SanitizerRegistryJpaRepository registryRepo;
    private final SanitizerCandidateJpaRepository candidateRepo;

    public JpaSanitizerResultAdapter(
        AgentRunDetailJpaRepository agentDetailRepo,
        ProjectVersionJpaRepository projectVersionRepo,
        SanitizerRegistryJpaRepository registryRepo,
        SanitizerCandidateJpaRepository candidateRepo
    ) {
        this.agentDetailRepo = agentDetailRepo;
        this.projectVersionRepo = projectVersionRepo;
        this.registryRepo = registryRepo;
        this.candidateRepo = candidateRepo;
    }

    @Override
    @Transactional
    public void saveAgentSanitizerResults(
        Long toolRunId,
        Long projectVersionId,
        List<SanitizerResultRow> results
    ) {
        if (results == null || results.isEmpty()) {
            return;
        }
        AgentRunDetailEntity agentRun = agentDetailRepo.findById(toolRunId)
            .orElseThrow(() -> new IllegalArgumentException("agent_run_detail not found for tool_run_id: " + toolRunId));
        ProjectVersionEntity projectVersion = projectVersionRepo.findById(projectVersionId)
            .orElseThrow(() -> new IllegalArgumentException("project_version not found: " + projectVersionId));
        ProjectEntity project = projectVersion.getProject();

        for (SanitizerResultRow row : results) {
            String methodSig = truncate(row.methodName(), 64 * 1024);  // text limit
            String className = deriveClassName(row.filePath());

            SanitizerDecision decision = mapStatus(row.status());
            BigDecimal confidence = confidenceFor(decision);

            SanitizerRegistryEntity registry = registryRepo
                .findByProjectAndMethodSignature(project, methodSig)
                .orElseGet(() -> {
                    SanitizerRegistryEntity r = SanitizerRegistryEntity.create(project, methodSig, className);
                    r.updateStatus(decision, confidence);
                    return registryRepo.save(r);
                });
            registry.seenIn(projectVersion);
            if (shouldUpgradeStatus(registry.getStatus(), decision)) {
                registry.updateStatus(decision, confidence);
            }
            registryRepo.save(registry);

            SanitizerCandidateEntity candidate = SanitizerCandidateEntity.create(
                agentRun,
                null,
                methodSig
            );
            candidate.attachLocation(
                truncate(className, 1024),
                truncate(row.filePath(), 1024),
                null,
                null
            );
            candidate.decide(
                decision,
                confidence,
                truncate(row.reasoning(), 64 * 1024)
            );
            candidate.linkRegistry(registry);
            candidateRepo.save(candidate);
        }
    }

    private static SanitizerDecision mapStatus(String status) {
        if (status == null || status.isBlank()) return SanitizerDecision.CANDIDATE;
        return switch (status.toUpperCase()) {
            case "CONFIRMED" -> SanitizerDecision.CONFIRMED;
            case "CONDITIONAL" -> SanitizerDecision.CONDITIONAL;
            case "NEEDS_REVIEW" -> SanitizerDecision.NEEDS_REVIEW;
            case "REJECTED" -> SanitizerDecision.REJECTED;
            default -> SanitizerDecision.CANDIDATE;
        };
    }

    private static boolean shouldUpgradeStatus(SanitizerDecision current, SanitizerDecision incoming) {
        if (incoming == SanitizerDecision.CONFIRMED && current != SanitizerDecision.CONFIRMED) return true;
        if (incoming == SanitizerDecision.NEEDS_REVIEW && current == SanitizerDecision.CANDIDATE) return true;
        return false;
    }

    private static BigDecimal confidenceFor(SanitizerDecision decision) {
        return switch (decision) {
            case CONFIRMED -> new BigDecimal("0.90");
            case CONDITIONAL -> new BigDecimal("0.70");
            case NEEDS_REVIEW -> new BigDecimal("0.50");
            case REJECTED -> new BigDecimal("0.20");
            case CANDIDATE -> new BigDecimal("0.50");
        };
    }

    private static String deriveClassName(String filePath) {
        if (filePath == null || filePath.isBlank()) return null;
        String path = filePath.replace("\\", "/");
        if (path.endsWith(".java")) {
            path = path.substring(0, path.length() - 5);
        }
        int srcIdx = path.indexOf("/src/");
        if (srcIdx >= 0) {
            path = path.substring(srcIdx + 5);
        }
        return path.replace("/", ".");
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}
