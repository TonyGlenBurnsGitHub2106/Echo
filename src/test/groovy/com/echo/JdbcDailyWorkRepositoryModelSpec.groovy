package com.echo

// Story 6852: Daily Work -- unfolding the view's folded columns (contacts "id:name", links) and reading a row.

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.DailyWorkModels.Entry
import com.echo.DailyWorkModels.EntryContact

import spock.lang.Unroll

class JdbcDailyWorkRepositoryModelSpec extends EchoSpockBaseSpec {

    // ---- contactsOf ----------------------------------------------------------------------------

    @Unroll
    def "#seq. contactsOf should read #expected when the folded text is #scenario"() {

        when:
        List<EntryContact> got = JdbcDailyWorkRepository.contactsOf(folded)

        then:
        got.collect { EntryContact c -> c.id() + '=' + c.name() }.join('|') == expected

        where:
        seq | scenario                         | folded                    || expected
        1   | 'null'                           | null                      || ''
        2   | 'empty'                          | ''                        || ''
        3   | 'one contact'                    | '12:Jill'                 || '12=Jill'
        4   | 'two contacts in order'          | '12:Jill\t7:Ron'          || '12=Jill|7=Ron'
        5   | 'a name holding a colon'         | '3:Ops: Night'            || '3=Ops: Night'
        6   | 'an id with spaces'              | ' 4 :Ann'                 || '4=Ann'
        7   | 'an empty name'                  | '5:'                      || '5='
        8   | 'an item with no colon'          | '12\t7:Ron'               || '7=Ron'
        9   | 'an item starting with a colon'  | ':Jill\t7:Ron'            || '7=Ron'
        10  | 'an id that is not a number'     | 'x:Jill\t7:Ron'           || '7=Ron'
        11  | 'an id too large for an int'     | '99999999999:Big\t7:Ron'  || '7=Ron'
        12  | 'empty items between tabs'       | '\t\t7:Ron\t'             || '7=Ron'
        13  | 'only junk'                      | 'a\tb:c\t:'               || ''
    }

    def "contactsOf should keep spaces inside the name when the name has them"() {

        when:
        List<EntryContact> got = JdbcDailyWorkRepository.contactsOf('8:Mary Ann Lee')

        then:
        got == [new EntryContact(8, 'Mary Ann Lee')]
    }

    // ---- linksOf -------------------------------------------------------------------------------

    @Unroll
    def "#seq. linksOf should read #expected when the folded text is #scenario"() {

        when:
        List<String> got = JdbcDailyWorkRepository.linksOf(folded)

        then:
        got.join('|') == expected

        where:
        seq | scenario                     | folded                                       || expected
        1   | 'null'                       | null                                         || ''
        2   | 'empty'                      | ''                                           || ''
        3   | 'one link'                   | 'https://a.example'                          || 'https://a.example'
        4   | 'two links in order'         | 'https://b.example\thttps://a.example'       || 'https://b.example|https://a.example'
        5   | 'a blank item between'       | 'https://a.example\t  \thttps://b.example'   || 'https://a.example|https://b.example'
        6   | 'leading and trailing tabs'  | '\thttps://a.example\t'                      || 'https://a.example'
        7   | 'only blanks'                | ' \t \t'                                     || ''
        8   | 'a link with a query'        | 'https://a.example/p?x=1&y=2'                || 'https://a.example/p?x=1&y=2'
    }

    def "linksOf should answer a list the caller may add to when the text is null"() {

        when:
        List<String> got = JdbcDailyWorkRepository.linksOf(null)
        got.add('x')

        then:
        got == ['x']
    }

    // ---- entryOf -------------------------------------------------------------------------------

    def "entryOf should read every column when a full row comes back"() {

        given:
        LocalDateTime when = getLocalDateTime('11/25/2026 09:30')
        LocalDateTime made = getLocalDateTime('11/25/2026 10:00')
        Map<String, Object> r = [DailyWorkEntryID: 9, WorkDate: Timestamp.valueOf(when), WorkDone: 'Fixed it',
                                 StoryRef: 'CLM-1', TeamID: 2, TeamName: 'Claims', DailyWorkKindID: 3, KindName: 'Jira',
                                 MinutesSpent: 45, IsFollowUp: true, Notes: 'all', NotesPreview: 'al', NotesCut: 1,
                                 ContactList: '12:Jill\t7:Ron', LinkList: 'https://a.example',
                                 CreatedBy: 'tony', CreatedDate: Timestamp.valueOf(made), ModifiedBy: 'felix',
                                 ModifiedDate: made]

        when:
        Entry e = JdbcDailyWorkRepository.entryOf(r)

        then:
        e.id() == 9
        e.workDate() == when
        e.workDone() == 'Fixed it'
        e.storyRef() == 'CLM-1'
        e.teamId() == 2
        e.teamName() == 'Claims'
        e.kindId() == 3
        e.kindName() == 'Jira'
        e.minutesSpent() == 45
        e.followUp()
        e.notes() == 'all'
        e.notesPreview() == 'al'
        e.notesCut()
        e.contacts() == [new EntryContact(12, 'Jill'), new EntryContact(7, 'Ron')]
        e.links() == ['https://a.example']
        e.createdBy() == 'tony'
        e.createdDate() == made
        e.modifiedBy() == 'felix'
        e.modifiedDate() == made
    }

    def "entryOf should read nulls, false and empty lists when the optional columns are missing"() {

        given:
        Map<String, Object> r = [DailyWorkEntryID: 4, WorkDone: null, IsFollowUp: 0, NotesCut: false]

        when:
        Entry e = JdbcDailyWorkRepository.entryOf(r)

        then:
        e.id() == 4
        e.workDate() == null
        e.workDone() == ''
        e.storyRef() == null
        e.teamId() == null
        e.kindId() == null
        e.minutesSpent() == null
        !e.followUp()
        e.notes() == null
        !e.notesCut()
        e.contacts() == []
        e.links() == []
        e.modifiedDate() == null
    }
}
