package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.AnalysisRunEntity;
import buaa.msasca.sca.infra.persistence.jpa.mapper.AnalysisRunMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;

public class JpaAnalysisRunAdapter implements AnalysisRunPort {
    private final AnalysisRunJpaRepository runRepo;
    private final ProjectVersionJpaRepository pvRepo;
    private final AnalysisRunMapper mapper;

    public JpaAnalysisRunAdapter(AnalysisRunJpaRepository runRepo, ProjectVersionJpaRepository pvRepo, AnalysisRunMapper mapper) {
        this.runRepo = runRepo;
        this.pvRepo = pvRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AnalysisRun create(Long projectVersionId, JsonNode configJson, String triggeredBy) {
        ProjectVersionEntity pv = pvRepo.findById(projectVersionId)
            .orElseThrow(() -> new IllegalArgumentException("project_version not found: " + projectVersionId));

        AnalysisRunEntity e = AnalysisRunEntity.create(pv, configJson, triggeredBy);
        return mapper.toDomain(runRepo.save(e));
    }

    @Override
    public Optional<AnalysisRun> findById(Long analysisRunId) {
        return runRepo.findById(analysisRunId).map(mapper::toDomain);
    }

    @Override
    public List<AnalysisRun> findByStatus(RunStatus status, int limit) {
        return runRepo.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, limit)).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public AnalysisRun markRunning(Long analysisRunId) {
        AnalysisRunEntity e = runRepo.findById(analysisRunId)
            .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));
        e.start();
        return mapper.toDomain(e);
    }

    @Override
    @Transactional
    public AnalysisRun markDone(Long analysisRunId) {
        AnalysisRunEntity e = runRepo.findById(analysisRunId)
            .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));
        e.finishSuccess();
        return mapper.toDomain(e);
    }

    @Override
    @Transactional
    public AnalysisRun markFailed(Long analysisRunId) {
        AnalysisRunEntity e = runRepo.findById(analysisRunId)
            .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));
        e.finishFail();
        return mapper.toDomain(e);
    }
}