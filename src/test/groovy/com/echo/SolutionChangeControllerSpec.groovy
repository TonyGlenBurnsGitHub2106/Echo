package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import com.echo.SolutionChangeModels.BulkResult
import com.echo.SolutionChangeModels.Promoted
import com.echo.SolutionChangeModels.Provenance

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5695 (Release 5): changing many items at once and making one record into another, over HTTP -- every
 * endpoint of SolutionChangeController, who may call it (a person, the house, or an agent the solution is
 * shared with), the path, body and query fields that become the storage call, each caller's own member code,
 * and how each refusal maps to a status (400 malformed, 404 not found or not shared, 409 a rule of the procs,
 * passed through word for word). The real SolutionChangeService and access fence run; only storage is mocked.
 */
@WebMvcTest(SolutionChangeController)
class SolutionChangeControllerSpec extends ConfigWebSupport {

    static final String BULK = '{"itemIds":[3,1,3,2],"field":"Status","value":" done ","reason":" The 837 path is dead. "}'
    static final String PROMOTE = '{"sourceKind":"message","sourceId":9012,"targetKind":"item","projectId":41,"title":"Parse the 835"}'

    @SpringBean SolutionChangeService service = Mock()

    SolutionChangeRepository repo = Mock()
    SolutionChangeService real

    def setup() {
        real = new SolutionChangeService(repo, access)
        service.bulkChange(*_) >> { a -> real.bulkChange(*a) }
        service.promote(*_) >> { a -> real.promote(*a) }
        service.provenance(*_) >> { a -> real.provenance(*a) }
        repo.bulkChange(*_) >> new BulkResult(310, 3)
        repo.promote(*_) >> new Promoted('item', 1200)
        repo.provenance(*_) >> [new Provenance('from', 'message', 9012, 'The partner app writes the 837', 44,
                                               getLocalDateTime('11/04/2026 10:15'), 'tony', 'Tony')]
    }

    // ---- who may call ----

    @Unroll
    def "#scenario should answer 401 and touch nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * service._
        0 * repo._
        r.andExpect(status().is(expectedStatus))

        where:
        scenario          | method | url                                                    | body    || expectedStatus
        'a bulk change'   | 'POST' | '/api/solutions/1/items/bulk'                          | BULK    || 401
        'a promotion'     | 'POST' | '/api/solutions/1/promote'                             | PROMOTE || 401
        'provenance'      | 'GET'  | '/api/solutions/1/provenance?kind=item&recordId=1200'  | null    || 401
    }

    @Unroll
    def "#scenario should answer 200 when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))
        }

        where:
        scenario          | method | url                                                    | body    || path              | expectedValue
        'a bulk change'   | 'POST' | '/api/solutions/1/items/bulk'                          | BULK    || '$.logId'         | 310
        'a promotion'     | 'POST' | '/api/solutions/1/promote'                             | PROMOTE || '$.id'            | 1200
        'provenance'      | 'GET'  | '/api/solutions/1/provenance?kind=item&recordId=1200'  | null    || '$[0].direction'  | 'from'
    }

    @Unroll
    def "#seq. #scenario should answer 404 and touch no storage when #reason"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * repo._

        where:
        seq | scenario          | reason                                  | who    | method | url                                                    | body    || expectedStatus
        1   | 'a bulk change'   | 'the agent was given nothing'           | 'zed'  | 'POST' | '/api/solutions/1/items/bulk'                          | BULK    || 404
        2   | 'a promotion'     | 'the agent was given nothing'           | 'zed'  | 'POST' | '/api/solutions/1/promote'                             | PROMOTE || 404
        3   | 'provenance'      | 'the agent was given nothing'           | 'zed'  | 'GET'  | '/api/solutions/1/provenance?kind=item&recordId=1200'  | null    || 404
        4   | 'a bulk change'   | 'the agent was not given the solution'  | 'rex'  | 'POST' | '/api/solutions/2/items/bulk'                          | BULK    || 404
        5   | 'a promotion'     | 'the agent was not given the solution'  | 'rex'  | 'POST' | '/api/solutions/2/promote'                             | PROMOTE || 404
        6   | 'provenance'      | 'the agent was not given the solution'  | 'rex'  | 'GET'  | '/api/solutions/2/provenance?kind=item&recordId=1200'  | null    || 404
        7   | 'a bulk change'   | 'the solution does not exist'           | 'user' | 'POST' | '/api/solutions/9/items/bulk'                          | BULK    || 404
        8   | 'a promotion'     | 'the solution does not exist'           | 'user' | 'POST' | '/api/solutions/9/promote'                             | PROMOTE || 404
        9   | 'provenance'      | 'the solution does not exist'           | 'user' | 'GET'  | '/api/solutions/9/provenance?kind=item&recordId=1200'  | null    || 404
    }

    def "every endpoint should match nothing when the solution id is not a number"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/one/items/bulk', BULK))).andExpect(status().isNotFound())
        mvc.perform(asUser(request('POST', '/api/solutions/1x/promote', PROMOTE))).andExpect(status().isNotFound())
        mvc.perform(asUser(get('/api/solutions/abc/provenance').param('kind', 'item').param('recordId', '1')))
            .andExpect(status().isNotFound())

        then:
        0 * service._
        0 * repo._
    }

    // ---- bulk change ----

    @Unroll
    def "bulk should pass the body on attributed to #expectedCode and answer the result when #scenario"() {

        when:
        mvc.perform(by(who, request('POST', '/api/solutions/1/items/bulk', BULK))).andExpect(status().isOk())
            .andExpect(jsonPath('$.logId').value(310))
            .andExpect(jsonPath('$.itemCount').value(2))

        then:
        1 * repo.bulkChange(1, '3,1,2', 'status', 'done', 'The 837 path is dead.', expectedCode) >> new BulkResult(310, 2)

        where:
        scenario                          | who       || expectedCode
        'a person changes them'           | 'user'    || 'tony'
        'the house changes them'          | 'service' || 'felix'
        'the house changes them as Stewart' | 'stewart' || 'stewart'
        'an agent changes them'           | 'rex'     || 'rex'
    }

    def "bulk should answer log 0 and count 0 when nothing needed changing"() {

        when:
        ResultActions r = mvc.perform(asAgent(request('POST', '/api/solutions/1/items/bulk',
            '{"itemIds":[5],"field":"priority","value":"3","reason":"Tidy"}')))

        then:
        1 * repo.bulkChange(1, '5', 'priority', '3', 'Tidy', 'rex') >> new BulkResult(0, 0)
        r.andExpect(status().isOk()).andExpect(jsonPath('$.logId').value(0)).andExpect(jsonPath('$.itemCount').value(0))
    }

    def "bulk should pass a missing value on as empty, which clears the field"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/1/items/bulk', '{"itemIds":[5],"field":"due","reason":"No date yet"}')))
            .andExpect(status().isOk())

        then:
        1 * repo.bulkChange(1, '5', 'due', '', 'No date yet', 'tony') >> new BulkResult(311, 1)
    }

    @Unroll
    def "#seq. bulk should answer 400 '#expectedMessage' and change nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/items/bulk', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * repo._

        where:
        seq | scenario                         | body                                                                       || expectedMessage
        1   | 'the field is unknown'           | '{"itemIds":[5],"field":"title","value":"x","reason":"r"}'                 || 'field must be one of status, project, assignee, due, priority, topic.'
        2   | 'the field is missing'           | '{"itemIds":[5],"value":"x","reason":"r"}'                                 || 'field must be one of status, project, assignee, due, priority, topic.'
        3   | 'no items are named'             | '{"itemIds":[],"field":"status","value":"done","reason":"r"}'              || 'Choose the items to change.'
        4   | 'the items are missing'          | '{"field":"status","value":"done","reason":"r"}'                           || 'Choose the items to change.'
        5   | 'every item id is zero or less'  | '{"itemIds":[0,-1,null],"field":"status","value":"done","reason":"r"}'     || 'Choose the items to change.'
        6   | 'there is no reason'             | '{"itemIds":[5],"field":"status","value":"done"}'                          || 'Say why. The reason goes in the log.'
        7   | 'the reason is blank'            | '{"itemIds":[5],"field":"status","value":"done","reason":"  "}'            || 'Say why. The reason goes in the log.'
        8   | 'the reason is too long'         | '{"itemIds":[5],"field":"status","value":"done","reason":"@1001@"}'        || 'A reason can be at most 1,000 characters.'
        9   | 'the value is too long'          | '{"itemIds":[5],"field":"status","value":"@401@","reason":"r"}'            || 'That value is too long.'
    }

    @Unroll
    def "bulk should answer 400 and change nothing when the body is #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/items/bulk', body)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * repo._

        where:
        scenario                      | body                                                               || expectedStatus
        'empty'                       | ''                                                                 || 400
        'null, which Spring reads as missing' | 'null'                                                     || 400
        'broken'                      | '{'                                                                || 400
        'a word for the item list'    | '{"itemIds":"x","field":"status","value":"done","reason":"r"}'     || 400
        'a word for an item id'       | '{"itemIds":["x"],"field":"status","value":"done","reason":"r"}'   || 400
    }

    def "bulk should refuse 501 items with 400 and change nothing"() {

        given:
        String ids = (1..501).join(',')

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/items/bulk',
            '{"itemIds":[' + ids + '],"field":"status","value":"done","reason":"r"}')))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value('At most 500 items can be changed at once.'))
        0 * repo._
    }

    @Unroll
    def "bulk should answer 409 with the proc's own words when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/items/bulk', BULK)))

        then:
        1 * repo.bulkChange(*_) >> { throw new SolutionRuleException(refusal) }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                                  | refusal                                                                       || expectedMessage
        'the status is not one'                   | 'That is not a status. Nothing was changed.'                                  || 'That is not a status. Nothing was changed.'
        'an item is in another solution'          | 'One of those items is not in this solution any more. Nothing was changed.'   || 'One of those items is not in this solution any more. Nothing was changed.'
        'the due date is written another way'     | 'A due date is written yyyy-mm-dd. Nothing was changed.'                      || 'A due date is written yyyy-mm-dd. Nothing was changed.'
    }

    // ---- promote ----

    @Unroll
    def "promote should pass the body on attributed to #expectedCode and answer the new record when #scenario"() {

        when:
        mvc.perform(by(who, request('POST', '/api/solutions/1/promote', PROMOTE))).andExpect(status().isOk())
            .andExpect(jsonPath('$.kind').value('item'))
            .andExpect(jsonPath('$.id').value(1200))

        then:
        1 * repo.promote(1, 'message', 9012, 'item', 41, 'Parse the 835', expectedCode) >> new Promoted('item', 1200)

        where:
        scenario                 | who       || expectedCode
        'a person makes it'      | 'user'    || 'tony'
        'the house makes it'     | 'service' || 'felix'
        'an agent makes it'      | 'rex'     || 'rex'
    }

    @Unroll
    def "promote should pass project #expectedProject and title #expectedTitle when #scenario"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/1/promote', body))).andExpect(status().isOk())

        then:
        1 * repo.promote(1, expectedSource, 77, expectedTarget, expectedProject, expectedTitle, 'tony') >> new Promoted(expectedTarget, 5)

        where:
        scenario                              | body                                                                                   || expectedSource | expectedTarget | expectedProject | expectedTitle
        'no project and no title are sent'    | '{"sourceKind":"note","sourceId":77,"targetKind":"item"}'                              || 'note'         | 'item'         | null            | null
        'project zero and a blank title'      | '{"sourceKind":"Log","sourceId":77,"targetKind":"NOTE","projectId":0,"title":"  "}'    || 'log'          | 'note'         | null            | null
        'a padded title'                      | '{"sourceKind":"item","sourceId":77,"targetKind":"note","title":" Runbook "}'           || 'item'         | 'note'         | null            | 'Runbook'
    }

    @Unroll
    def "#seq. promote should answer 400 '#expectedMessage' and add nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/promote', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * repo._

        where:
        seq | scenario                         | body                                                                     || expectedMessage
        1   | 'the source kind is missing'     | '{"sourceId":5,"targetKind":"item"}'                                     || 'sourceKind must be one of message, log, note, item.'
        2   | 'the source kind is a decision'  | '{"sourceKind":"decision","sourceId":5,"targetKind":"item"}'             || 'sourceKind must be one of message, log, note, item.'
        3   | 'the target kind is missing'     | '{"sourceKind":"message","sourceId":5}'                                  || 'targetKind must be one of item, note, question.'
        4   | 'the target kind is a log entry' | '{"sourceKind":"message","sourceId":5,"targetKind":"log"}'               || 'targetKind must be one of item, note, question.'
        5   | 'the source id is missing'       | '{"sourceKind":"message","targetKind":"item"}'                           || 'Say which record to make it from (sourceId).'
        6   | 'the source id is zero'          | '{"sourceKind":"message","sourceId":0,"targetKind":"item"}'              || 'Say which record to make it from (sourceId).'
        7   | 'the title is too long'          | '{"sourceKind":"message","sourceId":5,"targetKind":"item","title":"@401@"}' || 'A title can be at most 400 characters.'
    }

    @Unroll
    def "promote should answer 400 and add nothing when the body is #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/promote', body)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * repo._

        where:
        scenario                   | body                                                              || expectedStatus
        'empty'                    | ''                                                                || 400
        'null, which Spring reads as missing' | 'null'                                                 || 400
        'broken'                   | '{"sourceKind":'                                                  || 400
        'a word for the source id' | '{"sourceKind":"message","sourceId":"x","targetKind":"item"}'     || 400
    }

    @Unroll
    def "promote should answer 409 with the proc's own words when #scenario"() {

        when:
        ResultActions r = mvc.perform(asAgent(request('POST', '/api/solutions/1/promote', PROMOTE)))

        then:
        1 * repo.promote(*_) >> { throw new SolutionRuleException(refusal) }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                                  | refusal                                                                 || expectedMessage
        'it was already made into an item'        | 'That was already made into an item (#77). Nothing was added.'          || 'That was already made into an item (#77). Nothing was added.'
        'the item has no project'                 | 'Choose the project the item goes in. Nothing was added.'               || 'Choose the project the item goes in. Nothing was added.'
        'the pair is not allowed'                 | 'That cannot be made into that. Nothing was added.'                     || 'That cannot be made into that. Nothing was added.'
    }

    // ---- provenance ----

    def "provenance should pass the kind and record on and answer every field when an agent asks"() {

        when:
        mvc.perform(asAgent(get('/api/solutions/1/provenance').param('kind', 'Question').param('recordId', '12')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(2))
            .andExpect(jsonPath('$[0].direction').value('from'))
            .andExpect(jsonPath('$[0].kind').value('message'))
            .andExpect(jsonPath('$[0].id').value(9012))
            .andExpect(jsonPath('$[0].title').value('The partner app writes the 837'))
            .andExpect(jsonPath('$[0].conversationId').value(44))
            .andExpect(jsonPath('$[0].promotedDate').exists())
            .andExpect(jsonPath('$[0].promotedByCode').value('tony'))
            .andExpect(jsonPath('$[0].promotedByName').value('Tony'))
            .andExpect(jsonPath('$[1].direction').value('into'))
            .andExpect(jsonPath('$[1].kind').value('item'))
            .andExpect(jsonPath('$[1].conversationId').doesNotExist())

        then:
        1 * repo.provenance(1, 'question', 12) >> [
            new Provenance('from', 'message', 9012, 'The partner app writes the 837', 44, getLocalDateTime('11/04/2026 10:15'), 'tony', 'Tony'),
            new Provenance('into', 'item', 1300, 'Parse the 835', null, getLocalDateTime('11/04/2026 11:00'), 'rex', 'Rex')]
    }

    def "provenance should answer an empty list when nothing was made from the record"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/1/provenance').param('kind', 'item').param('recordId', '5')))

        then:
        1 * repo.provenance(1, 'item', 5) >> []
        r.andExpect(status().isOk()).andExpect(jsonPath('$.length()').value(0))
    }

    @Unroll
    def "provenance should answer 400 '#expectedMessage' and read nothing when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(get(url)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * repo._

        where:
        scenario                    | url                                                     || expectedMessage
        'no kind is sent'           | '/api/solutions/1/provenance?recordId=5'                || 'kind must be one of message, log, note, item, question.'
        'the kind is a decision'    | '/api/solutions/1/provenance?kind=decision&recordId=5'  || 'kind must be one of message, log, note, item, question.'
        'no record is sent'         | '/api/solutions/1/provenance?kind=item'                 || 'Say which record (recordId).'
        'the record is zero'        | '/api/solutions/1/provenance?kind=item&recordId=0'      || 'Say which record (recordId).'
    }

    def "provenance should answer 400 and read nothing when the record id is not a number"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/1/provenance').param('kind', 'item').param('recordId', 'abc')))

        then:
        r.andExpect(status().isBadRequest())
        0 * repo._
    }
}
