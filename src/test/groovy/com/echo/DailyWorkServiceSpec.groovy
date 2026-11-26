package com.echo

// Story 6852: Daily Work -- the rules in front of the procs (trimming, limits, links, contacts, search).

import java.time.LocalDate
import java.time.LocalDateTime

import com.echo.DailyWorkModels.Contact
import com.echo.DailyWorkModels.Entry
import com.echo.DailyWorkModels.EntryDraft
import com.echo.DailyWorkModels.Filter
import com.echo.DailyWorkModels.Kind
import com.echo.DailyWorkModels.Lookups
import com.echo.DailyWorkModels.Team

import spock.lang.Unroll

class DailyWorkServiceSpec extends EchoSpockBaseSpec {

    DailyWorkRepository repo = Mock()
    DailyWorkService service = new DailyWorkService(repo)

    // ---- lookups -------------------------------------------------------------------------------

    def "lookups should hand back the teams, contacts and kinds when the page asks for its dropdowns"() {

        given:
        List<Team> teams = [new Team(1, 'Claims')]
        List<Contact> contacts = [new Contact(2, 'Jill', 1, 'Claims')]
        List<Kind> kinds = [new Kind(3, 'Jira')]

        when:
        Lookups got = service.lookups()

        then:
        1 * repo.listTeams() >> teams
        1 * repo.listContacts() >> contacts
        1 * repo.listKinds() >> kinds
        got.teams().is(teams)
        got.contacts().is(contacts)
        got.kinds().is(kinds)
    }

    // ---- teams, contacts, kinds ----------------------------------------------------------------

    def "addTeam should trim the name and answer the repository's team when the name is padded"() {

        given:
        Team t = new Team(4, 'Claims')

        when:
        Team got = service.addTeam('  Claims  ')

        then:
        1 * repo.saveTeam('Claims') >> t
        got.is(t)
    }

    def "addKind should trim the name and answer the repository's kind when the name is padded"() {

        given:
        Kind k = new Kind(5, 'Jira')

        when:
        Kind got = service.addKind(' Jira ')

        then:
        1 * repo.saveKind('Jira') >> k
        got.is(k)
    }

    @Unroll
    def "addContact should pass team #expected when the team id is #scenario"() {

        when:
        service.addContact(' Jill ', teamId)

        then:
        1 * repo.saveContact('Jill', expected)

        where:
        scenario      | teamId || expected
        'null'        | null   || null
        'zero'        | 0      || null
        'negative'    | -3     || null
        'positive'    | 7      || 7
    }

    @Unroll
    def "#seq. #what should refuse '#message' when the name is #scenario"() {

        given:
        String name = repeat == null ? value : getReplicatedString(value, repeat)

        when:
        callAdd(service, what, name)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == message
        0 * repo._

        where:
        seq | what         | scenario            | value | repeat || message
        1   | 'addTeam'    | 'null'              | null  | null   || 'A team needs a name.'
        2   | 'addTeam'    | 'blank'             | '   ' | null   || 'A team needs a name.'
        3   | 'addTeam'    | '101 characters'    | 'x'   | 101    || "A team's name is longer than 100 characters."
        4   | 'addContact' | 'empty'             | ''    | null   || 'A contact needs a name.'
        5   | 'addContact' | '151 characters'    | 'x'   | 151    || "A contact's name is longer than 150 characters."
        6   | 'addKind'    | 'null'              | null  | null   || 'A kind of work needs a name.'
        7   | 'addKind'    | '101 characters'    | 'x'   | 101    || "A kind of work's name is longer than 100 characters."
    }

    @Unroll
    def "#what should accept the name when it is exactly #max characters"() {

        given:
        String name = getReplicatedString('x', max)

        when:
        callAdd(service, what, name)

        then:
        1 * repo./save.*/(*_)

        where:
        what         | max
        'addTeam'    | 100
        'addContact' | 150
        'addKind'    | 100
    }

    // ---- save: what I did ----------------------------------------------------------------------

    def "save should trim what I did and pass the user and the repository's id through when only workDone is given"() {

        given:
        EntryDraft sent = null

        when:
        int id = service.save(0, new EntryDraft(null, '  Fixed the batch  ', null, null, null, null, null, null, null, null), 'tony')

        then:
        1 * repo.save(0, _, 'tony') >> { args -> sent = args[1]; 41 }
        id == 41
        sent.workDone() == 'Fixed the batch'
        sent.workDate() == null
        sent.storyRef() == null
        sent.teamId() == null
        sent.kindId() == null
        sent.minutesSpent() == null
        sent.followUp() == false
        sent.notes() == null
        sent.contactIds() == []
        sent.links() == []
    }

    @Unroll
    def "save should refuse 'Say what you did.' when workDone is #scenario"() {

        when:
        service.save(0, new EntryDraft(null, workDone, null, null, null, null, null, null, null, null), 'tony')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'Say what you did.'
        0 * repo._

        where:
        scenario    | workDone
        'null'      | null
        'empty'     | ''
        'blank'     | '   '
        'a newline' | '\n\t'
    }

    def "save should refuse 'Say what you did.' when the draft itself is null"() {

        when:
        service.save(0, null, 'tony')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'Say what you did.'
        0 * repo._
    }

    @Unroll
    def "save should #outcome when workDone is #scenario"() {

        given:
        String workDone = getReplicatedString('w', length) + pad

        when:
        Exception err = null
        try { service.save(0, new EntryDraft(null, workDone, null, null, null, null, null, null, null, null), 'tony') }
        catch (IllegalArgumentException ex) { err = ex }

        then:
        (saved ? 1 : 0) * repo.save(*_) >> 1
        (err == null) == saved
        err == null || err.message == 'What you did is longer than 1000 characters. Put the rest in Notes.'

        where:
        scenario                          | length | pad    || saved | outcome
        '1000 characters'                 | 1000   | ''     || true  | 'save'
        '1000 characters plus spaces'     | 1000   | '   '  || true  | 'save'
        '1001 characters'                 | 1001   | ''     || false | 'refuse'
    }

    // ---- save: story, minutes, ids, notes ------------------------------------------------------

    @Unroll
    def "save should pass the story as #expected when it is #scenario"() {

        given:
        EntryDraft sent = null

        when:
        service.save(0, new EntryDraft(null, 'W', story, null, null, null, null, null, null, null), 'tony')

        then:
        1 * repo.save(0, _, 'tony') >> { args -> sent = args[1]; 1 }
        sent.storyRef() == expected

        where:
        scenario    | story          || expected
        'null'      | null           || null
        'blank'     | '   '          || null
        'padded'    | ' CLM-12 '     || 'CLM-12'
    }

    @Unroll
    def "save should #outcome when the story is #length characters"() {

        given:
        String story = getReplicatedString('s', length)
        Exception err = null

        when:
        try { service.save(0, new EntryDraft(null, 'W', story, null, null, null, null, null, null, null), 'tony') }
        catch (IllegalArgumentException ex) { err = ex }

        then:
        (saved ? 1 : 0) * repo.save(*_) >> 1
        saved ? err == null : err.message == 'The story is longer than 400 characters.'

        where:
        scenario  | length || saved | outcome
        'at max'  | 400    || true  | 'save'
        'over'    | 401    || false | 'refuse'
    }

    @Unroll
    def "#seq. save should #outcome when the minutes are #scenario"() {

        given:
        Exception err = null

        when:
        try { service.save(0, new EntryDraft(null, 'W', null, null, null, minutes, null, null, null, null), 'tony') }
        catch (IllegalArgumentException ex) { err = ex }

        then:
        (saved ? 1 : 0) * repo.save(*_) >> 1
        saved ? err == null : err.message == 'Minutes spent should be between 0 and 1440.'

        where:
        seq | scenario          | minutes || saved | outcome
        1   | 'null'            | null    || true  | 'save'
        2   | 'zero'            | 0       || true  | 'save'
        3   | 'one'             | 1       || true  | 'save'
        4   | 'a whole day'     | 1440    || true  | 'save'
        5   | 'one past a day'  | 1441    || false | 'refuse'
        6   | 'negative'        | -1      || false | 'refuse'
        7   | 'huge'            | 99999   || false | 'refuse'
    }

    @Unroll
    def "save should pass team #team and kind #kind when they are #scenario"() {

        given:
        EntryDraft sent = null

        when:
        service.save(0, new EntryDraft(null, 'W', null, teamId, kindId, null, null, null, null, null), 'tony')

        then:
        1 * repo.save(0, _, 'tony') >> { args -> sent = args[1]; 1 }
        sent.teamId() == team
        sent.kindId() == kind

        where:
        scenario        | teamId | kindId || team | kind
        'null'          | null   | null   || null | null
        'zero'          | 0      | 0      || null | null
        'negative'      | -1     | -9     || null | null
        'positive'      | 3      | 8      || 3    | 8
    }

    @Unroll
    def "save should pass followUp #expected when it is #scenario"() {

        given:
        EntryDraft sent = null

        when:
        service.save(0, new EntryDraft(null, 'W', null, null, null, null, followUp, null, null, null), 'tony')

        then:
        1 * repo.save(0, _, 'tony') >> { args -> sent = args[1]; 1 }
        sent.followUp() == expected
        sent.isFollowUp() == expected

        where:
        scenario    | followUp || expected
        'left out'  | null     || false
        'false'     | false    || false
        'true'      | true     || true
    }

    @Unroll
    def "save should pass the notes as #expected when they are #scenario"() {

        given:
        EntryDraft sent = null

        when:
        service.save(0, new EntryDraft(null, 'W', null, null, null, null, null, notes, null, null), 'tony')

        then:
        1 * repo.save(0, _, 'tony') >> { args -> sent = args[1]; 1 }
        sent.notes() == expected

        where:
        scenario    | notes            || expected
        'null'      | null             || null
        'blank'     | ' \n '           || null
        'padded'    | '  long text  '  || 'long text'
    }

    def "save should pass the work date through unchanged when one is given"() {

        given:
        LocalDateTime when = getLocalDateTime('11/25/2026 09:30')
        EntryDraft sent = null

        when:
        service.save(0, new EntryDraft(when, 'W', null, null, null, null, null, null, null, null), 'tony')

        then:
        1 * repo.save(0, _, 'tony') >> { args -> sent = args[1]; 1 }
        sent.workDate() == when
    }

    @Unroll
    def "save should save under id #expected when the id is #scenario"() {

        when:
        service.save(id, new EntryDraft(null, 'W', null, null, null, null, null, null, null, null), 'tony')

        then:
        1 * repo.save(expected, _, 'tony') >> 1

        where:
        scenario    | id || expected
        'zero'      | 0  || 0
        'negative'  | -5 || 0
        'positive'  | 9  || 9
    }

    // ---- save: contacts ------------------------------------------------------------------------

    @Unroll
    def "save should pass the contacts as #expected when they are #scenario"() {

        given:
        EntryDraft sent = null

        when:
        service.save(0, new EntryDraft(null, 'W', null, null, null, null, null, null, intsOf(ids), null), 'tony')

        then:
        1 * repo.save(0, _, 'tony') >> { args -> sent = args[1]; 1 }
        sent.contactIds() == intsOf(expected)

        where:
        scenario                               | ids                   || expected
        'null'                                 | null                  || ''
        'empty'                                | ''                    || ''
        'in the order picked'                  | '7,3,12'              || '7,3,12'
        'repeated, keeping the first place'    | '3,7,3,12,7'          || '3,7,12'
        'mixed with null, zero and negatives'  | 'null,5,0,-2,null,6'  || '5,6'
    }

    @Unroll
    def "save should #outcome when the entry names #count different contacts"() {

        given:
        List<Integer> ids = (1..count).toList()
        Exception err = null

        when:
        try { service.save(0, new EntryDraft(null, 'W', null, null, null, null, null, null, ids, null), 'tony') }
        catch (IllegalArgumentException ex) { err = ex }

        then:
        (saved ? 1 : 0) * repo.save(*_) >> 1
        saved ? err == null : err.message == 'An entry can name up to 50 contacts.'

        where:
        scenario  | count || saved | outcome
        'at max'  | 50    || true  | 'save'
        'over'    | 51    || false | 'refuse'
    }

    def "save should count contacts after repeats are dropped when the same contact is picked many times"() {

        given:
        List<Integer> ids = (1..50).toList() + (1..50).toList()

        when:
        service.save(0, new EntryDraft(null, 'W', null, null, null, null, null, null, ids, null), 'tony')

        then:
        1 * repo.save(0, { EntryDraft d -> d.contactIds().size() == 50 }, 'tony') >> 1
    }

    // ---- save: links ---------------------------------------------------------------------------

    @Unroll
    def "#seq. save should pass the links as #expected when they are #scenario"() {

        given:
        EntryDraft sent = null

        when:
        service.save(0, new EntryDraft(null, 'W', null, null, null, null, null, null, null, linesOf(links)), 'tony')

        then:
        1 * repo.save(0, _, 'tony') >> { args -> sent = args[1]; 1 }
        sent.links() == linesOf(expected)

        where:
        seq | scenario                         | links                                       || expected
        1   | 'null'                           | null                                        || ''
        2   | 'one link'                       | 'https://a.example/x'                       || 'https://a.example/x'
        3   | 'two items'                      | 'https://a.example|http://b.example'        || 'https://a.example|http://b.example'
        4   | 'one item holding two lines'     | 'https://a.example\nhttps://b.example'      || 'https://a.example|https://b.example'
        5   | 'Windows line ends'              | 'https://a.example\r\nhttps://b.example\r\n'|| 'https://a.example|https://b.example'
        6   | 'padded'                         | '   https://a.example   '                   || 'https://a.example'
        7   | 'blank lines and a null item'    | '\n  \nhttps://a.example\n\n|null|   '      || 'https://a.example'
        8   | 'upper case schemes'             | 'HTTPS://A.EXAMPLE|Http://b.example'        || 'HTTPS://A.EXAMPLE|Http://b.example'
        9   | 'all blank'                      | ' |\n|'                                     || ''
    }

    @Unroll
    def "save should refuse a link that does not start with http:// or https:// when it is #scenario"() {

        when:
        service.save(0, new EntryDraft(null, 'W', null, null, null, null, null, null, null, linesOf(links)), 'tony')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'A link should start with http:// or https://'
        0 * repo._

        where:
        scenario                     | links
        'a bare host'                | 'confluence.example/page'
        'ftp'                        | 'ftp://files.example'
        'javascript'                 | 'javascript:alert(1)'
        'a scheme with no slashes'   | 'https:a.example'
        'one bad among good'         | 'https://a.example\nwww.b.example'
    }

    @Unroll
    def "save should #outcome when the entry holds #count links"() {

        given:
        List<String> links = (1..count).collect { 'https://a.example/' + it }
        Exception err = null

        when:
        try { service.save(0, new EntryDraft(null, 'W', null, null, null, null, null, null, null, links), 'tony') }
        catch (IllegalArgumentException ex) { err = ex }

        then:
        (saved ? 1 : 0) * repo.save(*_) >> 1
        saved ? err == null : err.message == 'An entry can hold up to 50 links.'

        where:
        scenario  | count || saved | outcome
        'at max'  | 50    || true  | 'save'
        'over'    | 51    || false | 'refuse'
    }

    // ---- search --------------------------------------------------------------------------------

    def "search should refuse and never reach the repository when To is before From"() {

        given:
        Filter f = new Filter(getLocalDate('11/25/2026'), getLocalDate('11/24/2026'), null, null, null, false, 10)

        when:
        service.search(f)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'The To date is before the From date.'
        0 * repo._
    }

    @Unroll
    def "search should pass the dates through when #scenario"() {

        given:
        LocalDate from = getLocalDate(fromText)
        LocalDate to = getLocalDate(toText)
        List<Entry> found = []

        when:
        List<Entry> got = service.search(new Filter(from, to, null, null, null, false, 10))

        then:
        1 * repo.search(new Filter(from, to, null, null, null, false, 10)) >> found
        got.is(found)

        where:
        scenario                 | fromText     | toText
        'both are the same day'  | '11/25/2026' | '11/25/2026'
        'To is after From'       | '11/01/2026' | '11/25/2026'
        'only From is given'     | '11/01/2026' | null
        'only To is given'       | null         | '11/25/2026'
        'neither is given'       | null         | null
    }

    @Unroll
    def "#seq. search should pass top #expected when top is #scenario"() {

        when:
        service.search(new Filter(null, null, null, null, null, false, top))

        then:
        1 * repo.search({ Filter f -> f.top() == expected }) >> []

        where:
        seq | scenario          | top    || expected
        1   | 'zero'            | 0      || 500
        2   | 'negative'        | -1     || 500
        3   | 'one'             | 1      || 1
        4   | 'the default'     | 500    || 500
        5   | 'the ceiling'     | 2000   || 2000
        6   | 'one past it'     | 2001   || 2000
        7   | 'huge'            | 999999 || 2000
    }

    @Unroll
    def "search should pass the text as #expected when it is #scenario"() {

        given:
        String text = repeat == null ? value : getReplicatedString(value, repeat)
        String want = expectedRepeat == null ? expected : getReplicatedString(expected, expectedRepeat)

        when:
        service.search(new Filter(null, null, text, null, null, false, 10))

        then:
        1 * repo.search({ Filter f -> f.text() == want }) >> []

        where:
        scenario          | value       | repeat || expected    | expectedRepeat
        'null'            | null        | null   || null        | null
        'empty'           | ''          | null   || null        | null
        'blank'           | '   '       | null   || null        | null
        'padded'          | '  claims ' | null   || 'claims'    | null
        '200 characters'  | 'q'         | 200    || 'q'         | 200
        '201 characters'  | 'q'         | 201    || 'q'         | 200
    }

    @Unroll
    def "search should pass contact #contact and team #team and the follow-up flag when the ids are #scenario"() {

        when:
        service.search(new Filter(null, null, null, contactId, teamId, true, 10))

        then:
        1 * repo.search(new Filter(null, null, null, contact, team, true, 10)) >> []

        where:
        scenario      | contactId | teamId || contact | team
        'null'        | null      | null   || null    | null
        'zero'        | 0         | 0      || null    | null
        'negative'    | -4        | -1     || null    | null
        'positive'    | 12        | 3      || 12      | 3
    }

    // ---- get, setFollowUp, delete --------------------------------------------------------------

    @Unroll
    def "#what should answer empty or false and never reach the repository when the id is #scenario"() {

        when:
        Object got = callById(service, what, id)

        then:
        got == expected
        0 * repo._

        where:
        what            | scenario    | id || expected
        'get'           | 'zero'      | 0  || false
        'get'           | 'negative'  | -1 || false
        'setFollowUp'   | 'zero'      | 0  || false
        'setFollowUp'   | 'negative'  | -2 || false
        'delete'        | 'zero'      | 0  || false
        'delete'        | 'negative'  | -3 || false
    }

    def "get should pass through when the id is positive"() {

        given:
        Optional<Entry> found = Optional.empty()

        when:
        Optional<Entry> got = service.get(6)

        then:
        1 * repo.get(6) >> found
        got.is(found)
    }

    @Unroll
    def "setFollowUp should answer #answer when the repository answers #answer"() {

        when:
        boolean got = service.setFollowUp(6, true, 'tony')

        then:
        1 * repo.setFollowUp(6, true, 'tony') >> answer
        got == answer

        where:
        scenario    | answer
        'updated'   | true
        'gone'      | false
    }

    @Unroll
    def "delete should answer #answer when the repository answers #answer"() {

        when:
        boolean got = service.delete(6)

        then:
        1 * repo.delete(6) >> answer
        got == answer

        where:
        scenario    | answer
        'deleted'   | true
        'gone'      | false
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** "3,null,7" -> [3, null, 7]; "" -> []; null -> null. */
    private static List<Integer> intsOf(String csv) {
        if (csv == null) return null
        if (csv.isEmpty()) return []
        csv.split(',').collect { String s -> s == 'null' ? null : Integer.valueOf(s) }
    }

    /** "a|b|null" -> ['a', 'b', null] (each item may hold newlines); "" -> []; null -> null. */
    private static List<String> linesOf(String items) {
        if (items == null) return null
        if (items.isEmpty()) return []
        items.split('\\|', -1).collect { String s -> s == 'null' ? null : s }
    }

    private static Object callAdd(DailyWorkService s, String what, String name) {
        switch (what) {
            case 'addTeam': return s.addTeam(name)
            case 'addContact': return s.addContact(name, null)
            case 'addKind': return s.addKind(name)
            default: throw new IllegalArgumentException(what)
        }
    }

    private static Object callById(DailyWorkService s, String what, int id) {
        switch (what) {
            case 'get': return s.get(id).isPresent()
            case 'setFollowUp': return s.setFollowUp(id, true, 'tony')
            case 'delete': return s.delete(id)
            default: throw new IllegalArgumentException(what)
        }
    }
}
