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
    name = "codeql_finding_location",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_codeql_location_idx", columnNames = {"finding_id", "location_index"})
    },
    indexes = { @Index(name = "idx_codeql_location_finding", columnList = "finding_id") }
)
@SequenceGenerator(
    name = "codeql_finding_location_seq_gen",
    sequenceName = "codeql_finding_location_id_seq",
    allocationSize = 1
)
public class CodeqlFindingLocationEntity extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "codeql_finding_location_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private CodeqlFindingEntity finding;

    @Column(name = "location_index", nullable = false)
    private int locationIndex;

    @Column(name = "file_path", length = 1024, nullable = false)
    private String filePath;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    private CodeqlFindingLocationEntity(CodeqlFindingEntity finding, int idx, String filePath, int lineNumber) {
        this.finding = finding;
        this.locationIndex = idx;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
    }

    public static CodeqlFindingLocationEntity create(CodeqlFindingEntity finding, int idx, String filePath, int lineNumber) {
        return new CodeqlFindingLocationEntity(finding, idx, filePath, lineNumber);
    }
}