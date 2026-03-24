package buaa.msasca.sca.app.worker.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.app.worker.config.props.ToolMscanProperties;
import buaa.msasca.sca.app.worker.config.props.ToolLlmProperties;
import buaa.msasca.sca.app.worker.config.props.WorkerWorkspaceProperties;
import buaa.msasca.sca.core.port.out.persistence.CodeqlFindingPort;
import buaa.msasca.sca.core.port.out.tool.AgentPort;
import buaa.msasca.sca.core.port.out.tool.BuildPort;
import buaa.msasca.sca.core.port.out.tool.CodeqlPort;
import buaa.msasca.sca.core.port.out.tool.MscanPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;
import buaa.msasca.sca.infra.runner.LocalProcessRunnerPortAdapter;
import buaa.msasca.sca.infra.runner.build.DockerBuildPortAdapter;
import buaa.msasca.sca.infra.storage.local.LocalStoragePortAdapter;
import buaa.msasca.sca.tool.agent.AgentPortAdapter;
import buaa.msasca.sca.tool.codeql.DockerCodeqlPortAdapter;
import buaa.msasca.sca.tool.mscan.DockerMscanPortAdapter;

@Configuration
public class ToolWiringConfig {

    @Bean
    public RunnerPort runnerPort() {
        return new LocalProcessRunnerPortAdapter();
    }

    @Bean
    public BuildPort buildPort(RunnerPort runnerPort) {
        return new DockerBuildPortAdapter(runnerPort);
    }

    /**
     * 스토리지 Port 기본 구현을 등록
     * todo: 운영 스토리지 구현체가 생기면 해당 Bean으로 교체할것!!!!!
     *
     * @return StoragePort
     */
    @Bean
    @ConditionalOnMissingBean(StoragePort.class)
    public StoragePort storagePort() {
        return new LocalStoragePortAdapter();
    }

    @Bean
    public CodeqlPort codeqlPort(
        RunnerPort runnerPort,
        WorkerWorkspaceProperties workspaceProps
    ) {
        String basePath = workspaceProps.basePath();
        return new DockerCodeqlPortAdapter(
            runnerPort,
            basePath
        );
    }

    @Bean
    public MscanPort mscanPort(RunnerPort runnerPort, ToolMscanProperties mscanProps) {
        String mem = mscanProps.hasDockerMemoryLimit() ? mscanProps.dockerMemory() : null;
        return new DockerMscanPortAdapter(runnerPort, mem);
    }

    /** Agent 포트: CodeqlFindingPort 주입하여 SARIF 연동 Sanitizer 파이프라인 구성 */
    @Bean
    public AgentPort agentPort(ToolLlmProperties llmProps, CodeqlFindingPort codeqlFindingPort) {
        return new AgentPortAdapter(
            codeqlFindingPort,
            llmProps.openAiApiKey(),
            llmProps.openAiBaseUrl(),
            llmProps.openAiModel()
        );
    }
}