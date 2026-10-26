package com.echo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gmail Client attachments (Story 5608): "I should be able to attach from the Reading Nook or from
 * the file system via upload." A file chosen on the device is uploaded the moment it is picked and
 * held here, in memory, until the message is sent -- the page keeps only its token, so Send stays
 * one small request and a slow upload never holds Send up. Nothing is written to disk.
 *
 * Held for {@link #KEEP} (a draft left open that long attaches again), at most {@link #MAX_FILE}
 * per file and {@link #MAX_TOTAL} across everything held. One per controller: the real Gmail
 * Client's and the demo's never see each other's files. A restart forgets them all; Send then says
 * which file to attach again.
 */
final class GmailClientUploads {

    static final long MAX_FILE = 25L * 1024 * 1024;     // Gmail's own limit for one message's attachments
    static final long MAX_TOTAL = 200L * 1024 * 1024;
    static final Duration KEEP = Duration.ofHours(6);

    /** One held file. */
    record Upload(String token, String filename, String mimeType, byte[] bytes, Instant at) {
        OutlookClient.Attachment asAttachment() { return new OutlookClient.Attachment(filename, mimeType, bytes); }
    }

    private final Map<String, Upload> held = new ConcurrentHashMap<>();

    /** Holds one file and answers its token. Refuses an empty file, one over 25 MB, or a full store. */
    synchronized Upload put(String filename, String mimeType, byte[] bytes) {
        prune();
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("That file is empty, so there is nothing to attach.");
        if (bytes.length > MAX_FILE) {
            throw new IllegalArgumentException("Send at most 25 MB of attachments in one message, and "   // Story 5654: says no Gmail
                + GmailClientMime.fileName(filename) + " alone is " + (bytes.length / (1024 * 1024)) + " MB.");
        }
        long total = held.values().stream().mapToLong(u -> u.bytes().length).sum();
        if (total + bytes.length > MAX_TOTAL) {
            throw new IllegalStateException("Too many attachments are waiting to be sent. Send or discard a message first.");
        }
        String type = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType.trim();
        Upload u = new Upload(UUID.randomUUID().toString(), GmailClientMime.fileName(filename), type, bytes, Instant.now());
        held.put(u.token(), u);
        return u;
    }

    /** The held file, or null when the token is unknown or has expired. */
    Upload get(String token) {
        prune();
        return token == null ? null : held.get(token);
    }

    void remove(String token) { if (token != null) held.remove(token); }

    private void prune() {
        Instant cut = Instant.now().minus(KEEP);
        held.values().removeIf(u -> u.at().isBefore(cut));
    }
}
