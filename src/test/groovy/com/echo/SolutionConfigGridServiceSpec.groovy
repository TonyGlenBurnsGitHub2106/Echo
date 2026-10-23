package com.echo

import java.time.LocalDateTime

import com.echo.SolutionConfigGridModels.Column
import com.echo.SolutionConfigGridModels.Grid
import com.echo.SolutionConfigGridModels.Property

import spock.lang.PendingFeature
import spock.lang.Unroll

/**
 * Story 5653: the Configuration II tab's business rules -- assembly (axes, tagged columns, cells, matches,
 * counters, canImport), access on every call, merges, Colors, tags, lengths, and the cross-solution guards.
 */
class SolutionConfigGridServiceSpec extends SolutionServiceSupport {

    static final LocalDateTime MODIFIED = getLocalDateTime('10/03/2026 11:00')

    SolutionConfigGridRepository repo = Mock()
    SolutionConfigRepository oldTab = Mock()
    SolutionConfigGridService service = new SolutionConfigGridService(repo, oldTab, access)

    Map<String, Integer> locs = [
        'axis:51': 1, 'axis:52': 1, 'axis:54': 2,
        'axisvalue:61': 1, 'axisvalue:62': 1, 'axisvalue:63': 1, 'axisvalue:64': 2,
        'column:71': 1, 'column:72': 1, 'column:73': 1, 'column:74': 2,
        'group:81': 1, 'group:83': 1, 'group:82': 2,
        'property:91': 1, 'property:92': 1, 'property:93': 1, 'property:94': 2]

    Map<Integer, List<Map<String, Object>>> axes = [
        1: [[SolutionConfigGridAxisID: 51, AxisTitle: 'Environment', SortOrder: 1],
            [SolutionConfigGridAxisID: 52, AxisTitle: 'Server', SortOrder: 2]],
        2: [[SolutionConfigGridAxisID: 54, AxisTitle: 'Region', SortOrder: 1]]]
    Map<Integer, List<Map<String, Object>>> axisValues = [
        1: [[SolutionConfigGridAxisValueID: 61, SolutionConfigGridAxisID: 51, ValueTitle: 'Local', ColorHex: '#111111', SortOrder: 1],
            [SolutionConfigGridAxisValueID: 62, SolutionConfigGridAxisID: 51, ValueTitle: 'QA', ColorHex: null, SortOrder: 2],
            [SolutionConfigGridAxisValueID: 63, SolutionConfigGridAxisID: 52, ValueTitle: 'Tomcat 9', SortOrder: 1]],
        2: [[SolutionConfigGridAxisValueID: 64, SolutionConfigGridAxisID: 54, ValueTitle: 'East']]]
    Map<Integer, List<Map<String, Object>>> columns = [
        1: [[SolutionConfigGridColumnID: 71, ColumnTitle: 'Local', ColorHex: '#2563EB', IsDefaultColumn: false, SortOrder: 1],
            [SolutionConfigGridColumnID: 72, ColumnTitle: 'QA', ColorHex: null, IsDefaultColumn: 0, SortOrder: 2],
            [SolutionConfigGridColumnID: 73, ColumnTitle: 'All', ColorHex: null, IsDefaultColumn: true, SortOrder: 3]],
        2: [[SolutionConfigGridColumnID: 74, ColumnTitle: 'East']]]
    Map<Integer, List<Map<String, Object>>> tags = [
        1: [[SolutionConfigGridColumnID: 71, SolutionConfigGridAxisID: 52, SolutionConfigGridAxisValueID: 63, ValueTitle: 'Tomcat 9'],
            [SolutionConfigGridColumnID: 71, SolutionConfigGridAxisID: 51, SolutionConfigGridAxisValueID: 61, ValueTitle: 'Local'],
            [SolutionConfigGridColumnID: 72, SolutionConfigGridAxisID: 51, SolutionConfigGridAxisValueID: 62, ValueTitle: 'QA']],
        2: []]
    Map<Integer, List<Map<String, Object>>> groups = [
        1: [[SolutionConfigGridGroupID: 81, GroupTitle: 'Database', SortOrder: 1],
            [SolutionConfigGridGroupID: 83, GroupTitle: 'Empty', SortOrder: 2]],
        2: [[SolutionConfigGridGroupID: 82, GroupTitle: 'Other']]]
    Map<Integer, List<Map<String, Object>>> props = [
        1: [[SolutionConfigGridPropertyID: 91, SolutionConfigGridGroupID: 81, PropertyKey: 'db.url', PropertyDescription: 'The URL',
             TypeCode: 'url', TypeName: 'URL', IsSecret: false, SortOrder: 1],
            [SolutionConfigGridPropertyID: 92, SolutionConfigGridGroupID: 81, PropertyKey: 'db.password', TypeCode: 'secret',
             TypeName: 'Secret', IsSecret: true, SortOrder: 2],
            [SolutionConfigGridPropertyID: 93, SolutionConfigGridGroupID: 81, PropertyKey: 'timeout', TypeCode: 'number', SortOrder: 3]],
        2: [[SolutionConfigGridPropertyID: 94, SolutionConfigGridGroupID: 82, PropertyKey: 'other']]]
    Map<Integer, List<Map<String, Object>>> vals = [
        1: [[SolutionConfigGridValueID: 101, SolutionConfigGridPropertyID: 91, SolutionConfigGridColumnID: 71, ValueText: 'jdbc:a',
             SourceTitle: 'app.properties', ValueNote: 'note', StateCode: 'observed', ModifiedByCode: 'tony', ModifiedDate: MODIFIED],
            [SolutionConfigGridValueID: 102, SolutionConfigGridPropertyID: 91, SolutionConfigGridColumnID: 72, ValueText: 'JDBC:A ',
             StateCode: 'planned'],
            [SolutionConfigGridValueID: 103, SolutionConfigGridPropertyID: 93, SolutionConfigGridColumnID: 73, ValueText: '30']],
        2: []]
    Map<Integer, List<Map<String, Object>>> also = [
        1: [[SolutionConfigGridValueID: 101, SourceTitle: 'web.xml'], [SolutionConfigGridValueID: 101, SourceTitle: 'setenv.bat']],
        2: []]
    List<Map<String, Object>> oldEnvironments = []

    def setup() {
        repo.locate(_, _) >> { String kind, int id -> locs[kind + ':' + id] }
        repo.axes(_) >> { int s -> axes[s] ?: [] }
        repo.axisValues(_) >> { int s -> axisValues[s] ?: [] }
        repo.columns(_) >> { int s -> columns[s] ?: [] }
        repo.tags(_) >> { int s -> tags[s] ?: [] }
        repo.groups(_) >> { int s -> groups[s] ?: [] }
        repo.properties(_) >> { int s -> props[s] ?: [] }
        repo.values(_) >> { int s -> vals[s] ?: [] }
        repo.alsoSet(_) >> { int s -> also[s] ?: [] }
        repo.types() >> [[TypeCode: 'url', TypeName: 'URL', IsSecret: false, SortOrder: 1]]
        repo.cellStates() >> [[StateCode: 'observed', StateName: 'Observed', SortOrder: 1],
                              [StateCode: 'planned', StateName: 'Planned', SortOrder: 2]]
        repo.sources(_) >> { int s -> s == 1 ? ['app.properties'] : [] }
        oldTab.environments(_) >> { oldEnvironments }
    }

    /** Runs one named write of the grid as the caller -- the actions a data table can name. */
    private static Grid perform(SolutionConfigGridService s, SolutionCaller c, String action) {
        switch (action) {
            case 'importFromConfig': return s.importFromConfig(c, 1)
            case 'addAxis':          return s.addAxis(c, 1, 'x')
            case 'updateAxis':       return s.updateAxis(c, 51, 'x', null)
            case 'deleteAxis':       return s.deleteAxis(c, 51)
            case 'addAxisValue':     return s.addAxisValue(c, 51, 'x', null)
            case 'updateAxisValue':  return s.updateAxisValue(c, 61, 'x', null, null)
            case 'deleteAxisValue':  return s.deleteAxisValue(c, 61)
            case 'addColumn':        return s.addColumn(c, 1, 'x', null, null, [a: 'b'])
            case 'updateColumn':     return s.updateColumn(c, 71, 'x', null, null, null, [a: 'b'])
            case 'deleteColumn':     return s.deleteColumn(c, 71)
            case 'addGroup':         return s.addGroup(c, 1, 'x')
            case 'updateGroup':      return s.updateGroup(c, 81, 'x', null)
            case 'deleteGroup':      return s.deleteGroup(c, 81)
            case 'addProperty':      return s.addProperty(c, 81, 'k', null, null)
            case 'updateProperty':   return s.updateProperty(c, 91, 'k', null, null, null, null)
            case 'deleteProperty':   return s.deleteProperty(c, 91)
            case 'setValue':         return s.setValue(c, 91, 71, 'v', null, null, null, null)
            default: throw new IllegalStateException(action)
        }
    }

    /** A one-entry tag map {axis: value}; a null axis is allowed (a client can send one). */
    private static Map<String, String> tagOf(String axis, String value) {
        Map<String, String> m = new LinkedHashMap<>()
        m.put(axis, value)
        return m
    }

    // ---- read ---------------------------------------------------------------------

    def "grid should answer the axes with their values in order when the solution is visible"() {

        when:
        Grid g = service.grid(TONY, 1)

        then:
        g.solutionId() == 1
        g.axes()*.id() == [51, 52]
        g.axes()*.title() == ['Environment', 'Server']
        g.axes()[0].values()*.id() == [61, 62]
        g.axes()[0].values()[0].color() == '#111111'
        g.axes()[1].values()*.title() == ['Tomcat 9']
        g.types()*.code() == ['url']
        g.states()*.code() == ['observed', 'planned']
        g.sources() == ['app.properties']
    }

    def "grid should answer an empty value list when an axis has no values"() {

        given:
        axisValues[1].removeIf { Map<String, Object> v -> v.SolutionConfigGridAxisID == 52 }

        expect:
        service.grid(TONY, 1).axes()[1].values() == []
    }

    def "grid should sort a column's tags in the axes' order when they arrive out of order"() {

        when:
        List<Column> cols = service.grid(TONY, 1).columns()

        then:
        cols*.id() == [71, 72, 73]
        cols[0].color() == '#2563EB'
        cols[0].tags()*.axisId() == [51, 52]
        cols[0].tags()*.axisTitle() == ['Environment', 'Server']
        cols[0].tags()*.value() == ['Local', 'Tomcat 9']
        cols[0].tags()*.valueId() == [61, 63]
        cols[1].tags()*.value() == ['QA']
        cols[2].tags() == []
        !cols[0].isDefault()
        !cols[1].isDefault()
        cols[2].isDefault()
    }

    def "grid should sort a tag last with no axis title when its axis is not listed"() {

        given:
        tags[1] << [SolutionConfigGridColumnID: 72, SolutionConfigGridAxisID: 59, SolutionConfigGridAxisValueID: 69, ValueTitle: 'X']
        tags[1] << [SolutionConfigGridColumnID: 72, SolutionConfigGridAxisID: 52, SolutionConfigGridAxisValueID: 63, ValueTitle: 'Tomcat 9']

        when:
        Column col = service.grid(TONY, 1).columns()[1]

        then:
        col.tags()*.axisId() == [51, 52, 59]
        col.tags()[2].axisTitle() == null
    }

    def "grid should give properties their cells and matches when the default column stands in for blanks"() {

        when:
        List<Property> p = service.grid(TONY, 1).groups()[0].properties()

        then:
        p*.id() == [91, 92, 93]
        p[0].values()*.columnId() == [71, 72]
        p[0].values()[0].alsoSetIn() == ['web.xml', 'setenv.bat']
        p[0].values()[0].state() == 'observed'
        p[0].values()[0].modifiedDate() == MODIFIED
        p[0].values()[1].alsoSetIn() == []
        p[0].values()[1].state() == 'planned'

        and: 'the url agrees across Local and QA (trimmed, any case); each axis value holds one column'
        (p[0].matches().keySet() as List) == ['all', '51', '52']
        p[0].matches().get('all').state() == 'all'
        p[0].matches().get('51').state() == 'single'
        p[0].matches().get('52').state() == 'single'
        p[0].matches().get('52').sets()*.valueId() == [63, 0]

        and: 'the secret is secret everywhere'
        p[1].secret()
        p[1].values() == []
        p[1].matches().values()*.state() == ['secret', 'secret', 'secret']

        and: 'the timeout lives only in the default column, which every column takes'
        p[2].matches().get('all').state() == 'all'
        p[2].matches().get('all').sets()[0].columnIds() == [71, 72]
        p[2].matches().get('all').sets()[0].setCount() == 2
    }

    def "grid should never compare the default column when one exists"() {

        when:
        List<Property> p = service.grid(TONY, 1).groups()[0].properties()

        then:
        p.every { Property prop -> prop.matches().values().every { m -> m.sets().every { !it.columnIds().contains(73) } } }
    }

    def "grid should count every property once per comparison when counters are built"() {

        when:
        Grid g = service.grid(TONY, 1)

        then:
        g.counts() == ['state:all.all': 2, 'state:51.single': 2, 'state:52.single': 2,
                       'state:all.secret': 1, 'state:51.secret': 1, 'state:52.secret': 1, properties: 3]
        g.counts().keySet().last() == 'properties'
        g.groups()[0].states() == ['all.all': 2, '51.single': 2, '52.single': 2, 'all.secret': 1, '51.secret': 1, '52.secret': 1]
        g.groups()[0].propertyCount() == 3
        g.groups()[1].propertyCount() == 0
        g.groups()[1].states() == [:]
        g.groups()[1].properties() == []
    }

    def "grid should count but not show a property when its group is not listed"() {

        given:
        props[1] << [SolutionConfigGridPropertyID: 99, SolutionConfigGridGroupID: 999, PropertyKey: 'orphan']

        when:
        Grid g = service.grid(TONY, 1)

        then:
        g.counts().get('properties') == 4
        g.groups()*.properties().flatten()*.id() == [91, 92, 93]
    }

    def "grid should treat a blank as a gap when there is no default column"() {

        given:
        columns[1].removeIf { Map<String, Object> c -> c.SolutionConfigGridColumnID == 73 }

        when:
        List<Property> p = service.grid(TONY, 1).groups()[0].properties()

        then:
        p[2].matches().get('all').state() == 'empty'
        p[2].values()*.columnId() == [73]
    }

    def "grid should answer only the property counter when the solution is empty"() {

        given:
        columns[2] = []
        groups[2] = []
        props[2] = []

        when:
        Grid g = service.grid(TONY, 2)

        then:
        g.columns() == []
        g.groups() == []
        g.counts() == [properties: 0]
    }

    @Unroll
    def "grid should answer canImport #expected when #scenario"() {

        given:
        columns[2] = hasColumns ? [[SolutionConfigGridColumnID: 74]] : []
        groups[2] = hasGroups ? [[SolutionConfigGridGroupID: 82]] : []
        oldEnvironments = oldTabFilled ? [[SolutionConfigEnvironmentID: 1]] : []

        expect:
        service.grid(TONY, 2).canImport() == expected

        where:
        scenario                                            | hasColumns | hasGroups | oldTabFilled || expected
        'the grid is empty and the old tab has some'        | false      | false     | true         || true
        'the grid is empty and so is the old tab'           | false      | false     | false        || false
        'the grid has a column'                             | true       | false     | true         || false
        'the grid has a group'                              | false      | true      | true         || false
    }

    @Unroll
    def "grid should throw not found and read nothing when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.grid(caller, solutionId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.axes(_)

        where:
        scenario                         | callerName | solutionId || expectedMessage
        'the id is zero'                 | 'TONY'     | 0          || 'Solution not found.'
        'the solution does not exist'    | 'TONY'     | 3          || 'Solution not found.'
        'the client was not given it'    | 'REX'      | 2          || 'Solution not found.'
        'the client was given nothing'   | 'ZED'      | 1          || 'Solution not found.'
    }

    def "locateProperty should name the owning solution when the property is visible"() {

        expect:
        service.locateProperty(REX, 91) == [solutionId: 1, propertyId: 91]
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
        'the property does not exist'    | 'TONY'     | 999        || 'Property not found.'
        'the client was not given it'    | 'REX'      | 94         || 'Solution not found.'
    }

    // ---- import -------------------------------------------------------------------

    def "importFromConfig should import and answer the grid when visible, and import nothing when hidden"() {

        when:
        Grid g = service.importFromConfig(REX, 1)

        then:
        1 * repo.importFromConfig(1) >> [ColumnCount: 3]
        g.solutionId() == 1

        when:
        service.importFromConfig(REX, 2)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.importFromConfig(_)
    }

    // ---- axes ---------------------------------------------------------------------

    def "addAxis should accept 100 characters and refuse 101 when the name is long"() {

        when:
        service.addAxis(TONY, 1, textOf(100))

        then:
        1 * repo.saveAxis(0, 1, textOf(100), null)

        when:
        service.addAxis(TONY, 1, textOf(101))

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'An axis name may be at most 100 characters (this one is 101).'
        0 * repo.saveAxis(*_)
    }

    @Unroll
    def "updateAxis should save #expected when #scenario"() {

        when:
        service.updateAxis(HOUSE, 52, title, 9)

        then:
        1 * repo.saveAxis(52, 1, expected, 9)

        where:
        scenario               | title  || expected
        'the title is null'    | null   || 'Server'
        'the title is blank'   | '  '   || 'Server'
        'a new title is sent'  | 'Host' || 'Host'
    }

    @Unroll
    def "updateAxis should throw #expectedMessage when #scenario"() {

        given:
        locs['axis:55'] = 1
        SolutionCaller caller = callerOf(callerName)

        when:
        service.updateAxis(caller, axisId, textOf(titleLength), null)

        then:
        RuntimeException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveAxis(*_)

        where:
        scenario                            | callerName | axisId | titleLength || expectedMessage
        'the id is zero'                    | 'TONY'     | 0      | 1           || 'Axis not found.'
        'the axis does not exist'           | 'TONY'     | 50     | 1           || 'Axis not found.'
        'the axis is located but unlisted'  | 'TONY'     | 55     | 1           || 'Axis not found.'
        'the client was not given it'       | 'REX'      | 54     | 1           || 'Solution not found.'
        'the title is too long'             | 'TONY'     | 51     | 101         || 'An axis name may be at most 100 characters (this one is 101).'
    }

    def "deleteAxis should delete, refuse a failed delete and never touch a hidden axis"() {

        when:
        service.deleteAxis(TONY, 51)

        then:
        1 * repo.deleteAxis(51) >> true

        when:
        service.deleteAxis(TONY, 52)

        then:
        1 * repo.deleteAxis(52) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Axis not found.'

        when:
        service.deleteAxis(ZED, 51)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.deleteAxis(_)
    }

    @Unroll
    def "addAxisValue should store the Color #expected when #scenario"() {

        when:
        service.addAxisValue(REX, 51, 'Prod', color)

        then:
        1 * repo.saveAxisValue(0, 51, 'Prod', expected, null)

        where:
        scenario                          | color         || expected
        'no Color is sent'                | null          || null
        'an empty Color is sent'          | ''            || null
        'a blank Color is sent'           | '   '         || null
        'a lower-case Color is sent'      | '#abcdef'     || '#ABCDEF'
        'a padded mixed-case Color'       | ' #a1B2c3 '   || '#A1B2C3'
        'black is sent'                   | '#000000'     || '#000000'
    }

    @Unroll
    def "#seq. addAxisValue should refuse the Color when #scenario"() {

        when:
        service.addAxisValue(TONY, 51, 'Prod', color)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveAxisValue(*_)

        where:
        seq | scenario                        | color         || expectedMessage
        1   | 'it is a color name'            | 'red'         || 'A Color is written #RRGGBB, for example #2563EB.'
        2   | 'it has five digits'            | '#12345'      || 'A Color is written #RRGGBB, for example #2563EB.'
        3   | 'it has seven digits'           | '#1234567'    || 'A Color is written #RRGGBB, for example #2563EB.'
        4   | 'it has no hash'                | '123456'      || 'A Color is written #RRGGBB, for example #2563EB.'
        5   | 'it is not hex'                 | '#GGGGGG'     || 'A Color is written #RRGGBB, for example #2563EB.'
        6   | 'it has a space inside'         | '#12 456'     || 'A Color is written #RRGGBB, for example #2563EB.'
        7   | 'it is an rgb() form'           | 'rgb(1,2,3)'  || 'A Color is written #RRGGBB, for example #2563EB.'
        8   | 'it has two hashes'             | '##123456'    || 'A Color is written #RRGGBB, for example #2563EB.'
        9   | 'it is the short form'          | '#fff'        || 'A Color is written #RRGGBB, for example #2563EB.'
    }

    @Unroll
    def "addAxisValue should throw #expectedMessage when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.addAxisValue(caller, axisId, textOf(titleLength), null)

        then:
        RuntimeException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveAxisValue(*_)

        where:
        scenario                         | callerName | axisId | titleLength || expectedMessage
        'the title is too long'          | 'TONY'     | 51     | 101         || 'An axis value may be at most 100 characters (this one is 101).'
        'the axis id is zero'            | 'TONY'     | 0      | 1           || 'Axis not found.'
        'the client was not given it'    | 'REX'      | 54     | 1           || 'Solution not found.'
    }

    @Unroll
    def "updateAxisValue should merge title and Color when #scenario"() {

        when:
        service.updateAxisValue(TONY, 61, title, color, 4)

        then:
        1 * repo.saveAxisValue(61, 51, expectedTitle, expectedColor, 4)

        where:
        scenario                         | title | color     || expectedTitle | expectedColor
        'both are left out'              | null  | null      || 'Local'       | '#111111'
        'both are empty'                 | ''    | ''        || 'Local'       | null
        'both are sent'                  | 'Dev' | '#00ff00' || 'Dev'         | '#00FF00'
    }

    def "updateAxisValue should keep the value's own axis when the value is edited"() {

        when:
        service.updateAxisValue(TONY, 63, null, null, null)

        then:
        1 * repo.saveAxisValue(63, 52, 'Tomcat 9', null, null)
    }

    @Unroll
    def "updateAxisValue should throw #expectedMessage when #scenario"() {

        given:
        locs['axisvalue:65'] = 1
        SolutionCaller caller = callerOf(callerName)

        when:
        service.updateAxisValue(caller, valueId, textOf(titleLength), color, null)

        then:
        RuntimeException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveAxisValue(*_)

        where:
        scenario                            | callerName | valueId | titleLength | color  || expectedMessage
        'the id is zero'                    | 'TONY'     | 0       | null        | null   || 'Axis value not found.'
        'the value is located but unlisted' | 'TONY'     | 65      | null        | null   || 'Axis value not found.'
        'the client was not given it'       | 'REX'      | 64      | null        | null   || 'Solution not found.'
        'the Color is a word'               | 'TONY'     | 61      | null        | 'blue' || 'A Color is written #RRGGBB, for example #2563EB.'
        'the title is too long'             | 'TONY'     | 61      | 101         | null   || 'An axis value may be at most 100 characters (this one is 101).'
    }

    def "deleteAxisValue should delete and throw not found when nothing was deleted"() {

        when:
        service.deleteAxisValue(TONY, 61)

        then:
        1 * repo.deleteAxisValue(61) >> true

        when:
        service.deleteAxisValue(TONY, 62)

        then:
        1 * repo.deleteAxisValue(62) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Axis value not found.'
    }

    // ---- columns ------------------------------------------------------------------

    def "addColumn should save the column then tag it by trimmed names when tags are sent"() {

        given:
        Map<String, String> sentTags = [' Environment ': ' Prod ', 'Server': null]

        when:
        service.addColumn(REX, 1, 'Prod', '#ff0000', true, sentTags)

        then:
        1 * repo.saveColumn(0, 1, 'Prod', '#FF0000', true, null) >> 75

        then:
        1 * repo.setTag(75, 'Environment', 'Prod')
        1 * repo.setTag(75, 'Server', '')
    }

    @Unroll
    def "addColumn should save a non-default column without tagging when #scenario"() {

        when:
        service.addColumn(TONY, 1, 'X', null, isDefault, null)

        then:
        1 * repo.saveColumn(0, 1, 'X', null, false, null) >> 76
        0 * repo.setTag(*_)

        where:
        scenario                   | isDefault || unused
        'isDefault is null'        | null      || null
        'isDefault is false'       | false     || null
    }

    @Unroll
    def "addColumn should check every tag before it saves anything when #scenario"() {

        given:
        String axis = axisLength == null ? axisName : textOf(axisLength)
        String value = valueLength == null ? 'QA' : textOf(valueLength)
        Map<String, String> sentTags = tagOf(axis, value)

        when:
        service.addColumn(TONY, 1, 'X', null, null, sentTags)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveColumn(*_)
        0 * repo.setTag(*_)

        where:
        scenario                        | axisName      | axisLength | valueLength || expectedMessage
        'the axis name is empty'        | ''            | null       | null        || 'An axis name is required.'
        'the axis name is blank'        | '   '         | null       | null        || 'An axis name is required.'
        'the axis name is null'         | null          | null       | null        || 'An axis name is required.'
        'the axis name is too long'     | null          | 101        | null        || 'An axis name may be at most 100 characters (this one is 101).'
        'the axis value is too long'    | 'Environment' | null       | 101         || 'An axis value may be at most 100 characters (this one is 101).'
    }

    @Unroll
    def "addColumn should throw #expectedMessage when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.addColumn(caller, solutionId, textOf(titleLength), color, null, null)

        then:
        RuntimeException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveColumn(*_)

        where:
        scenario                         | callerName | solutionId | titleLength | color  || expectedMessage
        'the name is too long'           | 'TONY'     | 1          | 101         | null   || 'A column name may be at most 100 characters (this one is 101).'
        'the Color is a word'            | 'TONY'     | 1          | 1           | 'pink' || 'A Color is written #RRGGBB, for example #2563EB.'
        'the solution does not exist'    | 'TONY'     | 9          | 1           | null   || 'Solution not found.'
        'the client was not given it'    | 'REX'      | 2          | 1           | null   || 'Solution not found.'
    }

    @Unroll
    def "updateColumn should merge title, Color and default when #scenario"() {

        when:
        service.updateColumn(TONY, 71, title, color, isDefault, 5, null)

        then:
        1 * repo.saveColumn(71, 1, expectedTitle, expectedColor, expectedDefault, 5)
        0 * repo.setTag(*_)

        where:
        scenario                 | title | color     | isDefault || expectedTitle | expectedColor | expectedDefault
        'all are left out'       | null  | null      | null      || 'Local'       | '#2563EB'     | false
        'all are cleared'        | ' '   | ''        | true      || 'Local'       | null          | true
        'all are sent'           | 'Dev' | '#ABCDEF' | false     || 'Dev'         | '#ABCDEF'     | false
    }

    def "updateColumn should keep a default column the default when isDefault is left out"() {

        when:
        service.updateColumn(TONY, 73, null, null, null, null, null)

        then:
        1 * repo.saveColumn(73, 1, 'All', null, true, null)
    }

    def "updateColumn should apply only the tags it names when tags are sent"() {

        when:
        service.updateColumn(TONY, 72, null, null, null, null, ['Server': 'Tomcat 11', 'Environment': ''])

        then:
        1 * repo.saveColumn(72, *_)

        then:
        1 * repo.setTag(72, 'Server', 'Tomcat 11')
        1 * repo.setTag(72, 'Environment', '')
    }

    @Unroll
    def "updateColumn should save nothing when #scenario"() {

        given:
        locs['column:79'] = 1
        SolutionCaller caller = callerOf(callerName)
        Map<String, String> sentTags = badTag ? tagOf('', 'x') : null

        when:
        service.updateColumn(caller, columnId, null, null, null, null, sentTags)

        then:
        RuntimeException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveColumn(*_)
        0 * repo.setTag(*_)

        where:
        scenario                             | callerName | columnId | badTag || expectedMessage
        'the id is zero'                     | 'TONY'     | 0        | false  || 'Column not found.'
        'the column is located but unlisted' | 'TONY'     | 79       | false  || 'Column not found.'
        'the client was not given it'        | 'REX'      | 74       | false  || 'Solution not found.'
        'a tag has no axis name'             | 'TONY'     | 71       | true   || 'An axis name is required.'
    }

    def "deleteColumn should delete, refuse a failed delete and never touch a hidden column"() {

        when:
        service.deleteColumn(TONY, 72)

        then:
        1 * repo.deleteColumn(72) >> true

        when:
        service.deleteColumn(TONY, 71)

        then:
        1 * repo.deleteColumn(71) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Column not found.'

        when:
        service.deleteColumn(REX, 74)

        then:
        thrown(SolutionNotFoundException)
        0 * repo.deleteColumn(_)
    }

    // ---- groups -------------------------------------------------------------------

    def "addGroup should save without seeding, refuse 201 characters and refuse a hidden solution"() {

        when:
        service.addGroup(TONY, 1, textOf(200))

        then:
        1 * repo.saveGroup(0, 1, textOf(200), null)

        when:
        service.addGroup(TONY, 1, textOf(201))

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'A group name may be at most 200 characters (this one is 201).'

        when:
        service.addGroup(REX, 2, 'x')

        then:
        thrown(SolutionNotFoundException)
        0 * repo.saveGroup(*_)
    }

    def "updateGroup should merge the title and refuse a group that is located but unlisted"() {

        when:
        service.updateGroup(TONY, 81, null, 2)

        then:
        1 * repo.saveGroup(81, 1, 'Database', 2)

        when:
        locs['group:89'] = 1
        service.updateGroup(TONY, 89, 'x', 2)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == 'Group not found.'
        0 * repo.saveGroup(*_)
    }

    def "deleteGroup should delete and throw not found when nothing was deleted"() {

        when:
        service.deleteGroup(TONY, 83)

        then:
        1 * repo.deleteGroup(83) >> true

        when:
        service.deleteGroup(TONY, 81)

        then:
        1 * repo.deleteGroup(81) >> false
        thrown(SolutionNotFoundException)
    }

    // ---- properties ---------------------------------------------------------------

    @Unroll
    def "addProperty should attribute the property to #expectedCode when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.addProperty(caller, 81, 'k', 'd', 'text')

        then:
        1 * repo.saveProperty(0, 81, 'k', 'd', 'text', null, expectedCode)

        where:
        scenario               | callerName || expectedCode
        'a person adds it'     | 'TONY'     || 'tony'
        'the house adds it'    | 'HOUSE'    || 'felix'
        'a client adds it'     | 'REX'      || 'rex'
    }

    @Unroll
    def "addProperty should throw #expectedMessage when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.addProperty(caller, groupId, textOf(keyLength), textOf(descriptionLength), null)

        then:
        RuntimeException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveProperty(*_)

        where:
        scenario                         | callerName | groupId | keyLength | descriptionLength || expectedMessage
        'the key is too long'            | 'TONY'     | 81      | 201       | null              || 'A property name may be at most 200 characters (this one is 201).'
        'the description is too long'    | 'TONY'     | 81      | 1         | 2001              || 'A description may be at most 2000 characters (this one is 2001).'
        'the group id is zero'           | 'TONY'     | 0       | 1         | null              || 'Group not found.'
        'the client was not given it'    | 'REX'      | 82      | 1         | null              || 'Solution not found.'
    }

    @Unroll
    def "updateProperty should merge the fields when #scenario"() {

        when:
        service.updateProperty(TONY, 91, key, description, typeCode, null, null)

        then:
        1 * repo.saveProperty(91, 81, expectedKey, expectedDescription, expectedType, null, 'tony')

        where:
        scenario                   | key   | description | typeCode || expectedKey | expectedDescription | expectedType
        'all are left out'         | null  | null        | null     || 'db.url'    | 'The URL'           | 'url'
        'all are blank or empty'   | ' '   | ''          | ''       || 'db.url'    | ''                  | 'url'
        'all are sent'             | 'uri' | 'New'       | 'text'   || 'uri'       | 'New'               | 'text'
    }

    def "updateProperty should move the property when the group is of the same solution"() {

        when:
        service.updateProperty(TONY, 91, null, null, null, 83, null)

        then:
        1 * repo.saveProperty(91, 83, *_)
    }

    @Unroll
    def "updateProperty should refuse the move when #scenario"() {

        when:
        service.updateProperty(TONY, 91, null, null, null, groupId, null)

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'A property can only move to a group of the same solution.'
        0 * repo.saveProperty(*_)

        where:
        scenario                              | groupId || unused
        'the group is of another solution'    | 82      || null
        'the group does not exist'            | 404     || null
    }

    @Unroll
    def "updateProperty should keep the group without a lookup when #scenario"() {

        when:
        service.updateProperty(TONY, 91, null, null, null, groupId, null)

        then:
        0 * repo.locate('group', _)
        1 * repo.saveProperty(91, 81, *_)

        where:
        scenario                    | groupId || unused
        'no group is sent'          | null    || null
        'group zero is sent'        | 0       || null
        'a negative group is sent'  | -1      || null
        'the same group is sent'    | 81      || null
    }

    @Unroll
    def "updateProperty should throw #expectedMessage when #scenario"() {

        given:
        locs['property:98'] = 1
        SolutionCaller caller = callerOf(callerName)

        when:
        service.updateProperty(caller, propertyId, textOf(keyLength), textOf(descriptionLength), null, null, null)

        then:
        RuntimeException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveProperty(*_)

        where:
        scenario                               | callerName | propertyId | keyLength | descriptionLength || expectedMessage
        'the property is located but unlisted' | 'TONY'     | 98         | null      | null              || 'Property not found.'
        'the client was not given it'          | 'REX'      | 94         | null      | null              || 'Solution not found.'
        'the key is too long'                  | 'TONY'     | 91         | 201       | null              || 'A property name may be at most 200 characters (this one is 201).'
        'the description is too long'          | 'TONY'     | 91         | null      | 2001              || 'A description may be at most 2000 characters (this one is 2001).'
    }

    def "deleteProperty should delete and throw not found when nothing was deleted"() {

        when:
        service.deleteProperty(TONY, 93)

        then:
        1 * repo.deleteProperty(93) >> true

        when:
        service.deleteProperty(TONY, 91)

        then:
        1 * repo.deleteProperty(91) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'Property not found.'
    }

    // ---- a cell -------------------------------------------------------------------

    def "setValue should keep the whole cell when nothing is sent"() {

        when:
        service.setValue(TONY, 91, 71, null, null, null, null, null)

        then:
        1 * repo.saveValue(91, 71, 'jdbc:a', 'app.properties', 'note', 'observed', ['web.xml', 'setenv.bat'], 'tony')
    }

    def "setValue should normalize the state to trimmed lower case when a state is sent"() {

        when:
        service.setValue(REX, 91, 72, null, null, null, ' Unverified ', null)

        then:
        1 * repo.saveValue(91, 72, 'JDBC:A ', null, null, 'unverified', [], 'rex')
    }

    def "setValue should clear value, source, note and places when empty strings and an empty list are sent"() {

        when:
        service.setValue(TONY, 91, 71, '', '', '', null, [])

        then:
        1 * repo.saveValue(91, 71, '', '', '', 'observed', [], 'tony')
    }

    @PendingFeature(reason = 'BUG: SolutionConfigGridService.setValue documents "an empty string ... clears it", and the proc treats a blank state as "observed", but a blank state keeps the current one (planned stays planned)')
    def "setValue should reset the state to the default when a blank state is sent"() {

        when:
        service.setValue(TONY, 91, 72, null, null, null, '', null)

        then:
        1 * repo.saveValue(91, 72, 'JDBC:A ', null, null, { it == null || it == '' || it == 'observed' }, [], 'tony')
    }

    def "setValue should save an empty cell when the cell is empty and nothing is sent"() {

        when:
        service.setValue(TONY, 92, 71, null, null, null, null, null)

        then:
        1 * repo.saveValue(92, 71, null, null, null, null, [], 'tony')
    }

    def "setValue should refuse the cell when the column belongs to another solution"() {

        when:
        service.setValue(TONY, 91, 74, 'v', null, null, null, null)

        then:
        SolutionRuleException ex = thrown()
        ex.message == 'That column belongs to a different solution.'
        0 * repo.saveValue(*_)
    }

    @Unroll
    def "setValue should throw not found when #scenario"() {

        given:
        SolutionCaller caller = callerOf(callerName)

        when:
        service.setValue(caller, propertyId, columnId, 'v', null, null, null, null)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveValue(*_)

        where:
        scenario                         | callerName | propertyId | columnId || expectedMessage
        'the property id is zero'        | 'TONY'     | 0          | 71       || 'Property not found.'
        'the column id is zero'          | 'TONY'     | 91         | 0        || 'Column not found.'
        'the column does not exist'      | 'TONY'     | 91         | 700      || 'Column not found.'
        'the client was not given it'    | 'REX'      | 94         | 74       || 'Solution not found.'
        'the client was given nothing'   | 'ZED'      | 91         | 71       || 'Solution not found.'
    }

    @Unroll
    def "setValue should refuse the cell when #scenario"() {

        given:
        List<String> places = placeLength == null ? listOf(placesText) : [textOf(placeLength)]

        when:
        service.setValue(TONY, 91, 71, textOf(valueLength), textOf(sourceLength), textOf(noteLength), null, places)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * repo.saveValue(*_)

        where:
        scenario                | valueLength | sourceLength | noteLength | placeLength | placesText || expectedMessage
        'the value is 2001'     | 2001        | null         | null       | null        | null       || 'A value may be at most 2000 characters (this one is 2001).'
        'the source is 201'     | null        | 201          | null       | null        | null       || 'Where it is read from may be at most 200 characters (this one is 201).'
        'the note is 2001'      | null        | null         | 2001       | null        | null       || 'A note may be at most 2000 characters (this one is 2001).'
        'a place is 201'        | null        | null         | null       | 201         | null       || 'A place name may be at most 200 characters (this one is 201).'
        'a place has a pipe'    | null        | null         | null       | null        | 'x,a|b'    || 'A place name cannot contain |.'
    }

    def "setValue should accept the longest of everything when every field is at its limit"() {

        when:
        service.setValue(TONY, 91, 71, textOf(2000), textOf(200), textOf(2000), 'planned', [textOf(200)])

        then:
        1 * repo.saveValue(91, 71, textOf(2000), textOf(200), textOf(2000), 'planned', [textOf(200)], 'tony')
    }

    @Unroll
    def "#seq. #action should write nothing when the client was not given the solution and tries to #scenario"() {

        when:
        perform(service, ZED, action)

        then:
        thrown(SolutionNotFoundException)
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
        seq | scenario                | action             || unused
        1   | 'import'                | 'importFromConfig' || null
        2   | 'add an axis'           | 'addAxis'          || null
        3   | 'rename an axis'        | 'updateAxis'       || null
        4   | 'delete an axis'        | 'deleteAxis'       || null
        5   | 'add an axis value'     | 'addAxisValue'     || null
        6   | 'change an axis value'  | 'updateAxisValue'  || null
        7   | 'delete an axis value'  | 'deleteAxisValue'  || null
        8   | 'add a column'          | 'addColumn'        || null
        9   | 'change a column'       | 'updateColumn'     || null
        10  | 'delete a column'       | 'deleteColumn'     || null
        11  | 'add a group'           | 'addGroup'         || null
        12  | 'rename a group'        | 'updateGroup'      || null
        13  | 'delete a group'        | 'deleteGroup'      || null
        14  | 'add a property'        | 'addProperty'      || null
        15  | 'change a property'     | 'updateProperty'   || null
        16  | 'delete a property'     | 'deleteProperty'   || null
        17  | 'set a cell'            | 'setValue'         || null
    }

    @Unroll
    def "#seq. #action should answer the grid of the owning solution when #scenario"() {

        given:
        repo.deleteAxis(_) >> true
        repo.deleteAxisValue(_) >> true
        repo.deleteColumn(_) >> true
        repo.deleteGroup(_) >> true
        repo.deleteProperty(_) >> true
        repo.saveColumn(*_) >> 77

        expect:
        perform(service, TONY, action).solutionId() == expectedSolution

        where:
        seq | scenario                | action             || expectedSolution
        1   | 'import'                | 'importFromConfig' || 1
        2   | 'add an axis'           | 'addAxis'          || 1
        3   | 'rename an axis'        | 'updateAxis'       || 1
        4   | 'delete an axis'        | 'deleteAxis'       || 1
        5   | 'add an axis value'     | 'addAxisValue'     || 1
        6   | 'change an axis value'  | 'updateAxisValue'  || 1
        7   | 'delete an axis value'  | 'deleteAxisValue'  || 1
        8   | 'add a column'          | 'addColumn'        || 1
        9   | 'change a column'       | 'updateColumn'     || 1
        10  | 'delete a column'       | 'deleteColumn'     || 1
        11  | 'add a group'           | 'addGroup'         || 1
        12  | 'rename a group'        | 'updateGroup'      || 1
        13  | 'delete a group'        | 'deleteGroup'      || 1
        14  | 'add a property'        | 'addProperty'      || 1
        15  | 'change a property'     | 'updateProperty'   || 1
        16  | 'delete a property'     | 'deleteProperty'   || 1
        17  | 'set a cell'            | 'setValue'         || 1
    }
}
