package buaa.msasca.sca.core.port.in;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.model.AnalysisRun;

public interface CreateAnalysisRunUseCase {

    AnalysisRun handle(Command command);

    record Command(
        Long projectVersionId,
        JsonNode configJson,
        String triggeredBy,
        boolean requireSourceCache
    ) {}
}
