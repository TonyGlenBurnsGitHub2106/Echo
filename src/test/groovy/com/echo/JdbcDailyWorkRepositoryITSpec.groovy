package com.echo

// Story 6852: JdbcDailyWorkRepository against a real EchoTest_ database (stp_Team_*, stp_Contact_*,
// stp_DailyWorkKind_*, stp_DailyWorkEntry_*, vw_DailyWorkEntry). Every name starts "zz " and carries
// uniq(), and every feature is rolled back by EchoDbITSpec.

import java.time.LocalDateTime

import org.springframework.jdbc.core.JdbcTemplate

import com.echo.DailyWorkModels.Contact
import com.echo.DailyWorkModels.Entry
import com.echo.DailyWorkModels.EntryDraft
import com.echo.DailyWorkModels.Filter
import com.echo.DailyWorkModels.Kind
import com.echo.DailyWorkModels.Team

import spock.lang.Unroll

class JdbcDailyWorkRepositoryITSpec extends EchoDbITSpec {

    JdbcDailyWorkRepository repo

    def setup() {
        repo = new JdbcDailyWorkRepository(new JdbcTemplate(dataSource()))
    }

    // ---- teams, contacts, kinds ----------------------------------------------------------------

    def "saveTeam should answer the same team when the same name is saved again in any case"() {

        given:
        String name = 'zz Team ' + uniq()

        when:
        Team a = repo.saveTeam(name)
        Team b = repo.saveTeam(name)
        Team c = repo.saveTeam(name.toUpperCase())

        then:
        a.id() > 0
        a.name() == name
        b.id() == a.id()
        c.id() == a.id()
        repo.listTeams().count { Team t -> t.id() == a.id() } == 1
    }

    def "saveKind should answer the same kind when the same name is saved again"() {

        given:
        String name = 'zz Kind ' + uniq()

        when:
        Kind a = repo.saveKind(name)
        Kind b = repo.saveKind(name)

        then:
        a.id() > 0
        a.name() == name
        b.id() == a.id()
        repo.listKinds().find { Kind k -> k.id() == a.id() }.name() == name
    }

    def "saveContact should answer the same contact when the same name is saved again"() {

        given:
        String name = 'zz Contact ' + uniq()

        when:
        Contact a = repo.saveContact(name, null)
        Contact b = repo.saveContact(name, null)

        then:
        a.id() > 0
        a.name() == name
        a.teamId() == null
        a.teamName() == null
        b.id() == a.id()
    }

    def "saveContact should join the team when a new contact is added with a team"() {

        given:
        Team t = repo.saveTeam('zz Team ' + uniq())

        when:
        Contact c = repo.saveContact('zz Contact ' + uniq(), t.id())

        then:
        c.teamId() == t.id()
        c.teamName() == t.name()
        repo.listContacts().find { Contact x -> x.id() == c.id() }.teamId() == t.id()
    }

    def "saveContact should give a team to a contact that had none and never move one that has a team"() {

        given:
        Team t1 = repo.saveTeam('zz Team ' + uniq())
        Team t2 = repo.saveTeam('zz Team ' + uniq())
        String loose = 'zz Contact ' + uniq()
        String placed = 'zz Contact ' + uniq()
        repo.saveContact(loose, null)
        repo.saveContact(placed, t1.id())

        when:
        Contact a = repo.saveContact(loose, t2.id())
        Contact b = repo.saveContact(placed, t2.id())

        then:
        a.teamId() == t2.id()
        b.teamId() == t1.id()
    }

    def "saveContact should refuse when the team is not in Echo"() {

        when:
        repo.saveContact('zz Contact ' + uniq(), 987654321)

        then:
        SaveRefusedException ex = thrown()
        ex.message == 'That team is not in Echo. Nothing was saved.'
    }

    @Unroll
    def "#what should refuse when the name is blank"() {

        when:
        callSave(repo, what)

        then:
        SaveRefusedException ex = thrown()
        ex.message.endsWith('Nothing was saved.')

        where:
        what << ['saveTeam', 'saveContact', 'saveKind']
    }

    // ---- save and get --------------------------------------------------------------------------

    def "save should date the entry now and leave every optional field empty when only workDone is given"() {

        given:
        String done = 'zz only words ' + uniq()
        LocalDateTime before = LocalDateTime.now().minusMinutes(2)

        when:
        int id = repo.save(0, draft(done), 'tony')
        Entry e = repo.get(id).get()

        then:
        id > 0
        e.id() == id
        e.workDone() == done
        e.workDate().isAfter(before)
        e.workDate().isBefore(LocalDateTime.now().plusMinutes(2))
        e.storyRef() == null
        e.teamId() == null
        e.teamName() == null
        e.kindId() == null
        e.kindName() == null
        e.minutesSpent() == null
        !e.followUp()
        e.notes() == null
        e.notesPreview() == null
        !e.notesCut()
        e.contacts() == []
        e.links() == []
        e.createdBy() == 'tony'
        e.createdDate() != null
        e.modifiedBy() == null
        e.modifiedDate() == null
    }

    def "save should store every field and get should read them back when a full entry is saved"() {

        given:
        Team t = repo.saveTeam('zz Team ' + uniq())
        Kind k = repo.saveKind('zz Kind ' + uniq())
        Contact c1 = repo.saveContact('zz Contact A ' + uniq(), t.id())
        Contact c2 = repo.saveContact('zz Contact B ' + uniq(), t.id())
        LocalDateTime when = getLocalDateTime('11/20/2026 14:05')
        String done = 'zz full ' + uniq()

        when:
        int id = repo.save(0, new EntryDraft(when, done, 'CLM-12', t.id(), k.id(), 45, true, 'Some notes',
                                             [c2.id(), c1.id()], ['https://wiki.example/b', 'https://wiki.example/a']), 'tony')
        Entry e = repo.get(id).get()

        then:
        e.workDate() == when
        e.workDone() == done
        e.storyRef() == 'CLM-12'
        e.teamId() == t.id()
        e.teamName() == t.name()
        e.kindId() == k.id()
        e.kindName() == k.name()
        e.minutesSpent() == 45
        e.followUp()
        e.notes() == 'Some notes'
        e.notesPreview() == 'Some notes'
        !e.notesCut()
        e.contacts()*.id() == [c2.id(), c1.id()]
        e.contacts()*.name() == [c2.name(), c1.name()]
        e.links() == ['https://wiki.example/b', 'https://wiki.example/a']
    }

    def "save should keep the minutes at both ends of a day when they are 0 and 1440"() {

        when:
        int a = repo.save(0, new EntryDraft(null, 'zz zero ' + uniq(), null, null, null, 0, false, null, null, null), 'tony')
        int b = repo.save(0, new EntryDraft(null, 'zz day ' + uniq(), null, null, null, 1440, false, null, null, null), 'tony')

        then:
        repo.get(a).get().minutesSpent() == 0
        repo.get(b).get().minutesSpent() == 1440
    }

    def "save should replace the contacts and links and stamp the editor when an entry is edited"() {

        given:
        Contact c1 = repo.saveContact('zz Contact 1 ' + uniq(), null)
        Contact c2 = repo.saveContact('zz Contact 2 ' + uniq(), null)
        Contact c3 = repo.saveContact('zz Contact 3 ' + uniq(), null)
        String done = 'zz edit ' + uniq()
        int id = repo.save(0, new EntryDraft(null, done, null, null, null, null, false, null,
                                             [c1.id(), c2.id()], ['https://a.example', 'https://b.example']), 'tony')

        when:
        int again = repo.save(id, new EntryDraft(null, done + ' changed', null, null, null, null, false, null,
                                                 [c3.id(), c1.id()], ['https://b.example']), 'felix')
        Entry e = repo.get(id).get()

        then:
        again == id
        e.workDone() == done + ' changed'
        e.contacts()*.id() == [c3.id(), c1.id()]
        e.links() == ['https://b.example']
        e.createdBy() == 'tony'
        e.modifiedBy() == 'felix'
        e.modifiedDate() != null
        count('SELECT COUNT(*) FROM dbo.DailyWorkEntryContact WHERE DailyWorkEntryID = ?', id) == 2
    }

    def "save should clear the contacts and links when an edit sends none"() {

        given:
        Contact c = repo.saveContact('zz Contact ' + uniq(), null)
        int id = repo.save(0, new EntryDraft(null, 'zz clear ' + uniq(), null, null, null, null, false, null,
                                             [c.id()], ['https://a.example']), 'tony')

        when:
        repo.save(id, draft('zz cleared ' + uniq()), 'tony')
        Entry e = repo.get(id).get()

        then:
        e.contacts() == []
        e.links() == []
    }

    def "save should keep the contacts and their order when an edit sends the same contacts reordered"() {

        given:
        Contact c1 = repo.saveContact('zz Contact 1 ' + uniq(), null)
        Contact c2 = repo.saveContact('zz Contact 2 ' + uniq(), null)
        int id = repo.save(0, new EntryDraft(null, 'zz order ' + uniq(), null, null, null, null, false, null,
                                             [c1.id(), c2.id()], null), 'tony')

        when:
        repo.save(id, new EntryDraft(null, 'zz order again ' + uniq(), null, null, null, null, false, null,
                                     [c2.id(), c1.id()], null), 'tony')

        then:
        repo.get(id).get().contacts()*.id() == [c2.id(), c1.id()]
    }

    def "save should store a link whose only change is its letter case when an entry is edited"() {

        given:
        int id = repo.save(0, new EntryDraft(null, 'zz case ' + uniq(), null, null, null, null, false, null,
                                             null, ['https://wiki.example/Page']), 'tony')

        when:
        repo.save(id, new EntryDraft(null, 'zz case again ' + uniq(), null, null, null, null, false, null,
                                     null, ['https://wiki.example/page']), 'tony')

        then:
        repo.get(id).get().links() == ['https://wiki.example/page']
    }

    def "save should give a contact with no team the entry's team and leave a contact with a team alone"() {

        given:
        Team t1 = repo.saveTeam('zz Team ' + uniq())
        Team t2 = repo.saveTeam('zz Team ' + uniq())
        Contact loose = repo.saveContact('zz Loose ' + uniq(), null)
        Contact placed = repo.saveContact('zz Placed ' + uniq(), t1.id())

        when:
        repo.save(0, new EntryDraft(null, 'zz learn ' + uniq(), null, t2.id(), null, null, false, null,
                                    [loose.id(), placed.id()], null), 'tony')
        List<Contact> all = repo.listContacts()

        then:
        all.find { Contact x -> x.id() == loose.id() }.teamId() == t2.id()
        all.find { Contact x -> x.id() == loose.id() }.teamName() == t2.name()
        all.find { Contact x -> x.id() == placed.id() }.teamId() == t1.id()
    }

    def "save should leave a contact's team empty when the entry has no team"() {

        given:
        Contact loose = repo.saveContact('zz Loose ' + uniq(), null)

        when:
        repo.save(0, new EntryDraft(null, 'zz no team ' + uniq(), null, null, null, null, false, null, [loose.id()], null), 'tony')

        then:
        repo.listContacts().find { Contact x -> x.id() == loose.id() }.teamId() == null
    }

    // ---- refusals ------------------------------------------------------------------------------

    def "save should refuse a second new entry with the same words when it is a double tap"() {

        given:
        String done = 'zz double ' + uniq()
        repo.save(0, draft(done), 'tony')

        when:
        repo.save(0, draft(done), 'tony')

        then:
        SaveRefusedException ex = thrown()
        ex.message == 'That entry was just saved. Nothing was saved.'
        count('SELECT COUNT(*) FROM dbo.DailyWorkEntry WHERE WorkDone = ?', done) == 1
    }

    def "save should accept an edit that keeps the same words when the entry was just saved"() {

        given:
        String done = 'zz same words ' + uniq()
        int id = repo.save(0, draft(done), 'tony')

        when:
        int again = repo.save(id, new EntryDraft(null, done, null, null, null, 30, false, null, null, null), 'tony')

        then:
        again == id
        repo.get(id).get().minutesSpent() == 30
    }

    @Unroll
    def "#seq. save should refuse '#message' and write nothing when #scenario"() {

        given:
        String done = 'zz refused ' + uniq()
        EntryDraft d = new EntryDraft(null, done, null, teamId, kindId, minutes, false, null, intsOf(contacts), linesOf(links))

        when:
        repo.save(entryId, d, 'tony')

        then:
        SaveRefusedException ex = thrown()
        ex.message == message
        count('SELECT COUNT(*) FROM dbo.DailyWorkEntry WHERE WorkDone = ?', done) == 0

        where:
        seq | scenario                     | entryId   | teamId    | kindId    | minutes | contacts    | links                           || message
        1   | 'a link is ftp'              | 0         | null      | null      | null    | null        | 'ftp://files.example'           || 'A link should start with http:// or https://. Nothing was saved.'
        2   | 'a link is javascript'       | 0         | null      | null      | null    | null        | 'https://a.example|javascript:x'|| 'A link should start with http:// or https://. Nothing was saved.'
        3   | 'the team is not in Echo'    | 0         | 987654321 | null      | null    | null        | null                            || 'That team is not in Echo. Nothing was saved.'
        4   | 'the kind is not in Echo'    | 0         | null      | 987654321 | null    | null        | null                            || 'That kind of work is not in Echo. Nothing was saved.'
        5   | 'a contact is not in Echo'   | 0         | null      | null      | null    | '987654321' | null                            || 'A contact on this entry is not in Echo. Nothing was saved.'
        6   | 'minutes are over a day'     | 0         | null      | null      | 1441    | null        | null                            || 'Minutes spent should be between 0 and 1440. Nothing was saved.'
        7   | 'minutes are negative'       | 0         | null      | null      | -1      | null        | null                            || 'Minutes spent should be between 0 and 1440. Nothing was saved.'
        8   | 'the entry is gone'          | 987654321 | null      | null      | null    | null        | null                            || 'That entry is no longer in Echo. Nothing was saved.'
    }

    def "save should refuse 'Say what you did.' when workDone is blank"() {

        when:
        repo.save(0, draft('   '), 'tony')

        then:
        SaveRefusedException ex = thrown()
        ex.message == 'Say what you did. Nothing was saved.'
    }

    def "save should refuse a link longer than 1000 characters"() {

        when:
        repo.save(0, new EntryDraft(null, 'zz long link ' + uniq(), null, null, null, null, false, null, null,
                                    ['https://a.example/' + getReplicatedString('x', 1000)]), 'tony')

        then:
        SaveRefusedException ex = thrown()
        ex.message == 'A link is longer than 1000 characters. Nothing was saved.'
    }

    // ---- search --------------------------------------------------------------------------------

    def "search should find entries on or between the From and To days and none outside them"() {

        given:
        String tag = 'zzdate' + uniq()
        int before = repo.save(0, dated('01/01/2001 23:59', tag + ' before'), 'tony')
        int first = repo.save(0, dated('01/02/2001 00:00', tag + ' first'), 'tony')
        int last = repo.save(0, dated('01/03/2001 23:30', tag + ' last'), 'tony')
        int after = repo.save(0, dated('01/04/2001 00:00', tag + ' after'), 'tony')

        when:
        List<Integer> ids = repo.search(new Filter(getLocalDate('01/02/2001'), getLocalDate('01/03/2001'), tag, null, null, false, 500))*.id()

        then:
        ids == [last, first]
        !ids.contains(before)
        !ids.contains(after)
    }

    def "search should need every word, in any field, when several words are given"() {

        given:
        String tag = 'zzword' + uniq()
        Team t = repo.saveTeam('zz Teamword' + uniq())
        Contact c = repo.saveContact('zz Personword' + uniq(), null)
        int batch = repo.save(0, new EntryDraft(dt('11/02/2026 10:00'), tag + ' fixed the batch', 'CLM-77', t.id(), null, null,
                                                false, null, [c.id()], ['https://wiki.example/' + tag + 'linkword']), 'tony')
        int report = repo.save(0, new EntryDraft(dt('11/01/2026 10:00'), tag + ' wrote the report', null, null, null, null,
                                                 false, null, null, null), 'tony')

        expect:
        idsFor(tag) == [batch, report]
        idsFor(tag + ' batch') == [batch]
        idsFor('BATCH ' + tag) == [batch]
        idsFor(tag + ' report') == [report]
        idsFor(tag + ' batch report') == []
        idsFor(tag + ' CLM-77') == [batch]
        idsFor(tag + ' ' + t.name().substring(3)) == [batch]
        idsFor(tag + ' ' + c.name().substring(3)) == [batch]
        idsFor(tag + 'linkword') == [batch]
    }

    def "search should never look inside the notes when a word is only in the notes"() {

        given:
        String tag = 'zznote' + uniq()
        repo.save(0, new EntryDraft(null, 'zz notes only ' + uniq(), null, null, null, null, false, 'secret ' + tag, null, null), 'tony')

        expect:
        idsFor(tag) == []
    }

    def "search should find a word with a letter outside Latin-1 when the entry holds it"() {

        given:
        String tag = 'zzcyr' + uniq() + '\u0416\u0438\u043b'
        int id = repo.save(0, draft('zz ' + tag), 'tony')

        expect:
        repo.get(id).get().workDone() == 'zz ' + tag
        idsFor(tag) == [id]
    }

    def "search should treat % and _ as plain letters when the words hold them"() {

        given:
        String tag = 'zzpct' + uniq()
        int id = repo.save(0, draft(tag + ' 50%_done'), 'tony')
        repo.save(0, draft(tag + ' 50xxdone'), 'tony')

        expect:
        idsFor(tag + ' 50%_done') == [id]
    }

    def "search should answer only the entries naming the contact when a contact is chosen"() {

        given:
        String tag = 'zzcon' + uniq()
        Contact c = repo.saveContact('zz Contact ' + uniq(), null)
        Contact other = repo.saveContact('zz Other ' + uniq(), null)
        int mine = repo.save(0, new EntryDraft(null, tag + ' mine', null, null, null, null, false, null, [other.id(), c.id()], null), 'tony')
        repo.save(0, new EntryDraft(null, tag + ' theirs', null, null, null, null, false, null, [other.id()], null), 'tony')
        repo.save(0, draft(tag + ' nobody'), 'tony')

        expect:
        repo.search(new Filter(null, null, tag, c.id(), null, false, 500))*.id() == [mine]
    }

    def "search should answer only the entries of the team when a team is chosen"() {

        given:
        String tag = 'zzteam' + uniq()
        Team t = repo.saveTeam('zz Team ' + uniq())
        int mine = repo.save(0, new EntryDraft(null, tag + ' mine', null, t.id(), null, null, false, null, null, null), 'tony')
        repo.save(0, draft(tag + ' none'), 'tony')

        expect:
        repo.search(new Filter(null, null, tag, null, t.id(), false, 500))*.id() == [mine]
    }

    def "search should answer only flagged entries when follow-up only is asked for"() {

        given:
        String tag = 'zzflag' + uniq()
        int flagged = repo.save(0, new EntryDraft(null, tag + ' flagged', null, null, null, null, true, null, null, null), 'tony')
        int plain = repo.save(0, draft(tag + ' plain'), 'tony')

        expect:
        repo.search(new Filter(null, null, tag, null, null, true, 500))*.id() == [flagged]
        repo.search(new Filter(null, null, tag, null, null, false, 500))*.id().toSet() == [flagged, plain].toSet()
    }

    def "search should answer at most top entries, newest first, when more match"() {

        given:
        String tag = 'zztop' + uniq()
        repo.save(0, dated('11/01/2026 08:00', tag + ' one'), 'tony')
        int newest = repo.save(0, dated('11/03/2026 08:00', tag + ' three'), 'tony')
        repo.save(0, dated('11/02/2026 08:00', tag + ' two'), 'tony')

        expect:
        repo.search(new Filter(null, null, tag, null, null, false, 1))*.id() == [newest]
    }

    def "search should never return the full notes and should cut the preview at 300 when the notes are long"() {

        given:
        String tag = 'zzlong' + uniq()
        String notes = getReplicatedString('n', 350)
        int id = repo.save(0, new EntryDraft(null, tag + ' long', null, null, null, null, false, notes, null, null), 'tony')

        when:
        Entry listed = repo.search(new Filter(null, null, tag, null, null, false, 500)).first()
        Entry full = repo.get(id).get()

        then:
        listed.id() == id
        listed.notes() == null
        listed.notesPreview() == getReplicatedString('n', 300)
        listed.notesCut()
        full.notes() == notes
        full.notesCut()
    }

    def "search should read the whole short notes as the preview and not cut them when the notes are short"() {

        given:
        String tag = 'zzshort' + uniq()
        repo.save(0, new EntryDraft(null, tag + ' short', null, null, null, null, false, 'brief', null, null), 'tony')

        when:
        Entry listed = repo.search(new Filter(null, null, tag, null, null, false, 500)).first()

        then:
        listed.notes() == null
        listed.notesPreview() == 'brief'
        !listed.notesCut()
    }

    def "search should carry the contacts and links in order when an entry has them"() {

        given:
        String tag = 'zzcarry' + uniq()
        Contact c1 = repo.saveContact('zz Contact 1 ' + uniq(), null)
        Contact c2 = repo.saveContact('zz Contact 2 ' + uniq(), null)
        repo.save(0, new EntryDraft(null, tag, null, null, null, null, false, null, [c2.id(), c1.id()],
                                    ['https://b.example', 'https://a.example']), 'tony')

        when:
        Entry listed = repo.search(new Filter(null, null, tag, null, null, false, 500)).first()

        then:
        listed.contacts()*.id() == [c2.id(), c1.id()]
        listed.links() == ['https://b.example', 'https://a.example']
    }

    // ---- setFollowUp, delete, get --------------------------------------------------------------

    def "setFollowUp should set and clear the flag and stamp the editor when the entry exists"() {

        given:
        int id = repo.save(0, draft('zz follow ' + uniq()), 'tony')

        when:
        boolean on = repo.setFollowUp(id, true, 'felix')
        Entry flagged = repo.get(id).get()
        boolean again = repo.setFollowUp(id, true, 'felix')
        boolean off = repo.setFollowUp(id, false, 'tony')

        then:
        on
        again
        off
        flagged.followUp()
        flagged.modifiedBy() == 'felix'
        flagged.modifiedDate() != null
        !repo.get(id).get().followUp()
    }

    def "setFollowUp should answer false when the entry is gone"() {

        expect:
        !repo.setFollowUp(987654321, true, 'tony')
    }

    def "get should answer empty when the entry is not in Echo"() {

        expect:
        !repo.get(987654321).isPresent()
    }

    def "delete should remove the entry with its contacts and links, audit it, and answer false the second time"() {

        given:
        Contact c = repo.saveContact('zz Contact ' + uniq(), null)
        int id = repo.save(0, new EntryDraft(null, 'zz delete ' + uniq(), null, null, null, null, false, null,
                                             [c.id()], ['https://a.example']), 'tony')

        when:
        boolean first = repo.delete(id)
        boolean second = repo.delete(id)

        then:
        first
        !second
        !repo.get(id).isPresent()
        count('SELECT COUNT(*) FROM dbo.DailyWorkEntryContact WHERE DailyWorkEntryID = ?', id) == 0
        count('SELECT COUNT(*) FROM dbo.DailyWorkEntryLink WHERE DailyWorkEntryID = ?', id) == 0
        repo.listContacts().any { Contact x -> x.id() == c.id() }
        logRows('DailyWorkEntry', 'DailyWorkEntryID', id).any { Map<String, Object> r -> r.DeletedDate != null }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private List<Integer> idsFor(String text) {
        repo.search(new Filter(null, null, text, null, null, false, 500))*.id()
    }

    private static EntryDraft draft(String workDone) {
        new EntryDraft(null, workDone, null, null, null, null, false, null, null, null)
    }

    private static EntryDraft dated(String when, String workDone) {
        new EntryDraft(dt(when), workDone, null, null, null, null, false, null, null, null)
    }

    private static LocalDateTime dt(String when) { getLocalDateTime(when) }

    /** "3,7" -> [3, 7]; null -> null. */
    private static List<Integer> intsOf(String csv) {
        csv == null ? null : csv.split(',').collect { String s -> Integer.valueOf(s) }
    }

    /** "a|b" -> ['a', 'b']; null -> null. */
    private static List<String> linesOf(String items) {
        items == null ? null : items.split('\\|').toList()
    }

    private static Object callSave(JdbcDailyWorkRepository r, String what) {
        switch (what) {
            case 'saveTeam': return r.saveTeam('   ')
            case 'saveContact': return r.saveContact('   ', null)
            case 'saveKind': return r.saveKind('   ')
            default: throw new IllegalArgumentException(what)
        }
    }
}
