package buaa.msasca.sca.infra.persistence.jpa.entity.run;

import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;

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
    name = "analysis_run",
    indexes = {
        @Index(name = "idx_analysis_run_pv", columnList = "project_version_id"),
        @Index(name = "idx_analysis_run_status", columnList = "status")
    }
)
@SequenceGenerator(
    name = "analysis_run_seq_gen",
    sequenceName = "analysis_run_id_seq",
    allocationSize = 1
)
public class AnalysisRunEntity extends AuditedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_run_seq_gen")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_version_id", nullable = false)
  private ProjectVersionEntity projectVersion;

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

  @Column(name = "triggered_by", length = 255)
  private String triggeredBy;

  private AnalysisRunEntity(ProjectVersionEntity pv, JsonNode configJson, String triggeredBy) {
    this.projectVersion = pv;
    this.configJson = configJson;
    this.triggeredBy = triggeredBy;
    this.status = RunStatus.PENDING;
  }

  public static AnalysisRunEntity create(ProjectVersionEntity pv, JsonNode configJson, String triggeredBy) {
    return new AnalysisRunEntity(pv, configJson, triggeredBy);
  }

  public void start() {
    this.status = RunStatus.RUNNING;
    this.startedAt = Instant.now();
  }

  public void finishSuccess() {
    this.status = RunStatus.DONE;
    this.finishedAt = Instant.now();
  }

  public void finishFail() {
    this.status = RunStatus.FAILED;
    this.finishedAt = Instant.now();
  }
}
