package buaa.msasca.sca.infra.persistence.jpa.entity.unifiedresult;

import buaa.msasca.sca.core.domain.enums.Severity;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql.CodeqlFindingEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanFindingEntity;
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
    name = "unified_taint_record",
    indexes = {
        @Index(name = "idx_unified_codeql", columnList = "codeql_finding_id"),
        @Index(name = "idx_unified_mscan", columnList = "mscan_finding_id"),
        @Index(name = "idx_unified_severity", columnList = "severity")
    }
)
@SequenceGenerator(
    name = "unified_taint_record_seq_gen",
    sequenceName = "unified_taint_record_id_seq",
    allocationSize = 1
)
public class UnifiedTaintRecordEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "unified_taint_record_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codeql_finding_id")
    private CodeqlFindingEntity codeqlFinding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mscan_finding_id")
    private MscanFindingEntity mscanFinding;

    @Column(name = "vulnerability_type", length = 255, nullable = false)
    private String vulnerabilityType;

    @Column(length = 255, nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Severity severity;

    @Column(name = "source_file_path", length = 1024)
    private String sourceFilePath;

    @Column(name = "source_line")
    private Integer sourceLine;

    @Column(name = "sink_file_path", length = 1024)
    private String sinkFilePath;

    @Column(name = "sink_line")
    private Integer sinkLine;

    private UnifiedTaintRecordEntity(String vulnerabilityType, String title) {
        this.vulnerabilityType = vulnerabilityType;
        this.title = title;
    }

    public static UnifiedTaintRecordEntity create(String vulnerabilityType, String title) {
        return new UnifiedTaintRecordEntity(vulnerabilityType, title);
    }

    public void link(CodeqlFindingEntity codeql, MscanFindingEntity mscan) {
        this.codeqlFinding = codeql;
        this.mscanFinding = mscan;
    }

    public void describe(String description, Severity severity) {
        this.description = description;
        this.severity = severity;
    }

    public void setEndpoints(String srcFile, Integer srcLine, String sinkFile, Integer sinkLine) {
        this.sourceFilePath = srcFile;
        this.sourceLine = srcLine;
        this.sinkFilePath = sinkFile;
        this.sinkLine = sinkLine;
    }
}