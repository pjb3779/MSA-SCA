package buaa.msasca.sca.tool.mscan;

import buaa.msasca.sca.core.port.out.tool.MscanPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MscanPortAdapter implements MscanPort {
  private static final Logger log = LoggerFactory.getLogger(MscanPortAdapter.class);

  @Override
  public void runGlobalAnalysis(Long toolRunId, Long projectVersionId, String sourcePath) {
    log.info("[MScan] runGlobalAnalysis toolRunId={} projectVersionId={} sourcePath={}", toolRunId, projectVersionId, sourcePath);
  }
}