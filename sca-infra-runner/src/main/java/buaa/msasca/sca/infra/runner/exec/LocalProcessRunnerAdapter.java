package buaa.msasca.sca.infra.runner.exec;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import buaa.msasca.sca.core.port.out.tool.RunnerPort;

public class LocalProcessRunnerAdapter implements RunnerPort {

  @Override
  public ExecResult run(ExecSpec spec) {
    try {
      var pb = new ProcessBuilder(spec.command());
      if (spec.workDir() != null && !spec.workDir().isBlank()) {
        pb.directory(new java.io.File(spec.workDir()));
      }
      Map<String, String> env = pb.environment();
      if (spec.env() != null) env.putAll(spec.env());

      var process = pb.start();

      boolean finished;
      if (spec.timeout() != null) {
        finished = process.waitFor(spec.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      } else {
        process.waitFor();
        finished = true;
      }

      if (!finished) {
        process.destroyForcibly();
        return new ExecResult(124, "", "Process timeout");
      }

      var stdout = readAll(process.getInputStream());
      var stderr = readAll(process.getErrorStream());
      return new ExecResult(process.exitValue(), stdout, stderr);
    } catch (Exception e) {
      return new ExecResult(1, "", e.toString());
    }
  }

  private static String readAll(InputStream in) throws Exception {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    in.transferTo(bout);
    return bout.toString(StandardCharsets.UTF_8);
  }
}
