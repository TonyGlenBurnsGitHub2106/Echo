package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions

import com.echo.SolutionDependencyModels.StoredWait
import com.echo.SolutionDependencyModels.Wait
import com.echo.SolutionDependencyModels.WaitCount
import com.echo.SolutionModels.Item
import static com.echo.SolutionFixtures.item
import static com.echo.SolutionFixtures.lookups

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5695 (Release 3): what items wait on over HTTP -- every endpoint of SolutionDependencyController, who
 * may call it, the path and body fields that become the service call, and how each refusal maps to a status
 * (400 malformed, 404 not found or not shared, 409 a rule). The real SolutionDependencyService,
 * SolutionItemService (its get), access fence and code check run; only storage is mocked.
 */
@WebMvcTest(SolutionDependencyController)
class SolutionDependencyControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionDependencyService service = Mock()

    SolutionDependencyRepository waits = Mock()
    SolutionItemRepository itemRepo = Mock()
    SolutionDependencyService real

    Map<Integer, Item> itemRows = [100: item(id: 100, solutionId: 1), 101: item(id: 101, solutionId: 1, title: 'Deploy it'),
                                   200: item(id: 200, solutionId: 2), 201: item(id: 201, solutionId: 2)]
    Map<Integer, StoredWait> waitRows = [7: new StoredWait(7, 101, 100, 1), 8: new StoredWait(8, 201, 200, 2)]

    def setup() {
        SolutionCodeCheck codes = new SolutionCodeCheck(solutionRepo)
        SolutionItemService items = new SolutionItemService(itemRepo, new SolutionService(solutionRepo, access, codes), access, codes)
        real = new SolutionDependencyService(waits, items, access)
        service.forItem(*_) >> { a -> real.forItem(*a) }
        service.add(*_) >> { a -> real.add(*a) }
        service.remove(*_) >> { a -> real.remove(*a) }
        service.forSolution(*_) >> { a -> real.forSolution(*a) }

        solutionRepo.lookups() >> lookups()
        itemRepo.find(_) >> { int id -> itemRows[id] }
        waits.find(_) >> { int id -> waitRows[id] }
        waits.save(*_) >> 7
        waits.delete(_) >> true
        waits.forItem(_) >> [waitOn(7, 100)]
        waits.forSolution(_) >> [new WaitCount(101, 1, 1)]
    }

    private static Wait waitOn(int id, int otherItemId) {
        new Wait(id, 'waitsOn', otherItemId, 'Item ' + otherItemId, 'Deploy', 'todo', 'Not started', '#6B7280', false, null)
    }

    // ---- who may call ----

    @Unroll
    def "#scenario should answer 401 and touch nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * service._
        0 * waits._
        0 * itemRepo._
        r.andExpect(status().is(expectedStatus))

        where:
        scenario               | method   | url                                  | body                       || expectedStatus
        'waits of an item'     | 'GET'    | '/api/solutions/items/101/waits'     | null                       || 401
        'add a wait'           | 'POST'   | '/api/solutions/items/101/waits'     | '{"dependsOnItemId":100}'  || 401
        'remove a wait'        | 'DELETE' | '/api/solutions/waits/7'             | null                       || 401
        'waits of a solution'  | 'GET'    | '/api/solutions/1/waits'             | null                       || 401
    }

    @Unroll
    def "#scenario should answer 200 when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().isOk()).andExpect(jsonPath(path).value(expectedValue))
        }

        where:
        scenario               | method   | url                                  | body                       || path              | expectedValue
        'waits of an item'     | 'GET'    | '/api/solutions/items/101/waits'     | null                       || '$[0].direction'  | 'waitsOn'
        'add a wait'           | 'POST'   | '/api/solutions/items/101/waits'     | '{"dependsOnItemId":100}'  || '$[0].itemId'     | 100
        'remove a wait'        | 'DELETE' | '/api/solutions/waits/7'             | null                       || '$.deletedId'     | 7
        'waits of a solution'  | 'GET'    | '/api/solutions/1/waits'             | null                       || '$[0].openWaits'  | 1
    }

    @Unroll
    def "#seq. #scenario should answer 404 and write nothing when the agent #who was not given the solution"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * waits.save(*_)
        0 * waits.delete(_)
        0 * waits.forItem(_)
        0 * waits.forSolution(_)

        where:
        seq | scenario                         | who   | method   | url                                  | body                       || expectedStatus
        1   | 'waits of an item'               | 'zed' | 'GET'    | '/api/solutions/items/101/waits'     | null                       || 404
        2   | 'add a wait'                     | 'zed' | 'POST'   | '/api/solutions/items/101/waits'     | '{"dependsOnItemId":100}'  || 404
        3   | 'remove a wait'                  | 'zed' | 'DELETE' | '/api/solutions/waits/7'             | null                       || 404
        4   | 'waits of a solution'            | 'zed' | 'GET'    | '/api/solutions/1/waits'             | null                       || 404
        5   | 'waits of an item'               | 'rex' | 'GET'    | '/api/solutions/items/201/waits'     | null                       || 404
        6   | 'add a wait'                     | 'rex' | 'POST'   | '/api/solutions/items/201/waits'     | '{"dependsOnItemId":200}'  || 404
        7   | 'add a wait on a hidden item'    | 'rex' | 'POST'   | '/api/solutions/items/101/waits'     | '{"dependsOnItemId":200}'  || 404
        8   | 'remove a wait'                  | 'rex' | 'DELETE' | '/api/solutions/waits/8'             | null                       || 404
        9   | 'waits of a solution'            | 'rex' | 'GET'    | '/api/solutions/2/waits'             | null                       || 404
    }

    @Unroll
    def "#scenario should answer 404 '#expectedMessage' when the record is missing"() {

        when:
        ResultActions r = mvc.perform(asUser(request(method, url, body)))

        then:
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * waits.save(*_)
        0 * waits.delete(_)
        0 * waits.forItem(_)

        where:
        scenario                         | method   | url                                  | body                       || expectedMessage
        'waits of a missing item'        | 'GET'    | '/api/solutions/items/99/waits'      | null                       || 'Item not found.'
        'a missing item waits'           | 'POST'   | '/api/solutions/items/99/waits'      | '{"dependsOnItemId":100}'  || 'Item not found.'
        'an item waits on a missing one' | 'POST'   | '/api/solutions/items/101/waits'     | '{"dependsOnItemId":99}'   || 'Item not found.'
        'remove a missing wait'          | 'DELETE' | '/api/solutions/waits/99'            | null                       || 'Wait not found.'
        'remove wait zero'               | 'DELETE' | '/api/solutions/waits/0'             | null                       || 'Wait not found.'
        'waits of a missing solution'    | 'GET'    | '/api/solutions/9/waits'             | null                       || 'Solution not found.'
    }

    def "every endpoint should match nothing when an id is not a number"() {

        when:
        mvc.perform(asUser(get('/api/solutions/items/abc/waits'))).andExpect(status().isNotFound())
        mvc.perform(asUser(json(post('/api/solutions/items/x1/waits'), '{"dependsOnItemId":100}'))).andExpect(status().isNotFound())
        mvc.perform(asUser(delete('/api/solutions/waits/7x'))).andExpect(status().isNotFound())
        mvc.perform(asUser(get('/api/solutions/one/waits'))).andExpect(status().isNotFound())

        then:
        0 * service._
        0 * waits._
        0 * itemRepo._
    }

    // ---- what an item waits on ----

    def "forItem should answer both directions with every field when a person asks"() {

        when:
        mvc.perform(asUser(get('/api/solutions/items/100/waits'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(2))
            .andExpect(jsonPath('$[0].id').value(7))
            .andExpect(jsonPath('$[0].direction').value('blocks'))
            .andExpect(jsonPath('$[0].itemId').value(101))
            .andExpect(jsonPath('$[0].title').value('Deploy it'))
            .andExpect(jsonPath('$[0].projectName').value('Deploy'))
            .andExpect(jsonPath('$[0].statusCode').value('done'))
            .andExpect(jsonPath('$[0].statusName').value('Done'))
            .andExpect(jsonPath('$[0].colorHex').value('#16A34A'))
            .andExpect(jsonPath('$[0].closed').value(true))
            .andExpect(jsonPath('$[1].direction').value('waitsOn'))

        then:
        1 * waits.forItem(100) >> [new Wait(7, 'blocks', 101, 'Deploy it', 'Deploy', 'done', 'Done', '#16A34A', true, null), waitOn(9, 101)]
    }

    // ---- add ----

    @Unroll
    def "add should record the wait attributed to #expectedCode and answer the item's waits when #scenario"() {

        when:
        mvc.perform(by(who, request('POST', '/api/solutions/items/101/waits', '{"dependsOnItemId":100}'))).andExpect(status().isOk())
            .andExpect(jsonPath('$[0].itemId').value(100))

        then:
        1 * waits.save(101, 100, expectedCode) >> 7

        then:
        1 * waits.forItem(101) >> [waitOn(7, 100)]

        where:
        scenario                        | who       || expectedCode
        'a person adds it'              | 'user'    || 'tony'
        'the house adds it'             | 'service' || 'felix'
        'the house adds it as Stewart'  | 'stewart' || 'stewart'
        'an agent adds it'              | 'rex'     || 'rex'
    }

    @Unroll
    def "add should answer 400 'dependsOnItemId is required.' and look nothing up when #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/items/101/waits', body)))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * itemRepo._
        0 * waits.save(*_)

        where:
        scenario                 | body                          || expectedMessage
        'the id is missing'      | '{}'                          || 'dependsOnItemId is required.'
        'the id is null'         | '{"dependsOnItemId":null}'    || 'dependsOnItemId is required.'
        'the id is zero'         | '{"dependsOnItemId":0}'       || 'dependsOnItemId is required.'
        'the id is negative'     | '{"dependsOnItemId":-1}'      || 'dependsOnItemId is required.'
    }

    def "add should answer 409 and save nothing when the two items are of different solutions"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/items/101/waits', '{"dependsOnItemId":200}')))

        then:
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value('An item can only wait on an item of the same solution. Nothing was saved.'))
        0 * waits.save(*_)
    }

    @Unroll
    def "add should answer 409 verbatim when the repository refuses because #scenario"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/items/' + itemId + '/waits', body)))

        then:
        1 * waits.save(*_) >> { throw new SolutionRuleException(refusal) }
        0 * waits.forItem(_)
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                      | itemId | body                       | refusal                                                                                                         || expectedMessage
        'the item waits on itself'    | 101    | '{"dependsOnItemId":101}'  | 'An item cannot wait on itself. Nothing was saved.'                                                             || 'An item cannot wait on itself. Nothing was saved.'
        'the wait is a repeat'        | 101    | '{"dependsOnItemId":100}'  | 'It already waits on that item. Nothing was saved.'                                                             || 'It already waits on that item. Nothing was saved.'
        'the wait closes a circle'    | 100    | '{"dependsOnItemId":101}'  | 'That would go round in a circle: the other item already waits, step by step, on this one. Nothing was saved.' || 'That would go round in a circle: the other item already waits, step by step, on this one. Nothing was saved.'
    }

    @Unroll
    def "add should answer 400 and save nothing when the body is '#body'"() {

        when:
        ResultActions r = mvc.perform(asUser(json(request('POST', '/api/solutions/items/101/waits', null), body)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * waits.save(*_)

        where:
        scenario               | body                        || expectedStatus
        'an empty body'        | ''                          || 400
        'a null body'          | 'null'                      || 400
        'a broken body'        | '{'                         || 400
        'a word for the id'    | '{"dependsOnItemId":"x"}'   || 400
    }

    // ---- remove ----

    def "remove should take the wait away and answer its id when the wait is visible"() {

        when:
        mvc.perform(asAgent(delete('/api/solutions/waits/7'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.deletedId').value(7))

        then:
        1 * waits.delete(7) >> true
    }

    def "remove should answer 404 'Wait not found.' when the repository removed nothing"() {

        when:
        ResultActions r = mvc.perform(asUser(delete('/api/solutions/waits/7')))

        then:
        1 * waits.delete(7) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Wait not found.'))
    }

    // ---- the solution's counts ----

    def "forSolution should answer every waiting item's counts when an agent asks"() {

        when:
        mvc.perform(asAgent(get('/api/solutions/1/waits'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(2))
            .andExpect(jsonPath('$[0].itemId').value(101))
            .andExpect(jsonPath('$[0].waits').value(2))
            .andExpect(jsonPath('$[0].openWaits').value(1))
            .andExpect(jsonPath('$[1].openWaits').value(0))

        then:
        1 * waits.forSolution(1) >> [new WaitCount(101, 2, 1), new WaitCount(102, 1, 0)]
    }
}
