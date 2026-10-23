package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

/**
 * Story 5653: the Configuration tab over HTTP -- every endpoint, who may call it, what it answers, and how
 * each refusal maps to a status (400 / 404 / 409). The service is real (it delegates from the bean mock);
 * only the repositories are fake.
 */
@WebMvcTest(SolutionConfigController)
class SolutionConfigControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionConfigService service = Mock()

    SolutionConfigRepository repo = Mock()
    SolutionConfigService real

    Map<String, Integer> locs = ['environment:11': 1, 'environment:13': 2, 'group:21': 1, 'group:22': 2,
                                 'property:31': 1, 'property:32': 2]

    def setup() {
        real = new SolutionConfigService(repo, access)
        service.config(*_) >> { a -> real.config(*a) }
        service.locateProperty(*_) >> { a -> real.locateProperty(*a) }
        service.seedDefaults(*_) >> { a -> real.seedDefaults(*a) }
        service.addEnvironment(*_) >> { a -> real.addEnvironment(*a) }
        service.updateEnvironment(*_) >> { a -> real.updateEnvironment(*a) }
        service.deleteEnvironment(*_) >> { a -> real.deleteEnvironment(*a) }
        service.addGroup(*_) >> { a -> real.addGroup(*a) }
        service.updateGroup(*_) >> { a -> real.updateGroup(*a) }
        service.deleteGroup(*_) >> { a -> real.deleteGroup(*a) }
        service.addProperty(*_) >> { a -> real.addProperty(*a) }
        service.updateProperty(*_) >> { a -> real.updateProperty(*a) }
        service.deleteProperty(*_) >> { a -> real.deleteProperty(*a) }
        service.setValue(*_) >> { a -> real.setValue(*a) }

        repo.locate(_, _) >> { String kind, int id -> locs[kind + ':' + id] }
        repo.environments(_) >> { int s -> s == 1 ? [[SolutionConfigEnvironmentID: 11, SolutionEnvironmentID: 1, EnvironmentName: 'Local', SortOrder: 1]] : [] }
        repo.groups(_) >> { int s -> s == 1 ? [[SolutionConfigGroupID: 21, GroupTitle: 'Database', SortOrder: 1]] : [] }
        repo.properties(_) >> { int s -> s == 1 ? [[SolutionConfigPropertyID: 31, SolutionConfigGroupID: 21, PropertyKey: 'db.url',
                                                    TypeCode: 'url', TypeName: 'URL', IsSecret: false, MatchState: 'all', SetCount: 1]] : [] }
        repo.values(_) >> { int s -> s == 1 ? [[SolutionConfigValueID: 41, SolutionConfigPropertyID: 31, SolutionConfigEnvironmentID: 11,
                                                ValueText: 'jdbc:x', SourceTitle: 'app.properties']] : [] }
        repo.alsoSet(_) >> { int s -> s == 1 ? [[SolutionConfigValueID: 41, SourceTitle: 'web.xml']] : [] }
        repo.counts(_) >> { int s -> s == 1 ? [properties: 1, 'group:21': 1, 'groupstate:21.all': 1] : [:] }
        repo.types() >> [[TypeCode: 'url', TypeName: 'URL', IsSecret: false, SortOrder: 1]]
        repo.sources(_) >> { int s -> s == 1 ? ['app.properties'] : [] }
        repo.deleteEnvironment(_) >> true
        repo.deleteGroup(_) >> true
        repo.deleteProperty(_) >> true
    }

    /** The request a data table's caller column names: user | service | rex | actor (the house as named) | agentWithActor. */
    private static MockHttpServletRequestBuilder addCaller(SolutionConfigControllerSpec spec, String caller, String actor,
                                                           MockHttpServletRequestBuilder b) {
        switch (caller) {
            case 'actor':          return spec.asActor(b, actor)
            case 'agentWithActor': return spec.asAgent(b).header(SolutionCallerResolver.HEADER_ACTOR, actor)
            default:               return spec.by(caller, b)
        }
    }

    @Unroll
    def "#seq. #scenario should answer 401 and read nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(request(method, url, body))

        then:
        0 * repo._
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.error').value('Not signed in'))

        where:
        seq | scenario               | method   | url                                                | body                                                      || expectedStatus
        1   | 'config'               | 'GET'    | '/api/solutions/1/config'                          | null                                                      || 401
        2   | 'seedDefaults'         | 'POST'   | '/api/solutions/1/config/environments/defaults'    | null                                                      || 401
        3   | 'addEnvironment'       | 'POST'   | '/api/solutions/1/config/environments'             | '{"title":"Staging"}'                                     || 401
        4   | 'updateEnvironment'    | 'PUT'    | '/api/solutions/config/environments/11'            | '{"title":"Dev","sortOrder":2}'                           || 401
        5   | 'deleteEnvironment'    | 'DELETE' | '/api/solutions/config/environments/11'            | null                                                      || 401
        6   | 'addGroup'             | 'POST'   | '/api/solutions/1/config/groups'                   | '{"title":"Cache"}'                                       || 401
        7   | 'updateGroup'          | 'PUT'    | '/api/solutions/config/groups/21'                  | '{"title":"DB"}'                                          || 401
        8   | 'deleteGroup'          | 'DELETE' | '/api/solutions/config/groups/21'                  | null                                                      || 401
        9   | 'addProperty'          | 'POST'   | '/api/solutions/config/groups/21/properties'       | '{"key":"k","description":"d","typeCode":"text"}'         || 401
        10  | 'updateProperty'       | 'PUT'    | '/api/solutions/config/properties/31'              | '{"key":"k2"}'                                            || 401
        11  | 'deleteProperty'       | 'DELETE' | '/api/solutions/config/properties/31'              | null                                                      || 401
        12  | 'locate'               | 'GET'    | '/api/solutions/config/properties/31/locate'       | null                                                      || 401
        13  | 'setValue'             | 'PUT'    | '/api/solutions/config/properties/31/values/11'    | '{"value":"v","source":"s","note":"n","alsoSetIn":["a"]}' || 401
    }

    @Unroll
    def "#seq. #scenario should answer 200 with the owning solution when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(by(who, request(method, url, body))).andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.solutionId').value(1))
        }

        where:
        seq | scenario               | method   | url                                                | body                                                      || expectedStatus
        1   | 'config'               | 'GET'    | '/api/solutions/1/config'                          | null                                                      || 200
        2   | 'seedDefaults'         | 'POST'   | '/api/solutions/1/config/environments/defaults'    | null                                                      || 200
        3   | 'addEnvironment'       | 'POST'   | '/api/solutions/1/config/environments'             | '{"title":"Staging"}'                                     || 200
        4   | 'updateEnvironment'    | 'PUT'    | '/api/solutions/config/environments/11'            | '{"title":"Dev","sortOrder":2}'                           || 200
        5   | 'deleteEnvironment'    | 'DELETE' | '/api/solutions/config/environments/11'            | null                                                      || 200
        6   | 'addGroup'             | 'POST'   | '/api/solutions/1/config/groups'                   | '{"title":"Cache"}'                                       || 200
        7   | 'updateGroup'          | 'PUT'    | '/api/solutions/config/groups/21'                  | '{"title":"DB"}'                                          || 200
        8   | 'deleteGroup'          | 'DELETE' | '/api/solutions/config/groups/21'                  | null                                                      || 200
        9   | 'addProperty'          | 'POST'   | '/api/solutions/config/groups/21/properties'       | '{"key":"k","description":"d","typeCode":"text"}'         || 200
        10  | 'updateProperty'       | 'PUT'    | '/api/solutions/config/properties/31'              | '{"key":"k2"}'                                            || 200
        11  | 'deleteProperty'       | 'DELETE' | '/api/solutions/config/properties/31'              | null                                                      || 200
        12  | 'locate'               | 'GET'    | '/api/solutions/config/properties/31/locate'       | null                                                      || 200
        13  | 'setValue'             | 'PUT'    | '/api/solutions/config/properties/31/values/11'    | '{"value":"v","source":"s","note":"n","alsoSetIn":["a"]}' || 200
    }

    @Unroll
    def "#seq. #scenario should answer 404 and write nothing when the agent #who was not given the solution"() {

        when:
        ResultActions r = mvc.perform(by(who, request(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus))
            .andExpect(jsonPath('$.ok').value(false))
            .andExpect(jsonPath('$.error').value('not_found'))
            .andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * repo.seedDefaultEnvironments(_)
        0 * repo.saveEnvironment(*_)
        0 * repo.saveGroup(*_)
        0 * repo.saveProperty(*_)
        0 * repo.saveValue(*_)
        0 * repo.deleteEnvironment(_)
        0 * repo.deleteGroup(_)
        0 * repo.deleteProperty(_)

        where:
        seq | scenario               | who   | method   | url                                                | body                  || expectedStatus
        1   | 'config'               | 'zed' | 'GET'    | '/api/solutions/1/config'                          | null                  || 404
        2   | 'seedDefaults'         | 'zed' | 'POST'   | '/api/solutions/1/config/environments/defaults'    | null                  || 404
        3   | 'addEnvironment'       | 'zed' | 'POST'   | '/api/solutions/1/config/environments'             | '{"title":"Staging"}' || 404
        4   | 'updateEnvironment'    | 'zed' | 'PUT'    | '/api/solutions/config/environments/11'            | '{"title":"Dev"}'     || 404
        5   | 'deleteEnvironment'    | 'zed' | 'DELETE' | '/api/solutions/config/environments/11'            | null                  || 404
        6   | 'addGroup'             | 'zed' | 'POST'   | '/api/solutions/1/config/groups'                   | '{"title":"Cache"}'   || 404
        7   | 'updateGroup'          | 'zed' | 'PUT'    | '/api/solutions/config/groups/21'                  | '{"title":"DB"}'      || 404
        8   | 'deleteGroup'          | 'zed' | 'DELETE' | '/api/solutions/config/groups/21'                  | null                  || 404
        9   | 'addProperty'          | 'zed' | 'POST'   | '/api/solutions/config/groups/21/properties'       | '{"key":"k"}'         || 404
        10  | 'updateProperty'       | 'zed' | 'PUT'    | '/api/solutions/config/properties/31'              | '{"key":"k2"}'        || 404
        11  | 'deleteProperty'       | 'zed' | 'DELETE' | '/api/solutions/config/properties/31'              | null                  || 404
        12  | 'locate'               | 'zed' | 'GET'    | '/api/solutions/config/properties/31/locate'       | null                  || 404
        13  | 'setValue'             | 'zed' | 'PUT'    | '/api/solutions/config/properties/31/values/11'    | '{"value":"v"}'       || 404
        14  | 'config'               | 'rex' | 'GET'    | '/api/solutions/2/config'                          | null                  || 404
        15  | 'seedDefaults'         | 'rex' | 'POST'   | '/api/solutions/2/config/environments/defaults'    | null                  || 404
        16  | 'addEnvironment'       | 'rex' | 'POST'   | '/api/solutions/2/config/environments'             | '{"title":"Staging"}' || 404
        17  | 'updateEnvironment'    | 'rex' | 'PUT'    | '/api/solutions/config/environments/13'            | '{"title":"Dev"}'     || 404
        18  | 'deleteEnvironment'    | 'rex' | 'DELETE' | '/api/solutions/config/environments/13'            | null                  || 404
        19  | 'addGroup'             | 'rex' | 'POST'   | '/api/solutions/2/config/groups'                   | '{"title":"Cache"}'   || 404
        20  | 'updateGroup'          | 'rex' | 'PUT'    | '/api/solutions/config/groups/22'                  | '{"title":"DB"}'      || 404
        21  | 'deleteGroup'          | 'rex' | 'DELETE' | '/api/solutions/config/groups/22'                  | null                  || 404
        22  | 'addProperty'          | 'rex' | 'POST'   | '/api/solutions/config/groups/22/properties'       | '{"key":"k"}'         || 404
        23  | 'updateProperty'       | 'rex' | 'PUT'    | '/api/solutions/config/properties/32'              | '{"key":"k2"}'        || 404
        24  | 'deleteProperty'       | 'rex' | 'DELETE' | '/api/solutions/config/properties/32'              | null                  || 404
        25  | 'locate'               | 'rex' | 'GET'    | '/api/solutions/config/properties/32/locate'       | null                  || 404
        26  | 'setValue'             | 'rex' | 'PUT'    | '/api/solutions/config/properties/32/values/13'    | '{"value":"v"}'       || 404
    }

    @Unroll
    def "config should answer 401 before any controller when #scenario"() {

        given:
        String key = goodKey ? AGENT_KEY : 'wrong'
        MockHttpServletRequestBuilder b = asAgent(get('/api/solutions/1/config'), code, key)
        MockHttpServletRequestBuilder sent = addService ? asService(b) : (addUser ? asUser(b) : b)

        when:
        ResultActions r = mvc.perform(sent)

        then:
        0 * repo._
        r.andExpect(status().isUnauthorized()).andExpect(jsonPath('$.ok').value(false))

        where:
        scenario                                      | code     | goodKey | addService | addUser || unused
        'the agent key is wrong'                      | 'rex'    | false   | false      | false   || null
        'the agent code is unknown'                   | 'nobody' | true    | false      | false   || null
        'an agent also presents the service key'      | 'rex'    | true    | true       | false   || null
        'an agent also presents a person token'       | 'rex'    | true    | false      | true    || null
    }

    def "config should answer 401 when only the agent key header is sent"() {

        when:
        ResultActions r = mvc.perform(get('/api/solutions/1/config').header(ApiClientAuthFilter.HEADER_KEY, AGENT_KEY))

        then:
        0 * repo._
        r.andExpect(status().isUnauthorized())
    }

    def "config should answer the whole tab as JSON when a person reads it"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/1/config'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.solutionId').value(1))
            .andExpect(jsonPath('$.environments[0].id').value(11))
            .andExpect(jsonPath('$.environments[0].title').value('Local'))
            .andExpect(jsonPath('$.groups[0].id').value(21))
            .andExpect(jsonPath('$.groups[0].title').value('Database'))
            .andExpect(jsonPath('$.groups[0].propertyCount').value(1))
            .andExpect(jsonPath('$.groups[0].states.all').value(1))
            .andExpect(jsonPath('$.groups[0].properties[0].key').value('db.url'))
            .andExpect(jsonPath('$.groups[0].properties[0].matchState').value('all'))
            .andExpect(jsonPath('$.groups[0].properties[0].values[0].value').value('jdbc:x'))
            .andExpect(jsonPath('$.groups[0].properties[0].values[0].source').value('app.properties'))
            .andExpect(jsonPath('$.groups[0].properties[0].values[0].alsoSetIn[0]').value('web.xml'))
            .andExpect(jsonPath('$.types[0].code').value('url'))
            .andExpect(jsonPath('$.sources[0]').value('app.properties'))
            .andExpect(jsonPath('$.counts.properties').value(1))
    }

    def "locate should answer only the solution and the property when an agent asks"() {

        expect:
        mvc.perform(asAgent(get('/api/solutions/config/properties/31/locate'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.solutionId').value(1))
            .andExpect(jsonPath('$.propertyId').value(31))
            .andExpect(jsonPath('$.groups').doesNotExist())
    }

    @Unroll
    def "#seq. #scenario should answer 404 with the family's message when the thing is missing"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isNotFound())
            .andExpect(jsonPath('$.error').value('not_found'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        seq | scenario                  | method   | url                                              | body            || expectedMessage
        1   | 'config of solution 3'    | 'GET'    | '/api/solutions/3/config'                        | null            || 'Solution not found.'
        2   | 'config of solution 0'    | 'GET'    | '/api/solutions/0/config'                        | null            || 'Solution not found.'
        3   | 'updateEnvironment'       | 'PUT'    | '/api/solutions/config/environments/99'          | '{"title":"x"}' || 'Environment not found.'
        4   | 'deleteEnvironment'       | 'DELETE' | '/api/solutions/config/environments/0'           | null            || 'Environment not found.'
        5   | 'updateGroup'             | 'PUT'    | '/api/solutions/config/groups/99'                | '{"title":"x"}' || 'Group not found.'
        6   | 'deleteGroup'             | 'DELETE' | '/api/solutions/config/groups/99'                | null            || 'Group not found.'
        7   | 'addProperty'             | 'POST'   | '/api/solutions/config/groups/99/properties'     | '{"key":"k"}'   || 'Group not found.'
        8   | 'updateProperty'          | 'PUT'    | '/api/solutions/config/properties/99'            | '{"key":"k"}'   || 'Property not found.'
        9   | 'deleteProperty'          | 'DELETE' | '/api/solutions/config/properties/99'            | null            || 'Property not found.'
        10  | 'locate'                  | 'GET'    | '/api/solutions/config/properties/99/locate'     | null            || 'Property not found.'
        11  | 'setValue on a property'  | 'PUT'    | '/api/solutions/config/properties/99/values/11'  | '{"value":"v"}' || 'Property not found.'
        12  | 'setValue on an env'      | 'PUT'    | '/api/solutions/config/properties/31/values/99'  | '{"value":"v"}' || 'Environment not found.'
    }

    def "delete endpoints should answer 404 when the repository removed nothing"() {

        when:
        ResultActions r = mvc.perform(asUser(delete('/api/solutions/config/environments/11')))

        then:
        1 * repo.deleteEnvironment(11) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Environment not found.'))

        when:
        r = mvc.perform(asUser(delete('/api/solutions/config/groups/21')))

        then:
        1 * repo.deleteGroup(21) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Group not found.'))

        when:
        r = mvc.perform(asUser(delete('/api/solutions/config/properties/31')))

        then:
        1 * repo.deleteProperty(31) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Property not found.'))
    }

    @Unroll
    def "#scenario should answer 400 naming the field when the required name is blank or missing"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('bad_request'))
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                          | method | url                                            | body                  || expectedMessage
        'addEnvironment with no title'    | 'POST' | '/api/solutions/1/config/environments'         | '{}'                  || 'An environment name is required.'
        'addEnvironment with a blank'     | 'POST' | '/api/solutions/1/config/environments'         | '{"title":"   "}'     || 'An environment name is required.'
        'addGroup with an empty title'    | 'POST' | '/api/solutions/1/config/groups'               | '{"title":""}'        || 'A group name is required.'
        'addGroup with only an order'     | 'POST' | '/api/solutions/1/config/groups'               | '{"sortOrder":1}'     || 'A group name is required.'
        'addProperty with no key'         | 'POST' | '/api/solutions/config/groups/21/properties'   | '{"description":"d"}' || 'A property name is required.'
        'addProperty with a blank key'    | 'POST' | '/api/solutions/config/groups/21/properties'   | '{"key":" "}'         || 'A property name is required.'
    }

    @Unroll
    def "#seq. #scenario should answer 400 with the limit when a field is too long"() {

        expect:
        mvc.perform(asUser(request(method, url, body))).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        seq | scenario               | method | url                                              | body                          || expectedMessage
        1   | 'addEnvironment'       | 'POST' | '/api/solutions/1/config/environments'           | '{"title":"@101@"}'           || 'An environment name may be at most 100 characters (this one is 101).'
        2   | 'updateEnvironment'    | 'PUT'  | '/api/solutions/config/environments/11'          | '{"title":"@101@"}'           || 'An environment name may be at most 100 characters (this one is 101).'
        3   | 'addGroup'             | 'POST' | '/api/solutions/1/config/groups'                 | '{"title":"@201@"}'           || 'A group name may be at most 200 characters (this one is 201).'
        4   | 'updateGroup'          | 'PUT'  | '/api/solutions/config/groups/21'                | '{"title":"@201@"}'           || 'A group name may be at most 200 characters (this one is 201).'
        5   | 'addProperty'          | 'POST' | '/api/solutions/config/groups/21/properties'     | '{"key":"@201@"}'             || 'A property name may be at most 200 characters (this one is 201).'
        6   | 'updateProperty'       | 'PUT'  | '/api/solutions/config/properties/31'            | '{"description":"@2001@"}'    || 'A description may be at most 2000 characters (this one is 2001).'
        7   | 'setValue value'       | 'PUT'  | '/api/solutions/config/properties/31/values/11'  | '{"value":"@2001@"}'          || 'A value may be at most 2000 characters (this one is 2001).'
        8   | 'setValue source'      | 'PUT'  | '/api/solutions/config/properties/31/values/11'  | '{"source":"@201@"}'          || 'Where it is read from may be at most 200 characters (this one is 201).'
        9   | 'setValue note'        | 'PUT'  | '/api/solutions/config/properties/31/values/11'  | '{"note":"@2001@"}'           || 'A note may be at most 2000 characters (this one is 2001).'
        10  | 'setValue place'       | 'PUT'  | '/api/solutions/config/properties/31/values/11'  | '{"alsoSetIn":["@201@"]}'     || 'A place name may be at most 200 characters (this one is 201).'
        11  | 'setValue pipe'        | 'PUT'  | '/api/solutions/config/properties/31/values/11'  | '{"alsoSetIn":["a|b"]}'       || 'A place name cannot contain |.'
    }

    @Unroll
    def "#seq. #scenario should answer 400 and write nothing when the body is '#body'"() {

        when:
        ResultActions r = mvc.perform(asUser(json(request(method, url, null), body)))

        then:
        r.andExpect(status().is(expectedStatus))
        0 * repo.saveEnvironment(*_)
        0 * repo.saveGroup(*_)
        0 * repo.saveProperty(*_)
        0 * repo.saveValue(*_)

        where:
        seq | scenario               | method | url                                              | body        || expectedStatus
        1   | 'addEnvironment'       | 'POST' | '/api/solutions/1/config/environments'           | ''          || 400
        2   | 'addEnvironment'       | 'POST' | '/api/solutions/1/config/environments'           | 'null'      || 400
        3   | 'updateEnvironment'    | 'PUT'  | '/api/solutions/config/environments/11'          | ''          || 400
        4   | 'updateEnvironment'    | 'PUT'  | '/api/solutions/config/environments/11'          | 'null'      || 400
        5   | 'addGroup'             | 'POST' | '/api/solutions/1/config/groups'                 | '{not json' || 400
        6   | 'updateGroup'          | 'PUT'  | '/api/solutions/config/groups/21'                | 'null'      || 400
        7   | 'addProperty'          | 'POST' | '/api/solutions/config/groups/21/properties'     | ''          || 400
        8   | 'addProperty'          | 'POST' | '/api/solutions/config/groups/21/properties'     | '[1,2]'     || 400
        9   | 'updateProperty'       | 'PUT'  | '/api/solutions/config/properties/31'            | 'null'      || 400
        10  | 'updateProperty'       | 'PUT'  | '/api/solutions/config/properties/31'            | '{not json' || 400
        11  | 'setValue'             | 'PUT'  | '/api/solutions/config/properties/31/values/11'  | ''          || 400
        12  | 'setValue'             | 'PUT'  | '/api/solutions/config/properties/31/values/11'  | 'null'      || 400
        13  | 'setValue'             | 'PUT'  | '/api/solutions/config/properties/31/values/11'  | '[1,2]'     || 400
    }

    @Unroll
    def "#scenario should answer 400 when a field has the wrong JSON type"() {

        expect:
        mvc.perform(asUser(request('PUT', url, body))).andExpect(status().is(expectedStatus))

        where:
        scenario                     | url                                              | body                    || expectedStatus
        'updateProperty groupId'     | '/api/solutions/config/properties/31'            | '{"groupId":"twenty"}'  || 400
        'setValue alsoSetIn'         | '/api/solutions/config/properties/31/values/11'  | '{"alsoSetIn":{"a":1}}' || 400
    }

    def "updateProperty should answer 409 with the rule's message when the group is of another solution"() {

        expect:
        mvc.perform(asUser(request('PUT', '/api/solutions/config/properties/31', '{"groupId":22}'))).andExpect(status().isConflict())
            .andExpect(jsonPath('$.error').value('rule'))
            .andExpect(jsonPath('$.message').value('A property can only move to a group of the same solution.'))
    }

    def "setValue should answer 409 when the environment is of another solution"() {

        expect:
        mvc.perform(asUser(request('PUT', '/api/solutions/config/properties/31/values/13', '{"value":"v"}'))).andExpect(status().isConflict())
            .andExpect(jsonPath('$.message').value('That environment belongs to a different solution.'))
    }

    def "addGroup should answer 409 verbatim when the repository refuses a rule"() {

        when:
        ResultActions r = mvc.perform(asUser(request('POST', '/api/solutions/1/config/groups', '{"title":"Database"}')))

        then:
        1 * repo.saveGroup(0, 1, 'Database', null) >> { throw new SolutionRuleException('A group named Database already exists.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.message').value('A group named Database already exists.'))
    }

    @Unroll
    def "addProperty should attribute the write to #expectedCode when #scenario"() {

        given:
        MockHttpServletRequestBuilder b = addCaller(this, caller, actor,
            request('POST', '/api/solutions/config/groups/21/properties', '{"key":"k"}'))

        when:
        mvc.perform(b).andExpect(status().isOk())

        then:
        1 * repo.saveProperty(0, 21, 'k', null, null, null, expectedCode)

        where:
        scenario                                       | caller           | actor                    || expectedCode
        'a person signs in'                            | 'user'           | null                     || 'tony'
        'the house calls with no actor'                | 'service'        | null                     || 'felix'
        'the house names Stewart'                      | 'actor'          | 'Stewart'                || 'stewart'
        'the house names an unreadable actor'          | 'actor'          | 'bad actor!'             || 'felix'
        'the house names a 21-character actor'         | 'actor'          | 'abcdefghijklmnopqrstu'  || 'felix'
        'an agent calls'                               | 'rex'            | null                     || 'rex'
        'an agent tries to name tony as the actor'     | 'agentWithActor' | 'tony'                   || 'rex'
    }

    def "setValue should keep the fields left out when a merge is sent over HTTP"() {

        when:
        mvc.perform(asUser(request('PUT', '/api/solutions/config/properties/31/values/11', '{"note":"why"}'))).andExpect(status().isOk())

        then:
        1 * repo.saveValue(31, 11, 'jdbc:x', 'app.properties', 'why', ['web.xml'], 'tony')
    }

    def "config should match no endpoint when the id is not a number, and answer 400 when it is too big"() {

        when:
        mvc.perform(asUser(get('/api/solutions/abc/config'))).andExpect(status().isNotFound())
        mvc.perform(asUser(get('/api/solutions/-1/config'))).andExpect(status().isNotFound())
        mvc.perform(asUser(get('/api/solutions/99999999999/config'))).andExpect(status().isBadRequest())

        then:
        0 * repo._
    }

    def "config endpoints should answer 405 when the method is wrong"() {

        expect:
        mvc.perform(asUser(delete('/api/solutions/1/config'))).andExpect(status().isMethodNotAllowed())
        mvc.perform(asUser(get('/api/solutions/config/properties/31/values/11'))).andExpect(status().isMethodNotAllowed())
        mvc.perform(asUser(post('/api/solutions/config/properties/31'))).andExpect(status().isMethodNotAllowed())
        mvc.perform(asUser(put('/api/solutions/1/config/environments/defaults'))).andExpect(status().isMethodNotAllowed())
    }
}
