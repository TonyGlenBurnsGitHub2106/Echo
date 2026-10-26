package com.echo;

import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Story 5654: the Email Client's whitelist and blacklist -- Echo's echo of Leonardo's Mail Triage
 * decisions (Story 3561), and nothing more. Leonardo's Mail Triage also scans Gmail, keeps a
 * waiting list and makes a Gmail filter for every blacklisted sender; Echo does none of that.
 * Tony's rule for the Story: Echo NEVER creates rules in Outlook. A rule here lives in Echo's
 * database only, and the Email Client applies it when it reads mail: a blacklisted sender's mail
 * is never read in, a whitelisted sender's mail shows in the inboxes, and a sender with no rule
 * waits on the Undecided tab.
 *
 * Senders are keyed exactly as Leonardo keys them ({@link MailTriageSenders}): the registrable
 * domain, or the full address for free mail.
 */
@Service
public class MailTriageService {

    private final MailTriageRepository repo;

    public MailTriageService(MailTriageRepository repo) { this.repo = repo; }

    /** Whitelist or blacklist a sender (new, or switching sides). Returns the rule and the counts. */
    public Map<String, Object> decide(String sender, String disposition, String note) {
        MailTriageSenders.Key k = MailTriageSenders.normalizeTyped(sender);
        if (k == null) {
            throw new IllegalArgumentException("Give a domain such as nextdoor.com, or one full address. "
                + "A free-mail domain such as gmail.com is never ruled on whole -- give the person's address.");
        }
        String side = side(disposition);
        if (side == null) throw new IllegalArgumentException("Choose Whitelist or Blacklist.");
        Map<String, Object> before = repo.getRuleByKey(k.key());
        if ("black".equals(side) && before != null && truthy(before.get("IsProtected"))) {
            throw new SaveRefusedException(k.key() + " is protected and can never be blacklisted. Nothing was saved.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rule", rule(repo.saveRule(k.key(), k.kind(), side, null, null, clip(note, 500))));
        out.put("trashed", 0);
        out.put("counts", counts(null));
        return out;
    }

    /** white | black from what the page sends ("whitelist", "blacklist" and "delete" too). Null when it is neither. */
    static String side(String disposition) {
        String d = disposition == null ? "" : disposition.trim().toLowerCase(Locale.ROOT);
        return switch (d) {
            case "white", "whitelist" -> "white";
            case "black", "blacklist", "delete" -> "black";
            default -> null;
        };
    }

    /** Takes a sender off its list -- its mail goes back to Undecided. Null when there is no such rule. */
    public Map<String, Object> remove(int id) {
        Map<String, Object> rule = repo.getRule(id);
        if (rule == null) return null;
        if (truthy(rule.get("IsProtected"))) {
            throw new IllegalArgumentException(rule.get("SenderKey") + " is protected. Unprotect it first; nothing was removed.");
        }
        repo.deleteRule(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("removed", true);
        out.put("id", id);
        out.put("senderKey", rule.get("SenderKey"));
        out.put("counts", counts(null));
        return out;
    }

    /** Protect a sender (never blacklisted), or take the protection off. Null when there is no such rule. */
    public Map<String, Object> setProtected(int id, boolean protect) {
        if (repo.getRule(id) == null) return null;
        return rule(repo.setProtected(id, protect));
    }

    /** One page of rules: disposition all | white | black; search a part of the sender. */
    public Map<String, Object> rules(String disposition, String search, int offset, int size) {
        String d = disposition == null ? "all" : disposition.trim().toLowerCase(Locale.ROOT);
        if (!"white".equals(d) && !"black".equals(d)) d = "all";
        String q = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        int pageSize = size <= 0 ? 50 : Math.min(size, 200);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> r : repo.listRules(d, q, Math.max(0, offset), pageSize + 1)) rows.add(rule(r));
        boolean more = rows.size() > pageSize;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", more ? rows.subList(0, pageSize) : rows);
        m.put("hasMore", more);
        m.put("counts", counts(q));
        return m;
    }

    private Map<String, Object> counts(String search) {
        Map<String, Object> c = repo.counts(search);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("white", num(c.get("White")));
        m.put("black", num(c.get("Black")));
        m.put("protected", num(c.get("Protected")));
        m.put("matchAll", num(c.get("MatchAll")));
        m.put("matchWhite", num(c.get("MatchWhite")));
        m.put("matchBlack", num(c.get("MatchBlack")));
        return m;
    }

    static Map<String, Object> rule(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(r.get("MailTriageRuleID")));
        m.put("senderKey", r.get("SenderKey"));
        m.put("keyKind", r.get("KeyKind"));
        m.put("disposition", r.get("Disposition"));
        m.put("isProtected", truthy(r.get("IsProtected")));
        m.put("note", r.get("Note"));
        m.put("createdDate", iso(r.get("CreatedDate")));
        m.put("modifiedDate", iso(r.get("ModifiedDate")));
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
