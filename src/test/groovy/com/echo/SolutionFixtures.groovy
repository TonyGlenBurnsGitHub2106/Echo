package com.echo

import java.lang.reflect.Constructor
import java.lang.reflect.RecordComponent

import com.echo.ApiClientModels.ApiClient
import com.echo.ApiClientModels.Credential
import com.echo.ApiClientModels.SolutionLink
import com.echo.SolutionModels.CodeName
import com.echo.SolutionModels.Item
import com.echo.SolutionModels.LogEntry
import com.echo.SolutionModels.Lookups
import com.echo.SolutionModels.Member
import com.echo.SolutionModels.Note
import com.echo.SolutionModels.NoteStep
import com.echo.SolutionModels.NoteVersion
import com.echo.SolutionModels.Project
import com.echo.SolutionModels.Solution
import com.echo.SolutionModels.Status

/**
 * Story 5653: builders for the Solutions-family model records, shared by the Solution* specs.
 * A record is built from a map of its component names; anything left out takes the type's
 * zero value (0, false, null), so a spec names only what it is about. Fixtures are built in a
 * feature's given: block, never in a where: table.
 */
class SolutionFixtures {

    static final SolutionCaller PERSON = new SolutionCaller(SolutionCaller.Kind.USER, 'tony', null)
    static final SolutionCaller HOUSE = new SolutionCaller(SolutionCaller.Kind.SERVICE, 'felix', null)
    static final SolutionCaller AGENT = new SolutionCaller(SolutionCaller.Kind.CLIENT, 'rex', 7)

    static final int AGENT_ID = 7
    static final String AGENT_CODE = 'rex'
    static final String AGENT_KEY = 'rex-key'

    /** The caller a where: table names: "person", "house", "agent" or "none" (null). */
    static SolutionCaller caller(String name) {
        switch (name) {
            case 'person': return PERSON
            case 'house': return HOUSE
            case 'agent': return AGENT
            case 'none': return null
            default: throw new IllegalArgumentException('Unknown caller ' + name)
        }
    }

    /** A where: table's list cell: null stays null, "" is the empty list, items split on ";", "<null>" is a null item. */
    static List<String> textList(String cell) {
        if (cell == null) {
            return null
        }
        if (cell.isEmpty()) {
            return []
        }
        return cell.split(';', -1).collect { String s -> s == '<null>' ? null : s }
    }

    /** A where: table's list of whole numbers: null stays null, "" is the empty list, items split on ";". */
    static List<Integer> intList(String cell) {
        List<String> parts = textList(cell)
        return parts == null ? null : parts.collect { String s -> Integer.valueOf(s.trim()) }
    }

    static <T> T record(Class<T> type, Map<String, Object> values) {
        RecordComponent[] comps = type.recordComponents
        Set<String> names = comps.collect { RecordComponent c -> c.name } as Set
        Set<String> unknown = values.keySet() - names
        assert !unknown: "${type.simpleName} has no component(s) ${unknown}"
        List<Object> args = comps.collect { RecordComponent c -> values.containsKey(c.name) ? values[c.name] : zero(c.type) }
        Constructor<T> ctor = type.getDeclaredConstructor(comps.collect { RecordComponent c -> c.type } as Class[])
        ctor.accessible = true
        return ctor.newInstance(args as Object[])
    }

    private static Object zero(Class<?> type) {
        if (type == int) {
            return 0
        }
        if (type == long) {
            return 0L
        }
        if (type == boolean) {
            return false
        }
        return null
    }

    static Solution solution(Map<String, Object> m = [:]) {
        record(Solution, [id: 1, name: 'Alpha', statusCode: 'todo', ownerCode: 'tony'] + m)
    }

    static Project project(Map<String, Object> m = [:]) {
        record(Project, [id: 10, solutionId: 1, name: 'Deploy', statusCode: 'todo', ownerCode: 'tony', sortOrder: 1] + m)
    }

    static Item item(Map<String, Object> m = [:]) {
        record(Item, [id: 100, projectId: 10, solutionId: 1, title: 'Upgrade', statusCode: 'todo', priority: 3, sortOrder: 1] + m)
    }

    static LogEntry logEntry(Map<String, Object> m = [:]) {
        record(LogEntry, [id: 200, solutionId: 1, sequenceNumber: 1, typeCode: 'note', authorCode: 'tony', text: 'did it'] + m)
    }

    static Note note(Map<String, Object> m = [:]) {
        record(Note, [id: 300, solutionId: 1, typeCode: 'runbook', title: 'How to', body: 'body', currentVersionNumber: 1, steps: []] + m)
    }

    static NoteStep step(Map<String, Object> m = [:]) {
        record(NoteStep, [id: 31, noteId: 300, stepNumber: 1, title: 'Stop it'] + m)
    }

    static NoteVersion version(Map<String, Object> m = [:]) {
        record(NoteVersion, [noteId: 300, versionNumber: 1, body: 'body'] + m)
    }

    static Lookups lookups() {
        List<Status> statuses = ['todo', 'inprogress', 'blocked', 'done', 'cancelled'].withIndex().collect { String c, int i ->
            new Status(i + 1, c, c.capitalize(), c in ['done', 'cancelled'], '#000000', i + 1)
        }
        List<Member> members = [new Member(1, 'tony', 'Tony', false, true, 1), new Member(2, 'felix', 'Felix', true, true, 2),
                                new Member(3, 'old', 'Old Timer', false, false, 3)]
        List<CodeName> logTypes = ['note', 'command', 'result', 'decision', 'blocker', 'question'].withIndex().collect { String c, int i ->
            new CodeName(i + 1, c, c.capitalize(), i + 1)
        }
        List<CodeName> noteTypes = ['runbook', 'strategy', 'reference'].withIndex().collect { String c, int i ->
            new CodeName(i + 1, c, c.capitalize(), i + 1)
        }
        return new Lookups(statuses, members, logTypes, noteTypes)
    }

    static Credential credential(String prefixes = '/api/solutions/') {
        new Credential(AGENT_ID, AGENT_CODE, 'hash', true, prefixes)
    }

    static ApiClient apiClient(Map<String, Object> m = [:]) {
        record(ApiClient, [id: AGENT_ID, clientCode: AGENT_CODE, clientName: 'Rex', keyHint: 'abcd', hasKey: true, enabled: true,
                           allowedPathPrefixes: '/api/solutions/'] + m)
    }

    static SolutionLink link(int clientId, int solutionId) {
        new SolutionLink(clientId, solutionId, 'Alpha', 'rex', 'Rex')
    }
}
