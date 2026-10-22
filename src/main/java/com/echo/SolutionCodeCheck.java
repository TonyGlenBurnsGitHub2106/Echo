package com.echo;

import com.echo.SolutionModels.CodeName;
import com.echo.SolutionModels.Lookups;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Refuses a code nobody defined (Story 3493). Statuses, people and the two kinds
 * of type all arrive as CODES, and the save procs are forgiving: an unknown
 * status quietly becomes the first status, an unknown person quietly becomes
 * nobody. That is kind to a person at a dropdown and treacherous to an agent
 * working from text -- it is told "200, saved" and the record says something
 * else. So every save names its codes here first, and a wrong one is a 409
 * whose message lists the right ones.
 *
 * A blank code is never an error: on an update it means "leave it", and on a
 * create it means "the default".
 *
 * One kindness: an API client may always name ITSELF. Its member row is made
 * the first time it writes, which would otherwise be one call too late for
 * "assign this to me" on its very first item.
 */
@Service
public class SolutionCodeCheck {

    private final SolutionRepository repo;

    public SolutionCodeCheck(SolutionRepository repo) { this.repo = repo; }

    public void status(String code) {
        Lookups l = repo.lookups();
        require("status", code, l.statuses().stream().map(s -> s.code()).collect(Collectors.toList()));
    }

    public void logType(String code) {
        require("log type", code, repo.lookups().logTypes().stream().map(t -> t.code()).collect(Collectors.toList()));
    }

    public void noteType(String code) {
        require("note type", code, repo.lookups().noteTypes().stream().map(t -> t.code()).collect(Collectors.toList()));
    }

    /**
     * The Kind picker sends a NAME (find-or-create). An existing kind matches by
     * name or code; a new name creates the kind -- for a person only. An API
     * client may name any kind that exists, but never mints one: kinds are
     * shared by every solution, so an agent's new kind would appear in the
     * house's other solutions. A blank name leaves the code as sent.
     */
    public String noteTypeCode(SolutionCaller caller, String code, String name) {
        String n = trim(name);
        if (n.isEmpty()) return code;
        String found = byName(repo.lookups().noteTypes(), n);
        if (found != null) return found;
        if (caller == null || caller.isClient()) throw unknownName("note", n, repo.lookups().noteTypes());
        int id = repo.findOrCreateNoteType(n);
        return codeOf(repo.lookups().noteTypes(), id, n);
    }

    /** As {@link #noteTypeCode}, for a log entry's kind. */
    public String logTypeCode(SolutionCaller caller, String code, String name) {
        String n = trim(name);
        if (n.isEmpty()) return code;
        String found = byName(repo.lookups().logTypes(), n);
        if (found != null) return found;
        if (caller == null || caller.isClient()) throw unknownName("log", n, repo.lookups().logTypes());
        int id = repo.findOrCreateLogType(n);
        return codeOf(repo.lookups().logTypes(), id, n);
    }

    private static String byName(List<CodeName> kinds, String name) {
        String wanted = norm(name);
        return kinds.stream().filter(k -> norm(k.name()).equals(wanted) || norm(k.code()).equals(wanted))
            .map(CodeName::code).findFirst().orElse(null);
    }

    private static String codeOf(List<CodeName> kinds, int id, String name) {
        return kinds.stream().filter(k -> k.id() == id).map(CodeName::code).findFirst()
            .orElseThrow(() -> new SolutionRuleException("The kind '" + name + "' could not be added. Nothing was saved."));
    }

    private static SolutionRuleException unknownName(String what, String name, List<CodeName> kinds) {
        return new SolutionRuleException("There is no " + what + " kind named '" + name + "'. The " + what + " kinds are: "
            + kinds.stream().map(CodeName::name).collect(Collectors.joining(", ")) + ". Nothing was saved.");
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    /** A person: an owner or an assignee. The caller may always name itself. */
    public void member(SolutionCaller caller, String code) {
        String wanted = norm(code);
        if (wanted.isEmpty()) return;
        List<String> known = repo.lookups().members().stream().filter(m -> m.active()).map(m -> m.code()).collect(Collectors.toList());
        if (contains(known, wanted)) return;
        if (caller != null && caller.isClient() && wanted.equals(norm(caller.memberCode()))) {
            repo.saveMember(0, wanted, wanted.substring(0, 1).toUpperCase(Locale.ROOT) + wanted.substring(1), true, true, known.size() + 1);
            return;
        }
        throw unknown("member", code, known);
    }

    private static void require(String what, String code, List<String> known) {
        String wanted = norm(code);
        if (wanted.isEmpty() || contains(known, wanted)) return;
        throw unknown(what, code, known);
    }

    private static boolean contains(List<String> known, String wanted) {
        return known.stream().anyMatch(k -> norm(k).equals(wanted));
    }

    private static SolutionRuleException unknown(String what, String code, List<String> known) {
        return new SolutionRuleException("There is no " + what + " code '" + code.trim() + "'. The " + what + " codes are: "
            + String.join(", ", known) + ". Nothing was saved. GET /api/solutions/lookups lists every code.");
    }

    private static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
}
