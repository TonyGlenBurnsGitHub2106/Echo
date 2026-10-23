package com.echo

import java.time.LocalDateTime

import com.echo.SolutionConfigModels.Config
import com.echo.SolutionConfigModels.Property

import spock.lang.Unroll

/**
 * Story 5653: the Configuration tab's business rules -- every read and write passes the solution through
 * SolutionAccess, every PUT is a merge, every text is length-checked with a message that names the field.
 */
class SolutionConfigServiceSpec extends SolutionServiceSupport {

    static final LocalDateTime MODIFIED = getLocalDateTime('10/01/2026 09:00')
    static final LocalDateTime CREATED  = getLocalDateTime('09/01/2026 08:00')

    SolutionConfigRepository repo = Mock()
    SolutionConfigService service = new SolutionConfigService(repo, access)

    Map<String, Integer> locs = [
        'environment:11': 1, 'environment:12': 1, 'environment:13': 2,
        'group:21': 1, 'group:23': 1, 'group:22': 2,
        'property:31': 1, 'property:33': 1, 'property:32': 2]

    Map<Integer, List<Map<String, Object>>> envs = [
        1: [[SolutionConfigEnvironmentID: 11, SolutionEnvironmentID: 1, EnvironmentName: 'Local', SortOrder: 1],
            [SolutionConfigEnvironmentID: 12, SolutionEnvironmentID: 2, EnvironmentName: 'QA', SortOrder: 2]],
        2: [[SolutionConfigEnvironmentID: 13, SolutionEnvironmentID: 3, EnvironmentName: 'Production', SortOrder: 1]]]
    Map<Integer, List<Map<String, Object>>> groups = [
        1: [[SolutionConfigGroupID: 21, GroupTitle: 'Database', SortOrder: 1],
            [SolutionConfigGroupID: 23, GroupTitle: 'Empty', SortOrder: 2]],
        2: [[SolutionConfigGroupID: 22, GroupTitle: 'Other', SortOrder: 1]]]
    Map<Integer, List<Map<String, Object>>> props = [
        1: [[SolutionConfigPropertyID: 31, SolutionConfigGroupID: 21, PropertyKey: 'db.url', PropertyDescription: 'The URL',
             TypeCode: 'url', TypeName: 'URL', IsSecret: false, MatchState: 'some', SetCount: 2, SortOrder: 1],
            [SolutionConfigPropertyID: 33, SolutionConfigGroupID: 21, PropertyKey: 'db.password', PropertyDescription: null,
             TypeCode: 'secret', TypeName: 'Secret', IsSecret: 1, MatchState: 'secret', SetCount: 0, SortOrder: 2]],
        2: [[SolutionConfigPropertyID: 32, SolutionConfigGroupID: 22, PropertyKey: 'other.key', TypeCode: 'text']]]
    Map<Integer, List<Map<String, Object>>> vals = [
        1: [[SolutionConfigValueID: 41, SolutionConfigPropertyID: 31, SolutionConfigEnvironmentID: 11, ValueText: 'jdbc:local',
             SourceTitle: 'application.properties', ValueNote: 'the note', ModifiedByCode: 'tony', ModifiedByName: 'Tony',
             ModifiedDate: MODIFIED, CreatedDate: CREATED],
            [SolutionConfigValueID: 42, SolutionConfigPropertyID: 31, SolutionConfigEnvironmentID: 12, ValueText: 'jdbc:qa',
             SourceTitle: 'env', ValueNote: null, CreatedDate: CREATED]],
        2: []]
    Map<Integer, List<Map<String, Object>>> also = [
        1: [[SolutionConfigValueID: 41, SourceTitle: 'web.xml'], [SolutionConfigValueID: 41, SourceTitle: 'setenv.bat'],
            [SolutionConfigValueID: 42, SourceTitle: 'docker.env']],
        2: []]
    Map<String, Integer> counts1 = [properties: 2, 'group:21': 2, 'groupstate:21.some': 1, 'groupstate:21.secret': 1,
                                    'groupstate:21.all': 0, 'state:some': 1]

    def setup() {
        repo.locate(_, _) >> { String kind, int id -> locs[kind + ':' + id] }
        repo.environments(_) >> { int s -> envs[s] ?: [] }
        repo.groups(_) >> { int s -> groups[s] ?: [] }
        repo.properties(_) >> { int s -> props[s] ?: [] }
        repo.values(_) >> { int s -> vals[s] ?: [] }
        repo.alsoSet(_) >> { int s -> also[s] ?: [] }
        repo.counts(_) >> { int s -> s == 1 ? counts1 : [:] }
        repo.types() >> [[TypeCode: 'url', TypeName: 'URL', IsSecret: false, SortOrder: 1],
                         [TypeCode: 'secret', TypeName: 'Secret', IsSecret: true, SortOrder: 2]]
        repo.sources(_) >> { int s -> s == 1 ? ['application.properties', 'env'] : [] }
    }

    /** Runs one named write of the tab as the caller -- the actions a data table can name. */
    private static Config perform(SolutionConfigService s, SolutionCaller c, String action) {
        switch (action) {
            case 'seedDefaults':      return s.seedDefaults(c, 1)
            case 'addEnvironment':    return s.addEnvironment(c, 1, 'x')
            case 'updateEnvironment': return s.updateEnvironment(c, 11, 'x', 1)
            case 'deleteEnvironment': return s.deleteEnvironment(c, 11)
            case 'addGroup':          return s.addGroup(c, 1, 'x')
            case 'updateGroup':       return s.updateGroup(c, 21, 'x', 1)
            case 'deleteGroup':       return s.deleteGroup(c, 21)
            case 'addProperty':       return s.addProperty(c, 21, 'k', null, null)
            case 'updateProperty':    return s.updateProperty(c, 31, 'k', null, null, null, null)
            case 'deleteProperty':    return s.deleteProperty(c, 31)
            case 'setValue':          return s.setValue(c, 31, 11, 'v', null, null, null)
            default: throw new IllegalStateException(action)
        }
    }

    // ---- read ---------------------------------------------------------------------

    def "config should assemble the whole tab when the solution is visible"() {

        when:
        Config c = service.config(TONY, 1)

        then:
        c.solutionId() == 1
        c.environments()*.id() == [11, 12]
        c.environments()*.title() == ['Local', 'QA']
        c.environments()[0].environmentNameId() == 1
        c.groups()*.id() == [21, 23]
        c.groups()[0].title() == 'Database'
        c.groups()[0].propertyCount() == 2
        c.groups()[1].propertyCount() == 0
        c.groups()[1].properties() == []
        c.groups()[1].states() == [:]
        c.types()*.code() == ['url', 'secret']
        c.types()[1].secret()
        c.sources() == ['application.properties', 'env']
        c.counts() == counts1

        and: 'a group counts only the states it has, in the fixed order'
        c.groups()[0].states() == [some: 1, secret: 1]
        (c.groups()[0].states().keySet() as List) == ['some', 'secret']
    }

    def "config should attach each cell and its other places to its property when cells exist"() {

        when:
        Property url = service.config(TONY, 1).groups()[0].properties()[0]
        Property pw = service.config(TONY, 1).groups()[0].properties()[1]

        then:
        url.id() == 31
        url.key() == 'db.url'
        url.matchState() == 'some'
        url.setCount() == 2
        !url.secret()
        url.values()*.environmentId() == [11, 12]
        url.values()[0].value() == 'jdbc:local'
        url.values()[0].source() == 'application.properties'
        url.values()[0].note() == 'the note'
        url.values()[0].alsoSetIn() == ['web.xml', 'setenv.bat']
        url.values()[0].modifiedByCode() == 'tony'
        url.values()[0].modifiedDate() == MODIFIED
        url.values()[1].alsoSetIn() == ['docker.env']
        url.values()[1].modifiedDate() == CREATED

        and: 'a property with no cells has an empty list, and a secret says so'
        pw.values() == []
        pw.secret()
        pw.description() == null
    }

    def "config should leave a property out of every group when its group is not listed"() {

        given:
        props[1] << [SolutionConfigPropertyID: 39, SolutionConfigGroupID: 999, PropertyKey: 'orphan']

        when:
        Config c = service.config(TONY, 1)

        then:
        c.groups()*.properties().flatten()*.id() == [31, 33]
    }

    def "config should answer an empty tab when the solution has no counters or sources"() {

        when:
        Config c = service.config(TONY, 2)

        then:
        c.solutionId() == 2
        c.groups()[0].properties()*.id() == [32]
        c.groups()[0].states() == [:]
        c.groups()[0].propertyCount() == 0
        c.counts() == [:]
        c.sources() == []
    }

    @Unroll
    def "config should throw not found when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.config(caller, solutionId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Solution not found.'
        0 * repo.environments(_)

        where:
        scenario                               | callerName | solutionId || unused
        'the id is zero'                       | 'TONY'     | 0          || null
        'the id is negative'                   | 'TONY'     | -1         || null
        'the solution does not exist'          | 'TONY'     | 999        || null
        'the house asks for a missing one'     | 'HOUSE'    | 999        || null
        'the client was not given it'          | 'REX'      | 2          || null
        'the client was given nothing'         | 'ZED'      | 1          || null
    }

    @Unroll
    def "config should answer the tab when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        expect:
        service.config(caller, 1).solutionId() == expected

        where:
        scenario                          | callerName || expected
        'a person asks'                   | 'TONY'     || 1
        'the house asks'                  | 'HOUSE'    || 1
        'a client it was shared with asks' | 'REX'     || 1
    }

    def "locateProperty should name the owning solution when the property is visible"() {

        expect:
        service.locateProperty(TONY, 31) == [solutionId: 1, propertyId: 31]
        (service.locateProperty(TONY, 31).keySet() as List) == ['solutionId', 'propertyId']
        service.locateProperty(REX, 33) == [solutionId: 1, propertyId: 33]
    }

    @Unroll
    def "locateProperty should throw not found when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.locateProperty(caller, propertyId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                         | callerName | propertyId || expectedMessage
        'the id is zero'                 | 'TONY'     | 0          || 'Property not found.'
        'the id is negative'             | 'TONY'     | -5         || 'Property not found.'
        'the property does not exist'    | 'TONY'     | 9999       || 'Property not found.'
        'the client was not given it'    | 'REX'      | 32         || 'Solution not found.'
        'the client was given nothing'   | 'ZED'      | 31         || 'Solution not found.'
    }

    def "locateProperty should not reach the repository when the id is zero"() {

        when:
        service.locateProperty(TONY, 0)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.locate(_, _)
    }

    // ---- environments -------------------------------------------------------------

    def "seedDefaults should ask for the standard environments when the solution is visible"() {

        when:
        Config c = service.seedDefaults(TONY, 1)

        then:
        1 * repo.seedDefaultEnvironments(1) >> 3
        c.solutionId() == 1
    }

    def "seedDefaults should write nothing when the solution is hidden"() {

        when:
        service.seedDefaults(ZED, 1)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.seedDefaultEnvironments(_)
    }

    def "addEnvironment should save a new environment when a client it was shared with asks"() {

        when:
        service.addEnvironment(REX, 1, 'Staging')

        then:
        1 * repo.saveEnvironment(0, 1, 'Staging', null)
    }

    @Unroll
    def "addEnvironment should accept the name when #scenario"() {

        given:
        String name = padding + textOf(length) + padding

        when:
        service.addEnvironment(TONY, 1, name)

        then:
        noExceptionThrown()
        1 * repo.saveEnvironment(0, 1, name, null)

        where:
        scenario                                  | length | padding || unused
        'it is exactly 100 characters'            | 100    | ''      || null
        'it is 100 characters inside blanks'      | 100    | '  '    || null
        'it is one character'                     | 1      | ''      || null
    }

    def "addEnvironment should refuse the name when it is 101 characters"() {

        when:
        service.addEnvironment(TONY, 1, textOf(101))

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'An environment name may be at most 100 characters (this one is 101).'
        0 * repo.saveEnvironment(*_)
    }

    @Unroll
    def "updateEnvironment should save #expected when #scenario"() {

        when:
        service.updateEnvironment(TONY, 12, title, 5)

        then:
        1 * repo.saveEnvironment(12, 1, expected, 5)

        where:
        scenario                 | title     || expected
        'the title is null'      | null      || 'QA'
        'the title is empty'     | ''        || 'QA'
        'the title is blank'     | '   '     || 'QA'
        'a new title is sent'    | 'Testing' || 'Testing'
    }

    @Unroll
    def "updateEnvironment should throw #expectedException when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)
        String title = textOf(titleLength)

        when:
        service.updateEnvironment(caller, environmentId, title, null)

        then:
        RuntimeException ex = thrown()
        ex.getClass().simpleName == expectedException
        0 * repo.saveEnvironment(*_)

        where:
        scenario                                | callerName | environmentId | titleLength || expectedException
        'the name is too long'                  | 'TONY'     | 11            | 101         || 'IllegalArgumentException'
        'the id is zero'                        | 'TONY'     | 0             | 1           || 'SolutionNotFoundException'
        'the environment does not exist'        | 'TONY'     | 404           | 1           || 'SolutionNotFoundException'
        'the client was not given its solution' | 'REX'      | 13            | 1           || 'SolutionNotFoundException'
        'the client was given nothing'          | 'ZED'      | 11            | 1           || 'SolutionNotFoundException'
    }

    def "deleteEnvironment should answer the tab when deleted and throw not found when nothing was deleted"() {

        when:
        Config c = service.deleteEnvironment(TONY, 11)

        then:
        1 * repo.deleteEnvironment(11) >> true
        c.solutionId() == 1

        when:
        service.deleteEnvironment(TONY, 12)

        then:
        1 * repo.deleteEnvironment(12) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Environment not found.'
    }

    def "deleteEnvironment should never delete when the environment is hidden"() {

        when:
        service.deleteEnvironment(REX, 13)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.deleteEnvironment(_)
    }

    // ---- groups -------------------------------------------------------------------

    def "addGroup should seed the standard environments first when a group is added"() {

        when:
        service.addGroup(TONY, 1, 'Logging')

        then:
        1 * repo.seedDefaultEnvironments(1)

        then:
        1 * repo.saveGroup(0, 1, 'Logging', null)
    }

    def "addGroup should accept 200 characters and refuse 201 when the name is long"() {

        when:
        service.addGroup(TONY, 1, textOf(200))

        then:
        1 * repo.saveGroup(0, 1, textOf(200), null)

        when:
        service.addGroup(TONY, 1, textOf(201))

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'A group name may be at most 200 characters (this one is 201).'
        0 * repo.saveGroup(*_)
    }

    @Unroll
    def "addGroup should seed and save nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.addGroup(caller, solutionId, 'x')

        then:
        thrown(SolutionNotFoundException)
        0 * repo.seedDefaultEnvironments(_)
        0 * repo.saveGroup(*_)

        where:
        scenario                         | callerName | solutionId || unused
        'the solution does not exist'    | 'TONY'     | 3          || null
        'the client was not given it'    | 'REX'      | 2          || null
        'the client was given nothing'   | 'ZED'      | 1          || null
    }

    @Unroll
    def "updateGroup should save #expected when #scenario"() {

        given:
        Integer sortOrder = sort

        when:
        service.updateGroup(HOUSE, 21, title, sortOrder)

        then:
        1 * repo.saveGroup(21, 1, expected, sortOrder)

        where:
        scenario                         | title   | sort || expected
        'title and order are left out'   | null    | null || 'Database'
        'the title is blank'             | ' '     | 3    || 'Database'
        'a new title is sent'            | 'Cache' | 1    || 'Cache'
    }

    @Unroll
    def "updateGroup should throw #expectedException when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.updateGroup(caller, groupId, textOf(titleLength), null)

        then:
        RuntimeException ex = thrown()
        ex.getClass().simpleName == expectedException
        0 * repo.saveGroup(*_)

        where:
        scenario                         | callerName | groupId | titleLength || expectedException
        'the title is too long'          | 'TONY'     | 21      | 201         || 'IllegalArgumentException'
        'the id is negative'             | 'TONY'     | -1      | 1           || 'SolutionNotFoundException'
        'the group does not exist'       | 'TONY'     | 77      | 1           || 'SolutionNotFoundException'
        'the client was not given it'    | 'REX'      | 22      | 1           || 'SolutionNotFoundException'
    }

    def "deleteGroup should delete, refuse a failed delete and never touch a hidden group"() {

        when:
        service.deleteGroup(TONY, 21)

        then:
        1 * repo.deleteGroup(21) >> true

        when:
        service.deleteGroup(TONY, 23)

        then:
        1 * repo.deleteGroup(23) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Group not found.'

        when:
        service.deleteGroup(ZED, 21)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.deleteGroup(_)
    }

    // ---- properties ---------------------------------------------------------------

    @Unroll
    def "addProperty should seed then save the property attributed to #expectedCode when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.addProperty(caller, 21, 'cache.ttl', 'How long', 'number')

        then:
        1 * repo.seedDefaultEnvironments(1)

        then:
        1 * repo.saveProperty(0, 21, 'cache.ttl', 'How long', 'number', null, expectedCode)

        where:
        scenario                 | callerName || expectedCode
        'a person adds it'       | 'TONY'     || 'tony'
        'the house adds it'      | 'HOUSE'    || 'felix'
        'a client adds it'       | 'REX'      || 'rex'
    }

    @Unroll
    def "addProperty should refuse the text when #scenario"() {

        when:
        service.addProperty(TONY, 21, textOf(keyLength), textOf(descriptionLength), null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveProperty(*_)

        where:
        scenario                        | keyLength | descriptionLength || expectedMessage
        'the key is 201 characters'     | 201       | null              || 'A property name may be at most 200 characters (this one is 201).'
        'the description is 2001'       | 1         | 2001              || 'A description may be at most 2000 characters (this one is 2001).'
    }

    @Unroll
    def "addProperty should accept the longest key when #scenario"() {

        given:
        String description = textOf(descriptionLength)

        when:
        service.addProperty(TONY, 21, textOf(200), description, null)

        then:
        1 * repo.saveProperty(0, 21, textOf(200), description, null, null, 'tony')

        where:
        scenario                                 | descriptionLength || unused
        'the description is 2000 characters'     | 2000              || null
        'there is no description'                | null              || null
    }

    @Unroll
    def "addProperty should write nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.addProperty(caller, groupId, 'k', null, null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.seedDefaultEnvironments(_)
        0 * repo.saveProperty(*_)

        where:
        scenario                           | callerName | groupId || expectedMessage
        'the group id is zero'             | 'TONY'     | 0       || 'Group not found.'
        'the group does not exist'         | 'TONY'     | 500     || 'Group not found.'
        'the client was not given it'      | 'REX'      | 22      || 'Solution not found.'
    }

    @Unroll
    def "updateProperty should merge the fields when #scenario"() {

        when:
        service.updateProperty(TONY, 31, key, description, typeCode, null, 4)

        then:
        1 * repo.saveProperty(31, 21, expectedKey, expectedDescription, expectedType, 4, 'tony')

        where:
        scenario                             | key      | description | typeCode || expectedKey | expectedDescription | expectedType
        'every field is left out'            | null     | null        | null     || 'db.url'    | 'The URL'           | 'url'
        'every field is empty'               | ''       | ''          | ''       || 'db.url'    | ''                  | 'url'
        'key and type are blank'             | '  '     | 'New'       | ' '      || 'db.url'    | 'New'               | 'url'
        'key and type are sent'              | 'db.uri' | null        | 'text'   || 'db.uri'    | 'The URL'           | 'text'
    }

    @Unroll
    def "updateProperty should keep the group without a lookup when #scenario"() {

        when:
        service.updateProperty(TONY, 31, null, null, null, groupId, null)

        then:
        0 * repo.locate('group', _)
        1 * repo.saveProperty(31, 21, *_)

        where:
        scenario                    | groupId || unused
        'no group is sent'          | null    || null
        'group zero is sent'        | 0       || null
        'a negative group is sent'  | -3      || null
        'the same group is sent'    | 21      || null
    }

    def "updateProperty should move the property when the group is of the same solution"() {

        when:
        service.updateProperty(TONY, 31, null, null, null, 23, null)

        then:
        1 * repo.saveProperty(31, 23, 'db.url', 'The URL', 'url', null, 'tony')
    }

    @Unroll
    def "updateProperty should refuse the move when #scenario"() {

        when:
        service.updateProperty(TONY, 31, null, null, null, groupId, null)

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'A property can only move to a group of the same solution.'
        0 * repo.saveProperty(*_)

        where:
        scenario                                | groupId || unused
        'the group is of another solution'      | 22      || null
        'the group does not exist'              | 999     || null
    }

    def "updateProperty should throw not found when the property is located but not listed"() {

        given:
        locs['property:35'] = 1

        when:
        service.updateProperty(TONY, 35, 'k', null, null, null, null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Property not found.'
        0 * repo.saveProperty(*_)
    }

    @Unroll
    def "updateProperty should throw #expectedException when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.updateProperty(caller, propertyId, textOf(keyLength), textOf(descriptionLength), null, null, null)

        then:
        RuntimeException ex = thrown()
        ex.getClass().simpleName == expectedException
        0 * repo.saveProperty(*_)

        where:
        scenario                         | callerName | propertyId | keyLength | descriptionLength || expectedException
        'the key is too long'            | 'TONY'     | 31         | 201       | null              || 'IllegalArgumentException'
        'the description is too long'    | 'TONY'     | 31         | null      | 2001              || 'IllegalArgumentException'
        'the client was not given it'    | 'REX'      | 32         | 1         | null              || 'SolutionNotFoundException'
        'the client was given nothing'   | 'ZED'      | 31         | 1         | null              || 'SolutionNotFoundException'
    }

    def "deleteProperty should delete, refuse a failed delete and never touch a hidden property"() {

        when:
        service.deleteProperty(REX, 31)

        then:
        1 * repo.deleteProperty(31) >> true

        when:
        service.deleteProperty(TONY, 33)

        then:
        1 * repo.deleteProperty(33) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Property not found.'

        when:
        service.deleteProperty(REX, 32)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.deleteProperty(_)
    }

    // ---- a cell -------------------------------------------------------------------

    def "setValue should keep the whole cell when every field is left out"() {

        when:
        service.setValue(TONY, 31, 11, null, null, null, null)

        then:
        1 * repo.saveValue(31, 11, 'jdbc:local', 'application.properties', 'the note', ['web.xml', 'setenv.bat'], 'tony')
    }

    def "setValue should clear the fields when empty strings and an empty list are sent"() {

        when:
        service.setValue(TONY, 31, 11, '', '', '', [])

        then:
        1 * repo.saveValue(31, 11, '', '', '', [], 'tony')
    }

    def "setValue should replace what is sent and keep the rest when some fields are sent"() {

        when:
        service.setValue(HOUSE, 31, 12, 'jdbc:new', null, 'why', ['a', 'b'])

        then:
        1 * repo.saveValue(31, 12, 'jdbc:new', 'env', 'why', ['a', 'b'], 'felix')
    }

    def "setValue should save an empty cell when the cell is empty and nothing is sent"() {

        when:
        service.setValue(TONY, 33, 12, null, null, null, null)

        then:
        1 * repo.saveValue(33, 12, null, null, null, [], 'tony')
    }

    def "setValue should read the other places only for the answer when the cell is empty"() {

        when:
        service.setValue(TONY, 33, 11, 'v', null, null, null)

        then:
        1 * repo.alsoSet(1) >> []
        1 * repo.saveValue(33, 11, 'v', null, null, [], 'tony')
    }

    def "setValue should refuse the cell when the environment belongs to another solution"() {

        when:
        service.setValue(TONY, 31, 13, 'v', null, null, null)

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'That environment belongs to a different solution.'
        0 * repo.saveValue(*_)
    }

    @Unroll
    def "setValue should throw not found when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.setValue(caller, propertyId, environmentId, 'v', null, null, null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveValue(*_)

        where:
        scenario                           | callerName | propertyId | environmentId || expectedMessage
        'the property id is zero'          | 'TONY'     | 0          | 11            || 'Property not found.'
        'the environment id is zero'       | 'TONY'     | 31         | 0             || 'Environment not found.'
        'the environment does not exist'   | 'TONY'     | 31         | 404           || 'Environment not found.'
        'the client was not given it'      | 'REX'      | 32         | 13            || 'Solution not found.'
        'the client was given nothing'     | 'ZED'      | 31         | 11            || 'Solution not found.'
    }

    def "setValue should refuse a place name when it contains a pipe"() {

        when:
        service.setValue(TONY, 31, 11, 'v', null, null, ['ok', 'bad|name'])

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'A place name cannot contain |.'
        0 * repo.saveValue(*_)
    }

    @Unroll
    def "setValue should refuse the cell when #scenario"() {

        given:
        List<String> places = placeLength == null ? null : ['a', textOf(placeLength)]

        when:
        service.setValue(TONY, 31, 11, textOf(valueLength), textOf(sourceLength), textOf(noteLength), places)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveValue(*_)

        where:
        scenario                   | valueLength | sourceLength | noteLength | placeLength || expectedMessage
        'the value is 2001'        | 2001        | null         | null       | null        || 'A value may be at most 2000 characters (this one is 2001).'
        'the source is 201'        | null        | 201          | null       | null        || 'Where it is read from may be at most 200 characters (this one is 201).'
        'the note is 2001'         | null        | null         | 2001       | null        || 'A note may be at most 2000 characters (this one is 2001).'
        'a place is 201'           | null        | null         | null       | 201         || 'A place name may be at most 200 characters (this one is 201).'
    }

    def "setValue should accept the longest of everything when a place is null"() {

        when:
        service.setValue(TONY, 31, 11, textOf(2000), textOf(200), textOf(2000), [textOf(200), null])

        then:
        1 * repo.saveValue(31, 11, textOf(2000), textOf(200), textOf(2000), [textOf(200), null], 'tony')
    }

    def "setValue should attribute the cell to the agent when an agent sets it"() {

        when:
        service.setValue(REX, 31, 11, 'v', null, null, null)

        then:
        1 * repo.saveValue(31, 11, 'v', _, _, _, 'rex')
    }

    @Unroll
    def "#seq. #action should write nothing when the client was not given the solution and tries to #scenario"() {

        when:
        perform(service, ZED, action)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.seedDefaultEnvironments(_)
        0 * repo.saveEnvironment(*_)
        0 * repo.saveGroup(*_)
        0 * repo.saveProperty(*_)
        0 * repo.saveValue(*_)
        0 * repo.deleteEnvironment(_)
        0 * repo.deleteGroup(_)
        0 * repo.deleteProperty(_)

        where:
        seq | scenario               | action              || unused
        1   | 'seed defaults'        | 'seedDefaults'      || null
        2   | 'add an environment'   | 'addEnvironment'    || null
        3   | 'rename one'           | 'updateEnvironment' || null
        4   | 'delete one'           | 'deleteEnvironment' || null
        5   | 'add a group'          | 'addGroup'          || null
        6   | 'rename a group'       | 'updateGroup'       || null
        7   | 'delete a group'       | 'deleteGroup'       || null
        8   | 'add a property'       | 'addProperty'       || null
        9   | 'change a property'    | 'updateProperty'    || null
        10  | 'delete a property'    | 'deleteProperty'    || null
        11  | 'set a cell'           | 'setValue'          || null
    }

    @Unroll
    def "#seq. #action should answer the whole tab of the owning solution when #scenario"() {

        given:
        repo.deleteEnvironment(_) >> true
        repo.deleteGroup(_) >> true
        repo.deleteProperty(_) >> true

        when:
        Config c = perform(service, TONY, action)

        then:
        c.solutionId() == expectedSolution
        c.groups()*.id() == [21, 23]

        where:
        seq | scenario               | action              || expectedSolution
        1   | 'seed defaults'        | 'seedDefaults'      || 1
        2   | 'add an environment'   | 'addEnvironment'    || 1
        3   | 'rename one'           | 'updateEnvironment' || 1
        4   | 'delete one'           | 'deleteEnvironment' || 1
        5   | 'add a group'          | 'addGroup'          || 1
        6   | 'rename a group'       | 'updateGroup'       || 1
        7   | 'delete a group'       | 'deleteGroup'       || 1
        8   | 'add a property'       | 'addProperty'       || 1
        9   | 'change a property'    | 'updateProperty'    || 1
        10  | 'delete a property'    | 'deleteProperty'    || 1
        11  | 'set a cell'           | 'setValue'          || 1
    }
}
