package buaa.msasca.sca.infra.persistence.jpa.entity.result.sanitizer;

import buaa.msasca.sca.core.domain.enums.SanitizerDecision;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.AgentRunDetailEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "sanitizer_candidate",
    indexes = {
        @Index(name = "idx_sanitizer_candidate_agent", columnList = "agent_run_id"),
        @Index(name = "idx_sanitizer_candidate_service", columnList = "service_module_id"),
        @Index(name = "idx_sanitizer_candidate_decision", columnList = "decision")
    }
)
@SequenceGenerator(
    name = "sanitizer_candidate_seq_gen",
    sequenceName = "sanitizer_candidate_id_seq",
    allocationSize = 1
)
public class SanitizerCandidateEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sanitizer_candidate_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_run_id", referencedColumnName = "tool_run_id", nullable = false)
    private AgentRunDetailEntity agentRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_module_id")
    private ServiceModuleEntity serviceModule;

    @Column(name = "method_signature", columnDefinition = "text")
    private String methodSignature;

    @Column(name = "class_name", length = 1024)
    private String className;

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private SanitizerDecision decision;

    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "reason_summary", columnDefinition = "text")
    private String reasonSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registry_id")
    private SanitizerRegistryEntity registry;

    private SanitizerCandidateEntity(AgentRunDetailEntity agentRun, ServiceModuleEntity sm, String methodSignature) {
        this.agentRun = agentRun;
        this.serviceModule = sm;
        this.methodSignature = methodSignature;
        this.decision = SanitizerDecision.CANDIDATE;
    }

    public static SanitizerCandidateEntity create(AgentRunDetailEntity agentRun, ServiceModuleEntity sm, String methodSignature) {
        return new SanitizerCandidateEntity(agentRun, sm, methodSignature);
    }

    public void attachLocation(String className, String filePath, Integer startLine, Integer endLine) {
        this.className = className;
        this.filePath = filePath;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public void decide(SanitizerDecision decision, BigDecimal confidence, String reasonSummary) {
        this.decision = decision;
        this.confidence = confidence;
        this.reasonSummary = reasonSummary;
    }

    public void linkRegistry(SanitizerRegistryEntity registry) {
        this.registry = registry;
    }
}