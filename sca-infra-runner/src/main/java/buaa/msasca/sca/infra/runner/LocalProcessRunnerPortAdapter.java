package buaa.msasca.sca.infra.runner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import buaa.msasca.sca.core.port.out.tool.RunnerPort;

public class LocalProcessRunnerPortAdapter implements RunnerPort {
    /**
     * 로컬 OS에서 외부 커맨드를 실행한다.
     *
     * @param spec 실행 스펙
     * @return 실행 결과(stdout/stderr/exitCode)
     */
    @Override
    public ExecResult run(ExecSpec spec) {
        try {
        ProcessBuilder pb = new ProcessBuilder(spec.command());

        // env
        if (spec.env() != null && !spec.env().isEmpty()) {
            Map<String, String> env = pb.environment();
            env.putAll(spec.env());
        }

        // workdir
        if (spec.workDir() != null && !spec.workDir().isBlank()) {
            pb.directory(new java.io.File(spec.workDir()));
        }

        Process p = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread tOut = new Thread(() -> readAll(p.getInputStream(), stdout), "runner-stdout");
        Thread tErr = new Thread(() -> readAll(p.getErrorStream(), stderr), "runner-stderr");
        tOut.start();
        tErr.start();

        Duration timeout = (spec.timeout() == null) ? Duration.ofMinutes(10) : spec.timeout();
        boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

        if (!finished) {
            p.destroyForcibly();
            return new ExecResult(
                124,
                stdout.toString(),
                stderr + "\n[timeout] exceeded " + timeout
            );
        }

        tOut.join();
        tErr.join();

        return new ExecResult(p.exitValue(), stdout.toString(), stderr.toString());
        } catch (Exception e) {
        throw new IllegalStateException("RunnerPort failed: " + e.getMessage(), e);
        }
    }

    /**
     * 스트림을 끝까지 읽어서 StringBuilder에 적재한다.
     *
     * @param in 입력 스트림
     * @param sb 출력 버퍼
     */
    private void readAll(java.io.InputStream in, StringBuilder sb) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
        }
        } catch (Exception e) {
        sb.append("[read error] ").append(e.getMessage()).append('\n');
        }
    }
}