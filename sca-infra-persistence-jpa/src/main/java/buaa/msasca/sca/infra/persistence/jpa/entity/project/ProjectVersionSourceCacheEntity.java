package buaa.msasca.sca.infra.persistence.jpa.entity.project;

import java.time.Instant;

import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "project_version_source_cache",
    indexes = {
        @Index(name = "idx_pvsc_version_id", columnList = "project_version_id"),
        @Index(name = "idx_pvsc_is_valid", columnList = "is_valid")
    }
)
@SequenceGenerator(
    name = "pv_source_cache_seq_gen",
    sequenceName = "project_version_source_cache_id_seq",
    allocationSize = 1
)
public class ProjectVersionSourceCacheEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pv_source_cache_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_version_id", nullable = false)
    private ProjectVersionEntity projectVersion;

    @Column(name = "storage_path", length = 1024, nullable = false)
    private String storagePath;

    @Column(name = "is_valid", nullable = false)
    private boolean valid;

    @Column(name = "expires_at")
    private Instant expiresAt;

    private ProjectVersionSourceCacheEntity(ProjectVersionEntity projectVersion, String storagePath, Instant expiresAt) {
        this.projectVersion = projectVersion;
        this.storagePath = storagePath;
        this.valid = true;
        this.expiresAt = expiresAt;
    }

    public static ProjectVersionSourceCacheEntity create(ProjectVersionEntity pv, String storagePath, Instant expiresAt) {
        return new ProjectVersionSourceCacheEntity(pv, storagePath, expiresAt);
    }

    public void invalidate() { this.valid = false; }
    public void extend(Instant newExpiresAt) { this.expiresAt = newExpiresAt; this.valid = true; }
}