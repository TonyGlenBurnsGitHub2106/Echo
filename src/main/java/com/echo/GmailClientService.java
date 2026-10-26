package com.echo;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gmail Client (Story 5569) -- a Leonardo mail client whose source is Tony's real Gmail account,
 * reached through the Mail Triage connection.
 *
 * Story 5654: echoed into Echo as its Email Client. The source is the classic Outlook app signed in
 * on this computer ({@link OutlookClient}) instead of Gmail; "Gmail" below reads "Outlook". Echo has
 * no records, so emails link to nothing; the whitelist and blacklist are Echo's own
 * ({@link MailTriageService}) and never become Outlook rules. Every Echo change is marked Story 5654.
 *
 * Check mail (pressed, or the page opening) reads the headers of new Gmail inbox mail into
 * Leonardo. Mail from a BLACKLISTED sender is never read in. What shows is decided at read time:
 * only senders WHITELISTED in Mail Triage, filed into the inbox Tony chose for that sender (or
 * Unclassified). Delete hides a message in Leonardo only. Reply, reply all, forward and new mail
 * are sent through Gmail. Nothing here changes, moves or deletes anything in Gmail.
 *
 * A check runs on one background thread, one check at a time, so a long first check (a month of
 * mail) never holds a request open. (Story 5654: Outlook has no quota; the bridge answers one
 * request at a time.)
 *
 * Nothing here runs on a clock: every Gmail call starts from the page opening or a button.
 */
@Service
public class GmailClientService {

    private static final Logger log = LoggerFactory.getLogger(GmailClientService.class);

    static final int WINDOW_DAYS = 30;
    static final int MAX_NEW_PER_CHECK = 2000;
    static final int MAX_OLDER_DAYS = 365;
    static final long CHECK_WAIT_MILLIS = 3000;
    static final int MAX_BODIES_PER_CHECK = 1500;   // the whole message saved in Leonardo (Story 5576)
    static final long RESEND_GUARD_SECONDS = 15;
    static final int LIST_LIMIT = 5000;   // Story 5654: the most headers one Outlook listing returns
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a", Locale.US);

    private final GmailClientRepository repo;
    private final MailTriageRepository triageRepo;
    private final MailTriageService triage;
    private final OutlookClient outlook;   // Story 5654: Outlook, not Gmail
    private final AtomicBoolean checking = new AtomicBoolean(false);
    private final ExecutorService checker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gmail-client-check");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Instant> recentSends = new ConcurrentHashMap<>();
    private volatile Progress progress;

    public GmailClientService(GmailClientRepository repo, MailTriageRepository triageRepo,
                              MailTriageService triage, OutlookClient outlook) {
        this.repo = repo;
        this.triageRepo = triageRepo;
        this.triage = triage;
        this.outlook = outlook;
    }

    /** Story 5654: the Outlook mailbox's address, or null when Outlook cannot be reached (status says why). */
    private String accountOrNull(Map<String, Object> m) {
        try {
            return outlook.account();
        } catch (OutlookClient.OutlookException ex) {
            if (m != null) m.put("outlookError", ex.getMessage());
            return null;
        }
    }

    // ---- status and checking ------------------------------------------------------------

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        String account = accountOrNull(m);   // Story 5654: connected = Outlook is running and answers
        m.put("connected", account != null);
        m.put("account", account);
        Map<String, Object> last = repo.lastCheck(false);
        m.put("lastCheck", last == null ? null : check(last));
        m.put("checking", progressMap());
        m.put("reachDate", iso(repo.reach()));
        Map<String, Object> bc = repo.bodyCounts();
        m.put("savedInLeonardo", num(bc.get("Saved")));
        m.put("notYetSaved", num(bc.get("Missing")));
        return m;
    }

    /**
     * Reads new Gmail inbox mail into Leonardo on the background thread. A check that finishes
     * within {@link #CHECK_WAIT_MILLIS} answers with its result; a longer one answers
     * {@code checking: true} and the page follows it through {@link #status()}. A press while a
     * check is going answers with that check's progress instead of starting a second one.
     */
    public Map<String, Object> check() { return runFrom(null); }

    /**
     * Read older mail: a check that starts {@code days} before how far back the client has read
     * (or before the usual 30 days, the first time) and reads everything from there up to now --
     * so it is an ordinary complete check, and the next Check mail still starts where it should.
     * Mail already kept is skipped without asking Gmail for it again.
     */
    public Map<String, Object> checkOlder(int days) {
        if (days < 1 || days > MAX_OLDER_DAYS) throw new IllegalArgumentException("Read between 1 and " + MAX_OLDER_DAYS + " more days.");
        LocalDateTime reach = repo.reach();
        LocalDateTime from = reach != null ? reach : LocalDateTime.now().minusDays(WINDOW_DAYS);
        return runFrom(from.minusDays(days));
    }

    /** When the newest check (any, complete or not) started -- the nightly check's "done today?" test. */
    LocalDateTime lastCheckStarted() {
        Map<String, Object> last = repo.lastCheck(false);
        return last == null ? null : dateTime(last.get("StartedDate"));
    }

    /** True while a check is running (a Check mail press, or the nightly check). */
    boolean isChecking() { return checking.get(); }

    /** Starts a check reading from start (null: where the last complete check left off). */
    private Map<String, Object> runFrom(LocalDateTime start) {
        if (!checking.compareAndSet(false, true)) {
            Map<String, Object> now = progressMap();
            return now != null ? now : Map.of("checking", true);
        }
        Future<Map<String, Object>> job;
        try {
            outlook.account();   // Story 5654: refuse now, with a 409, when Outlook is not running
            Progress p = new Progress(start != null ? start : windowStart());
            progress = p;
            job = checker.submit(() -> {
                try {
                    return runCheck(p);
                } finally {
                    progress = null;
                    checking.set(false);
                }
            });
        } catch (RuntimeException ex) {
            progress = null;
            checking.set(false);
            throw ex;
        }
        try {
            return job.get(CHECK_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            return progressOrDone();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return progressOrDone();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException("Check mail failed: " + ex.getCause());
        }
    }

    private Map<String, Object> runCheck(Progress p) {
        int checkId = repo.startCheck(p.windowStart);
        boolean capped = false;
        try {
            Set<String> known = repo.knownSince(p.windowStart);
            Map<String, String> dispositions = new HashMap<>();
            // Story 5654: the Inbox is read a few days at a time (Outlook has no paging), so no one call
            // holds Outlook -- and the page's other requests -- for long.
            for (OutlookClient.MessageMeta meta : listSliced("inbox", p.windowStart, () -> p.sliceCapped = true)) {
                String id = meta.id();
                p.listed++;
                if (id == null || known.contains(id)) continue;
                if (p.added + p.skipped >= MAX_NEW_PER_CHECK) { capped = true; break; }
                MailTriageSenders.Key k = MailTriageSenders.keyFor(meta.from());
                String key = k == null ? null : k.key();
                String disp = key == null ? null : dispositions.computeIfAbsent(key, this::disposition);
                if ("black".equals(disp)) { p.skipped++; continue; }   // blacklisted mail is never read in
                LocalDateTime received = meta.received() == null ? LocalDateTime.now() : meta.received();
                if (repo.recordMessage(id, clip(meta.threadId(), 64), key, clip(meta.from(), 320), clip(meta.to(), 1000),
                        clip(meta.cc(), 1000), clip(meta.subject(), 500), clip(meta.snippet(), 500),
                        clip(meta.messageIdHeader(), 500), received)) {
                    p.added++;
                    // No Mail Triage rule yet: it lands in the Undecided tab, and the result says so.
                    if (disp == null || disp.isEmpty()) p.undecided++;
                }
            }
            if (p.sliceCapped) capped = true;
            if (readSent(p)) capped = true;
            Map<String, Object> done = repo.finishCheck(checkId, p.listed, p.added, !capped,
                capped ? "Stopped after " + MAX_NEW_PER_CHECK + " new messages; check again for the rest." : null);
            String draftError = readDrafts(p);
            saveBodies(p);
            Map<String, Object> out = check(done);
            out.put("checking", false);
            out.put("bodiesSaved", p.bodiesSaved);
            out.put("undecidedAdded", p.undecided);
            out.put("sentAdded", p.sentAdded);
            out.put("draftsAdded", p.draftsAdded);
            out.put("draftsChanged", p.draftsChanged);
            out.put("draftsGone", p.draftsGone);
            if (draftError != null) out.put("draftError", draftError);
            return out;
        } catch (RuntimeException ex) {
            repo.finishCheck(checkId, p.listed, p.added, false, clip(ex.getMessage(), 1000));
            log.info("Email Client: check {} stopped after {} new messages: {}", checkId, p.added, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Story 5596: Gmail's Sent folder, so mail Tony sent from Gmail itself is in the Sent tab too
     * (mail sent from the Gmail Client is kept as it goes, and skipped here). It always looks back at
     * least the usual 30 days -- not just to the last check -- so the first check after the Sent tab
     * arrived, or after a gap, still finds the month's sent mail; mail already kept costs only its id
     * in the listing, never a call of its own. True when it stopped at the per-check cap. Nothing is
     * skipped for a sender: it is all Tony's mail.
     */
    private boolean readSent(Progress p) {
        LocalDateTime floor = LocalDateTime.now().minusDays(WINDOW_DAYS);
        LocalDateTime start = p.windowStart.isBefore(floor) ? p.windowStart : floor;
        Set<String> known = repo.sentKnownSince(start);
        boolean[] sliceCapped = { false };
        for (OutlookClient.MessageMeta meta : listSliced("sent", start, () -> sliceCapped[0] = true)) {
            String id = meta.id();
            if (id == null || known.contains(id)) continue;
            // Story 5654: mail sent from Echo was kept at once under the key it was stamped with; now
            // that Outlook's Sent Items has it, the kept row takes its real id (and conversation)
            // instead of a twin, and its body is read again from Outlook's copy -- a forward there
            // carries the original's files too, in Outlook's order.
            if (meta.sendKey() != null && known.contains(SENT_KEY_PREFIX + meta.sendKey())) {
                Integer rowId = repo.rekeySent(SENT_KEY_PREFIX + meta.sendKey(), id, clip(meta.threadId(), 64),
                    clip(meta.messageIdHeader(), 500));
                if (rowId != null) {
                    try { fetchAndSaveSent(rowId, id); }
                    catch (RuntimeException ex) { log.info("Email Client: sent {} kept; its body is read later: {}", rowId, ex.getMessage()); }
                }
                continue;
            }
            if (p.sentAdded >= MAX_NEW_PER_CHECK) return true;
            Map<String, Object> rec = repo.recordSent(id, clip(meta.threadId(), 64), clip(meta.from(), 320), clip(meta.to(), 1000),
                clip(meta.cc(), 1000), null, clip(meta.subject(), 500), clip(meta.snippet(), 500),
                clip(meta.messageIdHeader(), 500), meta.received() == null ? LocalDateTime.now() : meta.received(), false);
            if (num(rec.get("Recorded")) > 0) p.sentAdded++;
        }
        return sliceCapped[0];
    }

    /** Story 5654: how many days one Outlook listing covers. */
    static final int SLICE_DAYS = 3;

    /**
     * Story 5654: a folder's headers from {@code from} up to now, read {@link #SLICE_DAYS} days per call
     * -- each call short, so the status line, opening a message or sending never waits long behind a
     * check. A slice that comes back full (LIST_LIMIT) calls {@code onCapped}: the check is then not
     * complete, and the next one reads that stretch again.
     */
    private List<OutlookClient.MessageMeta> listSliced(String folder, LocalDateTime from, Runnable onCapped) {
        List<OutlookClient.MessageMeta> out = new ArrayList<>();
        LocalDateTime end = LocalDateTime.now().plusMinutes(1);
        for (LocalDateTime a = from; a.isBefore(end); a = a.plusDays(SLICE_DAYS)) {
            LocalDateTime b = a.plusDays(SLICE_DAYS);
            List<OutlookClient.MessageMeta> slice = outlook.list(folder, a, b.isBefore(end) ? b : null, LIST_LIMIT);
            if (slice.size() >= LIST_LIMIT) onCapped.run();
            out.addAll(slice);
        }
        return out;
    }

    /** Story 5654: the id a sent message is kept under until Check mail finds it in Sent Items. */
    static final String SENT_KEY_PREFIX = "echo:";

    /**
     * Story 5576: every shown message is kept whole in Leonardo. After the headers, a check saves the
     * body of each shown message that has none yet -- the new ones and, the first time, everything
     * already kept -- paced like every other Gmail call. A failure here never undoes the check: what
     * was saved stays saved, and the next check carries on from there. Sent mail with no saved body
     * (sent from Gmail itself, Story 5596) follows, within the same per-check limit.
     */
    private void saveBodies(Progress p) {
        List<Map<String, Object>> missing = repo.listMissingBodies(MAX_BODIES_PER_CHECK);
        int room = MAX_BODIES_PER_CHECK - missing.size();
        List<Map<String, Object>> missingSent = room > 0 ? repo.listMissingSentBodies(room) : List.of();
        p.bodiesToSave = missing.size() + missingSent.size();
        try {
            for (Map<String, Object> m : missing) {
                if (Thread.currentThread().isInterrupted()) return;
                fetchAndSave(num(m.get("GmailClientMessageID")), (String) m.get("GmailID"));
                p.bodiesSaved++;
            }
            for (Map<String, Object> m : missingSent) {
                if (Thread.currentThread().isInterrupted()) return;
                fetchAndSaveSent(num(m.get("GmailClientSentMessageID")), (String) m.get("GmailID"));
                p.bodiesSaved++;
            }
        } catch (RuntimeException ex) {
            log.info("Email Client: saving whole messages stopped after {} of {}: {}", p.bodiesSaved, p.bodiesToSave, ex.getMessage());
        }
    }

    /** Reads one sent message's body from Gmail and keeps it in Leonardo. */
    private void fetchAndSaveSent(int id, String gmailId) {
        OutlookClient.MessageBody b;
        if (gmailId == null || gmailId.startsWith(SENT_KEY_PREFIX)) return;   // Story 5654: not in Sent Items yet
        try {
            b = outlook.body(gmailId);
        } catch (OutlookClient.OutlookException ex) {
            if (ex.status() != 404) throw ex;
            repo.saveSentBody(id, null, null, null, true);   // Gmail no longer has it; stop asking
            return;
        }
        String html = b.html() == null ? null : EmailHtmlSanitizer.sanitizeForReader(b.html());
        String text = b.text() != null ? b.text() : GmailClientMime.htmlToText(b.html());
        String names = b.attachments() == null || b.attachments().isEmpty() ? null : clip(String.join("\n", b.attachments()), 2000);
        repo.saveSentBody(id, html, text, names, false);
    }

    /** A sent message's saved body, saving it first if Leonardo does not have it yet. */
    private Map<String, Object> savedSentBody(Map<String, Object> r) {
        int id = num(r.get("GmailClientSentMessageID"));
        Map<String, Object> body = repo.getSentBody(id);
        if (body == null) {
            fetchAndSaveSent(id, (String) r.get("GmailID"));
            body = repo.getSentBody(id);
        }
        return body;
    }

    /** Reads one message's body from Gmail and keeps it in Leonardo (sanitized HTML, text, attachment names). */
    private void fetchAndSave(int id, String gmailId) {
        OutlookClient.MessageBody b;
        if (gmailId == null || gmailId.startsWith(SENT_KEY_PREFIX)) return;   // Story 5654: not in Sent Items yet
        try {
            b = outlook.body(gmailId);
        } catch (OutlookClient.OutlookException ex) {
            if (ex.status() != 404) throw ex;
            repo.saveBody(id, null, null, null, true);   // Gmail no longer has it; stop asking
            return;
        }
        String html = b.html() == null ? null : EmailHtmlSanitizer.sanitizeForReader(b.html());
        String text = b.text() != null ? b.text() : GmailClientMime.htmlToText(b.html());
        String names = b.attachments() == null || b.attachments().isEmpty() ? null : clip(String.join("\n", b.attachments()), 2000);
        repo.saveBody(id, html, text, names, false);
    }

    /**
     * Story 5591: re-read from Gmail, up to {@code top} at a time, the bodies saved before
     * {@code before} -- the ones kept before the reader kept each message's own formatting -- and
     * save them again the new way. Call again while {@code more} is true. One message that fails
     * is counted and skipped; the rest carry on.
     */
    public Map<String, Object> resaveBodies(LocalDateTime before, int top) {
        int saved = 0, failed = 0;
        String lastError = null;
        for (Map<String, Object> r : repo.listBodiesSavedBefore(before, top)) {
            int id = num(r.get("ID"));
            try {
                if ("sent".equals(r.get("Kind"))) fetchAndSaveSent(id, (String) r.get("GmailID"));
                else fetchAndSave(id, (String) r.get("GmailID"));
                saved++;
            } catch (RuntimeException ex) {
                failed++;
                lastError = ex.getMessage();
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("saved", saved);
        m.put("failed", failed);
        // More to do only if this round got somewhere; a round of nothing but failures stops the caller.
        m.put("more", saved > 0 && repo.listBodiesSavedBefore(before, failed + 1).size() > failed);
        m.put("error", lastError);
        return m;
    }

    /** The saved body, saving it first if Leonardo does not have it yet (only then is Gmail asked). */
    private Map<String, Object> savedBody(Map<String, Object> r) {
        int id = num(r.get("GmailClientMessageID"));
        Map<String, Object> body = repo.getBody(id);
        if (body == null) {
            fetchAndSave(id, (String) r.get("GmailID"));
            body = repo.getBody(id);
        }
        return body;
    }

    private static List<String> attachmentList(Map<String, Object> body) {
        Object names = body == null ? null : body.get("AttachmentNames");
        if (names == null || names.toString().isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String n : names.toString().split("\n")) if (!n.isBlank()) out.add(n.trim());
        return out;
    }

    private String disposition(String key) {
        Map<String, Object> r = triageRepo.getRuleByKey(key);
        return r == null ? "" : String.valueOf(r.get("Disposition"));
    }

    /** Where the next check starts reading: the last complete check, but never more than 30 days back. */
    LocalDateTime windowStart() {
        LocalDateTime floor = LocalDateTime.now().minusDays(WINDOW_DAYS);
        Map<String, Object> last = repo.lastCheck(true);
        LocalDateTime lastStart = last == null ? null : dateTime(last.get("StartedDate"));
        return lastStart != null && lastStart.isAfter(floor) ? lastStart : floor;
    }

    private Map<String, Object> progressOrDone() {
        Map<String, Object> m = progressMap();
        if (m != null) return m;
        Map<String, Object> last = repo.lastCheck(false);
        Map<String, Object> out = last == null ? new LinkedHashMap<>() : check(last);
        out.put("checking", false);
        return out;
    }

    private Map<String, Object> progressMap() {
        Progress p = progress;
        if (p == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("checking", true);
        m.put("startedDate", iso(p.started));
        m.put("windowStartDate", iso(p.windowStart));
        m.put("messagesListed", p.listed);
        m.put("messagesAdded", p.added);
        m.put("undecidedAdded", p.undecided);
        m.put("sentAdded", p.sentAdded);
        m.put("bodiesToSave", p.bodiesToSave);
        m.put("bodiesSaved", p.bodiesSaved);
        m.put("draftsAdded", p.draftsAdded);
        return m;
    }

    private static final class Progress {
        final LocalDateTime started = LocalDateTime.now();
        final LocalDateTime windowStart;
        volatile int listed, added, skipped, undecided;   // written by the check thread only
        volatile int sentAdded;                           // Story 5596: read from Gmail's Sent folder
        volatile int bodiesToSave, bodiesSaved;  // Story 5576: whole messages saved after the headers
        volatile int draftsAdded, draftsChanged, draftsGone;   // Story 5620: Gmail's Drafts folder
        volatile boolean sliceCapped;                         // Story 5654: an Outlook listing came back full
        Progress(LocalDateTime windowStart) { this.windowStart = windowStart; }
    }

    @PreDestroy
    void stopChecker() { checker.shutdownNow(); }

    // ---- lists --------------------------------------------------------------------------

    /** One page of a tab, with every tab's count for the same filters. */
    public Map<String, Object> list(String scope, Integer inboxId, GmailClientRepository.Filter f, int offset, int size) {
        String sc = scope(scope, inboxId);
        int pageSize = size <= 0 ? 50 : Math.min(size, 200);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : repo.listMessages(sc, sc.equals("inbox") ? inboxId : null, f,
                Math.max(0, offset), pageSize + 1)) {
            rows.add(listRow(r));
        }
        boolean more = rows.size() > pageSize;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", more ? rows.subList(0, pageSize) : rows);
        m.put("hasMore", more);
        m.put("tabs", tabs(f));
        return m;
    }

    /**
     * Story 5608: one page of CONVERSATIONS for the same tab and filters -- each row the newest
     * matching message of its Gmail thread, with threadCount (received and sent), sentCount,
     * matchCount and matchUnread -- plus totalThreads and every tab's count.
     */
    public Map<String, Object> listThreads(String scope, Integer inboxId, GmailClientRepository.Filter f, int offset, int size) {
        String sc = scope(scope, inboxId);
        int pageSize = size <= 0 ? 50 : Math.min(size, 200);
        List<Map<String, Object>> rows = new ArrayList<>();
        int total = 0;
        for (Map<String, Object> r : repo.listThreads(sc, sc.equals("inbox") ? inboxId : null, f, Math.max(0, offset), pageSize + 1)) {
            Map<String, Object> m = listRow(r);
            m.put("threadCount", num(r.get("ThreadCount")));
            m.put("sentCount", num(r.get("SentCount")));
            m.put("matchCount", num(r.get("MatchCount")));
            m.put("matchUnread", num(r.get("MatchUnread")));
            total = num(r.get("TotalThreads"));
            rows.add(m);
        }
        boolean more = rows.size() > pageSize;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", more ? rows.subList(0, pageSize) : rows);
        m.put("hasMore", more);
        m.put("totalThreads", total);
        m.put("tabs", tabs(f));
        return m;
    }

    /**
     * Every message of the conversation a message belongs to, oldest first -- received (kind inbox)
     * and sent (kind sent), headers and preview only; a piece's body is read when it is opened.
     * Null when there is no such message.
     */
    public Map<String, Object> conversation(String kind, int id) {
        List<Map<String, Object>> rows = repo.thread(kind, id);
        if (rows.isEmpty()) return null;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", r.get("Kind"));
            m.put("id", num(r.get("ID")));
            m.put("gmailId", r.get("GmailID"));
            m.put("senderKey", r.get("SenderKey"));
            m.put("from", r.get("FromHeader"));
            m.put("to", r.get("ToHeader"));
            m.put("cc", r.get("CcHeader"));
            m.put("subject", r.get("Subject"));
            m.put("snippet", GmailClientMime.unescapeSnippet((String) r.get("Snippet")));
            m.put("date", iso(r.get("MessageDate")));
            m.put("isRead", truthy(r.get("IsRead")));
            m.put("hasAttachments", truthy(r.get("HasAttachments")));
            out.add(m);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("messages", out);
        return m;
    }

    /** A conversation row's whole conversation: every inbox message in the threads of these ids. */
    public Map<String, Object> threadMessageIds(List<Integer> ids) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ids", repo.threadMessageIds(ids));
        return m;
    }

    /** all | followup | flagged | inbox | unclassified | undecided (anything else is all); an inbox needs its id. */
    private static String scope(String scope, Integer inboxId) {
        String sc = scope == null ? "all" : scope.trim().toLowerCase(Locale.ROOT);
        if (!sc.equals("inbox") && !sc.equals("unclassified") && !sc.equals("followup") && !sc.equals("undecided")
            && !sc.equals("flagged")) sc = "all";
        if (sc.equals("inbox") && (inboxId == null || inboxId <= 0)) throw new IllegalArgumentException("Choose an inbox.");
        return sc;
    }

    /**
     * All, Undecided right beside it (Tony, 2026-10-05), the built-in Follow up, Sent (Story 5596),
     * then each inbox in Tony's order, then Unclassified -- each with its count and unread (Follow up
     * and Sent also with how many follow-ups are overdue; Sent with how many wait for a reply).
     */
    public List<Map<String, Object>> tabs(GmailClientRepository.Filter f) {
        Map<String, int[]> byKey = new HashMap<>();
        for (Map<String, Object> c : repo.counts(f)) {
            String b = String.valueOf(c.get("Bucket"));
            String key = "inbox".equals(b) ? "inbox:" + num(c.get("BucketKey")) : b;
            byKey.put(key, new int[] { num(c.get("Cnt")), num(c.get("Unread")), num(c.get("Overdue")) });
        }
        List<Map<String, Object>> tabs = new ArrayList<>();
        tabs.add(tab("all", null, "All", byKey.get("all")));
        // Mail from senders not decided on in Mail Triage. Every other tab shows whitelisted mail only,
        // so without this tab such mail was read in and never seen (2026-10-04).
        tabs.add(tab("undecided", null, "Undecided", byKey.get("undecided")));
        tabs.add(tab("followup", null, "Follow up", byKey.get("followup")));
        // Story 5608: every message carrying a flag, grouped by flag.
        tabs.add(tab("flagged", null, "Flagged", byKey.get("flagged")));
        // Story 5596: the mail Tony sent. Its "unread" is always 0; waiting = no reply yet.
        Map<String, Object> sc = repo.sentCounts(sentFilter(f, "all"));
        Map<String, Object> sent = tab("sent", null, "Sent", new int[] { num(sc.get("Cnt")), 0, num(sc.get("Overdue")) });
        sent.put("waiting", num(sc.get("Waiting")));
        tabs.add(sent);
        // Story 5620: the drafts not sent yet -- written here, or in Gmail itself.
        tabs.add(tab("drafts", null, "Drafts", new int[] { repo.draftCount(f.search()), 0, 0 }));
        for (Map<String, Object> i : repo.listInboxes()) {
            int id = num(i.get("GmailClientInboxID"));
            tabs.add(tab("inbox", id, (String) i.get("InboxName"), byKey.get("inbox:" + id)));
        }
        tabs.add(tab("unclassified", null, "Unclassified", byKey.get("unclassified")));
        return tabs;
    }

    private static Map<String, Object> tab(String scope, Integer id, String name, int[] counts) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("scope", scope);
        t.put("inboxId", id);
        t.put("name", name);
        t.put("count", counts == null ? 0 : counts[0]);
        t.put("unread", counts == null ? 0 : counts[1]);
        t.put("overdue", counts == null || counts.length < 3 ? 0 : counts[2]);
        return t;
    }

    // ---- one message --------------------------------------------------------------------

    /** The message's headers, and its body fetched from Gmail. Opening it marks it read in Leonardo. */
    public Map<String, Object> get(int id) { return get(id, true); }

    /**
     * The same, choosing whether it counts as read. The preview pane picking a message on its own
     * passes false (Story 5607): auto-picking the newest unread message used to mark it read before Tony
     * ever saw it, and under the Unread filter each refresh then picked -- and read -- the next one.
     */
    public Map<String, Object> get(int id, boolean markRead) {
        Map<String, Object> r = repo.getMessage(id);
        if (r == null) return null;
        Map<String, Object> m = listRow(r);
        m.put("to", r.get("ToHeader"));
        m.put("cc", r.get("CcHeader"));
        // Story 5608: the message as an entity, the inbox its domain is in, its conversation, its links.
        m.put("entityId", r.get("EntityID") == null ? null : num(r.get("EntityID")));
        m.put("domainInboxId", r.get("DomainInboxID") == null ? null : num(r.get("DomainInboxID")));
        m.put("domainInboxName", r.get("DomainInboxName"));
        m.put("threadCount", Math.max(1, num(r.get("ThreadCount"))));
        m.put("links", List.of());   // Story 5654: Echo has no records to link an email to
        // No Mail Triage rule yet: the reader offers Whitelist / Blacklist (the Undecided tab, 2026-10-04).
        String key = (String) r.get("SenderKey");
        m.put("senderDecided", key == null || !disposition(key).isEmpty());
        // Story 5576: the body comes from Leonardo; Gmail is asked only for one not saved yet.
        Map<String, Object> body = savedBody(r);
        if (body == null || truthy(body.get("IsGoneFromGmail"))) {
            m.put("bodyHtml", null);
            m.put("bodyText", "This message is no longer in Outlook, so its text cannot be shown.");
            m.put("attachments", List.of());
        } else {
            m.put("bodyHtml", body.get("BodyHtml"));
            m.put("bodyText", body.get("BodyText"));
            m.put("attachments", attachmentList(body));
        }
        m.put("savedInLeonardo", body != null && !truthy(body.get("IsGoneFromGmail")));
        if (markRead && !truthy(r.get("IsRead"))) repo.setRead(id, true);
        m.put("isRead", markRead || truthy(r.get("IsRead")));
        return m;
    }

    /** Null when there is no such message. */
    public Map<String, Object> setRead(int id, boolean read) {
        return repo.setRead(id, read) ? Map.of("id", id, "isRead", read) : null;
    }

    /** Delete (hide) or restore, in Leonardo only. Null when there is no such message. */
    public Map<String, Object> setHidden(int id, boolean hidden) {
        if (!repo.setHidden(id, hidden)) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("hidden", hidden);
        m.put("tabs", tabs(emptyFilter()));
        return m;
    }

    // ---- many at once ------------------------------------------------------------------

    private static final Map<String, String> BULK_ACTIONS =
        Map.of("read", "read", "unread", "unread", "delete", "hide", "restore", "unhide");

    /** The selected messages at once: read, unread, delete (Leonardo only) or restore. */
    public Map<String, Object> bulk(List<Integer> ids, String action) {
        String a = action == null ? null : BULK_ACTIONS.get(action.trim().toLowerCase(Locale.ROOT));
        if (a == null) throw new IllegalArgumentException("Choose read, unread, delete or restore.");
        return counted(repo.setMany(ids, a));
    }

    /** Every message the tab shows for these filters -- not just the loaded page -- read or unread. */
    public Map<String, Object> markAll(String scope, Integer inboxId, GmailClientRepository.Filter f, boolean read) {
        return counted(repo.markAll(scope(scope, inboxId), inboxId, f, read));
    }

    /** Flag messages to follow up on a day (yyyy-mm-dd) with an optional note; re-flagging moves the day. */
    public Map<String, Object> followUp(List<Integer> ids, String dueDate, String note) {
        String n = note == null ? null : note.trim();
        if (n != null && n.length() > 300) throw new IllegalArgumentException("Keep the note to 300 characters.");
        return counted(repo.setFollowUp(ids, dueDate, n));
    }

    /** Close the open follow-ups of these messages. */
    public Map<String, Object> followUpDone(List<Integer> ids) { return counted(repo.followUpDone(ids)); }

    private Map<String, Object> counted(int n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("updated", n);
        m.put("tabs", tabs(emptyFilter()));
        return m;
    }

    // ---- sending ------------------------------------------------------------------------
    // Story 5654: everything goes out through Outlook. A reply, forward or write-again starts from the
    // message in Outlook (so Outlook keeps the conversation together); the words are always Echo's own,
    // plain text, with the original quoted below exactly as Leonardo quotes it.

    public Map<String, Object> reply(int id, String body, boolean replyAll) { return reply(id, body, replyAll, List.of()); }

    /** A reply, with files attached (Story 5608: from the device). */
    public Map<String, Object> reply(int id, String body, boolean replyAll, List<OutlookClient.Attachment> files) {
        Map<String, Object> r = repo.getMessage(id);
        if (r == null) return null;
        requireText(body, files);
        outlook.account();
        return send(replyOutgoing(r, body, replyAll), files);
    }

    /**
     * A reply to an inbox message: back to who wrote (Reply-To first), the original quoted below.
     * Story 5654: the headers come from Outlook when it still has the message, else from what Echo kept.
     */
    private OutlookClient.Outgoing replyOutgoing(Map<String, Object> r, String body, boolean replyAll) {
        String self = outlook.account();
        String from = (String) r.get("FromHeader"), toH = (String) r.get("ToHeader"), ccH = (String) r.get("CcHeader");
        String replyTo = null, subject0 = (String) r.get("Subject");
        String baseId = (String) r.get("GmailID");
        try {
            OutlookClient.MessageMeta meta = outlook.meta(baseId);
            from = meta.from(); toH = meta.to(); ccH = meta.cc(); replyTo = meta.replyTo(); subject0 = meta.subject();
        } catch (OutlookClient.OutlookException ex) {
            if (ex.status() != 404) throw ex;
            baseId = null;   // gone from Outlook: still send, as a new conversation
        }
        String to = replyTo != null && !replyTo.isBlank() ? replyTo : from;
        String cc = replyAll ? GmailClientMime.replyAllCc(toH, ccH, self, to) : null;
        String subject = GmailClientMime.replySubject(subject0);
        String text = (body == null ? "" : body.trim()) + "\n\n" + GmailClientMime.quoteForReply(when(r), from, originalText(r));
        return outgoing(baseId == null ? "new" : (replyAll ? "replyAll" : "reply"), baseId, to, cc, null, subject, text, false);
    }

    private static OutlookClient.Outgoing outgoing(String kind, String baseId, String to, String cc, String bcc,
                                                   String subject, String text, boolean keepOriginal) {
        return new OutlookClient.Outgoing(kind, baseId, to, cc, bcc, subject, text, keepOriginal, null);
    }

    /** A forward of an inbox message: the original below what Tony wrote. */
    private OutlookClient.Outgoing forwardOutgoing(Map<String, Object> r, String to, String cc, String bcc, String body,
                                                   boolean includeOriginal) {
        String subject = GmailClientMime.forwardSubject((String) r.get("Subject"));
        String text = (body == null ? "" : body.trim()) + "\n\n" + GmailClientMime.forwardBlock(when(r),
            (String) r.get("FromHeader"), (String) r.get("ToHeader"), (String) r.get("Subject"), originalText(r));
        return outgoing("forward", (String) r.get("GmailID"), to, cc, bcc, subject, text, includeOriginal);
    }

    public Map<String, Object> forward(int id, String to, String cc, String body) { return forward(id, to, cc, null, body); }

    public Map<String, Object> forward(int id, String to, String cc, String bcc, String body) {
        return forward(id, to, cc, bcc, body, List.of(), false);
    }

    /**
     * A forward with files attached, and -- includeOriginal -- the original message's own
     * attachments too (Story 5654: Outlook's own Forward carries them, read from the message there).
     */
    public Map<String, Object> forward(int id, String to, String cc, String bcc, String body,
                                       List<OutlookClient.Attachment> files, boolean includeOriginal) {
        Map<String, Object> r = repo.getMessage(id);
        if (r == null) return null;
        requireAddress(to);
        outlook.account();
        return send(forwardOutgoing(r, to, cc, bcc, body, includeOriginal), files);
    }

    public Map<String, Object> compose(String to, String cc, String subject, String body) {
        return compose(to, cc, null, subject, body);
    }

    public Map<String, Object> compose(String to, String cc, String bcc, String subject, String body) {
        return compose(to, cc, bcc, subject, body, List.of());
    }

    /** A new message, with files attached (a message may be only attachments). */
    public Map<String, Object> compose(String to, String cc, String bcc, String subject, String body,
                                       List<OutlookClient.Attachment> files) {
        requireAddress(to);
        requireText(body, files);
        outlook.account();
        return send(composeOutgoing(to, cc, bcc, subject, body), files);
    }

    private OutlookClient.Outgoing composeOutgoing(String to, String cc, String bcc, String subject, String body) {
        return outgoing("new", null, to, cc, bcc, subject == null ? "" : subject.trim(), body == null ? "" : body.trim(), false);
    }

    /**
     * Who to send to (Story 5575): the people who have written, and (Story 5654) the people Tony has
     * written to, best match first. value is what the To field takes.
     */
    public List<Map<String, Object>> recipients(String search, int top) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : repo.searchRecipients(search, top <= 0 ? 12 : Math.min(top, 50))) {
            String address = (String) r.get("Address");
            String name = (String) r.get("DisplayName");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("address", address);
            m.put("name", name);
            m.put("source", r.get("Source"));
            m.put("value", name == null || name.isBlank() ? address
                : "\"" + name.replace("\"", "").trim() + "\" <" + address + ">");
            out.add(m);
        }
        return out;
    }

    /** The most attachments one message carries (Leonardo's Gmail limit; Exchange's is usually lower). */
    static final long MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024;

    private Map<String, Object> send(OutlookClient.Outgoing o, List<OutlookClient.Attachment> files) {
        List<OutlookClient.Attachment> attach = files == null ? List.of() : files;
        long bytes = 0;
        StringBuilder names = new StringBuilder();
        for (OutlookClient.Attachment a : attach) {
            bytes += a.bytes() == null ? 0 : a.bytes().length;
            names.append('|').append(a.filename()).append(':').append(a.bytes() == null ? 0 : a.bytes().length);
        }
        if (bytes > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("Send at most 25 MB of attachments in one message; these come to "
                + (bytes / (1024 * 1024)) + " MB. Take one or two off and send again.");
        }
        // A Send pressed twice, or resent by the phone, must not send twice.
        String fingerprint = (o.to() + "|" + o.cc() + "|" + o.bcc() + "|" + o.subject() + "|" + o.text() + names).toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        recentSends.values().removeIf(t -> t.isBefore(now.minusSeconds(RESEND_GUARD_SECONDS)));
        if (recentSends.putIfAbsent(fingerprint, now) != null) {
            throw new SaveRefusedException("That message was just sent. Nothing was sent again.");
        }
        try {
            String self = outlook.account();
            // Story 5654: Outlook gives a message no id until it lands in Sent Items, so it is stamped
            // with a key Check mail finds it by there.
            String key = UUID.randomUUID().toString().replace("-", "");
            OutlookClient.Outgoing stamped = new OutlookClient.Outgoing(o.baseKind(), o.baseId(), o.to(), o.cc(), o.bcc(),
                o.subject(), o.text(), o.keepOriginalAttachments(), key);
            OutlookClient.Sent sent = outlook.send(stamped, attach);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sent", true);
            m.put("gmailId", SENT_KEY_PREFIX + key);
            m.put("sentId", keepSentCopy(SENT_KEY_PREFIX + key, sent.threadId(), self, o, attach));
            m.put("attachments", attach.size());
            m.put("to", o.to());
            m.put("cc", o.cc());
            m.put("bcc", o.bcc());
            m.put("subject", o.subject());
            return m;
        } catch (OutlookClient.OutlookException ex) {
            // Story 5654: Outlook did not answer in time -- the message may well have gone (a security
            // prompt in Outlook does this), so the same message is not let through again yet.
            if (ex.status() == 504) {
                throw new OutlookClient.OutlookException(504, "Outlook did not answer in time, so the message may have gone. "
                    + "Look in Outlook's Sent Items before you send it again.");
            }
            recentSends.remove(fingerprint);
            throw ex;
        } catch (RuntimeException ex) {
            recentSends.remove(fingerprint);   // it did not go, so a retry is allowed
            throw ex;
        }
    }

    /**
     * Story 5596: what just went out is kept in the Sent tab at once, with exactly the text sent, so
     * it never waits for the next Check mail (which then finds it in Sent Items and gives the row its
     * real id -- Story 5654). The message has gone either way, so a failure to keep it is logged,
     * never reported as a failed send. Returns the sent row's id, or null.
     */
    private Integer keepSentCopy(String gmailId, String threadId, String from, OutlookClient.Outgoing o,
                                 List<OutlookClient.Attachment> attach) {
        try {
            Map<String, Object> rec = repo.recordSent(gmailId, clip(threadId, 64), clip(from, 320), clip(o.to(), 1000),
                clip(o.cc(), 1000), clip(o.bcc(), 1000), clip(o.subject(), 500), clip(snippetOf(o.text()), 500), null,
                LocalDateTime.now(), true);
            int id = num(rec.get("GmailClientSentMessageID"));
            List<String> names = new ArrayList<>();
            for (OutlookClient.Attachment a : attach) names.add(a.filename());
            if (num(rec.get("Recorded")) > 0) {
                repo.saveSentBody(id, null, o.text(), names.isEmpty() ? null : clip(String.join("\n", names), 2000), false);
            }
            return id;
        } catch (RuntimeException ex) {
            log.info("Email Client: sent {} but could not keep it in the Sent tab yet: {}", gmailId, ex.getMessage());
            return null;
        }
    }

    /** Like Gmail's snippet: the start of the text on one line. */
    private static String snippetOf(String text) {
        if (text == null) return null;
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= 200 ? t : t.substring(0, 200);
    }

    /** The original's plain text (or its HTML made readable) for a quote; empty if Outlook no longer has it. */
    private String originalText(Map<String, Object> r) {
        Map<String, Object> body = savedBody(r);
        if (body == null || truthy(body.get("IsGoneFromGmail"))) return "";
        Object text = body.get("BodyText");
        return text != null ? text.toString().trim() : GmailClientMime.htmlToText((String) body.get("BodyHtml"));
    }

    /** A message needs some words -- or, Story 5608, at least one attachment. */
    private static void requireText(String body, List<OutlookClient.Attachment> files) {
        if ((body == null || body.isBlank()) && (files == null || files.isEmpty())) {
            throw new IllegalArgumentException("Write something to send, or attach a file.");
        }
    }

    private static void requireAddress(String to) {
        if (GmailClientMime.addresses(to).isEmpty()) throw new IllegalArgumentException("Give at least one address to send to.");
    }

    private static String when(Map<String, Object> r) {
        LocalDateTime d = dateTime(r.get("ReceivedDate"));
        return d == null ? "" : d.format(WHEN);
    }

    // ---- Sent mail (Story 5596) ---------------------------------------------------------

    /** One page of the Sent tab (reply all | waiting | replied), with every tab's count for the same filters. */
    public Map<String, Object> listSent(GmailClientRepository.Filter f, String reply, int offset, int size) {
        int pageSize = size <= 0 ? 50 : Math.min(size, 200);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : repo.listSent(sentFilter(f, reply), Math.max(0, offset), pageSize + 1)) rows.add(sentRow(r));
        boolean more = rows.size() > pageSize;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", more ? rows.subList(0, pageSize) : rows);
        m.put("hasMore", more);
        m.put("tabs", tabs(f));
        return m;
    }

    /** One sent message: its headers, its body (from Leonardo; Gmail only when not saved yet) and its replies. */
    public Map<String, Object> getSent(int id) {
        Map<String, Object> r = repo.getSent(id);
        if (r == null) return null;
        Map<String, Object> m = sentRow(r);
        m.put("cc", r.get("CcHeader"));
        m.put("bcc", r.get("BccHeader"));
        Map<String, Object> body = savedSentBody(r);
        if (body == null || truthy(body.get("IsGoneFromGmail"))) {
            m.put("bodyHtml", null);
            m.put("bodyText", "This message is no longer in Outlook, so its text cannot be shown.");
            m.put("attachments", List.of());
        } else {
            m.put("bodyHtml", body.get("BodyHtml"));
            m.put("bodyText", body.get("BodyText"));
            m.put("attachments", attachmentList(body));
        }
        List<Map<String, Object>> replies = new ArrayList<>();
        for (Map<String, Object> x : repo.sentReplies(id)) {
            Map<String, Object> rp = new LinkedHashMap<>();
            rp.put("id", num(x.get("GmailClientMessageID")));
            rp.put("from", x.get("FromHeader"));
            rp.put("subject", x.get("Subject"));
            rp.put("snippet", GmailClientMime.unescapeSnippet((String) x.get("Snippet")));
            rp.put("receivedDate", iso(x.get("ReceivedDate")));
            rp.put("isRead", truthy(x.get("IsRead")));
            replies.add(rp);
        }
        m.put("replies", replies);
        m.put("links", List.of());   // Story 5654
        return m;
    }

    private static final Map<String, String> SENT_ACTIONS = Map.of("delete", "hide", "restore", "unhide");

    /** Delete (from the Gmail Client only -- Gmail keeps it) or restore sent messages. */
    public Map<String, Object> sentBulk(List<Integer> ids, String action) {
        String a = action == null ? null : SENT_ACTIONS.get(action.trim().toLowerCase(Locale.ROOT));
        if (a == null) throw new IllegalArgumentException("Choose delete or restore.");
        return counted(repo.setManySent(ids, a));
    }

    /** Flag sent messages to follow up on a day (yyyy-mm-dd) with an optional note; again moves the day. */
    public Map<String, Object> sentFollowUp(List<Integer> ids, String dueDate, String note) {
        String n = note == null ? null : note.trim();
        if (n != null && n.length() > 300) throw new IllegalArgumentException("Keep the note to 300 characters.");
        return counted(repo.setSentFollowUp(ids, dueDate, n));
    }

    public Map<String, Object> sentFollowUpDone(List<Integer> ids) { return counted(repo.sentFollowUpDone(ids)); }

    /**
     * Write again: to the same people (To and Cc), in the same Gmail conversation, with the sent
     * message quoted below -- what Reply all does on a sent message in Gmail. Null: no such message.
     */
    public Map<String, Object> writeAgain(int id, String body) { return writeAgain(id, body, List.of()); }

    public Map<String, Object> writeAgain(int id, String body, List<OutlookClient.Attachment> files) {
        Map<String, Object> r = repo.getSent(id);
        if (r == null) return null;
        requireText(body, files);
        if (GmailClientMime.addresses((String) r.get("ToHeader")).isEmpty()) {
            throw new IllegalArgumentException("That message has no address to write to again.");
        }
        outlook.account();
        return send(writeAgainOutgoing(r, body), files);
    }

    /**
     * Write again: to the same To and Cc, the sent message quoted below. Story 5654: it starts from the
     * message in Sent Items (so Outlook keeps the conversation); one not found there yet goes as new.
     */
    private OutlookClient.Outgoing writeAgainOutgoing(Map<String, Object> r, String body) {
        String to = (String) r.get("ToHeader");
        String self = outlook.account();
        String subject = GmailClientMime.replySubject((String) r.get("Subject"));
        String from = r.get("FromHeader") == null ? self : (String) r.get("FromHeader");
        String text = (body == null ? "" : body.trim()) + "\n\n" + GmailClientMime.quoteForReply(sentWhen(r), from, sentText(r));
        String base = (String) r.get("GmailID");
        boolean inOutlook = base != null && !base.startsWith(SENT_KEY_PREFIX);
        return outgoing(inOutlook ? "replyAll" : "new", inOutlook ? base : null, to, (String) r.get("CcHeader"), null,
            subject, text, false);
    }

    /** Forward a sent message to someone new, the original below. Null: no such message. */
    public Map<String, Object> forwardSent(int id, String to, String cc, String bcc, String body) {
        return forwardSent(id, to, cc, bcc, body, List.of(), false);
    }

    /** The same, with files attached and (includeOriginal) the sent message's own attachments. */
    public Map<String, Object> forwardSent(int id, String to, String cc, String bcc, String body,
                                           List<OutlookClient.Attachment> files, boolean includeOriginal) {
        Map<String, Object> r = repo.getSent(id);
        if (r == null) return null;
        requireAddress(to);
        outlook.account();
        return send(forwardSentOutgoing(r, to, cc, bcc, body, includeOriginal), files);
    }

    /** A forward of a sent message: what Tony wrote, then the sent message below. */
    private OutlookClient.Outgoing forwardSentOutgoing(Map<String, Object> r, String to, String cc, String bcc, String body,
                                                       boolean includeOriginal) {
        String self = outlook.account();
        String subject = GmailClientMime.forwardSubject((String) r.get("Subject"));
        String from = r.get("FromHeader") == null ? self : (String) r.get("FromHeader");
        String text = (body == null ? "" : body.trim()) + "\n\n" + GmailClientMime.forwardBlock(sentWhen(r),
            from, (String) r.get("ToHeader"), (String) r.get("Subject"), sentText(r));
        String base = (String) r.get("GmailID");
        boolean inOutlook = base != null && !base.startsWith(SENT_KEY_PREFIX);
        return outgoing(inOutlook ? "forward" : "new", inOutlook ? base : null, to, cc, bcc, subject, text, includeOriginal);
    }

    /** One attachment of a sent message, read from Outlook on demand. Null: no such message. */
    public OutlookClient.Attachment sentAttachment(int id, int index) {
        Map<String, Object> r = repo.getSent(id);
        if (r == null) return null;
        String base = (String) r.get("GmailID");
        if (base == null || base.startsWith(SENT_KEY_PREFIX)) {
            throw new IllegalStateException("Outlook has not filed that message in Sent Items yet. Check mail, then try again.");
        }
        return outlook.attachment(base, index);
    }

    private String sentText(Map<String, Object> r) {
        Map<String, Object> body = savedSentBody(r);
        if (body == null || truthy(body.get("IsGoneFromGmail"))) return "";
        Object text = body.get("BodyText");
        return text != null ? text.toString().trim() : GmailClientMime.htmlToText((String) body.get("BodyHtml"));
    }

    private static String sentWhen(Map<String, Object> r) {
        LocalDateTime d = dateTime(r.get("SentDate"));
        return d == null ? "" : d.format(WHEN);
    }

    /** The list filters that make sense for sent mail (From is always Tony, read / unread does not apply). */
    static GmailClientRepository.SentFilter sentFilter(GmailClientRepository.Filter f, String reply) {
        return new GmailClientRepository.SentFilter(f.search(), f.to(), f.subject(), f.words(), f.after(), f.before(),
            reply == null || reply.isBlank() ? "all" : reply.trim(), f.hasAttachment());
    }

    /**
     * A sent row for the page. receivedDate carries the sent date so the list's day headings and
     * times work unchanged; isRead is always true (Tony wrote it).
     */
    private static Map<String, Object> sentRow(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(r.get("GmailClientSentMessageID")));
        m.put("gmailId", r.get("GmailID"));     // Story 5597: View in Gmail
        m.put("kind", "sent");
        m.put("from", r.get("FromHeader"));
        m.put("to", r.get("ToHeader"));
        m.put("subject", r.get("Subject"));
        m.put("snippet", GmailClientMime.unescapeSnippet((String) r.get("Snippet")));
        m.put("sentDate", iso(r.get("SentDate")));
        m.put("receivedDate", iso(r.get("SentDate")));
        m.put("isRead", true);
        m.put("fromGmailClient", truthy(r.get("IsFromGmailClient")));
        m.put("followUpDate", isoDate(r.get("FollowUpDate")));
        m.put("followUpNote", r.get("FollowUpNote"));
        m.put("hasAttachments", truthy(r.get("HasAttachments")));
        m.put("replyCount", num(r.get("ReplyCount")));
        m.put("lastReplyDate", iso(r.get("LastReplyDate")));
        m.put("linkCount", num(r.get("LinkCount")));
        return m;
    }

    // ---- Drafts (Story 5620) ------------------------------------------------------------
    // A draft is kept in Leonardo as Tony writes (the page saves as he types), so it is never lost
    // and is there on every device. When he closes the writing window it is also put in Gmail's own
    // Drafts folder -- the words and the recipients, built exactly as Send would send them (a reply
    // with its quote), but never the attachments, which wait in Leonardo for Send. Check mail reads
    // Gmail's Drafts back: a draft written in Gmail itself appears in the Drafts tab, one changed in
    // Gmail takes Gmail's words (unless it was changed here since), and one sent or deleted in Gmail
    // leaves the tab. Sending or discarding a draft here deletes Gmail's copy.

    static final int MAX_DRAFT_CHARS = 200_000;
    static final int MAX_DRAFTS_PER_CHECK = 200;
    private static final Set<String> DRAFT_MODES = Set.of("new", "reply", "replyAll", "forward", "writeAgain", "forwardSent", "gmail");

    /** One page of the Drafts tab (the search box's words narrow it), with every tab's count. */
    public Map<String, Object> listDrafts(GmailClientRepository.Filter f, int offset, int size) {
        int pageSize = size <= 0 ? 50 : Math.min(size, 200);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : repo.listDrafts(f.search(), Math.max(0, offset), pageSize + 1)) rows.add(draftRow(r));
        boolean more = rows.size() > pageSize;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", more ? rows.subList(0, pageSize) : rows);
        m.put("hasMore", more);
        m.put("tabs", tabs(f));
        return m;
    }

    /** One open draft with its words and attachments, to carry on writing. Null: no such open draft. */
    public Map<String, Object> getDraft(int id) {
        Map<String, Object> r = repo.getDraft(id);
        return r == null ? null : draftFull(r);
    }

    /** The open draft of a reply or forward of one message: { draft } -- null when there is none. */
    public Map<String, Object> findDraft(String mode, int sourceId) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> r = mode == null || sourceId <= 0 ? null : repo.findDraft(mode.trim(), sourceId);
        m.put("draft", r == null ? null : draftFull(r));
        return m;
    }

    /**
     * Saves a draft (id 0: the first save of a new one, found again by its clientKey). toGmail also
     * puts it in Gmail's Drafts; a Gmail failure never loses the draft -- it is kept in Leonardo and
     * the answer says what Gmail said (gmailError).
     */
    public Map<String, Object> saveDraft(int id, GmailClientRepository.DraftInput in, boolean toGmail) {
        if (in == null || in.mode() == null || !DRAFT_MODES.contains(in.mode().trim())) {
            throw new IllegalArgumentException("Say what the draft is: a new message, a reply, or a forward.");
        }
        if (in.body() != null && in.body().length() > MAX_DRAFT_CHARS) {
            throw new IllegalArgumentException("That draft is too long to keep (" + MAX_DRAFT_CHARS + " characters at most).");
        }
        if (in.attachmentsJson() != null && in.attachmentsJson().length() > 4000) {
            throw new IllegalArgumentException("That draft has too many attachments to keep. Take some off.");
        }
        Map<String, Object> saved = repo.saveDraft(Math.max(0, id), in);
        Map<String, Object> m = draftRow(saved);
        if (toGmail) {
            String error = pushDraft(num(saved.get("GmailClientDraftID")));
            m.put("inGmail", error == null);
            if (error != null) m.put("gmailError", error);
        }
        return m;
    }

    /** Discard: the draft is closed in Leonardo and deleted from Gmail's Drafts. Null: no such open draft. */
    public Map<String, Object> discardDraft(int id) {
        Map<String, Object> r = repo.getDraft(id);
        if (r == null) return null;
        repo.closeDraft(id, "discarded");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("discarded", true);
        String error = deleteGmailCopy((String) r.get("GmailDraftID"));
        if (error != null) m.put("gmailError", error);
        return m;
    }

    /**
     * After a draft went out through reply / forward / send: it is closed as sent and Gmail's copy is
     * deleted. The message has gone either way, so nothing here is ever reported as a failed send.
     */
    public void draftSent(Integer id) {
        if (id == null || id <= 0) return;
        try {
            Map<String, Object> r = repo.getDraft(id);
            if (r == null) return;
            repo.closeDraft(id, "sent");
            String error = deleteGmailCopy((String) r.get("GmailDraftID"));
            if (error != null) log.info("Email Client: draft {} was sent, but its Outlook copy stays: {}", id, error);
        } catch (RuntimeException ex) {
            log.info("Email Client: draft {} was sent, but could not be closed: {}", id, ex.getMessage());
        }
    }

    /**
     * Sends a draft written (or last changed) in Outlook. Story 5654: it is sent from Outlook's own
     * Drafts folder, so it goes exactly as written there -- its threading and any files attached in
     * Outlook included -- with the To, Cc, Bcc, subject and words from the page. Every other draft is
     * sent as the reply or forward it is. Null: no such open draft.
     */
    public Map<String, Object> sendDraft(int id, String to, String cc, String bcc, String subject, String body,
                                         List<OutlookClient.Attachment> files) {
        Map<String, Object> r = repo.getDraft(id);
        if (r == null) return null;
        if (!"gmail".equals(r.get("Mode"))) throw new IllegalArgumentException("Send this draft as the reply or forward it is.");
        requireAddress(to);
        requireText(body, files);
        outlook.account();
        Map<String, Object> out = send(outlookDraftOutgoing(r, to, cc, bcc, subject, body), files);
        // Story 5654: Outlook sent its own draft, which takes it out of Drafts -- so it is only closed
        // here, never deleted (deleting by its id could reach the message just sent).
        try { repo.closeDraft(id, "sent"); }
        catch (RuntimeException ex) { log.info("Email Client: draft {} was sent, but could not be closed: {}", id, ex.getMessage()); }
        return out;
    }

    /**
     * Puts one draft in Outlook's Drafts (a new copy, or new words for its copy). Null when it went
     * in; otherwise the reason, in words -- the draft itself is always kept in Echo.
     */
    private String pushDraft(int id) {
        Map<String, Object> r = repo.getDraft(id);
        if (r == null) return null;   // sent or discarded meanwhile
        try {
            OutlookClient.Outgoing o = draftOutgoing(r);
            if (o == null) return "The message it answers is no longer in the Email Client, so it was kept in Echo only.";
            String gid = (String) r.get("GmailDraftID");
            OutlookClient.DraftRef ref;
            try {
                ref = outlook.saveDraft(gid, o);
            } catch (OutlookClient.OutlookException ex) {
                if (ex.status() != 404 || gid == null) throw ex;
                ref = outlook.saveDraft(null, o);   // deleted in Outlook meanwhile: a fresh copy
            }
            String thread = ref.threadId() != null && !ref.threadId().isBlank() ? ref.threadId() : (String) r.get("ThreadID");
            repo.setDraftGmail(id, ref.draftId(), ref.version(), clip(thread, 64), null, null, dateTime(r.get("ModifiedDate")));
            return null;
        } catch (RuntimeException ex) {
            log.info("Email Client: draft {} kept in Echo; Outlook's Drafts did not take it: {}", id, ex.getMessage());
            return "Kept in Echo, but Outlook's Drafts did not take it: " + ex.getMessage();
        }
    }

    /** Deletes Outlook's copy of a draft, if it has one. Null when done (or nothing to do); otherwise the reason. */
    private String deleteGmailCopy(String gmailDraftId) {
        if (gmailDraftId == null || gmailDraftId.isBlank()) return null;
        try {
            outlook.deleteDraft(gmailDraftId);
            return null;
        } catch (RuntimeException ex) {
            return "Outlook still has its copy in Drafts: " + ex.getMessage();
        }
    }

    /**
     * The message a draft would send, built the way Send builds it -- and, when it already has a copy
     * in Outlook's Drafts, that copy is the one changed. Null when the message it answers is gone.
     */
    private OutlookClient.Outgoing draftOutgoing(Map<String, Object> r) {
        String mode = (String) r.get("Mode");
        String to = (String) r.get("ToHeader"), cc = (String) r.get("CcHeader"), bcc = (String) r.get("BccHeader");
        String body = (String) r.get("BodyText");
        boolean original = truthy(r.get("IncludeOriginal"));
        int source = num(r.get("SourceID"));
        switch (mode == null ? "" : mode) {
            case "reply", "replyAll" -> {
                Map<String, Object> m = repo.getMessage(source);
                return m == null ? null : replyOutgoing(m, body, "replyAll".equals(mode));
            }
            case "forward" -> {
                Map<String, Object> m = repo.getMessage(source);
                return m == null ? null : forwardOutgoing(m, to, cc, bcc, body, original);
            }
            case "writeAgain" -> {
                Map<String, Object> x = repo.getSent(source);
                return x == null ? null : writeAgainOutgoing(x, body);
            }
            case "forwardSent" -> {
                Map<String, Object> x = repo.getSent(source);
                return x == null ? null : forwardSentOutgoing(x, to, cc, bcc, body, original);
            }
            case "gmail" -> { return outlookDraftOutgoing(r, to, cc, bcc, (String) r.get("Subject"), body); }
            default -> { return composeOutgoing(to, cc, bcc, (String) r.get("Subject"), body); }
        }
    }

    /** Story 5654: a draft written in Outlook goes from its own copy in Outlook's Drafts, words as written. */
    private static OutlookClient.Outgoing outlookDraftOutgoing(Map<String, Object> r, String to, String cc, String bcc,
                                                               String subject, String body) {
        String gid = (String) r.get("GmailDraftID");
        return outgoing(gid == null ? "new" : "draft", gid, to, cc, bcc, subject == null ? "" : subject.trim(),
            body == null ? "" : body, false);
    }

    /**
     * Check mail's look at Gmail's Drafts folder. Never fails the check: a problem is logged and
     * returned in words. A draft is only overwritten from Gmail when nothing was typed here since
     * Gmail last had it, and only drafts copied to Gmail before the folder was listed are compared
     * against the listing -- one saved while the check runs is left for the next check.
     */
    private String readDrafts(Progress p) {
        try {
            LocalDateTime listedAt = LocalDateTime.now();
            List<OutlookClient.DraftRef> inOutlook = outlook.listDrafts();
            Map<String, Map<String, Object>> kept = new HashMap<>();
            for (Map<String, Object> k : repo.draftsInGmail()) kept.put((String) k.get("GmailDraftID"), k);
            Set<String> seen = new java.util.HashSet<>();
            int read = 0;
            for (OutlookClient.DraftRef d : inOutlook) {
                if (d.draftId() == null) continue;
                seen.add(d.draftId());
                Map<String, Object> k = kept.get(d.draftId());
                if (k != null) {
                    if (!truthy(k.get("IsOpen"))) continue;                                   // sent or discarded here
                    if (java.util.Objects.equals(k.get("GmailMessageID"), d.version())) continue;  // unchanged in Outlook
                    LocalDateTime pushed = dateTime(k.get("PushedDate")), modified = dateTime(k.get("ModifiedDate"));
                    if (pushed == null || !pushed.isBefore(listedAt)) continue;               // copied while listing
                    if (modified != null && modified.isAfter(pushed)) continue;               // typed here since: ours goes next
                }
                if (read >= MAX_DRAFTS_PER_CHECK) break;
                OutlookClient.DraftContent c = outlook.draft(d.draftId());
                read++;
                String text = c.text() != null ? c.text() : GmailClientMime.htmlToText(c.html());
                if (text != null && text.length() > MAX_DRAFT_CHARS) text = text.substring(0, MAX_DRAFT_CHARS);
                int recorded = num(repo.draftFromGmail(c, text).get("Recorded"));
                if (recorded == 1) p.draftsAdded++;
                else if (recorded == 2) p.draftsChanged++;
            }
            for (Map<String, Object> k : kept.values()) {
                if (!truthy(k.get("IsOpen")) || seen.contains((String) k.get("GmailDraftID"))) continue;
                LocalDateTime pushed = dateTime(k.get("PushedDate"));
                if (pushed == null || !pushed.isBefore(listedAt)) continue;
                if (repo.closeDraft(num(k.get("GmailClientDraftID")), "gone")) p.draftsGone++;
            }
            return null;
        } catch (RuntimeException ex) {
            log.info("Email Client: could not read Outlook's Drafts: {}", ex.getMessage());
            return "Could not read Outlook's Drafts: " + ex.getMessage();
        }
    }

    /** A Drafts tab row. receivedDate carries the last change so the list's day headings work unchanged. */
    private static Map<String, Object> draftRow(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(r.get("GmailClientDraftID")));
        m.put("kind", "draft");
        m.put("mode", r.get("Mode"));
        m.put("sourceId", r.get("SourceID") == null ? null : num(r.get("SourceID")));
        m.put("to", r.get("ToHeader"));
        m.put("cc", r.get("CcHeader"));
        m.put("subject", r.get("Subject"));
        m.put("snippet", r.get("Snippet"));
        m.put("attachmentCount", num(r.get("AttachmentCount")));
        m.put("hasAttachments", num(r.get("AttachmentCount")) > 0);
        m.put("inGmail", r.get("GmailDraftID") != null);
        m.put("fromGmail", !truthy(r.get("IsFromGmailClient")));
        m.put("modifiedDate", iso(r.get("ModifiedDate")));
        m.put("receivedDate", iso(r.get("ModifiedDate")));
        m.put("isRead", true);
        return m;
    }

    /** A draft opened to carry on writing: the row plus its words, Bcc, attachments and whether Gmail is up to date. */
    private static Map<String, Object> draftFull(Map<String, Object> r) {
        Map<String, Object> m = draftRow(r);
        m.put("bcc", r.get("BccHeader"));
        m.put("body", r.get("BodyText"));
        m.put("includeOriginal", truthy(r.get("IncludeOriginal")));
        m.put("attachments", r.get("AttachmentsJson"));
        LocalDateTime pushed = dateTime(r.get("PushedDate")), modified = dateTime(r.get("ModifiedDate"));
        m.put("gmailUpToDate", pushed != null && (modified == null || !modified.isAfter(pushed)));
        return m;
    }

    // ---- inboxes ------------------------------------------------------------------------

    public List<Map<String, Object>> inboxes() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> i : repo.listInboxes()) out.add(inbox(i));
        return out;
    }

    /** Who is filed in an inbox (the Inboxes dialog's sender count, tapped). Null when there is no such inbox. */
    public Map<String, Object> inboxSenders(int id) {
        Map<String, Object> inbox = null;
        for (Map<String, Object> i : repo.listInboxes()) if (num(i.get("GmailClientInboxID")) == id) inbox = i;
        if (inbox == null) return null;
        List<Map<String, Object>> senders = new ArrayList<>();
        for (Map<String, Object> r : repo.listInboxSenders(id)) {
            String sample = (String) r.get("SampleFrom");
            int lt = sample == null ? -1 : sample.indexOf('<');
            String name = lt > 0 ? sample.substring(0, lt).replace("\"", "").trim() : null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("senderKey", r.get("SenderKey"));
            m.put("isAddress", truthy(r.get("IsAddress")));   // one sender filed on their own (Story 5608)
            m.put("name", name == null || name.isBlank() ? null : name);
            m.put("messages", num(r.get("Messages")));
            m.put("unread", num(r.get("Unread")));
            m.put("lastReceived", iso(r.get("LastReceived")));
            senders.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inbox", inbox(inbox));
        out.put("senders", senders);
        return out;
    }

    public Map<String, Object> saveInbox(int id, String name) { return inbox(repo.saveInbox(id, name)); }

    /** Puts the inbox tabs in this order: {@code {inboxes, tabs}}. */
    public Map<String, Object> reorderInboxes(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Give the inboxes in their new order.");
        if (ids.size() > 500) throw new IllegalArgumentException("Too many inboxes at once.");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> i : repo.reorderInboxes(ids)) out.add(inbox(i));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inboxes", out);
        m.put("tabs", tabs(emptyFilter()));
        return m;
    }

    /** Null when there is no such inbox. */
    public Map<String, Object> deleteInbox(int id) {
        Map<String, Object> r = repo.deleteInbox(id);
        if (num(r.get("Deleted")) == 0) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deleted", true);
        m.put("sendersMoved", num(r.get("SendersMoved")));
        return m;
    }

    /**
     * Whitelist or blacklist a sender from the Gmail Client -- Mail Triage's own decision, so a
     * blacklist still makes its Gmail filter. Answers with the rule and the fresh tab counts.
     */
    public Map<String, Object> decideSender(String sender, String disposition) {
        Map<String, Object> m = new LinkedHashMap<>(triage.decide(sender, disposition, "Email Client"));
        m.put("tabs", tabs(emptyFilter()));
        return m;
    }

    /** One attachment of a message, read from Outlook on demand (Echo keeps only the names). Null: no such message. */
    public OutlookClient.Attachment attachment(int id, int index) {
        Map<String, Object> r = repo.getMessage(id);
        if (r == null) return null;
        return outlook.attachment((String) r.get("GmailID"), index);
    }

    /**
     * Story 5608: files ONE sender apart from their domain -- in an inbox (made if new), or alone in
     * Unclassified (blank inbox) -- or, followDomain, back with their domain's inbox. Answers the
     * address, the inbox it is in now, whether the override is there, and the fresh tab counts.
     */
    public Map<String, Object> setSenderInboxForSender(String address, String inboxName, boolean followDomain) {
        String a = MailTriageSenders.address(address);
        if (a == null) throw new IllegalArgumentException("File one sender by their full email address, such as greg@example.com.");
        Map<String, Object> r = repo.setSenderInboxForSender(a, inboxName == null || inboxName.isBlank() ? null : inboxName.trim(),
            followDomain);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("senderAddress", r.get("SenderKey"));
        m.put("inboxId", r.get("GmailClientInboxID") == null ? null : num(r.get("GmailClientInboxID")));
        m.put("inboxName", r.get("InboxName"));
        m.put("hasOverride", truthy(r.get("HasOverride")));
        m.put("tabs", tabs(emptyFilter()));
        return m;
    }

    // ---- flags (Story 5608) ------------------------------------------------------------------

    public List<Map<String, Object>> flags() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : repo.listFlags()) out.add(flag(r));
        return out;
    }

    public Map<String, Object> saveFlag(int id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("flag", flag(repo.saveFlag(id, name)));
        m.put("flags", flags());
        return m;
    }

    /** Null when there is no such flag. */
    public Map<String, Object> deleteFlag(int id) {
        Map<String, Object> r = repo.deleteFlag(id);
        if (num(r.get("Deleted")) == 0) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deleted", true);
        m.put("messagesUnflagged", num(r.get("MessagesUnflagged")));
        m.put("flags", flags());
        m.put("tabs", tabs(emptyFilter()));
        return m;
    }

    /** Exactly these flags on one message (each found, or made when new); its flags after, and every flag. */
    public Map<String, Object> setMessageFlags(int id, List<String> names) {
        if (repo.getMessage(id) == null) return null;
        List<Map<String, Object>> mine = new ArrayList<>();
        for (Map<String, Object> r : repo.setMessageFlags(id, names == null ? List.of() : names)) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", num(r.get("GmailClientFlagID")));
            f.put("name", r.get("FlagName"));
            mine.add(f);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("messageFlags", mine);
        m.put("flags", flags());
        m.put("tabs", tabs(emptyFilter()));
        return m;
    }

    /** One flag put on (add, made when new) or taken off (remove) the selected messages. */
    public Map<String, Object> bulkFlag(List<Integer> ids, String flagName, String action) {
        Map<String, Object> r = repo.bulkFlag(ids, flagName, action == null ? null : action.trim().toLowerCase(Locale.ROOT));
        Map<String, Object> m = counted(num(r.get("Updated")));
        m.put("flagId", r.get("GmailClientFlagID") == null ? null : num(r.get("GmailClientFlagID")));
        m.put("flags", flags());
        return m;
    }

    private static Map<String, Object> flag(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(r.get("GmailClientFlagID")));
        m.put("name", r.get("FlagName"));
        m.put("sortOrder", num(r.get("SortOrder")));
        m.put("color", r.get("ColorHex") == null ? null : r.get("ColorHex").toString().trim());
        m.put("messageCount", num(r.get("MessageCount")));
        return m;
    }

    // ---- colours (Story 5608) ----------------------------------------------------------------

    public List<Map<String, Object>> colors() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : repo.listColors()) out.add(color(r));
        return out;
    }

    /** One colour rule, made or changed; every rule after. */
    public Map<String, Object> saveColor(int id, String kind, String matchText, Integer flagId, String colorHex) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("color", color(repo.saveColor(id, kind, matchText, flagId, colorHex)));
        m.put("colors", colors());
        return m;
    }

    /** The same colour for every selected rule. */
    public Map<String, Object> setColors(List<Integer> ids, String colorHex) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("updated", repo.setColors(ids, colorHex));
        m.put("colors", colors());
        return m;
    }

    /** Removes the selected rules, or every one (all -- "clear colors and start over"). */
    public Map<String, Object> deleteColors(List<Integer> ids, boolean all) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("removed", repo.deleteColors(ids, all));
        m.put("colors", colors());
        return m;
    }

    private static Map<String, Object> color(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(r.get("GmailClientColorID")));
        m.put("kind", r.get("ColorKind"));
        m.put("match", r.get("MatchText"));
        m.put("flagId", r.get("GmailClientFlagID") == null ? null : num(r.get("GmailClientFlagID")));
        m.put("flagName", r.get("FlagName"));
        m.put("color", r.get("ColorHex") == null ? null : r.get("ColorHex").toString().trim());
        return m;
    }

    // ---- emails linked to records: Story 5654 -- left out; Echo has no records to link to ----

    /** Files a sender (domain, or free-mail address) in an inbox, or back in Unclassified. */
    public Map<String, Object> setSenderInbox(String sender, String inboxName) {
        MailTriageSenders.Key k = MailTriageSenders.normalizeTyped(sender);
        if (k == null) throw new IllegalArgumentException("Give a domain such as nextdoor.com, or one full address.");
        Map<String, Object> r = repo.setSenderInbox(k.key(), inboxName == null || inboxName.isBlank() ? null : inboxName.trim());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("senderKey", r.get("SenderKey"));
        m.put("inboxId", r.get("GmailClientInboxID") == null ? null : num(r.get("GmailClientInboxID")));
        m.put("inboxName", r.get("InboxName"));
        m.put("tabs", tabs(emptyFilter()));
        return m;
    }

    // ---- mapping ------------------------------------------------------------------------

    static GmailClientRepository.Filter emptyFilter() {
        return new GmailClientRepository.Filter(null, null, null, null, null, null, null, null);
    }

    private static Map<String, Object> listRow(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(r.get("GmailClientMessageID")));
        m.put("gmailId", r.get("GmailID"));     // Story 5597: View in Gmail
        m.put("senderKey", r.get("SenderKey"));
        m.put("from", r.get("FromHeader"));
        m.put("subject", r.get("Subject"));
        m.put("snippet", GmailClientMime.unescapeSnippet((String) r.get("Snippet")));
        m.put("receivedDate", iso(r.get("ReceivedDate")));
        m.put("isRead", truthy(r.get("IsRead")));
        m.put("inboxId", r.get("GmailClientInboxID") == null ? null : num(r.get("GmailClientInboxID")));
        m.put("inboxName", r.get("InboxName"));
        m.put("followUpDate", isoDate(r.get("FollowUpDate")));
        m.put("followUpNote", r.get("FollowUpNote"));
        m.put("hasAttachments", truthy(r.get("HasAttachments")));
        // Story 5608: the full address (colours, filing one sender), its flags and its links.
        m.put("senderAddress", r.get("SenderAddress"));
        m.put("isSenderLevel", truthy(r.get("IsSenderLevel")));
        m.put("flagIds", ids((String) r.get("FlagIDs")));
        m.put("firstFlagName", r.get("FirstFlagName"));   // the Flagged tab's heading
        m.put("linkCount", num(r.get("LinkCount")));
        return m;
    }

    /** "3,7" -> [3, 7]; null or blank -> []. */
    private static List<Integer> ids(String csv) {
        List<Integer> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty() && t.chars().allMatch(Character::isDigit)) out.add(Integer.parseInt(t));
        }
        return out;
    }

    /** A DATE column (java.sql.Date from the procs, yyyy-mm-dd text from the demo store) as yyyy-mm-dd. */
    private static String isoDate(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (o instanceof java.time.LocalDate d) return d.toString();
        String s = o.toString();
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    private static Map<String, Object> inbox(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(r.get("GmailClientInboxID")));
        m.put("name", r.get("InboxName"));
        m.put("sortOrder", num(r.get("SortOrder")));
        m.put("senderCount", num(r.get("SenderCount")));
        return m;
    }

    private static Map<String, Object> check(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(r.get("GmailClientCheckID")));
        m.put("startedDate", iso(r.get("StartedDate")));
        m.put("finishedDate", iso(r.get("FinishedDate")));
        m.put("windowStartDate", iso(r.get("WindowStartDate")));
        m.put("messagesListed", num(r.get("MessagesListed")));
        m.put("messagesAdded", num(r.get("MessagesAdded")));
        m.put("isComplete", truthy(r.get("IsComplete")));
        m.put("error", r.get("ErrorText"));
        return m;
    }

    private static String iso(Object o) {
        LocalDateTime d = dateTime(o);
        return d == null ? null : d.withNano(0).toString();
    }

    private static LocalDateTime dateTime(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime d) return d;
        if (o instanceof Timestamp t) return t.toLocalDateTime();
        if (o instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime();
        try { return LocalDateTime.parse(o.toString()); } catch (RuntimeException ex) { return null; }
    }

    private static boolean truthy(Object o) {
        return o instanceof Boolean b ? b : o instanceof Number n && n.intValue() != 0;
    }

    private static int num(Object o) { return o instanceof Number n ? n.intValue() : 0; }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
