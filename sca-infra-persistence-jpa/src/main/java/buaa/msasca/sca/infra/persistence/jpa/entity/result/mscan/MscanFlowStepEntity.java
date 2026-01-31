package buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan;

import buaa.msasca.sca.infra.persistence.jpa.entity.base.CreatedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.EndpointEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
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
    name = "mscan_flow_step",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_mscan_step_idx", columnNames = {"finding_id", "step_index"})
    },
    indexes = { @Index(name = "idx_mscan_step_finding", columnList = "finding_id") }
)
@SequenceGenerator(
    name = "mscan_flow_step_seq_gen",
    sequenceName = "mscan_flow_step_id_seq",
    allocationSize = 1
)
public class MscanFlowStepEntity extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mscan_flow_step_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private MscanFindingEntity finding;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_module_id")
    private ServiceModuleEntity serviceModule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id")
    private EndpointEntity endpoint;

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(columnDefinition = "text")
    private String description;

    private MscanFlowStepEntity(MscanFindingEntity finding, int stepIndex) {
        this.finding = finding;
        this.stepIndex = stepIndex;
    }

    public static MscanFlowStepEntity create(MscanFindingEntity finding, int stepIndex) {
        return new MscanFlowStepEntity(finding, stepIndex);
    }

    public void attachRefs(ServiceModuleEntity sm, EndpointEntity ep) {
        this.serviceModule = sm;
        this.endpoint = ep;
    }

    public void attachLocation(String filePath, Integer lineNumber, String description) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.description = description;
    }
}