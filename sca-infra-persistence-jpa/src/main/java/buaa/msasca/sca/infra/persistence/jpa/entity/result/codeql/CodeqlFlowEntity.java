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
    name = "codeql_flow",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_codeql_flow_idx", columnNames = {"finding_id", "flow_index"})
    },
    indexes = { @Index(name = "idx_codeql_flow_finding", columnList = "finding_id") }
)
@SequenceGenerator(
    name = "codeql_flow_seq_gen",
    sequenceName = "codeql_flow_id_seq",
    allocationSize = 1
)
public class CodeqlFlowEntity extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "codeql_flow_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private CodeqlFindingEntity finding;

    @Column(name = "flow_index", nullable = false)
    private int flowIndex;

    private CodeqlFlowEntity(CodeqlFindingEntity finding, int flowIndex) {
        this.finding = finding;
        this.flowIndex = flowIndex;
    }

    public static CodeqlFlowEntity create(CodeqlFindingEntity finding, int flowIndex) {
        return new CodeqlFlowEntity(finding, flowIndex);
    }
}