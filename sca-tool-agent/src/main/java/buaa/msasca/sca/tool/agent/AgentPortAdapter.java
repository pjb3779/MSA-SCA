package buaa.msasca.sca.tool.agent;

import buaa.msasca.sca.core.port.out.tool.AgentPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentPortAdapter implements AgentPort {
  private static final Logger log = LoggerFactory.getLogger(AgentPortAdapter.class);

  @Override
  public void buildSanitizerRegistry(Long toolRunId, Long projectVersionId, String sourcePath) {
    log.info("[Agent] buildSanitizerRegistry toolRunId={} projectVersionId={} sourcePath={}", toolRunId, projectVersionId, sourcePath);
  }
}
