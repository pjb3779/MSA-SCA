package buaa.msasca.sca.app.worker.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.core.port.out.tool.BuildPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;
import buaa.msasca.sca.infra.runner.LocalProcessRunnerPortAdapter;
import buaa.msasca.sca.infra.runner.build.DockerBuildPortAdapter;
import buaa.msasca.sca.infra.storage.local.LocalStoragePortAdapter;

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
     * 운영 스토리지 구현체가 생기면 해당 Bean으로 교체할것!!!!!
     *
     * @return StoragePort
     */
    @Bean
    @ConditionalOnMissingBean(StoragePort.class)
    public StoragePort storagePort() {
        return new LocalStoragePortAdapter();
    }
}