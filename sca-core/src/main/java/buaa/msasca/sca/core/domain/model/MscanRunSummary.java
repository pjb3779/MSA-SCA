package buaa.msasca.sca.core.domain.model;

import java.time.Instant;

import buaa.msasca.sca.core.domain.enums.MscanSummaryStatus;

public record MscanRunSummary(
    Long toolRunId,
    MscanSummaryStatus status,
    int resultCount,
    String reportStoragePath, // CLEAN이면 null 가능(권장: CLEAN이어도 저장)
    String reportSha256,
    Instant ingestedAt,
    Instant createdAt,
    Instant updatedAt
) {}