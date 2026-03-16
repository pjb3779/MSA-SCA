package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.time.Instant;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import buaa.msasca.sca.core.domain.enums.MscanSummaryStatus;
import buaa.msasca.sca.core.domain.model.MscanRunSummary;
import buaa.msasca.sca.core.port.out.persistence.MscanRunSummaryCommandPort;
import buaa.msasca.sca.core.port.out.persistence.MscanRunSummaryPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanRunSummaryEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.tooldetail.MscanRunDetailEntity;
import buaa.msasca.sca.infra.persistence.jpa.mapper.MscanRunSummaryMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.MscanRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.Mscan.MscanRunSummaryJpaRepository;

public class JpaMscanRunSummaryAdapter implements MscanRunSummaryPort, MscanRunSummaryCommandPort {

    private final MscanRunSummaryJpaRepository repo;
    private final MscanRunDetailJpaRepository detailRepo;
    private final MscanRunSummaryMapper mapper;

    public JpaMscanRunSummaryAdapter(
        MscanRunSummaryJpaRepository repo,
        MscanRunDetailJpaRepository detailRepo,
        MscanRunSummaryMapper mapper
    ) {
        this.repo = repo;
        this.detailRepo = detailRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MscanRunSummary> findByToolRunId(Long toolRunId) {
        return repo.findById(toolRunId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public MscanRunSummary upsert(
        Long toolRunId,
        MscanSummaryStatus status,
        int resultCount,
        String reportStoragePath,
        String reportSha256,
        Instant ingestedAt
    ) {
        MscanRunDetailEntity detail = detailRepo.findById(toolRunId)
            .orElseThrow(() -> new IllegalArgumentException("mscan_run_detail not found: " + toolRunId));

        MscanRunSummaryEntity e = repo.findById(toolRunId)
            .orElseGet(() -> MscanRunSummaryEntity.create(detail));

        e.update(status, resultCount, reportStoragePath, reportSha256, ingestedAt);
        return mapper.toDomain(repo.save(e));
    }
}