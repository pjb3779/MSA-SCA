package buaa.msasca.sca.infra.persistence.jpa.entity.result.sanitizer;

import buaa.msasca.sca.core.domain.enums.SanitizerDecision;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.CreatedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.AgentRunDetailEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "sanitizer_registry_revision",
    indexes = {
        @Index(name = "idx_sanitizer_revision_registry", columnList = "registry_id"),
        @Index(name = "idx_sanitizer_revision_agent_run", columnList = "agent_run_id")
    }
)
@SequenceGenerator(
    name = "sanitizer_registry_revision_seq_gen",
    sequenceName = "sanitizer_registry_revision_id_seq",
    allocationSize = 1
)
public class SanitizerRegistryRevisionEntity extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sanitizer_registry_revision_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registry_id", nullable = false)
    private SanitizerRegistryEntity registry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_run_id", referencedColumnName = "tool_run_id")
    private AgentRunDetailEntity agentRun;

    @Column(name = "changed_by", length = 255)
    private String changedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private SanitizerDecision fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 32, nullable = false)
    private SanitizerDecision toStatus;

    @Column(columnDefinition = "text")
    private String note;

    private SanitizerRegistryRevisionEntity(SanitizerRegistryEntity registry, SanitizerDecision toStatus) {
        this.registry = registry;
        this.toStatus = toStatus;
    }

    public static SanitizerRegistryRevisionEntity create(
        SanitizerRegistryEntity registry,
        SanitizerDecision from,
        SanitizerDecision to,
        String changedBy,
        String note
    ) {
        SanitizerRegistryRevisionEntity e = new SanitizerRegistryRevisionEntity(registry, to);
        e.fromStatus = from;
        e.changedBy = changedBy;
        e.note = note;
        return e;
    }

    public void linkAgentRun(AgentRunDetailEntity agentRun) { this.agentRun = agentRun; }
}