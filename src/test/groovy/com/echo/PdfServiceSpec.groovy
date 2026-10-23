package com.echo

// Story 5653: XHTML to PDF with openhtmltopdf, and the named-entity pre-pass that lets
// &nbsp; and friends through the strict XML parser.

import java.nio.charset.StandardCharsets

import spock.lang.Unroll

class PdfServiceSpec extends EchoSpockBaseSpec {

    PdfService service = new PdfService()

    // ---- input checks ----------------------------------------------------------------------------

    @Unroll
    def "htmlToPdf should refuse the input when it is #scenario"() {

        when:
        service.htmlToPdf(input)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'xhtml is empty'

        where:
        scenario | input
        'null'   | null
        'empty'  | ''
    }

    // ---- rendering --------------------------------------------------------------------------------

    @Unroll
    def "htmlToPdf should render a PDF when the input is #scenario"() {

        expect:
        isPdf(service.htmlToPdf(input))

        where:
        scenario                                  | input
        'well-formed XHTML'                       | '<html><head><title>t</title></head><body><h1>Echo</h1><p>Hello.</p></body></html>'
        'inline CSS and a table'                  | '<html><head><style>td{border:1px solid #000}</style></head><body><table><tr><td>a</td><td>b</td></tr></table></body></html>'
        'named HTML entities'                     | '<html><body><p>a&nbsp;b &mdash; c &copy; d &middot; e &hellip; &euro; &rarr; &hearts;</p></body></html>'
        'the five XML entities and numeric refs'  | '<html><body><p>&amp; &lt; &gt; &quot; &apos; &#160; &#x2014;</p></body></html>'
    }

    @Unroll
    def "htmlToPdf should throw a wrapped render failure when the input is #scenario"() {

        when:
        service.htmlToPdf(input)

        then:
        RuntimeException ex = thrown(RuntimeException)
        !(ex instanceof IllegalArgumentException)
        ex.message.startsWith('PDF render failed: ')
        ex.cause != null

        where:
        scenario              | input
        'an unknown entity'   | '<html><body><p>&bogusentity;</p></body></html>'
        'an unclosed tag'     | '<html><body><p>unclosed</body></html>'
        'not markup'          | '<<<>>>'
    }

    def "htmlToPdf should render a PDF when the input is large"() {

        given:
        String body = getReplicatedString('<p>A line of the long report.</p>', 300)

        expect:
        isPdf(service.htmlToPdf('<html><body>' + body + '</body></html>'))
    }

    // ---- the entity pre-pass ------------------------------------------------------------------------

    @Unroll
    def "#seq. numericizeNamedEntities should answer '#output' when the input is #scenario"() {

        expect:
        PdfService.numericizeNamedEntities(input) == output

        where:
        seq | scenario                         | input                     || output
        1   | 'empty'                          | ''                        || ''
        2   | 'plain text'                     | 'plain text'              || 'plain text'
        3   | 'nbsp'                           | '&nbsp;'                  || '&#160;'
        4   | 'two nbsp in text'               | 'a&nbsp;b&nbsp;c'         || 'a&#160;b&#160;c'
        5   | 'two dashes'                     | '&mdash;&ndash;'          || '&#8212;&#8211;'
        6   | 'copy in text'                   | '&copy; 2026'             || '&#169; 2026'
        7   | 'euro'                           | '&euro;'                  || '&#8364;'
        8   | 'card suits'                     | '&hearts;&diams;'         || '&#9829;&#9830;'
        9   | 'a name with a digit'            | '&sup2;'                  || '&#178;'
        10  | 'a fraction'                     | '&frac12;'                || '&#189;'
        11  | 'there4'                         | '&there4;'                || '&#8756;'
        12  | 'amp'                            | '&amp;'                   || '&amp;'
        13  | 'lt and gt'                      | '&lt;&gt;'                || '&lt;&gt;'
        14  | 'quot and apos'                  | '&quot;&apos;'            || '&quot;&apos;'
        15  | 'upper-case AMP'                 | '&AMP;'                   || '&AMP;'
        16  | 'title-case Lt'                  | '&Lt;'                    || '&Lt;'
        17  | 'upper-case Agrave'              | '&Agrave;'                || '&#192;'
        18  | 'lower-case agrave'              | '&agrave;'                || '&#224;'
        19  | 'upper-case Alpha'               | '&Alpha;'                 || '&#913;'
        20  | 'lower-case alpha'               | '&alpha;'                 || '&#945;'
        21  | 'upper-case Dagger'              | '&Dagger;'                || '&#8225;'
        22  | 'lower-case dagger'              | '&dagger;'                || '&#8224;'
        23  | 'NBSP falling back to lower case'| '&NBSP;'                  || '&#160;'
        24  | 'Mdash falling back to lower case'| '&Mdash;'                || '&#8212;'
        25  | 'an unknown name'                | '&bogus;'                 || '&bogus;'
        26  | 'a missing semicolon'            | '&nbsp'                   || '&nbsp'
        27  | 'a space after the ampersand'    | '& nbsp;'                 || '& nbsp;'
        28  | 'a decimal reference'            | '&#160;'                  || '&#160;'
        29  | 'a hex reference'                | '&#x2014;'                || '&#x2014;'
        30  | 'a one-letter name'              | '&a;'                     || '&a;'
        31  | 'a name starting with a digit'   | '&1abc;'                  || '&1abc;'
        32  | 'a doubled semicolon'            | '&nbsp;;'                 || '&#160;;'
        33  | 'a doubled ampersand'            | '&&nbsp;'                 || '&&#160;'
        34  | 'dollar and backslash kept'      | 'cost $5 &nbsp; \\ ok'    || 'cost $5 &#160; \\ ok'
        35  | 'replacement groups kept'        | '$1&bogus;\\2'            || '$1&bogus;\\2'
    }

    def "NAMED_TO_NUMERIC should hold the HTML4 set without the five XML entities when inspected"() {

        given:
        Map<String, Integer> table = PdfService.NAMED_TO_NUMERIC

        expect:
        table.size() >= 240
        !table.keySet().any { String k -> k in ['amp', 'lt', 'gt', 'quot', 'apos'] }
        table.values().every { Integer v -> v > 127 }
        table['nbsp'] == 160
        table['yuml'] == 255
        table['Yuml'] == 376
        table['diams'] == 9830
    }

    def "htmlToPdf should render every entry in the entity table when all are used at once"() {

        given:
        Map<String, Integer> table = PdfService.NAMED_TO_NUMERIC
        String all = table.keySet().collect { String k -> '&' + k + ';' }.join(' ')

        expect:
        isPdf(service.htmlToPdf('<html><body><p>' + all + '</p></body></html>'))
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static boolean isPdf(byte[] b) {
        b != null && b.length > 100 && new String(b, 0, 5, StandardCharsets.US_ASCII) == '%PDF-'
    }
}
