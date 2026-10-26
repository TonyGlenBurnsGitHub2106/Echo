package com.echo

// Story 5654: the two Outlook clients -- the pretend mailbox the tests and a home computer use, and the
// file names the PowerShell bridge hands Outlook for attachments.

import java.time.LocalDateTime

import spock.lang.Unroll

class OutlookClientSpec extends EchoSpockBaseSpec {

    DemoOutlookClient demo = new DemoOutlookClient()

    def "the pretend Inbox lists newest first, from a moment on"() {

        when:
        List<OutlookClient.MessageMeta> all = demo.list('inbox', LocalDateTime.now().minusDays(30), null, 100)
        List<OutlookClient.MessageMeta> today = demo.list('inbox', LocalDateTime.now().minusHours(3), null, 100)

        then:
        all.size() == 6
        all*.received() == all*.received().sort(false).reverse()
        today.size() == 2
        today.every { it.subject().contains('Q4 claims batch review') }
    }

    def "a sent message lands in Sent Items stamped with its send key, in the conversation it answers"() {

        given:
        OutlookClient.MessageMeta dana = demo.list('inbox', LocalDateTime.now().minusDays(1), null, 10).find { it.from().contains('dana') }

        when:
        OutlookClient.Sent sent = demo.send(new OutlookClient.Outgoing('reply', dana.id(), 'dana.whitfield@work.example', null, null,
            'RE: x', 'ok', false, 'k123'), [])
        List<OutlookClient.MessageMeta> box = demo.list('sent', LocalDateTime.now().minusMinutes(1), null, 10)

        then:
        sent.sendKey() == 'k123'
        sent.threadId() == dana.threadId()
        box.size() == 1
        box[0].sendKey() == 'k123'
    }

    def "a forward carries the original's files only when asked"() {

        given:
        OutlookClient.MessageMeta acme = demo.list('inbox', LocalDateTime.now().minusDays(5), null, 10).find { it.subject().startsWith('Invoice') }

        when:
        demo.send(new OutlookClient.Outgoing('forward', acme.id(), 'ap@work.example', null, null, 'FW: Invoice', 'see below', keep, 'f' + keep), [])
        OutlookClient.MessageMeta fw = demo.list('sent', LocalDateTime.now().minusMinutes(1), null, 10).find { it.sendKey() == 'f' + keep }

        then:
        demo.body(fw.id()).attachments() == files

        where:
        keep  | files
        true  | ['invoice-4471.txt']
        false | []
    }

    def "a draft's version changes only when its words do"() {

        given:
        OutlookClient.DraftRef first = demo.saveDraft(null, new OutlookClient.Outgoing('new', null, 'a@work.example', null, null, 'S', 'one', false, null))

        when:
        OutlookClient.DraftRef same = demo.saveDraft(first.draftId(), new OutlookClient.Outgoing('new', null, 'a@work.example', null, null, 'S', 'one', false, null))
        OutlookClient.DraftRef changed = demo.saveDraft(first.draftId(), new OutlookClient.Outgoing('new', null, 'a@work.example', null, null, 'S', 'two', false, null))

        then:
        same.version() == first.version()
        changed.version() != first.version()
        demo.listDrafts().size() == 2                // the one "written in Outlook" too

        when:
        demo.deleteDraft(first.draftId())
        demo.deleteDraft(first.draftId())            // already gone: not an error

        then:
        demo.listDrafts().size() == 1
    }

    def "Outlook not having an item is a 404"() {

        when:
        demo.body('no-such-id')

        then:
        OutlookClient.OutlookException ex = thrown()
        ex.status() == 404
    }

    @Unroll
    def "the bridge hands Outlook #expected for an attachment named #name (file #index)"() {

        expect:
        PowerShellOutlookClient.safeName(name, index) == expected

        where:
        name                 | index | expected
        'invoice.pdf'        | 0     | 'invoice.pdf'
        'invoice.pdf'        | 2     | '2_invoice.pdf'
        'a/b\\c:d*e?f"g<h>|' | 0     | 'a_b_c_d_e_f_g_h__'
        null                 | 0     | 'attachment'
        '   '                | 1     | '1_attachment'
    }
}
