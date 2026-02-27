package buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql;

import java.time.Instant;

import buaa.msasca.sca.core.domain.enums.CodeqlSummaryStatus;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "codeql_run_summary",
    indexes = {
        @Index(name = "idx_codeql_summary_service", columnList = "service_module_id"),
        @Index(name = "idx_codeql_summary_status", columnList = "status")
    }
)
public class CodeqlRunSummaryEntity extends AuditedEntity {

    @Id
    @Column(name = "tool_run_id")
    private Long toolRunId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "tool_run_id")
    private ToolRunEntity toolRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_module_id")
    private ServiceModuleEntity serviceModule;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private CodeqlSummaryStatus status;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "sarif_storage_path", length = 1024, nullable = false)
    private String sarifStoragePath;

    @Column(name = "sarif_sha256", length = 64)
    private String sarifSha256;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    private CodeqlRunSummaryEntity(
    ToolRunEntity toolRun,
    ServiceModuleEntity sm,
    CodeqlSummaryStatus status,
    int count,
    String sarifStoragePath,
    String sarifSha256,
    Instant ingestedAt
    ) {
    this.toolRun = toolRun;
    this.serviceModule = sm;
    this.status = status;
    this.resultCount = count;
    this.sarifStoragePath = sarifStoragePath;
    this.sarifSha256 = sarifSha256;
    this.ingestedAt = ingestedAt;
    }

    public static CodeqlRunSummaryEntity create(
    ToolRunEntity toolRun,
    ServiceModuleEntity sm,
    CodeqlSummaryStatus status,
    int count,
    String sarifStoragePath,
    String sarifSha256,
    Instant ingestedAt
    ) {
    return new CodeqlRunSummaryEntity(toolRun, sm, status, count, sarifStoragePath, sarifSha256, ingestedAt);
    }

    public void update(
    CodeqlSummaryStatus status,
    int count,
    String sarifStoragePath,
    String sarifSha256,
    Instant ingestedAt
    ) {
    this.status = status;
    this.resultCount = count;
    this.sarifStoragePath = sarifStoragePath;
    this.sarifSha256 = sarifSha256;
    this.ingestedAt = ingestedAt;
    }
}