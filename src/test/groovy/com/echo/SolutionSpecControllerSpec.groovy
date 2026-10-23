package com.echo

import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.regex.Matcher

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.Unroll

// Story 5653: GET /api/solutions/spec -- the agent's contract (solutions/api-spec.md), served with this Echo's address,
// and checked against what the Solutions controllers really map.
@WebMvcTest(SolutionSpecController)
class SolutionSpecControllerSpec extends SolutionWebSupport {

    /** Owner-only or helper endpoints an agent is not told about on purpose. */
    private static final Set<String> NOT_FOR_AGENTS = [
        'POST /api/solutions', 'DELETE /api/solutions/{id}', 'POST /api/solutions/members',
        'GET /api/solutions/{id}/shares', 'POST /api/solutions/{id}/shares', 'DELETE /api/solutions/{id}/shares/{apiClientId}',
        'GET /api/solutions/{id}/shares/{apiClientId}/brief', 'PUT /api/solutions/{id}/file-storage',
        'GET /api/solutions/items/{itemId}/locate', 'GET /api/solutions/notes/{noteId}/locate'
    ] as Set

    private static final List<Class<?>> AGENT_CONTROLLERS = [SolutionController, SolutionItemController, SolutionLogController,
                                                             SolutionNoteController, SolutionSpecController]

    def "RESOURCE should name the classpath file that holds the contract when read"() {

        expect:
        SolutionSpecController.RESOURCE == 'solutions/api-spec.md'
        raw().startsWith('# Solutions API -- the contract')
    }

    @Unroll
    def "spec should answer plain UTF-8 text with the base URL filled in when #scenario"() {

        when:
        MockHttpServletResponse res = mvc.perform(by(who, get('/api/solutions/spec'))).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith('text/plain'))
            .andReturn().response
        String body = res.getContentAsString(StandardCharsets.UTF_8)

        then:
        res.contentType.toLowerCase().contains('charset=utf-8')
        body.contains('Base URL: `' + expectedBase + '`')
        !body.contains('{{BASE_URL}}')
        body.length() == raw().replace('{{BASE_URL}}', expectedBase).length()

        where:
        scenario          | who       || expectedBase
        "a person asks"   | "user"    || "http://localhost:8770"
        "the house asks"  | "service" || "http://localhost:8770"
        "the agent asks"  | "agent"   || "http://localhost:8770"
    }

    def "spec should answer 401 with no credential and 403 to an agent outside its allowlist"() {

        expect:
        mvc.perform(get('/api/solutions/spec')).andExpect(status().isUnauthorized())
        mvc.perform(asScopedElsewhere(get('/api/solutions/spec'))).andExpect(status().isForbidden())
    }

    def "text should read once and cache, with the base URL the controller was given, when called twice"() {

        given:
        SolutionSpecController c = new SolutionSpecController('https://echo.example:9')

        when:
        String first = c.text()
        String second = c.text()

        then:
        first.is(second)
        first.contains('Base URL: `https://echo.example:9`')
        !first.contains('{{BASE_URL}}')
    }

    def "spec should answer 200 with the text when called directly"() {

        when:
        ResponseEntity<String> r = new SolutionSpecController('u').spec()

        then:
        r.statusCode.value() == 200
        r.body.startsWith('# Solutions API')
    }

    def "the contract should be pure ASCII when read as bytes"() {

        given:
        byte[] bytes = SolutionSpecController.getClassLoader().getResourceAsStream(SolutionSpecController.RESOURCE)
            .withCloseable { InputStream stream -> stream.readAllBytes() }

        expect:
        bytes.findIndexOf { byte b -> (b & 0xFF) > 0x7F } == -1
    }

    def "the contract should name Echo's headers, never LeonardoWeb's or the house key, when read"() {

        given:
        String t = raw()

        expect:
        t.contains('X-Echo-Client:')
        t.contains('X-Echo-Client-Key:')
        !t.contains('X-Leo-')
        !t.contains('X-Echo-Key')
        !t.toLowerCase().contains('leonardo')
    }

    def "the contract should describe the error body SolutionControllerSupport writes when read"() {

        expect:
        raw().contains('Error bodies look like `{"ok": false, "error": "rule", "message": "..."}`.')
        SolutionControllerSupport.error('rule', '...').keySet() as List == ['ok', 'error', 'message']
    }

    def "the contract should list every endpoint an agent may use when compared with the controllers"() {

        given:
        String spec = loosen(raw())
        List<String> endpoints = mappedEndpoints(AGENT_CONTROLLERS)
        List<String> missing = endpoints.findAll { String e -> !(e in NOT_FOR_AGENTS) && !spec.contains(loosen(e)) }

        expect:
        endpoints.size() > 30
        missing == []
    }

    def "the contract should name nothing under these controllers' paths that they do not map when compared"() {

        given:
        Set<String> mine = mappedEndpoints(AGENT_CONTROLLERS).collect { String e -> loosen(e) } as Set
        Matcher m = (raw() =~ /`(GET|POST|PUT|DELETE) (\/api\/solutions[^`?\s]*)/)
        List<String> documented = m.collect { List<String> g -> loosen(g[1] + ' ' + g[2]) }
        String ours = /^(GET|POST|PUT|DELETE) \/api\/solutions(|\/\{\}|\/whoami|\/lookups|\/spec|\/by-alias\/\{\}|\/\{\}\/projects|\/projects\/.*|\/\{\}\/items|\/items\/.*|\/\{\}\/log|\/log\/.*|\/\{\}\/notes|\/notes\/.*|\/note-types\/.*)$/
        List<String> unknown = documented.findAll { String d -> d ==~ ours && !(d in mine) }.unique()

        expect:
        documented.size() > 20
        unknown == []
    }

    private static String raw() {
        SolutionSpecController.getClassLoader().getResourceAsStream(SolutionSpecController.RESOURCE).withCloseable { InputStream stream ->
            new String(stream.readAllBytes(), StandardCharsets.UTF_8)
        }
    }

    /** Every endpoint the controllers map, as "VERB /path" with {placeholders} and no regex. */
    private static List<String> mappedEndpoints(List<Class<?>> controllers) {
        List<String> out = []
        controllers.each { Class<?> c ->
            RequestMapping rm = c.getAnnotation(RequestMapping)
            String base = rm == null || rm.value().length == 0 ? '' : rm.value()[0]
            c.declaredMethods.each { Method method ->
                [[GetMapping, 'GET'], [PostMapping, 'POST'], [PutMapping, 'PUT'], [DeleteMapping, 'DELETE']].each { List pair ->
                    Object a = method.getAnnotation((Class) pair[0])
                    if (a != null) {
                        List<String> values = (a.value() as List) + (a.path() as List)
                        String p = values.isEmpty() ? '' : values[0]
                        String path = (base + p).replaceAll(/\{(\w+):[^}]*\}/, '{$1}')
                        out << ((String) pair[1] + ' ' + path)
                    }
                }
            }
        }
        return out.sort()
    }

    /** The spec writes some ids by other names than the code ({id}, {n}); compare with every placeholder as {}. */
    private static String loosen(String endpoint) {
        endpoint.replaceAll(/\{[^}]+\}/, '{}')
    }
}
