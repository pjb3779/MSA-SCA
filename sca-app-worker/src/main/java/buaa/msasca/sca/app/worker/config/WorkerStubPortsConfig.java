package buaa.msasca.sca.app.worker.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.core.port.out.tool.AgentPort;
import buaa.msasca.sca.core.port.out.tool.CodeqlPort;

/**
 * 테스트·부팅 검증용 미구현 Port Stub 설정.
 *
 * <p>실제 persistence/tool 구현이 없을 때 빈/예외 stub을 제공하여 부팅·빌드가 가능하도록 한다.</p>
 * <p>CodeqlFindingPort는 PersistenceWiringConfig의 codeqlFindingAdapter로 항상 제공된다.</p>
 */
@Configuration
public class WorkerStubPortsConfig {

    /**
     * CodeQL 미구현 시 부팅·빌드 검증을 위한 Stub.
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
     * Agent 미구현 시 부팅·빌드 검증을 위한 Stub.
     */
    @Bean
    @ConditionalOnMissingBean(AgentPort.class)
    public AgentPort agentPortStub() {
        return new AgentPort() {
            @Override
            public java.util.List<buaa.msasca.sca.core.domain.model.ServiceModule> prefilterModules(
                Long toolRunId,
                Long projectVersionId,
                String sourcePath,
                java.util.List<buaa.msasca.sca.core.domain.model.ServiceModule> modules
            ) {
                return modules;
            }

            @Override
            public java.util.List<PrefilterDecision> prefilterDecisions(
                Long toolRunId,
                Long projectVersionId,
                String sourcePath,
                java.util.List<buaa.msasca.sca.core.domain.model.ServiceModule> modules
            ) {
                java.util.List<PrefilterDecision> out = new java.util.ArrayList<>();
                for (var m : modules) {
                    out.add(new PrefilterDecision(m.id(), true, "STUB_DEFAULT_SELECTED"));
                }
                return out;
            }

            @Override
            public AgentKnowledge buildKnowledge(
                Long toolRunId,
                Long projectVersionId,
                Long analysisRunId,
                String sourcePath,
                String gatewayYamlPathOnHost
            ) {
                return new AgentKnowledge(null, null, null, "stub", java.util.List.of());
            }
        };
    }
}