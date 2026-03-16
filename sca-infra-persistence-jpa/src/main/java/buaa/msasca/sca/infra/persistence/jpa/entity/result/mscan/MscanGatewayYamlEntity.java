package buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import buaa.msasca.sca.core.domain.enums.GatewayYamlProvidedBy;
import buaa.msasca.sca.core.domain.enums.GatewayYamlStatus;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "mscan_gateway_yaml",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_mscan_gateway_yaml_pv", columnNames = {"project_version_id"})
    },
    indexes = {
        @Index(name = "idx_mscan_gateway_yaml_status", columnList = "status")
    }
)
@SequenceGenerator(
    name = "mscan_gateway_yaml_seq_gen",
    sequenceName = "mscan_gateway_yaml_id_seq",
    allocationSize = 1
)
public class MscanGatewayYamlEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mscan_gateway_yaml_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_version_id", nullable = false)
    private ProjectVersionEntity projectVersion;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private GatewayYamlStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "provided_by", length = 32)
    private GatewayYamlProvidedBy providedBy;

    @Column(name = "storage_path", length = 2048)
    private String storagePath;

    @Column(length = 64)
    private String sha256;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "cache_rel_path", length = 1024, nullable = false)
    private String cacheRelPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private JsonNode metadataJson;

    private MscanGatewayYamlEntity(ProjectVersionEntity pv, String cacheRelPath) {
        this.projectVersion = pv;
        this.cacheRelPath = cacheRelPath;
        this.status = GatewayYamlStatus.MISSING;
    }

    public static MscanGatewayYamlEntity missing(ProjectVersionEntity pv, String cacheRelPath) {
        return new MscanGatewayYamlEntity(pv, cacheRelPath);
    }

    public void markReady(GatewayYamlProvidedBy by, String storagePath, String sha256, String originalFilename, JsonNode metadataJson) {
        this.status = GatewayYamlStatus.READY;
        this.providedBy = by;
        this.storagePath = storagePath;
        this.sha256 = sha256;
        this.originalFilename = originalFilename;
        this.metadataJson = metadataJson;
    }

    public void markMissing() {
        this.status = GatewayYamlStatus.MISSING;
        this.providedBy = null;
        this.storagePath = null;
        this.sha256 = null;
        this.originalFilename = null;
    }
}