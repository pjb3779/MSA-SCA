package buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql;

import buaa.msasca.sca.infra.persistence.jpa.entity.base.CreatedEntity;
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
    name = "codeql_flow_step",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_codeql_flow_step_idx", columnNames = {"flow_id", "step_index"})
    },
    indexes = { @Index(name = "idx_codeql_flow_step_flow", columnList = "flow_id") }
)
@SequenceGenerator(
    name = "codeql_flow_step_seq_gen",
    sequenceName = "codeql_flow_step_id_seq",
    allocationSize = 1
)
public class CodeqlFlowStepEntity extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "codeql_flow_step_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_id", nullable = false)
    private CodeqlFlowEntity flow;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(length = 255)
    private String label;

    private CodeqlFlowStepEntity(CodeqlFlowEntity flow, int stepIndex) {
        this.flow = flow;
        this.stepIndex = stepIndex;
    }

    public static CodeqlFlowStepEntity create(CodeqlFlowEntity flow, int stepIndex) {
        return new CodeqlFlowStepEntity(flow, stepIndex);
    }

    public void attachLocation(String filePath, Integer lineNumber, String label) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.label = label;
    }
}