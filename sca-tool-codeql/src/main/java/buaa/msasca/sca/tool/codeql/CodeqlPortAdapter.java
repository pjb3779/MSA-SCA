package buaa.msasca.sca.tool.codeql;

import buaa.msasca.sca.core.port.out.tool.CodeqlPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CodeqlPortAdapter implements CodeqlPort {
  private static final Logger log = LoggerFactory.getLogger(CodeqlPortAdapter.class);

  @Override
  public void createDatabase(Long toolRunId, Long serviceModuleId, String sourcePath) {
    log.info("[CodeQL] createDatabase toolRunId={} serviceModuleId={} sourcePath={}", toolRunId, serviceModuleId, sourcePath);
  }

  @Override
  public void runQueries(Long toolRunId, Long serviceModuleId, String sourcePath) {
    log.info("[CodeQL] runQueries toolRunId={} serviceModuleId={} sourcePath={}", toolRunId, serviceModuleId, sourcePath);
  }
}