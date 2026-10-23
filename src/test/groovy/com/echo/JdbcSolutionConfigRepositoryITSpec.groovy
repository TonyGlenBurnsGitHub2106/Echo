package com.echo

import spock.lang.PendingFeature

// Story 5653: JdbcSolutionConfigRepository against a real EchoTest_ database (stp_SolutionConfig*: environments,
// groups, properties, values and their other places, counts, sources, types, locate; vw_SolutionConfigPropertyState).
class JdbcSolutionConfigRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionConfigRepository repo
    int sid

    def setup() {
        repo = new JdbcSolutionConfigRepository(dataSource())
        sid = newSolution()
    }

    def "seedDefaultEnvironments should add Local, QA and Production once and refuse when the solution is missing"() {

        expect:
        repo.seedDefaultEnvironments(sid) == 3
        repo.environments(sid)*.EnvironmentName == ['Local', 'QA', 'Production']
        repo.environments(sid)*.SortOrder == [1, 2, 3]
        repo.seedDefaultEnvironments(sid) == 0
        repo.environments(sid).size() == 3
        ruleOf { repo.seedDefaultEnvironments(-1) }.message.contains('solution no longer exists')
    }

    def "saveEnvironment and deleteEnvironment should add, rename and refuse a duplicate and never drop the last environment when environments change"() {

        when:
        int a = repo.saveEnvironment(0, sid, ' Staging ', null)
        int b = repo.saveEnvironment(0, sid, 'Dev', 0)
        repo.saveEnvironment(a, 0, 'Pre-prod', 5)

        then:
        repo.environments(sid)*.EnvironmentName == ['Dev', 'Pre-prod']
        repo.environments(sid)*.SortOrder == [0, 5]
        ruleOf { repo.saveEnvironment(0, sid, 'dev', null) }.message == 'This solution already has an environment named "dev".'
        ruleOf { repo.saveEnvironment(0, sid, ' ', null) }.message.contains('needs a name')
        ruleOf { repo.saveEnvironment(0, -1, 'X', null) }.message.contains('solution no longer exists')

        when:
        boolean gone = repo.deleteEnvironment(a)

        then:
        gone
        repo.environments(sid)*.SolutionConfigEnvironmentID == [b]
        ruleOf { repo.deleteEnvironment(b) }.message.contains('at least one environment')
        !repo.deleteEnvironment(-1)
    }

    @PendingFeature(reason = 'BUG: stp_SolutionConfigEnvironment_Save (and stp_SolutionConfigProperty_Save, stp_SolutionConfigGridAxis/Column/Property_Save) put the user\'s text into a RAISERROR format string without escaping %, so a duplicate named "100% uptime" fails with SQL error 2787 (a 500) instead of the rule')
    def "saveEnvironment and saveProperty should refuse in words when a duplicate name holds a percent sign"() {

        given:
        repo.saveEnvironment(0, sid, '100% uptime', null)
        int g = repo.saveGroup(0, sid, 'G', null)
        repo.saveProperty(0, g, 'cpu%', null, 'text', null, 'tony')

        expect:
        ruleOf { repo.saveEnvironment(0, sid, '100% uptime', null) }.message.contains('already has an environment')
        ruleOf { repo.saveProperty(0, g, 'cpu%', null, 'text', null, 'tony') }.message.contains('already has a property')
    }

    def "saveGroup should store, order and edit groups and refuse blanks and missing parents when groups are saved"() {

        when:
        int b = repo.saveGroup(0, sid, 'Database', null)
        int a = repo.saveGroup(0, sid, ' Web ', 0)
        repo.saveGroup(b, 0, 'Database server', null)

        then:
        repo.groups(sid)*.SolutionConfigGroupID == [a, b]
        repo.groups(sid)*.GroupTitle == ['Web', 'Database server']
        ruleOf { repo.saveGroup(0, sid, '', null) }.message.contains('needs a name')
        ruleOf { repo.saveGroup(0, -1, 'X', null) }.message.contains('solution no longer exists')
        ruleOf { repo.saveGroup(987654321, sid, 'X', null) }.message.contains('group no longer exists')
    }

    def "saveProperty should store the property with its type and use text when the type is blank"() {

        given:
        envs(repo, sid)
        int g = repo.saveGroup(0, sid, 'G', null)

        when:
        int p = repo.saveProperty(0, g, ' db.host ', ' Where it lives ', ' HOST ', null, 'tony')
        int q = repo.saveProperty(0, g, 'db.name', null, '', null, 'tony')
        List<Map<String, Object>> props = repo.properties(sid)

        then:
        props*.SolutionConfigPropertyID == [p, q]
        props[0].PropertyKey == 'db.host'
        props[0].PropertyDescription == 'Where it lives'
        props[0].TypeCode == 'host'
        props[0].TypeName == 'Host or address'
        props[0].IsSecret == false
        props[0].SortOrder == 1
        props[0].MatchState == 'empty'
        props[0].SetCount == 0
        props[0].EnvCount == 3
        props[0].ModifiedBy == 'tony'
        props[1].TypeCode == 'text'
        props[1].PropertyDescription == null
    }

    def "saveProperty should refuse a blank name, an unknown type, a missing group, a duplicate or another solution's group when asked"() {

        given:
        int g = repo.saveGroup(0, sid, 'G', null)
        int g2 = repo.saveGroup(0, sid, 'G2', null)
        int p = repo.saveProperty(0, g, 'key', null, 'text', null, 'tony')
        int foreignGroup = repo.saveGroup(0, newSolution(), 'X', null)

        expect:
        ruleOf { repo.saveProperty(0, g, ' ', null, 'text', null, 'tony') }.message.contains('needs a name')
        ruleOf { repo.saveProperty(0, g, 'k2', null, 'banana', null, 'tony') }.message.startsWith('"banana" is not a property type')
        ruleOf { repo.saveProperty(0, -1, 'k2', null, 'text', null, 'tony') }.message.contains('group no longer exists')
        ruleOf { repo.saveProperty(0, g2, 'KEY', null, 'text', null, 'tony') }.message.contains('already has a property named')
        ruleOf { repo.saveProperty(987654321, g, 'k3', null, 'text', null, 'tony') }.message.contains('property no longer exists')
        ruleOf { repo.saveProperty(p, foreignGroup, 'key', null, 'text', null, 'tony') }.message.contains('same solution')
        repo.saveProperty(p, g2, 'key', null, 'text', null, 'rex') == p
        repo.properties(sid)[0].SolutionConfigGroupID == g2
        repo.properties(sid)[0].ModifiedBy == 'rex'
    }

    def "saveValue should store values per environment with source, note and other places and update the match state when values change"() {

        given:
        List<Integer> envIds = envs(repo, sid)
        int local = envIds[0]
        int qa = envIds[1]
        int prod = envIds[2]
        int g = repo.saveGroup(0, sid, 'G', null)
        int p = repo.saveProperty(0, g, 'url', null, 'url', null, 'tony')

        when:
        repo.saveValue(p, local, ' http://a ', ' web.config ', ' checked ', ['appsettings.json', ' web.config ', '', 'appsettings.json'], 'Rex')
        List<Map<String, Object>> v = repo.values(sid)

        then:
        v.size() == 1
        v[0].ValueText == 'http://a'
        v[0].SourceTitle == 'web.config'
        v[0].ValueNote == 'checked'
        v[0].ModifiedByCode == 'rex'
        repo.alsoSet(sid)*.SourceTitle == ['appsettings.json']   // the main source and repeats are dropped
        repo.sources(sid) == ['appsettings.json', 'web.config']
        repo.properties(sid)[0].MatchState == 'incomplete'

        when:
        repo.saveValue(p, qa, 'http://a', null, null, null, 'tony')
        repo.saveValue(p, prod, 'http://a ', null, null, null, 'tony')

        then:
        repo.properties(sid)[0].MatchState == 'all'
        repo.properties(sid)[0].SetCount == 3

        when:
        repo.saveValue(p, prod, 'http://b', null, null, null, 'tony')

        then:
        repo.properties(sid)[0].MatchState == 'some'

        when:
        repo.saveValue(p, qa, 'http://c', null, null, null, 'tony')

        then:
        repo.properties(sid)[0].MatchState == 'none'
    }

    def "saveValue should remove the cell and its other places when every part is blank"() {

        given:
        List<Integer> envIds = envs(repo, sid)
        int local = envIds[0]
        int p = repo.saveProperty(0, repo.saveGroup(0, sid, 'G', null), 'k', null, 'text', null, 'tony')
        repo.saveValue(p, local, 'x', null, null, ['elsewhere'], 'tony')
        int valueId = repo.values(sid)[0].SolutionConfigValueID as int

        when:
        repo.saveValue(p, local, '  ', '', ' ', [], 'tony')

        then:
        repo.values(sid).isEmpty()
        repo.alsoSet(sid).isEmpty()
        logRows('SolutionConfigValue', 'SolutionConfigValueID', valueId).last().DeletedDate != null
    }

    def "saveValue and saveProperty should never keep the text of a secret when a property is or becomes secret"() {

        given:
        List<Integer> envIds = envs(repo, sid)
        int local = envIds[0]
        int qa = envIds[1]
        int g = repo.saveGroup(0, sid, 'G', null)
        int secret = repo.saveProperty(0, g, 'password', null, 'secret', null, 'tony')
        int plain = repo.saveProperty(0, g, 'token', null, 'text', null, 'tony')
        repo.saveValue(plain, local, 'abc123', null, null, null, 'tony')

        when:
        repo.saveValue(secret, local, 'hunter2', 'vault', null, null, 'tony')

        then:
        repo.values(sid).find { it.SolutionConfigPropertyID == secret }.ValueText == null
        repo.values(sid).find { it.SolutionConfigPropertyID == secret }.SourceTitle == 'vault'
        repo.properties(sid).find { it.SolutionConfigPropertyID == secret }.MatchState == 'secret'

        when:
        repo.saveProperty(plain, 0, 'token', null, 'secret', null, 'tony')

        then:
        repo.values(sid).find { it.SolutionConfigPropertyID == plain }.ValueText == null
        count("SELECT COUNT(*) FROM dbo.SolutionConfigValue_LOG WHERE SolutionConfigPropertyID = ? AND ValueText = 'abc123'", plain) >= 1
    }

    def "saveValue should refuse when the property or environment is missing or the environment belongs to another solution"() {

        given:
        List<Integer> envIds = envs(repo, sid)
        int local = envIds[0]
        int p = repo.saveProperty(0, repo.saveGroup(0, sid, 'G', null), 'k', null, 'text', null, 'tony')
        int other = newSolution()
        repo.seedDefaultEnvironments(other)
        int foreignEnv = repo.environments(other)[0].SolutionConfigEnvironmentID as int

        expect:
        ruleOf { repo.saveValue(-1, local, 'x', null, null, null, 'tony') }.message.contains('property no longer exists')
        ruleOf { repo.saveValue(p, -1, 'x', null, null, null, 'tony') }.message.contains('environment no longer exists')
        ruleOf { repo.saveValue(p, foreignEnv, 'x', null, null, null, 'tony') }.message.contains('different solution')
        repo.values(sid).isEmpty()
    }

    def "counts should count per state and per group when properties are in different states"() {

        given:
        List<Integer> envIds = envs(repo, sid)
        int local = envIds[0]
        int qa = envIds[1]
        int prod = envIds[2]
        int g1 = repo.saveGroup(0, sid, 'G1', null)
        int g2 = repo.saveGroup(0, sid, 'G2', null)
        int a = repo.saveProperty(0, g1, 'a', null, 'text', null, 'tony')
        repo.saveProperty(0, g1, 'b', null, 'secret', null, 'tony')
        repo.saveProperty(0, g2, 'c', null, 'text', null, 'tony')
        [local, qa, prod].each { repo.saveValue(a, it, 'same', null, null, null, 'tony') }

        expect:
        repo.counts(sid) == ['properties': 3, 'state:all': 1, 'state:empty': 1, 'state:secret': 1,
                             ('group:' + g1): 2, ('group:' + g2): 1,
                             ('groupstate:' + g1 + '.all'): 1, ('groupstate:' + g1 + '.secret'): 1, ('groupstate:' + g2 + '.empty'): 1]
    }

    def "deleteEnvironment, deleteProperty and deleteGroup should remove the values under them when values exist"() {

        given:
        List<Integer> envIds = envs(repo, sid)
        int local = envIds[0]
        int qa = envIds[1]
        int g = repo.saveGroup(0, sid, 'G', null)
        int p1 = repo.saveProperty(0, g, 'a', null, 'text', null, 'tony')
        int p2 = repo.saveProperty(0, g, 'b', null, 'text', null, 'tony')
        [p1, p2].each { p -> [local, qa].each { e -> repo.saveValue(p, e, 'v', null, null, ['x'], 'tony') } }

        expect:
        repo.values(sid).size() == 4
        repo.deleteEnvironment(qa)
        repo.values(sid).size() == 2
        repo.deleteProperty(p1)
        !repo.deleteProperty(p1)
        repo.values(sid)*.SolutionConfigPropertyID == [p2]
        repo.deleteGroup(g)
        !repo.deleteGroup(g)
        repo.values(sid).isEmpty()
        repo.properties(sid).isEmpty()
        repo.alsoSet(sid).isEmpty()
        logRows('SolutionConfigGroup', 'SolutionConfigGroupID', g).last().DeletedDate != null
    }

    def "locate should answer the solution for an environment, a group or a property and null otherwise"() {

        given:
        List<Integer> envIds = envs(repo, sid)
        int local = envIds[0]
        int g = repo.saveGroup(0, sid, 'G', null)
        int p = repo.saveProperty(0, g, 'k', null, 'text', null, 'tony')

        expect:
        repo.locate('environment', local) == sid
        repo.locate(' GROUP ', g) == sid
        repo.locate('property', p) == sid
        repo.locate('property', -1) == null
        repo.locate('value', p) == null
    }

    def "types should list the eight seeded kinds with the secret flag when the database is fresh"() {

        expect:
        repo.types()*.TypeCode == ['text', 'path', 'host', 'port', 'url', 'number', 'flag', 'secret']
        repo.types().find { it.TypeCode == 'secret' }.IsSecret == true
    }

    /** Seeds Local / QA / Production and answers their ids in order. */
    private static List<Integer> envs(JdbcSolutionConfigRepository repo, int solutionId) {
        repo.seedDefaultEnvironments(solutionId)
        repo.environments(solutionId).collect { Map<String, Object> e -> e.SolutionConfigEnvironmentID as int }
    }
}
