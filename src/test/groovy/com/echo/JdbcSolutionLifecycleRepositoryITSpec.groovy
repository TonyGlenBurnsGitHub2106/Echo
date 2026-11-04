package com.echo

import java.math.BigDecimal

import com.echo.SolutionDecisionModels.DecisionDraft
import com.echo.SolutionDecisionModels.QuestionDraft
import com.echo.SolutionLifecycleModels.OpenThing
import com.echo.SolutionLifecycleModels.ShapeResult
import com.echo.SolutionLinkModels.TopicDraft

import spock.lang.PendingFeature
import spock.lang.Unroll

/**
 * Story 5695 (Release 6): JdbcSolutionLifecycleRepository against a real EchoTest_ database.
 *
 *   shape       stp_Solution_ApplyShape -- each starting frame's exact projects, kinds of part (with their number
 *               labels), parts (with their kind) and tags; every project and part at the first status, every
 *               list numbered in order, the work attributed to the member; the refusals (a solution with a
 *               project, a topic or a kind of part; no such shape; no such solution; its own shape; no source)
 *               with nothing added; and copy: the other solution's projects (description, owner, order), kinds
 *               of part, parts linked to the NEW kinds, tags, term types and term categories, and none of its
 *               items, notes, log, files, terms, questions, decisions, subjects or assumptions
 *   close-out   stp_Solution_CloseOutList -- every group found (open projects and items only, open questions,
 *               proposed decisions, parts not closed or with no status, assumptions holding or unmarked but
 *               not proved false, threads whose last message awaits an answer and are not resolved), each row's
 *               kind, id, title and sub, and the order: by group, then by project and order
 *
 * Every feature is rolled back by EchoDbITSpec.
 */
class JdbcSolutionLifecycleRepositoryITSpec extends EchoDbITSpec {

    static final String NOT_EMPTY = 'A shape is for a new, empty solution. This one has projects or topics already. Nothing was added.'
    static final String NO_SUCH_SHAPE = 'A shape is programming, writing, life or copy. Nothing was added.'
    static final String NO_SOURCE = 'The solution to copy the shape of no longer exists. Nothing was added.'

    JdbcSolutionLifecycleRepository repo
    JdbcSolutionLinkRepository links
    JdbcSolutionTopicKindRepository kinds
    JdbcSolutionDecisionRepository decisions
    int sid
    String me

    def setup() {
        repo = new JdbcSolutionLifecycleRepository(dataSource())
        links = new JdbcSolutionLinkRepository(dataSource())
        kinds = new JdbcSolutionTopicKindRepository(dataSource())
        decisions = new JdbcSolutionDecisionRepository(dataSource())
        sid = newSolution()
        me = member(this)
    }

    // ---- fixtures ----

    /** A member code no other feature uses, created so its name is the code with a capital first letter. */
    private static String member(EchoDbITSpec db) {
        String code = ('it' + uniq()).take(20)
        db.scalar('SET NOCOUNT ON; DECLARE @m INT; EXEC dbo.stp_SolutionMember_FindOrCreate ?, @m OUTPUT; SELECT @m AS SolutionMemberID', code)
        return code
    }

    private static int project(EchoDbITSpec db, int solutionId, String name, String status, String owner, String description = 'pdesc') {
        db.idOf('EXEC dbo.stp_SolutionProject_Save 0, ?, ?, ?, ?, ?, 0, ?', solutionId, name, description, status, owner, 'felix')
    }

    /** An item of the project; assignee null = nobody has it. */
    private static int item(EchoDbITSpec db, int projectId, String title, String status, String assignee) {
        db.idOf('EXEC dbo.stp_SolutionProjectItem_Save 0, ?, NULL, ?, ?, ?, ?, 0, 0, NULL, ?', projectId, title, 'detail', status, assignee, 'tony')
    }

    /** A part of the solution, of the named kind of part (null = none). */
    private static int part(JdbcSolutionLinkRepository r, int solutionId, String name, String typeName, String status,
                            String figureText = null) {
        BigDecimal figure = figureText == null ? null : new BigDecimal(figureText)
        r.saveTopic(new TopicDraft(0, solutionId, name, null, 'part', typeName, status, figure, false, null), 'tony')
    }

    /** A topic of the named kind (subject, tag or assumption). */
    private static int topic(JdbcSolutionLinkRepository r, int solutionId, String name, String kind, Boolean holds = null) {
        r.saveTopic(new TopicDraft(0, solutionId, name, null, kind, null, null, null, false, holds), 'tony')
    }

    private static int thread(EchoDbITSpec db, int solutionId, String title) {
        db.idOf('EXEC dbo.stp_SolutionConversation_Create ?, ?', solutionId, title)
    }

    /** A message on the thread; awaiting '' = waiting for nobody. */
    private static int message(EchoDbITSpec db, int conversationId, String sender, String body, String awaiting) {
        db.idOf('EXEC dbo.stp_ConversationMessage_Save @prmConversationMessageID=0, @prmConversationID=?, @prmSender=?, @prmBody=?, ' +
                "@prmAwaitingResponseFrom=?, @prmTokensIn=0, @prmTokensOut=0, @prmDurationMs=0, @prmModelUsed='', @prmErrorText=''",
                conversationId, sender, body, awaiting)
    }

    private static int question(JdbcSolutionDecisionRepository r, int solutionId, String text, String owner = null) {
        r.saveQuestion(new QuestionDraft(0, solutionId, text, null, owner), 'tony')
    }

    private static int decision(JdbcSolutionDecisionRepository r, int solutionId, String title, String status) {
        r.saveDecision(new DecisionDraft(0, solutionId, title, null, null, null, status, null, null), 'tony').id()
    }

    /** A term of the solution, of the named term type, in the categories given one per line ('' = none). */
    private static int term(EchoDbITSpec db, int solutionId, String word, String typeName, String categories) {
        db.idOf("EXEC dbo.stp_SolutionTerm_Save 0, ?, ?, 'Means something', ?, NULL, NULL, ?, 'tony'", solutionId, word, typeName, categories)
    }

    private static int newNote(EchoDbITSpec db, int solutionId, String title) {
        db.idOf("EXEC dbo.stp_SolutionNote_Save 0, ?, NULL, 'runbook', ?, 'Body', NULL, 'tony', 0", solutionId, title)
    }

    // ---- reads ----

    private static List<Map<String, Object>> projectsOf(EchoDbITSpec db, int solutionId) {
        db.rows('SELECT p.SolutionProjectID, p.ProjectName, p.ProjectDescription, p.SortOrder, p.ModifiedBy, s.StatusCode, ' +
                'm.MemberCode AS OwnerCode FROM dbo.SolutionProject p JOIN dbo.SolutionStatus s ON s.SolutionStatusID = p.SolutionStatusID ' +
                'LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = p.OwnerMemberID ' +
                'WHERE p.SolutionID = ? ORDER BY p.SortOrder, p.SolutionProjectID', solutionId)
    }

    private static List<Map<String, Object>> typesOf(EchoDbITSpec db, int solutionId) {
        db.rows('SELECT SolutionTopicTypeID, TypeName, NormalizedName, FigureLabel, SortOrder FROM dbo.SolutionTopicType ' +
                'WHERE SolutionID = ? ORDER BY SortOrder, SolutionTopicTypeID', solutionId)
    }

    private static List<Map<String, Object>> topicsOf(EchoDbITSpec db, int solutionId, String kind) {
        db.rows('SELECT t.SolutionTopicID, t.TopicName, t.NormalizedName, t.SortOrder, t.FigureValue, t.AssumptionHolds, ' +
                't.SolutionTopicTypeID, ty.TypeName, ty.SolutionID AS TypeSolutionID, s.StatusCode, m.MemberCode AS AuthorCode, ' +
                't.ModifiedBy FROM dbo.SolutionTopic t ' +
                'LEFT JOIN dbo.SolutionTopicType ty ON ty.SolutionTopicTypeID = t.SolutionTopicTypeID ' +
                'LEFT JOIN dbo.SolutionStatus s ON s.SolutionStatusID = t.SolutionStatusID ' +
                'LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = t.AuthorMemberID ' +
                'WHERE t.SolutionID = ? AND t.TopicKindCode = ? ORDER BY t.SortOrder, t.SolutionTopicID', solutionId, kind)
    }

    /** How many of each thing a shape never makes or copies. */
    private static Map<String, Integer> othersOf(EchoDbITSpec db, int solutionId) {
        [items    : db.count('SELECT COUNT(*) FROM dbo.SolutionProjectItem i JOIN dbo.SolutionProject p ' +
                             'ON p.SolutionProjectID = i.SolutionProjectID WHERE p.SolutionID = ?', solutionId),
         notes    : db.count('SELECT COUNT(*) FROM dbo.SolutionNote WHERE SolutionID = ?', solutionId),
         log      : db.count('SELECT COUNT(*) FROM dbo.SolutionLog WHERE SolutionID = ?', solutionId),
         files    : db.count('SELECT COUNT(*) FROM dbo.SolutionFile WHERE SolutionID = ?', solutionId),
         terms    : db.count('SELECT COUNT(*) FROM dbo.SolutionTerm WHERE SolutionID = ?', solutionId),
         questions: db.count('SELECT COUNT(*) FROM dbo.SolutionQuestion WHERE SolutionID = ?', solutionId),
         decisions: db.count('SELECT COUNT(*) FROM dbo.SolutionDecision WHERE SolutionID = ?', solutionId),
         threads  : db.count('SELECT COUNT(*) FROM dbo.SolutionConversation WHERE SolutionID = ?', solutionId)]
    }

    /** How many projects, topics and kinds of part the solution has. */
    private static List<Integer> frameOf(EchoDbITSpec db, int solutionId) {
        [db.count('SELECT COUNT(*) FROM dbo.SolutionProject WHERE SolutionID = ?', solutionId),
         db.count('SELECT COUNT(*) FROM dbo.SolutionTopic WHERE SolutionID = ?', solutionId),
         db.count('SELECT COUNT(*) FROM dbo.SolutionTopicType WHERE SolutionID = ?', solutionId)]
    }

    /** "a,b" to ["a", "b"]; "" to an empty list; the token NULL is a null element. */
    private static List<String> listOf(String csv) {
        if (csv.isEmpty()) {
            return []
        }
        List<String> out = []
        for (String s : csv.split(',', -1)) {
            out << (s == 'NULL' ? null : s)
        }
        return out
    }

    // ---- shape: the starting frames ----

    @Unroll
    def "applyShape should give a new, empty solution the #shape shape's projects, kinds of part, parts and tags"() {

        when:
        ShapeResult made = repo.applyShape(sid, shape, null, me)
        List<Map<String, Object>> types = typesOf(this, sid)
        List<Map<String, Object>> parts = topicsOf(this, sid, 'part')
        List<Map<String, Object>> tags = topicsOf(this, sid, 'tag')

        then:
        made == new ShapeResult(listOf(expectedProjects).size(), listOf(expectedKinds).size(), listOf(expectedParts).size(),
                                listOf(expectedTags).size(), 0, 0)
        projectsOf(this, sid)*.ProjectName == listOf(expectedProjects)
        types*.TypeName == listOf(expectedKinds)
        types*.FigureLabel == listOf(expectedLabels)
        parts*.TopicName == listOf(expectedParts)
        parts*.TypeName == listOf(expectedPartKinds)
        tags*.TopicName == listOf(expectedTags)
        topicsOf(this, sid, 'subject').isEmpty()
        topicsOf(this, sid, 'assumption').isEmpty()
        count('SELECT COUNT(*) FROM dbo.SolutionTermType WHERE SolutionID = ?', sid) == 0
        count('SELECT COUNT(*) FROM dbo.SolutionTermCategory WHERE SolutionID = ?', sid) == 0

        where:
        scenario      | shape         || expectedProjects                | expectedKinds       | expectedLabels | expectedParts       | expectedPartKinds                     | expectedTags
        'programming' | 'programming' || 'Design,Build,Test,Release'     | 'Environment'       | 'NULL'         | 'Dev,QA,Production' | 'Environment,Environment,Environment' | 'Risk,Idea'
        'writing'     | 'writing'     || 'Draft,Revise,Publish'          | 'Chapter,Character' | 'words,NULL'   | ''                  | ''                                    | 'Idea,Cut'
        'life'        | 'life'        || 'Gather the facts,Decide,Do it' | 'Account'           | 'balance'      | ''                  | ''                                    | 'Important,Waiting on someone'
    }

    def "applyShape should start every project and part at the first status, number each list in order and credit the member"() {

        when:
        repo.applyShape(sid, 'programming', null, me.toUpperCase())
        List<Map<String, Object>> projects = projectsOf(this, sid)
        List<Map<String, Object>> types = typesOf(this, sid)
        List<Map<String, Object>> parts = topicsOf(this, sid, 'part')
        List<Map<String, Object>> tags = topicsOf(this, sid, 'tag')

        then:
        scalar('SELECT TOP 1 StatusCode FROM dbo.SolutionStatus ORDER BY SortOrder, SolutionStatusID') == 'todo'
        projects*.StatusCode == ['todo', 'todo', 'todo', 'todo']
        projects*.SortOrder == [1, 2, 3, 4]
        projects*.ProjectDescription == [null, null, null, null]
        projects*.OwnerCode == [null, null, null, null]
        projects*.ModifiedBy == [me, me, me, me]
        types*.SortOrder == [1]
        types*.NormalizedName == ['environment']
        parts*.StatusCode == ['todo', 'todo', 'todo']
        parts*.SortOrder == [1, 2, 3]
        parts*.FigureValue == [null, null, null]
        parts*.TypeSolutionID == [sid, sid, sid]
        parts*.NormalizedName == ['dev', 'qa', 'production']
        parts*.AuthorCode == [me, me, me]
        parts*.ModifiedBy == [me, me, me]
        tags*.StatusCode == [null, null]
        tags*.SolutionTopicTypeID == [null, null]
        tags*.SortOrder == [1, 2]
        tags*.AssumptionHolds == [null, null]
    }

    def "applyShape should take the shape's code in any case and with padding"() {

        when:
        ShapeResult made = repo.applyShape(sid, ' WRITING ', null, me)

        then:
        made == new ShapeResult(3, 2, 0, 2, 0, 0)
        projectsOf(this, sid)*.ProjectName == ['Draft', 'Revise', 'Publish']
    }

    def "applyShape should ignore a source sent with a starting frame"() {

        given:
        int other = newSolution()
        project(this, other, 'Not to be copied', 'todo', 'tony')

        when:
        ShapeResult made = repo.applyShape(sid, 'life', other, me)

        then:
        made == new ShapeResult(3, 1, 0, 2, 0, 0)
        projectsOf(this, sid)*.ProjectName == ['Gather the facts', 'Decide', 'Do it']
    }

    // ---- shape: refusals ----

    @Unroll
    def "applyShape should refuse a solution that already has #scenario and add nothing"() {

        given:
        Integer from = shape == 'copy' ? newSolution() : null
        if (already == 'project') {
            newProject(sid)
        } else if (already == 'kind of part') {
            kinds.saveType(0, sid, 'Chapter', null)
        } else if (already == 'part') {
            part(links, sid, 'Opening', null, null)
        } else {
            topic(links, sid, 'Something ' + already, already)
        }
        List<Integer> before = frameOf(this, sid)

        when:
        SolutionRuleException ex = ruleOf { repo.applyShape(sid, shape, from, me) }

        then:
        ex.message == expectedMessage
        frameOf(this, sid) == before

        where:
        scenario                       | already        | shape         || expectedMessage
        'a project'                    | 'project'      | 'programming' || NOT_EMPTY
        'a subject'                    | 'subject'      | 'writing'     || NOT_EMPTY
        'a tag'                        | 'tag'          | 'life'        || NOT_EMPTY
        'an assumption'                | 'assumption'   | 'programming' || NOT_EMPTY
        'a part'                       | 'part'         | 'copy'        || NOT_EMPTY
        'only a kind of part'          | 'kind of part' | 'copy'        || NOT_EMPTY
    }

    @Unroll
    def "#seq. applyShape should refuse #scenario with its own words and add nothing"() {

        given:
        int target = targetName == 'missing' ? Integer.MAX_VALUE : sid
        Integer from = fromName == 'this' ? sid : (fromName == 'missing' ? Integer.MAX_VALUE : null)

        when:
        SolutionRuleException ex = ruleOf { repo.applyShape(target, shape, from, me) }

        then:
        ex.message == expectedMessage
        frameOf(this, sid) == [0, 0, 0]

        where:
        seq | scenario                                  | targetName | shape         | fromName  || expectedMessage
        1   | 'a shape that is not one'                 | 'this'     | 'design'      | 'none'    || 'A shape is programming, writing, life or copy. Nothing was added.'
        2   | 'an empty shape'                          | 'this'     | ''            | 'none'    || 'A shape is programming, writing, life or copy. Nothing was added.'
        3   | 'no shape at all'                         | 'this'     | null          | 'none'    || 'A shape is programming, writing, life or copy. Nothing was added.'
        4   | 'a solution that does not exist'          | 'missing'  | 'programming' | 'none'    || 'That solution no longer exists. Nothing was added.'
        5   | 'a missing solution before a bad shape'   | 'missing'  | 'design'      | 'none'    || 'That solution no longer exists. Nothing was added.'
        6   | 'a copy of its own shape'                 | 'this'     | 'copy'        | 'this'    || 'A solution cannot take its own shape. Nothing was added.'
        7   | 'a copy naming no source'                 | 'this'     | 'copy'        | 'none'    || 'The solution to copy the shape of no longer exists. Nothing was added.'
        8   | 'a copy of a solution that does not exist'| 'this'     | 'Copy'        | 'missing' || 'The solution to copy the shape of no longer exists. Nothing was added.'
    }

    // ---- shape: copy ----

    def "applyShape copy should copy the other solution's skeleton and nothing of its content"() {

        given:
        int src = newSolution()
        String owner = member(this)
        String otherOwner = member(this)
        int alpha = project(this, src, 'Alpha', 'inprogress', owner, 'Alpha in full')
        int beta = project(this, src, 'Beta', 'done', otherOwner, 'Beta in full')
        sql.update('UPDATE dbo.SolutionProject SET SortOrder = CASE WHEN SolutionProjectID = ? THEN 2 ELSE 1 END WHERE SolutionID = ?', alpha, src)
        newItem(alpha, 'An item of Alpha')
        newItem(beta, 'An item of Beta', null, 'done')
        int chapter = kinds.saveType(0, src, 'Chapter', 'words')
        int character = kinds.saveType(0, src, 'Character', null)
        part(links, src, 'Opening', 'Chapter', 'done', '1200')
        part(links, src, 'Hero', 'Character', 'inprogress')
        part(links, src, 'Loose end', null, null)
        topic(links, src, 'Idea', 'tag')
        topic(links, src, 'Cut', 'tag')
        topic(links, src, 'Charset', 'subject')
        topic(links, src, 'The vendor stays', 'assumption', true)
        term(this, src, 'Word', 'Glossary', 'Cat B\nCat A')
        term(this, src, 'EDI', 'Acronym', '')
        newLog(src, 'A log entry')
        newNote(this, src, 'A note')
        newFile(src, 'a.txt')
        question(decisions, src, 'Which feed?')
        decision(decisions, src, 'Use SFTP', 'proposed')
        int t = thread(this, src, 'A thread')
        message(this, t, 'rex', 'Any news?', 'tony')
        Map<String, Integer> before = othersOf(this, sid)
        Map<String, Integer> sourceBefore = othersOf(this, src)
        List<String> sourceParts = topicsOf(this, src, 'part')*.TopicName
        List<String> sourceTags = topicsOf(this, src, 'tag')*.TopicName

        when:
        ShapeResult made = repo.applyShape(sid, 'copy', src, me)
        List<Map<String, Object>> projects = projectsOf(this, sid)
        List<Map<String, Object>> types = typesOf(this, sid)
        List<Map<String, Object>> parts = topicsOf(this, sid, 'part')
        List<Integer> newTypeIds = types.collect { Map<String, Object> r -> ((Number) r.SolutionTopicTypeID).intValue() }

        then: 'the counts'
        made == new ShapeResult(2, 2, 3, 2, 2, 2)

        and: 'the projects, in their order, with their descriptions and owners, at the first status'
        projects*.ProjectName == ['Beta', 'Alpha']
        projects*.ProjectDescription == ['Beta in full', 'Alpha in full']
        projects*.OwnerCode == [otherOwner, owner]
        projects*.StatusCode == ['todo', 'todo']
        projects*.SortOrder == [1, 2]
        projects*.SolutionProjectID.intersect([alpha, beta]).isEmpty()

        and: 'the kinds of part, new ones of this solution'
        types*.TypeName == ['Chapter', 'Character']
        types*.FigureLabel == ['words', null]
        types*.SortOrder == [1, 2]
        newTypeIds.intersect([chapter, character]).isEmpty()

        and: 'the parts, linked to the new kinds, at the first status, with no number'
        parts*.TopicName == sourceParts
        parts.collectEntries { Map<String, Object> r -> [(r.TopicName): r.TypeName] } == [Opening: 'Chapter', Hero: 'Character', 'Loose end': null]
        parts.findAll { Map<String, Object> r -> r.SolutionTopicTypeID != null }
             .every { Map<String, Object> r -> newTypeIds.contains(((Number) r.SolutionTopicTypeID).intValue()) }
        parts*.StatusCode == ['todo', 'todo', 'todo']
        parts*.FigureValue == [null, null, null]
        parts*.SortOrder == [1, 2, 3]

        and: 'the tags, and no subjects or assumptions'
        topicsOf(this, sid, 'tag')*.TopicName == sourceTags
        topicsOf(this, sid, 'subject').isEmpty()
        topicsOf(this, sid, 'assumption').isEmpty()

        and: 'the term types in order and the term categories'
        rows('SELECT TypeName FROM dbo.SolutionTermType WHERE SolutionID = ? ORDER BY SortOrder', sid)*.TypeName == ['Glossary', 'Acronym']
        rows('SELECT CategoryName FROM dbo.SolutionTermCategory WHERE SolutionID = ? ORDER BY CategoryName', sid)*.CategoryName == ['Cat A', 'Cat B']

        and: 'none of the content, and the source as it was'
        othersOf(this, sid) == before
        before.items == 0
        before.terms == 0
        othersOf(this, src) == sourceBefore
        projectsOf(this, src)*.StatusCode == ['done', 'inprogress']
        topicsOf(this, src, 'part').find { Map<String, Object> r -> r.TopicName == 'Opening' }.StatusCode == 'done'
    }

    def "applyShape copy should copy an empty solution as nothing at all"() {

        given:
        int src = newSolution()

        when:
        ShapeResult made = repo.applyShape(sid, 'copy', src, me)

        then:
        made == new ShapeResult(0, 0, 0, 0, 0, 0)
        frameOf(this, sid) == [0, 0, 0]
    }

    def "applyShape copy should store a copied kind of part under the normalized name a save gives it"() {

        given:
        int src = newSolution()
        int type = kinds.saveType(0, src, "Main\r\ncharacter", null)

        when:
        repo.applyShape(sid, 'copy', src, me)
        Map<String, Object> copied = typesOf(this, sid)[0]

        then:
        copied.TypeName == 'Main  character'
        copied.NormalizedName == scalar('SELECT NormalizedName FROM dbo.SolutionTopicType WHERE SolutionTopicTypeID = ?', type)
        kinds.saveType(0, sid, 'main character', null) == ((Number) copied.SolutionTopicTypeID).intValue()
    }

    def "applyShape copy should take a source whose term type the new solution already has"() {

        given:
        int src = newSolution()
        term(this, src, 'Word', 'Glossary', '')
        term(this, sid, 'Other word', 'Glossary', '')

        when:
        repo.applyShape(sid, 'copy', src, me)

        then:
        notThrown(SolutionRuleException)
        rows('SELECT TypeName FROM dbo.SolutionTermType WHERE SolutionID = ?', sid)*.TypeName == ['Glossary']
    }

    // ---- close-out ----

    def "closeOut should list every open thing by group, in order, with its kind, id, title and sub, and leave out what is closed"() {

        given: 'projects: one closed with an open item, one open, one skipped'
        String asker = member(this)
        String doer = member(this)
        int pShipped = project(this, sid, 'Shipped work', 'done', 'tony')
        int pLive = project(this, sid, 'Live work', 'inprogress', 'tony')
        project(this, sid, 'Dropped work', 'cancelled', 'tony')
        sql.update('UPDATE dbo.SolutionProject SET SortOrder = CASE SolutionProjectID WHEN ? THEN 1 WHEN ? THEN 2 ELSE 3 END WHERE SolutionID = ?',
                   pShipped, pLive, sid)

        and: 'items: three open, two closed'
        int iLate = item(this, pShipped, 'Left behind', 'todo', null)
        int iWrite = item(this, pLive, 'Write the parser', 'inprogress', doer)
        int iWait = item(this, pLive, 'Wait for the feed', 'blocked', null)
        item(this, pLive, 'Finished', 'done', doer)
        item(this, pLive, 'Skipped', 'cancelled', null)
        sql.update('UPDATE dbo.SolutionProjectItem SET SortOrder = CASE SolutionProjectItemID WHEN ? THEN 1 ELSE 2 END ' +
                   'WHERE SolutionProjectItemID IN (?, ?)', iWait, iWait, iWrite)

        and: 'questions: two open, one answered, one dropped'
        int qFor = question(decisions, sid, 'Which feed?', asker)
        int qNobody = question(decisions, sid, 'Who signs off?')
        decisions.setQuestionStatus(question(decisions, sid, 'Answered already?'), 'answered', null, 'tony')
        decisions.setQuestionStatus(question(decisions, sid, 'Dropped already?'), 'dropped', null, 'tony')

        and: 'decisions: one proposed, one accepted'
        int dProposed = decision(decisions, sid, 'Use SFTP', 'proposed')
        decision(decisions, sid, 'Use UTF-8', 'accepted')

        and: 'parts: four open (one with no status), two closed; a subject and a tag'
        kinds.saveType(0, sid, 'Environment', null)
        kinds.saveType(0, sid, 'Database', null)
        int partEnv = part(links, sid, 'Production', 'Environment', 'inprogress')
        int partDb = part(links, sid, 'Main database', 'Database', 'blocked')
        int partNone = part(links, sid, 'Loose end', null, 'todo')
        int partTodo = part(links, sid, 'Spare part', null, 'todo')
        part(links, sid, 'Retired box', 'Environment', 'done')
        part(links, sid, 'Skipped box', 'Environment', 'cancelled')
        sql.update('UPDATE dbo.SolutionTopic SET SolutionStatusID = NULL WHERE SolutionTopicID = ?', partNone)
        sql.update('UPDATE dbo.SolutionTopic SET SortOrder = CASE SolutionTopicID WHEN ? THEN 5 WHEN ? THEN 1 WHEN ? THEN 3 ELSE 4 END ' +
                   'WHERE SolutionTopicID IN (?, ?, ?, ?)', partEnv, partDb, partNone, partEnv, partDb, partNone, partTodo)
        topic(links, sid, 'Charset', 'subject')
        topic(links, sid, 'Risk', 'tag')

        and: 'assumptions: one holding, one never marked, one proved false'
        int aHolds = topic(links, sid, 'The vendor stays', 'assumption', true)
        int aUnmarked = topic(links, sid, 'The feed is daily', 'assumption', true)
        int aFalse = topic(links, sid, 'The format is fixed', 'assumption', true)
        sql.update('UPDATE dbo.SolutionTopic SET AssumptionHolds = NULL, SortOrder = 1 WHERE SolutionTopicID = ?', aUnmarked)
        sql.update('UPDATE dbo.SolutionTopic SET SortOrder = 2 WHERE SolutionTopicID = ?', aHolds)
        sql.update('UPDATE dbo.SolutionTopic SET AssumptionHolds = 0 WHERE SolutionTopicID = ?', aFalse)

        and: 'threads: three waiting, the others answered, empty, closed or resolved'
        int tWaiting = thread(this, sid, 'Waiting thread')
        message(this, tWaiting, 'rex', 'Any news?', 'tony')
        int tAnswered = thread(this, sid, 'Answered thread')
        message(this, tAnswered, 'rex', 'Any news?', 'tony')
        message(this, tAnswered, 'tony', 'Yes.', '')
        int tAgain = thread(this, sid, 'Asked again')
        message(this, tAgain, 'tony', 'Done for now.', '')
        message(this, tAgain, 'tony', 'One more thing?', 'rex')
        thread(this, sid, 'Empty thread')
        int tClosed = thread(this, sid, 'Closed thread')
        message(this, tClosed, 'rex', 'Any news?', 'tony')
        sql.update("EXEC dbo.stp_Conversation_SetStatus ?, 'closed', 'tony'", tClosed)
        int tResolved = thread(this, sid, 'Resolved thread')
        message(this, tResolved, 'rex', 'Any news?', 'tony')
        sql.update("UPDATE dbo.Conversation SET Status = 'resolved' WHERE ConversationID = ?", tResolved)
        int tUntitled = thread(this, sid, 'To be untitled')
        message(this, tUntitled, 'rex', 'Hello?', 'tony')
        sql.update("UPDATE dbo.Conversation SET Title = '' WHERE ConversationID = ?", tUntitled)

        when:
        List<OpenThing> open = repo.closeOut(sid)

        then:
        open*.group() == ['project', 'item', 'item', 'item', 'question', 'question', 'decision', 'part', 'part', 'part', 'part',
                          'assumption', 'assumption', 'thread', 'thread', 'thread']
        open*.kind() == ['project', 'item', 'item', 'item', 'question', 'question', 'decision', 'topic', 'topic', 'topic', 'topic',
                         'topic', 'topic', 'message', 'message', 'message']
        open*.id() == [pLive, iLate, iWait, iWrite, qFor, qNobody, dProposed, partEnv, partDb, partNone, partTodo,
                       aUnmarked, aHolds, tWaiting, tAgain, tUntitled]
        open*.title() == ['Live work', 'Left behind', 'Wait for the feed', 'Write the parser', 'Which feed?', 'Who signs off?', 'Use SFTP',
                          'Production', 'Main database', 'Loose end', 'Spare part', 'The feed is daily', 'The vendor stays',
                          'Waiting thread', 'Asked again', 'Untitled thread']
        open*.sub() == ['In progress',
                        'Shipped work | Not started', 'Live work | Blocked', 'Live work | In progress | ' + doer.capitalize(),
                        'for ' + asker.capitalize(), 'nobody named to answer it',
                        'proposed, not yet agreed',
                        'Environment | In progress', 'Database | Blocked', 'no status', 'Not started',
                        'still marked holding', 'still marked holding',
                        'waiting for tony', 'waiting for rex', 'waiting for tony']
        open.every { OpenThing o -> o.changedDate() != null }
    }

    def "closeOut should date a thread by its last message"() {

        given:
        int t = thread(this, sid, 'Waiting thread')
        message(this, t, 'tony', 'First.', '')
        int last = message(this, t, 'rex', 'Second?', 'tony')

        when:
        List<OpenThing> open = repo.closeOut(sid)

        then:
        open*.id() == [t]
        open[0].changedDate() == SolutionRows.dateOf(row('SELECT CreatedDate FROM dbo.ConversationMessage WHERE ConversationMessageID = ?', last),
                                                     'CreatedDate')
    }

    def "closeOut should answer nothing for a solution with nothing open"() {

        given:
        int pDone = project(this, sid, 'Shipped work', 'done', 'tony')
        item(this, pDone, 'Finished', 'done', null)
        topic(links, sid, 'Charset', 'subject')

        expect:
        repo.closeOut(sid) == []
        repo.closeOut(newSolution()) == []
    }

    def "closeOut should list only the solution's own open things"() {

        given:
        int other = newSolution()
        int mine = project(this, sid, 'Mine', 'todo', 'tony')
        int theirs = project(this, other, 'Theirs', 'todo', 'tony')
        item(this, theirs, 'Their item', 'todo', null)
        question(decisions, other, 'Their question?')
        message(this, thread(this, other, 'Their thread'), 'rex', 'Hello?', 'tony')

        expect:
        repo.closeOut(sid)*.id() == [mine]
    }

    def "closeOut should leave out a thread resolved the way the house resolves one even if its last message still awaits"() {

        given:
        int t = thread(this, sid, 'Closed thread')
        message(this, t, 'rex', 'Any news?', 'tony')
        sql.update("UPDATE dbo.Conversation SET Status = 'closed' WHERE ConversationID = ?", t)

        expect:
        repo.closeOut(sid) == []
    }
}
