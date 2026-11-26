package com.echo;

import com.echo.DailyWorkModels.Contact;
import com.echo.DailyWorkModels.Entry;
import com.echo.DailyWorkModels.EntryDraft;
import com.echo.DailyWorkModels.Filter;
import com.echo.DailyWorkModels.Kind;
import com.echo.DailyWorkModels.Team;

import java.util.List;
import java.util.Optional;

/**
 * Story 6852 (Daily Work): data access, stored procedures only (stp_Team_*, stp_Contact_*,
 * stp_DailyWorkKind_*, stp_DailyWorkEntry_*). A proc refusal ("... Nothing was saved.")
 * arrives as a {@link SaveRefusedException}.
 */
public interface DailyWorkRepository {

    List<Team> listTeams();

    /** Find or create by name. */
    Team saveTeam(String name);

    List<Contact> listContacts();

    /** Find or create by name; a new contact (or one with no team yet) joins teamId when given. */
    Contact saveContact(String name, Integer teamId);

    List<Kind> listKinds();

    /** Find or create by name. */
    Kind saveKind(String name);

    List<Entry> search(Filter filter);

    Optional<Entry> get(int id);

    /** id 0 adds. Returns the entry's id. */
    int save(int id, EntryDraft draft, String user);

    /** Returns false when the entry was already gone. */
    boolean setFollowUp(int id, boolean followUp, String user);

    /** Returns false when the entry was already gone. */
    boolean delete(int id);
}
