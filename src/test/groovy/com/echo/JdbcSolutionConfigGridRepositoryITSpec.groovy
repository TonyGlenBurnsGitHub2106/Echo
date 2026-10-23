package com.echo

import spock.lang.PendingFeature

// Story 5653: JdbcSolutionConfigGridRepository (Configuration II) against a real EchoTest_ database
// (stp_SolutionConfigGrid*: axes, axis values, columns and their tags, groups, properties, cells, import, locate).
class JdbcSolutionConfigGridRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionConfigGridRepository repo
    int sid

    def setup() {
        repo = new JdbcSolutionConfigGridRepository(dataSource())
        sid = newSolution()
    }

    def "saveAxis and saveAxisValue should store, order and edit axes and values and refuse blanks, bad colors and duplicates"() {

        when:
        int env = repo.saveAxis(0, sid, ' Environment ', null)
        int region = repo.saveAxis(0, sid, 'Region', 0)
        int prod = repo.saveAxisValue(0, env, 'Prod', '#ff0000', null)
        int qa = repo.saveAxisValue(0, env, 'QA', null, 0)
        repo.saveAxisValue(prod, 0, 'Production', '#00aa00', null)

        then:
        repo.axes(sid)*.SolutionConfigGridAxisID == [region, env]
        repo.axes(sid)[1].AxisTitle == 'Environment'
        repo.axisValues(sid)*.SolutionConfigGridAxisValueID == [qa, prod]
        repo.axisValues(sid)[1].ValueTitle == 'Production'
        repo.axisValues(sid)[1].ColorHex == '#00AA00'
        repo.axisValues(sid)[0].ColorHex == null

        expect:
        ruleOf { repo.saveAxis(0, sid, ' ', null) }.message.endsWith('Nothing was saved.')
        ruleOf { repo.saveAxis(0, sid, 'environment', null) }.message.contains('already has an axis named')
        ruleOf { repo.saveAxis(0, -1, 'X', null) }.message.contains('solution no longer exists')
        ruleOf { repo.saveAxis(987654321, sid, 'X', null) }.message.contains('axis no longer exists')
        ruleOf { repo.saveAxisValue(0, env, 'qa', null, null) }.message.contains('already has a value named')
        ruleOf { repo.saveAxisValue(0, env, 'X', 'red', null) }.message.contains('#RRGGBB')
        ruleOf { repo.saveAxisValue(0, env, 'X', '#GGGGGG', null) }.message.contains('#RRGGBB')
        ruleOf { repo.saveAxisValue(0, -1, 'X', null, null) }.message.contains('axis no longer exists')
        ruleOf { repo.saveAxisValue(987654321, env, 'X', null, null) }.message.contains('axis value no longer exists')
        ruleOf { repo.saveAxisValue(0, env, ' ', null, null) }.message.contains('needs a name')
    }

    def "saveColumn should store a column with its color and refuse a second default column when one exists"() {

        when:
        int all = repo.saveColumn(0, sid, ' All ', '#abcdef', true, null)
        int prod = repo.saveColumn(0, sid, 'Prod', null, false, null)
        repo.saveColumn(prod, 0, 'Production', '#111111', false, 9)
        List<Map<String, Object>> cols = repo.columns(sid)

        then:
        cols*.SolutionConfigGridColumnID == [all, prod]
        cols[0].ColumnTitle == 'All'
        cols[0].ColorHex == '#ABCDEF'
        cols[0].IsDefaultColumn == true
        cols[1].ColumnTitle == 'Production'
        cols[1].SortOrder == 9
        cols[1].IsDefaultColumn == false

        expect:
        ruleOf { repo.saveColumn(prod, 0, 'Production', null, true, null) }.message == '"All" is already the column that applies to all. Only one column can be. Nothing was saved.'
        ruleOf { repo.saveColumn(0, sid, 'production', null, false, null) }.message.contains('already has a column named')
        ruleOf { repo.saveColumn(0, sid, ' ', null, false, null) }.message.contains('needs a name')
        ruleOf { repo.saveColumn(0, sid, 'X', '#12345', false, null) }.message.contains('#RRGGBB')
        ruleOf { repo.saveColumn(0, -1, 'X', null, false, null) }.message.contains('solution no longer exists')
        ruleOf { repo.saveColumn(987654321, sid, 'X', null, false, null) }.message.contains('column no longer exists')
        repo.saveColumn(all, 0, 'All', null, true, null) == all   // the default column itself may stay default
    }

    def "setTag should find or create the axis and value, re-tag, and remove the tag when the value is blank"() {

        given:
        int col = repo.saveColumn(0, sid, 'C', null, false, null)

        when:
        repo.setTag(col, ' Environment ', ' Prod ')
        List<Map<String, Object>> tags = repo.tags(sid)

        then:
        tags.size() == 1
        tags[0].SolutionConfigGridColumnID == col
        tags[0].ValueTitle == 'Prod'
        repo.axes(sid)*.AxisTitle == ['Environment']
        repo.axisValues(sid)*.ValueTitle == ['Prod']

        when:
        repo.setTag(col, 'Environment', 'QA')

        then:
        repo.tags(sid)*.ValueTitle == ['QA']
        repo.axisValues(sid)*.ValueTitle == ['Prod', 'QA']

        when:
        repo.setTag(col, 'Environment', '')
        repo.setTag(col, 'Nonexistent axis', '')

        then:
        repo.tags(sid).isEmpty()
        repo.axes(sid)*.AxisTitle == ['Environment']      // a blank value never creates an axis

        expect:
        ruleOf { repo.setTag(col, ' ', 'x') }.message.contains('Name the axis')
        ruleOf { repo.setTag(-1, 'A', 'x') }.message.contains('column no longer exists')
    }

    def "deleteAxisValue and deleteAxis should remove the tags that used them when columns are tagged"() {

        given:
        int col = repo.saveColumn(0, sid, 'C', null, false, null)
        repo.setTag(col, 'Env', 'Prod')
        repo.setTag(col, 'Region', 'East')
        int env = repo.axes(sid).find { it.AxisTitle == 'Env' }.SolutionConfigGridAxisID as int
        int east = repo.axisValues(sid).find { it.ValueTitle == 'East' }.SolutionConfigGridAxisValueID as int

        expect:
        repo.deleteAxisValue(east)
        !repo.deleteAxisValue(east)
        repo.tags(sid)*.ValueTitle == ['Prod']
        repo.deleteAxis(env)
        !repo.deleteAxis(env)
        repo.tags(sid).isEmpty()
        repo.axisValues(sid).isEmpty()              // East went with deleteAxisValue, Prod with its axis
        repo.axes(sid)*.AxisTitle == ['Region']
    }

    def "saveGroup and saveProperty should store and edit and take the property's solution from its group"() {

        when:
        int g = repo.saveGroup(0, sid, ' Web ', null)
        int g2 = repo.saveGroup(0, sid, 'Db', null)
        int p = repo.saveProperty(0, g, ' timeout ', ' seconds ', 'NUMBER', null, 'tony')
        repo.saveGroup(g2, 0, 'Database', 0)
        List<Map<String, Object>> props = repo.properties(sid)

        then:
        repo.groups(sid)*.GroupTitle == ['Database', 'Web']
        props.size() == 1
        props[0].SolutionConfigGridPropertyID == p
        props[0].SolutionID == sid
        props[0].PropertyKey == 'timeout'
        props[0].PropertyDescription == 'seconds'
        props[0].TypeCode == 'number'
        props[0].ModifiedBy == 'tony'

        when:
        repo.saveProperty(p, g2, 'timeout', null, '', null, 'rex')

        then:
        repo.properties(sid)[0].SolutionConfigGridGroupID == g2
        repo.properties(sid)[0].TypeCode == 'text'

        expect:
        ruleOf { repo.saveGroup(0, sid, ' ', null) }.message.contains('needs a name')
        ruleOf { repo.saveGroup(0, -1, 'X', null) }.message.contains('solution no longer exists')
        ruleOf { repo.saveGroup(987654321, sid, 'X', null) }.message.contains('group no longer exists')
        ruleOf { repo.saveProperty(0, g, 'TIMEOUT', null, 'text', null, 'tony') }.message.contains('already has a property named')
        ruleOf { repo.saveProperty(0, g, 'x', null, 'nope', null, 'tony') }.message.startsWith('"nope" is not a property type')
        ruleOf { repo.saveProperty(0, -1, 'x', null, 'text', null, 'tony') }.message.contains('group no longer exists')
        ruleOf { repo.saveProperty(987654321, g, 'x', null, 'text', null, 'tony') }.message.contains('property no longer exists')
        ruleOf { repo.saveProperty(p, repo.saveGroup(0, newSolution(), 'X', null), 'timeout', null, 'text', null, 'tony') }.message.contains('same solution')
        ruleOf { repo.saveProperty(0, g, ' ', null, 'text', null, 'tony') }.message.contains('needs a name')
    }

    def "saveValue should store a cell with state, source, note and other places, clear it when blank and never keep a secret's text"() {

        given:
        int col = repo.saveColumn(0, sid, 'Prod', null, false, null)
        int g = repo.saveGroup(0, sid, 'G', null)
        int p = repo.saveProperty(0, g, 'url', null, 'url', null, 'tony')
        int s = repo.saveProperty(0, g, 'pwd', null, 'secret', null, 'tony')

        when:
        repo.saveValue(p, col, ' http://x ', ' web.config ', ' note ', ' PLANNED ', ['a.json', 'web.config', 'a.json', ' '], 'Rex')
        Map<String, Object> v = repo.values(sid)[0]

        then:
        v.ValueText == 'http://x'
        v.SourceTitle == 'web.config'
        v.ValueNote == 'note'
        v.StateCode == 'planned'
        v.ModifiedByCode == 'rex'
        repo.alsoSet(sid)*.SourceTitle == ['a.json']
        repo.sources(sid) == ['a.json', 'web.config']

        when:
        repo.saveValue(p, col, 'http://y', null, null, null, null, 'tony')

        then:
        repo.values(sid)[0].StateCode == 'observed'
        repo.alsoSet(sid).isEmpty()

        when:
        repo.saveValue(s, col, 'hunter2', 'vault', null, 'observed', null, 'tony')

        then:
        repo.values(sid).find { it.SolutionConfigGridPropertyID == s }.ValueText == null

        when:
        repo.saveValue(p, col, ' ', null, null, null, null, 'tony')

        then:
        repo.values(sid)*.SolutionConfigGridPropertyID == [s]
    }

    def "saveProperty should wipe the text of its cells when a grid property becomes secret"() {

        given:
        int col = repo.saveColumn(0, sid, 'C', null, false, null)
        int g = repo.saveGroup(0, sid, 'G', null)
        int p = repo.saveProperty(0, g, 'k', null, 'text', null, 'tony')
        repo.saveValue(p, col, 'plain', null, null, null, null, 'tony')

        when:
        repo.saveProperty(p, 0, 'k', null, 'secret', null, 'tony')

        then:
        repo.values(sid)[0].ValueText == null
    }

    def "saveValue should refuse when the property or column is missing, the column is another solution's or the state is unknown"() {

        given:
        int col = repo.saveColumn(0, sid, 'C', null, false, null)
        int p = repo.saveProperty(0, repo.saveGroup(0, sid, 'G', null), 'k', null, 'text', null, 'tony')
        int foreignCol = repo.saveColumn(0, newSolution(), 'C', null, false, null)

        expect:
        ruleOf { repo.saveValue(-1, col, 'x', null, null, null, null, 'tony') }.message.contains('property no longer exists')
        ruleOf { repo.saveValue(p, -1, 'x', null, null, null, null, 'tony') }.message.contains('column no longer exists')
        ruleOf { repo.saveValue(p, foreignCol, 'x', null, null, null, null, 'tony') }.message.contains('different solution')
        ruleOf { repo.saveValue(p, col, 'x', null, null, 'guessed', null, 'tony') }.message.startsWith('"guessed" is not a cell state')
        repo.values(sid).isEmpty()
    }

    def "deleteColumn, deleteProperty and deleteGroup should remove their cells and other places when cells exist"() {

        given:
        int c1 = repo.saveColumn(0, sid, 'C1', null, false, null)
        int c2 = repo.saveColumn(0, sid, 'C2', null, false, null)
        int g = repo.saveGroup(0, sid, 'G', null)
        int p1 = repo.saveProperty(0, g, 'a', null, 'text', null, 'tony')
        int p2 = repo.saveProperty(0, g, 'b', null, 'text', null, 'tony')
        [p1, p2].each { p -> [c1, c2].each { c -> repo.saveValue(p, c, 'v', null, null, null, ['x'], 'tony') } }
        repo.setTag(c2, 'Env', 'Prod')

        expect:
        repo.values(sid).size() == 4
        repo.deleteColumn(c2)
        !repo.deleteColumn(c2)
        repo.values(sid).size() == 2
        repo.tags(sid).isEmpty()
        repo.deleteProperty(p1)
        !repo.deleteProperty(p1)
        repo.values(sid)*.SolutionConfigGridPropertyID == [p2]
        repo.deleteGroup(g)
        !repo.deleteGroup(g)
        repo.values(sid).isEmpty()
        repo.alsoSet(sid).isEmpty()
        repo.properties(sid).isEmpty()
    }

    def "importFromConfig should copy the Configuration tab into an empty grid once and refuse otherwise"() {

        given:
        JdbcSolutionConfigRepository config = new JdbcSolutionConfigRepository(dataSource())
        config.seedDefaultEnvironments(sid)
        List<Object> envs = config.environments(sid)*.SolutionConfigEnvironmentID
        int g = config.saveGroup(0, sid, 'Web', null)
        int p = config.saveProperty(0, g, 'url', 'the url', 'url', null, 'tony')
        config.saveValue(p, envs[0] as int, 'http://local', 'web.config', 'n', ['x.json'], 'tony')
        config.saveValue(p, envs[2] as int, 'http://prod', null, null, null, 'tony')

        when:
        Map<String, Object> r = repo.importFromConfig(sid)

        then:
        r == [ColumnCount: 3, GroupCount: 1, PropertyCount: 1, ValueCount: 2]
        repo.columns(sid)*.ColumnTitle == ['Local', 'QA', 'Production']
        repo.groups(sid)*.GroupTitle == ['Web']
        repo.properties(sid)[0].PropertyKey == 'url'
        repo.properties(sid)[0].PropertyDescription == 'the url'
        repo.values(sid)*.ValueText as Set == ['http://local', 'http://prod'] as Set
        repo.values(sid)*.StateCode.unique() == ['observed']
        repo.alsoSet(sid)*.SourceTitle == ['x.json']

        expect:
        ruleOf { repo.importFromConfig(sid) }.message.contains('only be copied into an empty one')
        ruleOf { repo.importFromConfig(newSolution()) }.message.contains('nothing in it to copy')
        ruleOf { repo.importFromConfig(-1) }.message.contains('solution no longer exists')
    }

    def "locate should answer the solution for every kind and null otherwise when the lookups are seeded"() {

        given:
        int axis = repo.saveAxis(0, sid, 'A', null)
        int av = repo.saveAxisValue(0, axis, 'V', null, null)
        int col = repo.saveColumn(0, sid, 'C', null, false, null)
        int g = repo.saveGroup(0, sid, 'G', null)
        int p = repo.saveProperty(0, g, 'k', null, 'text', null, 'tony')

        expect:
        repo.locate('axis', axis) == sid
        repo.locate('axisvalue', av) == sid
        repo.locate(' COLUMN ', col) == sid
        repo.locate('group', g) == sid
        repo.locate('property', p) == sid
        repo.locate('property', -1) == null
        repo.locate('cell', p) == null
        repo.cellStates()*.StateCode == ['observed', 'planned', 'unverified']
        repo.types().size() == 8
    }

    def "saveValue and deleteColumn should write Configuration II _LOG rows when cells change"() {

        given:
        int col = repo.saveColumn(0, sid, 'C', null, false, null)
        int p = repo.saveProperty(0, repo.saveGroup(0, sid, 'G', null), 'k', null, 'text', null, 'tony')

        when:
        repo.saveValue(p, col, 'one', null, null, null, null, 'tony')
        repo.saveValue(p, col, 'two', null, null, null, null, 'tony')
        int valueId = repo.values(sid)[0].SolutionConfigGridValueID as int
        repo.deleteColumn(col)

        then:
        logRows('SolutionConfigGridValue', 'SolutionConfigGridValueID', valueId)*.ValueText == ['one', 'two', 'two']
        logRows('SolutionConfigGridValue', 'SolutionConfigGridValueID', valueId).last().DeletedDate != null
        logRows('SolutionConfigGridColumn', 'SolutionConfigGridColumnID', col).last().DeletedDate != null
    }

    @PendingFeature(reason = 'BUG: the stp_SolutionConfigGrid*_Save procs build their duplicate / bad-type / bad-state messages from the user\'s text and hand them to RAISERROR as a format string, so a % in a name gives SQL error 2787 (a 500) instead of the rule')
    def "saveAxis should refuse in words when a duplicate axis name holds a percent sign"() {

        given:
        repo.saveAxis(0, sid, '99% tier', null)

        expect:
        ruleOf { repo.saveAxis(0, sid, '99% tier', null) }.message.contains('already has an axis named')
    }
}
