package com.echo

// Story 5653: the Print menu's server half -- /api/pdf/printers and /api/pdf/generic.

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import com.echo.ApiClientModels.Credential

import spock.lang.Unroll

@WebMvcTest(PdfController)
class PdfControllerSpec extends EchoWebSpec {

    @SpringBean PdfService pdf = Mock()

    private static final byte[] PDF = '%PDF-1.4 fake'.bytes

    // ---- who may call --------------------------------------------------------------------------

    def "printers and generic should answer 401 when nobody is signed in"() {

        when:
        mvc.perform(get('/api/pdf/printers')).andExpect(status().isUnauthorized())
            .andExpect(content().json('{"error":"Not signed in"}', true))
        mvc.perform(json(post('/api/pdf/generic'), '{"html":"<p/>"}')).andExpect(status().isUnauthorized())

        then:
        0 * pdf._
    }

    def "printers and generic should answer 403 from the fence when the caller is an API client"() {

        given:
        apiClients.authenticate('rex', 'k') >> new Credential(7, 'rex', 'h', true, '/api/solutions/')

        when:
        mvc.perform(json(post('/api/pdf/generic'), '{"html":"<p/>"}').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isForbidden())
        mvc.perform(get('/api/pdf/printers').header('X-Echo-Client', 'rex').header('X-Echo-Client-Key', 'k'))
            .andExpect(status().isForbidden())

        then:
        0 * pdf._
    }

    // ---- printers -------------------------------------------------------------------------------

    @Unroll
    def "printers should answer an empty list when the caller is #scenario"() {

        given:
        MockHttpServletRequestBuilder b = asHouse ? asService(get('/api/pdf/printers')) : asUser(get('/api/pdf/printers'))

        expect:
        mvc.perform(b).andExpect(status().isOk()).andExpect(content().json('{"printers":[]}', true))

        where:
        scenario     | asHouse
        'a person'   | false
        'the house'  | true
    }

    // ---- generic: the happy path ------------------------------------------------------------------

    def "generic should answer the PDF bytes as an attachment when the html renders"() {

        when:
        MockHttpServletResponse res = mvc.perform(json(asUser(post('/api/pdf/generic')), '{"html":"<html><body>x</body></html>","filename":"report.pdf"}'))
            .andExpect(status().isOk())
            .andExpect(header().string('Content-Disposition', 'attachment; filename="report.pdf"'))
            .andReturn().response

        then:
        1 * pdf.htmlToPdf('<html><body>x</body></html>') >> PDF
        res.contentType == 'application/pdf'
        res.contentAsByteArray == PDF
    }

    @Unroll
    def "#seq. generic should name the attachment '#expected' when the filename is #scenario"() {

        given:
        pdf.htmlToPdf(_) >> PDF
        String body = fname == null ? '{"html":"<p/>"}' : '{"html":"<p/>","filename":"' + esc(fname) + '"}'

        expect:
        mvc.perform(json(asUser(post('/api/pdf/generic')), body))
            .andExpect(status().isOk())
            .andExpect(header().string('Content-Disposition', 'attachment; filename="' + expected + '"'))

        where:
        seq | scenario                         | fname                         || expected
        1   | 'absent'                         | null                          || 'echo_print.pdf'
        2   | 'empty'                          | ''                            || 'echo_print.pdf'
        3   | 'blank'                          | '   '                         || 'echo_print.pdf'
        4   | 'a bare name'                    | 'report'                      || 'report.pdf'
        5   | 'a padded name'                  | '  report  '                  || 'report.pdf'
        6   | 'a pdf name'                     | 'report.pdf'                  || 'report.pdf'
        7   | 'an upper-case extension'        | 'report.PDF'                  || 'report.PDF'
        8   | 'a mixed-case extension'         | 'report.Pdf'                  || 'report.Pdf'
        9   | 'another extension'              | 'report.txt'                  || 'report.txt.pdf'
        10  | 'only the extension'             | '.pdf'                        || '.pdf'
        11  | 'a slash'                        | 'a/b'                         || 'a_b.pdf'
        12  | 'a backslash'                    | 'a\\b'                        || 'a_b.pdf'
        13  | 'every forbidden character'      | 'a:b*c?d"e<f>g|h'             || 'a_b_c_d_e_f_g_h.pdf'
        14  | 'a run of forbidden characters'  | 'a//\\\\::b'                  || 'a_b.pdf'
        15  | 'only slashes'                   | '///'                         || '_.pdf'
        16  | 'a path climb'                   | '../../etc/passwd'            || '.._.._etc_passwd.pdf'
        17  | 'a header injection'             | 'line1\r\nSet-Cookie: x=1'    || 'line1_Set-Cookie_ x=1.pdf'
        18  | 'a tab'                          | 'tab\there'                   || 'tab_here.pdf'
        19  | 'spaces'                         | 'a b c.pdf'                   || 'a b c.pdf'
        20  | 'a semicolon'                    | 'a;b.pdf'                     || 'a;b.pdf'
    }

    def "generic should use a numeric filename as text when the filename is a number"() {

        given:
        pdf.htmlToPdf(_) >> PDF

        expect:
        mvc.perform(json(asUser(post('/api/pdf/generic')), '{"html":"<p/>","filename":42}'))
            .andExpect(header().string('Content-Disposition', 'attachment; filename="42.pdf"'))
    }

    def "generic should render the text form when the html is not a string"() {

        when:
        mvc.perform(json(asUser(post('/api/pdf/generic')), '{"html":12345}')).andExpect(status().isOk())

        then:
        1 * pdf.htmlToPdf('12345') >> PDF
    }

    def "generic should serve the house key when it prints"() {

        given:
        pdf.htmlToPdf(_) >> PDF

        expect:
        mvc.perform(json(asService(post('/api/pdf/generic')), '{"html":"<p/>"}')).andExpect(status().isOk())
    }

    // ---- generic: refusals ------------------------------------------------------------------------

    def "generic should answer 400 missing body when there is no body"() {

        when:
        mvc.perform(asUser(post('/api/pdf/generic')).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"missing body"}', true))

        then:
        0 * pdf._
    }

    @Unroll
    def "generic should answer 400 html is empty when #scenario"() {

        when:
        mvc.perform(json(asUser(post('/api/pdf/generic')), body))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"html is empty"}', true))

        then:
        0 * pdf._

        where:
        scenario                     | body
        'the body is empty'          | '{}'
        'the html is null'           | '{"html":null}'
        'the html is empty'          | '{"html":""}'
        'the html is blank'          | '{"html":"   "}'
        'the html is whitespace'     | '{"html":"\\n\\t"}'
        'only a filename is sent'    | '{"filename":"x.pdf"}'
    }

    def "generic should answer 400 when the JSON is broken"() {

        expect:
        mvc.perform(json(asUser(post('/api/pdf/generic')), '{"html":')).andExpect(status().isBadRequest())
    }

    def "generic should answer 400 with the message when the renderer refuses with IllegalArgumentException"() {

        when:
        mvc.perform(json(asUser(post('/api/pdf/generic')), '{"html":"<p/>"}'))
            .andExpect(status().isBadRequest())
            .andExpect(content().json('{"error":"xhtml is empty"}', true))

        then:
        1 * pdf.htmlToPdf(_) >> { throw new IllegalArgumentException('xhtml is empty') }
    }

    def "generic should answer 500 with the reason when the render fails"() {

        when:
        mvc.perform(json(asUser(post('/api/pdf/generic')), '{"html":"<p>"}'))
            .andExpect(status().isInternalServerError())
            .andExpect(content().json('{"error":"PDF render failed: PDF render failed: bad markup"}', true))

        then:
        1 * pdf.htmlToPdf('<p>') >> { throw new RuntimeException('PDF render failed: bad markup') }
    }

    def "generic should answer 405 when called with GET"() {

        expect:
        mvc.perform(asUser(get('/api/pdf/generic'))).andExpect(status().isMethodNotAllowed())
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        b.contentType(MediaType.APPLICATION_JSON).content(body)
    }

    private static String esc(String s) {
        s.replace('\\', '\\\\').replace('"', '\\"').replace('\r', '\\r').replace('\n', '\\n').replace('\t', '\\t')
    }
}
