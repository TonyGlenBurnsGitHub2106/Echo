package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

/**
 * Story 5653: the base of every controller spec. A subclass adds {@code @WebMvcTest(XController)}
 * and {@code @SpringBean} mocks for what that controller needs. Echo's real security chain
 * (SecurityConfig, JwtAuthenticationFilter, ApiClientAuthFilter) and a real JwtService (test
 * secret from src/test/resources/application.properties) are in every context, so each spec
 * also proves who may call what.
 */
@Import([SecurityConfig, JwtService])
abstract class EchoWebSpec extends EchoSpockBaseSpec {

    static final String SERVICE_KEY = 'test-service-key'

    @Autowired MockMvc mvc
    @Autowired JwtService jwt

    /** Only consulted when a request carries agent headers; specs about the fence stub it. */
    @SpringBean ApiClientService apiClients = Mock()

    /** A signed-in person, as auth.js sends it. */
    MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder b, String username = 'tony', int id = 1) {
        b.header('Authorization', 'Bearer ' + jwt.issueAccess(new AppUser(id, username, username, null, true, true, 0, null, 0)))
    }

    /** A script on this machine, with the service key. */
    MockHttpServletRequestBuilder asService(MockHttpServletRequestBuilder b) {
        b.header(JwtAuthenticationFilter.HEADER_SERVICE_KEY, SERVICE_KEY)
    }
}
