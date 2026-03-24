package buaa.msasca.sca.tool.mscan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import buaa.msasca.sca.core.port.out.tool.MscanPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;

public class DockerMscanPortAdapter implements MscanPort {

  private final RunnerPort runnerPort;
  private final String dockerMemoryLimit;

  public DockerMscanPortAdapter(RunnerPort runnerPort) {
    this(runnerPort, null);
  }

  public DockerMscanPortAdapter(RunnerPort runnerPort, String dockerMemoryLimit) {
    this.runnerPort = runnerPort;
    this.dockerMemoryLimit = (dockerMemoryLimit != null && !dockerMemoryLimit.isBlank())
        ? dockerMemoryLimit : null;
  }

  @Override
  public RunResult runGlobalAnalysis(RunRequest req) {
    try {
      Path reportOnHost = Path.of(req.sourceRootPathOnHost())
          .resolve(".msasca/mscan/report.txt");
      Files.createDirectories(reportOnHost.getParent());

      Path gatewayOnHost = Path.of(req.gatewayYamlPathOnHost());
      if (!Files.exists(gatewayOnHost)) {
        throw new IllegalStateException("gateway.yml not found: " + req.gatewayYamlPathOnHost());
      }

      Path jarDirOnHost = Path.of(req.jarDirPathOnHost());
      if (!Files.exists(jarDirOnHost)) {
        throw new IllegalStateException("jar dir not found: " + req.jarDirPathOnHost());
      }

      String srcInContainer = "/work/src";
      String jarsInContainer = "/work/jars";

      String gatewayInContainer = srcInContainer + "/.msasca/mscan/gateway.yml";
      String outInContainer = srcInContainer + "/.msasca/mscan/report.txt";
      String optionsInContainer = null;

      if (!isBlank(req.optionsFilePathOnHost())) {
        try {
          Path srcHost = Path.of(req.sourceRootPathOnHost()).toAbsolutePath().normalize();
          Path optHost = Path.of(req.optionsFilePathOnHost()).toAbsolutePath().normalize();
          if (optHost.startsWith(srcHost)) {
            String rel = srcHost.relativize(optHost).toString().replace("\\", "/");
            optionsInContainer = srcInContainer + "/" + rel;
          } else {
            optionsInContainer = req.optionsFilePathOnHost();
          }
        } catch (Exception ignored) {
          optionsInContainer = req.optionsFilePathOnHost();
        }
      }

      // ------------------------------------------------------------
      // docker run 조립
      // ------------------------------------------------------------
      List<String> cmd = new ArrayList<>();
      cmd.addAll(List.of("docker", "run", "--rm"));

      if (dockerMemoryLimit != null) {
        cmd.addAll(List.of("--memory", dockerMemoryLimit));
      }

      // mount
      cmd.addAll(List.of("-v", req.sourceRootPathOnHost() + ":" + srcInContainer));
      cmd.addAll(List.of("-v", req.jarDirPathOnHost() + ":" + jarsInContainer));

      // image
      cmd.add(req.mscanDockerImage());

      // args (entrypoint.sh 계약)
      cmd.addAll(List.of(
          "--name", safe(req.projectName()),
          "--jar-path", jarsInContainer,
          "--classpath-keywords", safe(req.classpathKeywordsCsv()),
          "--gateway-yaml", gatewayInContainer,
          "--out", outInContainer,
          "--target-path", srcInContainer + "/.msasca/mscan/tmp"
      ));

      // optional flags
      if (!isBlank(req.optionsFilePathOnHost())) {
        cmd.addAll(List.of("--options-file", optionsInContainer));
      }
      if (req.reuse()) {
        cmd.add("--reuse");
      }
      if (!isBlank(req.appJvmArgs())) {
        cmd.addAll(List.of("--app-jvm-args", req.appJvmArgs()));
      }

      Duration timeout = (req.timeout() == null) ? Duration.ofMinutes(120) : req.timeout();
      var res = runnerPort.run(new RunnerPort.ExecSpec(cmd, Map.of(), null, timeout));

      if (res.exitCode() != 0) {
        throw new IllegalStateException(
            "mscan docker failed. exit=" + res.exitCode() +
            "\nSTDOUT:\n" + res.stdout() +
            "\nSTDERR:\n" + res.stderr()
        );
      }

      return new RunResult(reportOnHost.toString(), res.stdout(), res.stderr());
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      String cause = (e.getMessage() == null) ? e.toString() : e.getMessage();
      throw new IllegalStateException("mscan adapter failed: " + cause, e);
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String safe(String s) {
    return (s == null) ? "" : s;
  }

}