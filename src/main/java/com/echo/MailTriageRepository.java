package com.echo;

import java.util.List;
import java.util.Map;

/**
 * Mail Triage (Story 3561) -- the sender rules. Rows come back in the procs' PascalCase column
 * shape; {@link MailTriageService} maps them to JSON.
 *
 * Story 5654: echoed into Echo as the Email Client's whitelist and blacklist, and only that -- the
 * Gmail scan, its waiting list, its runs and its connected account are Leonardo's alone. A rule
 * lives in Echo's database and never becomes an Outlook rule.
 *
 * Refusals: a proc sentence ending "Nothing was saved." becomes {@link SaveRefusedException}
 * (409); any other rule a proc raises becomes IllegalArgumentException (400).
 */
public interface MailTriageRepository {

    /** One page of rules: disposition all | white | black, search is a fragment of the sender. */
    List<Map<String, Object>> listRules(String disposition, String search, int offset, int pageSize);

    /** White, Black, Protected and the Match* counts for a search (Leonardo's Pending columns come back 0). */
    Map<String, Object> counts(String search);

    /** Rules whose key contains the search, or that the search falls under (IsCovering). */
    List<Map<String, Object>> find(String search, int maxRows);

    Map<String, Object> getRule(int id);

    Map<String, Object> getRuleByKey(String senderKey);

    /** Whitelist or blacklist a sender (new, or switch sides). */
    Map<String, Object> saveRule(String senderKey, String keyKind, String disposition,
                                 String gmailFilterId, String filterMode, String note);

    Map<String, Object> setProtected(int id, boolean isProtected);

    /** Take a rule away. A protected rule is refused. */
    boolean deleteRule(int id);
}
