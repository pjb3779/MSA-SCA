package buaa.msasca.sca.tool.agent.pipeline;

import java.util.List;

public record JudgedSanitizerCandidate(
    SanitizerCandidate candidate,
    String actionType,
    List<String> vulnTypes,
    double confidence,
    String reasoning
) {}

