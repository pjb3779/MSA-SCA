package buaa.msasca.sca.infra.persistence.jpa.entity.result.codeql;

import buaa.msasca.sca.infra.persistence.jpa.entity.base.CreatedEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.CodeqlRunDetailEntity;
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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "codeql_finding",
    indexes = {
        @Index(name = "idx_codeql_finding_run", columnList = "codeql_run_id"),
        @Index(name = "idx_codeql_finding_rule", columnList = "rule_id")
    }
)
@SequenceGenerator(
    name = "codeql_finding_seq_gen",
    sequenceName = "codeql_finding_id_seq",
    allocationSize = 1
)
public class CodeqlFindingEntity extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "codeql_finding_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "codeql_run_id", referencedColumnName = "tool_run_id", nullable = false)
    private CodeqlRunDetailEntity codeqlRun;

    @Column(name = "rule_id", length = 255, nullable = false)
    private String ruleId;

    @Column(columnDefinition = "text", nullable = false)
    private String message;

    @Column(length = 32)
    private String level;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags_json", columnDefinition = "jsonb")
    private JsonNode tagsJson;

    @Column(name = "help_text", columnDefinition = "text")
    private String helpText;

    @Column(name = "primary_file", length = 1024)
    private String primaryFile;

    @Column(name = "primary_line")
    private Integer primaryLine;

    private CodeqlFindingEntity(CodeqlRunDetailEntity run, String ruleId, String message) {
        this.codeqlRun = run;
        this.ruleId = ruleId;
        this.message = message;
    }

    public static CodeqlFindingEntity create(CodeqlRunDetailEntity run, String ruleId, String message) {
        return new CodeqlFindingEntity(run, ruleId, message);
    }

    public void attachPrimary(String file, Integer line) {
        this.primaryFile = file;
        this.primaryLine = line;
    }

    public void attachHelp(String level, JsonNode tagsJson, String helpText) {
        this.level = level;
        this.tagsJson = tagsJson;
        this.helpText = helpText;
    }
}