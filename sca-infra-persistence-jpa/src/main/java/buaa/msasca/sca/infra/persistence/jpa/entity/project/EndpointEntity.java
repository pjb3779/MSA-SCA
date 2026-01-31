package buaa.msasca.sca.infra.persistence.jpa.entity.project;

import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
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
    name = "endpoint",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_endpoint_unique",
            columnNames = {"service_module_id", "http_method", "path_pattern"}
        )
    },
    indexes = { @Index(name = "idx_endpoint_service_module_id", columnList = "service_module_id") }
)
@SequenceGenerator(
    name = "endpoint_seq_gen",
    sequenceName = "endpoint_id_seq",
    allocationSize = 1
)
public class EndpointEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "endpoint_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_module_id", nullable = false)
    private ServiceModuleEntity serviceModule;

    @Column(name = "http_method", length = 16)
    private String httpMethod;

    @Column(name = "path_pattern", length = 1024)
    private String pathPattern;

    @Column(name = "handler_signature", columnDefinition = "text")
    private String handlerSignature;

    @Column(name = "operation_id", length = 255)
    private String operationId;

    private EndpointEntity(ServiceModuleEntity sm, String httpMethod, String pathPattern, String handlerSignature, String operationId) {
        this.serviceModule = sm;
        this.httpMethod = httpMethod;
        this.pathPattern = pathPattern;
        this.handlerSignature = handlerSignature;
        this.operationId = operationId;
    }

    public static EndpointEntity create(ServiceModuleEntity sm, String httpMethod, String pathPattern, String handlerSignature, String operationId) {
        return new EndpointEntity(sm, httpMethod, pathPattern, handlerSignature, operationId);
    }
}
