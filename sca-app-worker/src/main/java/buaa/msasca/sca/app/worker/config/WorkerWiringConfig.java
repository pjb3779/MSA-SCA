package buaa.msasca.sca.app.worker.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.app.worker.build.FileBasedBuildImageResolver;
import buaa.msasca.sca.app.worker.config.props.BuildImagesProperties;
import buaa.msasca.sca.app.worker.tool.FileSystemServiceModuleScannerAdapter;
import buaa.msasca.sca.core.application.pipeline.PipelineExecutor;
import buaa.msasca.sca.core.port.out.persistence.AnalysisArtifactPort;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.persistence.ServiceModuleCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ServiceModulePort;
import buaa.msasca.sca.core.port.out.persistence.ToolRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ToolRunPort;

import buaa.msasca.sca.core.port.out.tool.AgentPort;
import buaa.msasca.sca.core.port.out.tool.BuildImageResolver;
import buaa.msasca.sca.core.port.out.tool.BuildPort;
import buaa.msasca.sca.core.port.out.tool.CodeqlPort;
import buaa.msasca.sca.core.port.out.tool.DockerImagePort;
import buaa.msasca.sca.core.port.out.tool.MscanPort;
import buaa.msasca.sca.core.port.out.tool.ServiceModuleScannerPort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;

/**
 * Worker Wiring
 * - PipelineExecutor를 구성한다.
 * - ToolPort(Runner/Storage/CodeQL/Agent/MScan)는 별도의 ToolWiringConfig에서 제공하는 것을 권장한다.
 */
@Configuration
@EnableConfigurationProperties(BuildImagesProperties.class)
public class WorkerWiringConfig {

    /**
     * 파일 기반 jdk 감지 리졸버 등록
     * @param props 설정값
     * @return buildImageResolver
     */
    @Bean
    public BuildImageResolver buildImageResolver(BuildImagesProperties props) {
        List<FileBasedBuildImageResolver.Rule> rules =
            (props.rules() == null)
                ? List.of()
                : props.rules().stream()
                    .map(r -> new FileBasedBuildImageResolver.Rule(r.buildTool(), r.jdkVersion(), r.image()))
                    .toList();

        return new FileBasedBuildImageResolver(props.defaultImage(), rules);
    }

    /**
     * 파일시스템 기반 ServiceModuleScannerPort 빈을 등록한다.
     *
     * @return ServiceModuleScannerPort
     */
    @Bean
    public ServiceModuleScannerPort serviceModuleScannerPort() {
        return new FileSystemServiceModuleScannerAdapter();
    }
    /**
     * 
     * PipelineExecutor를 구성한다.
     *
     * @return PipelineExecutor
     */
    @Bean
    public PipelineExecutor pipelineExecutor(
        AnalysisRunCommandPort analysisRunCommandPort,
        ServiceModulePort serviceModulePort,
        ProjectVersionSourceCachePort sourceCachePort,
        ToolRunCommandPort toolRunCommandPort,
        ToolRunPort toolRunPort,
        AnalysisArtifactPort artifactPort,
        BuildPort buildPort,
        BuildImageResolver buildImageResolver,
        DockerImagePort dockerImagePort,
        StoragePort storagePort,
        CodeqlPort codeqlPort,
        AgentPort agentPort,
        MscanPort mscanPort,
        ServiceModuleScannerPort serviceModuleScannerPort,
        ServiceModuleCommandPort serviceModuleCommandPort
    ) {
        return new PipelineExecutor(
            analysisRunCommandPort,
            serviceModulePort,
            sourceCachePort,
            toolRunCommandPort,
            toolRunPort,
            artifactPort,
            storagePort,
            buildPort,
            buildImageResolver,
            dockerImagePort,
            codeqlPort,
            agentPort,
            mscanPort,
            serviceModuleScannerPort,
            serviceModuleCommandPort
        );
    }
}