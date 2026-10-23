package com.echo

import jakarta.servlet.http.HttpServletRequest
import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.SolutionDrafts.NoteDraft
import com.echo.SolutionDrafts.StepDraft
import static com.echo.SolutionFixtures.intList
import static com.echo.SolutionFixtures.logEntry
import static com.echo.SolutionFixtures.note
import static com.echo.SolutionFixtures.step
import static com.echo.SolutionFixtures.version

import spock.lang.Unroll

// Story 5653: /api/solutions notes -- notes, kinds order, versions, runbook steps; every endpoint, every caller.
@WebMvcTest(SolutionNoteController)
@Import([SolutionNoteService, SolutionLogService])
class SolutionNoteControllerSpec extends SolutionWebSupport {

    @SpringBean SolutionNoteRepository notes = Mock()
    @SpringBean SolutionLogRepository logRepo = Mock()

    def setup() {
        solutionExists(1, true)
        solutionExists(2, false)
        notes.find(300) >> note(id: 300, solutionId: 1, title: 'How to deploy', body: '# Deploy', steps: [step(id: 31, noteId: 300)])
        notes.find(301) >> note(id: 301, solutionId: 2)
        notes.steps(300) >> [step(id: 31, noteId: 300)]
        logRepo.find(200) >> logEntry(id: 200, solutionId: 1)
        logRepo.find(201) >> logEntry(id: 201, solutionId: 2)
    }

    @Unroll
    def "#seq. #method #path should answer #expectedStatus when #scenario"() {

        given:
        MockHttpServletRequestBuilder req = json(request(method, path), '{}')

        expect:
        mvc.perform(req).andExpect(status().is(expectedStatus))

        where:
        seq | scenario                         | method   | path                                          || expectedStatus
        1   | "list has no credential"         | "GET"    | "/api/solutions/1/notes"                      || 401
        2   | "get has no credential"          | "GET"    | "/api/solutions/notes/300"                    || 401
        3   | "locate has no credential"       | "GET"    | "/api/solutions/notes/300/locate"             || 401
        4   | "kindOrder has no credential"    | "PUT"    | "/api/solutions/note-types/order"             || 401
        5   | "create has no credential"       | "POST"   | "/api/solutions/1/notes"                      || 401
        6   | "update has no credential"       | "PUT"    | "/api/solutions/notes/300"                    || 401
        7   | "delete has no credential"       | "DELETE" | "/api/solutions/notes/300"                    || 401
        8   | "versions has no credential"     | "GET"    | "/api/solutions/notes/300/versions"           || 401
        9   | "version has no credential"      | "GET"    | "/api/solutions/notes/300/versions/1"         || 401
        10  | "addStep has no credential"      | "POST"   | "/api/solutions/notes/300/steps"              || 401
        11  | "updateStep has no credential"   | "PUT"    | "/api/solutions/notes/300/steps/31"           || 401
        12  | "moveStep has no credential"     | "POST"   | "/api/solutions/notes/300/steps/31/move"      || 401
        13  | "deleteStep has no credential"   | "DELETE" | "/api/solutions/notes/300/steps/31"           || 401
        14  | "stepFromLog has no credential"  | "POST"   | "/api/solutions/notes/300/steps/from-log"     || 401
    }

    // ---- list / one / locate ------------------------------------------------------------

    @Unroll
    def "list should answer the notes, by kind or all, when #scenario"() {

        when:
        ResultActions a = mvc.perform(by(who, get('/api/solutions/1/notes').param('type', 'runbook')))
        ResultActions b = mvc.perform(by(who, get('/api/solutions/1/notes')))

        then:
        1 * notes.listForSolution(1, 'runbook') >> [note(id: 300)]
        1 * notes.listForSolution(1, null) >> []
        a.andExpect(status().isOk()).andExpect(jsonPath('$[0].id').value(expectedId))
        b.andExpect(status().isOk())

        where:
        scenario          | who       || expectedId
        "a person asks"   | "user"    || 300
        "the house asks"  | "service" || 300
        "the agent asks"  | "agent"   || 300
    }

    def "list should answer 404 when the agent was not given the solution"() {

        expect:
        mvc.perform(asAgent(get('/api/solutions/2/notes'))).andExpect(status().isNotFound())
    }

    @Unroll
    def "get and locate should answer the body, the steps and where it lives when #scenario"() {

        expect:
        mvc.perform(by(who, get('/api/solutions/notes/300'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.body').value('# Deploy'))
            .andExpect(jsonPath('$.steps[0].id').value(31))
        mvc.perform(by(who, get('/api/solutions/notes/300/locate'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.noteId').value(300))
            .andExpect(jsonPath('$.solutionId').value(expectedSolution))

        where:
        scenario          | who       || expectedSolution
        "a person asks"   | "user"    || 1
        "the house asks"  | "service" || 1
        "the agent asks"  | "agent"   || 1
    }

    @Unroll
    def "get and locate should answer 404 when #scenario"() {

        expect:
        mvc.perform(by(who, get(path))).andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                                     | who     | path                              || expectedMessage
        "the note does not exist"                    | "user"  | "/api/solutions/notes/999"        || "Note not found."
        "the agent reads another solution's note"    | "agent" | "/api/solutions/notes/301"        || "Solution not found."
        "the agent locates another solution's note"  | "agent" | "/api/solutions/notes/301/locate" || "Solution not found."
    }

    // ---- kinds order --------------------------------------------------------------------

    @Unroll
    def "kindOrder should reorder the kinds and answer them when #scenario"() {

        when:
        ResultActions r = mvc.perform(json(by(who, put('/api/solutions/note-types/order')), body))

        then:
        1 * repo.reorderNoteTypes(intList(expectedIdsCsv))
        r.andExpect(status().isOk()).andExpect(jsonPath('$.length()').value(3)).andExpect(jsonPath('$[0].code').value('runbook'))

        where:
        scenario                         | who       | body                  || expectedIdsCsv
        "a person sends a new order"     | "user"    | '{"ids": [3, 1, 2]}'  || "3;1;2"
        "the house sends a new order"    | "service" | '{"ids": [3, 1, 2]}'  || "3;1;2"
        "a person sends an empty order"  | "user"    | '{"ids": []}'         || ""
    }

    def "kindOrder should answer 403, as the contract says, when an agent reorders the shared kinds"() {

        when:
        ResultActions r = mvc.perform(json(asAgent(put('/api/solutions/note-types/order')), [ids: [1]]))

        then:
        0 * repo.reorderNoteTypes(_)
        r.andExpect(status().isForbidden()).andExpect(jsonPath('$.error').value('forbidden'))
    }

    @Unroll
    def "kindOrder should answer 400 when #scenario"() {

        expect:
        mvc.perform(json(asUser(put('/api/solutions/note-types/order')), body)).andExpect(status().is(expectedStatus))

        where:
        scenario                    | body              || expectedStatus
        "the ids are missing"       | "{}"              || 400
        "the ids are null"          | '{"ids": null}'   || 400
        "an id is a letter"         | '{"ids": ["a"]}'  || 400
        "the body is an array"      | "[]"              || 400
    }

    // ---- create / update / delete ---------------------------------------------------------

    def "create should pass every field into the draft when the agent adds a note"() {

        given:
        Map<String, Object> body = [title: ' How to deploy ', typeCode: 'runbook', body: '# Deploy', changeNote: 'first draft', projectId: 10,
                                    sortOrder: 2]

        when:
        ResultActions r = mvc.perform(json(asAgent(post('/api/solutions/1/notes')), body))

        then:
        1 * notes.save(new NoteDraft(0, 1, 10, 'runbook', 'How to deploy', '# Deploy', 'first draft', 2), 'rex') >> 302
        1 * notes.find(302) >> note(id: 302)
        r.andExpect(status().isOk()).andExpect(jsonPath('$.id').value(302))
    }

    def "create should find a kind name for anyone and mint one only for a person when the agent names kinds"() {

        when:
        mvc.perform(json(asAgent(post('/api/solutions/1/notes')), [title: 'S', typeName: 'Strategy'])).andExpect(status().isOk())

        then:
        1 * notes.save({ NoteDraft d -> d.typeCode() == 'strategy' }, 'rex') >> 302
        1 * notes.find(302) >> note(id: 302)

        when:
        ResultActions r = mvc.perform(json(asAgent(post('/api/solutions/1/notes')), [title: 'S', typeName: 'Checklist']))

        then:
        0 * repo.findOrCreateNoteType(_)
        0 * notes.save(*_)
        r.andExpect(status().isConflict())
    }

    @Unroll
    def "create should answer #expectedStatus when #scenario"() {

        expect:
        mvc.perform(json(by(who, post(path)), body)).andExpect(status().is(expectedStatus))

        where:
        scenario                                  | who     | path                     | body                                   || expectedStatus
        "the body is empty"                       | "user"  | "/api/solutions/1/notes" | "{}"                                   || 400
        "the title is blank"                      | "user"  | "/api/solutions/1/notes" | '{"title": " "}'                       || 400
        "the type code is unknown"                | "user"  | "/api/solutions/1/notes" | '{"title": "x", "typeCode": "diary"}'  || 409
        "the agent was not given the solution"    | "agent" | "/api/solutions/2/notes" | '{"title": "x"}'                       || 404
    }

    @Unroll
    def "update should send only what changed, keeping the title, when #scenario"() {

        when:
        ResultActions r = mvc.perform(json(by(who, put('/api/solutions/notes/300')), [body: '# Deploy (revised)', changeNote: 'rollback']))

        then:
        1 * notes.save(new NoteDraft(300, 1, null, 'runbook', 'How to deploy', '# Deploy (revised)', 'rollback', 0), expectedAuthor) >> 300
        r.andExpect(status().isOk())

        where:
        scenario              | who     || expectedAuthor
        "a person updates"    | "user"  || "tony"
        "the agent updates"   | "agent" || "rex"
    }

    def "update should answer 404 when the agent edits another solution's note"() {

        expect:
        mvc.perform(json(asAgent(put('/api/solutions/notes/301')), [title: 'x'])).andExpect(status().isNotFound())
    }

    def "delete should delete for a person and an agent inside its solution and answer 404 when hidden"() {

        when:
        ResultActions a = mvc.perform(asUser(delete('/api/solutions/notes/300')))
        ResultActions b = mvc.perform(asAgent(delete('/api/solutions/notes/300')))
        ResultActions c = mvc.perform(asAgent(delete('/api/solutions/notes/301')))

        then:
        2 * notes.delete(300) >> true
        0 * notes.delete(301)
        a.andExpect(status().isOk()).andExpect(jsonPath('$.deletedId').value(300))
        b.andExpect(status().isOk())
        c.andExpect(status().isNotFound())
    }

    // ---- versions -----------------------------------------------------------------------

    def "versions and version should answer the list, one wording, and 404 for a missing version"() {

        when:
        ResultActions a = mvc.perform(asAgent(get('/api/solutions/notes/300/versions')))
        ResultActions b = mvc.perform(asAgent(get('/api/solutions/notes/300/versions/2')))
        ResultActions c = mvc.perform(asUser(get('/api/solutions/notes/300/versions/9')))

        then:
        1 * notes.versions(300) >> [version(versionNumber: 1), version(versionNumber: 2)]
        1 * notes.version(300, 2) >> version(versionNumber: 2, body: 'v2')
        1 * notes.version(300, 9) >> null
        a.andExpect(status().isOk()).andExpect(jsonPath('$.length()').value(2))
        b.andExpect(status().isOk()).andExpect(jsonPath('$.body').value('v2'))
        c.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Version not found.'))
    }

    @Unroll
    def "versions and version should answer 404 when #scenario"() {

        expect:
        mvc.perform(asAgent(get(path))).andExpect(status().is(expectedStatus))

        where:
        scenario                                         | path                                   || expectedStatus
        "the agent lists another solution's versions"    | "/api/solutions/notes/301/versions"    || 404
        "the agent reads another solution's version"     | "/api/solutions/notes/301/versions/1"  || 404
    }

    // ---- steps --------------------------------------------------------------------------

    def "addStep should save the step and answer the whole note when the agent adds one"() {

        given:
        Map<String, Object> body = [title: ' Stop the service ', command: 'sc stop alpha', expectedResult: 'STOPPED', notes: 'n', stepNumber: 2]

        when:
        ResultActions r = mvc.perform(json(asAgent(post('/api/solutions/notes/300/steps')), body))

        then:
        1 * notes.saveStep(new StepDraft(0, 300, 2, 'Stop the service', 'sc stop alpha', 'STOPPED', 'n', null)) >> 32
        r.andExpect(status().isOk()).andExpect(jsonPath('$.id').value(300)).andExpect(jsonPath('$.steps').isArray())
    }

    def "addStep should answer 400 with no title and 404 for another solution's note"() {

        expect:
        mvc.perform(json(asUser(post('/api/solutions/notes/300/steps')), [command: 'x'])).andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.message').value('A title is required.'))
        mvc.perform(json(asAgent(post('/api/solutions/notes/301/steps')), [title: 'x'])).andExpect(status().isNotFound())
    }

    def "updateStep should merge, keeping the title, when the agent edits the notes only"() {

        when:
        ResultActions r = mvc.perform(json(asAgent(put('/api/solutions/notes/300/steps/31')), [notes: 'Wait for STOPPED.']))

        then:
        1 * notes.saveStep(new StepDraft(31, 300, 0, 'Stop it', null, null, 'Wait for STOPPED.', null)) >> 31
        r.andExpect(status().isOk())
    }

    def "updateStep should answer 404 when the step is not the note's"() {

        when:
        ResultActions r = mvc.perform(json(asUser(put('/api/solutions/notes/300/steps/77')), [title: 'x']))

        then:
        0 * notes.saveStep(_)
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('Step not found.'))
    }

    def "moveStep should move the step when the agent sends a number"() {

        when:
        ResultActions r = mvc.perform(json(asAgent(post('/api/solutions/notes/300/steps/31/move')), [stepNumber: 1]))

        then:
        1 * notes.moveStep(31, 1) >> true
        r.andExpect(status().isOk())
    }

    @Unroll
    def "moveStep should answer 400 'A step number is required.' when #scenario"() {

        expect:
        mvc.perform(json(asUser(post('/api/solutions/notes/300/steps/31/move')), body)).andExpect(status().is(expectedStatus))
            .andExpect(jsonPath('$.message').value('A step number is required.'))

        where:
        scenario                     | body                    || expectedStatus
        "the number is missing"      | "{}"                    || 400
        "the number is zero"         | '{"stepNumber": 0}'     || 400
        "the number is negative"     | '{"stepNumber": -2}'    || 400
    }

    def "moveStep should answer 404 when the step is foreign or storage refuses"() {

        expect:
        mvc.perform(json(asUser(post('/api/solutions/notes/300/steps/77/move')), [stepNumber: 1])).andExpect(status().isNotFound())

        when:
        ResultActions r = mvc.perform(json(asUser(post('/api/solutions/notes/300/steps/31/move')), [stepNumber: 1]))

        then:
        1 * notes.moveStep(31, 1) >> false
        r.andExpect(status().isNotFound())
    }

    def "deleteStep should answer the note, and 404 when the step is foreign or the note hidden"() {

        when:
        ResultActions a = mvc.perform(asAgent(delete('/api/solutions/notes/300/steps/31')))
        ResultActions b = mvc.perform(asUser(delete('/api/solutions/notes/300/steps/77')))
        ResultActions c = mvc.perform(asAgent(delete('/api/solutions/notes/301/steps/31')))

        then:
        1 * notes.deleteStep(31) >> true
        a.andExpect(status().isOk()).andExpect(jsonPath('$.id').value(300))
        b.andExpect(status().isNotFound())
        c.andExpect(status().isNotFound())
    }

    def "stepFromLog should make a step and answer the note when the log entry is of the same solution"() {

        when:
        ResultActions r = mvc.perform(json(asAgent(post('/api/solutions/notes/300/steps/from-log')), [logId: 200]))

        then:
        1 * notes.stepFromLog(300, 200) >> 33
        r.andExpect(status().isOk()).andExpect(jsonPath('$.id').value(300))
    }

    @Unroll
    def "stepFromLog should answer #expectedStatus when #scenario"() {

        when:
        ResultActions r = mvc.perform(json(by(who, post('/api/solutions/notes/300/steps/from-log')), body))

        then:
        0 * notes.stepFromLog(*_)
        r.andExpect(status().is(expectedStatus))

        where:
        scenario                                         | who     | body             || expectedStatus
        "no log id is sent"                              | "user"  | "{}"             || 400
        "the log id is zero"                             | "user"  | '{"logId": 0}'   || 400
        "a person names another solution's log entry"    | "user"  | '{"logId": 201}' || 409
        "the agent names a log entry it may not see"     | "agent" | '{"logId": 201}' || 404
        "the log entry does not exist"                   | "user"  | '{"logId": 999}' || 404
    }

    def "stepFromLog should name the field and the rule when refused"() {

        expect:
        mvc.perform(json(asUser(post('/api/solutions/notes/300/steps/from-log')), [:]))
            .andExpect(jsonPath('$.message').value('A log entry id is required.'))
        mvc.perform(json(asUser(post('/api/solutions/notes/300/steps/from-log')), [logId: 201]))
            .andExpect(jsonPath('$.message').value('The log entry and the note belong to different solutions.'))
    }

    def "the mapping methods should refuse a null body themselves when called directly"() {

        given:
        SolutionNoteController c = new SolutionNoteController(Mock(SolutionNoteService), Mock(SolutionCallerResolver))
        HttpServletRequest req = Mock()

        when:
        c.create(req, 1, null)

        then:
        IllegalArgumentException e1 = thrown()
        e1.message == 'A note is required.'

        when:
        c.update(req, 300, null)

        then:
        thrown(IllegalArgumentException)

        when:
        c.addStep(req, 300, null)

        then:
        IllegalArgumentException e2 = thrown()
        e2.message == 'A step is required.'

        when:
        c.updateStep(req, 300, 31, null)

        then:
        thrown(IllegalArgumentException)

        when:
        c.moveStep(req, 300, 31, null)

        then:
        thrown(IllegalArgumentException)

        when:
        c.stepFromLog(req, 300, null)

        then:
        thrown(IllegalArgumentException)

        when:
        c.kindOrder(req, null)

        then:
        IllegalArgumentException e3 = thrown()
        e3.message == 'ids (the new order) is required.'
    }
}
