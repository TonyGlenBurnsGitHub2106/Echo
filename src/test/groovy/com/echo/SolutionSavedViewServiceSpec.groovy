package com.echo

import com.echo.SolutionSavedViewModels.SavedView

import spock.lang.Unroll

/**
 * Story 5695 (Release 5): the business rules of a member's saved views. Listing and saving check the solution
 * first, so a hidden or missing solution reads and writes nothing. The scope defaults to the items tab and no
 * other tab is accepted; a name is trimmed, required and at most 100 characters; the filters are a JSON object
 * written as a string (it starts with { and ends with }), trimmed, at most 2,000 characters. A save answers the
 * member's views of that tab. Every call is made in the caller's own member code, so a member only ever sees,
 * saves and deletes their own; deleting what is not theirs, or id zero, is "View not found."
 */
class SolutionSavedViewServiceSpec extends SolutionServiceSupport {

    static final String SCOPE_REFUSED = 'Views can be saved for the items tab (scope=items).'
    static final String FILTER_REFUSED = 'filter must be a JSON object, as a string.'

    SolutionSavedViewRepository repo = Mock()
    SolutionSavedViewService service = new SolutionSavedViewService(repo, access)

    private static SavedView view(int id, String name) {
        new SavedView(id, 1, 'items', name, '{"hideClosed":true}', id, null, null)
    }

    /** A filter object of exactly length characters: { then x's then }. */
    private static String filterOf(int length) {
        '{' + getReplicatedString('x', length - 2) + '}'
    }

    // ---- access first ----

    @Unroll
    def "#action should read and write nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        if (action == 'list') {
            service.list(caller, solutionId, 'items')
        } else {
            service.save(caller, solutionId, 'items', 'Mine', '{}')
        }

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario                                  | action | callerName | solutionId || expectedMessage
        'the client was given nothing'            | 'list' | 'ZED'      | 1          || 'Solution not found.'
        'the client was not given the solution'   | 'list' | 'REX'      | 2          || 'Solution not found.'
        'the solution does not exist'             | 'list' | 'TONY'     | 9          || 'Solution not found.'
        'the client was given nothing'            | 'save' | 'ZED'      | 1          || 'Solution not found.'
        'the client was not given the solution'   | 'save' | 'REX'      | 2          || 'Solution not found.'
        'the solution does not exist'             | 'save' | 'HOUSE'    | 9          || 'Solution not found.'
        'the solution id is zero'                 | 'save' | 'TONY'     | 0          || 'Solution not found.'
    }

    def "save should answer not found before it looks at a bad request when the solution is hidden"() {

        when:
        service.save(REX, 2, 'notes', '', 'x')

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Solution not found.'
        0 * repo._
    }

    // ---- list ----

    @Unroll
    def "list should read the #expectedScope views of #expectedCode when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        List<SavedView> rows = [view(4, 'Mine')]

        when:
        List<SavedView> got = service.list(caller, 1, scope)

        then:
        1 * repo.list(1, expectedCode, expectedScope) >> rows
        got.is(rows)

        where:
        scenario                                 | callerName | scope       || expectedCode | expectedScope
        'a person names the items tab'           | 'TONY'     | 'items'     || 'tony'       | 'items'
        'the house names no tab'                 | 'HOUSE'    | null        || 'felix'      | 'items'
        'a client sends an empty tab'            | 'REX'      | ''          || 'rex'        | 'items'
        'a person sends a blank tab'             | 'TONY'     | '   '       || 'tony'       | 'items'
        'a person sends it padded in upper case' | 'TONY'     | ' ITEMS '   || 'tony'       | 'items'
    }

    @Unroll
    def "list should refuse the tab and read nothing when it is #scenario"() {

        when:
        service.list(TONY, 1, scope)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario          | scope      || expectedMessage
        'the notes tab'   | 'notes'    || 'Views can be saved for the items tab (scope=items).'
        'the log tab'     | 'log'      || 'Views can be saved for the items tab (scope=items).'
        'a near miss'     | 'item'     || 'Views can be saved for the items tab (scope=items).'
    }

    // ---- save ----

    @Unroll
    def "save should save the view as #expectedCode and answer the views of the tab when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        List<SavedView> rows = [view(4, 'Mine'), view(5, 'Theirs')]

        when:
        List<SavedView> got = service.save(caller, 1, scope, 'Mine', '{"hideClosed":true}')

        then:
        1 * repo.save(1, expectedCode, 'items', 'Mine', '{"hideClosed":true}') >> 4

        then:
        1 * repo.list(1, expectedCode, 'items') >> rows
        got.is(rows)

        where:
        scenario                  | callerName | scope    || expectedCode
        'a person saves it'       | 'TONY'     | 'items'  || 'tony'
        'the house saves it'      | 'HOUSE'    | null     || 'felix'
        'a client saves it'       | 'REX'      | 'Items'  || 'rex'
    }

    @Unroll
    def "save should pass the name as '#expectedName' and the filter as '#expectedFilter' when #scenario"() {

        when:
        service.save(TONY, 1, 'items', name, filter)

        then:
        1 * repo.save(1, 'tony', 'items', expectedName, expectedFilter) >> 4
        1 * repo.list(1, 'tony', 'items') >> []

        where:
        scenario                         | name                | filter                      || expectedName  | expectedFilter
        'both are as wanted'             | 'My open work'      | '{"assignee":"tony"}'       || 'My open work' | '{"assignee":"tony"}'
        'both are padded'                | '  My open work  '  | '  {"assignee":"tony"}  '   || 'My open work' | '{"assignee":"tony"}'
        'the filter is the empty object' | 'All'               | '{}'                        || 'All'          | '{}'
        'the filter is not checked as JSON inside' | 'Odd'     | '{not json}'                || 'Odd'          | '{not json}'
    }

    @Unroll
    def "save should refuse the tab and write nothing when it is #scenario"() {

        when:
        service.save(TONY, 1, scope, 'Mine', '{}')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario          | scope      || expectedMessage
        'the notes tab'   | 'notes'    || 'Views can be saved for the items tab (scope=items).'
        'the files tab'   | 'files'    || 'Views can be saved for the items tab (scope=items).'
    }

    @Unroll
    def "save should refuse the name and write nothing when it is #scenario"() {

        when:
        service.save(TONY, 1, 'items', name, '{}')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario    | name   || expectedMessage
        'null'      | null   || 'A view needs a name.'
        'empty'     | ''     || 'A view needs a name.'
        'blank'     | '   '  || 'A view needs a name.'
    }

    def "save should take a name of exactly 100 characters"() {

        given:
        String name = textOf(100)

        when:
        service.save(TONY, 1, 'items', name, '{}')

        then:
        1 * repo.save(1, 'tony', 'items', name, '{}') >> 4
        1 * repo.list(1, 'tony', 'items') >> []
    }

    def "save should refuse a name of 101 characters and write nothing"() {

        when:
        service.save(TONY, 1, 'items', textOf(101), '{}')

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "A view's name can be at most 100 characters."
        0 * repo._
    }

    @Unroll
    def "save should refuse the filter and write nothing when it is #scenario"() {

        when:
        service.save(TONY, 1, 'items', 'Mine', filter)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario                      | filter          || expectedMessage
        'null'                        | null            || 'filter must be a JSON object, as a string.'
        'empty'                       | ''              || 'filter must be a JSON object, as a string.'
        'a word'                      | 'mine'          || 'filter must be a JSON object, as a string.'
        'a list'                      | '[1,2]'         || 'filter must be a JSON object, as a string.'
        'missing its closing brace'   | '{"a":1'        || 'filter must be a JSON object, as a string.'
        'missing its opening brace'   | '"a":1}'        || 'filter must be a JSON object, as a string.'
        'a quoted object'             | '"{}"'          || 'filter must be a JSON object, as a string.'
    }

    def "save should take filters of exactly 2,000 characters"() {

        given:
        String filter = filterOf(2000)

        when:
        service.save(TONY, 1, 'items', 'Mine', filter)

        then:
        1 * repo.save(1, 'tony', 'items', 'Mine', filter) >> 4
        1 * repo.list(1, 'tony', 'items') >> []
        filter.length() == 2000
    }

    def "save should refuse filters of 2,001 characters and write nothing"() {

        when:
        service.save(TONY, 1, 'items', 'Mine', filterOf(2001))

        then:
        IllegalArgumentException ex = thrown()
        ex.message == "A view's filters can be at most 2,000 characters."
        0 * repo._
    }

    def "save should check the tab, then the name, then the filter when all three are wrong"() {

        when:
        service.save(TONY, 1, 'notes', '', 'x')

        then:
        IllegalArgumentException first = thrown()
        first.message == SCOPE_REFUSED

        when:
        service.save(TONY, 1, 'items', '', 'x')

        then:
        IllegalArgumentException second = thrown()
        second.message == 'A view needs a name.'

        when:
        service.save(TONY, 1, 'items', 'Mine', 'x')

        then:
        IllegalArgumentException third = thrown()
        third.message == FILTER_REFUSED
        0 * repo._
    }

    def "save should pass the repository's refusal through and read no list"() {

        when:
        service.save(TONY, 1, 'items', 'One more', '{}')

        then:
        1 * repo.save(*_) >> { throw new SolutionRuleException('You have 30 saved views here already. Delete one first. Nothing was saved.') }
        SolutionRuleException ex = thrown()
        ex.message == 'You have 30 saved views here already. Delete one first. Nothing was saved.'
        0 * repo.list(*_)
    }

    // ---- delete ----

    @Unroll
    def "delete should delete only #expectedCode's own view when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.delete(caller, 4)

        then:
        1 * repo.delete(4, expectedCode) >> true
        0 * solutionRepo.findSolution(_)

        where:
        scenario                   | callerName || expectedCode
        'a person deletes it'      | 'TONY'     || 'tony'
        'the house deletes it'     | 'HOUSE'    || 'felix'
        'a client deletes it'      | 'REX'      || 'rex'
        'a client with nothing'    | 'ZED'      || 'zed'
    }

    def "delete should answer View not found when the repository deleted nothing"() {

        when:
        service.delete(REX, 4)

        then:
        1 * repo.delete(4, 'rex') >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'View not found.'
    }

    @Unroll
    def "delete should answer View not found and not reach the repository when the id is #scenario"() {

        when:
        service.delete(TONY, viewId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario     | viewId || expectedMessage
        'zero'       | 0      || 'View not found.'
        'negative'   | -4     || 'View not found.'
    }
}
