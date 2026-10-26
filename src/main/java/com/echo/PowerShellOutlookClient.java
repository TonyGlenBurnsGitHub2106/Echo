package com.echo;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Story 5654: {@link OutlookClient} over the classic Outlook app on this computer, through a small
 * PowerShell bridge (scripts\OutlookBridge.ps1, beside Echo). The bridge is started once and kept
 * running: each call writes one JSON line to it and reads one JSON line back, so Outlook is attached
 * to once, not once per call. One call at a time (Outlook's object model is single-threaded anyway).
 *
 * The bridge attaches to the Outlook that is ALREADY running and never starts one -- a closed Outlook
 * is a 409 ("open Outlook"), never a hidden Outlook or a profile prompt. A call that takes too long
 * stops the bridge; the next call starts a fresh one.
 *
 * Files to attach are written for a moment to Echo's own files folder (mail-outbox), handed to
 * Outlook by path, and removed once Outlook has them -- Outlook takes attachments only from a file.
 */
@Component
@ConditionalOnProperty(name = "echo.mail.source", havingValue = "outlook", matchIfMissing = true)
public class PowerShellOutlookClient implements OutlookClient {

    private static final Logger log = LoggerFactory.getLogger(PowerShellOutlookClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long ACCOUNT_CACHE_MILLIS = 60_000;
    private static final java.time.format.DateTimeFormatter SECONDS = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Path bridge;
    private final Path scratch;
    private Process process;
    private BufferedWriter toBridge;
    private BlockingQueue<String> fromBridge;
    private volatile String account;   // read without the lock, so a long call never holds up the status line
    private volatile long accountAt;

    public PowerShellOutlookClient(@Value("${echo.mail.bridge:scripts/OutlookBridge.ps1}") String bridgePath,
                                   @Value("${echo.files.path:EchoFiles}") String filesPath) {
        this.bridge = Path.of(bridgePath).toAbsolutePath();
        this.scratch = Path.of(filesPath).toAbsolutePath().resolve("mail-outbox");
    }

    // ---- OutlookClient -------------------------------------------------------------------

    @Override
    public String account() {
        String a = account;
        if (a != null && System.currentTimeMillis() - accountAt < ACCOUNT_CACHE_MILLIS) return a;
        Map<String, Object> r = map(call(Map.of("op", "account"), 30));
        a = str(r.get("account"));
        accountAt = System.currentTimeMillis();
        account = a;
        return a;
    }

    @Override
    public List<MessageMeta> list(String folder, LocalDateTime after, LocalDateTime before, int max) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("op", "list");
        req.put("folder", folder);
        req.put("after", after.format(SECONDS));
        if (before != null) req.put("before", before.format(SECONDS));
        req.put("max", max);
        List<MessageMeta> out = new ArrayList<>();
        for (Object o : list(call(req, 600))) out.add(meta(map(o)));
        return out;
    }

    @Override
    public MessageMeta meta(String id) {
        return meta(map(call(Map.of("op", "meta", "id", id), 60)));
    }

    @Override
    public MessageBody body(String id) {
        Map<String, Object> r = map(call(Map.of("op", "body", "id", id), 120));
        List<String> names = new ArrayList<>();
        for (Object n : list(r.get("attachments"))) if (n != null) names.add(n.toString());
        return new MessageBody(str(r.get("text")), str(r.get("html")), names);
    }

    @Override
    public Attachment attachment(String id, int index) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("op", "attachment");
        req.put("id", id);
        req.put("index", index);
        req.put("dir", scratch.toString());
        Map<String, Object> r = map(call(req, 300));
        String name = str(r.get("name"));
        byte[] bytes = Base64.getDecoder().decode(str(r.get("data")) == null ? "" : str(r.get("data")));
        String type = name == null ? null : URLConnection.guessContentTypeFromName(name);
        return new Attachment(name == null ? "attachment" : name, type == null ? "application/octet-stream" : type, bytes);
    }

    @Override
    public Sent send(Outgoing o, List<Attachment> files) {
        Path dir = null;
        try {
            Map<String, Object> req = outgoing("send", o);
            List<String> paths = new ArrayList<>();
            if (files != null && !files.isEmpty()) {
                dir = scratch.resolve(UUID.randomUUID().toString().replace("-", ""));
                Files.createDirectories(dir);
                for (Attachment a : files) {
                    // Each file in a folder of its own, so it keeps its own name (Outlook names the
                    // attachment after the file) even when two have the same one.
                    Path one = dir.resolve(String.valueOf(paths.size()));
                    Files.createDirectories(one);
                    Path p = one.resolve(safeName(a.filename(), 0));
                    Files.write(p, a.bytes() == null ? new byte[0] : a.bytes());
                    paths.add(p.toString());
                }
            }
            req.put("files", paths);
            req.put("sendKey", o.sendKey());
            Map<String, Object> r = map(call(req, 300));
            return new Sent(str(r.get("sendKey")), str(r.get("threadId")));
        } catch (IOException ex) {
            throw new OutlookException(500, "Could not hand the attachments to Outlook: " + ex.getMessage());
        } finally {
            removeScratch(dir);
        }
    }

    @Override
    public DraftRef saveDraft(String draftId, Outgoing o) {
        Map<String, Object> req = outgoing("saveDraft", o);
        req.put("draftId", draftId);
        Map<String, Object> r = map(call(req, 120));
        return new DraftRef(str(r.get("draftId")), str(r.get("version")), str(r.get("threadId")));
    }

    @Override
    public void deleteDraft(String draftId) {
        call(Map.of("op", "deleteDraft", "draftId", draftId == null ? "" : draftId), 60);
    }

    @Override
    public List<DraftRef> listDrafts() {
        List<DraftRef> out = new ArrayList<>();
        for (Object o : list(call(Map.of("op", "listDrafts"), 300))) {
            Map<String, Object> d = map(o);
            out.add(new DraftRef(str(d.get("draftId")), str(d.get("version")), str(d.get("threadId"))));
        }
        return out;
    }

    @Override
    public DraftContent draft(String draftId) {
        Map<String, Object> d = map(call(Map.of("op", "draft", "draftId", draftId), 60));
        return new DraftContent(str(d.get("draftId")), str(d.get("version")), str(d.get("threadId")), str(d.get("to")),
            str(d.get("cc")), str(d.get("bcc")), str(d.get("subject")), str(d.get("text")), str(d.get("html")),
            dateTime(str(d.get("updated"))));
    }

    // ---- the bridge -----------------------------------------------------------------------

    /** One request to the bridge and its answer's result; Outlook's refusal becomes an OutlookException. */
    private synchronized Object call(Map<String, Object> request, int timeoutSeconds) {
        ensureRunning();
        String line;
        try {
            toBridge.write(JSON.writeValueAsString(request));
            toBridge.newLine();
            toBridge.flush();
            line = fromBridge.poll(timeoutSeconds, TimeUnit.SECONDS);
        } catch (IOException ex) {
            stop();
            throw new OutlookException(409, "Echo lost its connection to Outlook (" + ex.getMessage() + "). Try again.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            stop();
            throw new OutlookException(500, "Stopped while waiting for Outlook.");
        }
        if (line == null) {
            stop();
            throw new OutlookException(504, "Outlook did not answer within " + timeoutSeconds + " seconds. Try again.");
        }
        if (line.equals(EOF)) {
            stop();
            throw new OutlookException(409, "Echo's link to Outlook stopped. Try again; if it keeps stopping, see logs\\echo.log.");
        }
        Map<String, Object> answer;
        try {
            answer = map(JSON.readValue(line, Map.class));
        } catch (JacksonException ex) {
            throw new OutlookException(500, "Outlook's answer could not be read: " + clip(line, 200));
        }
        if (Boolean.TRUE.equals(answer.get("ok"))) return answer.get("result");
        int status = answer.get("status") instanceof Number n ? n.intValue() : 500;
        if (status == 409) account = null;
        throw new OutlookException(status, str(answer.get("error")) == null ? "Outlook said no." : str(answer.get("error")));
    }

    private static final String EOF = "\u0000EOF";

    private void ensureRunning() {
        if (process != null && process.isAlive()) return;
        if (!Files.isRegularFile(bridge)) {
            throw new OutlookException(409, "Echo's Outlook bridge is missing: " + bridge + ". Run update-echo.cmd.");
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", bridge.toString());
            pb.redirectErrorStream(false);
            Process p = pb.start();
            BlockingQueue<String> q = new LinkedBlockingQueue<>();
            Thread out = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = r.readLine()) != null) if (!l.isBlank()) q.add(l);
                } catch (IOException ignored) {
                    // the bridge stopped; the waiting call is told below
                }
                q.add(EOF);
            }, "outlook-bridge-out");
            out.setDaemon(true);
            out.start();
            Thread err = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = r.readLine()) != null) if (!l.isBlank()) log.info("Outlook bridge: {}", l);
                } catch (IOException ignored) {
                    // the bridge stopped
                }
            }, "outlook-bridge-err");
            err.setDaemon(true);
            err.start();
            process = p;
            fromBridge = q;
            toBridge = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
            log.info("Email Client: started the Outlook bridge (process {})", p.pid());
        } catch (IOException ex) {
            throw new OutlookException(409, "Could not start Windows PowerShell for Outlook: " + ex.getMessage());
        }
    }

    @PreDestroy
    synchronized void stop() {
        Process p = process;
        process = null;
        account = null;
        if (p == null) return;
        try { toBridge.close(); } catch (IOException ignored) { /* closing anyway */ }
        p.destroy();
    }

    // ---- mapping ------------------------------------------------------------------------

    private static Map<String, Object> outgoing(String op, Outgoing o) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("op", op);
        req.put("baseKind", o.baseKind() == null ? "new" : o.baseKind());
        req.put("baseId", o.baseId());
        req.put("to", GmailClientMime.addresses(o.to()));
        req.put("cc", GmailClientMime.addresses(o.cc()));
        req.put("bcc", GmailClientMime.addresses(o.bcc()));
        req.put("subject", o.subject() == null ? "" : o.subject());
        req.put("text", o.text() == null ? "" : o.text());
        req.put("keepOriginalAttachments", o.keepOriginalAttachments());
        return req;
    }

    private static MessageMeta meta(Map<String, Object> m) {
        return new MessageMeta(str(m.get("id")), str(m.get("threadId")), str(m.get("from")), blank(str(m.get("to"))),
            blank(str(m.get("cc"))), blank(str(m.get("replyTo"))), str(m.get("subject")), str(m.get("snippet")),
            blank(str(m.get("messageId"))), dateTime(str(m.get("received"))), blank(str(m.get("sendKey"))));
    }

    /** A file name Windows will take, kept recognizable; the index keeps two files of one name apart. */
    static String safeName(String name, int index) {
        String n = name == null || name.isBlank() ? "attachment" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (n.length() > 150) n = n.substring(n.length() - 150);
        return index == 0 ? n : index + "_" + n;
    }

    /** The files written for one send, removed once Outlook has them (Echo's own scratch copies). */
    private static void removeScratch(Path dir) {
        if (dir == null) return;
        try (var walk = Files.walk(dir)) {
            for (Path f : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(f);
        } catch (IOException ex) {
            log.info("Email Client: could not tidy {}: {}", dir, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) { return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }

    private static List<?> list(Object o) { return o instanceof List<?> l ? l : List.of(); }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s; }

    private static LocalDateTime dateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDateTime.parse(s); } catch (RuntimeException ex) { return null; }
    }

    private static String clip(String s, int max) { return s == null || s.length() <= max ? s : s.substring(0, max); }
}
