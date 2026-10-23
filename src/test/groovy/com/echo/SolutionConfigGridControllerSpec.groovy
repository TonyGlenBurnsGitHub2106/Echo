package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import com.echo.ApiClientModels.Credential

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5653: the Configuration II tab over HTTP -- every endpoint, who may call it, its JSON, and how each
 * refusal maps to a status. The service is real (delegated from the bean mock); only storage is fake.
 */
@WebMvcTest(SolutionConfigGridController)
class SolutionConfigGridControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionConfigGridService service = Mock()

    SolutionConfigGridRepository repo = Mock()
    SolutionConfigRepository oldTab = Mock()
    SolutionConfigGridService real

    Map<String, Integer> locs = ['axis:51': 1, 'axis:54': 2, 'axisvalue:61': 1, 'axisvalue:64': 2, 'column:71': 1, 'column:74': 2,
                                 'group:81': 1, 'group:82': 2, 'property:91': 1, 'property:94': 2]

    def setup() {
        real = new SolutionConfigGridService(repo, oldTab, access)
        service.grid(*_) >> { a -> real.grid(*a) }
        service.locateProperty(*_) >> { a -> real.locateProperty(*a) }
        service.importFromConfig(*_) >> { a -> real.importFromConfig(*a) }
        service.addAxis(*_) >> { a -> real.addAxis(*a) }
        service.updateAxis(*_) >> { a -> real.updateAxis(*a) }
        service.deleteAxis(*_) >> { a -> real.deleteAxis(*a) }
        service.addAxisValue(*_) >> { a -> real.addAxisValue(*a) }
        service.updateAxisValue(*_) >> { a -> real.updateAxisValue(*a) }
        service.deleteAxisValue(*_) >> { a -> real.deleteAxisValue(*a) }
        service.addColumn(*_) >> { a -> real.addColumn(*a) }
        service.updateColumn(*_) >> { a -> real.updateColumn(*a) }
        service.deleteColumn(*_) >> { a -> real.deleteColumn(*a) }
        service.addGroup(*_) >> { a -> real.addGroup(*a) }
        service.updateGroup(*_) >> { a -> real.updateGroup(*a) }
        service.deleteGroup(*_) >> { a -> real.deleteGroup(*a) }
        service.addProperty(*_) >> { a -> real.addProperty(*a) }
        service.updateProperty(*_) >> { a -> real.updateProperty(*a) }
        service.deleteProperty(*_) >> { a -> real.deleteProperty(*a) }
        service.setValue(*_) >> { a -> real.setValue(*a) }

        repo.locate(_, _) >> { String kind, int id -> locs[kind + ':' + id] }
        repo.axes(_) >> { int s -> s == 1 ? [[SolutionConfigGridAxisID: 51, AxisTitle: 'Environment', SortOrder: 1]] : [] }
        repo.axisValues(_) >> { int s -> s == 1 ? [[SolutionConfigGridAxisValueID: 61, SolutionConfigGridAxisID: 51, ValueTitle: 'QA', ColorHex: '#111111']] : [] }
        repo.columns(_) >> { int s -> s == 1 ? [[SolutionConfigGridColumnID: 71, ColumnTitle: 'QA', ColorHex: '#2563EB', IsDefaultColumn: false]] : [] }
        repo.tags(_) >> { int s -> s == 1 ? [[SolutionConfigGridColumnID: 71, SolutionConfigGridAxisID: 51, SolutionConfigGridAxisValueID: 61, ValueTitle: 'QA']] : [] }
        repo.groups(_) >> { int s -> s == 1 ? [[SolutionConfigGridGroupID: 81, GroupTitle: 'Database']] : [] }
        repo.properties(_) >> { int s -> s == 1 ? [[SolutionConfigGridPropertyID: 91, SolutionConfigGridGroupID: 81, PropertyKey: 'db.url', TypeCode: 'url']] : [] }
        repo.values(_) >> { int s -> s == 1 ? [[SolutionConfigGridValueID: 101, SolutionConfigGridPropertyID: 91, SolutionConfigGridColumnID: 71,
                                                ValueText: 'jdbc:x', SourceTitle: 'app.properties', StateCode: 'planned']] : [] }
        repo.alsoSet(_) >> { int s -> s == 1 ? [[SolutionConfigGridValueID: 101, SourceTitle: 'web.xml']] : [] }
        repo.types() >> []
        repo.cellStates() >> [[StateCode: 'observed', StateName: 'Observed']]
        repo.sources(_) >> []
        repo.saveColumn(*_) >> 75
        repo.deleteAxis(_) >> true
        repo.deleteAxisValue(_) >> true
        repo.deleteColumn(_) >> true
        repo.deleteGroup(_) >> true
        repo.deleteProperty(_) >> true
        oldTab.environments(_) >> []
    }

    @Unroll
    def "#seq. #scenario should answer 401 and read nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * repo._
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.error').value('Not signed in'))

        where:
        seq | scenario            | method   | url                                                    | body                                             || expectedStatus
        1   | 'grid'              | 'GET'    | '/api/solutions/1/config-grid'                         | null                                             || 401
        2   | 'importFromConfig'  | 'POST'   | '/api/solutions/1/config-grid/import'                  | null                                             || 401
        3   | 'addAxis'           | 'POST'   | '/api/solutions/1/config-grid/axes'                    | '{"title":"Server"}'                             || 401
        4   | 'updateAxis'        | 'PUT'    | '/api/solutions/config-grid/axes/51'                   | '{"title":"Env","sortOrder":1}'                  || 401
        5   | 'deleteAxis'        | 'DELETE' | '/api/solutions/config-grid/axes/51'                   | null                                             || 401
        6   | 'addAxisValue'      | 'POST'   | '/api/solutions/config-grid/axes/51/values'            | '{"title":"Prod","color":"#00FF00"}'             || 401
        7   | 'updateAxisValue'   | 'PUT'    | '/api/solutions/config-grid/axis-values/61'            | '{"title":"QA2"}'                                || 401
        8   | 'deleteAxisValue'   | 'DELETE' | '/api/solutions/config-grid/axis-values/61'            | null                                             || 401
        9   | 'addColumn'         | 'POST'   | '/api/solutions/1/config-grid/columns'                 | '{"title":"Prod","tags":{"Environment":"Prod"}}' || 401
        10  | 'updateColumn'      | 'PUT'    | '/api/solutions/config-grid/columns/71'                | '{"title":"QA2"}'                                || 401
        11  | 'deleteColumn'      | 'DELETE' | '/api/solutions/config-grid/columns/71'                | null                                             || 401
        12  | 'addGroup'          | 'POST'   | '/api/solutions/1/config-grid/groups'                  | '{"title":"Cache"}'                              || 401
        13  | 'updateGroup'       | 'PUT'    | '/api/solutions/config-grid/groups/81'                 | '{"title":"DB"}'                                 || 401
        14  | 'deleteGroup'       | 'DELETE' | '/api/solutions/config-grid/groups/81'                 | null                                             || 401
        15  | 'addProperty'       | 'POST'   | '/api/solutions/config-grid/groups/81/properties'      | '{"key":"k"}'                                    || 401
        16  | 'updateProperty'    | 'PUT'    | '/api/solutions/config-grid/properties/91'             | '{"key":"k2"}'                                   || 401
        17  | 'deleteProperty'    | 'DELETE' | '/api/solutions/config-grid/properties/91'             | null                                             || 401
        18  | 'locate'            | 'GET'    | '/api/solutions/config-grid/properties/91/locate'      | null                                             || 401
        19  | 'setValue'          | 'PUT'    | '/api/solutions/config-grid/properties/91/values/71'   | '{"value":"v","state":"observed"}'               || 401
    }

    @Unroll
    def "#seq. #scenario should answer 200 with the owning solution when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.solutionId').value(1))
        }

        where:
        seq | scenario            | method   | url                                                    | body                                             || expectedStatus
        1   | 'grid'              | 'GET'    | '/api/solutions/1/config-grid'                         | null                                             || 200
        2   | 'importFromConfig'  | 'POST'   | '/api/solutions/1/config-grid/import'                  | null                                             || 200
        3   | 'addAxis'           | 'POST'   | '/api/solutions/1/config-grid/axes'                    | '{"title":"Server"}'                             || 200
        4   | 'updateAxis'        | 'PUT'    | '/api/solutions/config-grid/axes/51'                   | '{"title":"Env","sortOrder":1}'                  || 200
        5   | 'deleteAxis'        | 'DELETE' | '/api/solutions/config-grid/axes/51'                   | null                                             || 200
        6   | 'addAxisValue'      | 'POST'   | '/api/solutions/config-grid/axes/51/values'            | '{"title":"Prod","color":"#00FF00"}'             || 200
        7   | 'updateAxisValue'   | 'PUT'    | '/api/solutions/config-grid/axis-values/61'            | '{"title":"QA2"}'                                || 200
        8   | 'deleteAxisValue'   | 'DELETE' | '/api/solutions/config-grid/axis-values/61'            | null                                             || 200
        9   | 'addColumn'         | 'POST'   | '/api/solutions/1/config-grid/columns'                 | '{"title":"Prod","tags":{"Environment":"Prod"}}' || 200
        10  | 'updateColumn'      | 'PUT'    | '/api/solutions/config-grid/columns/71'                | '{"title":"QA2"}'                                || 200
        11  | 'deleteColumn'      | 'DELETE' | '/api/solutions/config-grid/columns/71'                | null                                             || 200
        12  | 'addGroup'          | 'POST'   | '/api/solutions/1/config-grid/groups'                  | '{"title":"Cache"}'                              || 200
        13  | 'updateGroup'       | 'PUT'    | '/api/solutions/config-grid/groups/81'                 | '{"title":"DB"}'                                 || 200
        14  | 'deleteGroup'       | 'DELETE' | '/api/solutions/config-grid/groups/81'                 | null                                             || 200
        15  | 'addProperty'       | 'POST'   | '/api/solutions/config-grid/groups/81/properties'      | '{"key":"k"}'                                    || 200
        16  | 'updateProperty'    | 'PUT'    | '/api/solutions/config-grid/properties/91'             | '{"key":"k2"}'                                   || 200
        17  | 'deleteProperty'    | 'DELETE' | '/api/solutions/config-grid/properties/91'             | null                                             || 200
        18  | 'locate'            | 'GET'    | '/api/solutions/config-grid/properties/91/locate'      | null                                             || 200
        19  | 'setValue'          | 'PUT'    | '/api/solutions/config-grid/properties/91/values/71'   | '{"value":"v","state":"observed"}'               || 200
    }

    @Unroll
    def "#seq. #scenario should answer 404 and write nothing when the agent #who was not given the solution"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.error').value('not_found'))
            .andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * repo.importFromConfig(_)
        0 * repo.saveAxis(*_)
        0 * repo.saveAxisValue(*_)
        0 * repo.saveColumn(*_)
        0 * repo.setTag(*_)
        0 * repo.saveGroup(*_)
        0 * repo.saveProperty(*_)
        0 * repo.saveValue(*_)
        0 * repo.deleteAxis(_)
        0 * repo.deleteAxisValue(_)
        0 * repo.deleteColumn(_)
        0 * repo.deleteGroup(_)
        0 * repo.deleteProperty(_)

        where:
        seq | scenario            | who   | method   | url                                                    | body                                || expectedStatus
        1   | 'grid'              | 'zed' | 'GET'    | '/api/solutions/1/config-grid'                         | null                                || 404
        2   | 'importFromConfig'  | 'zed' | 'POST'   | '/api/solutions/1/config-grid/import'                  | null                                || 404
        3   | 'addAxis'           | 'zed' | 'POST'   | '/api/solutions/1/config-grid/axes'                    | '{"title":"Server"}'                || 404
        4   | 'updateAxis'        | 'zed' | 'PUT'    | '/api/solutions/config-grid/axes/51'                   | '{"title":"Env"}'                   || 404
        5   | 'deleteAxis'        | 'zed' | 'DELETE' | '/api/solutions/config-grid/axes/51'                   | null                                || 404
        6   | 'addAxisValue'      | 'zed' | 'POST'   | '/api/solutions/config-grid/axes/51/values'            | '{"title":"Prod"}'                  || 404
        7   | 'updateAxisValue'   | 'zed' | 'PUT'    | '/api/solutions/config-grid/axis-values/61'            | '{"title":"QA2"}'                   || 404
        8   | 'deleteAxisValue'   | 'zed' | 'DELETE' | '/api/solutions/config-grid/axis-values/61'            | null                                || 404
        9   | 'addColumn'         | 'zed' | 'POST'   | '/api/solutions/1/config-grid/columns'                 | '{"title":"Prod","tags":{"a":"b"}}' || 404
        10  | 'updateColumn'      | 'zed' | 'PUT'    | '/api/solutions/config-grid/columns/71'                | '{"title":"QA2","tags":{"a":"b"}}'  || 404
        11  | 'deleteColumn'      | 'zed' | 'DELETE' | '/api/solutions/config-grid/columns/71'                | null                                || 404
        12  | 'addGroup'          | 'zed' | 'POST'   | '/api/solutions/1/config-grid/groups'                  | '{"title":"Cache"}'                 || 404
        13  | 'updateGroup'       | 'zed' | 'PUT'    | '/api/solutions/config-grid/groups/81'                 | '{"title":"DB"}'                    || 404
        14  | 'deleteGroup'       | 'zed' | 'DELETE' | '/api/solutions/config-grid/groups/81'                 | null                                || 404
        15  | 'addProperty'       | 'zed' | 'POST'   | '/api/solutions/config-grid/groups/81/properties'      | '{"key":"k"}'                       || 404
        16  | 'updateProperty'    | 'zed' | 'PUT'    | '/api/solutions/config-grid/properties/91'             | '{"key":"k2"}'                      || 404
        17  | 'deleteProperty'    | 'zed' | 'DELETE' | '/api/solutions/config-grid/properties/91'             | null                                || 404
        18  | 'locate'            | 'zed' | 'GET'    | '/api/solutions/config-grid/properties/91/locate'      | null                                || 404
        19  | 'setValue'          | 'zed' | 'PUT'    | '/api/solutions/config-grid/properties/91/values/71'   | '{"value":"v"}'                     || 404
        20  | 'grid'              | 'rex' | 'GET'    | '/api/solutions/2/config-grid'                         | null                                || 404
        21  | 'importFromConfig'  | 'rex' | 'POST'   | '/api/solutions/2/config-grid/import'                  | null                                || 404
        22  | 'addAxis'           | 'rex' | 'POST'   | '/api/solutions/2/config-grid/axes'                    | '{"title":"Server"}'                || 404
        23  | 'updateAxis'        | 'rex' | 'PUT'    | '/api/solutions/config-grid/axes/54'                   | '{"title":"Env"}'                   || 404
        24  | 'deleteAxis'        | 'rex' | 'DELETE' | '/api/solutions/config-grid/axes/54'                   | null                                || 404
        25  | 'addAxisValue'      | 'rex' | 'POST'   | '/api/solutions/config-grid/axes/54/values'            | '{"title":"Prod"}'                  || 404
        26  | 'updateAxisValue'   | 'rex' | 'PUT'    | '/api/solutions/config-grid/axis-values/64'            | '{"title":"QA2"}'                   || 404
        27  | 'deleteAxisValue'   | 'rex' | 'DELETE' | '/api/solutions/config-grid/axis-values/64'            | null                                || 404
        28  | 'addColumn'         | 'rex' | 'POST'   | '/api/solutions/2/config-grid/columns'                 | '{"title":"Prod"}'                  || 404
        29  | 'updateColumn'      | 'rex' | 'PUT'    | '/api/solutions/config-grid/columns/74'                | '{"title":"QA2"}'                   || 404
        30  | 'deleteColumn'      | 'rex' | 'DELETE' | '/api/solutions/config-grid/columns/74'                | null                                || 404
        31  | 'addGroup'          | 'rex' | 'POST'   | '/api/solutions/2/config-grid/groups'                  | '{"title":"Cache"}'                 || 404
        32  | 'updateGroup'       | 'rex' | 'PUT'    | '/api/solutions/config-grid/groups/82'                 | '{"title":"DB"}'                    || 404
        33  | 'deleteGroup'       | 'rex' | 'DELETE' | '/api/solutions/config-grid/groups/82'                 | null                                || 404
        34  | 'addProperty'       | 'rex' | 'POST'   | '/api/solutions/config-grid/groups/82/properties'      | '{"key":"k"}'                       || 404
        35  | 'updateProperty'    | 'rex' | 'PUT'    | '/api/solutions/config-grid/properties/94'             | '{"key":"k2"}'                      || 404
        36  | 'deleteProperty'    | 'rex' | 'DELETE' | '/api/solutions/config-grid/properties/94'             | null                                || 404
        37  | 'locate'            | 'rex' | 'GET'    | '/api/solutions/config-grid/properties/94/locate'      | null                                || 404
        38  | 'setValue'          | 'rex' | 'PUT'    | '/api/solutions/config-grid/properties/94/values/74'   | '{"value":"v"}'                     || 404
    }

    def "grid should answer 401 when the agent key is wrong or the agent also presents the house key"() {

        expect:
        mvc.perform(asAgent(get('/api/solutions/1/config-grid'), 'rex', 'nope')).andExpect(status().isUnauthorized())
        mvc.perform(asService(asAgent(get('/api/solutions/1/config-grid')))).andExpect(status().isUnauthorized())
            .andExpect(jsonPath('$.error').value('An API client may not present any other credential.'))
    }

    def "grid should answer the whole tab as JSON when a person reads it"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/1/config-grid'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.solutionId').value(1))
            .andExpect(jsonPath('$.axes[0].title').value('Environment'))
            .andExpect(jsonPath('$.axes[0].values[0].color').value('#111111'))
            .andExpect(jsonPath('$.columns[0].title').value('QA'))
            .andExpect(jsonPath('$.columns[0].isDefault').value(false))
            .andExpect(jsonPath('$.columns[0].tags[0].axisTitle').value('Environment'))
            .andExpect(jsonPath('$.columns[0].tags[0].value').value('QA'))
            .andExpect(jsonPath('$.groups[0].properties[0].key').value('db.url'))
            .andExpect(jsonPath('$.groups[0].properties[0].values[0].state').value('planned'))
            .andExpect(jsonPath('$.groups[0].properties[0].values[0].alsoSetIn[0]').value('web.xml'))
            .andExpect(jsonPath('$.groups[0].properties[0].matches.all.state').value('single'))
            .andExpect(jsonPath('$.groups[0].properties[0].matches.51.sets[0].valueId').value(61))
            .andExpect(jsonPath('$.states[0].code').value('observed'))
            .andExpect(jsonPath('$.counts.properties').value(1))
            .andExpect(jsonPath('$.canImport').value(false))
    }

    def "addColumn should tag by axis name and remove a tag when its value is JSON null"() {

        when:
        mvc.perform(asUser(request('POST', '/api/solutions/1/config-grid/columns',
            '{"title":"Prod","isDefault":true,"color":"#abcdef","tags":{"Environment":"Prod","Server":null}}'))).andExpect(status().isOk())

        then:
        1 * repo.saveColumn(0, 1, 'Prod', '#ABCDEF', true, null) >> 75
        1 * repo.setTag(75, 'Environment', 'Prod')
        1 * repo.setTag(75, 'Server', '')
    }

    @Unroll
    def "#seq. #scenario should answer 404 with the family's message when the thing is missing"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isNotFound())
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        seq | scenario                 | method   | url                                                    | body            || expectedMessage
        1   | 'grid of solution 3'     | 'GET'    | '/api/solutions/3/config-grid'                         | null            || 'Solution not found.'
        2   | 'updateAxis'             | 'PUT'    | '/api/solutions/config-grid/axes/50'                   | '{"title":"x"}' || 'Axis not found.'
        3   | 'addAxisValue'           | 'POST'   | '/api/solutions/config-grid/axes/0/values'             | '{"title":"x"}' || 'Axis not found.'
        4   | 'updateAxisValue'        | 'PUT'    | '/api/solutions/config-grid/axis-values/60'            | '{"title":"x"}' || 'Axis value not found.'
        5   | 'deleteAxisValue'        | 'DELETE' | '/api/solutions/config-grid/axis-values/60'            | null            || 'Axis value not found.'
        6   | 'updateColumn'           | 'PUT'    | '/api/solutions/config-grid/columns/70'                | '{"title":"x"}' || 'Column not found.'
        7   | 'deleteGroup'            | 'DELETE' | '/api/solutions/config-grid/groups/80'                 | null            || 'Group not found.'
        8   | 'locate'                 | 'GET'    | '/api/solutions/config-grid/properties/90/locate'      | null            || 'Property not found.'
        9   | 'setValue'               | 'PUT'    | '/api/solutions/config-grid/properties/91/values/70'   | '{"value":"v"}' || 'Column not found.'
    }

    @Unroll
    def "#scenario should answer 400 naming the field when the required name is blank or missing"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('bad_request'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                         | method | url                                                | body                               || expectedMessage
        'addAxis with no title'          | 'POST' | '/api/solutions/1/config-grid/axes'                | '{}'                               || 'An axis name is required.'
        'addAxisValue with a blank'      | 'POST' | '/api/solutions/config-grid/axes/51/values'        | '{"title":" "}'                    || 'An axis value is required.'
        'addColumn with only a Color'    | 'POST' | '/api/solutions/1/config-grid/columns'             | '{"color":"#000000"}'              || 'A column name is required.'
        'addGroup with an empty title'   | 'POST' | '/api/solutions/1/config-grid/groups'              | '{"title":""}'                     || 'A group name is required.'
        'addProperty with a tab key'     | 'POST' | '/api/solutions/config-grid/groups/81/properties'  | '{"key":"\\t"}'                    || 'A property name is required.'
        'addColumn with a blank axis'    | 'POST' | '/api/solutions/1/config-grid/columns'             | '{"title":"X","tags":{" ":"v"}}'   || 'An axis name is required.'
    }

    @Unroll
    def "#scenario should answer 400 when the Color is not hex RRGGBB"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario            | method | url                                              | body                                            || expectedMessage
        'addAxisValue'      | 'POST' | '/api/solutions/config-grid/axes/51/values'      | '{"title":"x","color":"red"}'                   || 'A Color is written #RRGGBB, for example #2563EB.'
        'updateAxisValue'   | 'PUT'  | '/api/solutions/config-grid/axis-values/61'      | '{"color":"#12345"}'                            || 'A Color is written #RRGGBB, for example #2563EB.'
        'addColumn'         | 'POST' | '/api/solutions/1/config-grid/columns'           | '{"title":"x","color":"javascript:alert(1)"}'   || 'A Color is written #RRGGBB, for example #2563EB.'
        'updateColumn'      | 'PUT'  | '/api/solutions/config-grid/columns/71'          | '{"color":"#1234567"}'                          || 'A Color is written #RRGGBB, for example #2563EB.'
    }

    @Unroll
    def "#seq. #scenario should answer 400 with the limit when a field is too long"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        seq | scenario               | method | url                                                    | body                          || expectedMessage
        1   | 'addAxis'              | 'POST' | '/api/solutions/1/config-grid/axes'                    | '{"title":"@101@"}'           || 'An axis name may be at most 100 characters (this one is 101).'
        2   | 'addAxisValue'         | 'POST' | '/api/solutions/config-grid/axes/51/values'            | '{"title":"@101@"}'           || 'An axis value may be at most 100 characters (this one is 101).'
        3   | 'addColumn'            | 'POST' | '/api/solutions/1/config-grid/columns'                 | '{"title":"@101@"}'           || 'A column name may be at most 100 characters (this one is 101).'
        4   | 'updateColumn tag'     | 'PUT'  | '/api/solutions/config-grid/columns/71'                | '{"tags":{"Env":"@101@"}}'    || 'An axis value may be at most 100 characters (this one is 101).'
        5   | 'addGroup'             | 'POST' | '/api/solutions/1/config-grid/groups'                  | '{"title":"@201@"}'           || 'A group name may be at most 200 characters (this one is 201).'
        6   | 'updateProperty'       | 'PUT'  | '/api/solutions/config-grid/properties/91'             | '{"key":"@201@"}'             || 'A property name may be at most 200 characters (this one is 201).'
        7   | 'setValue value'       | 'PUT'  | '/api/solutions/config-grid/properties/91/values/71'   | '{"value":"@2001@"}'          || 'A value may be at most 2000 characters (this one is 2001).'
        8   | 'setValue pipe'        | 'PUT'  | '/api/solutions/config-grid/properties/91/values/71'   | '{"alsoSetIn":["x|y"]}'       || 'A place name cannot contain |.'
    }

    @Unroll
    def "#seq. #scenario should answer 400 and write nothing when the body is '#body'"() {

        when:
        ResultActions r = mvc.perform(asUser(json(request(method, url, null), body)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * repo.saveAxis(*_)
        0 * repo.saveAxisValue(*_)
        0 * repo.saveColumn(*_)
        0 * repo.saveGroup(*_)
        0 * repo.saveProperty(*_)
        0 * repo.saveValue(*_)

        where:
        seq | scenario           | method | url                                                    | body        || expectedStatus
        1   | 'addAxis'          | 'POST' | '/api/solutions/1/config-grid/axes'                    | ''          || 400
        2   | 'updateAxis'       | 'PUT'  | '/api/solutions/config-grid/axes/51'                   | 'null'      || 400
        3   | 'addAxisValue'     | 'POST' | '/api/solutions/config-grid/axes/51/values'            | '{"title":' || 400
        4   | 'updateAxisValue'  | 'PUT'  | '/api/solutions/config-grid/axis-values/61'            | '"text"'    || 400
        5   | 'addColumn'        | 'POST' | '/api/solutions/1/config-grid/columns'                 | ''          || 400
        6   | 'updateColumn'     | 'PUT'  | '/api/solutions/config-grid/columns/71'                | 'null'      || 400
        7   | 'addGroup'         | 'POST' | '/api/solutions/1/config-grid/groups'                  | '{"title":' || 400
        8   | 'updateGroup'      | 'PUT'  | '/api/solutions/config-grid/groups/81'                 | '"text"'    || 400
        9   | 'addProperty'      | 'POST' | '/api/solutions/config-grid/groups/81/properties'      | ''          || 400
        10  | 'updateProperty'   | 'PUT'  | '/api/solutions/config-grid/properties/91'             | 'null'      || 400
        11  | 'setValue'         | 'PUT'  | '/api/solutions/config-grid/properties/91/values/71'   | ''          || 400
        12  | 'setValue'         | 'PUT'  | '/api/solutions/config-grid/properties/91/values/71'   | '{"title":' || 400
    }

    @Unroll
    def "#scenario should answer 400 when a field has the wrong JSON type"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().is(expectedStatus))

        where:
        scenario                     | method | url                                        | body                                || expectedStatus
        'tags as an array'           | 'POST' | '/api/solutions/1/config-grid/columns'     | '{"title":"x","tags":["a","b"]}'    || 400
        'isDefault as a word'        | 'POST' | '/api/solutions/1/config-grid/columns'     | '{"title":"x","isDefault":"maybe"}' || 400
        'sortOrder as a word'        | 'PUT'  | '/api/solutions/config-grid/groups/81'     | '{"sortOrder":"first"}'             || 400
    }

    @Unroll
    def "#scenario should answer 409 when the target belongs to another solution"() {

        expect:
        mvc.perform(asUser(request('PUT', url, body))).andExpect(status().isConflict())
            .andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                | url                                                    | body            || expectedMessage
        'updateProperty group'  | '/api/solutions/config-grid/properties/91'             | '{"groupId":82}' || 'A property can only move to a group of the same solution.'
        'setValue column'       | '/api/solutions/config-grid/properties/91/values/74'   | '{"value":"v"}'  || 'That column belongs to a different solution.'
    }

    def "setValue should answer 409 verbatim when the repository refuses an unknown state"() {

        when:
        ResultActions r = mvc.perform(asUser(request('PUT', '/api/solutions/config-grid/properties/91/values/71', '{"state":"bogus"}')))

        then:
        1 * repo.saveValue(91, 71, 'jdbc:x', 'app.properties', null, 'bogus', ['web.xml'], 'tony') >> {
            throw new SolutionRuleException('"bogus" is not a cell state. Use observed, planned or unverified. Nothing was saved.')
        }
        r.andExpect(status().isConflict())
            .andExpect(jsonPath('$.message').value('"bogus" is not a cell state. Use observed, planned or unverified. Nothing was saved.'))
    }

    def "delete endpoints should answer 404 when the repository removed nothing"() {

        when:
        ResultActions r = mvc.perform(asUser(delete('/api/solutions/config-grid/axes/51')))

        then:
        1 * repo.deleteAxis(51) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Axis not found.'))

        when:
        r = mvc.perform(asUser(delete('/api/solutions/config-grid/columns/71')))

        then:
        1 * repo.deleteColumn(71) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Column not found.'))
    }

    @Unroll
    def "setValue should attribute the cell to #expectedCode when #scenario"() {

        given:
        MockHttpServletRequestBuilder b = actor == null
            ? by(caller, request('PUT', '/api/solutions/config-grid/properties/91/values/71', '{"value":"v"}'))
            : asActor(request('PUT', '/api/solutions/config-grid/properties/91/values/71', '{"value":"v"}'), actor)

        when:
        mvc.perform(b).andExpect(status().isOk())

        then:
        1 * repo.saveValue(91, 71, 'v', 'app.properties', null, 'planned', ['web.xml'], expectedCode)

        where:
        scenario                         | caller    | actor      || expectedCode
        'a person signs in'              | 'user'    | null       || 'tony'
        'the house calls'                | 'service' | null       || 'felix'
        'a house test actor calls'       | 'service' | 'zz-probe' || 'zz-probe'
        'an agent calls'                 | 'rex'     | null       || 'rex'
    }

    def "grid should answer 403 at the door when the agent's allowlist does not reach /api/solutions/"() {

        given:
        agents.narrow = new Credential(12, 'narrow', 'hash', true, '/api/solutions-other/')

        when:
        ResultActions r = mvc.perform(asAgent(get('/api/solutions/1/config-grid'), 'narrow'))

        then:
        0 * repo._
        r.andExpect(status().isForbidden()).andExpect(jsonPath('$.error').value('This API client may not reach that path.'))
    }

    def "grid endpoints should match nothing when a path id is not a number"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/x/config-grid'))).andExpect(status().isNotFound())
        mvc.perform(asUser(put('/api/solutions/config-grid/axes/1.5'))).andExpect(status().isNotFound())
    }
}
