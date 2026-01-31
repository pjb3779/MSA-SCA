package buaa.msasca.sca.core.domain.model;

import buaa.msasca.sca.core.domain.enums.BuildTool;

public record ServiceModule(
    Long id,
    Long projectVersionId,
    String name,
    String rootPath,
    BuildTool buildTool,
    String jdkVersion,
    boolean isGateway
) {}
