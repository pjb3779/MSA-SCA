package buaa.msasca.sca.core.domain.model;

import buaa.msasca.sca.core.domain.enums.SourceType;

public record ProjectVersionView(
    Long id,
    Long projectId,
    String versionLabel,
    SourceType sourceType,
    String sourceUrl,
    String uploadFilePath,
    String vcsCommitHash
) {}