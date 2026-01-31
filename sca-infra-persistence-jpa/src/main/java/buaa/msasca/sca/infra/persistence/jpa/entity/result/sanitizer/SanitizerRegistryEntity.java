package buaa.msasca.sca.infra.persistence.jpa.entity.result.sanitizer;

import buaa.msasca.sca.core.domain.enums.SanitizerDecision;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "sanitizer_registry",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sanitizer_registry", columnNames = {"project_id", "method_signature"})
    },
    indexes = { @Index(name = "idx_sanitizer_registry_status", columnList = "status") }
)
@SequenceGenerator(
    name = "sanitizer_registry_seq_gen",
    sequenceName = "sanitizer_registry_id_seq",
    allocationSize = 1
)
public class SanitizerRegistryEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sanitizer_registry_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(name = "method_signature", columnDefinition = "text", nullable = false)
    private String methodSignature;

    @Column(name = "class_name", length = 1024)
    private String className;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private SanitizerDecision status;

    @Column(name = "default_confidence", precision = 5, scale = 2)
    private BigDecimal defaultConfidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "first_seen_version_id")
    private ProjectVersionEntity firstSeenVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_seen_version_id")
    private ProjectVersionEntity lastSeenVersion;

    private SanitizerRegistryEntity(ProjectEntity project, String methodSignature, String className) {
        this.project = project;
        this.methodSignature = methodSignature;
        this.className = className;
        this.status = SanitizerDecision.CANDIDATE;
    }

    public static SanitizerRegistryEntity create(ProjectEntity project, String methodSignature, String className) {
        return new SanitizerRegistryEntity(project, methodSignature, className);
    }

    public void updateStatus(SanitizerDecision to, BigDecimal confidence) {
        this.status = to;
        this.defaultConfidence = confidence;
    }

    public void seenIn(ProjectVersionEntity version) {
        if (this.firstSeenVersion == null) this.firstSeenVersion = version;
        this.lastSeenVersion = version;
    }
}