package com.echo

import com.echo.SolutionLifecycleModels.CloseOut
import com.echo.SolutionLifecycleModels.OpenThing
import com.echo.SolutionLifecycleModels.ShapeResult

import spock.lang.Unroll

/**
 * Story 5695 (Release 6): the business rules of the start and the end of a solution. Both calls check the
 * solution first, so a hidden or missing solution reads and writes nothing. A shape is one of the four codes in
 * any case and with any padding; copy needs a fromSolutionId above zero that the caller can see too, and every
 * other shape ignores it (it reaches storage as null). Every shape is made in the caller's own member code. The
 * close-out answers the open things as storage read them, a count for every group in GROUPS order (zeros
 * included) and the total.
 */
class SolutionLifecycleServiceSpec extends SolutionServiceSupport {

    static final String SHAPE_REFUSED = 'shape must be one of programming, writing, life, copy.'
    static final String FROM_REFUSED = 'Say which solution to copy the shape of (fromSolutionId).'

    SolutionLifecycleRepository repo = Mock()
    SolutionLifecycleService service = new SolutionLifecycleService(repo, access)

    private static OpenThing thing(String group, int id) {
        new OpenThing(group, group, id, 'Thing ' + id, null, null)
    }

    // ---- shapes ----

    def "shapes should answer the four shapes and reach no storage"() {

        when:
        List got = service.shapes()

        then:
        got.is(SolutionLifecycleModels.SHAPES)
        0 * repo._
        0 * solutionRepo._
    }

    // ---- access first ----

    @Unroll
    def "#action should read and write nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        if (action == 'shape') {
            service.applyShape(caller, solutionId, 'programming', null)
        } else {
            service.closeOut(caller, solutionId)
        }

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario                                  | action      | callerName | solutionId || expectedMessage
        'the client was given nothing'            | 'shape'     | 'ZED'      | 1          || 'Solution not found.'
        'the client was not given the solution'   | 'shape'     | 'REX'      | 2          || 'Solution not found.'
        'the solution does not exist'             | 'shape'     | 'TONY'     | 9          || 'Solution not found.'
        'the client was given nothing'            | 'close-out' | 'ZED'      | 1          || 'Solution not found.'
        'the client was not given the solution'   | 'close-out' | 'REX'      | 2          || 'Solution not found.'
        'the solution does not exist'             | 'close-out' | 'HOUSE'    | 9          || 'Solution not found.'
        'the solution id is zero'                 | 'close-out' | 'TONY'     | 0          || 'Solution not found.'
    }

    def "applyShape should answer not found before it looks at the shape when the solution is hidden"() {

        when:
        service.applyShape(REX, 2, 'bogus', null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Solution not found.'
        0 * repo._
    }

    // ---- applyShape: the shape ----

    @Unroll
    def "#seq. applyShape should make the #expectedShape shape as #expectedCode with no source when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        ShapeResult made = new ShapeResult(4, 1, 3, 2, 0, 0)

        when:
        ShapeResult got = service.applyShape(caller, 1, shape, fromSolutionId)

        then:
        1 * repo.applyShape(1, expectedShape, null, expectedCode) >> made
        got.is(made)

        where:
        seq | scenario                                    | callerName | shape           | fromSolutionId || expectedShape | expectedCode
        1   | 'a person names programming'                | 'TONY'     | 'programming'   | null           || 'programming' | 'tony'
        2   | 'the house names writing in upper case'     | 'HOUSE'    | 'WRITING'       | null           || 'writing'     | 'felix'
        3   | 'a client names life padded'                | 'REX'      | '  Life  '      | null           || 'life'        | 'rex'
        4   | 'a source is sent with programming'         | 'TONY'     | 'programming'   | 2              || 'programming' | 'tony'
        5   | 'a hidden source is sent with writing'      | 'REX'      | 'writing'       | 2              || 'writing'     | 'rex'
        6   | 'a missing source is sent with life'        | 'TONY'     | 'life'          | 9              || 'life'        | 'tony'
        7   | 'a zero source is sent with programming'    | 'HOUSE'    | 'Programming'   | 0              || 'programming' | 'felix'
        8   | 'a negative source is sent with writing'    | 'TONY'     | ' writing'      | -3             || 'writing'     | 'tony'
    }

    @Unroll
    def "applyShape should refuse the shape with the exact message and write nothing when it is #scenario"() {

        when:
        service.applyShape(TONY, 1, shape, 2)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario          | shape          || expectedMessage
        'null'            | null           || 'shape must be one of programming, writing, life, copy.'
        'empty'           | ''             || 'shape must be one of programming, writing, life, copy.'
        'blank'           | '   '          || 'shape must be one of programming, writing, life, copy.'
        'not a shape'     | 'design'       || 'shape must be one of programming, writing, life, copy.'
        'a near miss'     | 'copies'       || 'shape must be one of programming, writing, life, copy.'
        'two words'       | 'copy life'    || 'shape must be one of programming, writing, life, copy.'
    }

    def "applyShape should refuse the shape before it looks for a source"() {

        when:
        service.applyShape(TONY, 1, 'bogus', null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == SHAPE_REFUSED
        0 * repo._
    }

    // ---- applyShape: copy ----

    @Unroll
    def "applyShape should copy the shape of solution #fromSolutionId as #expectedCode when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        ShapeResult made = new ShapeResult(2, 2, 3, 2, 1, 1)

        when:
        ShapeResult got = service.applyShape(caller, solutionId, shape, fromSolutionId)

        then:
        1 * repo.applyShape(solutionId, 'copy', fromSolutionId, expectedCode) >> made
        got.is(made)

        where:
        scenario                                         | callerName | solutionId | shape    | fromSolutionId || expectedCode
        'a person copies a solution they can see'        | 'TONY'     | 1          | 'copy'   | 2              || 'tony'
        'the house copies one in upper case'             | 'HOUSE'    | 2          | 'COPY'   | 1              || 'felix'
        'a client copies the one solution it was given'  | 'REX'      | 1          | ' Copy ' | 1              || 'rex'
    }

    @Unroll
    def "applyShape should refuse a copy with the exact message and write nothing when the source is #scenario"() {

        when:
        service.applyShape(TONY, 1, 'copy', fromSolutionId)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario     | fromSolutionId || expectedMessage
        'not sent'   | null           || 'Say which solution to copy the shape of (fromSolutionId).'
        'zero'       | 0              || 'Say which solution to copy the shape of (fromSolutionId).'
        'negative'   | -1             || 'Say which solution to copy the shape of (fromSolutionId).'
    }

    @Unroll
    def "applyShape should answer not found and write nothing when the source to copy is #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.applyShape(caller, 1, 'copy', fromSolutionId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo._

        where:
        scenario                          | callerName | fromSolutionId || expectedMessage
        'not shared with the client'      | 'REX'      | 2              || 'Solution not found.'
        'missing'                         | 'TONY'     | 9              || 'Solution not found.'
        'missing, for the house'          | 'HOUSE'    | 9              || 'Solution not found.'
    }

    def "applyShape should pass the repository's refusal through"() {

        when:
        service.applyShape(TONY, 1, 'programming', null)

        then:
        1 * repo.applyShape(*_) >> {
            throw new SolutionRuleException('A shape is for a new, empty solution. This one has projects or topics already. Nothing was added.')
        }
        SolutionRuleException ex = thrown()
        ex.message == 'A shape is for a new, empty solution. This one has projects or topics already. Nothing was added.'
    }

    // ---- closeOut ----

    def "closeOut should count every group in order, zeros included, and answer the open things as read"() {

        given:
        List<OpenThing> open = [thing('project', 1), thing('item', 2), thing('item', 3), thing('item', 4), thing('part', 5),
                                thing('thread', 6), thing('thread', 7)]

        when:
        CloseOut got = service.closeOut(REX, 1)

        then:
        1 * repo.closeOut(1) >> open
        got.open().is(open)
        got.counts() == [project: 1, item: 3, question: 0, decision: 0, part: 1, assumption: 0, thread: 2]
        got.counts().keySet() as List == SolutionLifecycleModels.GROUPS
        got.total() == 7
    }

    @Unroll
    def "closeOut should answer every group as zero and a total of zero when nothing is open for #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        CloseOut got = service.closeOut(caller, 1)

        then:
        1 * repo.closeOut(1) >> []
        got.open() == []
        got.counts() == [project: 0, item: 0, question: 0, decision: 0, part: 0, assumption: 0, thread: 0]
        got.counts().keySet() as List == SolutionLifecycleModels.GROUPS
        got.total() == 0

        where:
        scenario     | callerName || unused
        'a person'   | 'TONY'     || null
        'the house'  | 'HOUSE'    || null
        'a client'   | 'REX'      || null
    }

    def "closeOut should count one of each group when every group has one open thing"() {

        given:
        List<OpenThing> open = [thing('project', 1), thing('item', 2), thing('question', 3), thing('decision', 4), thing('part', 5),
                                thing('assumption', 6), thing('thread', 7)]

        when:
        CloseOut got = service.closeOut(TONY, 2)

        then:
        1 * repo.closeOut(2) >> open
        got.counts() == [project: 1, item: 1, question: 1, decision: 1, part: 1, assumption: 1, thread: 1]
        got.total() == 7
    }
}
