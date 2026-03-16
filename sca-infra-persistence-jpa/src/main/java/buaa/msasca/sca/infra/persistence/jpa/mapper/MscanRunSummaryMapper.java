package buaa.msasca.sca.infra.persistence.jpa.mapper;

import buaa.msasca.sca.core.domain.model.MscanRunSummary;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanRunSummaryEntity;

public class MscanRunSummaryMapper {
    public MscanRunSummary toDomain(MscanRunSummaryEntity e) {
        return new MscanRunSummary(
            e.getToolRunId(),
            e.getStatus(),
            e.getResultCount(),
            e.getReportStoragePath(),
            e.getReportSha256(),
            e.getIngestedAt(),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}