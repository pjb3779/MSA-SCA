package buaa.msasca.sca.core.port.in;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface RequestMscanOnlyRunUseCase {

  record JarUpload(String filename, InputStream content) {}

  record Request(
      Long projectVersionId,
      String mscanName,
      String classpathKeywords,
      String jvmArgs,
      Boolean reuse,
      String optionsFileRelPath,     // optional
      InputStream gatewayYaml,
      String gatewayFilename,
      InputStream jarsZip,           //  zip 1개
      String jarsZipFilename  
  ) {}

  record Response(
      Long projectVersionId,
      Long analysisRunId,
      int jarCount,
      String gatewayCachePath,
      String jarCacheDir,
      String message
  ) {}

  Response request(Request req);
}