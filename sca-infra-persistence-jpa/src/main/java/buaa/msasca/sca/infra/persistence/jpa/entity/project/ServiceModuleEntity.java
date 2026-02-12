package buaa.msasca.sca.infra.persistence.jpa.entity.project;

import buaa.msasca.sca.core.domain.enums.BuildTool;
import buaa.msasca.sca.infra.persistence.jpa.entity.base.AuditedEntity;
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
    name = "service_module",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_service_module_version_name", columnNames = {"project_version_id", "name"})
    },
    indexes = {
        @Index(name = "idx_service_module_pv", columnList = "project_version_id"),
        @Index(name = "idx_service_module_build_tool", columnList = "build_tool")
    }
)
@SequenceGenerator(
    name = "service_module_seq_gen",
    sequenceName = "service_module_id_seq",
    allocationSize = 1
)
public class ServiceModuleEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "service_module_seq_gen")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_version_id", nullable = false)
    private ProjectVersionEntity projectVersion;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(name = "root_path", length = 1024, nullable = false)
    private String rootPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "build_tool", length = 32, nullable = false)
    private BuildTool buildTool;

    @Column(name = "jdk_version", length = 16)
    private String jdkVersion;

    @Column(name = "is_gateway", nullable = false)
    private boolean gateway;

    private ServiceModuleEntity(ProjectVersionEntity pv, String name, String rootPath, BuildTool buildTool, String jdkVersion, boolean gateway) {
        this.projectVersion = pv;
        this.name = name;
        this.rootPath = rootPath;
        this.buildTool = buildTool;
        this.jdkVersion = jdkVersion;
        this.gateway = gateway;
    }

    public static ServiceModuleEntity create(ProjectVersionEntity pv, String name, String rootPath, BuildTool buildTool, String jdkVersion, boolean gateway) {
        return new ServiceModuleEntity(pv, name, rootPath, buildTool, jdkVersion, gateway);
    }

    public void changeRootPath(String newRootPath) {
        this.rootPath = newRootPath;
    }
    public void changeBuildTool(buaa.msasca.sca.core.domain.enums.BuildTool newBuildTool) {
        this.buildTool = newBuildTool;
    }
    public void changeJdkVersion(String newJdkVersion) {
        this.jdkVersion = newJdkVersion;
    }

    public void changeGateway(boolean gateway) {
        this.gateway = gateway;
    }
}