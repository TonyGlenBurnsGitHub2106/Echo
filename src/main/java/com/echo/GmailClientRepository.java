package com.echo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gmail Client (Story 5569) -- Leonardo's own copy of the Gmail inbox headers, the inboxes Tony
 * makes, which inbox each sender belongs in, and the checks. Rows come back in the procs'
 * PascalCase column shape; {@link GmailClientService} maps them to JSON.
 *
 * A message shows only while it is not hidden AND its sender is whitelisted in Mail Triage.
 *
 * Story 5654: echoed into Echo (its Email Client, over Outlook). One implementation,
 * {@link JdbcGmailClientRepository} (stp_GmailClient* procs); the record links are left out
 * (Echo has no records) and "Gmail id" holds an Outlook EntryID.
 *
 * Refusals: a proc sentence ending "Nothing was saved." becomes {@link SaveRefusedException}
 * (409); any other rule a proc raises becomes IllegalArgumentException (400).
 */
public interface GmailClientRepository {

    /**
     * The filters a list and its counts share. Blank strings mean "not filtering on this". search is
     * the search box's words, one per line (every one must match -- GmailClientSearch splits what
     * Tony typed); hasAttachment null = either (Story 5608 has:attachment); flag a flag name or its
     * start (flag:taxes); flagIds the flag filter on the main page -- flag ids, comma separated, and
     * a message with ANY of them matches.
     */
    record Filter(String search, String from, String to, String subject, String words,
                  String after, String before, String readState, Boolean hasAttachment, String flag, String flagIds) {
        Filter(String search, String from, String to, String subject, String words,
               String after, String before, String readState) {
            this(search, from, to, subject, words, after, before, readState, null, null, null);
        }

        Filter(String search, String from, String to, String subject, String words,
               String after, String before, String readState, Boolean hasAttachment, String flag) {
            this(search, from, to, subject, words, after, before, readState, hasAttachment, flag, null);
        }
    }

    /**
     * scope all | followup | inbox | unclassified | undecided | flagged; inboxId with scope inbox.
     * Newest first, except Follow up: earliest due first, and Flagged: by each message's first flag.
     * readState all | unread | read. Each row carries FlagIDs (comma separated), LinkCount,
     * SenderAddress and IsSenderLevel (Story 5608).
     */
    List<Map<String, Object>> listMessages(String scope, Integer inboxId, Filter f, int offset, int pageSize);

    /** Bucket (all | followup | flagged | unclassified | undecided | inbox), BucketKey, Cnt, Unread, Overdue. */
    List<Map<String, Object>> counts(Filter f);

    // ---- conversations (Story 5608) ----

    /**
     * One page of conversations for the same tab and filters: each row the newest matching message
     * of its Gmail thread (the listMessages columns) plus MatchCount, MatchUnread, ThreadCount,
     * SentCount and TotalThreads.
     */
    List<Map<String, Object>> listThreads(String scope, Integer inboxId, Filter f, int offset, int pageSize);

    /**
     * Every message of the conversation one message belongs to, oldest first: kind inbox | sent and
     * that numbering's id. Rows: Kind, ID, GmailID, SenderKey, FromHeader, ToHeader, CcHeader,
     * Subject, Snippet, MessageDate, IsRead, HasAttachments. Empty when the message is not there.
     */
    List<Map<String, Object>> thread(String kind, int id);

    /**
     * Every inbox message (not hidden, sender not blacklisted) in the conversations of these
     * messages, them included -- a conversation row's whole conversation, for Read / Delete / Flag.
     */
    List<Integer> threadMessageIds(List<Integer> ids);

    /** One visible message, or null. */
    Map<String, Object> getMessage(int id);

    /** Keeps one message's headers. False when it was already kept. */
    boolean recordMessage(String gmailId, String threadId, String senderKey, String from, String to, String cc,
                          String subject, String snippet, String messageIdHeader, LocalDateTime received);

    /** Gmail ids already kept that arrived on or after a day before since. */
    Set<String> knownSince(LocalDateTime since);

    /** False when there is no such message. */
    boolean setRead(int id, boolean read);

    /** Delete in the Gmail Client: hides in Leonardo only. False when there is no such message. */
    boolean setHidden(int id, boolean hidden);

    /** Many at once: action read | unread | hide | unhide. The number that changed. */
    int setMany(List<Integer> ids, String action);

    /** Mark every message a tab shows for the filters read or unread. The number that changed. */
    int markAll(String scope, Integer inboxId, Filter f, boolean read);

    /** Flag messages to follow up on dueDate (yyyy-mm-dd); re-opens a closed one. The number flagged. */
    int setFollowUp(List<Integer> ids, String dueDate, String note);

    /** Close the open follow-ups of these messages. The number closed. */
    int followUpDone(List<Integer> ids);

    /**
     * Who to send to (Story 5575): Address, DisplayName, Source (leonardo | mail), Hits -- best
     * match first, de-duplicated by address, at most top rows.
     */
    List<Map<String, Object>> searchRecipients(String search, int top);

    // ---- the whole message, kept in Leonardo (Story 5576) ----

    /** A message's saved body: BodyHtml, BodyText, AttachmentNames, IsGoneFromGmail, SavedDate -- or null. */
    Map<String, Object> getBody(int id);

    /** Saves (or replaces) a message's body; gone = Gmail no longer had it. */
    void saveBody(int id, String html, String text, String attachmentNames, boolean gone);

    /** Shown messages (not hidden, sender whitelisted) with no saved body, newest first: GmailClientMessageID, GmailID. */
    List<Map<String, Object>> listMissingBodies(int top);

    /**
     * Story 5591: saved bodies, inbox and sent, saved before a moment and still in Gmail, newest
     * first -- Kind ('inbox' / 'sent'), ID, GmailID. Re-saving one moves it off the list.
     */
    List<Map<String, Object>> listBodiesSavedBefore(LocalDateTime before, int top);

    /** Of the shown messages: Saved, Missing. */
    Map<String, Object> bodyCounts();

    /** Files a sender in an inbox (made if new), or back in Unclassified when inboxName is blank. */
    Map<String, Object> setSenderInbox(String senderKey, String inboxName);

    /**
     * Story 5608: files ONE sender (a full address) apart from their domain -- in an inbox (made if
     * new), or in Unclassified when inboxName is blank -- or, followDomain, back with the domain.
     * SenderKey, GmailClientInboxID, InboxName, HasOverride.
     */
    Map<String, Object> setSenderInboxForSender(String address, String inboxName, boolean followDomain);

    List<Map<String, Object>> listInboxes();

    /** The senders filed in one inbox: SenderKey, IsAddress, Messages, Unread, LastReceived, SampleFrom -- busiest first. */
    List<Map<String, Object>> listInboxSenders(int inboxId);

    /** id 0 = a new inbox. */
    Map<String, Object> saveInbox(int id, String name);

    /** Deleted (0 or 1) and SendersMoved. */
    Map<String, Object> deleteInbox(int id);

    /**
     * Puts the inboxes (their tabs) in this order, first first; an inbox not listed keeps its
     * place after them. An unknown id refuses with SaveRefusedException. Returns the inbox list.
     */
    List<Map<String, Object>> reorderInboxes(List<Integer> ids);

    int startCheck(LocalDateTime windowStart);

    Map<String, Object> finishCheck(int checkId, int listed, int added, boolean complete, String error);

    /** The latest check, or the latest complete one; null when none. */
    Map<String, Object> lastCheck(boolean completeOnly);

    /** The earliest window start of any complete check -- how far back mail has been read; null when none. */
    LocalDateTime reach();

    // ---- flags (Story 5608) ----

    /** Every flag: GmailClientFlagID, FlagName, SortOrder, ColorHex, MessageCount -- in flag order. */
    List<Map<String, Object>> listFlags();

    /** Makes (id 0; an existing name is found, not doubled) or renames a flag. */
    Map<String, Object> saveFlag(int id, String name);

    /** Deleted (0 or 1) and MessagesUnflagged. The flag comes off every message; its colour goes. */
    Map<String, Object> deleteFlag(int id);

    /** Sets exactly these flags on one message (found or made by name): its flags after, in flag order. */
    List<Map<String, Object>> setMessageFlags(int messageId, List<String> names);

    /** One flag on (action add, made if new) or off (remove) many messages: Updated, GmailClientFlagID. */
    Map<String, Object> bulkFlag(List<Integer> ids, String flagName, String action);

    // ---- colours (Story 5608) ----

    /** Every colour rule: GmailClientColorID, ColorKind (sender | domain | flag), MatchText, GmailClientFlagID, FlagName, ColorHex. */
    List<Map<String, Object>> listColors();

    /** Saves one rule; a rule matching the same sender, domain or flag gets the new colour instead of a twin. */
    Map<String, Object> saveColor(int id, String kind, String matchText, Integer flagId, String colorHex);

    /** The same colour for every selected rule. The number changed. */
    int setColors(List<Integer> ids, String colorHex);

    /** Removes the selected rules, or every rule (all). The number removed. */
    int deleteColors(List<Integer> ids, boolean all);

    // ---- emails linked to records (Story 5608): Story 5654 -- left out; Echo has no records ----

    // ---- Sent mail (Story 5596) ----
    // A sent message is kept the moment the Gmail Client sends it, and Check mail reads the rest from
    // Gmail's Sent folder. A reply is mail read in from the same thread after it went (vw_GmailClientSentReply).

    /** The Sent tab's filters. replyState all | waiting (no reply yet) | replied. Blank = not filtering. */
    record SentFilter(String search, String to, String subject, String words, String after, String before, String replyState,
                      Boolean hasAttachment) {
        SentFilter(String search, String to, String subject, String words, String after, String before, String replyState) {
            this(search, to, subject, words, after, before, replyState, null);
        }
    }

    /** One page of sent mail, newest first, each row with ReplyCount, LastReplyDate, its open follow-up and HasAttachments. */
    List<Map<String, Object>> listSent(SentFilter f, int offset, int pageSize);

    /** Cnt, Waiting (no reply yet) and Overdue (open follow-up past due) for the filters. */
    Map<String, Object> sentCounts(SentFilter f);

    /** One sent message that is not deleted, or null. */
    Map<String, Object> getSent(int id);

    /** The replies to a sent message, oldest first: GmailClientMessageID, FromHeader, Subject, Snippet, ReceivedDate, IsRead. */
    List<Map<String, Object>> sentReplies(int id);

    /** Keeps a sent message's headers: Recorded (0 when already kept) and GmailClientSentMessageID. */
    Map<String, Object> recordSent(String gmailId, String threadId, String from, String to, String cc, String bcc,
                                   String subject, String snippet, String messageIdHeader, LocalDateTime sent,
                                   boolean fromGmailClient);

    /** Gmail ids of sent mail already kept that went on or after a day before since. */
    Set<String> sentKnownSince(LocalDateTime since);

    /**
     * Story 5654: a sent message kept at send time under its provisional key ("echo:...") takes the
     * id Outlook gave it in Sent Items, and its conversation and Message-ID where it had none. Returns
     * the sent row's id, or null when there was nothing to change.
     */
    Integer rekeySent(String oldGmailId, String newGmailId, String threadId, String messageIdHeader);

    /** Delete (hide) or restore sent messages in Leonardo only: action hide | unhide. The number that changed. */
    int setManySent(List<Integer> ids, String action);

    /** Flag sent messages to follow up on dueDate (yyyy-mm-dd), moving the day of one already flagged. The number flagged. */
    int setSentFollowUp(List<Integer> ids, String dueDate, String note);

    /** Close the open follow-ups of these sent messages. The number closed. */
    int sentFollowUpDone(List<Integer> ids);

    /** A sent message's saved body (BodyHtml, BodyText, AttachmentNames, IsGoneFromGmail), or null. */
    Map<String, Object> getSentBody(int id);

    void saveSentBody(int id, String html, String text, String attachmentNames, boolean gone);

    /** Sent messages (not deleted) with no saved body, newest first: GmailClientSentMessageID, GmailID. */
    List<Map<String, Object>> listMissingSentBodies(int top);

    // ---- Drafts (Story 5620) ----
    // A draft is kept in Leonardo as it is written and mirrored to Gmail's Drafts folder. Rows are
    // closed (sent | discarded | gone), never deleted.

    /** What one draft save carries. mode new | reply | replyAll | forward | writeAgain | forwardSent | gmail. */
    record DraftInput(String clientKey, String mode, Integer sourceId, String to, String cc, String bcc, String subject,
                      String body, boolean includeOriginal, String attachmentsJson, int attachmentCount) {}

    /**
     * One page of open drafts, changed most recently first, matching the search box's words (one per
     * line): GmailClientDraftID, Mode, SourceID, ToHeader, CcHeader, Subject, Snippet, AttachmentCount,
     * GmailDraftID, ThreadID, IsFromGmailClient, PushedDate, CreatedDate, ModifiedDate. No body.
     */
    List<Map<String, Object>> listDrafts(String search, int offset, int pageSize);

    /** How many open drafts match the search box. */
    int draftCount(String search);

    /** One open draft with its BodyText, AttachmentsJson and threading -- or null. */
    Map<String, Object> getDraft(int id);

    /** The open draft of a reply or forward of one message (mode + the message's id), or null. */
    Map<String, Object> findDraft(String mode, int sourceId);

    /**
     * Saves a draft: id > 0 updates it (refused once closed); id 0 makes one -- or updates the one
     * already kept for the same clientKey, or the open draft of the same reply / forward. Returns the
     * row (no body).
     */
    Map<String, Object> saveDraft(int id, DraftInput in);

    /** Keeps Gmail's copy of a draft: its draft id, message id and threading; pushedAt = the ModifiedDate it was built from. */
    boolean setDraftGmail(int id, String gmailDraftId, String gmailMessageId, String threadId, String inReplyTo,
                          String references, LocalDateTime pushedAt);

    /** Closes an open draft: reason sent | discarded | gone. False when it was not open. */
    boolean closeDraft(int id, String reason);

    /** Every draft with a Gmail copy, open or closed: GmailClientDraftID, GmailDraftID, GmailMessageID, Mode, IsOpen, PushedDate, ModifiedDate. */
    List<Map<String, Object>> draftsInGmail();

    /**
     * A draft as Gmail has it: kept as a new 'gmail' draft (Recorded 1), or replacing an open draft's
     * content (Recorded 2), or left alone when that draft is closed (Recorded 0). Recorded, GmailClientDraftID.
     */
    Map<String, Object> draftFromGmail(OutlookClient.DraftContent d, String text);   // Story 5654: Outlook's Drafts
}
