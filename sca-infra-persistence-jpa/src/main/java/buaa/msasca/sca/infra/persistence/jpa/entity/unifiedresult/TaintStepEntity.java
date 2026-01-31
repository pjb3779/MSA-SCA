package buaa.msasca.sca.infra.persistence.jpa.entity.unifiedresult;

import buaa.msasca.sca.core.domain.enums.RoleType;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.CreatedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "taint_step",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_taint_step_idx", columnNames = {"unified_taint_record_id", "step_index"})
    },
    indexes = { @Index(name = "idx_taint_step_record", columnList = "unified_taint_record_id") }
)
@SequenceGenerator(
    name = "taint_step_seq_gen",
    sequenceName = "taint_step_id_seq",
    allocationSize = 1
)
public class TaintStepEntity extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "taint_step_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unified_taint_record_id", nullable = false)
    private UnifiedTaintRecordEntity record;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_module_id")
    private ServiceModuleEntity serviceModule;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RoleType role;

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(columnDefinition = "text")
    private String description;

    private TaintStepEntity(UnifiedTaintRecordEntity record, int stepIndex) {
        this.record = record;
        this.stepIndex = stepIndex;
    }

    public static TaintStepEntity create(UnifiedTaintRecordEntity record, int stepIndex) {
        return new TaintStepEntity(record, stepIndex);
    }

    public void attach(ServiceModuleEntity sm, RoleType role, String filePath, Integer lineNumber, String description) {
        this.serviceModule = sm;
        this.role = role;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.description = description;
    }
}