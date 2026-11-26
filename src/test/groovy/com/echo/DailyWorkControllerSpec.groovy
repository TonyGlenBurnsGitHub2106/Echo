package com.echo

// Story 6852: /api/daily-work -- lookups, find-or-create teams / contacts / kinds, and the entries.

import java.time.LocalDateTime

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.ApiClientModels.Credential
import com.echo.DailyWorkModels.Contact
import com.echo.DailyWorkModels.Entry
import com.echo.DailyWorkModels.EntryContact
import com.echo.DailyWorkModels.EntryDraft
import com.echo.DailyWorkModels.Filter
import com.echo.DailyWorkModels.Kind
import com.echo.DailyWorkModels.Lookups
import com.echo.DailyWorkModels.Team

import spock.lang.Unroll

@WebMvcTest(DailyWorkController)
class DailyWorkControllerSpec extends EchoWebSpec {

    @SpringBean DailyWorkService service = Mock()

    static final String DRAFT = '{"workDone":"Fixed the batch"}'

    // ---- who may call --------------------------------------------------------------------------

    @Unroll
    def "#seq. the endpoints should answer 401 when #scenario is called signed out"() {

        when:
        mvc.perform(call(method, path, body)).andExpect(status().isUnauthorized())
            .andExpect(content().json('{"error":"Not signed in"}', true))

        then:
        0 * service._

        where:
        seq | scenario      | method   | path                                 | body
        1   | 'lookups'     | 'GET'    | '/api/daily-work/lookups'            | null
        2   | 'add team'    | 'POST'   | '/api/daily-work/teams'              | '{"name":"A"}'
        3   | 'add contact' | 'POST'   | '/api/daily-work/contacts'           | '{"name":"A"}'
        4   | 'add kind'    | 'POST'   | '/api/daily-work/kinds'              | '{"name":"A"}'
        5   | 'search'      | 'GET'    | '/api/daily-work/entries'            | null
        6   | 'get'         | 'GET'    | '/api/daily-work/entries/3'          | null
        7   | 'create'      | 'POST'   | '/api/daily-work/entries'            | '{"workDone":"W"}'
        8   | 'update'      | 'PUT'    | '/api/daily-work/entries/3'          | '{"workDone":"W"}'
        9   | 'follow-up'   | 'PUT'    | '/api/daily-work/entries/3/follow-up'| '{"followUp":true}'
        10  | 'delete'      | 'DELETE' | '/api/daily-work/entries/3'          | null
    }

    @Unroll
    def "#seq. the fence should answer 403 when an API client calls #scenario"() {

        given:
        apiClients.authenticate('rex', 'k') >> new Credential(7, 'rex', 'h', true, '/api/solutions/')

        when:
        mvc.perform(call(method, path, body).header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isForbidden())

        then:
        0 * service._

        where:
        seq | scenario      | method   | path                                 | body
        1   | 'lookups'     | 'GET'    | '/api/daily-work/lookups'            | null
        2   | 'add team'    | 'POST'   | '/api/daily-work/teams'              | '{"name":"A"}'
        3   | 'add contact' | 'POST'   | '/api/daily-work/contacts'           | '{"name":"A"}'
        4   | 'add kind'    | 'POST'   | '/api/daily-work/kinds'              | '{"name":"A"}'
        5   | 'search'      | 'GET'    | '/api/daily-work/entries'            | null
        6   | 'get'         | 'GET'    | '/api/daily-work/entries/3'          | null
        7   | 'create'      | 'POST'   | '/api/daily-work/entries'            | '{"workDone":"W"}'
        8   | 'update'      | 'PUT'    | '/api/daily-work/entries/3'          | '{"workDone":"W"}'
        9   | 'follow-up'   | 'PUT'    | '/api/daily-work/entries/3/follow-up'| '{"followUp":true}'
        10  | 'delete'      | 'DELETE' | '/api/daily-work/entries/3'          | null
    }

    def "create should record the writer as service when the house key calls it"() {

        when:
        mvc.perform(asService(json(post('/api/daily-work/entries'), DRAFT))).andExpect(status().isOk())

        then:
        1 * service.save(0, _, 'service') >> 3
    }

    // ---- lookups -------------------------------------------------------------------------------

    def "lookups should answer the teams, contacts and kinds when they exist"() {

        given:
        service.lookups() >> new Lookups([new Team(1, 'Claims')], [new Contact(2, 'Jill', 1, 'Claims'), new Contact(3, 'Ron', null, null)],
                                         [new Kind(4, 'Jira')])

        expect:
        mvc.perform(asUser(get('/api/daily-work/lookups')))
            .andExpect(status().isOk())
            .andExpect(content().json('''{"teams":[{"id":1,"name":"Claims"}],
                "contacts":[{"id":2,"name":"Jill","teamId":1,"teamName":"Claims"},{"id":3,"name":"Ron","teamId":null,"teamName":null}],
                "kinds":[{"id":4,"name":"Jira"}]}''', true))
    }

    // ---- teams, contacts, kinds ----------------------------------------------------------------

    def "add team should pass the name as sent and answer the team when the body is valid"() {

        when:
        mvc.perform(json(asUser(post('/api/daily-work/teams')), '{"name":" Claims "}'))
            .andExpect(status().isOk())
            .andExpect(content().json('{"id":5,"name":"Claims"}', true))

        then:
        1 * service.addTeam(' Claims ') >> new Team(5, 'Claims')
    }

    def "add contact should pass the name and team and answer the contact when the body is valid"() {

        when:
        mvc.perform(json(asUser(post('/api/daily-work/contacts')), '{"name":"Jill","teamId":5}'))
            .andExpect(status().isOk())
            .andExpect(content().json('{"id":8,"name":"Jill","teamId":5,"teamName":"Claims"}', true))

        then:
        1 * service.addContact('Jill', 5) >> new Contact(8, 'Jill', 5, 'Claims')
    }

    def "add contact should pass a null team when the team is left out"() {

        when:
        mvc.perform(json(asUser(post('/api/daily-work/contacts')), '{"name":"Jill"}')).andExpect(status().isOk())

        then:
        1 * service.addContact('Jill', null) >> new Contact(8, 'Jill', null, null)
    }

    def "add kind should pass the name and answer the kind when the body is valid"() {

        when:
        mvc.perform(json(asUser(post('/api/daily-work/kinds')), '{"name":"Jira"}'))
            .andExpect(status().isOk())
            .andExpect(content().json('{"id":6,"name":"Jira"}', true))

        then:
        1 * service.addKind('Jira') >> new Kind(6, 'Jira')
    }

    @Unroll
    def "#what should pass a null name to the service when #scenario"() {

        given:
        MockHttpServletRequestBuilder b = asUser(post(path)).contentType(MediaType.APPLICATION_JSON)
        if (body != null) b.content(body)

        when:
        mvc.perform(b).andExpect(status().isBadRequest()).andExpect(content().json('{"error":"needs a name"}', true))

        then:
        1 * service./add.*/(*_) >> { throw new IllegalArgumentException('needs a name') }

        where:
        what          | scenario               | path                        | body
        'add team'    | 'the body is empty'    | '/api/daily-work/teams'     | '{}'
        'add team'    | 'there is no body'     | '/api/daily-work/teams'     | null
        'add contact' | 'there is no body'     | '/api/daily-work/contacts'  | null
        'add kind'    | 'the name is null'     | '/api/daily-work/kinds'     | '{"name":null}'
    }

    @Unroll
    def "#what should answer 400 with the message when the service refuses the input"() {

        when:
        mvc.perform(json(asUser(post(path)), '{"name":"   "}'))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"A team needs a name."}', true))

        then:
        1 * service./add.*/(*_) >> { throw new IllegalArgumentException('A team needs a name.') }

        where:
        what          | path
        'add team'    | '/api/daily-work/teams'
        'add contact' | '/api/daily-work/contacts'
        'add kind'    | '/api/daily-work/kinds'
    }

    @Unroll
    def "#what should answer 409 with the proc's message when the proc refuses"() {

        when:
        mvc.perform(json(asUser(post(path)), '{"name":"A","teamId":99}'))
            .andExpect(status().isConflict())
            .andExpect(content().json('{"error":"That team is not in Echo. Nothing was saved."}', true))

        then:
        1 * service./add.*/(*_) >> { throw new SaveRefusedException('That team is not in Echo. Nothing was saved.') }

        where:
        what          | path
        'add team'    | '/api/daily-work/teams'
        'add contact' | '/api/daily-work/contacts'
        'add kind'    | '/api/daily-work/kinds'
    }

    // ---- GET /entries --------------------------------------------------------------------------

    def "search should pass every filter through when all are given"() {

        when:
        mvc.perform(asUser(get('/api/daily-work/entries')
                .param('from', '2026-11-01').param('to', '2026-11-25').param('q', 'claims batch')
                .param('contactId', '12').param('teamId', '3').param('followUp', 'true').param('top', '50')))
            .andExpect(status().isOk())
            .andExpect(content().json('[]', true))

        then:
        1 * service.search(new Filter(getLocalDate('11/01/2026'), getLocalDate('11/25/2026'), 'claims batch', 12, 3, true, 50)) >> []
    }

    def "search should use no filters, followUp false and top 500 when no parameters are sent"() {

        when:
        mvc.perform(asUser(get('/api/daily-work/entries'))).andExpect(status().isOk())

        then:
        1 * service.search(new Filter(null, null, null, null, null, false, 500)) >> []
    }

    def "search should answer each entry with every field when entries are found"() {

        given:
        service.search(_) >> [entry(4, null, 'abc', true)]

        expect:
        mvc.perform(asUser(get('/api/daily-work/entries')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.length()').value(1))
            .andExpect(jsonPath('$[0].id').value(4))
            .andExpect(jsonPath('$[0].workDate').value('2026-11-25T09:30:00'))
            .andExpect(jsonPath('$[0].workDone').value('Fixed the batch'))
            .andExpect(jsonPath('$[0].storyRef').value('CLM-1'))
            .andExpect(jsonPath('$[0].teamId').value(2))
            .andExpect(jsonPath('$[0].teamName').value('Claims'))
            .andExpect(jsonPath('$[0].kindId').value(3))
            .andExpect(jsonPath('$[0].kindName').value('Jira'))
            .andExpect(jsonPath('$[0].minutesSpent').value(45))
            .andExpect(jsonPath('$[0].followUp').value(true))
            .andExpect(jsonPath('$[0].notes').value(null))
            .andExpect(jsonPath('$[0].notesPreview').value('abc'))
            .andExpect(jsonPath('$[0].notesCut').value(true))
            .andExpect(jsonPath('$[0].contacts[0].id').value(12))
            .andExpect(jsonPath('$[0].contacts[0].name').value('Jill'))
            .andExpect(jsonPath('$[0].contacts[1].name').value('Ron'))
            .andExpect(jsonPath('$[0].links[0]').value('https://a.example'))
            .andExpect(jsonPath('$[0].createdBy').value('tony'))
    }

    def "search should answer 400 with the message when To is before From"() {

        when:
        mvc.perform(asUser(get('/api/daily-work/entries').param('from', '2026-11-25').param('to', '2026-11-01')))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"The To date is before the From date."}', true))

        then:
        1 * service.search(_) >> { throw new IllegalArgumentException('The To date is before the From date.') }
    }

    @Unroll
    def "search should answer 400 when #scenario"() {

        when:
        mvc.perform(asUser(get('/api/daily-work/entries').param(name, value))).andExpect(status().isBadRequest())

        then:
        0 * service._

        where:
        scenario                      | name        | value
        'from is not a date'          | 'from'      | '11/25/2026'
        'to is not a date'            | 'to'        | 'soon'
        'top is not a number'         | 'top'       | 'all'
        'contactId is not a number'   | 'contactId' | 'jill'
    }

    // ---- GET /entries/{id} ---------------------------------------------------------------------

    def "get should answer the entry with its full notes when it exists"() {

        given:
        service.get(4) >> Optional.of(entry(4, 'the whole story', 'the whole story', false))

        expect:
        mvc.perform(asUser(get('/api/daily-work/entries/4')))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.id').value(4))
            .andExpect(jsonPath('$.notes').value('the whole story'))
            .andExpect(jsonPath('$.notesCut').value(false))
            .andExpect(jsonPath('$.contacts.length()').value(2))
            .andExpect(jsonPath('$.links.length()').value(1))
    }

    @Unroll
    def "get should answer 404 when the entry is #scenario"() {

        given:
        service.get(id) >> Optional.empty()

        expect:
        mvc.perform(asUser(get('/api/daily-work/entries/' + id)))
            .andExpect(status().isNotFound())
            .andExpect(content().json('{"error":"That entry is not in Echo."}', true))

        where:
        scenario          | id
        'missing'         | 404
        'id zero'         | 0
    }

    def "get should answer 400 when the id is not a number"() {

        when:
        mvc.perform(asUser(get('/api/daily-work/entries/abc'))).andExpect(status().isBadRequest())

        then:
        0 * service._
    }

    // ---- POST /entries -------------------------------------------------------------------------

    def "create should read every field, sign it with the user and answer the new id when the body is full"() {

        given:
        EntryDraft sent = null
        String body = '''{"workDate":"2026-11-25T09:30:00","workDone":"Fixed the batch","storyRef":"CLM-1","teamId":2,
            "kindId":3,"minutesSpent":45,"followUp":true,"notes":"n","contactIds":[12,7],"links":["https://a.example"]}'''

        when:
        mvc.perform(json(asUser(post('/api/daily-work/entries')), body))
            .andExpect(status().isOk())
            .andExpect(content().json('{"id":12}', true))

        then:
        1 * service.save(0, _, 'tony') >> { args -> sent = args[1]; 12 }
        sent == new EntryDraft(getLocalDateTime('11/25/2026 09:30'), 'Fixed the batch', 'CLM-1', 2, 3, 45, true, 'n',
                               [12, 7], ['https://a.example'])
    }

    def "create should read the body with followUp null when the body leaves followUp out"() {

        given:
        EntryDraft sent = null

        when:
        mvc.perform(json(asUser(post('/api/daily-work/entries')), DRAFT)).andExpect(status().isOk())

        then:
        1 * service.save(0, _, 'tony') >> { args -> sent = args[1]; 1 }
        sent.workDone() == 'Fixed the batch'
        sent.followUp() == null
        !sent.isFollowUp()
        sent.workDate() == null
        sent.contactIds() == null
        sent.links() == null
    }

    def "create should pass a null draft to the service and answer 400 when there is no body"() {

        when:
        mvc.perform(asUser(post('/api/daily-work/entries')).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"Say what you did."}', true))

        then:
        1 * service.save(0, null, 'tony') >> { throw new IllegalArgumentException('Say what you did.') }
    }

    def "create should answer 400 with the message when the service refuses the input"() {

        when:
        mvc.perform(json(asUser(post('/api/daily-work/entries')), '{"workDone":"W","minutesSpent":5000}'))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"Minutes spent should be between 0 and 1440."}', true))

        then:
        1 * service.save(*_) >> { throw new IllegalArgumentException('Minutes spent should be between 0 and 1440.') }
    }

    def "create should answer 409 with the proc's message when the proc refuses a double tap"() {

        when:
        mvc.perform(json(asUser(post('/api/daily-work/entries')), DRAFT))
            .andExpect(status().isConflict())
            .andExpect(content().json('{"error":"That entry was just saved. Nothing was saved."}', true))

        then:
        1 * service.save(*_) >> { throw new SaveRefusedException('That entry was just saved. Nothing was saved.') }
    }

    @Unroll
    def "create should answer 400 when the body is #scenario"() {

        when:
        mvc.perform(json(asUser(post('/api/daily-work/entries')), body)).andExpect(status().isBadRequest())

        then:
        0 * service._

        where:
        scenario                  | body
        'broken JSON'             | '{"workDone":'
        'an array'                | '[]'
        'a bad date'              | '{"workDone":"W","workDate":"yesterday"}'
        'a contact id as words'   | '{"workDone":"W","contactIds":["jill"]}'
    }

    // ---- PUT /entries/{id} ---------------------------------------------------------------------

    def "update should save under the id and answer the id when the body is valid"() {

        when:
        mvc.perform(json(asUser(put('/api/daily-work/entries/9')), DRAFT))
            .andExpect(status().isOk())
            .andExpect(content().json('{"id":9}', true))

        then:
        1 * service.save(9, { EntryDraft d -> d.workDone() == 'Fixed the batch' }, 'tony') >> 9
    }

    @Unroll
    def "update should answer 400 invalid id when the id is #scenario"() {

        when:
        mvc.perform(json(asUser(put('/api/daily-work/entries/' + id)), DRAFT))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"invalid id"}', true))

        then:
        0 * service._

        where:
        scenario    | id
        'zero'      | 0
        'negative'  | -1
    }

    def "update should answer 409 with the proc's message when the entry is gone"() {

        when:
        mvc.perform(json(asUser(put('/api/daily-work/entries/9')), DRAFT))
            .andExpect(status().isConflict())
            .andExpect(content().json('{"error":"That entry is no longer in Echo. Nothing was saved."}', true))

        then:
        1 * service.save(9, _, 'tony') >> { throw new SaveRefusedException('That entry is no longer in Echo. Nothing was saved.') }
    }

    def "update should answer 400 with the message when the service refuses the input"() {

        when:
        mvc.perform(json(asUser(put('/api/daily-work/entries/9')), '{"workDone":" "}'))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"Say what you did."}', true))

        then:
        1 * service.save(9, _, 'tony') >> { throw new IllegalArgumentException('Say what you did.') }
    }

    // ---- PUT /entries/{id}/follow-up -----------------------------------------------------------

    @Unroll
    def "follow-up should set the flag to #flag and answer ok when the entry exists"() {

        when:
        mvc.perform(json(asUser(put('/api/daily-work/entries/9/follow-up')), '{"followUp":' + flag + '}'))
            .andExpect(status().isOk())
            .andExpect(content().json('{"ok":true}', true))

        then:
        1 * service.setFollowUp(9, flag, 'tony') >> true

        where:
        scenario  | flag
        'on'      | true
        'off'     | false
    }

    def "follow-up should answer 404 when the entry is gone"() {

        when:
        mvc.perform(json(asUser(put('/api/daily-work/entries/9/follow-up')), '{"followUp":true}'))
            .andExpect(status().isNotFound())
            .andExpect(content().json('{"error":"That entry is not in Echo."}', true))

        then:
        1 * service.setFollowUp(9, true, 'tony') >> false
    }

    @Unroll
    def "follow-up should answer 400 followUp is required when #scenario"() {

        given:
        MockHttpServletRequestBuilder b = asUser(put('/api/daily-work/entries/9/follow-up')).contentType(MediaType.APPLICATION_JSON)
        if (body != null) b.content(body)

        when:
        mvc.perform(b).andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"followUp is required"}', true))

        then:
        0 * service._

        where:
        scenario                  | body
        'the body is empty'       | '{}'
        'followUp is null'        | '{"followUp":null}'
        'there is no body'        | null
    }

    // ---- DELETE /entries/{id} ------------------------------------------------------------------

    def "delete should remove the entry and answer ok when it exists"() {

        when:
        mvc.perform(asUser(delete('/api/daily-work/entries/9')))
            .andExpect(status().isOk())
            .andExpect(content().json('{"ok":true}', true))

        then:
        1 * service.delete(9) >> true
    }

    @Unroll
    def "delete should answer 404 when the entry is #scenario"() {

        when:
        mvc.perform(asUser(delete('/api/daily-work/entries/' + id)))
            .andExpect(status().isNotFound())
            .andExpect(content().json('{"error":"That entry is not in Echo."}', true))

        then:
        1 * service.delete(id) >> false

        where:
        scenario          | id
        'already gone'    | 9
        'id zero'         | 0
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Entry entry(int id, String notes, String preview, boolean cut) {
        LocalDateTime t = getLocalDateTime('11/25/2026 09:30')
        new Entry(id, t, 'Fixed the batch', 'CLM-1', 2, 'Claims', 3, 'Jira', 45, true, notes, preview, cut,
                  [new EntryContact(12, 'Jill'), new EntryContact(7, 'Ron')], ['https://a.example'], 'tony', t, null, null)
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        b.contentType(MediaType.APPLICATION_JSON).content(body)
    }

    private static MockHttpServletRequestBuilder call(String method, String path, String body) {
        MockHttpServletRequestBuilder b = request(HttpMethod.valueOf(method), path)
        body == null ? b : json(b, body)
    }
}
