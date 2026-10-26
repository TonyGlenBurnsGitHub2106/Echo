package com.echo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Story 5654: Echo's Email Client reads and sends mail through the classic Outlook desktop app that
 * is already signed in on this computer -- Echo's stand-in for Leonardo's GmailTriageClient. No
 * password, token or app registration is kept anywhere: Outlook does the signing in.
 *
 * Two implementations, chosen by {@code echo.mail.source}:
 *   - {@link PowerShellOutlookClient} (outlook, the default) -- talks to Outlook through a small
 *     PowerShell bridge (scripts\OutlookBridge.ps1) that attaches to the RUNNING Outlook over COM
 *   - {@link DemoOutlookClient} (demo) -- a pretend mailbox in memory, for tests and for trying the
 *     client on a computer with no Outlook
 *
 * What it never does: create, change or delete an Outlook rule, or move, flag, file or delete a
 * message in Outlook. Echo's inboxes, whitelist and blacklist live in Echo's database only. The
 * only things written to Outlook are the mail Tony sends (which Outlook keeps in Sent Items) and his
 * drafts (kept in Outlook's Drafts folder, as Leonardo keeps them in Gmail's).
 *
 * Ids: a message's id is its Outlook EntryID; threadId is its ConversationID.
 * Failures throw {@link OutlookException}; status 404 = Outlook no longer has that item, 409 =
 * Outlook is not running / not reachable.
 */
public interface OutlookClient {

    /** The signed-in mailbox's own address (what goes in From). */
    String account();

    /**
     * Every message in a folder (inbox | sent) received / sent at or after {@code after} and before
     * {@code before} (null: up to now), newest first, at most {@code max}: headers and the start of the
     * text only. Echo asks a few days at a time, so no one call holds Outlook for long.
     */
    List<MessageMeta> list(String folder, LocalDateTime after, LocalDateTime before, int max);

    /** The headers of one message. */
    MessageMeta meta(String id);

    /** One message's text and HTML bodies (either may be null) and its attachment names, in order. */
    MessageBody body(String id);

    /** The {@code index}-th attachment of a message (0-based, the order {@link #body} names them in). */
    Attachment attachment(String id, int index);

    /**
     * Sends one message. How it is built depends on {@code o.baseKind()}: reply / replyAll / forward
     * start from the message {@code o.baseId()} so Outlook keeps the conversation together; draft
     * sends the Outlook draft {@code o.baseId()}; new (or a base Outlook no longer has) starts fresh.
     * To, Cc, Bcc, Subject and the text always come from {@code o} -- the text is plain, quote and all.
     */
    Sent send(Outgoing o, List<Attachment> files);

    /**
     * Puts a draft in Outlook's Drafts folder: a new one (draftId null), or the words of the one
     * already there. Attachments are never put there -- they wait in Echo until Send. 404 = Outlook
     * no longer has that draft.
     */
    DraftRef saveDraft(String draftId, Outgoing o);

    /** Deletes a draft from Outlook's Drafts folder. One that is already gone is not an error. */
    void deleteDraft(String draftId);

    /** Every draft in Outlook's Drafts folder: its id and a version that changes when it is changed. */
    List<DraftRef> listDrafts();

    /** One draft as Outlook has it. */
    DraftContent draft(String draftId);

    /**
     * One message to send or keep as a draft. baseKind new | reply | replyAll | forward | draft;
     * baseId the message (or draft) it starts from; keepOriginalAttachments: a forward carries the
     * original's own files; sendKey is stamped on the message so Check mail can find the copy Outlook
     * keeps in Sent Items (Outlook gives a sent message no id at send time).
     */
    record Outgoing(String baseKind, String baseId, String to, String cc, String bcc, String subject, String text,
                    boolean keepOriginalAttachments, String sendKey) {}

    /**
     * Headers of one message. sendKey is the key Echo stamped on mail it sent (null for anything
     * else); received is the local time Outlook received (or sent) it.
     */
    record MessageMeta(String id, String threadId, String from, String to, String cc, String replyTo,
                       String subject, String snippet, String messageIdHeader, LocalDateTime received, String sendKey) {}

    record MessageBody(String text, String html, List<String> attachments) {}

    /** One attachment: its file name, its MIME type and its bytes. */
    record Attachment(String filename, String mimeType, byte[] bytes) {}

    /** A message Outlook just sent: the key it was stamped with, and its conversation when known. */
    record Sent(String sendKey, String threadId) {}

    /** version changes whenever the draft is changed (Outlook's last-modified time). */
    record DraftRef(String draftId, String version, String threadId) {}

    /** updated is when it was last changed in Outlook. */
    record DraftContent(String draftId, String version, String threadId, String to, String cc, String bcc,
                        String subject, String text, String html, LocalDateTime updated) {}

    /** Outlook could not do it. status 404 = no such item, 409 = Outlook not running or not reachable. */
    class OutlookException extends RuntimeException {
        private final int status;

        public OutlookException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() { return status; }
    }
}
