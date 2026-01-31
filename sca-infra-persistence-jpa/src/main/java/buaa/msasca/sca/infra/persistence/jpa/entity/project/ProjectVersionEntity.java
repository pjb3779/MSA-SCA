package buaa.msasca.sca.infra.persistence.jpa.entity.project;

import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.domain.enums.SourceType;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "project_version",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_version_label", columnNames = {"project_id", "version_label"})
    },
    indexes = {
        @Index(name = "idx_project_version_project_id", columnList = "project_id"),
        @Index(name = "idx_project_version_status", columnList = "status")
    }
)
@SequenceGenerator(
    name = "project_version_seq_gen",
    sequenceName = "project_version_id_seq",
    allocationSize = 1
)
public class ProjectVersionEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "project_version_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(name = "version_label", length = 255, nullable = false)
    private String versionLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "soruce_type", length = 32, nullable = false)
    private SourceType sourceType;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "upload_file_path", length = 1024)
    private String uploadFilePath;

    @Column(name = "vcs_commit_hash", length = 64)
    private String vcsCommitHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private RunStatus status;

    private ProjectVersionEntity(
        ProjectEntity project,
        String versionLabel,
        SourceType sourceType,
        String sourceUrl,
        String uploadFilePath,
        String vcsCommitHash
    ) {
        this.project = project;
        this.versionLabel = versionLabel;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.uploadFilePath = uploadFilePath;
        this.vcsCommitHash = vcsCommitHash;
        this.status = RunStatus.PENDING;
    }

    public static ProjectVersionEntity create(
        ProjectEntity project,
        String versionLabel,
        SourceType sourceType,
        String sourceUrl,
        String uploadFilePath,
        String vcsCommitHash
    ) {
        return new ProjectVersionEntity(project, versionLabel, sourceType, sourceUrl, uploadFilePath, vcsCommitHash);
    }

    public void changeSourceType(SourceType type) { this.sourceType = type; }
    public void markRunning() { this.status = RunStatus.RUNNING; }
    public void markDone() { this.status = RunStatus.DONE; }
    public void markFailed() { this.status = RunStatus.FAILED; }
}
