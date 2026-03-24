package buaa.msasca.sca.tool.agent.pipeline;

import java.util.List;

public record VerifiedSanitizerCandidate(
    SanitizerCandidate candidate,
    SanitizerStatus status,
    String actionType,
    List<String> vulnTypes,
    String reasoning
) {}

