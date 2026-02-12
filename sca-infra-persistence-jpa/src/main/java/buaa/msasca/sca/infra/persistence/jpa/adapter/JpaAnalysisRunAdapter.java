package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.run.AnalysisRunEntity;
import buaa.msasca.sca.infra.persistence.jpa.mapper.AnalysisRunMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;

public class JpaAnalysisRunAdapter implements AnalysisRunCommandPort {

    private final AnalysisRunJpaRepository runRepo;
    private final ProjectVersionJpaRepository pvRepo;
    private final AnalysisRunMapper mapper;

    public JpaAnalysisRunAdapter(
        AnalysisRunJpaRepository runRepo,
        ProjectVersionJpaRepository pvRepo,
        AnalysisRunMapper mapper
    ) {
        this.runRepo = runRepo;
        this.pvRepo = pvRepo;
        this.mapper = mapper;
    }

    /**
     * analysis_run(PENDING)을 생성한다.
     *
     * @param projectVersionId project_version id
     * @param configJson config json
     * @param triggeredBy triggered_by
     * @return 생성된 run
     */
    @Override
    @Transactional
    public AnalysisRun createPending(Long projectVersionId, JsonNode configJson, String triggeredBy) {
        ProjectVersionEntity pv = pvRepo.findById(projectVersionId)
            .orElseThrow(() -> new IllegalArgumentException("project_version not found: " + projectVersionId));

        AnalysisRunEntity e = AnalysisRunEntity.create(pv, configJson, triggeredBy);
        return mapper.toDomain(runRepo.save(e));
    }

    /**
     * id로 run 조회.
     *
     * @param analysisRunId run id
     * @return optional
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisRun> findById(Long analysisRunId) {
        return runRepo.findById(analysisRunId).map(mapper::toDomain);
    }

    /**
     * status 기준 오래된 순서 조회.
     *
     * @param status 상태
     * @param limit 최대 개수
     * @return run 리스트
     */
    @Override
    @Transactional(readOnly = true)
    public List<AnalysisRun> findByStatus(RunStatus status, int limit) {
        return runRepo.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, limit)).stream()
            .map(mapper::toDomain)
            .toList();
    }

    /**
     * PENDING -> RUNNING 원자적 클레임.
     * 실패하면 예외를 던진다(현재 RunPollingJob 구조에 맞춤).
     *
     * @param analysisRunId run id
     * @return claimed run
     */
    @Override
    @Transactional
    public AnalysisRun markRunning(Long analysisRunId) {
        int updated = runRepo.tryMarkRunning(analysisRunId, Instant.now());
        if (updated != 1) {
        throw new IllegalStateException("claim failed (not pending): " + analysisRunId);
        }

        AnalysisRunEntity e = runRepo.findById(analysisRunId)
            .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));
        return mapper.toDomain(e);
    }

    /**
     * RUNNING -> DONE 전이.
     *
     * @param analysisRunId run id
     */
    @Override
    @Transactional
    public void markDone(Long analysisRunId) {
        int updated = runRepo.markDone(analysisRunId, Instant.now());
        if (updated != 1) {
        throw new IllegalStateException("markDone failed (not running): " + analysisRunId);
        }
    }

    /**
     * PENDING/RUNNING -> FAILED 전이.
     *
     * @param analysisRunId run id
     */
    @Override
    @Transactional
    public void markFailed(Long analysisRunId) {
        int updated = runRepo.markFailed(analysisRunId, Instant.now());
        if (updated != 1) {
        throw new IllegalStateException("markFailed failed (not pending/running): " + analysisRunId);
        }
    }

    /**
     * project_version에 활성 run(PENDING/RUNNING)이 있는지 확인한다.
     *
     * @param projectVersionId project_version id
     * @return 활성 run 존재 여부
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveRun(Long projectVersionId) {
        return runRepo.existsActiveRun(projectVersionId);
    }
}