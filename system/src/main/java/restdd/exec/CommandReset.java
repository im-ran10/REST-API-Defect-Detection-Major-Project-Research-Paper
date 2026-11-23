package restdd.exec;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reset through project scripts ({@code bash -c <command>}), then a health check: GET {@code healthUrl} until it
 * answers 2xx or {@code healthTimeout} passes. Script output is kept (shortened, redacted) in the run record.
 */
public final class CommandReset implements ResetAdapter {

    private final String name;
    private final String snapshotCommand;
    private final String restoreCommand;
    private final Path workDir;
    private final String healthUrl;
    private final AuthConfig healthAuth;
    private final Duration healthTimeout;
    private final Duration commandTimeout;
    private final List<String> secrets;

    public CommandReset(String name, String snapshotCommand, String restoreCommand, Path workDir, String healthUrl,
                        AuthConfig healthAuth, Duration healthTimeout, Duration commandTimeout) {
        this.name = name;
        this.snapshotCommand = snapshotCommand;
        this.restoreCommand = restoreCommand;
        this.workDir = workDir;
        this.healthUrl = healthUrl;
        this.healthAuth = healthAuth == null ? AuthConfig.NONE : healthAuth;
        this.healthTimeout = healthTimeout;
        this.commandTimeout = commandTimeout;
        this.secrets = this.healthAuth.secrets();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void snapshot(ObjectNode record) throws ResetException {
        runCommand("snapshot", snapshotCommand, record);
        waitHealthy(record);
    }

    @Override
    public void restore(ObjectNode record) throws ResetException {
        runCommand("restore", restoreCommand, record);
        waitHealthy(record);
    }

    private void runCommand(String what, String command, ObjectNode record) throws ResetException {
        ObjectNode r = record.putObject(what);
        r.put("command", command);
        long t0 = System.nanoTime();
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command).redirectErrorStream(true);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            Process p = pb.start();
            p.getOutputStream().close();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try {
                    p.getInputStream().transferTo(out);
                } catch (IOException ignored) {
                    // process ended or was killed
                }
            }, "restdd-reset-output");
            reader.setDaemon(true);
            reader.start();
            boolean done = p.waitFor(commandTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                throw new ResetException(what + " timed out after " + commandTimeout.toSeconds() + " s");
            }
            reader.join(5000);
            String text = Redactor.redact(out.toString(StandardCharsets.UTF_8), secrets);
            r.put("output", text.length() > 4000 ? text.substring(text.length() - 4000) : text);
            r.put("exitCode", p.exitValue());
            r.put("ms", (System.nanoTime() - t0) / 1_000_000);
            if (p.exitValue() != 0) {
                String tail = text.strip();
                tail = tail.length() > 400 ? "..." + tail.substring(tail.length() - 400) : tail;
                throw new ResetException(what + " failed with exit code " + p.exitValue()
                        + (tail.isEmpty() ? "" : ": " + tail.replace('\n', ' ')));
            }
        } catch (IOException e) {
            throw new ResetException(what + " could not start: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResetException(what + " interrupted");
        }
    }

    private void waitHealthy(ObjectNode record) throws ResetException {
        if (healthUrl == null || healthUrl.isBlank()) {
            return;
        }
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
        long t0 = System.nanoTime();
        long deadline = t0 + healthTimeout.toNanos();
        int last = -1;
        while (System.nanoTime() < deadline) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(healthUrl)).timeout(Duration.ofSeconds(10)).GET();
                healthAuth.apply(b);
                last = http.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
                if (last >= 200 && last < 300) {
                    record.put("healthMs", (System.nanoTime() - t0) / 1_000_000);
                    return;
                }
            } catch (IOException e) {
                last = -1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResetException("health check interrupted");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResetException("health check interrupted");
            }
        }
        throw new ResetException("not healthy after " + healthTimeout.toSeconds() + " s (last status " + last + ")");
    }
}
