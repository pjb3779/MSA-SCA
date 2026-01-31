package buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail;

import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "codeql_run_detail",
    indexes = { @Index(name = "idx_codeql_detail_service_module", columnList = "service_module_id") }
)
public class CodeqlRunDetailEntity {

    @Id
    @Column(name = "tool_run_id")
    private Long toolRunId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "tool_run_id")
    private ToolRunEntity toolRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_module_id")
    private ServiceModuleEntity serviceModule;

    private CodeqlRunDetailEntity(ToolRunEntity toolRun, ServiceModuleEntity serviceModule) {
        this.toolRun = toolRun;
        this.serviceModule = serviceModule;
    }

    public static CodeqlRunDetailEntity create(ToolRunEntity toolRun, ServiceModuleEntity serviceModule) {
        return new CodeqlRunDetailEntity(toolRun, serviceModule);
    }
}
