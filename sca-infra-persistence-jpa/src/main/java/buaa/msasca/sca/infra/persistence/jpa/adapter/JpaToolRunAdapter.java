package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.model.ToolRun;
import buaa.msasca.sca.core.port.out.persistence.ToolRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ToolRunPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.AnalysisRunEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.ToolRunEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.AgentRunDetailEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.BuildRunDetailEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.CodeqlRunDetailEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.MscanRunDetailEntity;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ToolRunMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.AgentRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.BuildRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.CodeqlRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.MscanRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ServiceModuleJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ToolRunJpaRepository;

public class JpaToolRunAdapter implements ToolRunCommandPort, ToolRunPort {

    private final ToolRunJpaRepository toolRunRepo;
    private final AnalysisRunJpaRepository analysisRunRepo;
    private final ServiceModuleJpaRepository serviceModuleRepo;

    private final BuildRunDetailJpaRepository buildDetailRepo;
    private final CodeqlRunDetailJpaRepository codeqlDetailRepo;
    private final AgentRunDetailJpaRepository agentDetailRepo;
    private final MscanRunDetailJpaRepository mscanDetailRepo;

    private final ToolRunMapper mapper;

    public JpaToolRunAdapter(
        ToolRunJpaRepository toolRunRepo,
        AnalysisRunJpaRepository analysisRunRepo,
        ServiceModuleJpaRepository serviceModuleRepo,
        BuildRunDetailJpaRepository buildDetailRepo,
        CodeqlRunDetailJpaRepository codeqlDetailRepo,
        AgentRunDetailJpaRepository agentDetailRepo,
        MscanRunDetailJpaRepository mscanDetailRepo,
        ToolRunMapper mapper
    ) {
        this.toolRunRepo = toolRunRepo;
        this.analysisRunRepo = analysisRunRepo;
        this.serviceModuleRepo = serviceModuleRepo;
        this.buildDetailRepo = buildDetailRepo;
        this.codeqlDetailRepo = codeqlDetailRepo;
        this.agentDetailRepo = agentDetailRepo;
        this.mscanDetailRepo = mscanDetailRepo;
        this.mapper = mapper;
    }

    @Override
    public Optional<ToolRun> findById(Long toolRunId) {
        return toolRunRepo.findById(toolRunId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public ToolRun createBuildRun(Long analysisRunId, Long serviceModuleId, String toolVersion, JsonNode configJson) {
        AnalysisRunEntity ar = analysisRunRepo.findById(analysisRunId)
            .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));
        ServiceModuleEntity sm = serviceModuleRepo.findById(serviceModuleId)
            .orElseThrow(() -> new IllegalArgumentException("service_module not found: " + serviceModuleId));

        ToolRunEntity tr = ToolRunEntity.create(ar, buaa.msasca.sca.core.domain.enums.ToolType.BUILD, toolVersion, configJson);
        ToolRunEntity saved = toolRunRepo.save(tr);

        BuildRunDetailEntity detail = BuildRunDetailEntity.create(saved, sm);
        buildDetailRepo.save(detail);

        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public ToolRun createCodeqlRun(Long analysisRunId, Long serviceModuleId, String toolVersion, JsonNode configJson) {
        AnalysisRunEntity ar = analysisRunRepo.findById(analysisRunId)
            .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));
        ServiceModuleEntity sm = serviceModuleRepo.findById(serviceModuleId)
            .orElseThrow(() -> new IllegalArgumentException("service_module not found: " + serviceModuleId));

        ToolRunEntity tr = ToolRunEntity.create(ar, buaa.msasca.sca.core.domain.enums.ToolType.CODEQL, toolVersion, configJson);
        ToolRunEntity saved = toolRunRepo.save(tr);

        CodeqlRunDetailEntity detail = CodeqlRunDetailEntity.create(saved, sm);
        codeqlDetailRepo.save(detail);

        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public ToolRun createAgentRun(Long analysisRunId, String modelName, String toolVersion, JsonNode configJson) {
        AnalysisRunEntity ar = analysisRunRepo.findById(analysisRunId)
            .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));

        ToolRunEntity tr = ToolRunEntity.create(ar, buaa.msasca.sca.core.domain.enums.ToolType.AGENT, toolVersion, configJson);
        ToolRunEntity saved = toolRunRepo.save(tr);

        AgentRunDetailEntity detail = AgentRunDetailEntity.create(saved, modelName);
        agentDetailRepo.save(detail);

        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public ToolRun createMscanRun(Long analysisRunId, String toolVersion, JsonNode configJson) {
        AnalysisRunEntity ar = analysisRunRepo.findById(analysisRunId)
            .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));

        ToolRunEntity tr = ToolRunEntity.create(ar, buaa.msasca.sca.core.domain.enums.ToolType.MSCAN, toolVersion, configJson);
        ToolRunEntity saved = toolRunRepo.save(tr);

        MscanRunDetailEntity detail = MscanRunDetailEntity.create(saved);
        mscanDetailRepo.save(detail);

        return mapper.toDomain(saved);
    }

    @Override
    @Transactional
    public ToolRun markRunning(Long toolRunId) {
        ToolRunEntity e = toolRunRepo.findById(toolRunId)
            .orElseThrow(() -> new IllegalArgumentException("tool_run not found: " + toolRunId));
        e.start();
        return mapper.toDomain(e);
    }

    @Override
    @Transactional
    public ToolRun markDone(Long toolRunId) {
        ToolRunEntity e = toolRunRepo.findById(toolRunId)
            .orElseThrow(() -> new IllegalArgumentException("tool_run not found: " + toolRunId));
        e.done();
        return mapper.toDomain(e);
    }

    @Override
    @Transactional
    public ToolRun markFailed(Long toolRunId, String errorMessage) {
        ToolRunEntity e = toolRunRepo.findById(toolRunId)
            .orElseThrow(() -> new IllegalArgumentException("tool_run not found: " + toolRunId));
        e.fail(errorMessage);
        return mapper.toDomain(e);
    }
}