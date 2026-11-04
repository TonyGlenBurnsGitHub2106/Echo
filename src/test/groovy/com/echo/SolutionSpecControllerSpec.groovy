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
        'GET /api/solutions/items/{itemId}/locate', 'GET /api/solutions/notes/{noteId}/locate',
        // Story 5695: the deep-link helpers of topics, questions and decisions
        'GET /api/solutions/topics/{topicId}/locate', 'GET /api/solutions/questions/{qid}/locate', 'GET /api/solutions/decisions/{did}/locate',
        // Story 5695 Release 5: opening Explorer on this computer is a person's action only (an agent is answered 403)
        'POST /api/solutions/files/{fileId}/open-folder',
        // Story 5695 (API audit 2026-11-04): the deep-link helpers of terms and the two configuration tabs
        'GET /api/solutions/terms/{termId}/locate', 'GET /api/solutions/config/properties/{propertyId}/locate',
        'GET /api/solutions/config-grid/properties/{propertyId}/locate'
    ] as Set

    /** The documented paths under these controllers' prefixes, loosened ({} for every placeholder). Story 5695 Release 5 adds
     *  items/bulk, promote, provenance, views and files (with their versions). */
    private static final String OURS = /^(GET|POST|PUT|DELETE) \/api\/solutions(|\/\{\}|\/whoami|\/lookups|\/spec|\/by-alias\/\{\}|\/\{\}\/projects|\/projects\/.*|\/\{\}\/items|\/items\/.*|\/\{\}\/log|\/log\/.*|\/\{\}\/notes|\/notes\/.*|\/note-types\/.*|\/\{\}\/(links|link-targets|topics|topic-types|waits|visit|since|caught-up|questions|decisions|summary-age|lens|left-off|activity|items\/bulk|promote|provenance|views|files|terms|conversations|test-plans|config|config-grid|shape|close-out).*|\/(topics|topic-types|waits|questions|decisions|views|files|terms|conversations|test-plans|config|config-grid)\/.*|\/topic-kinds|\/shapes)$/

    private static final List<Class<?>> AGENT_CONTROLLERS = [SolutionController, SolutionItemController, SolutionLogController,
                                                             SolutionNoteController, SolutionSpecController,
                                                             // Story 5695: links and topics, questions and decisions, the summary age
                                                             // (Release 4: SolutionOrientationController also maps lens, left-off and activity)
                                                             SolutionLinkController, SolutionDecisionController, SolutionOrientationController,
                                                             // Story 5695 Release 3: kinds of topic and the premise switch, what items wait on
                                                             SolutionTopicKindController, SolutionDependencyController,
                                                             // Story 5695 Release 5: many at once and Make into, saved views, and
                                                             // the files (for their versions)
                                                             SolutionChangeController, SolutionSavedViewController, SolutionFileController,
                                                             // the API audit of 2026-11-04: five more an agent can reach, documented now
                                                             SolutionTermController, SolutionConversationController, SolutionTestController,
                                                             SolutionConfigController, SolutionConfigGridController,
                                                             // Story 5695 Release 6: shapes and close-out
                                                             SolutionLifecycleController]

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
        List<String> unknown = documented.findAll { String d -> d ==~ OURS && !(d in mine) }.unique()

        expect:
        documented.size() > 20
        unknown == []
    }

    @Unroll
    def "#seq. the contract's path pattern should recognise the Release 5 path for #scenario"() {

        given:
        Set<String> mine = mappedEndpoints(AGENT_CONTROLLERS).collect { String e -> loosen(e) } as Set

        expect:
        path ==~ OURS
        mine.contains(path)
        loosen(raw()).contains(path)

        where:
        seq | scenario                             | path                                              || unused
        1   | 'a bulk change'                      | 'POST /api/solutions/{}/items/bulk'               || null
        2   | 'a promotion'                        | 'POST /api/solutions/{}/promote'                  || null
        3   | 'provenance'                         | 'GET /api/solutions/{}/provenance'                || null
        4   | 'the saved views'                    | 'GET /api/solutions/{}/views'                     || null
        5   | 'saving a view'                      | 'POST /api/solutions/{}/views'                    || null
        6   | 'deleting a view'                    | 'DELETE /api/solutions/views/{}'                  || null
        7   | 'uploading a new version'            | 'POST /api/solutions/files/{}/versions'           || null
        8   | 'listing the versions'               | 'GET /api/solutions/files/{}/versions'            || null
        9   | 'downloading an earlier version'     | 'GET /api/solutions/files/versions/{}/content'    || null
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
                        // a placeholder that is a choice of words ({kind:types|categories}) is one endpoint per word,
                        // the way the contract names them (Story 5695, API audit 2026-11-04)
                        java.util.regex.Matcher m = (base + p) =~ /\{\w+:([a-z-]+(?:\|[a-z-]+)+)\}/
                        List<String> raws = m.find() ? m.group(1).split(/\|/).collect { String w -> (base + p).replace(m.group(0), w) } : [base + p]
                        raws.each { String r -> out << ((String) pair[1] + ' ' + r.replaceAll(/\{(\w+):[^}]*\}/, '{$1}')) }
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
