package buaa.msasca.sca.app.worker.tool.docker;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import buaa.msasca.sca.core.port.out.tool.DockerImagePort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;

public class DockerImagePortAdapter implements DockerImagePort {

    private final RunnerPort runnerPort;
    private final String workspaceBasePath;
    private final ConcurrentHashMap<String, Boolean> ensured = new ConcurrentHashMap<>();

    public DockerImagePortAdapter(RunnerPort runnerPort, String workspaceBasePath) {
        this.runnerPort = Objects.requireNonNull(runnerPort, "runnerPort must not be null");
        this.workspaceBasePath = Objects.requireNonNull(workspaceBasePath, "workspaceBasePath must not be null");
    }

    /**
     * 이미지가 로컬에 존재하도록 보장한다.
     * - 이미 ensure된 이미지는 캐시로 스킵한다.
     * - 없으면 pull 한다.
     *
     * @param image docker image
     * @param timeout 타임아웃(주로 pull에 사용)
     * @return ensure 결과
     */
    @Override
    public EnsureResult ensurePresent(String image, Duration timeout) {
        String img = Objects.requireNonNull(image, "image must not be null").trim();
        if (img.isBlank()) throw new IllegalArgumentException("image must not be blank");

        if (Boolean.TRUE.equals(ensured.get(img))) {
        return new EnsureResult(false, "[ensureImage] cached: " + img, "");
        }

        Duration inspectTimeout = Duration.ofSeconds(5);
        if (timeout != null && timeout.compareTo(inspectTimeout) < 0) {
        inspectTimeout = timeout;
        }

        // 1) inspect
        RunnerPort.ExecResult inspect = runnerPort.run(new RunnerPort.ExecSpec(
            List.of("bash", "-lc", "docker image inspect " + shQuote(img) + " >/dev/null 2>&1"),
            Map.of(),
            workspaceBasePath,
            inspectTimeout
        ));

        if (inspect.exitCode() == 0) {
        ensured.put(img, true);
        return new EnsureResult(false, "[ensureImage] found locally: " + img, "");
        }

        // 2) pull
        Duration pullTimeout = (timeout == null) ? Duration.ofMinutes(20) : timeout;
        RunnerPort.ExecResult pull = runnerPort.run(new RunnerPort.ExecSpec(
            List.of("bash", "-lc", "docker pull " + shQuote(img)),
            Map.of(),
            workspaceBasePath,
            pullTimeout
        ));

        if (pull.exitCode() != 0) {
        throw new IllegalStateException("docker pull failed for image=" + img + "\n" + pull.stderr());
        }

        ensured.put(img, true);
        return new EnsureResult(true, "[ensureImage] pulled: " + img + "\n" + pull.stdout(), pull.stderr());
    }

    /**
     * 단일따옴표 기반 shell quoting.
     *
     * @param s 문자열
     * @return quoted 문자열
     */
    private String shQuote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
}