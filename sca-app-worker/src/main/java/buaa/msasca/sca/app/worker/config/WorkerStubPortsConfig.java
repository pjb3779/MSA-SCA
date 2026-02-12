package buaa.msasca.sca.app.worker.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.core.port.out.tool.AgentPort;
import buaa.msasca.sca.core.port.out.tool.CodeqlPort;
import buaa.msasca.sca.core.port.out.tool.MscanPort;

//Test용 미구현 Stub
@Configuration
public class WorkerStubPortsConfig {

    /**
     * CodeQL 미구현 시 부팅/BUILD 검증을 위한 Stub.
     *
     * @return CodeqlPort
     */
    @Bean
    @ConditionalOnMissingBean(CodeqlPort.class)
    public CodeqlPort codeqlPortStub() {
        return new CodeqlPort() {
        @Override
        public CreateDbResult createDatabase(CreateDbRequest req) {
            throw new UnsupportedOperationException("CodeQL not implemented yet");
        }

        @Override
        public RunQueriesResult runQueries(RunQueriesRequest req) {
            throw new UnsupportedOperationException("CodeQL not implemented yet");
        }
        };
    }

    /**
     * Agent 미구현 시 부팅/BUILD 검증을 위한 Stub.
     *
     * @return AgentPort
     */
    @Bean
    @ConditionalOnMissingBean(AgentPort.class)
    public AgentPort agentPortStub() {
        return (toolRunId, projectVersionId, sourceRootPath) -> {
        throw new UnsupportedOperationException("Agent not implemented yet");
        };
    }

    /**
     * MScan 미구현 시 부팅/BUILD 검증을 위한 Stub.
     *
     * @return MscanPort
     */
    @Bean
    @ConditionalOnMissingBean(MscanPort.class)
    public MscanPort mscanPortStub() {
        return (toolRunId, projectVersionId, sourceRootPath) -> {
        throw new UnsupportedOperationException("MScan not implemented yet");
        };
    }
}