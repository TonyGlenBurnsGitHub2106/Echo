package com.echo

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Story 5653: the Reader's marks and place work; an excerpt has nowhere to go in Echo. */
@WebMvcTest(ReaderController)
class ReaderControllerSpec extends EchoWebSpec {

    @SpringBean ReaderHighlightService highlights = Mock()
    @SpringBean ReaderPositionService positions = Mock()

    def "excerpt should answer 404 not_available and touch nothing when Echo has no Research shelf"() {

        when:
        def result = mvc.perform(asUser(post('/api/reader/excerpt').contentType(MediaType.APPLICATION_JSON)
            .content('{"highlight":{"docKey":"url:/api/solutions/files/1/content","selectedText":"x"},"projectId":1}')))

        then:
        0 * highlights._
        0 * positions._
        result.andExpect(status().isNotFound())
            .andExpect(jsonPath('$.error').value('not_available'))
            .andExpect(jsonPath('$.message').value('Echo has no Research shelf, so an excerpt cannot be saved here.'))
    }

    def "highlights should list a document's marks when a person asks"() {

        when:
        def result = mvc.perform(asUser(get('/api/reader/highlights').param('docKey', 'url:/api/solutions/files/1/content')))

        then:
        1 * highlights.listForDocument('url:/api/solutions/files/1/content') >> []
        result.andExpect(status().isOk())
    }

    def "the Reader should answer 401 when there is no credential"() {

        expect:
        mvc.perform(get('/api/reader/highlights').param('docKey', 'k')).andExpect(status().isUnauthorized())
    }
}
