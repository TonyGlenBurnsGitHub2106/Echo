package com.echo

import java.time.LocalDateTime

import com.echo.SolutionLinkModels.Change
import com.echo.SolutionLinkModels.Link
import com.echo.SolutionLinkModels.LinkPage
import com.echo.SolutionLinkModels.LinkTarget
import com.echo.SolutionLinkModels.StoredLink
import com.echo.SolutionLinkModels.Topic
import com.echo.SolutionLinkModels.TopicDraft
import com.echo.SolutionLinkModels.Visit

import spock.lang.Unroll

/**
 * Story 5695: JdbcSolutionLinkRepository against a real EchoTest_ database (stp_SolutionLink*,
 * stp_SolutionLinkTarget_Search, stp_SolutionTopic*, stp_SolutionMemberVisit*, stp_SolutionActivity*) --
 * a pair stored once in fixed order, the procs' refusals, paging and counts, the Link picker, topics,
 * visits and "since", and that the delete procs of every linkable record (and of a solution) take that
 * record's links with them. Every feature is rolled back by EchoDbITSpec.
 */
class JdbcSolutionLinkRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionLinkRepository repo
    int sid
    int project

    def setup() {
        repo = new JdbcSolutionLinkRepository(dataSource())
        sid = newSolution()
        project = newProject(sid)
    }

    // ---- fixtures ----

    private static int newNote(EchoDbITSpec db, int solutionId, String title) {
        db.idOf("EXEC dbo.stp_SolutionNote_Save 0, ?, NULL, 'runbook', ?, 'Body', NULL, 'tony', 0", solutionId, title)
    }

    private static int newTerm(EchoDbITSpec db, int solutionId, String term, String memberCode) {
        db.idOf("EXEC dbo.stp_SolutionTerm_Save 0, ?, ?, 'Means something', NULL, NULL, NULL, NULL, ?", solutionId, term, memberCode)
    }

    private static int newTopic(JdbcSolutionLinkRepository repo, int solutionId, String name, String summary = null, String memberCode = 'rex') {
        repo.saveTopic(new TopicDraft(0, solutionId, name, summary), memberCode)
    }

    /** One record of the named kind in the solution (the project is the solution's own, for an item). */
    private static int newRecord(EchoDbITSpec db, JdbcSolutionLinkRepository repo, String kind, int solutionId, int projectId, String title) {
        switch (kind) {
            case 'item':  return db.newItem(projectId, title)
            case 'note':  return newNote(db, solutionId, title)
            case 'log':   return db.newLog(solutionId, title)
            case 'file':  return db.newFile(solutionId, title + '.txt', title)
            case 'term':  return newTerm(db, solutionId, title, 'tony')
            case 'topic': return newTopic(repo, solutionId, title)
            default: throw new IllegalStateException(kind)
        }
    }

    /** How many links touch one record, from either end. */
    private static int linksOf(EchoDbITSpec db, String kind, int id) {
        db.count('SELECT COUNT(*) FROM dbo.SolutionLink WHERE (FromKind = ? AND FromID = ?) OR (ToKind = ? AND ToID = ?)', kind, id, kind, id)
    }

    /** A member code no other feature uses, at most 20 characters. */
    private static String member() {
        ('it' + uniq()).take(20)
    }

    // ---- links ----

    def "link should store the pair lower kind first and findLink should read it back when the higher kind links to the lower"() {

        given:
        int item = newItem(project)
        int note = newNote(this, sid, 'Runbook')

        when:
        int id = repo.link(sid, 'note', note, 'item', item, 'tony')
        StoredLink s = repo.findLink(id)

        then:
        id > 0
        s == new StoredLink(id, sid, 'item', item, 'note', note)
        count('SELECT COUNT(*) FROM dbo.SolutionLink WHERE SolutionID = ?', sid) == 1
    }

    def "link should store the lower id first when two records of one kind are linked"() {

        given:
        int first = newItem(project)
        int second = newItem(project)

        when:
        int id = repo.link(sid, 'item', second, 'item', first, 'tony')

        then:
        repo.findLink(id) == new StoredLink(id, sid, 'item', first, 'item', second)
    }

    def "link should lower and trim the kinds when they arrive padded and in capitals"() {

        given:
        int item = newItem(project)
        int term = newTerm(this, sid, 'EOB', 'tony')

        when:
        int id = repo.link(sid, ' TERM ', term, 'Item', item, 'tony')

        then:
        repo.findLink(id) == new StoredLink(id, sid, 'item', item, 'term', term)
    }

    def "link should refuse the pair the other way round and the same way again with the proc's sentence"() {

        given:
        int item = newItem(project)
        int note = newNote(this, sid, 'Runbook')
        repo.link(sid, 'item', item, 'note', note, 'tony')

        expect:
        ruleOf { repo.link(sid, 'note', note, 'item', item, 'tony') }.message == 'Those two are already linked. Nothing was saved.'
        ruleOf { repo.link(sid, 'item', item, 'note', note, 'rex') }.message == 'Those two are already linked. Nothing was saved.'
        count('SELECT COUNT(*) FROM dbo.SolutionLink WHERE SolutionID = ?', sid) == 1
    }

    @Unroll
    def "#seq. link should refuse and write nothing when #scenario"() {

        given:
        int other = newSolution()
        int otherProject = newProject(other)
        int mine = newItem(project)
        int foreign = newItem(otherProject)
        int fromId = fromTarget == 'mine' ? mine : (fromTarget == 'other' ? foreign : 987654321)
        int toId
        if (toTarget == 'self') {
            toId = mine
        } else if (toTarget == 'mine') {
            toId = newRecord(this, repo, toKind, sid, project, 'Mine ' + uniq())
        } else if (toTarget == 'other') {
            toId = newRecord(this, repo, toKind, other, otherProject, 'Theirs ' + uniq())
        } else {
            toId = 987654321
        }
        int solutionId = solutionTarget == 'mine' ? sid : -1
        int before = count('SELECT COUNT(*) FROM dbo.SolutionLink')

        when:
        repo.link(solutionId, fromKind, fromId, toKind, toId, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        count('SELECT COUNT(*) FROM dbo.SolutionLink') == before

        where:
        seq | scenario                                       | solutionTarget | fromKind | fromTarget | toKind  | toTarget  || expectedMessage
        1   | 'a record is linked to itself'                 | 'mine'         | 'item'   | 'mine'     | 'item'  | 'self'    || 'A record cannot be linked to itself. Nothing was saved.'
        2   | 'the record to link to is in another solution' | 'mine'         | 'item'   | 'mine'     | 'note'  | 'other'   || 'The record to link to is not in this solution. Nothing was saved.'
        3   | 'a topic of another solution is named'         | 'mine'         | 'item'   | 'mine'     | 'topic' | 'other'   || 'The record to link to is not in this solution. Nothing was saved.'
        4   | 'the record to link to does not exist'         | 'mine'         | 'item'   | 'mine'     | 'term'  | 'missing' || 'The record to link to is not in this solution. Nothing was saved.'
        5   | 'the record to link from is in another solution' | 'mine'       | 'item'   | 'other'    | 'log'   | 'mine'    || 'The record to link from is not in this solution. Nothing was saved.'
        6   | 'the from kind is unknown'                     | 'mine'         | 'story'  | 'missing'  | 'item'  | 'self'    || 'A link joins two of: item, note, log, file, term, topic, question, decision. Nothing was saved.'
        7   | 'the to kind is unknown'                       | 'mine'         | 'item'   | 'mine'     | 'story' | 'missing' || 'A link joins two of: item, note, log, file, term, topic, question, decision. Nothing was saved.'
        8   | 'the solution does not exist'                  | 'missing'      | 'item'   | 'mine'     | 'file'  | 'mine'    || 'That solution no longer exists. Nothing was saved.'
    }

    def "link should keep working on the same connection when a refusal came first"() {

        given:
        int item = newItem(project)
        int note = newNote(this, sid, 'Runbook')
        ruleOf { repo.link(sid, 'item', item, 'item', item, 'tony') }

        expect:
        repo.link(sid, 'item', item, 'note', note, 'tony') > 0
    }

    def "unlink should remove only the link, leave both records and audit the delete, answering false the second time"() {

        given:
        int item = newItem(project)
        int note = newNote(this, sid, 'Runbook')
        int id = repo.link(sid, 'item', item, 'note', note, 'tony')

        expect:
        repo.unlink(id)
        repo.findLink(id) == null
        count('SELECT COUNT(*) FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = ?', item) == 1
        count('SELECT COUNT(*) FROM dbo.SolutionNote WHERE SolutionNoteID = ?', note) == 1
        logRows('SolutionLink', 'SolutionLinkID', id)*.IsInsertion == [true, false]
        logRows('SolutionLink', 'SolutionLinkID', id).last().DeletedDate != null
        !repo.unlink(id)
    }

    def "findLink should answer null when the id is unknown"() {

        expect:
        repo.findLink(-1) == null
        repo.findLink(0) == null
    }

    def "links should answer the other end of every link with topics first and count them all"() {

        given:
        int item = newItem(project)
        int note = newNote(this, sid, 'Runbook')
        int log = newLog(sid, 'Restarted the service')
        int term = newTerm(this, sid, 'EOB', 'tony')
        int file = newFile(sid, 'notes.txt', 'Notes file')
        int topic = newTopic(repo, sid, 'Charset')
        [['note', note], ['log', log], ['term', term], ['file', file]].each { List pair ->
            repo.link(sid, 'item', item, pair[0] as String, pair[1] as int, 'tony')
        }
        repo.link(sid, 'topic', topic, 'item', item, null)

        when:
        LinkPage page = repo.links(sid, 'item', item, 0, 50)
        Link logLink = page.links().find { Link l -> l.kind() == 'log' }

        then:
        page.kind() == 'item'
        page.id() == item
        page.total() == 5
        page.offset() == 0
        page.pageSize() == 50
        page.links()[0].kind() == 'topic'
        page.links()[0].id() == topic
        page.links()[0].title() == 'Charset'
        page.links()[0].linkedByCode() == null
        page.links().collect { Link l -> l.kind() + ':' + l.id() } as Set ==
            ['note:' + note, 'log:' + log, 'term:' + term, 'file:' + file, 'topic:' + topic] as Set
        page.links().findAll { Link l -> l.kind() != 'topic' }*.linkedByCode() == ['tony', 'tony', 'tony', 'tony']
        page.links().every { Link l -> l.linkId() > 0 && l.linkedDate() != null && l.recordDate() != null }
        logLink.title().startsWith('#')
        logLink.title().endsWith('Restarted the service')
        page.links().find { Link l -> l.kind() == 'file' }.title() == 'Notes file'
        page.links().find { Link l -> l.kind() == 'file' }.sub() == 'notes.txt'
    }

    def "links should show the link from the other end too, since a link has no direction"() {

        given:
        int item = newItem(project, 'Upgrade the driver')
        int note = newNote(this, sid, 'Runbook')
        int id = repo.link(sid, 'item', item, 'note', note, 'tony')

        when:
        LinkPage fromNote = repo.links(sid, 'note', note, 0, 50)

        then:
        fromNote.total() == 1
        fromNote.links()*.linkId() == [id]
        fromNote.links()*.kind() == ['item']
        fromNote.links()*.id() == [item]
        fromNote.links()[0].title() == 'Upgrade the driver'
        !fromNote.links()[0].closed()
    }

    def "links should page in SQL and still answer the total when the page asked is past the end"() {

        given:
        int item = newItem(project)
        (1..5).each { int n -> repo.link(sid, 'item', item, 'note', newNote(this, sid, 'Note ' + n), 'tony') }

        when:
        LinkPage second = repo.links(sid, 'item', item, 1, 2)
        LinkPage past = repo.links(sid, 'item', item, 10, 5)
        LinkPage proc = repo.links(sid, 'item', item, 0, 0)

        then:
        second.links().size() == 2
        second.total() == 5
        second.offset() == 1
        second.pageSize() == 2
        past.links().isEmpty()
        past.total() == 5
        past.offset() == 10
        proc.links().size() == 5
        proc.total() == 5
    }

    def "links should answer an empty page with a total of zero when the record has no links on any page"() {

        given:
        int item = newItem(project)

        expect:
        repo.links(sid, 'item', item, 0, 50).total() == 0
        repo.links(sid, 'item', item, 0, 50).links().isEmpty()
        repo.links(sid, 'item', item, 3, 50).total() == 0
        repo.links(sid, 'item', 987654321, 0, 50).total() == 0
    }

    def "links should leave out every link when the solution named is not the record's"() {

        given:
        int item = newItem(project)
        int note = newNote(this, sid, 'Runbook')
        repo.link(sid, 'item', item, 'note', note, 'tony')

        expect:
        repo.links(newSolution(), 'item', item, 0, 50).total() == 0
        repo.links(sid, 'item', item, 0, 50).total() == 1
    }

    // ---- the Link picker ----

    def "targets should rank titles that start with the words first, then topics, and mark the ones already linked"() {

        given:
        int item = newItem(project, 'Source item')
        int topic = newTopic(repo, sid, 'Charset')
        int note = newNote(this, sid, 'Charset runbook')
        int log = newLog(sid, 'Fixed the charset')
        newTerm(this, sid, 'Zebra', 'tony')
        newTopic(repo, newSolution(), 'Charset elsewhere')
        repo.link(sid, 'item', item, 'topic', topic, 'tony')

        when:
        List<LinkTarget> got = repo.targets(sid, 'CHARSET', 'item', item, 30)

        then:
        got*.kind() == ['topic', 'note', 'log']
        got*.id() == [topic, note, log]
        got*.alreadyLinked() == [true, false, false]
        got[2].title().endsWith('Fixed the charset')
        got.every { LinkTarget t -> t.recordDate() != null }
    }

    def "targets should leave out the record being linked from but offer it to anyone else"() {

        given:
        int item = newItem(project, 'Source item')

        expect:
        repo.targets(sid, 'source', 'item', item, 30).isEmpty()
        repo.targets(sid, 'source', null, null, 30)*.id() == [item]
        repo.targets(sid, 'source', 'note', item, 30)*.id() == [item]
    }

    def "targets should offer the topics first and then everything else when nothing is typed"() {

        given:
        newItem(project, 'An item')
        newNote(this, sid, 'A note')
        newTerm(this, sid, 'A term', 'tony')
        int topic = newTopic(repo, sid, 'A topic')

        when:
        List<LinkTarget> all = repo.targets(sid, null, null, null, 30)

        then:
        all.size() == 4
        all[0].kind() == 'topic'
        all[0].id() == topic
        all*.kind() as Set == ['item', 'note', 'term', 'topic'] as Set
        !all.any { LinkTarget t -> t.alreadyLinked() }
        repo.targets(sid, '', null, null, 30).size() == 4
        repo.targets(sid, null, null, null, 2).size() == 2
        repo.targets(sid, null, null, null, 0).size() == 4
        repo.targets(sid, 'nothing is called this', null, null, 30).isEmpty()
    }

    def "targets should match LIKE wildcards in what was typed as themselves"() {

        given:
        int pct = newTerm(this, sid, '50% rule', 'tony')
        newTerm(this, sid, '500 rule', 'tony')
        newTerm(this, sid, 'Brackets', 'tony')

        expect:
        repo.targets(sid, '0%', null, null, 30)*.id() == [pct]
        repo.targets(sid, '_0', null, null, 30).isEmpty()
        repo.targets(sid, '[b]', null, null, 30).isEmpty()
    }

    // ---- topics ----

    def "saveTopic should store a new topic with its name tidied and findTopic should read every field back"() {

        when:
        int id = repo.saveTopic(new TopicDraft(0, sid, "  Charset\r\nrules ", '  Pinned to windows-1252  '), 'Rex')
        Topic t = repo.findTopic(id)

        then:
        id > 0
        t.id() == id
        t.solutionId() == sid
        t.name() == 'Charset  rules'
        t.summary() == 'Pinned to windows-1252'
        !t.truncated()
        t.authorCode() == 'rex'
        t.modifiedBy() == 'rex'
        t.createdDate() != null
        t.memberCount() == 0
        scalar('SELECT NormalizedName FROM dbo.SolutionTopic WHERE SolutionTopicID = ?', id) == 'charset rules'
    }

    def "saveTopic should store no summary when the summary is blank"() {

        when:
        int id = repo.saveTopic(new TopicDraft(0, sid, 'Bare', '   '), 'tony')

        then:
        repo.findTopic(id).summary() == null
    }

    def "saveTopic should keep a null field, clear an empty summary and record the editor when a topic is edited"() {

        given:
        int id = newTopic(repo, sid, 'Charset', 'First summary', 'rex')

        when:
        repo.saveTopic(new TopicDraft(id, 0, null, 'Second summary'), 'Tony')
        Topic t = repo.findTopic(id)

        then:
        t.name() == 'Charset'
        t.summary() == 'Second summary'
        t.authorCode() == 'rex'
        t.modifiedBy() == 'tony'
        t.modifiedDate() != null

        when:
        repo.saveTopic(new TopicDraft(id, 0, ' Encoding ', ''), 'tony')
        t = repo.findTopic(id)

        then:
        t.name() == 'Encoding'
        t.summary() == null
        scalar('SELECT NormalizedName FROM dbo.SolutionTopic WHERE SolutionTopicID = ?', id) == 'encoding'
    }

    def "saveTopic should keep the topic in its own solution when an edit names another solution"() {

        given:
        int id = newTopic(repo, sid, 'Stay')

        when:
        repo.saveTopic(new TopicDraft(id, newSolution(), 'Stay put', null), 'tony')

        then:
        repo.findTopic(id).solutionId() == sid
        repo.findTopic(id).name() == 'Stay put'
    }

    def "saveTopic should refuse a repeat name ignoring case and spaces but allow it in another solution"() {

        given:
        newTopic(repo, sid, 'Claim  Batch')
        int other = newTopic(repo, sid, 'Other')

        expect:
        ruleOf { newTopic(repo, sid, ' claim   BATCH ') }.message == 'This solution already has the topic "Claim  Batch". Nothing was saved.'
        ruleOf { repo.saveTopic(new TopicDraft(other, 0, 'CLAIM BATCH', null), 'tony') }.message ==
            'This solution already has the topic "Claim  Batch". Nothing was saved.'
        count('SELECT COUNT(*) FROM dbo.SolutionTopic WHERE SolutionID = ?', sid) == 2
        newTopic(repo, newSolution(), 'claim batch') > 0
    }

    def "saveTopic should let a topic keep its own name in another case when it is edited"() {

        given:
        int id = newTopic(repo, sid, 'Claim batch')

        when:
        repo.saveTopic(new TopicDraft(id, 0, 'CLAIM  BATCH', null), 'tony')

        then:
        repo.findTopic(id).name() == 'CLAIM  BATCH'
    }

    def "saveTopic should keep the percent sign in the refusal when the repeat name holds one"() {

        given:
        newTopic(repo, sid, '100% done')

        expect:
        ruleOf { newTopic(repo, sid, '100%   DONE') }.message == 'This solution already has the topic "100% done". Nothing was saved.'
    }

    @Unroll
    def "saveTopic should refuse and write nothing when #scenario"() {

        given:
        int existing = newTopic(repo, sid, 'Existing')
        int before = count('SELECT COUNT(*) FROM dbo.SolutionTopic')
        int id = target == 'existing' ? existing : (target == 'missing' ? 987654321 : 0)
        int solution = target == 'missing-solution' ? -1 : sid
        String nameText = nameLength > 0 ? getReplicatedString('n', nameLength) : name

        when:
        repo.saveTopic(new TopicDraft(id, solution, nameText, null), 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        count('SELECT COUNT(*) FROM dbo.SolutionTopic') == before
        repo.findTopic(existing).name() == 'Existing'

        where:
        scenario                               | target             | name  | nameLength || expectedMessage
        'the topic to edit no longer exists'   | 'missing'          | 'x'   | 0          || 'That topic no longer exists. Nothing was saved.'
        'the solution does not exist'          | 'missing-solution' | 'x'   | 0          || 'That solution no longer exists. Nothing was added.'
        'a new name is blank'                  | 'new'              | '  '  | 0          || 'A topic needs a name. Nothing was added.'
        'a new name is missing'                | 'new'              | null  | 0          || 'A topic needs a name. Nothing was added.'
        'an edit blanks the name'              | 'existing'         | ' '   | 0          || 'A topic name cannot be blank. Nothing was saved.'
        'the name is over 200 characters'      | 'new'              | null  | 201        || 'A topic name can be at most 200 characters. Nothing was saved.'
    }

    def "saveTopic should take a name of exactly 200 characters"() {

        when:
        int id = repo.saveTopic(new TopicDraft(0, sid, getReplicatedString('n', 200), null), 'tony')

        then:
        repo.findTopic(id).name().length() == 200
    }

    def "findTopic should answer null when the id is unknown"() {

        expect:
        repo.findTopic(-1) == null
    }

    def "findTopic should count the records linked to the topic from either end"() {

        given:
        int topic = newTopic(repo, sid, 'Charset')
        repo.link(sid, 'topic', topic, 'item', newItem(project), 'tony')
        repo.link(sid, 'note', newNote(this, sid, 'Runbook'), 'topic', topic, 'tony')

        expect:
        repo.findTopic(topic).memberCount() == 2
        repo.links(sid, 'topic', topic, 0, 50).total() == 2
    }

    def "topics should list by name with a 600-character preview and count each topic's records when topics are mixed"() {

        given:
        int beta = newTopic(repo, sid, 'Beta', getReplicatedString('x', 700))
        int alpha = newTopic(repo, sid, 'Alpha')
        int gamma = newTopic(repo, sid, 'Gamma')
        newTopic(repo, newSolution(), 'Aardvark elsewhere')
        repo.link(sid, 'topic', alpha, 'item', newItem(project), 'tony')
        repo.link(sid, 'topic', alpha, 'note', newNote(this, sid, 'Runbook'), 'tony')

        when:
        List<Topic> list = repo.topics(sid, null, 0, 50)

        then:
        list*.id() == [alpha, beta, gamma]
        list*.name() == ['Alpha', 'Beta', 'Gamma']
        list[1].summary().length() == 600
        list[1].truncated()
        !list[0].truncated()
        list*.memberCount() == [2, 0, 0]
        list[0].latestDate() != null
        list[2].latestDate() == null
        repo.topics(sid, null, 1, 1)*.name() == ['Beta']
        repo.topics(sid, null, 0, 0).size() == 3
    }

    def "topics and topicCounts should match the name or the summary with LIKE wildcards literal when searching"() {

        given:
        newTopic(repo, sid, 'Charset')
        newTopic(repo, sid, 'Other', 'All about the charset')
        newTopic(repo, sid, '50% rule')
        newTopic(repo, sid, '500 rule')

        expect:
        repo.topics(sid, 'CHARSET', 0, 50)*.name() == ['Charset', 'Other']
        repo.topicCounts(sid, 'charset') == [total: 2, all: 4]
        repo.topicCounts(sid, null) == [total: 4, all: 4]
        repo.topics(sid, '0%', 0, 50)*.name() == ['50% rule']
        repo.topics(sid, '_0', 0, 50).isEmpty()
        repo.topicCounts(sid, 'nothing matches') == [total: 0, all: 4]
        repo.topicCounts(newSolution(), null) == [total: 0, all: 0]
    }

    def "deleteTopic should remove the topic and its links but leave the records, answering false the second time"() {

        given:
        int topic = newTopic(repo, sid, 'Charset')
        int item = newItem(project)
        int note = newNote(this, sid, 'Runbook')
        repo.link(sid, 'topic', topic, 'item', item, 'tony')
        repo.link(sid, 'note', note, 'topic', topic, 'tony')
        int keep = repo.link(sid, 'item', item, 'note', note, 'tony')

        expect:
        repo.deleteTopic(topic)
        repo.findTopic(topic) == null
        linksOf(this, 'topic', topic) == 0
        repo.findLink(keep) != null
        count('SELECT COUNT(*) FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = ?', item) == 1
        count('SELECT COUNT(*) FROM dbo.SolutionNote WHERE SolutionNoteID = ?', note) == 1
        logRows('SolutionTopic', 'SolutionTopicID', topic).last().DeletedDate != null
        !repo.deleteTopic(topic)
    }

    // ---- since you were last here ----

    def "touch should start a first visit with no previous one and visit should read it back without moving it"() {

        given:
        String code = member()

        expect:
        repo.visit(sid, code) == null

        when:
        Visit first = repo.touch(sid, code)

        then:
        first.previousSeen() == null
        first.lastSeen() != null
        repo.visit(sid, code) == first
        repo.visit(sid, code) == first

        when:
        Visit again = repo.touch(sid, code)

        then:
        again.previousSeen() == null
        !again.lastSeen().isBefore(first.lastSeen())
        count('SELECT COUNT(*) FROM dbo.SolutionMemberVisit WHERE SolutionID = ?', sid) == 1
    }

    @Unroll
    def "touch should #expectedOutcome when the last visit was #scenario"() {

        given:
        String code = member()
        repo.touch(sid, code)
        sql.update('UPDATE dbo.SolutionMemberVisit SET LastSeenDate = DATEADD(MINUTE, ?, LastSeenDate) WHERE SolutionID = ?', -minutesBack, sid)
        LocalDateTime old = repo.visit(sid, code).lastSeen()

        when:
        Visit v = repo.touch(sid, code)

        then:
        (v.previousSeen() == old) == rolled
        (v.previousSeen() == null) == !rolled
        v.lastSeen().isAfter(old)

        where:
        scenario                 | minutesBack || rolled | expectedOutcome
        'two hours ago'          | 120         || true   | 'roll the visit'
        'just over an hour ago'  | 62          || true   | 'roll the visit'
        'half an hour ago'       | 30          || false  | 'keep the visit'
    }

    def "touch should keep each member's visit apart when two members open one solution"() {

        given:
        String mine = member()
        String theirs = member()

        when:
        repo.touch(sid, mine)

        then:
        repo.visit(sid, mine) != null
        repo.visit(sid, theirs) == null
        repo.visit(newSolution(), mine) == null
    }

    def "caughtUp should start since now for a member who has been here and for one who never was"() {

        given:
        String seen = member()
        String fresh = member()
        repo.touch(sid, seen)
        sql.update('UPDATE dbo.SolutionMemberVisit SET LastSeenDate = DATEADD(HOUR, -2, LastSeenDate) WHERE SolutionID = ?', sid)
        LocalDateTime old = repo.touch(sid, seen).previousSeen()

        when:
        repo.caughtUp(sid, seen)
        repo.caughtUp(sid, fresh)
        Visit a = repo.visit(sid, seen)
        Visit b = repo.visit(sid, fresh)

        then:
        old != null
        a.previousSeen() == a.lastSeen()
        a.previousSeen().isAfter(old)
        b.previousSeen() != null
        b.previousSeen() == b.lastSeen()
    }

    @Unroll
    def "#action should refuse with the proc's sentence when #scenario"() {

        given:
        int solutionId = solutionTarget == 'mine' ? sid : -1

        when:
        if (action == 'touch') repo.touch(solutionId, memberCode) else repo.caughtUp(solutionId, memberCode)

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                        | action     | solutionTarget | memberCode || expectedMessage
        'the solution does not exist'   | 'touch'    | 'missing'      | 'tony'     || 'That solution no longer exists.'
        'the member is empty'           | 'touch'    | 'mine'         | ''         || 'A visit needs a member.'
        'the member is null'            | 'touch'    | 'mine'         | null       || 'A visit needs a member.'
        'the solution does not exist'   | 'caughtUp' | 'missing'      | 'tony'     || 'That solution no longer exists.'
        'the member is empty'           | 'caughtUp' | 'mine'         | ''         || 'A visit needs a member.'
        'the member is null'            | 'caughtUp' | 'mine'         | null       || 'A visit needs a member.'
    }

    def "changes and changeCounts should leave out the member's own changes and agree when others changed things"() {

        given:
        LocalDateTime since = LocalDateTime.now().minusDays(1)
        int item = newItem(project, 'Tony item')
        int log = newLog(sid, 'Tony wrote this')
        int topic = newTopic(repo, sid, 'Rex topic', 'A summary', 'rex')
        int term = newTerm(this, sid, 'Rex term', 'rex')

        when:
        List<Change> others = repo.changes(sid, since, 'tony', 0, 50)

        then:
        others.collect { Change c -> c.kind() + ':' + c.id() } as Set == ['topic:' + topic, 'term:' + term] as Set
        others.every { Change c -> c.who() == 'rex' && c.whoName() != null && c.action() == 'added' && c.changedDate() != null }
        others.find { Change c -> c.kind() == 'topic' }.title() == 'Rex topic'
        others.find { Change c -> c.kind() == 'topic' }.sub() == 'A summary'
        repo.changeCounts(sid, since, 'tony') == [total: 2, 'kind:topic': 1, 'kind:term': 1]

        and: 'the member code is matched whatever its case'
        repo.changes(sid, since, 'REX', 0, 50).collect { Change c -> c.kind() + ':' + c.id() } as Set == ['item:' + item, 'log:' + log] as Set
        repo.changeCounts(sid, since, 'REX') == [total: 2, 'kind:item': 1, 'kind:log': 1]
        repo.changes(sid, since, 'REX', 0, 50).find { Change c -> c.kind() == 'log' }.action() == 'wrote'

        and: 'no member means every change'
        repo.changes(sid, since, null, 0, 50).size() == 4
        repo.changeCounts(sid, since, null).total == 4
        repo.changes(sid, since, null, 1, 2).size() == 2
        repo.changes(sid, since, null, 0, 0).size() == 4
    }

    def "changes should list the newest change first when changes are spread over time"() {

        given:
        int older = newTopic(repo, sid, 'Older')
        int newer = newTopic(repo, sid, 'Newer')
        sql.update('UPDATE dbo.SolutionTopic SET ModifiedDate = DATEADD(HOUR, -3, GETDATE()) WHERE SolutionTopicID = ?', older)

        expect:
        repo.changes(sid, LocalDateTime.now().minusDays(1), 'tony', 0, 50)*.id() == [newer, older]
    }

    def "changes should mark an edit as changed when the record was added before since"() {

        given:
        int topic = newTopic(repo, sid, 'Charset', null, 'rex')
        sql.update('UPDATE dbo.SolutionTopic SET CreatedDate = DATEADD(DAY, -3, GETDATE()), ModifiedDate = DATEADD(DAY, -3, GETDATE()) WHERE SolutionTopicID = ?', topic)
        LocalDateTime since = LocalDateTime.now().minusDays(1)

        expect:
        repo.changes(sid, since, 'tony', 0, 50).isEmpty()

        when:
        repo.saveTopic(new TopicDraft(topic, 0, null, 'Edited'), 'rex')
        List<Change> got = repo.changes(sid, since, 'tony', 0, 50)

        then:
        got*.id() == [topic]
        got*.action() == ['changed']
    }

    def "changes and changeCounts should answer nothing when since is after every change"() {

        given:
        newTopic(repo, sid, 'Charset', null, 'rex')
        LocalDateTime later = LocalDateTime.now().plusDays(1)

        expect:
        repo.changes(sid, later, 'tony', 0, 50).isEmpty()
        repo.changeCounts(sid, later, 'tony') == [total: 0]
    }

    def "changes should leave out the changes of another solution"() {

        given:
        newTopic(repo, newSolution(), 'Elsewhere', null, 'rex')

        expect:
        repo.changes(sid, LocalDateTime.now().minusDays(1), 'tony', 0, 50).isEmpty()
        repo.changeCounts(sid, LocalDateTime.now().minusDays(1), 'tony') == [total: 0]
    }

    // ---- the delete procs take the links with them ----

    @Unroll
    def "#proc should remove the #kind's links from either end but leave the other records when #scenario"() {

        given:
        int anchor = newFile(sid, 'anchor.txt', 'Anchor')
        int topic = newTopic(repo, sid, 'Charset')
        int record = newRecord(this, repo, kind, sid, project, 'Doomed ' + uniq())
        repo.link(sid, kind, record, 'topic', topic, 'tony')
        int fromAnchor = repo.link(sid, 'file', anchor, kind, record, 'tony')
        int bystander = repo.link(sid, 'file', anchor, 'topic', topic, 'tony')
        int before = linksOf(this, kind, record)

        when:
        Map<String, Object> result = row('EXEC dbo.' + proc + ' ?', record)

        then:
        ((Number) result.RowsAffected).intValue() >= 1
        before == 2
        linksOf(this, kind, record) == 0
        repo.findLink(fromAnchor) == null
        repo.findLink(bystander) != null
        repo.findTopic(topic).memberCount() == 1
        count('SELECT COUNT(*) FROM dbo.SolutionFile WHERE SolutionFileID = ?', anchor) == 1

        where:
        scenario                 | kind   | proc                              || unused
        'an item is deleted'     | 'item' | 'stp_SolutionProjectItem_Delete'  || null
        'a note is deleted'      | 'note' | 'stp_SolutionNote_Delete'         || null
        'a log entry is deleted' | 'log'  | 'stp_SolutionLog_Delete'          || null
        'a file is deleted'      | 'file' | 'stp_SolutionFile_Delete'         || null
        'a term is deleted'      | 'term' | 'stp_SolutionTerm_Delete'         || null
    }

    def "stp_SolutionProjectItem_Delete should remove the links of every sub-item when a parent item is deleted"() {

        given:
        int topic = newTopic(repo, sid, 'Charset')
        int parent = newItem(project, 'Parent')
        int child = newItem(project, 'Child', parent)
        int grandchild = newItem(project, 'Grandchild', child)
        [parent, child, grandchild].each { int i -> repo.link(sid, 'item', i, 'topic', topic, 'tony') }

        when:
        row('EXEC dbo.stp_SolutionProjectItem_Delete ?', parent)

        then:
        linksOf(this, 'item', parent) == 0
        linksOf(this, 'item', child) == 0
        linksOf(this, 'item', grandchild) == 0
        repo.findTopic(topic).memberCount() == 0
    }

    def "stp_SolutionProject_Delete should remove the links of the project's items"() {

        given:
        int topic = newTopic(repo, sid, 'Charset')
        int item = newItem(project)
        repo.link(sid, 'item', item, 'topic', topic, 'tony')

        when:
        row('EXEC dbo.stp_SolutionProject_Delete ?', project)

        then:
        linksOf(this, 'item', item) == 0
        repo.findTopic(topic).memberCount() == 0
    }

    def "stp_Solution_Delete should remove every link, topic and visit of the solution and audit the links"() {

        given:
        int item = newItem(project)
        int note = newNote(this, sid, 'Runbook')
        int topic = newTopic(repo, sid, 'Charset')
        int a = repo.link(sid, 'item', item, 'note', note, 'tony')
        int b = repo.link(sid, 'topic', topic, 'item', item, 'tony')
        repo.touch(sid, member())
        int other = newSolution()
        int otherTopic = newTopic(repo, other, 'Elsewhere')
        int kept = repo.link(other, 'topic', otherTopic, 'note', newNote(this, other, 'Their note'), 'tony')

        when:
        row('EXEC dbo.stp_Solution_Delete ?', sid)

        then:
        count('SELECT COUNT(*) FROM dbo.SolutionLink WHERE SolutionID = ?', sid) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionTopic WHERE SolutionID = ?', sid) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionMemberVisit WHERE SolutionID = ?', sid) == 0
        logRows('SolutionLink', 'SolutionLinkID', a).last().DeletedDate != null
        logRows('SolutionLink', 'SolutionLinkID', b).last().DeletedDate != null
        repo.findTopic(topic) == null
        repo.findLink(kept) != null
        repo.findTopic(otherTopic).memberCount() == 1
    }
}
