package com.echo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gmail Client search box (Story 5608): "In outlook, I can say to search FROM: Greg, TO: Greg,
 * HAS:Attachment, HAS: Attachment AND FROM: Greg. I want these kinds of features honored in our
 * search box." Turns what Tony typed into the list's filters. Any case, a space after the colon
 * allowed, a value in quotes when it has spaces:
 *
 *   from:greg      FROM: Greg      from:"Greg Smith"      (the sender's name, address or domain)
 *   to:greg        cc:greg                                (To or Cc)
 *   subject:invoice
 *   has:attachment (has:attachments, has:file)           -has:attachment  without one
 *   is:unread      is:read
 *   flag:taxes     label:taxes     tag:taxes             (a flag, or the start of one's name)
 *   after:2026-10-01   before:10/31/2026                  (on or after / on or before)
 *   newer_than:3d  older_than:2w                          (d days, w weeks, m months, y years)
 *   AND                                                   joins terms -- every term must match anyway
 *
 * Everything else is a word, or a "quoted phrase", and EVERY one must appear somewhere in the
 * sender, the subject or the preview -- at most six. OR is not understood (said so in the notes).
 * A word with a colon that is not one of the above (a web address) stays a word.
 *
 * Pure: no database, no clock beyond today's date for newer_than / older_than.
 */
final class GmailClientSearch {

    static final int MAX_WORDS = 6;
    private static final Pattern OPERATOR = Pattern.compile("^(-?)([A-Za-z_]+):(.*)$", Pattern.DOTALL);
    private static final Pattern RELATIVE = Pattern.compile("^(\\d{1,4})([dwmy])$");
    private static final DateTimeFormatter US = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US);
    private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US);

    private GmailClientSearch() { }

    /**
     * What the search box asked for. words are the free words and phrases; the rest are null when
     * not asked for. understood says it back in plain words; notes lists what was ignored and why.
     */
    record Parsed(List<String> words, String from, String to, String subject, Boolean hasAttachment,
                  String readState, String after, String before, String flag,
                  List<String> understood, List<String> notes) {

        /** The words as the procs take them: one per line, or null for none. */
        String searchParam() { return words.isEmpty() ? null : String.join("\n", words); }

        boolean isEmpty() {
            return words.isEmpty() && from == null && to == null && subject == null && hasAttachment == null
                && readState == null && after == null && before == null && flag == null;
        }
    }

    private record Token(String text, boolean quoted) {}

    /** Parses the search box. Blank is an empty search. A bad date or too many words is refused (400). */
    static Parsed parse(String q) { return parse(q, LocalDate.now()); }

    static Parsed parse(String q, LocalDate today) {
        List<Token> tokens = tokens(q == null ? "" : q);
        List<String> words = new ArrayList<>();
        List<String> understood = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        String from = null, to = null, subject = null, read = null, after = null, before = null, flag = null;
        Boolean attach = null;

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (!t.quoted()) {
                String upper = t.text().toUpperCase(Locale.ROOT);
                if (upper.equals("AND") || upper.equals("&&")) continue;
                if (upper.equals("OR") || upper.equals("||")) {
                    if (notes.stream().noneMatch(n -> n.startsWith("OR"))) {
                        notes.add("OR is not understood -- every term must match. Search once for each.");
                    }
                    continue;
                }
            }
            Matcher m = t.quoted() ? null : OPERATOR.matcher(t.text());
            String key = m != null && m.matches() ? m.group(2).toLowerCase(Locale.ROOT) : null;
            if (key == null || !isOperator(key)) {
                words.add(t.text());
                continue;
            }
            boolean negated = !m.group(1).isEmpty();
            String value = m.group(3);
            if (value.isEmpty() && i + 1 < tokens.size()) value = tokens.get(++i).text();   // "FROM: Greg"
            value = value.trim();
            if (value.isEmpty()) {
                notes.add(key + ": needs something after it.");
                continue;
            }
            switch (key) {
                case "from" -> from = value;
                case "to", "cc" -> to = value;
                case "subject" -> subject = value;
                case "has" -> {
                    String v = value.toLowerCase(Locale.ROOT);
                    if (v.startsWith("attachment") || v.startsWith("file")) attach = !negated;
                    else if (v.equals("noattachment") || v.equals("noattachments")) attach = false;
                    else notes.add("has:" + value + " is not understood -- has:attachment is.");
                }
                case "is" -> {
                    String v = value.toLowerCase(Locale.ROOT);
                    if (v.equals("unread")) read = "unread";
                    else if (v.equals("read")) read = "read";
                    else notes.add("is:" + value + " is not understood -- is:unread and is:read are.");
                }
                case "flag", "label", "tag" -> flag = value;
                case "after" -> after = date(value, key);
                case "before" -> before = date(value, key);
                case "newer_than" -> after = relative(value, key, today);
                // Older than two weeks: on or before the day before two weeks ago.
                case "older_than" -> before = LocalDate.parse(relative(value, key, today)).minusDays(1).toString();
                default -> words.add(t.text());
            }
        }

        if (words.size() > MAX_WORDS) {
            throw new IllegalArgumentException("Search for at most six words or phrases at once.");
        }
        for (String w : words) {
            if (w.length() > 200) throw new IllegalArgumentException("Keep each search word or phrase to 200 characters.");
        }

        if (from != null) understood.add("from " + quote(from));
        if (to != null) understood.add("to " + quote(to));
        if (subject != null) understood.add("subject has " + quote(subject));
        if (Boolean.TRUE.equals(attach)) understood.add("with an attachment");
        if (Boolean.FALSE.equals(attach)) understood.add("without an attachment");
        if (read != null) understood.add(read);
        if (flag != null) understood.add("flagged " + quote(flag));
        if (after != null) understood.add("on or after " + LocalDate.parse(after).format(SHOW));
        if (before != null) understood.add("on or before " + LocalDate.parse(before).format(SHOW));
        if (!words.isEmpty()) {
            List<String> q2 = new ArrayList<>();
            for (String w : words) q2.add(quote(w));
            understood.add((words.size() == 1 ? "the word " : "all of ") + String.join(", ", q2));
        }
        return new Parsed(List.copyOf(words), from, to, subject, attach, read, after, before, flag,
            List.copyOf(understood), List.copyOf(notes));
    }

    private static boolean isOperator(String key) {
        return switch (key) {
            case "from", "to", "cc", "subject", "has", "is", "flag", "label", "tag",
                 "after", "before", "newer_than", "older_than" -> true;
            default -> false;
        };
    }

    /** Splits on white space, keeping "quoted phrases" whole; from:"Greg Smith" stays one token. */
    private static List<Token> tokens(String q) {
        List<Token> out = new ArrayList<>();
        int i = 0, n = q.length();
        while (i < n) {
            char c = q.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '"') {
                int end = q.indexOf('"', i + 1);
                String phrase = end < 0 ? q.substring(i + 1) : q.substring(i + 1, end);
                if (!phrase.isBlank()) out.add(new Token(phrase.trim(), true));
                i = end < 0 ? n : end + 1;
                continue;
            }
            StringBuilder b = new StringBuilder();
            while (i < n && !Character.isWhitespace(q.charAt(i))) {
                char d = q.charAt(i);
                if (d == '"') {                                   // key:"a value with spaces"
                    int end = q.indexOf('"', i + 1);
                    b.append(end < 0 ? q.substring(i + 1) : q.substring(i + 1, end));
                    i = end < 0 ? n : end + 1;
                    continue;
                }
                b.append(d);
                i++;
            }
            out.add(new Token(b.toString(), false));
        }
        return out;
    }

    /** yyyy-mm-dd or mm/dd/yyyy -> yyyy-mm-dd. */
    private static String date(String v, String key) {
        try {
            return v.contains("/") ? LocalDate.parse(v, US).toString() : LocalDate.parse(v).toString();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(key + ": takes a date such as 2026-10-01 or 10/01/2026.");
        }
    }

    /** 3d / 2w / 6m / 1y back from today, as yyyy-mm-dd. */
    private static String relative(String v, String key, LocalDate today) {
        Matcher m = RELATIVE.matcher(v.toLowerCase(Locale.ROOT));
        if (!m.matches()) throw new IllegalArgumentException(key + ": takes a number and d, w, m or y, such as 3d or 2w.");
        int n = Integer.parseInt(m.group(1));
        LocalDate d = switch (m.group(2)) {
            case "d" -> today.minusDays(n);
            case "w" -> today.minusWeeks(n);
            case "m" -> today.minusMonths(n);
            default -> today.minusYears(n);
        };
        return d.toString();
    }

    private static String quote(String s) { return "\"" + s + "\""; }
}
