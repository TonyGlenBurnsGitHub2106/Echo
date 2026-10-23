package com.echo

import com.echo.SolutionTermModels.Term
import com.echo.SolutionTermModels.TermDraft
import com.echo.SolutionTermModels.TermFilter
import com.echo.SolutionTermModels.TermLookup

import spock.lang.Unroll

// Story 5653: JdbcSolutionTermRepository against a real EchoTest_ database (stp_SolutionTerm*, stp_SolutionTermType*,
// stp_SolutionTermCategory*).
class JdbcSolutionTermRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionTermRepository repo
    int sid

    def setup() {
        repo = new JdbcSolutionTermRepository(dataSource())
        sid = newSolution()
    }

    def "save should store the term with its type and categories so find reads every field back when a term is added"() {

        when:
        int id = repo.save(new TermDraft(0, sid, "  EOB\r\nfile ", '  Explanation of benefits  ', ' Acronym ', ' The manual ',
            ' Some notes ', ['Claims', ' Billing ', 'claims', '']), 'Rex')
        Term t = repo.find(id)

        then:
        t.id() == id
        t.solutionId() == sid
        t.term() == 'EOB  file'
        t.definition() == 'Explanation of benefits'
        !t.truncated()
        t.typeId() != null
        t.type() == 'Acronym'
        t.source() == 'The manual'
        t.notes() == 'Some notes'
        t.hasNotes()
        !t.favorite()
        t.categories() == ['Billing', 'Claims']
        t.authorCode() == 'rex'
        t.authorName() == 'Rex'
        t.modifiedByCode() == 'rex'
        t.createdDate() != null
        scalar('SELECT NormalizedTerm FROM dbo.SolutionTerm WHERE SolutionTermID = ?', id) == 'eob file'
    }

    def "find should return null when the id is unknown"() {

        expect:
        repo.find(-1) == null
    }

    def "save should keep null fields, clear empty ones and remove every category when an edit sends an empty list"() {

        given:
        int id = add(repo, sid, [term: 'Word', definition: 'Def', type: 'Kind', cats: ['A', 'B'], source: 'Src', notes: 'Notes'])

        when:
        repo.save(new TermDraft(id, 0, null, 'New def', null, null, null, null), 'tony')
        Term t = repo.find(id)

        then:
        t.term() == 'Word'
        t.definition() == 'New def'
        t.type() == 'Kind'
        t.source() == 'Src'
        t.notes() == 'Notes'
        t.categories() == ['A', 'B']
        t.authorCode() == 'rex'
        t.modifiedByCode() == 'tony'

        when:
        repo.save(new TermDraft(id, 0, null, null, '', '', '', []), 'tony')
        t = repo.find(id)

        then:
        t.type() == null
        t.typeId() == null
        t.source() == null
        t.notes() == null
        !t.hasNotes()
        t.categories() == []
    }

    def "save should keep the term in its own solution when an edit names another solution"() {

        given:
        int id = add(repo, sid, [term: 'Stay'])
        int other = newSolution()

        when:
        repo.save(new TermDraft(id, other, 'Stay put', null, null, null, null, null), 'tony')

        then:
        repo.find(id).solutionId() == sid
        repo.find(id).term() == 'Stay put'
    }

    def "save should refuse a duplicate ignoring case and spacing but allow it in another solution"() {

        given:
        add(repo, sid, [term: 'Claim  Batch'])

        when:
        add(repo, sid, [term: 'claim batch'])

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'This solution already has the term "Claim  Batch". Nothing was saved.'
        ex.message.endsWith('Nothing was saved.')

        expect:
        add(repo, newSolution(), [term: 'claim batch']) > 0
    }

    def "save should keep the percent sign in the refusal when the duplicate term holds one"() {

        given:
        add(repo, sid, [term: '100% done'])

        expect:
        ruleOf { add(repo, sid, [term: '100%   DONE']) }.message == 'This solution already has the term "100% done". Nothing was saved.'
    }

    @Unroll
    def "#seq. save should refuse and write nothing when #scenario"() {

        given:
        int existing = add(repo, sid, [term: 'Existing'])
        int before = count('SELECT COUNT(*) FROM dbo.SolutionTerm')
        int id = target == 'existing' ? existing : (target == 'missing' ? 987654321 : 0)
        int solution = target == 'missing-solution' ? -1 : sid
        String termText = termLength > 0 ? getReplicatedString('t', termLength) : term
        String typeText = typeLength > 0 ? getReplicatedString('k', typeLength) : null
        String sourceText = sourceLength > 0 ? getReplicatedString('s', sourceLength) : null
        List<String> cats = categoryLength > 0 ? [getReplicatedString('c', categoryLength)] : null

        when:
        repo.save(new TermDraft(id, solution, termText, definition, typeText, sourceText, null, cats), 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)
        count('SELECT COUNT(*) FROM dbo.SolutionTerm') == before

        where:
        seq | scenario                               | target             | term | definition | termLength | typeLength | sourceLength | categoryLength || message
        1   | "the term to edit no longer exists"    | "missing"          | "x"  | "y"        | 0          | 0          | 0            | 0              || "term no longer exists"
        2   | "the solution does not exist"          | "missing-solution" | "x"  | "y"        | 0          | 0          | 0            | 0              || "Nothing was added."
        3   | "a new term is blank"                  | "new"              | "  " | "y"        | 0          | 0          | 0            | 0              || "needs the word or phrase"
        4   | "a new definition is blank"            | "new"              | "x"  | " "        | 0          | 0          | 0            | 0              || "needs a definition"
        5   | "an edit blanks the term"              | "existing"         | " "  | null       | 0          | 0          | 0            | 0              || "cannot be blank"
        6   | "an edit blanks the definition"        | "existing"         | null | " "        | 0          | 0          | 0            | 0              || "definition cannot be blank"
        7   | "the term is over 400 characters"      | "new"              | null | "y"        | 401        | 0          | 0            | 0              || "at most 400"
        8   | "the type is over 200 characters"      | "new"              | "x"  | "y"        | 0          | 201        | 0            | 0              || "type name can be at most 200"
        9   | "the source is over 500 characters"    | "new"              | "x"  | "y"        | 0          | 0          | 501          | 0              || "source can be at most 500"
        10  | "a category is over 200 characters"    | "new"              | "x"  | "y"        | 0          | 0          | 0            | 201            || "category name can be at most 200"
    }

    def "types and categories should be per solution, found or created and listed with usage when terms share them"() {

        given:
        add(repo, sid, [term: 'a', type: 'Acronym', cats: ['Claims']])
        add(repo, sid, [term: 'b', type: 'acronym', cats: ['claims', 'Money']])
        add(repo, sid, [term: 'c', type: 'Concept'])
        add(repo, newSolution(), [term: 'z', type: 'Acronym', cats: ['Claims']])

        when:
        List<TermLookup> types = repo.types(sid)
        List<TermLookup> cats = repo.categories(sid)

        then:
        types*.name() == ['Acronym', 'Concept']
        types*.usageCount() == [2, 1]
        cats*.name() == ['Claims', 'Money']
        cats*.usageCount() == [2, 1]
    }

    def "list should group by type with the untyped last and carry a 600-character preview without notes when grouped by type"() {

        given:
        int none = add(repo, sid, [term: 'Zeta'])
        int b = add(repo, sid, [term: 'Beta', definition: getReplicatedString('x', 700), type: 'Acronym', notes: 'secret notes'])
        int a = add(repo, sid, [term: 'Alpha', type: 'Acronym'])
        int c = add(repo, sid, [term: 'Gamma', type: 'Concept'])

        when:
        List<Term> list = repo.list(filter(sid, [:]))

        then:
        list*.id() == [a, b, c, none]
        list*.groupLabel() == ['Acronym', 'Acronym', 'Concept', '(No type)']
        list[3].groupKey() == 'type:0'
        list[1].definition().length() == 600
        list[1].truncated()
        list[1].notes() == null
        list[1].hasNotes()
    }

    def "list should show a term once per category with the uncategorized last when grouped by category"() {

        given:
        int both = add(repo, sid, [term: 'Both', cats: ['Red', 'Blue']])
        int none = add(repo, sid, [term: 'None'])

        when:
        List<Term> list = repo.list(filter(sid, [group: 'category']))

        then:
        list*.id() == [both, both, none]
        list*.groupLabel() == ['Blue', 'Red', '(Uncategorized)']
    }

    @Unroll
    def "list should return #expected when #scenario"() {

        given:
        Map<String, Integer> ids = [Bravo: add(repo, sid, [term: 'Bravo']), Alpha: add(repo, sid, [term: 'Alpha']),
                                    Charlie: add(repo, sid, [term: 'Charlie'])]

        when:
        List<Integer> got = repo.list(filter(sid, [group: 'none', reverse: reverse, offset: offset, size: size]))*.id()

        then:
        got == expected.split(',').collect { String n -> ids[n] }

        where:
        scenario                          | reverse | offset | size || expected
        "not grouped"                     | false   | 0      | 50   || "Alpha,Bravo,Charlie"
        "not grouped and reversed"        | true    | 0      | 50   || "Charlie,Bravo,Alpha"
        "the second row alone is asked"   | false   | 1      | 1    || "Bravo"
    }

    def "list should match term, definition, source, notes, type and category with LIKE wildcards literal when searching"() {

        given:
        int byTerm = add(repo, sid, [term: 'Adjudication'])
        int byDef = add(repo, sid, [term: 'X1', definition: 'the adjudication step'])
        int bySource = add(repo, sid, [term: 'X2', source: 'Adjudication guide'])
        int byNotes = add(repo, sid, [term: 'X3', notes: 'see adjudication'])
        int byType = add(repo, sid, [term: 'X4', type: 'Adjudication terms'])
        int byCat = add(repo, sid, [term: 'X5', cats: ['Adjudication']])
        add(repo, sid, [term: 'Unrelated'])
        int pct = add(repo, sid, [term: '50% rule'])
        add(repo, sid, [term: '500 rule'])

        expect:
        repo.list(filter(sid, [search: 'ADJUDICATION', group: 'none']))*.id() as Set == [byTerm, byDef, bySource, byNotes, byType, byCat] as Set
        repo.list(filter(sid, [search: '0%', group: 'none']))*.id() == [pct]
        repo.list(filter(sid, [search: '_0', group: 'none'])).isEmpty()
    }

    def "list and counts should filter by type, category and favorites and agree when terms are mixed"() {

        given:
        int a = add(repo, sid, [term: 'A', type: 'Kind', cats: ['Cat']])
        int b = add(repo, sid, [term: 'B', type: 'Kind'])
        add(repo, sid, [term: 'C'])
        repo.setFavorite(a, true, 'tony')
        int kind = repo.types(sid)[0].id()
        int cat = repo.categories(sid)[0].id()

        expect:
        repo.list(filter(sid, [type: kind, group: 'none']))*.id() == [a, b]
        repo.list(filter(sid, [category: cat, group: 'none']))*.id() == [a]
        repo.list(filter(sid, [fav: true, group: 'none']))*.id() == [a]
        repo.counts(filter(sid, [:])) == ['total': 3, ('group:type:' + kind): 2, 'group:type:0': 1, 'all': 3, 'favorites': 1, 'types': 1, 'categories': 1]
        repo.counts(filter(sid, [group: 'category'])) == ['total': 3, ('group:category:' + cat): 1, 'group:category:0': 2, 'all': 3, 'favorites': 1, 'types': 1, 'categories': 1]
        repo.counts(filter(sid, [fav: true, group: 'none'])) == ['total': 1, 'all': 3, 'favorites': 1, 'types': 1, 'categories': 1]
        repo.counts(filter(sid, [search: 'nothing-matches'])).total == 0
    }

    def "setFavorite should star and unstar and record who, answering false when the id is unknown"() {

        given:
        int id = add(repo, sid, [term: 'Fav'])

        expect:
        repo.setFavorite(id, true, 'Tony')
        repo.find(id).favorite()
        repo.find(id).modifiedByCode() == 'tony'
        repo.setFavorite(id, false, null)
        !repo.find(id).favorite()
        repo.find(id).modifiedByCode() == 'tony'
        !repo.setFavorite(-1, true, 'tony')
    }

    def "delete should remove the term and its category links and audit both when the term has a category"() {

        given:
        int id = add(repo, sid, [term: 'Gone', cats: ['Cat']])

        expect:
        repo.delete(id)
        repo.find(id) == null
        count('SELECT COUNT(*) FROM dbo.SolutionTermCategoryXRef WHERE SolutionTermID = ?', id) == 0
        repo.categories(sid)[0].usageCount() == 0
        logRows('SolutionTerm', 'SolutionTermID', id).last().DeletedDate != null
        count('SELECT COUNT(*) FROM dbo.SolutionTermCategoryXRef_LOG WHERE SolutionTermID = ? AND DeletedDate IS NOT NULL', id) == 1
        !repo.delete(id)
    }

    def "renameLookup should rename a type or category of this solution only and refuse blanks, long names and duplicates"() {

        given:
        add(repo, sid, [term: 'a', type: 'Old type', cats: ['Old cat']])
        add(repo, sid, [term: 'b', type: 'Other type', cats: ['Other cat']])
        int type = repo.types(sid).find { it.name() == 'Old type' }.id()
        int cat = repo.categories(sid).find { it.name() == 'Old cat' }.id()
        int otherSolution = newSolution()

        when:
        repo.renameLookup('type', sid, type, ' New type ')
        repo.renameLookup('category', sid, cat, 'New cat')

        then:
        repo.types(sid)*.name().contains('New type')
        repo.categories(sid)*.name().contains('New cat')

        expect:
        ruleOf { repo.renameLookup('type', otherSolution, type, 'Hijack') }.message.contains('not part of this solution')
        ruleOf { repo.renameLookup('category', otherSolution, cat, 'Hijack') }.message.contains('not part of this solution')
        ruleOf { repo.renameLookup('type', sid, type, ' ') }.message.contains('needs a name')
        ruleOf { repo.renameLookup('category', sid, cat, getReplicatedString('x', 201)) }.message.contains('at most 200')
        ruleOf { repo.renameLookup('type', sid, type, 'OTHER TYPE') }.message.contains('already has a type')
        ruleOf { repo.renameLookup('category', sid, cat, 'other cat') }.message.contains('already has a category')
    }

    def "deleteLookup should remove a type or a category within the solution only when asked"() {

        given:
        int t = add(repo, sid, [term: 'a', type: 'Kind', cats: ['Cat']])
        int type = repo.types(sid)[0].id()
        int cat = repo.categories(sid)[0].id()

        expect:
        ruleOf { repo.deleteLookup('type', newSolution(), type) }.message.endsWith('Nothing was removed.')
        repo.deleteLookup('type', sid, type)
        repo.find(t).type() == null
        repo.types(sid).isEmpty()
        repo.deleteLookup('category', sid, cat)
        repo.find(t).categories() == []
        repo.categories(sid).isEmpty()
        ruleOf { repo.deleteLookup('category', sid, cat) }.message.endsWith('Nothing was removed.')
    }

    @Unroll
    def "renameLookup and deleteLookup should refuse before any SQL when the kind is #scenario"() {

        when:
        if (rename) repo.renameLookup(kind, sid, 1, 'n') else repo.deleteLookup(kind, sid, 1)

        then:
        thrown(IllegalArgumentException)

        where:
        scenario                          | kind                       | rename || _
        "an injection attempt on rename"  | "Solution; DROP TABLE x"   | true   || _
        "an unknown kind on delete"       | "member"                   | false  || _
    }

    def "save and setFavorite should write SolutionTerm_LOG rows when a term is added, edited and starred"() {

        when:
        int id = add(repo, sid, [term: 'Logged'])
        repo.save(new TermDraft(id, 0, null, 'changed', null, null, null, null), 'tony')
        repo.setFavorite(id, true, 'tony')
        List<Map<String, Object>> log = logRows('SolutionTerm', 'SolutionTermID', id)

        then:
        log*.IsInsertion == [true, false, false]
        log[1].Definition == 'changed'
        log[2].IsFavorite == true
    }

    private static int add(JdbcSolutionTermRepository repo, int solutionId, Map m) {
        repo.save(new TermDraft(0, solutionId, m.term as String, (m.definition ?: 'Means something') as String,
            m.type as String, m.source as String, m.notes as String, m.cats as List<String>), 'rex')
    }

    private static TermFilter filter(int solutionId, Map m) {
        new TermFilter(solutionId, m.search as String, m.type as Integer, m.category as Integer, (m.fav ?: false) as boolean,
            (m.group ?: 'type') as String, (m.reverse ?: false) as boolean, (m.offset ?: 0) as int, (m.size ?: 50) as int)
    }
}
