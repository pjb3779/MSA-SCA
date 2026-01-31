package buaa.msasca.sca.infra.persistence.jpa.entity.run;

import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.domain.enums.ToolType;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "tool_run",
    indexes = {
        @Index(name = "idx_tool_run_analysis_run_id", columnList = "analysis_run_id"),
        @Index(name = "idx_tool_run_tool_type", columnList = "tool_type")
    }
)
@SequenceGenerator(
    name = "tool_run_seq_gen",
    sequenceName = "tool_run_id_seq",
    allocationSize = 1
)
public class ToolRunEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tool_run_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRunEntity analysisRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_type", length = 32, nullable = false)
    private ToolType toolType;

    @Column(name = "tool_version", length = 64)
    private String toolVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", columnDefinition = "jsonb")
    private JsonNode configJson;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private RunStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    private ToolRunEntity(AnalysisRunEntity analysisRun, ToolType toolType, String toolVersion, JsonNode configJson) {
        this.analysisRun = analysisRun;
        this.toolType = toolType;
        this.toolVersion = toolVersion;
        this.configJson = configJson;
        this.status = RunStatus.PENDING;
    }

    public static ToolRunEntity create(AnalysisRunEntity run, ToolType type, String toolVersion, JsonNode configJson) {
        return new ToolRunEntity(run, type, toolVersion, configJson);
    }

    public void start() { this.status = RunStatus.RUNNING; this.startedAt = Instant.now(); }
    public void done() { this.status = RunStatus.DONE; this.finishedAt = Instant.now(); }

    public void fail(String errorMessage) {
        this.status = RunStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }
}
