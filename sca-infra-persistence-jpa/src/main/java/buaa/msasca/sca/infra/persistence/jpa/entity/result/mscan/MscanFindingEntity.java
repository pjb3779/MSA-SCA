package buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan;

import buaa.msasca.sca.infra.persistence.jpa.entity.base.CreatedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.MscanRunDetailEntity;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "mscan_finding",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_mscan_flow_idx", columnNames = {"mscan_run_id", "flow_index"})
    },
    indexes = {
        @Index(name = "idx_mscan_finding_run", columnList = "mscan_run_id"),
        @Index(name = "idx_mscan_finding_vul", columnList = "vul_id")
    }
)
@SequenceGenerator(
    name = "mscan_finding_seq_gen",
    sequenceName = "mscan_finding_id_seq",
    allocationSize = 1
)
public class MscanFindingEntity extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mscan_finding_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mscan_run_id", referencedColumnName = "tool_run_id", nullable = false)
    private MscanRunDetailEntity mscanRun;

    @Column(name = "flow_index", nullable = false)
    private int flowIndex;

    @Column(name = "source_signature", columnDefinition = "text", nullable = false)
    private String sourceSignature;

    @Column(name = "sink_signature", columnDefinition = "text", nullable = false)
    private String sinkSignature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_service_id")
    private ServiceModuleEntity sourceService;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sink_service_id")
    private ServiceModuleEntity sinkService;

    @Column(name = "sink_file_path", length = 1024)
    private String sinkFilePath;

    @Column(name = "sink_line")
    private Integer sinkLine;

    @Column(name = "sink_basic_block")
    private Integer sinkBasicBlock;

    @Column(name = "sink_call_kind", length = 64)
    private String sinkCallKind;

    @Column(name = "sink_call_target", columnDefinition = "text")
    private String sinkCallTarget;

    @Column(name = "vul_id", length = 255, nullable = false)
    private String vulId;

    @Column(name = "raw_flow_text", columnDefinition = "text", nullable = false)
    private String rawFlowText;

    private MscanFindingEntity(MscanRunDetailEntity run, int flowIndex, String sourceSig, String sinkSig, String vulId, String rawFlowText) {
        this.mscanRun = run;
        this.flowIndex = flowIndex;
        this.sourceSignature = sourceSig;
        this.sinkSignature = sinkSig;
        this.vulId = vulId;
        this.rawFlowText = rawFlowText;
    }

    public static MscanFindingEntity create(MscanRunDetailEntity run, int flowIndex, String sourceSig, String sinkSig, String vulId, String rawFlowText) {
        return new MscanFindingEntity(run, flowIndex, sourceSig, sinkSig, vulId, rawFlowText);
    }

    public void attachSinkMeta(String filePath, Integer line, Integer basicBlock, String callKind, String callTarget) {
        this.sinkFilePath = filePath;
        this.sinkLine = line;
        this.sinkBasicBlock = basicBlock;
        this.sinkCallKind = callKind;
        this.sinkCallTarget = callTarget;
    }

    public void attachServices(ServiceModuleEntity source, ServiceModuleEntity sink) {
        this.sourceService = source;
        this.sinkService = sink;
    }
}