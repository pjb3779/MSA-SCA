package buaa.msasca.sca.infra.persistence.jpa.entity.result.sanitizer;

import buaa.msasca.sca.core.domain.enums.EvidenceType;
import buaa.msasca.sca.core.domain.enums.ToolType;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "sanitizer_evidence",
    indexes = {
        @Index(name = "idx_sanitizer_evidence_candidate", columnList = "candidate_id"),
        @Index(name = "idx_sanitizer_evidence_type", columnList = "evidence_type")
    }
)
@SequenceGenerator(
    name = "sanitizer_evidence_seq_gen",
    sequenceName = "sanitizer_evidence_id_seq",
    allocationSize = 1
)
public class SanitizerEvidenceEntity extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sanitizer_evidence_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private SanitizerCandidateEntity candidate;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", length = 32, nullable = false)
    private EvidenceType evidenceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_tool", length = 32)
    private ToolType sourceTool;

    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "source_ref_table", length = 64)
    private String sourceRefTable;

    @Column(name = "snippet_text", columnDefinition = "text")
    private String snippetText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private JsonNode metadataJson;

    private SanitizerEvidenceEntity(SanitizerCandidateEntity candidate, EvidenceType type) {
        this.candidate = candidate;
        this.evidenceType = type;
    }

    public static SanitizerEvidenceEntity create(SanitizerCandidateEntity candidate, EvidenceType type) {
        return new SanitizerEvidenceEntity(candidate, type);
    }

    public void attachSource(ToolType toolType, Long refId, String refTable) {
        this.sourceTool = toolType;
        this.sourceRefId = refId;
        this.sourceRefTable = refTable;
    }

    public void attachSnippet(String snippetText, JsonNode metadata) {
        this.snippetText = snippetText;
        this.metadataJson = metadata;
    }
}