package com.echo;

import com.echo.DailyWorkModels.Contact;
import com.echo.DailyWorkModels.Entry;
import com.echo.DailyWorkModels.EntryContact;
import com.echo.DailyWorkModels.EntryDraft;
import com.echo.DailyWorkModels.Filter;
import com.echo.DailyWorkModels.Kind;
import com.echo.DailyWorkModels.Team;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Story 6852 (Daily Work): {@link DailyWorkRepository} over the stored procedures.
 * Contacts and links come back folded into one column each by vw_DailyWorkEntry
 * ("id:name" / URL items separated by a tab) and are unfolded here.
 */
@Repository
public class JdbcDailyWorkRepository implements DailyWorkRepository {

    static final String SEPARATOR = "\t";

    private final JdbcTemplate jdbc;

    public JdbcDailyWorkRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // ---- lookups ---------------------------------------------------------------------------

    @Override
    public List<Team> listTeams() {
        List<Team> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("EXEC dbo.stp_Team_List"))
            out.add(new Team(intVal(r.get("TeamID")), str(r.get("TeamName"))));
        return out;
    }

    @Override
    public Team saveTeam(String name) {
        try {
            Map<String, Object> r = jdbc.queryForMap("EXEC dbo.stp_Team_Save @prmTeamName=?", name);
            return new Team(intVal(r.get("TeamID")), str(r.get("TeamName")));
        } catch (DataAccessException ex) {
            throw SaveRefusedException.translate(ex);
        }
    }

    @Override
    public List<Contact> listContacts() {
        List<Contact> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("EXEC dbo.stp_Contact_List")) out.add(contactOf(r));
        return out;
    }

    @Override
    public Contact saveContact(String name, Integer teamId) {
        try {
            return contactOf(jdbc.queryForMap("EXEC dbo.stp_Contact_Save @prmContactName=?, @prmTeamID=?", name, teamId));
        } catch (DataAccessException ex) {
            throw SaveRefusedException.translate(ex);
        }
    }

    @Override
    public List<Kind> listKinds() {
        List<Kind> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("EXEC dbo.stp_DailyWorkKind_List"))
            out.add(new Kind(intVal(r.get("DailyWorkKindID")), str(r.get("KindName"))));
        return out;
    }

    @Override
    public Kind saveKind(String name) {
        try {
            Map<String, Object> r = jdbc.queryForMap("EXEC dbo.stp_DailyWorkKind_Save @prmKindName=?", name);
            return new Kind(intVal(r.get("DailyWorkKindID")), str(r.get("KindName")));
        } catch (DataAccessException ex) {
            throw SaveRefusedException.translate(ex);
        }
    }

    // ---- entries ---------------------------------------------------------------------------

    @Override
    public List<Entry> search(Filter f) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "EXEC dbo.stp_DailyWorkEntry_Search @prmFromDate=?, @prmToDate=?, @prmText=?, @prmContactID=?,"
                + " @prmTeamID=?, @prmFollowUpOnly=?, @prmTop=?",
            f.fromDate() == null ? null : Date.valueOf(f.fromDate()),
            f.toDate() == null ? null : Date.valueOf(f.toDate()),
            f.text(), f.contactId(), f.teamId(), f.followUpOnly(), f.top());
        List<Entry> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) out.add(entryOf(r));
        return out;
    }

    @Override
    public Optional<Entry> get(int id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "EXEC dbo.stp_DailyWorkEntry_Get @prmDailyWorkEntryID=?", id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(entryOf(rows.get(0)));
    }

    @Override
    public int save(int id, EntryDraft d, String user) {
        String contactIds = d.contactIds() == null ? null
            : d.contactIds().stream().filter(x -> x != null).map(String::valueOf).collect(Collectors.joining(","));
        String links = d.links() == null ? null : String.join("\n", d.links());
        try {
            Map<String, Object> r = jdbc.queryForMap(
                "EXEC dbo.stp_DailyWorkEntry_Save @prmDailyWorkEntryID=?, @prmWorkDate=?, @prmWorkDone=?,"
                    + " @prmStoryRef=?, @prmTeamID=?, @prmDailyWorkKindID=?, @prmMinutesSpent=?, @prmIsFollowUp=?,"
                    + " @prmNotes=?, @prmContactIDs=?, @prmLinks=?, @prmUser=?",
                id, d.workDate() == null ? null : Timestamp.valueOf(d.workDate()), d.workDone(), d.storyRef(),
                d.teamId(), d.kindId(), d.minutesSpent(), d.isFollowUp(), d.notes(), contactIds, links, user);
            return intVal(r.get("DailyWorkEntryID"));
        } catch (DataAccessException ex) {
            throw SaveRefusedException.translate(ex);
        }
    }

    @Override
    public boolean setFollowUp(int id, boolean followUp, String user) {
        Map<String, Object> r = jdbc.queryForMap(
            "EXEC dbo.stp_DailyWorkEntry_SetFollowUp @prmDailyWorkEntryID=?, @prmIsFollowUp=?, @prmUser=?",
            id, followUp, user);
        return intVal(r.get("Updated")) > 0;
    }

    @Override
    public boolean delete(int id) {
        Map<String, Object> r = jdbc.queryForMap("EXEC dbo.stp_DailyWorkEntry_Delete @prmDailyWorkEntryID=?", id);
        return intVal(r.get("Deleted")) > 0;
    }

    // ---- rows ------------------------------------------------------------------------------

    private static Contact contactOf(Map<String, Object> r) {
        return new Contact(intVal(r.get("ContactID")), str(r.get("ContactName")),
            intOrNull(r.get("TeamID")), strOrNull(r.get("TeamName")));
    }

    static Entry entryOf(Map<String, Object> r) {
        return new Entry(
            intVal(r.get("DailyWorkEntryID")), ts(r.get("WorkDate")), str(r.get("WorkDone")),
            strOrNull(r.get("StoryRef")), intOrNull(r.get("TeamID")), strOrNull(r.get("TeamName")),
            intOrNull(r.get("DailyWorkKindID")), strOrNull(r.get("KindName")), intOrNull(r.get("MinutesSpent")),
            bool(r.get("IsFollowUp")), strOrNull(r.get("Notes")), strOrNull(r.get("NotesPreview")),
            bool(r.get("NotesCut")), contactsOf(strOrNull(r.get("ContactList"))), linksOf(strOrNull(r.get("LinkList"))),
            strOrNull(r.get("CreatedBy")), ts(r.get("CreatedDate")), strOrNull(r.get("ModifiedBy")),
            ts(r.get("ModifiedDate")));
    }

    /** "12:Jill\t7:Ron" -> [(12, Jill), (7, Ron)]. A malformed item is skipped. */
    static List<EntryContact> contactsOf(String folded) {
        List<EntryContact> out = new ArrayList<>();
        if (folded == null || folded.isEmpty()) return out;
        for (String item : folded.split(SEPARATOR)) {
            int colon = item.indexOf(':');
            if (colon <= 0) continue;
            try {
                out.add(new EntryContact(Integer.parseInt(item.substring(0, colon).trim()), item.substring(colon + 1)));
            } catch (NumberFormatException ignored) { /* skipped */ }
        }
        return out;
    }

    /** "url1\turl2" -> [url1, url2]. */
    static List<String> linksOf(String folded) {
        List<String> out = new ArrayList<>();
        if (folded == null || folded.isEmpty()) return out;
        for (String item : folded.split(SEPARATOR)) if (!item.isBlank()) out.add(item);
        return out;
    }

    private static int intVal(Object o) { return o instanceof Number n ? n.intValue() : 0; }
    private static Integer intOrNull(Object o) { return o instanceof Number n ? n.intValue() : null; }
    private static String str(Object o) { return o == null ? "" : o.toString(); }
    private static String strOrNull(Object o) { return o == null ? null : o.toString(); }
    private static boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        return o instanceof Number n && n.intValue() != 0;
    }
    private static LocalDateTime ts(Object o) {
        if (o instanceof Timestamp t) return t.toLocalDateTime();
        if (o instanceof LocalDateTime l) return l;
        return null;
    }
}
