package com.echo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings -> Update Echo (Story 5653). Starts scripts/Update-Echo.ps1 in a process of its own --
 * it stops this Echo, swaps in the newest release and starts it again -- and reads back the
 * logs/update-state.json and logs/update-*.log the script writes, so the page can say how it went.
 *
 * The folder is the one Echo runs from (Start-Echo.ps1 sets it as the working directory).
 */
@Component
public class EchoUpdater {

    static final String STATE_FILE = "update-state.json";
    static final int LOG_TAIL_LINES = 60;
    /** A "starting" state older than this means the script never got going. */
    static final Duration STARTING_GRACE = Duration.ofMinutes(2);

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path home;

    public EchoUpdater(@Value("${echo.home:}") String home) {
        this.home = Path.of(home == null || home.isBlank() ? System.getProperty("user.dir") : home).toAbsolutePath().normalize();
    }

    Path script() { return home.resolve("scripts").resolve("Update-Echo.ps1"); }
    Path logs() { return home.resolve("logs"); }

    /** Thrown when an update cannot be started; the message is for the person. */
    public static class UpdateRefusedException extends RuntimeException {
        public UpdateRefusedException(String message) { super(message); }
    }

    /** Starts the update. Refused when the script is missing or an update is already running. */
    public synchronized Map<String, Object> start() {
        if (!Files.isRegularFile(script())) {
            throw new UpdateRefusedException("This Echo cannot update itself: " + script() + " is not there. Run update-echo.cmd in the Echo folder instead.");
        }
        if (isRunning(readState())) throw new UpdateRefusedException("An update is already running. Wait for it to finish.");
        try {
            Files.createDirectories(logs());
            Map<String, Object> starting = new LinkedHashMap<>();
            starting.put("state", "starting");
            starting.put("message", "Starting the update.");
            starting.put("started", LocalDateTime.now().withNano(0).toString());
            Files.writeString(logs().resolve(STATE_FILE), JSON.writeValueAsString(starting), StandardCharsets.UTF_8);
            launch(command());
        } catch (IOException ex) {
            throw new UpdateRefusedException("The update could not be started: " + ex.getMessage());
        }
        return status();
    }

    List<String> command() {
        return new ArrayList<>(Arrays.asList("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-WindowStyle", "Hidden", "-File", script().toString()));
    }

    /** Its own process: it outlives this Echo, which the script stops. Output goes to a log, never a pipe. */
    void launch(List<String> command) throws IOException {
        new ProcessBuilder(command)
            .directory(home.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logs().resolve("update-web.log").toFile()))
            .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
            .start();
    }

    private static java.io.File nullDevice() {
        return new java.io.File(System.getProperty("os.name", "").toLowerCase().contains("win") ? "NUL" : "/dev/null");
    }

    /** {state, message, started, finished, release, log, logTail, running, current} -- state "none" before any update. */
    public Map<String, Object> status() {
        Map<String, Object> state = readState();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", state.getOrDefault("state", "none"));
        out.put("message", state.getOrDefault("message", ""));
        out.put("started", state.getOrDefault("started", ""));
        out.put("finished", state.getOrDefault("finished", ""));
        out.put("release", state.getOrDefault("release", ""));
        boolean running = isRunning(state);
        if (!running && ("running".equals(out.get("state")) || "starting".equals(out.get("state")))) {
            out.put("state", "failed");
            out.put("message", "The update stopped before it finished. See the log below.");
        }
        out.put("running", running);
        String log = String.valueOf(state.getOrDefault("log", ""));
        out.put("log", log);
        out.put("logTail", tail(log));
        out.put("current", currentRelease());
        return out;
    }

    /** The release this Echo runs, from config/echo-release.json; empty when it was built here. */
    public Map<String, Object> currentRelease() {
        return readJson(home.resolve("config").resolve("echo-release.json"));
    }

    boolean isRunning(Map<String, Object> state) {
        Object s = state.get("state");
        if ("starting".equals(s)) {
            try {
                return LocalDateTime.parse(String.valueOf(state.get("started"))).plus(STARTING_GRACE).isAfter(LocalDateTime.now());
            } catch (RuntimeException ex) { return false; }
        }
        if (!"running".equals(s)) return false;
        try {
            long pid = Long.parseLong(String.valueOf(state.get("pid")).trim());
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (NumberFormatException ex) { return false; }
    }

    Map<String, Object> readState() { return readJson(logs().resolve(STATE_FILE)); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(Path p) {
        try {
            if (!Files.isRegularFile(p)) return new LinkedHashMap<>();
            String text = decode(Files.readAllBytes(p)).trim();
            if (text.isEmpty()) return new LinkedHashMap<>();
            return JSON.readValue(text, LinkedHashMap.class);
        } catch (IOException | RuntimeException ex) {
            return new LinkedHashMap<>();
        }
    }

    /** The last lines of logs/<name>; only a plain update-*.log name is read. */
    List<String> tail(String name) {
        if (name == null || !name.matches("update-[0-9-]+\\.log")) return List.of();
        Path p = logs().resolve(name);
        try {
            if (!Files.isRegularFile(p)) return List.of();
            List<String> lines = Arrays.asList(decode(Files.readAllBytes(p)).split("\\r?\\n"));
            return new ArrayList<>(lines.subList(Math.max(0, lines.size() - LOG_TAIL_LINES), lines.size()));
        } catch (IOException ex) {
            return List.of();
        }
    }

    /** PowerShell writes UTF-8 with a byte-order mark, or UTF-16; read either. */
    static String decode(byte[] b) {
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        return new String(b, StandardCharsets.UTF_8);
    }
}
