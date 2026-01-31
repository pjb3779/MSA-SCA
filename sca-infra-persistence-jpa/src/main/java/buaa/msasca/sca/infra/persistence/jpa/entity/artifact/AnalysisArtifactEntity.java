package buaa.msasca.sca.infra.persistence.jpa.entity.artifact;

import buaa.msasca.sca.core.domain.enums.ArtifactType;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.CreatedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;
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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "analysis_artifact",
    indexes = {
        @Index(name = "idx_artifact_tool_run", columnList = "tool_run_id"),
        @Index(name = "idx_artifact_type", columnList = "artifact_type")
    }
)
@SequenceGenerator(
    name = "analysis_artifact_seq_gen",
    sequenceName = "analysis_artifact_id_seq",
    allocationSize = 1
)
public class AnalysisArtifactEntity extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_artifact_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tool_run_id", nullable = false)
    private ToolRunEntity toolRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", length = 32, nullable = false)
    private ArtifactType artifactType;

    @Column(name = "storage_path", length = 1024, nullable = false)
    private String storagePath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private JsonNode metadataJson;

    private AnalysisArtifactEntity(ToolRunEntity toolRun, ArtifactType artifactType, String storagePath, JsonNode metadataJson) {
        this.toolRun = toolRun;
        this.artifactType = artifactType;
        this.storagePath = storagePath;
        this.metadataJson = metadataJson;
    }

    public static AnalysisArtifactEntity create(ToolRunEntity toolRun, ArtifactType type, String storagePath, JsonNode metadata) {
        return new AnalysisArtifactEntity(toolRun, type, storagePath, metadata);
    }
}