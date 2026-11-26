package com.echo;

import com.echo.DailyWorkModels.Contact;
import com.echo.DailyWorkModels.Entry;
import com.echo.DailyWorkModels.EntryDraft;
import com.echo.DailyWorkModels.Filter;
import com.echo.DailyWorkModels.Kind;
import com.echo.DailyWorkModels.Lookups;
import com.echo.DailyWorkModels.Team;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Story 6852 (Daily Work): the rules in front of the procs. The procs check everything again
 * (they are the last word); this layer turns obviously bad input into a plain 400 before a
 * round trip, trims what was typed, and drops blank links and repeated contacts.
 */
@Service
public class DailyWorkService {

    static final int MAX_WORK_DONE = 1000;
    static final int MAX_STORY_REF = 400;
    static final int MAX_NAME      = 100;
    static final int MAX_CONTACT   = 150;
    static final int MAX_LINKS     = 50;
    static final int MAX_CONTACTS  = 50;

    private final DailyWorkRepository repo;

    public DailyWorkService(DailyWorkRepository repo) { this.repo = repo; }

    public Lookups lookups() {
        return new Lookups(repo.listTeams(), repo.listContacts(), repo.listKinds());
    }

    public Team addTeam(String name) {
        return repo.saveTeam(name(name, MAX_NAME, "A team"));
    }

    public Contact addContact(String name, Integer teamId) {
        return repo.saveContact(name(name, MAX_CONTACT, "A contact"), positiveOrNull(teamId));
    }

    public Kind addKind(String name) {
        return repo.saveKind(name(name, MAX_NAME, "A kind of work"));
    }

    public List<Entry> search(Filter f) {
        if (f.fromDate() != null && f.toDate() != null && f.toDate().isBefore(f.fromDate()))
            throw new IllegalArgumentException("The To date is before the From date.");
        String text = f.text() == null ? null : f.text().trim();
        if (text != null && text.length() > 200) text = text.substring(0, 200);
        int top = f.top() <= 0 ? 500 : Math.min(f.top(), 2000);
        return repo.search(new Filter(f.fromDate(), f.toDate(), text == null || text.isEmpty() ? null : text,
            positiveOrNull(f.contactId()), positiveOrNull(f.teamId()), f.followUpOnly(), top));
    }

    public Optional<Entry> get(int id) {
        return id <= 0 ? Optional.empty() : repo.get(id);
    }

    /** id 0 adds. Returns the entry's id. */
    public int save(int id, EntryDraft d, String user) {
        if (d == null) throw new IllegalArgumentException("Say what you did.");
        String workDone = d.workDone() == null ? "" : d.workDone().trim();
        if (workDone.isEmpty()) throw new IllegalArgumentException("Say what you did.");
        if (workDone.length() > MAX_WORK_DONE)
            throw new IllegalArgumentException("What you did is longer than " + MAX_WORK_DONE + " characters. Put the rest in Notes.");
        String story = blankToNull(d.storyRef());
        if (story != null && story.length() > MAX_STORY_REF)
            throw new IllegalArgumentException("The story is longer than " + MAX_STORY_REF + " characters.");
        if (d.minutesSpent() != null && (d.minutesSpent() < 0 || d.minutesSpent() > 1440))
            throw new IllegalArgumentException("Minutes spent should be between 0 and 1440.");

        List<Integer> contacts = new ArrayList<>(new LinkedHashSet<>(
            d.contactIds() == null ? List.of() : d.contactIds().stream().filter(x -> x != null && x > 0).toList()));
        if (contacts.size() > MAX_CONTACTS) throw new IllegalArgumentException("An entry can name up to " + MAX_CONTACTS + " contacts.");

        List<String> links = new ArrayList<>();
        if (d.links() != null) {
            for (String l : d.links()) {
                if (l == null) continue;
                for (String one : l.split("\\r?\\n")) {
                    String t = one.trim();
                    if (t.isEmpty()) continue;
                    if (!t.regionMatches(true, 0, "http://", 0, 7) && !t.regionMatches(true, 0, "https://", 0, 8))
                        throw new IllegalArgumentException("A link should start with http:// or https://");
                    links.add(t);
                }
            }
        }
        if (links.size() > MAX_LINKS) throw new IllegalArgumentException("An entry can hold up to " + MAX_LINKS + " links.");

        EntryDraft clean = new EntryDraft(d.workDate(), workDone, story, positiveOrNull(d.teamId()),
            positiveOrNull(d.kindId()), d.minutesSpent(), d.isFollowUp(), blankToNull(d.notes()), contacts, links);
        return repo.save(Math.max(id, 0), clean, user);
    }

    public boolean setFollowUp(int id, boolean followUp, String user) {
        return id > 0 && repo.setFollowUp(id, followUp, user);
    }

    public boolean delete(int id) {
        return id > 0 && repo.delete(id);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static String name(String raw, int max, String what) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) throw new IllegalArgumentException(what + " needs a name.");
        if (t.length() > max) throw new IllegalArgumentException(what + "'s name is longer than " + max + " characters.");
        return t;
    }

    private static Integer positiveOrNull(Integer v) { return v == null || v <= 0 ? null : v; }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
