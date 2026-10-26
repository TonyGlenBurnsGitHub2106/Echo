package com.echo;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Gmail Client (Story 5569) -- every endpoint of the Gmail Client, written once. Two controllers
 * extend it: {@link GmailClientController} at /api/gmail-client (Tony's real Gmail) and
 * {@link GmailClientDemoController} at /api/gmail-client-demo (made-up mail, GmailClientDemo).
 * Paths below are relative to either. Separate from the older Gmail page (/inbox, /api/inbox,
 * Leonardo Mail over IMAP): nothing is shared.
 *
 *   GET    /api/gmail-client/status                  connected?, account, last check, a check going now
 *   POST   /api/gmail-client/check                   read new Gmail inbox mail (its result, or { checking: true })
 *   POST   /api/gmail-client/check-older             { days }  read further back than the client has read so far
 *   GET    /api/gmail-client/messages                ?scope=all|followup|inbox|unclassified&inbox=&q=&from=&to=&subject=
 *                                                     &words=&after=&before=&unread=&read=all|unread|read&offset=&size=
 *                                                     -> rows, hasMore, tabs
 *   POST   /api/gmail-client/messages/mark-all       { scope, inbox, the list filters, isRead }  every message the tab shows
 *   POST   /api/gmail-client/messages/bulk           { ids, action: read|unread|delete|restore }
 *   POST   /api/gmail-client/followups               { ids, dueDate yyyy-mm-dd, note }  flag (or move) a follow-up
 *   POST   /api/gmail-client/followups/done          { ids }  close their follow-ups
 *   GET    /api/gmail-client/messages/{id}           one message, its body read from Gmail (marks it read)
 *                                                    ?peek=true shows it WITHOUT marking it read -- the preview pane
 *                                                    picking a message on its own (Story 5607)
 *   POST   /api/gmail-client/messages/{id}/read      { read: true|false }
 *   POST   /api/gmail-client/messages/{id}/delete    hide it in Leonardo (Gmail untouched)
 *   POST   /api/gmail-client/messages/{id}/restore   bring a deleted message back
 *   POST   /api/gmail-client/messages/{id}/reply     { body, replyAll }
 *   POST   /api/gmail-client/messages/{id}/forward   { to, cc, body }
 *   POST   /api/gmail-client/send                    { to, cc, bcc, subject, body }
 *   GET    /api/gmail-client/recipients              ?q=&top=  who to send to (address book + people who wrote)
 *   GET    /api/gmail-client/inboxes                 the inboxes, in tab order
 *   POST   /api/gmail-client/inboxes                 { name }  a new inbox
 *   PUT    /api/gmail-client/inboxes/{id}            { name }  rename
 *   GET    /api/gmail-client/inboxes/{id}/senders    who is filed in that inbox (messages, unread, last)
 *   DELETE /api/gmail-client/inboxes/{id}            its senders go back to Unclassified
 *   POST   /api/gmail-client/inboxes/reorder         { ids }  the inbox tabs in this order
 *   POST   /api/gmail-client/senders/inbox           { sender, inbox }  file a sender (blank inbox = Unclassified)
 *   POST   /api/gmail-client/senders/decide          { sender, disposition: white|black }  Mail Triage's decision, from here
 *   GET    /api/gmail-client/messages/{id}/attachments/{n}         the n-th attachment (0-based), read from Gmail
 *   POST   /api/gmail-client/messages/{id}/attachments/{n}/save    save it to Leonardo Files (refused in the demo)
 *
 * Sent mail (Story 5596) -- ids here are sent-message ids, a separate numbering from /messages:
 *   GET    /api/gmail-client/sent                    ?q=&to=&subject=&words=&after=&before=&reply=all|waiting|replied
 *                                                     &offset=&size=  -> rows (with replyCount), hasMore, tabs
 *   GET    /api/gmail-client/sent/{id}               one sent message, its body and its replies
 *   POST   /api/gmail-client/sent/bulk               { ids, action: delete|restore }  (Leonardo only)
 *   POST   /api/gmail-client/sent/followups          { ids, dueDate, note }
 *   POST   /api/gmail-client/sent/followups/done     { ids }
 *   POST   /api/gmail-client/sent/{id}/write-again   { body }  to the same people, in the same conversation
 *   POST   /api/gmail-client/sent/{id}/forward       { to, cc, bcc, body }
 *   GET    /api/gmail-client/sent/{id}/attachments/{n}        the n-th attachment, read from Gmail
 *   POST   /api/gmail-client/sent/{id}/attachments/{n}/save   save it to Leonardo Files (refused in the demo)
 *
 * Story 5608 (2026-10-08):
 *   q on /messages, /threads, /sent and mark-all understands from: to: subject: has:attachment
 *     is:unread flag: after: before: newer_than: older_than: and "phrases" (GmailClientSearch);
 *     the answer's search says back how it was read ({ understood, notes })
 *   GET    /api/gmail-client/threads                 the same params as /messages -> conversations: rows
 *                                                     (threadCount, sentCount, matchCount), totalThreads, tabs
 *   POST   /api/gmail-client/threads/messages    { ids }  -> { ids }  every inbox message of those rows' conversations
 *   GET    /api/gmail-client/messages/{id}/conversation   every message of its conversation, oldest first
 *   GET    /api/gmail-client/sent/{id}/conversation
 *   POST   /api/gmail-client/senders/sender-inbox    { address, inbox, followDomain }  file ONE sender apart from the domain
 *   GET    /api/gmail-client/look                    { flags, colors }  the page's lookups, once
 *   GET    /api/gmail-client/flags                   POST { name }   PUT /flags/{id} { name }   DELETE /flags/{id}
 *   POST   /api/gmail-client/messages/{id}/flags     { names }  exactly these flags on one message (made if new)
 *   POST   /api/gmail-client/flags/bulk              { ids, flag, action: add|remove }
 *   GET    /api/gmail-client/colors                  POST { kind, match, flagId, color }   PUT /colors/{id} (same)
 *   POST   /api/gmail-client/colors/set              { ids, color }   POST /colors/delete { ids, all }
 *   GET    /api/gmail-client/messages/{id}/links     POST { entityId, emailName? }   DELETE /messages/{id}/links/{entityId}
 *   GET    /api/gmail-client/record-search           ?q=&max=  records to link an email to
 *   GET    /api/gmail-client/entities/{entityId}/messages     ?withSent=  the emails linked to a record
 *   GET    /api/gmail-client/entities/{entityId}/available    ?q=&top=&withSent=  emails it could be linked to
 *   GET    /api/gmail-client/sent/{id}/links         POST { entityId, emailName? }   DELETE /sent/{id}/links/{entityId}  (2026-10-12)
 *   POST   /api/gmail-client/attachments             multipart file -> { token, name, size, type }  held until Send
 *   DELETE /api/gmail-client/attachments/{token}
 *   Send, reply, forward, write again and forward-sent take attachments: [ { upload: token } |
 *   { nook: view, name: file } ] and (forwards) includeOriginal: true for the original's own files.
 *
 * Drafts (Story 5620) -- ids here are draft ids, their own numbering:
 *   GET    /api/gmail-client/drafts                  ?q=&offset=&size=  -> rows (newest change first), hasMore, tabs
 *   GET    /api/gmail-client/drafts/{id}             one open draft, its words and attachments
 *   GET    /api/gmail-client/drafts/find             ?mode=reply&source=12  -> { draft } the open draft of that reply / forward
 *   POST   /api/gmail-client/drafts                  { id, clientKey, mode, sourceId, to, cc, bcc, subject, body,
 *                                                     includeOriginal, attachments, toGmail }  save (toGmail: also to Gmail's Drafts)
 *   POST   /api/gmail-client/drafts/{id}/discard     closed here, deleted from Gmail's Drafts
 *   POST   /api/gmail-client/drafts/{id}/send        { to, cc, bcc, subject, body, attachments }  a draft written in Gmail
 *   Reply, forward, send, write again and forward-sent take draftId: the draft they finish.
 *
 * The Undecided scope (2026-10-04) lists mail from senders with no Mail Triage rule; every other
 * scope shows whitelisted senders only.
 *
 * Errors: 400 a rule was broken, 404 no such record, 409 refused / needs Gmail connected,
 * 424 Google said no (never 502 -- the Cloudflare tunnel swaps a 502 for its own page).
 *
 * Story 5654: echoed into Echo as the Email Client, at /api/gmail-client (the same paths, so the
 * page and Leonardo's stay comparable). The source is Outlook ({@link OutlookClient}); a 409 now
 * also means Outlook is not running, a 424 that Outlook refused. Left out: the demo, Reading Nook
 * attachments, Save to Leonardo Files, and every record-link endpoint (Echo has no records).
 * Added: the whitelist and blacklist list --
 *   GET    /api/gmail-client/senders/rules           ?disposition=all|white|black&q=&offset=&size=
 *   DELETE /api/gmail-client/senders/rules/{id}      take a sender off its list (its mail goes to Undecided)
 *   POST   /api/gmail-client/senders/rules/{id}/protect   { protect }  a protected sender is never blacklisted
 */
public abstract class GmailClientEndpoints {

    /** The service these endpoints drive: the real one, or the demo's (GmailClientDemo). */
    protected abstract GmailClientService service();

    /** Files picked to attach, held until Send (Story 5608). Each controller has its own. */
    private final GmailClientUploads uploads = new GmailClientUploads();

    @GetMapping("/status")
    public ResponseEntity<?> status() { return ResponseEntity.ok(service().status()); }

    @PostMapping("/check")
    public ResponseEntity<?> check() { return guard(() -> service().check()); }

    @GetMapping("/messages")
    public ResponseEntity<?> messages(@RequestParam(required = false) String scope,
                                      @RequestParam(required = false) Integer inbox,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to,
                                      @RequestParam(required = false) String subject,
                                      @RequestParam(required = false) String words,
                                      @RequestParam(required = false) String after,
                                      @RequestParam(required = false) String before,
                                      @RequestParam(required = false, defaultValue = "false") boolean unread,
                                      @RequestParam(required = false) String read,
                                      @RequestParam(required = false) String flags,
                                      @RequestParam(required = false, defaultValue = "0") int offset,
                                      @RequestParam(required = false, defaultValue = "50") int size) {
        return guard(() -> {
            GmailClientSearch.Parsed p = GmailClientSearch.parse(q);
            GmailClientRepository.Filter f = filter(p, from, to, subject, words, after, before, readState(unread, read), flags);
            return withSearch(service().list(scope, inbox, f, offset, size), p);
        });
    }

    /** Story 5608: the same list as conversations -- one row per Gmail thread. */
    @GetMapping("/threads")
    public ResponseEntity<?> threads(@RequestParam(required = false) String scope,
                                     @RequestParam(required = false) Integer inbox,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @RequestParam(required = false) String subject,
                                     @RequestParam(required = false) String words,
                                     @RequestParam(required = false) String after,
                                     @RequestParam(required = false) String before,
                                     @RequestParam(required = false, defaultValue = "false") boolean unread,
                                     @RequestParam(required = false) String read,
                                     @RequestParam(required = false) String flags,
                                     @RequestParam(required = false, defaultValue = "0") int offset,
                                     @RequestParam(required = false, defaultValue = "50") int size) {
        return guard(() -> {
            GmailClientSearch.Parsed p = GmailClientSearch.parse(q);
            GmailClientRepository.Filter f = filter(p, from, to, subject, words, after, before, readState(unread, read), flags);
            return withSearch(service().listThreads(scope, inbox, f, offset, size), p);
        });
    }

    /** In the conversation view a row is its conversation: every inbox message of these rows' threads. */
    @PostMapping("/threads/messages")
    public ResponseEntity<?> threadMessages(@RequestBody(required = false) BulkRequest req) {
        return guard(() -> service().threadMessageIds(req == null ? null : req.ids()));
    }

    /** Every message of the conversation an inbox message belongs to, oldest first. */
    @GetMapping("/messages/{id:\\d+}/conversation")
    public ResponseEntity<?> conversation(@PathVariable int id) { return guardOrNotFound(() -> service().conversation("inbox", id)); }

    /** Every message of the conversation a sent message belongs to, oldest first. */
    @GetMapping("/sent/{id:\\d+}/conversation")
    public ResponseEntity<?> sentConversation(@PathVariable int id) { return guardOrNotFound(() -> service().conversation("sent", id)); }

    /**
     * The search box's operators over the custom search fields: an operator wins over the field it
     * names; the later "after" and the earlier "before" win; is: wins over the All / Unread choice.
     */
    static GmailClientRepository.Filter filter(GmailClientSearch.Parsed p, String from, String to, String subject,
                                               String words, String after, String before, String readState) {
        return filter(p, from, to, subject, words, after, before, readState, null);
    }

    /** The same, with the main page's flag filter: flagIds "3,7" -- mail with any of those flags. */
    static GmailClientRepository.Filter filter(GmailClientSearch.Parsed p, String from, String to, String subject,
                                               String words, String after, String before, String readState, String flagIds) {
        return new GmailClientRepository.Filter(p.searchParam(),
            p.from() != null ? p.from() : from, p.to() != null ? p.to() : to, p.subject() != null ? p.subject() : subject,
            words, later(p.after(), after), earlier(p.before(), before),
            p.readState() != null ? p.readState() : readState, p.hasAttachment(), p.flag(), flagIds(flagIds));
    }

    /** The flag filter as the procs take it: "3,7", or null for none. Anything but flag numbers is refused. */
    static String flagIds(String raw) {
        if (raw == null || raw.isBlank()) return null;
        List<String> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            if (!t.matches("\\d{1,9}")) throw new IllegalArgumentException("The flag filter takes flag numbers only.");
            if (!ids.contains(t)) ids.add(t);
        }
        if (ids.size() > 40) throw new IllegalArgumentException("Filter by at most 40 flags at once.");
        return ids.isEmpty() ? null : String.join(",", ids);
    }

    private static String later(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a.compareTo(b.trim()) >= 0 ? a : b.trim();
    }

    private static String earlier(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a.compareTo(b.trim()) <= 0 ? a : b.trim();
    }

    /** The list's answer with how the search box was read, when anything was typed. */
    private static Map<String, Object> withSearch(Map<String, Object> page, GmailClientSearch.Parsed p) {
        if (p.isEmpty() && p.notes().isEmpty()) return page;
        Map<String, Object> out = new LinkedHashMap<>(page);
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("understood", p.understood());
        search.put("notes", p.notes());
        out.put("search", search);
        return out;
    }

    @PostMapping("/messages/mark-all")
    public ResponseEntity<?> markAll(@RequestBody(required = false) MarkAllRequest req) {
        MarkAllRequest r = req == null ? new MarkAllRequest(null, null, null, null, null, null, null, null, null, null, null, null, null) : req;
        return guard(() -> {
            GmailClientSearch.Parsed p = GmailClientSearch.parse(r.q());
            GmailClientRepository.Filter f = filter(p, r.from(), r.to(), r.subject(), r.words(), r.after(), r.before(),
                readState(Boolean.TRUE.equals(r.unread()), r.read()), r.flags());
            return service().markAll(r.scope(), r.inbox(), f, r.isRead() == null || r.isRead());
        });
    }

    @PostMapping("/messages/bulk")
    public ResponseEntity<?> bulk(@RequestBody(required = false) BulkRequest req) {
        BulkRequest r = req == null ? new BulkRequest(null, null) : req;
        return guard(() -> service().bulk(r.ids(), r.action()));
    }

    @PostMapping("/followups")
    public ResponseEntity<?> followUp(@RequestBody(required = false) FollowUpRequest req) {
        FollowUpRequest r = req == null ? new FollowUpRequest(null, null, null) : req;
        return guard(() -> service().followUp(r.ids(), r.dueDate(), r.note()));
    }

    @PostMapping("/followups/done")
    public ResponseEntity<?> followUpDone(@RequestBody(required = false) FollowUpRequest req) {
        FollowUpRequest r = req == null ? new FollowUpRequest(null, null, null) : req;
        return guard(() -> service().followUpDone(r.ids()));
    }

    @PostMapping("/check-older")
    public ResponseEntity<?> checkOlder(@RequestBody(required = false) OlderRequest req) {
        int days = req == null || req.days() == null ? GmailClientService.WINDOW_DAYS : req.days();
        return guard(() -> service().checkOlder(days));
    }

    /** Story 5591: re-save, the new way, bodies saved before a moment (call again while more is true). */
    @PostMapping("/bodies/resave")
    public ResponseEntity<?> resaveBodies(@RequestBody(required = false) ResaveRequest req) {
        if (req == null || req.before() == null || req.before().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "before is required, e.g. 2026-10-07T09:00"));
        }
        java.time.LocalDateTime before;
        try { before = java.time.LocalDateTime.parse(req.before().trim()); }
        catch (java.time.format.DateTimeParseException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "before must look like 2026-10-07T09:00"));
        }
        int top = req.top() == null || req.top() <= 0 ? 100 : Math.min(req.top(), 500);
        return guard(() -> service().resaveBodies(before, top));
    }

    record ResaveRequest(String before, Integer top) {}

    /** The custom search's Unread only wins; otherwise the page's All / Unread / Read choice. */
    private static String readState(boolean unreadOnly, String read) {
        if (unreadOnly) return "unread";
        return read == null || read.isBlank() ? "all" : read.trim();
    }

    @GetMapping("/messages/{id:\\d+}")
    public ResponseEntity<?> message(@PathVariable int id,
                                     @RequestParam(required = false, defaultValue = "false") boolean peek) {
        return guardOrNotFound(() -> service().get(id, !peek));
    }

    @PostMapping("/messages/{id:\\d+}/read")
    public ResponseEntity<?> read(@PathVariable int id, @RequestBody(required = false) ReadRequest req) {
        boolean read = req == null || req.read() == null || req.read();
        return guardOrNotFound(() -> service().setRead(id, read));
    }

    @PostMapping("/messages/{id:\\d+}/delete")
    public ResponseEntity<?> delete(@PathVariable int id) { return guardOrNotFound(() -> service().setHidden(id, true)); }

    @PostMapping("/messages/{id:\\d+}/restore")
    public ResponseEntity<?> restore(@PathVariable int id) { return guardOrNotFound(() -> service().setHidden(id, false)); }

    @PostMapping("/messages/{id:\\d+}/reply")
    public ResponseEntity<?> reply(@PathVariable int id, @RequestBody(required = false) ReplyRequest req) {
        ReplyRequest r = req == null ? new ReplyRequest(null, null, null, null) : req;
        return guardOrNotFound(() -> sent(service().reply(id, r.body(), Boolean.TRUE.equals(r.replyAll()), files(r.attachments())),
            r.attachments(), r.draftId()));
    }

    @PostMapping("/messages/{id:\\d+}/forward")
    public ResponseEntity<?> forward(@PathVariable int id, @RequestBody(required = false) SendRequest req) {
        SendRequest r = req == null ? new SendRequest(null, null, null, null, null, null, null, null) : req;
        return guardOrNotFound(() -> sent(service().forward(id, r.to(), r.cc(), r.bcc(), r.body(), files(r.attachments()),
            Boolean.TRUE.equals(r.includeOriginal())), r.attachments(), r.draftId()));
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody(required = false) SendRequest req) {
        SendRequest r = req == null ? new SendRequest(null, null, null, null, null, null, null, null) : req;
        return guard(() -> sent(service().compose(r.to(), r.cc(), r.bcc(), r.subject(), r.body(), files(r.attachments())),
            r.attachments(), r.draftId()));
    }

    // ---- attachments (Story 5608) ----------------------------------------------------------

    /** A file from the device, held until Send: { token, name, size, type }. */
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam(value = "file", required = false) MultipartFile file) {
        return guard(() -> {
            if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a file to attach.");
            byte[] bytes;
            try { bytes = file.getBytes(); }
            catch (IOException ex) { throw new IllegalStateException("Could not read " + file.getOriginalFilename() + ": " + ex.getMessage()); }
            String type = file.getContentType();
            if (type == null || type.isBlank()) type = java.net.URLConnection.guessContentTypeFromName(file.getOriginalFilename());
            GmailClientUploads.Upload u = uploads.put(file.getOriginalFilename(), type, bytes);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("token", u.token());
            m.put("name", u.filename());
            m.put("size", u.bytes().length);
            m.put("type", u.mimeType());
            return m;
        });
    }

    /** A held file the user took off the message before sending. */
    @DeleteMapping("/attachments/{token}")
    public ResponseEntity<?> dropUpload(@PathVariable String token) {
        uploads.remove(token);
        return ResponseEntity.ok(Map.of("removed", true));
    }

    /** What the page asked to attach, as files: held uploads, in order (Story 5654: Echo has no Reading Nook). */
    private List<OutlookClient.Attachment> files(List<AttachmentRef> refs) {
        List<OutlookClient.Attachment> out = new ArrayList<>();
        if (refs == null) return out;
        if (refs.size() > 20) throw new IllegalArgumentException("Attach at most 20 files to one message.");
        for (AttachmentRef a : refs) {
            if (a == null) continue;
            if (a.upload() != null && !a.upload().isBlank()) {
                GmailClientUploads.Upload u = uploads.get(a.upload());
                if (u == null) {
                    throw new IllegalStateException((a.name() == null ? "An attachment" : a.name())
                        + " is no longer waiting to be sent (it was picked too long ago, or Echo restarted). Attach it again.");
                }
                out.add(u.asAttachment());
            }
        }
        return out;
    }

    /** After a send went: the held uploads it carried are let go. */
    private Map<String, Object> sent(Map<String, Object> result, List<AttachmentRef> refs) {
        if (result != null && refs != null) for (AttachmentRef a : refs) if (a != null) uploads.remove(a.upload());
        return result;
    }

    /** The same, and (Story 5620) the draft it was written as is closed and taken out of Gmail's Drafts. */
    private Map<String, Object> sent(Map<String, Object> result, List<AttachmentRef> refs, Integer draftId) {
        if (result != null) service().draftSent(draftId);
        return sent(result, refs);
    }

    // ---- Drafts (Story 5620) -------------------------------------------------------------

    private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();   // Story 5654: Jackson 3 in Echo

    @GetMapping("/drafts")
    public ResponseEntity<?> drafts(@RequestParam(required = false) String q,
                                    @RequestParam(required = false, defaultValue = "0") int offset,
                                    @RequestParam(required = false, defaultValue = "50") int size) {
        return guard(() -> {
            GmailClientSearch.Parsed p = GmailClientSearch.parse(q);
            List<String> notes = new ArrayList<>(p.notes());
            if (p.from() != null || p.to() != null || p.subject() != null || p.hasAttachment() != null || p.readState() != null
                || p.after() != null || p.before() != null || p.flag() != null) notes.add("Drafts are searched by their words only -- who it is to, the subject and the start of the text.");
            GmailClientSearch.Parsed dp = new GmailClientSearch.Parsed(p.words(), null, null, null, null,
                null, null, null, null, p.understood(), notes);
            return withSearch(service().listDrafts(filter(dp, null, null, null, null, null, null, "all"), offset, size), dp);
        });
    }

    @GetMapping("/drafts/{id:\\d+}")
    public ResponseEntity<?> draft(@PathVariable int id) {
        return guardOrNotFound(() -> service().getDraft(id));
    }

    /** The open draft of a reply / forward of one message: { draft } (null when there is none). */
    @GetMapping("/drafts/find")
    public ResponseEntity<?> findDraft(@RequestParam(required = false) String mode,
                                       @RequestParam(required = false, defaultValue = "0") int source) {
        return guard(() -> service().findDraft(mode, source));
    }

    /** Saves a draft (id 0 or missing = the first save of a new one); toGmail also puts it in Gmail's Drafts. */
    @PostMapping("/drafts")
    public ResponseEntity<?> saveDraft(@RequestBody(required = false) DraftRequest req) {
        return guard(() -> {
            if (req == null) throw new IllegalArgumentException("Nothing to save.");
            List<Map<String, Object>> files = req.attachments() == null ? List.of() : req.attachments();
            if (files.size() > 20) throw new IllegalArgumentException("Attach at most 20 files to one message.");
            String json;
            try { json = files.isEmpty() ? null : JSON.writeValueAsString(files); }
            catch (tools.jackson.core.JacksonException ex) { throw new IllegalArgumentException("The attachments could not be read."); }
            GmailClientRepository.DraftInput in = new GmailClientRepository.DraftInput(req.clientKey(), req.mode(), req.sourceId(),
                req.to(), req.cc(), req.bcc(), req.subject(), req.body(), Boolean.TRUE.equals(req.includeOriginal()), json, files.size());
            return service().saveDraft(req.id() == null ? 0 : req.id(), in, Boolean.TRUE.equals(req.toGmail()));
        });
    }

    /** Discard: closed in Leonardo and deleted from Gmail's Drafts. */
    @PostMapping("/drafts/{id:\\d+}/discard")
    public ResponseEntity<?> discardDraft(@PathVariable int id) {
        return guardOrNotFound(() -> service().discardDraft(id));
    }

    /** Sends a draft written in Gmail (whole text, Gmail's threading); other drafts go through reply / forward / send. */
    @PostMapping("/drafts/{id:\\d+}/send")
    public ResponseEntity<?> sendDraft(@PathVariable int id, @RequestBody(required = false) SendRequest req) {
        SendRequest r = req == null ? new SendRequest(null, null, null, null, null, null, null, null) : req;
        return guardOrNotFound(() -> sent(service().sendDraft(id, r.to(), r.cc(), r.bcc(), r.subject(), r.body(), files(r.attachments())),
            r.attachments()));
    }

    /**
     * One draft save. attachments are kept as the page sent them ({kind, token, nook, name, size}) and
     * handed back when the draft is opened; they are read only when it is sent.
     */
    public record DraftRequest(Integer id, String clientKey, String mode, Integer sourceId, String to, String cc, String bcc,
                               String subject, String body, Boolean includeOriginal, List<Map<String, Object>> attachments,
                               Boolean toGmail) {}

    /** Who to send to: Leonardo's email addresses and people who have written (Story 5575). */
    @GetMapping("/recipients")
    public ResponseEntity<?> recipients(@RequestParam(required = false) String q,
                                        @RequestParam(required = false, defaultValue = "12") int top) {
        return guard(() -> Map.of("recipients", service().recipients(q, top)));
    }

    @GetMapping("/inboxes")
    public ResponseEntity<?> inboxes() { return ResponseEntity.ok(Map.of("inboxes", service().inboxes())); }

    @PostMapping("/inboxes")
    public ResponseEntity<?> addInbox(@RequestBody(required = false) InboxRequest req) {
        return guard(() -> service().saveInbox(0, req == null ? null : req.name()));
    }

    /** The senders filed in one inbox, busiest first. */
    @GetMapping("/inboxes/{id:\\d+}/senders")
    public ResponseEntity<?> inboxSenders(@PathVariable int id) { return guardOrNotFound(() -> service().inboxSenders(id)); }

    @PutMapping("/inboxes/{id:\\d+}")
    public ResponseEntity<?> renameInbox(@PathVariable int id, @RequestBody(required = false) InboxRequest req) {
        return guard(() -> service().saveInbox(id, req == null ? null : req.name()));
    }

    @PostMapping("/inboxes/reorder")
    public ResponseEntity<?> reorderInboxes(@RequestBody(required = false) ReorderRequest req) {
        return guard(() -> service().reorderInboxes(req == null ? null : req.ids()));
    }

    @DeleteMapping("/inboxes/{id:\\d+}")
    public ResponseEntity<?> deleteInbox(@PathVariable int id) { return guardOrNotFound(() -> service().deleteInbox(id)); }

    @PostMapping("/senders/inbox")
    public ResponseEntity<?> senderInbox(@RequestBody(required = false) SenderInboxRequest req) {
        SenderInboxRequest r = req == null ? new SenderInboxRequest(null, null) : req;
        return guard(() -> service().setSenderInbox(r.sender(), r.inbox()));
    }

    /** Story 5608: file ONE sender apart from their domain, or (followDomain) back with it. */
    @PostMapping("/senders/sender-inbox")
    public ResponseEntity<?> senderLevelInbox(@RequestBody(required = false) SenderLevelRequest req) {
        SenderLevelRequest r = req == null ? new SenderLevelRequest(null, null, null) : req;
        return guard(() -> service().setSenderInboxForSender(r.address(), r.inbox(), Boolean.TRUE.equals(r.followDomain())));
    }

    // ---- flags and colours (Story 5608) ----------------------------------------------------

    /** The page's two small lookups at once: every flag and every colour rule. */
    @GetMapping("/look")
    public ResponseEntity<?> look() {
        return guard(() -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("flags", service().flags());
            m.put("colors", service().colors());
            return m;
        });
    }

    @GetMapping("/flags")
    public ResponseEntity<?> flags() { return guard(() -> Map.of("flags", service().flags())); }

    @PostMapping("/flags")
    public ResponseEntity<?> addFlag(@RequestBody(required = false) FlagRequest req) {
        return guard(() -> service().saveFlag(0, req == null ? null : req.name()));
    }

    @PutMapping("/flags/{id:\\d+}")
    public ResponseEntity<?> renameFlag(@PathVariable int id, @RequestBody(required = false) FlagRequest req) {
        return guard(() -> service().saveFlag(id, req == null ? null : req.name()));
    }

    @DeleteMapping("/flags/{id:\\d+}")
    public ResponseEntity<?> deleteFlag(@PathVariable int id) { return guardOrNotFound(() -> service().deleteFlag(id)); }

    /** Exactly these flags on one message (each found, or made when new). */
    @PostMapping("/messages/{id:\\d+}/flags")
    public ResponseEntity<?> messageFlags(@PathVariable int id, @RequestBody(required = false) MessageFlagsRequest req) {
        return guardOrNotFound(() -> service().setMessageFlags(id, req == null ? null : req.names()));
    }

    @PostMapping("/flags/bulk")
    public ResponseEntity<?> bulkFlag(@RequestBody(required = false) BulkFlagRequest req) {
        BulkFlagRequest r = req == null ? new BulkFlagRequest(null, null, null) : req;
        return guard(() -> service().bulkFlag(r.ids(), r.flag(), r.action()));
    }

    @GetMapping("/colors")
    public ResponseEntity<?> colors() { return guard(() -> Map.of("colors", service().colors())); }

    @PostMapping("/colors")
    public ResponseEntity<?> addColor(@RequestBody(required = false) ColorRequest req) {
        ColorRequest r = req == null ? new ColorRequest(null, null, null, null) : req;
        return guard(() -> service().saveColor(0, r.kind(), r.match(), r.flagId(), r.color()));
    }

    @PutMapping("/colors/{id:\\d+}")
    public ResponseEntity<?> saveColor(@PathVariable int id, @RequestBody(required = false) ColorRequest req) {
        ColorRequest r = req == null ? new ColorRequest(null, null, null, null) : req;
        return guard(() -> service().saveColor(id, r.kind(), r.match(), r.flagId(), r.color()));
    }

    /** The same colour for the selected rules -- "select and edit a set of them". */
    @PostMapping("/colors/set")
    public ResponseEntity<?> setColors(@RequestBody(required = false) ColorsRequest req) {
        ColorsRequest r = req == null ? new ColorsRequest(null, null, null) : req;
        return guard(() -> service().setColors(r.ids(), r.color()));
    }

    /** Removes the selected rules, or every one (all: "clear colors and start over"). */
    @PostMapping("/colors/delete")
    public ResponseEntity<?> deleteColors(@RequestBody(required = false) ColorsRequest req) {
        ColorsRequest r = req == null ? new ColorsRequest(null, null, null) : req;
        return guard(() -> service().deleteColors(r.ids(), Boolean.TRUE.equals(r.all())));
    }

    // ---- emails linked to records (Story 5608): Story 5654 -- left out; Echo has no records ----

    // ---- the whitelist and blacklist (Story 5654) -----------------------------------------------

    /** Story 5654: the senders on Echo's lists -- never Outlook rules. */
    @GetMapping("/senders/rules")
    public ResponseEntity<?> senderRules(@RequestParam(required = false) String disposition,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false, defaultValue = "0") int offset,
                                         @RequestParam(required = false, defaultValue = "50") int size) {
        return guard(() -> triage().rules(disposition, q, offset, size));
    }

    /** Story 5654: takes a sender off its list; its mail waits on the Undecided tab again. */
    @DeleteMapping("/senders/rules/{id:\\d+}")
    public ResponseEntity<?> removeSenderRule(@PathVariable int id) {
        return guardOrNotFound(() -> {
            Map<String, Object> m = triage().remove(id);
            if (m == null) return null;
            Map<String, Object> out = new LinkedHashMap<>(m);
            out.put("tabs", service().tabs(GmailClientService.emptyFilter()));
            return out;
        });
    }

    /** Story 5654: a protected sender can never be blacklisted. */
    @PostMapping("/senders/rules/{id:\\d+}/protect")
    public ResponseEntity<?> protectSenderRule(@PathVariable int id, @RequestBody(required = false) ProtectRequest req) {
        return guardOrNotFound(() -> triage().setProtected(id, req == null || req.protect() == null || req.protect()));
    }

    public record ProtectRequest(Boolean protect) {}

    /** Story 5654: the whitelist and blacklist behind the rules endpoints. */
    protected abstract MailTriageService triage();

    @PostMapping("/senders/decide")
    public ResponseEntity<?> decideSender(@RequestBody(required = false) DecideRequest req) {
        DecideRequest r = req == null ? new DecideRequest(null, null) : req;
        return guard(() -> service().decideSender(r.sender(), r.disposition()));
    }

    // ---- Sent mail (Story 5596) ---------------------------------------------------------

    @GetMapping("/sent")
    public ResponseEntity<?> sent(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) String to,
                                  @RequestParam(required = false) String subject,
                                  @RequestParam(required = false) String words,
                                  @RequestParam(required = false) String after,
                                  @RequestParam(required = false) String before,
                                  @RequestParam(required = false) String reply,
                                  @RequestParam(required = false, defaultValue = "0") int offset,
                                  @RequestParam(required = false, defaultValue = "50") int size) {
        return guard(() -> {
            GmailClientSearch.Parsed p = GmailClientSearch.parse(q);
            List<String> notes = new ArrayList<>(p.notes());
            if (p.from() != null) notes.add("from: does not apply to sent mail -- it is all from you.");
            if (p.readState() != null) notes.add("is: does not apply to sent mail.");
            if (p.flag() != null) notes.add("Sent mail carries no flags.");
            GmailClientSearch.Parsed sp = new GmailClientSearch.Parsed(p.words(), null, p.to(), p.subject(), p.hasAttachment(),
                null, p.after(), p.before(), null, p.understood(), notes);
            GmailClientRepository.Filter f = filter(sp, null, to, subject, words, after, before, "all");
            return withSearch(service().listSent(f, reply, offset, size), sp);
        });
    }

    @GetMapping("/sent/{id:\\d+}")
    public ResponseEntity<?> sentMessage(@PathVariable int id) { return guardOrNotFound(() -> service().getSent(id)); }

    @PostMapping("/sent/bulk")
    public ResponseEntity<?> sentBulk(@RequestBody(required = false) BulkRequest req) {
        BulkRequest r = req == null ? new BulkRequest(null, null) : req;
        return guard(() -> service().sentBulk(r.ids(), r.action()));
    }

    @PostMapping("/sent/followups")
    public ResponseEntity<?> sentFollowUp(@RequestBody(required = false) FollowUpRequest req) {
        FollowUpRequest r = req == null ? new FollowUpRequest(null, null, null) : req;
        return guard(() -> service().sentFollowUp(r.ids(), r.dueDate(), r.note()));
    }

    @PostMapping("/sent/followups/done")
    public ResponseEntity<?> sentFollowUpDone(@RequestBody(required = false) FollowUpRequest req) {
        FollowUpRequest r = req == null ? new FollowUpRequest(null, null, null) : req;
        return guard(() -> service().sentFollowUpDone(r.ids()));
    }

    @PostMapping("/sent/{id:\\d+}/write-again")
    public ResponseEntity<?> writeAgain(@PathVariable int id, @RequestBody(required = false) ReplyRequest req) {
        ReplyRequest r = req == null ? new ReplyRequest(null, null, null, null) : req;
        return guardOrNotFound(() -> sent(service().writeAgain(id, r.body(), files(r.attachments())), r.attachments(), r.draftId()));
    }

    @PostMapping("/sent/{id:\\d+}/forward")
    public ResponseEntity<?> forwardSent(@PathVariable int id, @RequestBody(required = false) SendRequest req) {
        SendRequest r = req == null ? new SendRequest(null, null, null, null, null, null, null, null) : req;
        return guardOrNotFound(() -> sent(service().forwardSent(id, r.to(), r.cc(), r.bcc(), r.body(), files(r.attachments()),
            Boolean.TRUE.equals(r.includeOriginal())), r.attachments(), r.draftId()));
    }

    @GetMapping("/sent/{id:\\d+}/attachments/{index:\\d+}")
    public ResponseEntity<?> sentAttachment(@PathVariable int id, @PathVariable int index) {
        return download(() -> service().sentAttachment(id, index));
    }

    /** One attachment, read from Outlook on demand and handed to the browser as a download. */
    @GetMapping("/messages/{id:\\d+}/attachments/{index:\\d+}")
    public ResponseEntity<?> attachment(@PathVariable int id, @PathVariable int index) {
        return download(() -> service().attachment(id, index));
    }

    /** An attachment as a download; 404 when its message no longer exists. */
    private ResponseEntity<?> download(Supplier<OutlookClient.Attachment> read) {
        OutlookClient.Attachment a;
        try {
            a = read.get();
        } catch (RuntimeException ex) {
            return guard(() -> { throw ex; });
        }
        if (a == null) return ResponseEntity.status(404).body(Map.of("error", "That message no longer exists."));
        MediaType type;
        try { type = MediaType.parseMediaType(a.mimeType()); } catch (RuntimeException ex) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
            .contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(a.filename(), StandardCharsets.UTF_8).build().toString())
            .body(a.bytes());
    }

    public record DecideRequest(String sender, String disposition) {}

    public record ReadRequest(Boolean read) {}

    /** draftId (Story 5620): the draft this send finishes -- closed, and deleted from Gmail's Drafts, once it goes. */
    public record ReplyRequest(String body, Boolean replyAll, List<AttachmentRef> attachments, Integer draftId) {}

    public record SendRequest(String to, String cc, String bcc, String subject, String body,
                              List<AttachmentRef> attachments, Boolean includeOriginal, Integer draftId) {}

    /** One file to attach: a held upload (its token). nook / name are Leonardo's Reading Nook files -- ignored in Echo (Story 5654). */
    public record AttachmentRef(String upload, String nook, String name) {}

    public record SenderLevelRequest(String address, String inbox, Boolean followDomain) {}

    public record FlagRequest(String name) {}

    public record MessageFlagsRequest(List<String> names) {}

    public record BulkFlagRequest(List<Integer> ids, String flag, String action) {}

    public record ColorRequest(String kind, String match, Integer flagId, String color) {}

    public record ColorsRequest(List<Integer> ids, String color, Boolean all) {}

    /** emailName (2026-10-12): the email's name on the record; blank = its subject. */
    public record LinkRequest(Integer entityId, String emailName) {}

    public record InboxRequest(String name) {}

    public record ReorderRequest(List<Integer> ids) {}

    public record SenderInboxRequest(String sender, String inbox) {}

    public record MarkAllRequest(String scope, Integer inbox, String q, String from, String to, String subject,
                                 String words, String after, String before, Boolean unread, String read, Boolean isRead,
                                 String flags) {}

    public record BulkRequest(List<Integer> ids, String action) {}

    public record FollowUpRequest(List<Integer> ids, String dueDate, String note) {}

    public record OlderRequest(Integer days) {}

    // ---- error mapping ------------------------------------------------------------------

    protected static ResponseEntity<?> guardOrNotFound(Supplier<Object> action) {
        ResponseEntity<?> r = guard(action);
        return r.getStatusCode().is2xxSuccessful() && r.getBody() == null
            ? ResponseEntity.status(404).body(Map.of("error", "That message or inbox no longer exists.")) : r;
    }

    protected static ResponseEntity<?> guard(Supplier<Object> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (SaveRefusedException | IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (OutlookClient.OutlookException ex) {
            // Story 5654: Outlook not running = 409 (open Outlook and try again); anything else Outlook refused = 424.
            HttpStatus st = ex.status() == 409 ? HttpStatus.CONFLICT : ex.status() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.FAILED_DEPENDENCY;
            return ResponseEntity.status(st).body(Map.of("error", ex.getMessage()));
        }
    }
}
