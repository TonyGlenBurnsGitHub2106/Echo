package com.echo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gmail Client (Story 5569): builds the plain-text RFC 822 messages the client sends through the
 * Gmail API, and the quoted text a reply or forward carries. No mail library -- the shape is
 * small: a few headers (non-ASCII ones RFC 2047 encoded), a UTF-8 body in base64, CRLF line ends.
 *
 * Story 5654: echoed into Echo. Outlook builds and sends the message itself, so Echo uses only the
 * quoting and address helpers here; build() is kept so the file stays comparable with Leonardo's.
 */
final class GmailClientMime {

    private static final String CRLF = "\r\n";
    private static final char NBSP = (char) 0xA0;
    private static final Pattern ADDRESS = Pattern.compile("[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private GmailClientMime() { }

    /** One outgoing message. inReplyTo / references may be null for a new message. */
    static String build(String from, String to, String cc, String subject, String body,
                        String inReplyTo, String references) {
        return build(from, to, cc, null, subject, body, inReplyTo, references);
    }

    /** With Bcc (Story 5575): Gmail delivers to the Bcc addresses and strips the header from what others see. */
    static String build(String from, String to, String cc, String bcc, String subject, String body,
                        String inReplyTo, String references) {
        return build(from, to, cc, bcc, subject, body, inReplyTo, references, List.of());
    }

    /**
     * With attachments (Story 5608): multipart/mixed -- the text first, then each file in base64
     * with its name (RFC 2231 for a name that is not plain ASCII). No attachments: the plain
     * single-part message exactly as before.
     */
    static String build(String from, String to, String cc, String bcc, String subject, String body,
                        String inReplyTo, String references, List<OutlookClient.Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return buildPlain(from, to, cc, bcc, subject, body, inReplyTo, references);
        String boundary = "leo-mixed-" + java.util.UUID.randomUUID().toString().replace("-", "");
        StringBuilder m = new StringBuilder();
        headers(m, from, to, cc, bcc, subject, inReplyTo, references);
        header(m, "MIME-Version", "1.0");
        header(m, "Content-Type", "multipart/mixed; boundary=\"" + boundary + "\"");
        m.append(CRLF);
        m.append("--").append(boundary).append(CRLF);
        header(m, "Content-Type", "text/plain; charset=UTF-8");
        header(m, "Content-Transfer-Encoding", "base64");
        m.append(CRLF);
        base64Lines(m, (body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
        for (OutlookClient.Attachment a : attachments) {
            String name = fileName(a.filename());
            String type = a.mimeType() == null || a.mimeType().isBlank() || a.mimeType().matches(".*[\\r\\n;\"].*")
                ? "application/octet-stream" : a.mimeType().trim();
            m.append("--").append(boundary).append(CRLF);
            header(m, "Content-Type", type + "; " + nameParam("name", name));
            header(m, "Content-Disposition", "attachment; " + nameParam("filename", name));
            header(m, "Content-Transfer-Encoding", "base64");
            m.append(CRLF);
            base64Lines(m, a.bytes() == null ? new byte[0] : a.bytes());
        }
        m.append("--").append(boundary).append("--").append(CRLF);
        return m.toString();
    }

    /** A file name with no path, no line breaks and no quotes; "attachment" when nothing is left. */
    static String fileName(String name) {
        String n = name == null ? "" : name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\r\\n\"]+", " ").trim();
        return n.isEmpty() ? "attachment" : n;
    }

    /** name="x.pdf", or name*=UTF-8''%C3%A9t%C3%A9.pdf for a name that is not plain ASCII. */
    private static String nameParam(String param, String name) {
        boolean ascii = name.chars().allMatch(c -> c >= 0x20 && c < 0x7F);
        if (ascii) return param + "=\"" + name + "\"";
        StringBuilder b = new StringBuilder(param).append("*=UTF-8''");
        for (byte x : name.getBytes(StandardCharsets.UTF_8)) {
            int c = x & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_') {
                b.append((char) c);
            } else {
                b.append('%').append(String.format("%02X", c));
            }
        }
        return b.toString();
    }

    private static void base64Lines(StringBuilder m, byte[] bytes) {
        String b64 = Base64.getEncoder().encodeToString(bytes);
        for (int i = 0; i < b64.length(); i += 76) m.append(b64, i, Math.min(b64.length(), i + 76)).append(CRLF);
    }

    private static void headers(StringBuilder m, String from, String to, String cc, String bcc, String subject,
                                String inReplyTo, String references) {
        header(m, "From", from);
        header(m, "To", to);
        if (cc != null && !cc.isBlank()) header(m, "Cc", cc);
        if (bcc != null && !bcc.isBlank()) header(m, "Bcc", bcc);
        header(m, "Subject", encodeWord(subject == null ? "" : subject));
        header(m, "Date", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
        if (inReplyTo != null && !inReplyTo.isBlank()) {
            header(m, "In-Reply-To", inReplyTo);
            header(m, "References", (references == null || references.isBlank() ? "" : references + " ") + inReplyTo);
        }
    }

    private static String buildPlain(String from, String to, String cc, String bcc, String subject, String body,
                                     String inReplyTo, String references) {
        StringBuilder m = new StringBuilder();
        headers(m, from, to, cc, bcc, subject, inReplyTo, references);
        header(m, "MIME-Version", "1.0");
        header(m, "Content-Type", "text/plain; charset=UTF-8");
        header(m, "Content-Transfer-Encoding", "base64");
        m.append(CRLF);
        base64Lines(m, (body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
        return m.toString();
    }

    private static void header(StringBuilder m, String name, String value) {
        // A header value never carries a line break: that would let typed text add headers.
        m.append(name).append(": ").append(value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim()).append(CRLF);
    }

    /** RFC 2047 for a header that is not plain ASCII (a subject with an accent or a dash). */
    static String encodeWord(String s) {
        boolean ascii = s.chars().allMatch(c -> c >= 0x20 && c < 0x7F);
        if (ascii) return s;
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    /**
     * Story 5620: reads back a message this class built -- its headers (lower-case names, RFC 2047
     * subjects decoded) and its text (the base64 text/plain part). The demo's pretend Gmail keeps
     * drafts this way. Not a general MIME parser: it reads only the shapes {@link #build} writes.
     */
    static Read read(String raw) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        String text = "";
        if (raw == null) return new Read(headers, text);
        String[] halves = raw.split("\r?\n\r?\n", 2);
        for (String line : halves[0].split("\r?\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) headers.putIfAbsent(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), decodeWord(line.substring(colon + 1).trim()));
        }
        String rest = halves.length > 1 ? halves[1] : "";
        String type = headers.getOrDefault("content-type", "");
        Matcher b = Pattern.compile("boundary=\"([^\"]+)\"").matcher(type);
        if (b.find()) {
            // multipart/mixed: the first part is the text
            String[] parts = rest.split("--" + Pattern.quote(b.group(1)));
            for (String part : parts) {
                String[] ph = part.split("\r?\n\r?\n", 2);
                if (ph.length == 2 && ph[0].toLowerCase(Locale.ROOT).contains("text/plain")) { text = unbase64(ph[1]); break; }
            }
        } else {
            text = "base64".equalsIgnoreCase(headers.getOrDefault("content-transfer-encoding", "")) ? unbase64(rest) : rest;
        }
        return new Read(headers, text);
    }

    /** A message read back: headers by lower-case name, and the text. */
    record Read(java.util.Map<String, String> headers, String text) {
        String header(String name) { return headers.get(name.toLowerCase(Locale.ROOT)); }
    }

    private static String unbase64(String s) {
        try {
            return new String(Base64.getMimeDecoder().decode(s.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return s;
        }
    }

    /** The one encoded-word shape {@link #encodeWord} writes; anything else is returned as it is. */
    static String decodeWord(String s) {
        Matcher m = Pattern.compile("^=\\?UTF-8\\?B\\?([A-Za-z0-9+/=]+)\\?=$", Pattern.CASE_INSENSITIVE).matcher(s == null ? "" : s);
        return m.find() ? new String(Base64.getDecoder().decode(m.group(1)), StandardCharsets.UTF_8) : s;
    }

    /** "Re: x" unless it already starts with Re:. */
    static String replySubject(String subject) {
        String s = subject == null ? "" : subject.trim();
        return s.toLowerCase(Locale.ROOT).startsWith("re:") ? s : "Re: " + s;
    }

    static String forwardSubject(String subject) {
        String s = subject == null ? "" : subject.trim();
        String l = s.toLowerCase(Locale.ROOT);
        return l.startsWith("fwd:") || l.startsWith("fw:") ? s : "Fwd: " + s;
    }

    /** The bare addresses in a header ("Pat <pat@x.com>, b@y.com" -> pat@x.com, b@y.com), lower case, once each. */
    static List<String> addresses(String header) {
        Set<String> out = new LinkedHashSet<>();
        if (header != null) {
            Matcher m = ADDRESS.matcher(header);
            while (m.find()) out.add(m.group().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(out);
    }

    /** Reply-all's Cc: everyone the message went to, less the account itself and the reply's To. */
    static String replyAllCc(String to, String cc, String self, String replyTo) {
        List<String> keep = new ArrayList<>();
        Set<String> drop = new LinkedHashSet<>(addresses(replyTo));
        if (self != null) drop.add(self.toLowerCase(Locale.ROOT));
        for (String a : addresses((to == null ? "" : to) + "," + (cc == null ? "" : cc))) {
            if (!drop.contains(a)) keep.add(a);
        }
        return String.join(", ", keep);
    }

    /** Readable text for an HTML-only message: line breaks kept at br / p / div / li / tr / headings. */
    static String htmlToText(String html) {
        if (html == null || html.isBlank()) return "";
        // Mark the line breaks in the markup itself, then let jsoup take the tags away.
        String marked = html.replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</(p|div|li|tr|h[1-6]|blockquote)\\s*>", "$0\n");
        Document d = Jsoup.parse(marked);
        d.select("script, style, head").remove();
        String t = (d.body() == null ? d.wholeText() : d.body().wholeText()).replace(NBSP, ' ');
        return t.replaceAll("[ \\t]+\n", "\n").replaceAll("\n[ \\t]+", "\n").replaceAll("\n{3,}", "\n\n").trim();
    }

    /**
     * Gmail's snippet is HTML-escaped ("Toys &amp;amp; Games", "You&amp;#39;re") and marketing mail pads it
     * with invisible spacer characters (combining grapheme joiner, zero-width joiners, soft
     * hyphens). Decode the entities, drop the spacers, and close up the white space.
     */
    static String unescapeSnippet(String snippet) {
        if (snippet == null) return null;
        String t = org.jsoup.parser.Parser.unescapeEntities(snippet, false);
        t = t.replaceAll("[\\u034F\\u00AD\\u200B-\\u200F\\u2060\\uFEFF]", "").replace(NBSP, ' ');
        return t.replaceAll("\\s{2,}", " ").trim();
    }

    /** "On <date>, <from> wrote:" then every line of the original prefixed with "> ". */
    static String quoteForReply(String when, String from, String originalText) {
        StringBuilder b = new StringBuilder();
        b.append("On ").append(when).append(", ").append(from == null ? "someone" : from).append(" wrote:\n");
        for (String line : (originalText == null ? "" : originalText).split("\r?\n", -1)) b.append("> ").append(line).append('\n');
        return b.toString();
    }

    static String forwardBlock(String when, String from, String to, String subject, String originalText) {
        return "---------- Forwarded message ---------\n"
            + "From: " + (from == null ? "" : from) + "\n"
            + "Date: " + when + "\n"
            + "Subject: " + (subject == null ? "" : subject) + "\n"
            + "To: " + (to == null ? "" : to) + "\n\n"
            + (originalText == null ? "" : originalText) + "\n";
    }
}
