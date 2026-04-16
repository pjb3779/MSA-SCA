package buaa.msasca.sca.infra.persistence.jpa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaAnalysisRunAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaArtifactAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaCodeqlFindingAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaCodeqlResultAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaMscanGatewayYamlAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaMscanFindingQueryAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaMscanResultAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaMscanRunSummaryAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectVersionAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectVersionSourceCacheAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaSanitizerResultAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaServiceModuleAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaServiceModuleCommandAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaToolRunAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaUnifiedTaintRecordAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaUnifiedResultQueryAdapter;

import buaa.msasca.sca.infra.persistence.jpa.mapper.AnalysisRunMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ArtifactMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.MscanGatewayYamlMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.MscanRunSummaryMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectVersionViewMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ServiceModuleMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.SourceCacheMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ToolRunMapper;

import buaa.msasca.sca.infra.persistence.jpa.repository.AgentRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisArtifactJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.BuildRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.MscanRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.SanitizerCandidateJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.SanitizerRegistryJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionSourceCacheJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ServiceModuleJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ToolRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.TaintStepJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.UnifiedTaintRecordJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.Mscan.MscanGatewayYamlJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.Mscan.MscanRunSummaryJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.Mscan.MscanFindingJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFindingJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFindingLocationJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFlowJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlFlowStepJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.codeQl.CodeqlRunSummaryJpaRepository;

/**
 * Persistence Wiring
 * - JPA Repository + Mapper + Adapter 조립
 * - Adapter가 core Port/CommandPort를 직접 구현하는 전제에서,
 *   별도 Port 래핑 빈을 만들지 않는다(중복 빈 방지).
 */
@Configuration
public class PersistenceWiringConfig {

    // =========================================================
    // MAPPERS
    // =========================================================
    @Bean public ProjectMapper projectMapper() { return new ProjectMapper(); }
    @Bean public ProjectVersionViewMapper projectVersionViewMapper() { return new ProjectVersionViewMapper(); }
    @Bean public SourceCacheMapper sourceCacheMapper() { return new SourceCacheMapper(); }
    @Bean public AnalysisRunMapper analysisRunMapper() { return new AnalysisRunMapper(); }
    @Bean public ServiceModuleMapper serviceModuleMapper() { return new ServiceModuleMapper(); }
    @Bean public ToolRunMapper toolRunMapper() { return new ToolRunMapper(); }
    @Bean public ArtifactMapper artifactMapper() { return new ArtifactMapper(); }

    // =========================================================
    // ADAPTER BEANS
    // =========================================================

    // ---- Project ----
    @Bean
    public JpaProjectAdapter projectAdapter(ProjectJpaRepository repo, ProjectMapper mapper) {
        return new JpaProjectAdapter(repo, mapper);
    }

    // ---- ProjectVersion ----
    @Bean
    public JpaProjectVersionAdapter projectVersionAdapter(
        ProjectVersionJpaRepository repo,
        ProjectJpaRepository projectRepo,
        ProjectVersionViewMapper mapper
    ) {
        return new JpaProjectVersionAdapter(repo, projectRepo, mapper);
    }

    // ---- ProjectVersionSourceCache ----
    @Bean
    public JpaProjectVersionSourceCacheAdapter sourceCacheAdapter(
        ProjectVersionSourceCacheJpaRepository repo,
        ProjectVersionJpaRepository pvRepo,
        SourceCacheMapper mapper
    ) {
        return new JpaProjectVersionSourceCacheAdapter(repo, pvRepo, mapper);
    }

    // ---- AnalysisRun ----
    @Bean
    public JpaAnalysisRunAdapter analysisRunAdapter(
        AnalysisRunJpaRepository runRepo,
        ProjectVersionJpaRepository pvRepo,
        AnalysisRunMapper mapper
    ) {
        return new JpaAnalysisRunAdapter(runRepo, pvRepo, mapper);
    }

    // ---- ServiceModule (READ) ----
    @Bean
    public JpaServiceModuleAdapter serviceModuleAdapter(ServiceModuleJpaRepository repo, ServiceModuleMapper mapper) {
        return new JpaServiceModuleAdapter(repo, mapper);
    }

    // ---- ServiceModule (COMMAND) ----
    @Bean
    public JpaServiceModuleCommandAdapter serviceModuleCommandAdapter(
        ServiceModuleJpaRepository repo,
        ProjectVersionJpaRepository pvRepo,
        ServiceModuleMapper mapper
    ) {
        return new JpaServiceModuleCommandAdapter(repo, pvRepo, mapper);
    }

    // ---- ToolRun ----
    @Bean
    public JpaToolRunAdapter toolRunAdapter(
        ToolRunJpaRepository toolRunRepo,
        AnalysisRunJpaRepository analysisRunRepo,
        ServiceModuleJpaRepository serviceModuleRepo,
        BuildRunDetailJpaRepository buildDetailRepo,
        CodeqlRunDetailJpaRepository codeqlDetailRepo,
        AgentRunDetailJpaRepository agentDetailRepo,
        MscanRunDetailJpaRepository mscanDetailRepo,
        ToolRunMapper mapper
    ) {
        return new JpaToolRunAdapter(
            toolRunRepo,
            analysisRunRepo,
            serviceModuleRepo,
            buildDetailRepo,
            codeqlDetailRepo,
            agentDetailRepo,
            mscanDetailRepo,
            mapper
        );
    }

    // ---- AnalysisArtifact ----
    @Bean
    public JpaArtifactAdapter analysisArtifactAdapter(
        AnalysisArtifactJpaRepository repo,
        ToolRunJpaRepository toolRunRepo,
        ArtifactMapper mapper
    ) {
        return new JpaArtifactAdapter(repo, toolRunRepo, mapper);
    }

    // ---- CodeQL Results ----
    @Bean
    public JpaCodeqlResultAdapter codeqlResultAdapter(
        ToolRunJpaRepository toolRunRepo,
        ServiceModuleJpaRepository serviceModuleRepo,
        CodeqlRunDetailJpaRepository codeqlDetailRepo,
        CodeqlRunSummaryJpaRepository summaryRepo,
        CodeqlFindingJpaRepository findingRepo,
        CodeqlFindingLocationJpaRepository locationRepo,
        CodeqlFlowJpaRepository flowRepo,
        CodeqlFlowStepJpaRepository stepRepo
    ) {
        return new JpaCodeqlResultAdapter(
            toolRunRepo,
            serviceModuleRepo,
            codeqlDetailRepo,
            summaryRepo,
            findingRepo,
            locationRepo,
            flowRepo,
            stepRepo
        );
    }

    // ---- Mapper  ----
    @Bean
    public MscanGatewayYamlMapper mscanGatewayYamlMapper() {
        return new MscanGatewayYamlMapper();
    }

    @Bean
    public MscanRunSummaryMapper mscanRunSummaryMapper() {
        return new MscanRunSummaryMapper();
    }

    // Adapter Bean
    @Bean
    public JpaMscanGatewayYamlAdapter mscanGatewayYamlAdapter(
        MscanGatewayYamlJpaRepository repo,
        ProjectVersionJpaRepository pvRepo,
        MscanGatewayYamlMapper mapper
    ) {
    return new JpaMscanGatewayYamlAdapter(repo, pvRepo, mapper);
    }

    @Bean
    public JpaMscanRunSummaryAdapter mscanRunSummaryAdapter(
        MscanRunSummaryJpaRepository repo,
        MscanRunDetailJpaRepository detailRepo,
        MscanRunSummaryMapper mapper
    ) {
        return new JpaMscanRunSummaryAdapter(repo, detailRepo, mapper);
    }

    @Bean
    public JpaMscanResultAdapter mscanResultAdapter(
        MscanRunDetailJpaRepository runDetailRepo,
        MscanFindingJpaRepository findingRepo
    ) {
        return new JpaMscanResultAdapter(runDetailRepo, findingRepo);
    }

    @Bean
    public JpaMscanFindingQueryAdapter mscanFindingQueryAdapter(
        MscanFindingJpaRepository findingRepo
    ) {
        return new JpaMscanFindingQueryAdapter(findingRepo);
    }

    @Bean
    public JpaCodeqlFindingAdapter codeqlFindingAdapter(
        CodeqlFindingJpaRepository findingRepo,
        CodeqlFlowJpaRepository flowRepo,
        CodeqlFlowStepJpaRepository stepRepo
    ) {
        return new JpaCodeqlFindingAdapter(findingRepo, flowRepo, stepRepo);
    }

    @Bean
    public JpaSanitizerResultAdapter sanitizerResultAdapter(
        AgentRunDetailJpaRepository agentDetailRepo,
        ProjectVersionJpaRepository projectVersionRepo,
        SanitizerRegistryJpaRepository registryRepo,
        SanitizerCandidateJpaRepository candidateRepo
    ) {
        return new JpaSanitizerResultAdapter(
            agentDetailRepo,
            projectVersionRepo,
            registryRepo,
            candidateRepo
        );
    }

    @Bean
    public JpaUnifiedTaintRecordAdapter unifiedTaintRecordAdapter(
        UnifiedTaintRecordJpaRepository unifiedRepo,
        TaintStepJpaRepository stepRepo,
        CodeqlFindingJpaRepository codeqlFindingRepo,
        MscanFindingJpaRepository mscanFindingRepo,
        ServiceModuleJpaRepository serviceModuleRepo
    ) {
        return new JpaUnifiedTaintRecordAdapter(
            unifiedRepo,
            stepRepo,
            codeqlFindingRepo,
            mscanFindingRepo,
            serviceModuleRepo
        );
    }

    @Bean
    public JpaUnifiedResultQueryAdapter unifiedResultQueryAdapter(
        UnifiedTaintRecordJpaRepository unifiedRepo,
        TaintStepJpaRepository stepRepo,
        AnalysisRunJpaRepository analysisRunRepo,
        ServiceModuleJpaRepository serviceModuleRepo
    ) {
        return new JpaUnifiedResultQueryAdapter(unifiedRepo, stepRepo, analysisRunRepo, serviceModuleRepo);
    }

}