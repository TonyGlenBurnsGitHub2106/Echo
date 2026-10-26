package com.echo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Story 5654: a pretend Outlook mailbox, in memory, for the tests and for trying the Email Client on a
 * computer with no Outlook (echo.mail.source=demo). Everything stays here: nothing is read from or
 * sent to any real mailbox, and a restart puts it back the way it starts.
 *
 * The mailbox is a small, made-up work week at an example company: a manager, a vendor, an HR
 * newsletter, a colleague's thread, a message with an attachment, a draft "written in Outlook".
 * Mail Echo sends lands in the pretend Sent Items, stamped with its send key, the way Outlook keeps it.
 */
@Component
@ConditionalOnProperty(name = "echo.mail.source", havingValue = "demo")
public class DemoOutlookClient implements OutlookClient {

    static final String ACCOUNT = "tony.burns@work.example";

    private record Msg(MessageMeta meta, String text, String html, List<Attachment> files) {}

    private final Map<String, Msg> inbox = new LinkedHashMap<>();
    private final Map<String, Msg> sent = new LinkedHashMap<>();
    private final Map<String, DraftContent> drafts = new LinkedHashMap<>();

    public DemoOutlookClient() { seed(); }

    private void seed() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        add("\"Dana Whitfield\" <dana.whitfield@work.example>", "Q4 claims batch review", "conv-q4",
            "Tony,\n\nCan you walk me through the claims batch numbers before Thursday? Bring the error counts.\n\nDana",
            now.minusHours(2), List.of());
        add("\"Dana Whitfield\" <dana.whitfield@work.example>", "RE: Q4 claims batch review", "conv-q4",
            "Also -- add the retry totals. Thanks.\n\nDana", now.minusHours(1), List.of());
        add("\"Acme Billing\" <billing@acme-supplies.example>", "Invoice 4471 for October", "conv-acme",
            "Hello,\n\nYour October invoice is attached.\n\nAcme Supplies", now.minusDays(1),
            List.of(new Attachment("invoice-4471.txt", "text/plain", "Invoice 4471\nAmount: 1,250.00\n".getBytes(StandardCharsets.UTF_8))));
        add("\"People Team\" <news@hr.work.example>", "This week at work", "conv-hr",
            "Open enrollment ends Friday. The cafeteria is closed Monday.", now.minusDays(2), List.of());
        add("\"Rex Bot\" <alerts@monitoring.example>", "Nightly job finished with 3 warnings", "conv-mon",
            "Nightly claims job: 3 warnings, 0 errors. See the dashboard.", now.minusDays(3), List.of());
        add("\"Sam Ortiz\" <sam.ortiz@gmail.com>", "Lunch Friday?", "conv-lunch",
            "Are we still on for Friday? Same place at noon.", now.minusDays(4), List.of());
        drafts.put("draft-seed", new DraftContent("draft-seed", null, "conv-lunch", "\"Sam Ortiz\" <sam.ortiz@gmail.com>",
            null, null, "RE: Lunch Friday?", "Yes -- see you at noon.", null, now.minusDays(3)));
        drafts.put("draft-seed", withVersion(drafts.get("draft-seed")));
    }

    private void add(String from, String subject, String thread, String text, LocalDateTime when, List<Attachment> files) {
        String id = "demo-" + UUID.randomUUID().toString().replace("-", "");
        String snippet = text.replaceAll("\\s+", " ").trim();
        MessageMeta m = new MessageMeta(id, thread, from, ACCOUNT, null, null, subject,
            snippet.length() > 300 ? snippet.substring(0, 300) : snippet, "<" + id + "@work.example>", when, null);
        inbox.put(id, new Msg(m, text, null, files));
    }

    // ---- OutlookClient --------------------------------------------------------------------

    @Override
    public String account() { return ACCOUNT; }

    @Override
    public synchronized List<MessageMeta> list(String folder, LocalDateTime after, LocalDateTime before, int max) {
        Map<String, Msg> box = "sent".equals(folder) ? sent : inbox;
        List<MessageMeta> out = new ArrayList<>();
        for (Msg m : box.values()) {
            if (out.size() >= max) break;
            LocalDateTime r = m.meta().received();
            if ((after == null || !r.isBefore(after)) && (before == null || r.isBefore(before))) out.add(m.meta());
        }
        out.sort((a, b) -> b.received().compareTo(a.received()));
        return out;
    }

    @Override
    public synchronized MessageMeta meta(String id) { return find(id).meta(); }

    @Override
    public synchronized MessageBody body(String id) {
        Msg m = find(id);
        List<String> names = new ArrayList<>();
        for (Attachment a : m.files()) names.add(a.filename());
        return new MessageBody(m.text(), m.html(), names);
    }

    @Override
    public synchronized Attachment attachment(String id, int index) {
        Msg m = find(id);
        if (index < 0 || index >= m.files().size()) throw new OutlookException(404, "That message has no such attachment.");
        return m.files().get(index);
    }

    @Override
    public synchronized Sent send(Outgoing o, List<Attachment> files) {
        Msg base = o.baseId() == null ? null : box(o.baseId());
        if ("draft".equals(o.baseKind()) && o.baseId() != null) drafts.remove(o.baseId());
        String thread = base != null && !"forward".equals(o.baseKind()) ? base.meta().threadId() : "conv-" + UUID.randomUUID();
        String id = "demo-sent-" + UUID.randomUUID().toString().replace("-", "");
        // As Outlook does: a forward's own files come first, then the ones Echo adds.
        List<Attachment> all = new ArrayList<>();
        if ("forward".equals(o.baseKind()) && base != null && o.keepOriginalAttachments()) all.addAll(base.files());
        if (files != null) all.addAll(files);
        String text = o.text() == null ? "" : o.text();
        String snippet = text.replaceAll("\\s+", " ").trim();
        MessageMeta m = new MessageMeta(id, thread, ACCOUNT, o.to(), o.cc(), null, o.subject(),
            snippet.length() > 300 ? snippet.substring(0, 300) : snippet, "<" + id + "@work.example>",
            LocalDateTime.now().withNano(0), o.sendKey());
        sent.put(id, new Msg(m, text, null, all));
        return new Sent(o.sendKey(), thread);
    }

    @Override
    public synchronized DraftRef saveDraft(String draftId, Outgoing o) {
        String id = draftId;
        String thread = null;
        if (id != null && !drafts.containsKey(id)) throw new OutlookException(404, "Outlook no longer has that draft.");
        if (id == null) {
            id = "draft-" + UUID.randomUUID().toString().replace("-", "");
            Msg base = o.baseId() == null ? null : box(o.baseId());
            thread = base == null ? null : base.meta().threadId();
        } else {
            thread = drafts.get(id).threadId();
        }
        DraftContent d = withVersion(new DraftContent(id, null, thread, o.to(), o.cc(), o.bcc(), o.subject(), o.text(), null,
            LocalDateTime.now().withNano(0)));
        drafts.put(id, d);
        return new DraftRef(id, d.version(), thread);
    }

    @Override
    public synchronized void deleteDraft(String draftId) { if (draftId != null) drafts.remove(draftId); }

    @Override
    public synchronized List<DraftRef> listDrafts() {
        List<DraftRef> out = new ArrayList<>();
        for (DraftContent d : drafts.values()) out.add(new DraftRef(d.draftId(), d.version(), d.threadId()));
        return out;
    }

    @Override
    public synchronized DraftContent draft(String draftId) {
        DraftContent d = drafts.get(draftId);
        if (d == null) throw new OutlookException(404, "Outlook no longer has that draft.");
        return d;
    }

    /** For the tests: a new message arrives in the pretend Inbox. Returns its id. */
    public synchronized String deliver(String from, String subject, String text) {
        add(from, subject, "conv-" + UUID.randomUUID(), text, LocalDateTime.now().withNano(0), List.of());
        String last = null;
        for (String k : inbox.keySet()) last = k;
        return last;
    }

    // ---- helpers --------------------------------------------------------------------------

    private Msg box(String id) {
        Msg m = inbox.get(id);
        return m != null ? m : sent.get(id);
    }

    private Msg find(String id) {
        Msg m = id == null ? null : box(id);
        if (m == null) throw new OutlookException(404, "Outlook no longer has that message.");
        return m;
    }

    private static DraftContent withVersion(DraftContent d) {
        String s = d.to() + "\n" + d.cc() + "\n" + d.bcc() + "\n" + d.subject() + "\n" + d.text();
        try {
            String v = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
            return new DraftContent(d.draftId(), v, d.threadId(), d.to(), d.cc(), d.bcc(), d.subject(), d.text(), d.html(), d.updated());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
