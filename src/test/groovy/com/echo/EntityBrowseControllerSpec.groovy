package com.echo

import com.echo.ApiClientModels.Credential

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.jdbc.core.JdbcTemplate

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Story 5653: the solution page's Web tab asks which EntityID a solution has -- house-only. */
@WebMvcTest([EntityBrowseController, EntityRecordErrors])
class EntityBrowseControllerSpec extends EchoWebSpec {

    @SpringBean JdbcTemplate jdbc = Mock()

    def "browse should answer the solution's EntityID when the type is a solution"() {

        when:
        def result = mvc.perform(asUser(get('/api/entity-browse').param('type', '222').param('identityId', '6')))

        then:
        1 * jdbc.queryForList('EXEC dbo.stp_Solution_GetEntity @prmSolutionID = ?', 6) >> [[SolutionID: 6, EntityID: 41]]
        result.andExpect(status().isOk())
            .andExpect(jsonPath('$.rows[0].entityId').value(41))
            .andExpect(jsonPath('$.rows[0].identityId').value(6))
    }

    def "browse should answer no rows and ask nothing when the type is not a solution"() {

        when:
        def result = mvc.perform(asUser(get('/api/entity-browse').param('type', '5').param('identityId', '6')))

        then:
        0 * jdbc._
        result.andExpect(status().isOk())
            .andExpect(jsonPath('$.rows.length()').value(0))
    }

    def "browse should answer 401 when there is no credential"() {

        expect:
        mvc.perform(get('/api/entity-browse').param('type', '222').param('identityId', '6'))
            .andExpect(status().isUnauthorized())
    }

    def "browse should answer 403 before the controller when an API client calls it"() {

        when:
        def result = mvc.perform(get('/api/entity-browse').param('type', '222').param('identityId', '6')
            .header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))

        then:
        1 * apiClients.authenticate('rex', 'k') >> new Credential(7, 'rex', 'h', true, '/api/solutions/')
        1 * apiClients.logAccess(7, 'rex', '/api/entity-browse', 'GET', 403, false, _)
        0 * jdbc._
        result.andExpect(status().isForbidden())
    }
}
