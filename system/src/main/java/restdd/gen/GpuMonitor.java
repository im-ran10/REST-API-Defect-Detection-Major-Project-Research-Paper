package restdd.gen;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * GPU temperature, power and clock per call (D44 condition 2, R35). Before every fresh model call the runner
 * reads the GPU; at or above {@code pauseC} it waits, re-reading every 15 s, until the GPU is at or below
 * {@code resumeC}. Without {@code nvidia-smi} the reading is recorded as unavailable and nothing pauses.
 */
public final class GpuMonitor {

    public record Reading(boolean available, int temperatureC, double powerW, int clockMhz, int utilisationPct,
                          int memoryMiB) {
        public ObjectNode toJson() {
            ObjectNode o = JsonNodeFactory.instance.objectNode();
            o.put("available", available);
            if (available) {
                o.put("temperatureC", temperatureC);
                o.put("powerW", powerW);
                o.put("clockMhz", clockMhz);
                o.put("utilisationPct", utilisationPct);
                o.put("memoryMiB", memoryMiB);
            }
            return o;
        }
    }

    static final Reading UNAVAILABLE = new Reading(false, -1, -1, -1, -1, -1);

    private final int pauseC;
    private final int resumeC;
    private long pausedMs;
    private int pauses;

    public GpuMonitor(int pauseC, int resumeC) {
        if (resumeC >= pauseC) {
            throw new IllegalArgumentException("resume temperature must be below the pause temperature");
        }
        this.pauseC = pauseC;
        this.resumeC = resumeC;
    }

    public Reading read() {
        try {
            Process p = new ProcessBuilder("nvidia-smi",
                    "--query-gpu=temperature.gpu,power.draw,clocks.gr,utilization.gpu,memory.used",
                    "--format=csv,noheader,nounits").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0) {
                return UNAVAILABLE;
            }
            return parse(out);
        } catch (IOException e) {
            return UNAVAILABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return UNAVAILABLE;
        }
    }

    static Reading parse(String line) {
        String[] f = line.strip().split("\\R")[0].split(",");
        if (f.length < 5) {
            return UNAVAILABLE;
        }
        try {
            return new Reading(true, (int) Math.round(num(f[0])), num(f[1]), (int) Math.round(num(f[2])),
                    (int) Math.round(num(f[3])), (int) Math.round(num(f[4])));
        } catch (NumberFormatException e) {
            return UNAVAILABLE;
        }
    }

    private static double num(String s) {
        String t = s.trim();
        return t.startsWith("[") ? -1 : Double.parseDouble(t); // "[N/A]" on some laptops
    }

    /** Blocks while the GPU is too hot; returns the reading taken just before the call. */
    public Reading waitUntilCool() throws InterruptedException {
        Reading r = read();
        if (!r.available() || r.temperatureC() < pauseC) {
            return r;
        }
        pauses++;
        long t0 = System.nanoTime();
        System.out.printf("  GPU at %d °C (limit %d): pausing until %d °C%n", r.temperatureC(), pauseC, resumeC);
        while (r.available() && r.temperatureC() > resumeC) {
            Thread.sleep(15_000);
            r = read();
        }
        pausedMs += (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("  GPU at %d °C: resuming%n", r.temperatureC());
        return r;
    }

    public long pausedMs() {
        return pausedMs;
    }

    public int pauses() {
        return pauses;
    }
}
