package com.echo

import java.math.BigDecimal

import com.echo.SolutionLinkModels.Link
import com.echo.SolutionLinkModels.LinkTarget
import com.echo.SolutionLinkModels.Topic
import com.echo.SolutionLinkModels.TopicDraft
import com.echo.SolutionTopicKindModels.OpenItem
import com.echo.SolutionTopicKindModels.TopicType

import spock.lang.Unroll

/**
 * Story 5695 (Release 3): JdbcSolutionTopicKindRepository (stp_SolutionTopicType*, stp_SolutionTopic_Reorder,
 * stp_SolutionTopic_OpenItems) and the kind rules of stp_SolutionTopic_Save / _Get / _ListForSolution / _Counts
 * through JdbcSolutionLinkRepository, against a real EchoTest_ database -- kinds of part found or made (case and
 * spaces ignored), renamed (never onto a used name), labelled and removed (their parts stay, with no kind);
 * Arrange (order kept, a foreign id refused with nothing changed); the open items of a topic; what each kind may
 * carry, what an edit that changes the kind takes off, and a part named for a new kind of part making it in the
 * same save; the Topics tab's order, kind filter and kind counts; vw_SolutionLinkable's second line and closed
 * rule for topics; stp_SolutionLink_ListForRecord's TopicKind; stp_SolutionLinkTarget_Search's target kind; and
 * stp_Solution_Delete taking the kinds of part with it. Every feature is rolled back by EchoDbITSpec.
 */
class JdbcSolutionTopicKindRepositoryITSpec extends EchoDbITSpec {

    static final String ONLY_A_PART = 'Only a part carries a kind of part, a status and a number. Nothing was saved.'

    JdbcSolutionTopicKindRepository repo
    JdbcSolutionLinkRepository links
    String projectName
    int sid
    int project

    def setup() {
        repo = new JdbcSolutionTopicKindRepository(dataSource())
        links = new JdbcSolutionLinkRepository(dataSource())
        sid = newSolution()
        projectName = 'Deploy ' + uniq()
        project = newProject(sid, projectName)
    }

    // ---- fixtures ----

    /** A part of the solution, of the named kind of part (null = none). */
    private static int part(JdbcSolutionLinkRepository r, int solutionId, String name, String typeName, String status = null,
                            String figureText = null) {
        BigDecimal figure = figureText == null ? null : new BigDecimal(figureText)
        r.saveTopic(new TopicDraft(0, solutionId, name, null, 'part', typeName, status, figure, false, null), 'tony')
    }

    /** A topic of the named kind. */
    private static int kindOf(JdbcSolutionLinkRepository r, int solutionId, String name, String kind, String summary = null,
                              Boolean holds = null) {
        r.saveTopic(new TopicDraft(0, solutionId, name, summary, kind, null, null, null, false, holds), 'tony')
    }

    private static int newNote(EchoDbITSpec db, int solutionId, String title) {
        db.idOf("EXEC dbo.stp_SolutionNote_Save 0, ?, NULL, 'runbook', ?, 'Body', NULL, 'tony', 0", solutionId, title)
    }

    /** "12.5" to a BigDecimal; null stays null. */
    private static BigDecimal figureOf(String text) {
        text == null ? null : new BigDecimal(text)
    }

    // ---- kinds of part ----

    def "saveType should add a kind of part last in the solution with its name tidied and findType should read every field back"() {

        when:
        int a = repo.saveType(0, sid, "  Chapter\t", ' words ')
        int b = repo.saveType(0, sid, "Main\r\ncharacter", null)

        then:
        a > 0
        b > 0
        a != b
        repo.findType(a) == new TopicType(a, sid, 'Chapter', 'words', 1, 0)
        repo.findType(b) == new TopicType(b, sid, 'Main  character', null, 2, 0)
        scalar('SELECT NormalizedName FROM dbo.SolutionTopicType WHERE SolutionTopicTypeID = ?', a) == 'chapter'
        scalar('SELECT NormalizedName FROM dbo.SolutionTopicType WHERE SolutionTopicTypeID = ?', b) == 'main character'
        logRows('SolutionTopicType', 'SolutionTopicTypeID', a)*.IsInsertion == [true]
    }

    def "saveType should find the kind by name, ignoring case and repeated spaces, rather than add a second"() {

        given:
        int a = repo.saveType(0, sid, 'Main  Character', null)

        expect:
        repo.saveType(0, sid, ' main character ', null) == a
        repo.saveType(0, sid, 'MAIN    CHARACTER', null) == a
        count('SELECT COUNT(*) FROM dbo.SolutionTopicType WHERE SolutionID = ?', sid) == 1
        repo.findType(a).name() == 'Main  Character'
        repo.saveType(0, newSolution(), 'main character', null) != a
    }

    def "saveType should set, keep and clear the label of the kind it finds when a find-or-create sends one"() {

        given:
        int a = repo.saveType(0, sid, 'Chapter', 'words')

        when:
        int found = repo.saveType(0, sid, 'chapter', 'pages')

        then:
        found == a
        repo.findType(a).figureLabel() == 'pages'

        when:
        repo.saveType(0, sid, 'CHAPTER', null)

        then:
        repo.findType(a).figureLabel() == 'pages'

        when:
        repo.saveType(0, sid, 'Chapter', '')

        then:
        repo.findType(a).figureLabel() == null
    }

    def "saveType should rename the kind, keep or clear its label and stay in its own solution when it is edited"() {

        given:
        int a = repo.saveType(0, sid, 'Chapter', 'words')
        int p = part(links, sid, 'Opening', 'Chapter')

        when:
        int saved = repo.saveType(a, 0, ' Scene ', null)

        then:
        saved == a
        repo.findType(a).name() == 'Scene'
        repo.findType(a).figureLabel() == 'words'
        scalar('SELECT NormalizedName FROM dbo.SolutionTopicType WHERE SolutionTopicTypeID = ?', a) == 'scene'
        links.findTopic(p).typeName() == 'Scene'

        when:
        repo.saveType(a, 0, null, '')

        then:
        repo.findType(a).name() == 'Scene'
        repo.findType(a).figureLabel() == null

        when:
        repo.saveType(a, newSolution(), null, 'pages')

        then:
        repo.findType(a).solutionId() == sid
        repo.findType(a).figureLabel() == 'pages'
    }

    def "saveType should refuse a rename onto a name the solution already uses but let a kind keep its own name in another case"() {

        given:
        int a = repo.saveType(0, sid, 'Chapter', null)
        int b = repo.saveType(0, sid, 'Character', null)

        expect:
        ruleOf { repo.saveType(b, 0, ' CHAPTER ', null) }.message == 'This solution already has that kind of part. Nothing was saved.'
        repo.findType(b).name() == 'Character'
        repo.saveType(a, 0, 'CHAPTER', null) == a
        repo.findType(a).name() == 'CHAPTER'
        repo.saveType(0, newSolution(), 'Chapter', null) > 0
    }

    @Unroll
    def "#seq. saveType should refuse and write nothing when #scenario"() {

        given:
        int existing = repo.saveType(0, sid, 'Existing', 'words')
        int before = count('SELECT COUNT(*) FROM dbo.SolutionTopicType')
        int id = target == 'existing' ? existing : (target == 'missing' ? 987654321 : 0)
        int solution = target == 'missing-solution' ? -1 : sid
        String nameText = nameLength > 0 ? getReplicatedString('n', nameLength) : name
        String labelText = labelLength > 0 ? getReplicatedString('l', labelLength) : null

        when:
        repo.saveType(id, solution, nameText, labelText)

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        count('SELECT COUNT(*) FROM dbo.SolutionTopicType') == before
        repo.findType(existing).name() == 'Existing'
        repo.findType(existing).figureLabel() == 'words'

        where:
        seq | scenario                               | target             | name      | nameLength | labelLength || expectedMessage
        1   | 'the kind to edit no longer exists'    | 'missing'          | 'x'       | 0          | 0           || 'That kind of part no longer exists. Nothing was saved.'
        2   | 'the solution does not exist'          | 'missing-solution' | 'x'       | 0          | 0           || 'That solution no longer exists. Nothing was added.'
        3   | 'a new name is blank'                  | 'new'              | '  '      | 0          | 0           || 'A kind of part needs a name. Nothing was added.'
        4   | 'a new name is missing'                | 'new'              | null      | 0          | 0           || 'A kind of part needs a name. Nothing was added.'
        5   | 'an edit blanks the name'              | 'existing'         | ' '       | 0          | 0           || 'A kind of part cannot be blank. Nothing was saved.'
        6   | 'the name is over 100 characters'      | 'new'              | null      | 101        | 0           || 'A kind of part can be at most 100 characters. Nothing was saved.'
        7   | 'an edit sends a label over 40'        | 'existing'         | null      | 0          | 41          || 'What a number counts can be at most 40 characters. Nothing was saved.'
        8   | 'a new kind sends a label over 40'     | 'new'              | 'Chapter' | 0          | 41          || 'What a number counts can be at most 40 characters. Nothing was saved.'
    }

    def "saveType should take a name of exactly 100 characters and a label of exactly 40"() {

        when:
        int id = repo.saveType(0, sid, getReplicatedString('n', 100), getReplicatedString('l', 40))

        then:
        repo.findType(id).name().length() == 100
        repo.findType(id).figureLabel().length() == 40
    }

    def "types should list the solution's kinds in their order with how many parts each has"() {

        given:
        part(links, sid, 'Opening', 'Chapter')
        part(links, sid, 'Closing', 'Chapter')
        part(links, sid, 'Bob', 'Character')
        int empty = repo.saveType(0, sid, 'Account', 'dollars')
        repo.saveType(0, newSolution(), 'Elsewhere', null)

        when:
        List<TopicType> got = repo.types(sid)

        then:
        got*.name() == ['Chapter', 'Character', 'Account']
        got*.sortOrder() == [1, 2, 3]
        got*.partCount() == [2, 1, 0]
        got*.solutionId() == [sid, sid, sid]
        got[2] == new TopicType(empty, sid, 'Account', 'dollars', 3, 0)
        got[0] == repo.findType(got[0].id())
        repo.types(newSolution()).isEmpty()
    }

    def "findType should answer null when the id is unknown"() {

        expect:
        repo.findType(-1) == null
        repo.findType(987654321) == null
    }

    def "deleteType should remove the kind and leave its parts with no kind, answering false the second time"() {

        given:
        int p = part(links, sid, 'Opening', 'Chapter', 'inprogress')
        int a = repo.types(sid)[0].id()

        expect:
        repo.deleteType(a)
        repo.findType(a) == null
        links.findTopic(p).kindCode() == 'part'
        links.findTopic(p).typeId() == null
        links.findTopic(p).typeName() == null
        links.findTopic(p).statusCode() == 'inprogress'
        logRows('SolutionTopicType', 'SolutionTopicTypeID', a).last().DeletedDate != null
        !repo.deleteType(a)
    }

    // ---- Arrange ----

    def "reorder should give the named topics SortOrder 1, 2, 3 in the order given and the list should follow it"() {

        given:
        int c1 = part(links, sid, 'One', 'Chapter')
        int c2 = part(links, sid, 'Two', 'Chapter')
        int c3 = part(links, sid, 'Three', 'Chapter')

        when:
        repo.reorder(sid, [c3, c1, c2])

        then:
        [c3, c1, c2].collect { int id -> links.findTopic(id).sortOrder() } == [1, 2, 3]
        links.topics(sid, null, 'part', 0, 50)*.id() == [c3, c1, c2]
    }

    def "reorder should refuse an order naming a topic of another solution, an unknown one or none, and change nothing"() {

        given:
        int c1 = part(links, sid, 'One', 'Chapter')
        int c2 = part(links, sid, 'Two', 'Chapter')
        int foreign = kindOf(links, newSolution(), 'Theirs', 'subject')

        expect:
        ruleOf { repo.reorder(sid, [c2, foreign]) }.message == 'A topic in that order is not in this solution. Nothing was changed.'
        ruleOf { repo.reorder(sid, [c2, 987654321]) }.message == 'A topic in that order is not in this solution. Nothing was changed.'
        ruleOf { repo.reorder(sid, []) }.message == 'Name the topics in their new order. Nothing was changed.'
        links.findTopic(c1).sortOrder() == 1
        links.findTopic(c2).sortOrder() == 2
        links.findTopic(foreign).sortOrder() == 0

        and: 'the connection still works after the refusals'
        repo.reorder(sid, [c2, c1])
        links.findTopic(c2).sortOrder() == 1
    }

    // ---- open items ----

    def "openItems should answer only the items linked to the topic that are still open"() {

        given:
        int a = kindOf(links, sid, 'The 837 path is live', 'assumption')
        int open = newItem(project, 'Submit 837')
        int started = newItem(project, 'Watch 999', null, 'inprogress')
        int child = newItem(project, 'Child', open, 'blocked')
        int done = newItem(project, 'Old work', null, 'done')
        int skipped = newItem(project, 'Dropped work', null, 'cancelled')
        newItem(project, 'Not linked')
        int note = newNote(this, sid, 'Runbook')
        links.link(sid, 'topic', a, 'item', open, 'tony')
        links.link(sid, 'item', started, 'topic', a, 'tony')
        [child, done, skipped].each { int i -> links.link(sid, 'topic', a, 'item', i, 'tony') }
        links.link(sid, 'topic', a, 'note', note, 'tony')

        when:
        List<OpenItem> got = repo.openItems(a)

        then:
        got*.id() as Set == [open, started, child] as Set
        got.find { OpenItem i -> i.id() == open }.title() == 'Submit 837'
        got.find { OpenItem i -> i.id() == open }.sub() == projectName + ' | Not started'
        got.find { OpenItem i -> i.id() == started }.sub() == projectName + ' | In progress'
        got.find { OpenItem i -> i.id() == child }.sub() == projectName + ' | Blocked'
    }

    def "openItems should answer nothing for a topic with no linked items or that does not exist"() {

        given:
        int lonely = kindOf(links, sid, 'Lonely', 'assumption')
        links.link(sid, 'topic', lonely, 'note', newNote(this, sid, 'Runbook'), 'tony')

        expect:
        repo.openItems(lonely).isEmpty()
        repo.openItems(987654321).isEmpty()
    }

    // ---- what each kind carries (stp_SolutionTopic_Save) ----

    def "saveTopic should store a part with its kind of part, status and number, making the kind of part in the same save"() {

        when:
        int id = part(links, sid, 'Cherry Creek', 'Chapter', 'inprogress', '4200.50')
        Topic t = links.findTopic(id)

        then:
        t.kindCode() == 'part'
        t.typeId() != null
        t.typeName() == 'Chapter'
        t.statusCode() == 'inprogress'
        t.statusName() == 'In progress'
        t.colorHex() == '#2563EB'
        !t.closed()
        t.figure() == new BigDecimal('4200.5')
        t.holds() == null
        t.sortOrder() == 1
        repo.types(sid)*.name() == ['Chapter']
        repo.types(sid)[0].id() == t.typeId()
        repo.types(sid)[0].partCount() == 1
    }

    def "saveTopic should make no kind of part when the save that names it is refused"() {

        given:
        kindOf(links, sid, 'Opening', 'subject')

        expect:
        ruleOf { part(links, sid, 'Brand new', 'Brand new kind', 'nosuch') }.message == 'That is not a status. Nothing was saved.'
        ruleOf { part(links, sid, ' opening ', 'Scene') }.message == 'This solution already has the topic "Opening". Nothing was saved.'
        repo.types(sid).isEmpty()
        count('SELECT COUNT(*) FROM dbo.SolutionTopic WHERE SolutionID = ?', sid) == 1
    }

    def "saveTopic should put a new part last in its own kind of part"() {

        given:
        int c1 = part(links, sid, 'One', 'Chapter')
        int c2 = part(links, sid, 'Two', 'chapter')
        int x = part(links, sid, 'Bob', 'Character')
        int n1 = part(links, sid, 'Loose', null)
        int n2 = part(links, sid, 'Looser', null)
        int s = kindOf(links, sid, 'Charset', 'subject')

        expect:
        [c1, c2, x, n1, n2, s].collect { int id -> links.findTopic(id).sortOrder() } == [1, 2, 1, 1, 2, 0]
        links.findTopic(c2).typeId() == links.findTopic(c1).typeId()

        when:
        repo.reorder(sid, [c2, c1])
        int c3 = part(links, sid, 'Three', 'Chapter')

        then:
        links.findTopic(c3).sortOrder() == 3
    }

    def "saveTopic should make a new topic a subject, an assumption hold by default and read the kind tidied"() {

        when:
        Topic s = links.findTopic(links.saveTopic(new TopicDraft(0, sid, 'Charset', null), 'tony'))
        Topic a = links.findTopic(kindOf(links, sid, 'The 837 path is live', 'assumption'))
        Topic b = links.findTopic(kindOf(links, sid, 'The driver stays', 'assumption', null, false))
        Topic g = links.findTopic(links.saveTopic(new TopicDraft(0, sid, 'Risk', null, ' TAG ', null, null, null, false, null), 'tony'))

        then:
        s.kindCode() == 'subject'
        s.holds() == null
        s.typeId() == null
        s.statusCode() == null
        s.figure() == null
        s.sortOrder() == 0
        !s.closed()
        scalar('SELECT TopicKindCode FROM dbo.SolutionTopic WHERE SolutionTopicID = ?', s.id()) == 'subject'
        a.kindCode() == 'assumption'
        a.holds() == true
        b.holds() == false
        g.kindCode() == 'tag'
        g.holds() == null
    }

    @Unroll
    def "#seq. saveTopic should refuse and write nothing when #scenario"() {

        given:
        int topicsBefore = count('SELECT COUNT(*) FROM dbo.SolutionTopic WHERE SolutionID = ?', sid)
        int typesBefore = count('SELECT COUNT(*) FROM dbo.SolutionTopicType WHERE SolutionID = ?', sid)
        BigDecimal figure = figureOf(figureText)

        when:
        links.saveTopic(new TopicDraft(0, sid, 'Refused ' + uniq(), null, kind, typeName, status, figure, false, holds), 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        count('SELECT COUNT(*) FROM dbo.SolutionTopic WHERE SolutionID = ?', sid) == topicsBefore
        count('SELECT COUNT(*) FROM dbo.SolutionTopicType WHERE SolutionID = ?', sid) == typesBefore

        where:
        seq | scenario                            | kind         | typeName  | status     | figureText | holds || expectedMessage
        1   | 'a subject names a kind of part'    | 'subject'    | 'Chapter' | null       | null       | null  || 'Only a part carries a kind of part, a status and a number. Nothing was saved.'
        2   | 'a subject has a status'            | 'subject'    | null      | 'todo'     | null       | null  || 'Only a part carries a kind of part, a status and a number. Nothing was saved.'
        3   | 'a tag has a number'                | 'tag'        | null      | null       | '5'        | null  || 'Only a part carries a kind of part, a status and a number. Nothing was saved.'
        4   | 'an assumption has a status'        | 'assumption' | null      | 'done'     | null       | null  || 'Only a part carries a kind of part, a status and a number. Nothing was saved.'
        5   | 'no kind is named and it has a status' | null      | null      | 'todo'     | null       | null  || 'Only a part carries a kind of part, a status and a number. Nothing was saved.'
        6   | 'a subject holds'                   | 'subject'    | null      | null       | null       | true  || 'Only an assumption holds or is proved false. Nothing was saved.'
        7   | 'a part is proved false'            | 'part'       | null      | null       | null       | false || 'Only an assumption holds or is proved false. Nothing was saved.'
        8   | 'a tag holds'                       | 'tag'        | null      | null       | null       | true  || 'Only an assumption holds or is proved false. Nothing was saved.'
        9   | 'the kind is unknown'               | 'chapter'    | null      | null       | null       | null  || 'A topic is a subject, a tag, a part or an assumption. Nothing was saved.'
        10  | 'a part has an unknown status'      | 'part'       | 'Chapter' | 'finished' | null       | null  || 'That is not a status. Nothing was saved.'
    }

    def "saveTopic should refuse a kind of part over 100 characters and make nothing"() {

        expect:
        ruleOf { part(links, sid, 'Long', getReplicatedString('k', 101)) }.message == 'A kind of part can be at most 100 characters. Nothing was saved.'
        repo.types(sid).isEmpty()
    }

    def "saveTopic should refuse an edit that gives a subject a status and leave the topic as it was"() {

        given:
        int s = kindOf(links, sid, 'Charset', 'subject')

        expect:
        ruleOf { links.saveTopic(new TopicDraft(s, 0, null, null, null, null, 'done', null, false, null), 'tony') }.message == ONLY_A_PART
        links.findTopic(s).kindCode() == 'subject'
        links.findTopic(s).statusCode() == null
    }

    def "saveTopic should take off what the old kind carried when an edit changes the kind"() {

        given:
        int p = part(links, sid, 'Cherry Creek', 'Chapter', 'inprogress', '4200')
        int a = kindOf(links, sid, 'The 837 path is live', 'assumption', null, false)
        int s = kindOf(links, sid, 'Charset', 'subject')
        int g = kindOf(links, sid, 'Risk', 'tag')

        when:
        links.saveTopic(new TopicDraft(p, 0, null, null, 'subject', null, null, null, false, null), 'tony')
        links.saveTopic(new TopicDraft(a, 0, null, null, 'tag', null, null, null, false, null), 'tony')
        links.saveTopic(new TopicDraft(s, 0, null, null, 'assumption', null, null, null, false, null), 'tony')
        links.saveTopic(new TopicDraft(g, 0, null, null, 'part', 'Chapter', 'todo', null, false, null), 'tony')
        Topic wasPart = links.findTopic(p)
        Topic wasAssumption = links.findTopic(a)
        Topic wasSubject = links.findTopic(s)
        Topic wasTag = links.findTopic(g)

        then:
        wasPart.kindCode() == 'subject'
        wasPart.typeId() == null
        wasPart.typeName() == null
        wasPart.statusCode() == null
        wasPart.figure() == null
        wasPart.holds() == null
        wasAssumption.kindCode() == 'tag'
        wasAssumption.holds() == null
        scalar('SELECT AssumptionHolds FROM dbo.SolutionTopic WHERE SolutionTopicID = ?', a) == null
        wasSubject.kindCode() == 'assumption'
        wasSubject.holds() == true
        wasTag.kindCode() == 'part'
        wasTag.typeName() == 'Chapter'
        wasTag.statusCode() == 'todo'
        repo.types(sid)*.name() == ['Chapter']
    }

    def "saveTopic should keep what a part carries when an edit sends nulls and change only what is sent"() {

        given:
        int p = part(links, sid, 'Cherry Creek', 'Chapter', 'todo', '10')

        when:
        links.saveTopic(new TopicDraft(p, 0, 'Cherry Creek Road', null), 'tony')
        Topic t = links.findTopic(p)

        then:
        t.name() == 'Cherry Creek Road'
        t.kindCode() == 'part'
        t.typeName() == 'Chapter'
        t.statusCode() == 'todo'
        t.figure() == new BigDecimal('10')

        when:
        links.saveTopic(new TopicDraft(p, 0, null, null, null, null, 'done', null, false, null), 'tony')
        t = links.findTopic(p)

        then:
        t.statusCode() == 'done'
        t.closed()
        t.figure() == new BigDecimal('10')

        when:
        links.saveTopic(new TopicDraft(p, 0, null, null, null, null, null, new BigDecimal('12.5'), false, null), 'tony')

        then:
        links.findTopic(p).figure() == new BigDecimal('12.5')
        links.findTopic(p).statusCode() == 'done'

        when:
        links.saveTopic(new TopicDraft(p, 0, null, null, null, null, null, null, true, null), 'tony')

        then:
        links.findTopic(p).figure() == null

        when:
        links.saveTopic(new TopicDraft(p, 0, null, null, null, '', '', null, false, null), 'tony')
        t = links.findTopic(p)

        then:
        t.kindCode() == 'part'
        t.typeId() == null
        t.typeName() == null
        t.statusCode() == null
        !t.closed()
    }

    def "saveTopic should keep an assumption's holds when an edit sends none and set it when one is sent"() {

        given:
        int a = kindOf(links, sid, 'The 837 path is live', 'assumption')

        when:
        links.saveTopic(new TopicDraft(a, 0, null, null, null, null, null, null, false, false), 'tony')

        then:
        links.findTopic(a).holds() == false

        when:
        links.saveTopic(new TopicDraft(a, 0, 'The 837 path is live again', null), 'tony')

        then:
        links.findTopic(a).holds() == false

        when:
        links.saveTopic(new TopicDraft(a, 0, null, null, null, null, null, null, false, true), 'tony')

        then:
        links.findTopic(a).holds() == true
    }

    // ---- the Topics tab: order, kind filter, kind counts ----

    def "topics should list the parts by kind-of-part order then their own order, then assumptions, subjects and tags by name"() {

        given:
        int c1 = part(links, sid, 'Zeta chapter', 'Chapter')
        int c2 = part(links, sid, 'Alpha chapter', 'Chapter')
        int ch = part(links, sid, 'Bob', 'Character')
        int loose = part(links, sid, 'Loose part', null)
        int aB = kindOf(links, sid, 'B assumption', 'assumption')
        int aA = kindOf(links, sid, 'A assumption', 'assumption')
        int sZ = kindOf(links, sid, 'Subject Z', 'subject')
        int sA = kindOf(links, sid, 'Subject A', 'subject')
        int tR = kindOf(links, sid, 'Risk', 'tag')
        int tI = kindOf(links, sid, 'Important', 'tag')

        expect:
        links.topics(sid, null, null, 0, 50)*.id() == [c1, c2, ch, loose, aA, aB, sA, sZ, tI, tR]
        links.topics(sid, null, null, 2, 3)*.id() == [ch, loose, aA]
        links.topics(sid, null, null, 0, 50)[0].typeName() == 'Chapter'

        when:
        repo.reorder(sid, [c2, c1])

        then:
        links.topics(sid, null, null, 0, 50)*.id() == [c2, c1, ch, loose, aA, aB, sA, sZ, tI, tR]
    }

    def "topics and topicCounts should narrow to one kind and count each kind under the search alone"() {

        given:
        int p1 = part(links, sid, 'Opening', 'Chapter')
        int p2 = part(links, sid, 'Closing', 'Chapter')
        int a1 = kindOf(links, sid, 'Driver stays pinned', 'assumption')
        int s1 = kindOf(links, sid, 'Charset', 'subject')
        int s2 = kindOf(links, sid, 'Other', 'subject', 'All about the charset')
        int g1 = kindOf(links, sid, 'Charset risk', 'tag')

        expect:
        links.topics(sid, null, 'part', 0, 50)*.id() == [p1, p2]
        links.topics(sid, null, ' TAG ', 0, 50)*.id() == [g1]
        links.topics(sid, null, 'assumption', 0, 50)*.id() == [a1]
        links.topics(sid, 'charset', 'subject', 0, 50)*.id() == [s1, s2]
        links.topics(sid, 'chapter', null, 0, 50)*.id() == [p1, p2]
        links.topicCounts(sid, null, null) == [total: 6, 'kind:part': 2, 'kind:assumption': 1, 'kind:subject': 2, 'kind:tag': 1, all: 6]
        links.topicCounts(sid, null, 'part') == [total: 2, 'kind:part': 2, 'kind:assumption': 1, 'kind:subject': 2, 'kind:tag': 1, all: 6]
        links.topicCounts(sid, 'charset', 'tag') == [total: 1, 'kind:subject': 2, 'kind:tag': 1, all: 6]
        links.topicCounts(sid, 'chapter', null) == [total: 2, 'kind:part': 2, all: 6]
        links.topicCounts(newSolution(), null, 'part') == [total: 0, all: 0]
    }

    // ---- vw_SolutionLinkable, stp_SolutionLink_ListForRecord, stp_SolutionLinkTarget_Search ----

    @Unroll
    def "#seq. vw_SolutionLinkable should give the topic the second line '#expectedSub' and closed #expectedClosed when it is #scenario"() {

        given:
        int id = links.saveTopic(new TopicDraft(0, sid, 'T ' + uniq(), summary, kind, typeName, status, null, false, holds), 'tony')

        when:
        Map<String, Object> v = row("SELECT Sub, IsClosed FROM dbo.vw_SolutionLinkable WHERE SolutionID = ? AND Kind = 'topic' AND RecordID = ?", sid, id)

        then:
        v.Sub == expectedSub
        v.IsClosed == expectedClosed

        where:
        seq | scenario                          | kind         | typeName  | status       | holds | summary   || expectedSub                         | expectedClosed
        1   | 'a subject with a summary'        | 'subject'    | null      | null         | null  | 'About it' || 'Topic | About it'                  | false
        2   | 'a subject with no summary'       | 'subject'    | null      | null         | null  | null      || 'Topic'                             | false
        3   | 'a tag'                           | 'tag'        | null      | null         | null  | null      || 'Tag'                               | false
        4   | 'a part of an open status'        | 'part'       | 'Chapter' | 'inprogress' | null  | null      || 'Chapter'                           | false
        5   | 'a part whose status is done'     | 'part'       | 'Chapter' | 'done'       | null  | null      || 'Chapter'                           | true
        6   | 'a part whose status is skipped'  | 'part'       | 'Chapter' | 'cancelled'  | null  | null      || 'Chapter'                           | true
        7   | 'a part with no kind of part'     | 'part'       | null      | null         | null  | null      || 'Part'                              | false
        8   | 'a part with a summary'           | 'part'       | 'Account' | 'blocked'    | null  | 'Card 1'  || 'Account | Card 1'                  | false
        9   | 'an assumption that holds'        | 'assumption' | null      | null         | true  | null      || 'Assumption'                        | false
        10  | 'an assumption proved false'      | 'assumption' | null      | null         | false | 'It died' || 'Assumption, proved false | It died' | true
    }

    def "stp_SolutionLink_ListForRecord should say the kind of each linked topic and nothing for other records"() {

        given:
        int item = newItem(project, 'Upgrade the driver')
        int g = kindOf(links, sid, 'Risk', 'tag')
        int p = part(links, sid, 'Opening', 'Chapter')
        int s = kindOf(links, sid, 'Charset', 'subject')
        int a = kindOf(links, sid, 'Driver stays pinned', 'assumption', null, false)
        int note = newNote(this, sid, 'Runbook')
        [g, p, s, a].each { int t -> links.link(sid, 'item', item, 'topic', t, 'tony') }
        links.link(sid, 'item', item, 'note', note, 'tony')

        when:
        List<Link> got = links.links(sid, 'item', item, 0, 50).links()
        List<Link> topics = got.findAll { Link l -> l.kind() == 'topic' }

        then:
        topics.collectEntries { Link l -> [(l.id()): l.topicKind()] } == [(g): 'tag', (p): 'part', (s): 'subject', (a): 'assumption']
        got.find { Link l -> l.kind() == 'note' }.topicKind() == null
        topics.find { Link l -> l.id() == a }.closed()
        !topics.find { Link l -> l.id() == s }.closed()
        topics.find { Link l -> l.id() == p }.sub() == 'Chapter'
        links.links(sid, 'topic', g, 0, 50).links()*.topicKind() == [null]
    }

    def "stp_SolutionLinkTarget_Search should offer only one kind of record when a target kind is asked"() {

        given:
        int i1 = newItem(project, 'Wait A')
        int i2 = newItem(project, 'Wait B')
        newNote(this, sid, 'Wait note')
        int t = kindOf(links, sid, 'Wait topic', 'subject')

        expect:
        links.targets(sid, null, null, null, 30, 'item')*.id() as Set == [i1, i2] as Set
        links.targets(sid, null, null, null, 30, 'item').every { LinkTarget x -> x.kind() == 'item' }
        links.targets(sid, 'wait', 'item', i1, 30, 'item')*.id() == [i2]
        links.targets(sid, null, null, null, 30, 'ITEM')*.id() as Set == [i1, i2] as Set
        links.targets(sid, null, null, null, 30, 'topic')*.id() == [t]
        links.targets(sid, null, null, null, 30, 'question').isEmpty()
        links.targets(sid, null, null, null, 30, '').size() == 4
        links.targets(sid, null, null, null, 30, null).size() == 4
    }

    // ---- stp_Solution_Delete ----

    def "stp_Solution_Delete should remove the solution's kinds of part and leave another solution's"() {

        given:
        part(links, sid, 'Opening', 'Chapter')
        int a = repo.types(sid)[0].id()
        int other = newSolution()
        int theirs = repo.saveType(0, other, 'Chapter', null)

        when:
        row('EXEC dbo.stp_Solution_Delete ?', sid)

        then:
        repo.types(sid).isEmpty()
        repo.findType(a) == null
        repo.findType(theirs) != null
        logRows('SolutionTopicType', 'SolutionTopicTypeID', a).last().DeletedDate != null
    }
}
