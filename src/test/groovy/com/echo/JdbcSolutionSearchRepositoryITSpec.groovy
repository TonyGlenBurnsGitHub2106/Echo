package com.echo

import java.sql.Timestamp
import java.time.LocalDate

import org.springframework.jdbc.core.JdbcTemplate

import com.echo.SolutionSearchModels.SearchFilter
import com.echo.SolutionSearchModels.SearchHit
import com.echo.SolutionSearchRepository.SearchRows

import spock.lang.Unroll

// Story 5653: JdbcSolutionSearchRepository against a real EchoTest_ database (stp_Solution_Search, fn_Search_Terms,
// fn_Search_Snippet): every tab searched at once, OR / AND / NOT terms, date range, titles only, open only, paging.
class JdbcSolutionSearchRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionSearchRepository repo
    int sid

    def setup() {
        repo = new JdbcSolutionSearchRepository(dataSource())
        sid = newSolution()
    }

    def "search should find the term once per openable thing on every tab with totals per tab when every tab holds it"() {

        given:
        Map<String, Integer> ids = everything(sql, sid)
        newSolution()

        when:
        SearchRows r = repo.search(filter(sid, [or: ['zebra']]))
        Set<String> kinds = r.hits().collect { SearchHit h -> h.source() + '/' + h.kind() + '/' + h.id() } as Set

        then:
        kinds == ['overview/project/' + ids.project, 'items/item/' + ids.item, 'log/entry/' + ids.log,
                  'notes/note/' + ids.note, 'notes/step/' + ids.step, 'terms/term/' + ids.term,
                  'tests/checklist/' + ids.plan, 'tests/test/' + ids.tcase, 'qa/checklist/' + ids.qa,
                  'config/property/' + ids.prop, 'grid/property/' + ids.gprop, 'files/file/' + ids.file,
                  'conversations/conversation/' + ids.conv, 'conversations/message/' + ids.msg] as Set
        r.totals() == [conversations: 2, config: 1, files: 1, grid: 1, items: 1, log: 1, notes: 2, overview: 1, qa: 1, terms: 1, tests: 2]
    }

    def "search should carry the parent, a snippet around the first body match and the term when hits are returned"() {

        given:
        Map<String, Integer> ids = everything(sql, sid)

        when:
        List<SearchHit> hits = repo.search(filter(sid, [or: ['nothing-here', 'Zebra']])).hits()
        SearchHit item = hits.find { it.kind() == 'item' }
        SearchHit log = hits.find { it.kind() == 'entry' }
        SearchHit step = hits.find { it.kind() == 'step' }
        SearchHit tcase = hits.find { it.kind() == 'test' }

        then:
        item.parentId() == ids.project
        item.parentTitle() == 'Zebra project'
        item.title() == 'Feed the zebra'
        item.statusName() == 'Not started'
        item.colorHex() == '#6B7280'
        !item.closed()
        item.extra() == 'Tony'
        item.date() != null
        log.title() == '#1 Note'
        log.snippet() == 'saw a zebra today'
        log.matchTerm() == 'Zebra'
        step.parentId() == ids.note
        step.title() == 'Step 1: Brush'
        tcase.parentTitle() == 'Zebra tests / G'
    }

    @Unroll
    def "search should return #expected when #scenario"() {

        given:
        newLog(sid, 'deploy to prod')
        newLog(sid, 'deploy to qa')
        newLog(sid, 'rollback prod')
        List<String> orTerms = orText == null ? null : orText.split(',').toList()
        List<String> andTerms = andText == null ? null : andText.split(',').toList()
        List<String> notTerms = notText == null ? null : notText.split(',').toList()

        when:
        Set<String> got = texts(repo, filter(sid, [or: orTerms, and: andTerms, not: notTerms, sources: ['log']]))

        then:
        got == (expected == '' ? [] as Set : expected.split(',') as Set)

        where:
        scenario                                    | orText            | andText       | notText    || expected
        "either OR term matches, in any case"       | "DEPLOY,rollback" | null          | null       || "deploy to prod,deploy to qa,rollback prod"
        "every AND term must match"                 | null              | "deploy,prod" | null       || "deploy to prod"
        "a NOT term removes a match"                | "deploy"          | null          | "QA"       || "deploy to prod"
        "OR, AND and NOT are combined"              | "deploy"          | "prod"        | "rollback" || "deploy to prod"
        "nothing matches"                           | "nothing"         | null          | null       || ""
    }

    def "search should return everything in the chosen tabs when no term is given"() {

        given:
        everything(sql, sid)

        expect:
        repo.search(filter(sid, [sources: ['log', 'files']])).totals() == [files: 1, log: 1]
    }

    @Unroll
    def "search should take the LIKE character literally when #scenario"() {

        given:
        newLog(sid, '100% done')
        newLog(sid, '1000 done')
        newLog(sid, 'a_b and [x]')
        newLog(sid, 'axb')

        expect:
        texts(repo, filter(sid, [or: [term], sources: ['log']])) == [expected] as Set

        where:
        scenario                        | term  || expected
        "the term holds a percent"      | "0%"  || "100% done"
        "the term holds an underscore"  | "a_b" || "a_b and [x]"
        "the term holds a bracket"      | "[x]" || "a_b and [x]"
    }

    def "search should never look inside the text when titles only is asked"() {

        given:
        everything(sql, sid)

        when:
        SearchRows r = repo.search(filter(sid, [or: ['striped'], titles: true]))
        SearchRows all = repo.search(filter(sid, [or: ['striped']]))

        then:
        r.hits().isEmpty()
        all.hits()*.kind() == ['term']
    }

    def "search should skip closed projects, items and conversations when open only is asked"() {

        given:
        int p = newProject(sid, 'Done zebra project')
        sql.queryForList("EXEC dbo.stp_SolutionProject_Save ?, 0, 'Done zebra project', NULL, 'done', NULL, 0, 'tony'", p)
        newItem(p, 'closed zebra item', null, 'done')
        newItem(p, 'open zebra item', null, 'todo')
        int conv = idOf('EXEC dbo.stp_SolutionConversation_Create ?, ?', sid, 'zebra chat')
        sql.update("EXEC dbo.stp_Conversation_SetStatus ?, 'closed', 'tony'", conv)

        when:
        List<SearchHit> open = repo.search(filter(sid, [or: ['zebra'], open: true])).hits()
        List<SearchHit> all = repo.search(filter(sid, [or: ['zebra']])).hits()

        then:
        open*.title() == ['open zebra item']
        all*.title() as Set == ['Done zebra project', 'closed zebra item', 'open zebra item', 'zebra chat'] as Set
        all.find { it.kind() == 'conversation' }.statusName() == 'Resolved'
        all.find { it.kind() == 'conversation' }.closed()
    }

    @Unroll
    def "search should keep only the entries dated inside the range when #scenario"() {

        given:
        ['01/14/2026', '01/15/2026', '01/16/2026'].each { String d ->
            sql.queryForList("EXEC dbo.stp_SolutionLog_Save 0, ?, NULL, NULL, 'note', 'tony', ?, ?", sid, 'on ' + d,
                Timestamp.valueOf(getLocalDateTime(d + ' 23:59')))
        }
        LocalDate from = getLocalDate(fromText)
        LocalDate through = getLocalDate(throughText)

        when:
        Set<String> got = texts(repo, filter(sid, [sources: ['log'], from: from, through: through]))

        then:
        got == expected.split(',') as Set

        where:
        scenario                                   | fromText     | throughText  || expected
        "from is the day before the boundary"      | "01/14/2026" | null         || "on 01/14/2026,on 01/15/2026,on 01/16/2026"
        "from is the day of the boundary"          | "01/15/2026" | null         || "on 01/15/2026,on 01/16/2026"
        "from is the day after the boundary"       | "01/16/2026" | null         || "on 01/16/2026"
        "through is the day before the boundary"   | null         | "01/14/2026" || "on 01/14/2026"
        "through is the day of the boundary"       | null         | "01/15/2026" || "on 01/14/2026,on 01/15/2026"
        "through is the day after the boundary"    | null         | "01/16/2026" || "on 01/14/2026,on 01/15/2026,on 01/16/2026"
        "from and through are the same day"        | "01/15/2026" | "01/15/2026" || "on 01/15/2026"
    }

    def "search should list a tab newest first and cap it at the page size while the total counts every hit"() {

        given:
        List<Integer> ids = (1..5).collect { int i ->
            idOf("EXEC dbo.stp_SolutionLog_Save 0, ?, NULL, NULL, 'note', 'tony', ?, ?", sid, 'entry ' + i,
                Timestamp.valueOf(getLocalDateTime('03/0' + i + '/2026 00:00')))
        }

        when:
        SearchRows r = repo.search(filter(sid, [sources: ['log'], size: 2]))

        then:
        r.hits()*.id() == [ids[4], ids[3]]
        r.totals() == [log: 5]
    }

    def "search should page one tab on its own when a single source is given"() {

        given:
        List<Integer> ids = (1..5).collect { int i ->
            idOf("EXEC dbo.stp_SolutionLog_Save 0, ?, NULL, NULL, 'note', 'tony', ?, ?", sid, 'entry ' + i,
                Timestamp.valueOf(getLocalDateTime('03/0' + i + '/2026 00:00')))
        }
        newProject(sid, 'not in the log tab')

        when:
        SearchRows page2 = repo.search(filter(sid, [source: 'log', offset: 2, size: 2]))

        then:
        page2.hits()*.id() == [ids[2], ids[1]]
        page2.hits()*.source().unique() == ['log']
        page2.totals() == [log: 5]
    }

    def "search should center the snippet on the term and trim it with ellipses when the text is long"() {

        given:
        newLog(sid, getReplicatedString('a', 300) + ' needle ' + getReplicatedString('b', 300))

        when:
        SearchHit hit = repo.search(filter(sid, [or: ['needle'], sources: ['log']])).hits()[0]

        then:
        hit.snippet().startsWith('...')
        hit.snippet().endsWith('...')
        hit.snippet().contains('needle')
        hit.snippet().length() == 240 + 6
    }

    @Unroll
    def "search should find nothing when #scenario"() {

        given:
        everything(sql, sid)

        expect:
        repo.search(filter(sid, [sources: sourcesText == null ? null : [sourcesText], source: source])).hits().isEmpty()

        where:
        scenario                                      | sourcesText | source || _
        "the web tab is chosen (Echo has no web)"     | "web"       | null   || _
        "the web tab is paged on its own"             | null        | "web"  || _
    }

    private static SearchFilter filter(int solutionId, Map m) {
        new SearchFilter(solutionId, m.or as List<String>, m.and as List<String>, m.not as List<String>,
            m.sources as List<String>, m.from as LocalDate, m.through as LocalDate, (m.titles ?: false) as boolean,
            (m.open ?: false) as boolean, m.source as String, (m.offset ?: 0) as int, (m.size ?: 25) as int)
    }

    private static Set<String> texts(JdbcSolutionSearchRepository repo, SearchFilter f) {
        repo.search(f).hits()*.snippet() as Set
    }

    private static int id(JdbcTemplate sql, String q, Object... args) {
        ((Number) sql.queryForList(q, args)[0].values().iterator().next()).intValue()
    }

    /** One of everything, each holding the word "zebra" somewhere. */
    private static Map<String, Integer> everything(JdbcTemplate sql, int sid) {
        int p = id(sql, "EXEC dbo.stp_SolutionProject_Save 0, ?, 'Zebra project', 'pdesc', 'todo', 'tony', 0, 'felix'", sid)
        int item = id(sql, "EXEC dbo.stp_SolutionProjectItem_Save 0, ?, NULL, 'Feed the zebra', 'detail', 'todo', 'tony', 0, 0, NULL, 'tony'", p)
        int log = id(sql, "EXEC dbo.stp_SolutionLog_Save 0, ?, NULL, NULL, 'note', 'tony', 'saw a zebra today', NULL", sid)
        int note = id(sql, "EXEC dbo.stp_SolutionNote_Save 0, ?, NULL, 'runbook', 'Zebra care', 'Brush the zebra daily', NULL, 'tony', 0", sid)
        int step = id(sql, "EXEC dbo.stp_SolutionNoteStep_Save 0, ?, 0, 'Brush', 'brush --zebra', NULL, NULL, NULL", note)
        int term = id(sql, "EXEC dbo.stp_SolutionTerm_Save 0, ?, 'Zebra', 'A striped horse', NULL, NULL, NULL, NULL, 'tony'", sid)
        int plan = id(sql, "EXEC dbo.stp_SolutionTestPlan_Save 0, ?, 'Zebra tests', NULL, NULL, 'tony', 'dev'", sid)
        int qa = id(sql, "EXEC dbo.stp_SolutionTestPlan_Save 0, ?, 'Zebra QA', NULL, NULL, 'tony', 'qa'", sid)
        int group = id(sql, "EXEC dbo.stp_SolutionTestGroup_Save 0, ?, 'G', NULL", plan)
        int tcase = id(sql, "EXEC dbo.stp_SolutionTestCase_Save 0, ?, 'The zebra is fed', NULL, NULL, NULL", group)
        int env = id(sql, "EXEC dbo.stp_SolutionConfigEnvironment_Save 0, ?, 'Prod', NULL", sid)
        int cg = id(sql, "EXEC dbo.stp_SolutionConfigGroup_Save 0, ?, 'Zoo', NULL", sid)
        int prop = id(sql, "EXEC dbo.stp_SolutionConfigProperty_Save 0, ?, 'animal', NULL, 'text', NULL, 'tony'", cg)
        sql.queryForList("EXEC dbo.stp_SolutionConfigValue_Save ?, ?, 'zebra', NULL, NULL, NULL, 'tony'", prop, env)
        int col = id(sql, "EXEC dbo.stp_SolutionConfigGridColumn_Save 0, ?, 'Prod', NULL, 0, NULL", sid)
        int gg = id(sql, "EXEC dbo.stp_SolutionConfigGridGroup_Save 0, ?, 'Zoo', NULL", sid)
        int gprop = id(sql, "EXEC dbo.stp_SolutionConfigGridProperty_Save 0, ?, 'animal', NULL, 'text', NULL, 'tony'", gg)
        sql.queryForList("EXEC dbo.stp_SolutionConfigGridValue_Save ?, ?, 'zebra', NULL, NULL, NULL, NULL, 'tony'", gprop, col)
        int file = id(sql, "EXEC dbo.stp_SolutionFile_Save 0, ?, NULL, 'Zebra photo', 'zebra.jpg', 'local', NULL, 's', 'image/jpeg', 1, NULL, 'tony'", sid)
        int conv = id(sql, 'EXEC dbo.stp_SolutionConversation_Create ?, ?', sid, 'Zebra thread')
        int msg = id(sql, "EXEC dbo.stp_ConversationMessage_Save @prmConversationMessageID=0, @prmConversationID=?, @prmSender='rex', " +
            "@prmBody='where is the zebra', @prmAwaitingResponseFrom='', @prmTokensIn=0, @prmTokensOut=0, @prmDurationMs=0, @prmModelUsed='', @prmErrorText=''", conv)
        [project: p, item: item, log: log, note: note, step: step, term: term, plan: plan, qa: qa, tcase: tcase,
         prop: prop, gprop: gprop, file: file, conv: conv, msg: msg]
    }
}
