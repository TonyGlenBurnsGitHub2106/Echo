package com.echo

import com.echo.ApiClientModels.Credential

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Story 5653: the Web tab's add / list -- and EntityRecordErrors, which gives these copied controllers
 * the answers LeonardoWeb's global handler gives (409 with the sentence for a duplicate or a refusal).
 */
@WebMvcTest([EntityWebAddressController, EntityRecordErrors])
class EntityWebAddressControllerSpec extends EchoWebSpec {

    @SpringBean EntityWebAddressService service = Mock()
    @SpringBean WebTypeService types = Mock()

    static final String BODY = '{"webAddressName":"Portal","webAddress":"https://claims.example","webAddressTypeId":1,"webSiteTypeId":8}'

    def "add should save the address on the record when it is complete"() {

        when:
        def result = mvc.perform(asUser(post('/api/entities/41/web-addresses').contentType(MediaType.APPLICATION_JSON).content(BODY)))

        then:
        1 * service.save(41, 0, { it.webAddress == 'https://claims.example' && it.webSiteTypeId == 8 }) >> [EntityWebAddressXRefID: 9]
        result.andExpect(status().isOk())
    }

    def "add should answer 409 with the sentence when the address is already in use"() {

        given:
        service.save(41, 0, _) >> { throw new DuplicateEntityException("The web address 'https://claims.example' is already in use.") }

        expect:
        mvc.perform(asUser(post('/api/entities/41/web-addresses').contentType(MediaType.APPLICATION_JSON).content(BODY)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath('$.error').value("The web address 'https://claims.example' is already in use."))
    }

    def "add should answer 409 with the proc's sentence when the save is refused"() {

        given:
        service.save(41, 0, _) >> { throw new SaveRefusedException('That record is gone. Nothing was saved.') }

        expect:
        mvc.perform(asUser(post('/api/entities/41/web-addresses').contentType(MediaType.APPLICATION_JSON).content(BODY)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath('$.error').value('That record is gone. Nothing was saved.'))
    }

    def "add should answer 400 and save nothing when the URL is missing"() {

        when:
        def result = mvc.perform(asUser(post('/api/entities/41/web-addresses').contentType(MediaType.APPLICATION_JSON)
            .content('{"webAddressTypeId":1,"webSiteTypeId":8}')))

        then:
        0 * service._
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.error').value('webAddress is required'))
    }

    def "list should answer 401 when there is no credential"() {

        expect:
        mvc.perform(get('/api/entities/41/web-addresses')).andExpect(status().isUnauthorized())
    }

    def "list should answer 403 before the controller when an API client calls it"() {

        when:
        def result = mvc.perform(get('/api/entities/41/web-addresses').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))

        then:
        1 * apiClients.authenticate('rex', 'k') >> new Credential(7, 'rex', 'h', true, '/api/solutions/')
        1 * apiClients.logAccess(7, 'rex', '/api/entities/41/web-addresses', 'GET', 403, false, _)
        0 * service._
        result.andExpect(status().isForbidden())
    }
}
