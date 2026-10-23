package com.echo

import com.echo.SolutionTermModels.Term
import com.echo.SolutionTermModels.TermDraft
import com.echo.SolutionTermModels.TermFilter
import com.echo.SolutionTermModels.TermLookup
import com.echo.SolutionTermModels.TermLookups
import com.echo.SolutionTermModels.TermPage

import spock.lang.PendingFeature
import spock.lang.Unroll

/**
 * Story 5653: the Terms tab's business rules -- access on every call (a term is reached through its own
 * solution, never the one a caller names), saves, favorites, deletes, and the type / category lists.
 */
class SolutionTermServiceSpec extends SolutionServiceSupport {

    SolutionTermRepository terms = Mock()
    SolutionTermService service = new SolutionTermService(terms, access)

    Map<Integer, Term> rows = [5: term(5, 1), 6: term(6, 2)]

    def setup() {
        terms.find(_) >> { int id -> rows[id] }
        terms.types(_) >> { int s -> [new TermLookup(1, 'Acronym', 2)] }
        terms.categories(_) >> { int s -> [new TermLookup(2, 'Web', 1)] }
    }

    private static Term term(int id, int solutionId) {
        Term.of([SolutionTermID: id, SolutionID: solutionId, Term: 'T' + id, Definition: 'D' + id])
    }

    private static TermFilter filter(int solutionId) {
        new TermFilter(solutionId, null, null, null, false, null, false, 10, 20)
    }

    /** Runs one named lookup call against a hidden solution -- the calls a data table can name. */
    private static TermLookups lookupCall(SolutionTermService s, String action) {
        switch (action) {
            case 'lookups':      return s.lookups(REX, 2)
            case 'renameLookup': return s.renameLookup(REX, 2, 'type', 1, 'x')
            case 'deleteLookup': return s.deleteLookup(ZED, 1, 'category', 1)
            default: throw new IllegalStateException(action)
        }
    }

    def "page should list the terms with their counts and paging when the solution is visible"() {

        given:
        TermFilter f = filter(1)

        when:
        TermPage p = service.page(REX, f)

        then:
        1 * terms.counts(f) >> [total: 42, all: 50]
        1 * terms.list(f) >> [rows[5]]
        p.terms()*.id() == [5]
        p.counts() == [total: 42, all: 50]
        p.offset() == 10
        p.pageSize() == 20
        p.total() == 42
    }

    def "page should answer a total of zero when there is no total counter"() {

        when:
        TermPage p = service.page(TONY, filter(1))

        then:
        1 * terms.counts(_) >> [:]
        1 * terms.list(_) >> []
        p.total() == 0
    }

    @Unroll
    def "page should read nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.page(caller, filter(solutionId))

        then:
        thrown(SolutionNotFoundException)
        0 * terms.counts(_)
        0 * terms.list(_)

        where:
        scenario                         | callerName | solutionId || unused
        'the id is zero'                 | 'TONY'     | 0          || null
        'the solution does not exist'    | 'TONY'     | 9          || null
        'the client was not given it'    | 'REX'      | 2          || null
        'the client was given nothing'   | 'ZED'      | 1          || null
    }

    def "get should answer the term when the caller may see it"() {

        expect:
        service.get(TONY, 6).id() == 6
        service.get(REX, 5).solutionId() == 1
    }

    @Unroll
    def "get should throw not found when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.get(caller, termId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                           | callerName | termId || expectedMessage
        'the id is zero'                   | 'TONY'     | 0      || 'Term not found.'
        'the id is negative'               | 'TONY'     | -1     || 'Term not found.'
        'the term does not exist'          | 'TONY'     | 99     || 'Term not found.'
        'the client was not given it'      | 'REX'      | 6      || 'Solution not found.'
        'the client was given nothing'     | 'ZED'      | 5      || 'Solution not found.'
    }

    def "get should not reach the repository when the id is zero"() {

        when:
        service.get(TONY, 0)

        then:
        thrown(SolutionNotFoundException)
        0 * terms.find(_)
    }

    @Unroll
    def "save should add the term attributed to #expectedCode when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        TermDraft draft = new TermDraft(0, 1, 'API', 'Interface', 'Acronym', null, null, ['Web'])

        when:
        Term t = service.save(caller, draft)

        then:
        1 * terms.save(draft, expectedCode) >> 5
        t.id() == 5

        where:
        scenario               | callerName || expectedCode
        'a person adds it'     | 'TONY'     || 'tony'
        'the house adds it'    | 'HOUSE'    || 'felix'
        'a client adds it'     | 'REX'      || 'rex'
    }

    @Unroll
    def "save should save nothing when an add targets #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.save(caller, new TermDraft(0, solutionId, 'x', 'y', null, null, null, []))

        then:
        thrown(SolutionNotFoundException)
        0 * terms.save(*_)

        where:
        scenario                            | callerName | solutionId || unused
        'solution zero'                     | 'TONY'     | 0          || null
        'a solution the client was not given' | 'REX'    | 2          || null
        'a solution of a client given none' | 'ZED'      | 1          || null
    }

    def "save should check the term's own solution when an edit names another solution"() {

        when: 'rex edits a term of solution 2 while naming solution 1, which he can see'
        service.save(REX, new TermDraft(6, 1, 'x', null, null, null, null, null))

        then:
        thrown(SolutionNotFoundException)
        0 * terms.save(*_)
    }

    def "save should edit the term when the draft's solution is left at zero"() {

        given:
        TermDraft draft = new TermDraft(5, 0, null, 'New', null, null, null, null)

        when:
        Term t = service.save(REX, draft)

        then:
        1 * terms.save(draft, 'rex') >> 5
        t.id() == 5
    }

    def "save should throw not found when an edit targets a missing term"() {

        when:
        service.save(TONY, new TermDraft(77, 1, 'x', 'y', null, null, null, null))

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Term not found.'
        0 * terms.save(*_)
    }

    @Unroll
    def "save should throw not found when the repository answers a #scenario id"() {

        when:
        service.save(TONY, new TermDraft(0, 1, 'x', 'y', null, null, null, []))

        then:
        1 * terms.save(*_) >> savedId
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario      | savedId || expectedMessage
        'zero'        | 0       || 'Term not found.'
        'negative'    | -1      || 'Term not found.'
    }

    def "save should pass a refusal through when the repository refuses a rule"() {

        when:
        service.save(TONY, new TermDraft(0, 1, 'API', 'y', null, null, null, []))

        then:
        1 * terms.save(*_) >> { throw new SolutionRuleException('That term is already in this solution. Nothing was saved.') }
        SolutionRuleException ex = thrown()
        ex.message == 'That term is already in this solution. Nothing was saved.'
    }

    @Unroll
    def "favorite should set #favorite attributed to the caller when #scenario"() {

        when:
        Term t = service.favorite(REX, 5, favorite)

        then:
        1 * terms.setFavorite(5, favorite, 'rex') >> true
        t.id() == expectedId

        where:
        scenario               | favorite || expectedId
        'a term is marked'     | true     || 5
        'a term is unmarked'   | false    || 5
    }

    def "favorite should never write a hidden term and throw not found when nothing changed"() {

        when:
        service.favorite(REX, 6, true)

        then:
        thrown(SolutionNotFoundException)
        0 * terms.setFavorite(*_)

        when:
        service.favorite(TONY, 5, true)

        then:
        1 * terms.setFavorite(5, true, 'tony') >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Term not found.'
    }

    def "delete should delete a visible term, refuse a hidden one and throw not found when nothing was removed"() {

        when:
        service.delete(REX, 5)

        then:
        1 * terms.delete(5) >> true

        when:
        service.delete(REX, 6)

        then:
        thrown(SolutionNotFoundException)
        0 * terms.delete(_)

        when:
        service.delete(TONY, 6)

        then:
        1 * terms.delete(6) >> false
        thrown(SolutionNotFoundException)
    }

    def "lookups should answer the solution's types and categories when the solution is visible"() {

        when:
        TermLookups l = service.lookups(REX, 1)

        then:
        l.types()*.name() == ['Acronym']
        l.categories()*.name() == ['Web']
    }

    def "renameLookup and deleteLookup should act on the named solution and answer the fresh lists when visible"() {

        when:
        TermLookups a = service.renameLookup(REX, 1, 'type', 4, 'Abbreviation')
        TermLookups b = service.deleteLookup(REX, 1, 'category', 3)

        then:
        1 * terms.renameLookup('type', 1, 4, 'Abbreviation')
        1 * terms.deleteLookup('category', 1, 3) >> true
        a.types()*.id() == [1]
        b.categories()*.id() == [2]
    }

    @Unroll
    def "#action should touch nothing when the solution is hidden while #scenario"() {

        when:
        lookupCall(service, action)

        then:
        thrown(SolutionNotFoundException)
        0 * terms.renameLookup(*_)
        0 * terms.deleteLookup(*_)
        0 * terms.types(_)
        0 * terms.categories(_)

        where:
        scenario                         | action         || unused
        'reading the lists'              | 'lookups'      || null
        'renaming a type'                | 'renameLookup' || null
        'deleting a category'            | 'deleteLookup' || null
    }

    def "renameLookup should pass a refusal through when the type is not this solution's"() {

        when:
        service.renameLookup(TONY, 1, 'type', 99, 'x')

        then:
        1 * terms.renameLookup('type', 1, 99, 'x') >> { throw new SolutionRuleException('That type is not in this solution.') }
        thrown(SolutionRuleException)
    }

    @PendingFeature(reason = 'BUG: SolutionTermService.deleteLookup ignores the repository result, so deleting a type or category that does not exist (or is another solution\'s) answers 200 instead of not found like every other delete in the family')
    def "deleteLookup should throw not found when nothing was removed"() {

        when:
        service.deleteLookup(TONY, 1, 'type', 99)

        then:
        1 * terms.deleteLookup('type', 1, 99) >> false
        thrown(SolutionNotFoundException)
    }
}
