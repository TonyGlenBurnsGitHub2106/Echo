package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType

import spock.lang.Unroll

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Story 5653: the record dialogs' pick-lists -- only the three the Web tab uses exist in Echo. */
@WebMvcTest([LookupController, EntityRecordErrors])
@Import(LookupService)
class LookupControllerSpec extends EchoWebSpec {

    @SpringBean LookupRepository repo = Mock()

    @Unroll
    def "a lookup should run #proc when the list is #slug"() {

        when:
        def result = mvc.perform(asUser(get('/api/lookups/' + slug)))

        then:
        1 * repo.run(proc) >> [[Id: 3, Name: 'zz-felix']]
        result.andExpect(status().isOk())
            .andExpect(jsonPath('$[0].id').value(3))
            .andExpect(jsonPath('$[0].name').value('zz-felix'))

        where:
        slug                | proc
        'web-address-types' | 'stp_LW_Lookup_WebAddressTypes'
        'web-site-types'    | 'stp_LW_Lookup_WebSiteTypes'
        'users'             | 'stp_LW_Lookup_Users'
    }

    @Unroll
    def "a lookup should answer 404 when the list is #slug, which Echo does not have"() {

        when:
        def result = mvc.perform(asUser(get('/api/lookups/' + slug)))

        then:
        0 * repo._
        result.andExpect(status().isNotFound())

        where:
        slug << ['phone-types', 'address-types', 'vendor-types', 'event-types', 'nothing']
    }

    def "add should find or create an address type when one is typed in a dialog"() {

        when:
        def result = mvc.perform(asUser(post('/api/lookups/web-address-types')
            .contentType(MediaType.APPLICATION_JSON).content('{"name":"Intranet"}')))

        then:
        1 * repo.findOrCreate('web-address-types', 'Intranet') >> [Id: 4, Name: 'Intranet', WasCreated: true]
        result.andExpect(status().isOk())
            .andExpect(jsonPath('$.id').value(4))
            .andExpect(jsonPath('$.created').value(true))
    }

    def "a lookup should answer 401 when there is no credential"() {

        expect:
        mvc.perform(get('/api/lookups/users')).andExpect(status().isUnauthorized())
    }
}
