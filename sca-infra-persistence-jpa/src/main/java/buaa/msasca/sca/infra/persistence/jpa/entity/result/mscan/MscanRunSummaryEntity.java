package buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan;

import java.time.Instant;

import buaa.msasca.sca.core.domain.enums.MscanSummaryStatus;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.MscanRunDetailEntity;
import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "mscan_run_summary",
    indexes = {
        @Index(name = "idx_mscan_run_summary_status", columnList = "status")
    }
)
public class MscanRunSummaryEntity extends AuditedEntity {

    @Id
    @Column(name = "tool_run_id")
    private Long toolRunId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "tool_run_id", referencedColumnName = "tool_run_id")
    private MscanRunDetailEntity mscanRun;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private MscanSummaryStatus status;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "report_storage_path", length = 1024)
    private String reportStoragePath;

    @Column(name = "report_sha256", length = 64)
    private String reportSha256;

    @Column(name = "ingested_at")
    private Instant ingestedAt;

    private MscanRunSummaryEntity(MscanRunDetailEntity run) {
        this.mscanRun = run;
        this.status = MscanSummaryStatus.INGEST_FAILED;
        this.resultCount = 0;
    }

    public static MscanRunSummaryEntity create(MscanRunDetailEntity run) {
        return new MscanRunSummaryEntity(run);
    }

    public void update(MscanSummaryStatus status, int resultCount, String reportStoragePath, String reportSha256, Instant ingestedAt) {
        this.status = status;
        this.resultCount = resultCount;
        this.reportStoragePath = reportStoragePath;
        this.reportSha256 = reportSha256;
        this.ingestedAt = ingestedAt;
    }
}