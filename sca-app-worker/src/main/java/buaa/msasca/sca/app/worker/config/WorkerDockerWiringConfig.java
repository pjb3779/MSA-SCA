package buaa.msasca.sca.app.worker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.app.worker.config.props.WorkerWorkspaceProperties;
import buaa.msasca.sca.app.worker.tool.docker.DockerImagePortAdapter;
import buaa.msasca.sca.core.port.out.tool.DockerImagePort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;

@Configuration
@EnableConfigurationProperties(WorkerWorkspaceProperties.class)
public class WorkerDockerWiringConfig {

    @Bean
    public DockerImagePort dockerImagePort(RunnerPort runnerPort, WorkerWorkspaceProperties props) {
        return new DockerImagePortAdapter(runnerPort, props.basePath());
    }
}