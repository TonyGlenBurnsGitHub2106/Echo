package com.echo

// Story 5654: the Email Client's service over Outlook -- what Check mail reads in (and what it never
// does), how a sent message finds its Outlook copy, and how a reply is built. Outlook is the pretend
// mailbox (DemoOutlookClient); the database is a mock.

import java.time.LocalDateTime

class GmailClientServiceSpec extends EchoSpockBaseSpec {

    GmailClientRepository repo = Mock()
    MailTriageRepository triageRepo = Mock()
    DemoOutlookClient outlook = new DemoOutlookClient()
    GmailClientService service = new GmailClientService(repo, triageRepo, new MailTriageService(triageRepo), outlook)

    def cleanup() { service.stopChecker() }

    def "Check mail should read whitelisted and undecided mail in, and never a blacklisted sender's"() {

        given:
        List<String> recorded = []
        repo.startCheck(_) >> 1
        repo.knownSince(_) >> ([] as Set)
        repo.sentKnownSince(_) >> ([] as Set)
        repo.lastCheck(_) >> null
        repo.draftsInGmail() >> []
        repo.listMissingBodies(_) >> []
        repo.listMissingSentBodies(_) >> []
        repo.draftFromGmail(_, _) >> [Recorded: 1]
        repo.finishCheck(1, _, _, true, null) >> [GmailClientCheckID: 1, IsComplete: true]
        triageRepo.getRuleByKey('monitoring.example') >> [Disposition: 'black']
        triageRepo.getRuleByKey('work.example') >> [Disposition: 'white']
        triageRepo.getRuleByKey(_) >> null

        when:
        Map out = service.check()

        then:
        5 * repo.recordMessage(_, _, _, _, _, _, _, _, _, _) >> { args -> recorded << (args[2] as String); true }
        out.checking == false
        out.undecidedAdded == 2                       // acme-supplies.example and sam.ortiz@gmail.com
        !recorded.contains('monitoring.example')
        recorded.count { it == 'work.example' } == 3
        out.draftsAdded == 1                          // the draft "written in Outlook"
    }

    def "a sent message kept under its send key should take its Outlook id when Check mail finds it"() {

        given: 'a reply sent from Echo'
        Map dana = [GmailClientMessageID: 4, GmailID: firstInboxId('Q4 claims batch review'), FromHeader: '"Dana" <dana.whitfield@work.example>',
                    ToHeader: DemoOutlookClient.ACCOUNT, Subject: 'Q4 claims batch review', ReceivedDate: LocalDateTime.now()]
        repo.getMessage(4) >> dana
        repo.getBody(4) >> [BodyText: 'Can you walk me through it?']
        String keptAs = null

        when:
        Map sent = service.reply(4, 'On it.', false)

        then:
        1 * repo.recordSent({ it.startsWith('echo:') }, _, DemoOutlookClient.ACCOUNT, _, _, _, 'Re: Q4 claims batch review', _, null, _, true) >> { args ->
            keptAs = args[0]; [Recorded: 1, GmailClientSentMessageID: 31] }
        1 * repo.saveSentBody(31, null, { it.startsWith('On it.') && it.contains('> Can you walk me through it?') }, null, false)
        sent.sent
        sent.sentId == 31

        when: 'Check mail reads Sent Items'
        repo.startCheck(_) >> 2
        repo.knownSince(_) >> ([] as Set)
        repo.sentKnownSince(_) >> ([keptAs] as Set)
        repo.recordMessage(*_) >> false
        repo.draftsInGmail() >> []
        repo.listMissingBodies(_) >> []
        repo.listMissingSentBodies(_) >> []
        repo.draftFromGmail(_, _) >> [Recorded: 0]
        repo.finishCheck(*_) >> [GmailClientCheckID: 2, IsComplete: true]
        triageRepo.getRuleByKey(_) >> null
        service.check()

        then: 'the kept row is re-keyed, never recorded twice'
        1 * repo.rekeySent(keptAs, { it.startsWith('demo-sent-') }, 'conv-q4', _) >> 31
        1 * repo.saveSentBody(31, null, { it.startsWith('On it.') }, null, false)   // the body read again from Outlook's copy
        0 * repo.recordSent(*_)
    }

    def "a reply to a message Outlook no longer has should still go, as a new message"() {

        given:
        repo.getMessage(5) >> [GmailClientMessageID: 5, GmailID: 'gone-from-outlook', FromHeader: 'greg@example.com',
                               ToHeader: DemoOutlookClient.ACCOUNT, Subject: 'Lunch', ReceivedDate: LocalDateTime.now()]
        repo.getBody(5) >> [BodyText: 'Noon?']
        repo.recordSent(*_) >> [Recorded: 1, GmailClientSentMessageID: 40]

        when:
        Map sent = service.reply(5, 'Yes', false)

        then:
        sent.sent
        sent.to == 'greg@example.com'
        sent.subject == 'Re: Lunch'
    }

    def "the same message sent twice within moments should be refused the second time"() {

        given:
        repo.recordSent(*_) >> [Recorded: 1, GmailClientSentMessageID: 41]

        when:
        service.compose('a@work.example', null, 'Hi', 'Hello')
        service.compose('a@work.example', null, 'Hi', 'Hello')

        then:
        SaveRefusedException ex = thrown()
        ex.message == 'That message was just sent. Nothing was sent again.'
    }

    def "status should say Outlook's own reason when it cannot be reached"() {

        given:
        OutlookClient closed = Stub() { account() >> { throw new OutlookClient.OutlookException(409, 'Outlook is not running on this computer.') } }
        GmailClientService s = new GmailClientService(repo, triageRepo, new MailTriageService(triageRepo), closed)
        repo.lastCheck(_) >> null
        repo.bodyCounts() >> [Saved: 0, Missing: 0]

        when:
        Map st = s.status()

        then:
        !st.connected
        st.outlookError == 'Outlook is not running on this computer.'

        cleanup:
        s.stopChecker()
    }

    def "sending a draft written in Outlook sends Outlook's own draft and never deletes it afterwards"() {

        given:
        OutlookClient ol = Mock()
        GmailClientService s = new GmailClientService(repo, triageRepo, new MailTriageService(triageRepo), ol)
        repo.getDraft(8) >> [GmailClientDraftID: 8, Mode: 'gmail', GmailDraftID: 'D-ENTRY']
        repo.recordSent(*_) >> [Recorded: 1, GmailClientSentMessageID: 50]
        ol.account() >> DemoOutlookClient.ACCOUNT

        when:
        Map out = s.sendDraft(8, 'a@work.example', null, null, 'S', 'words', [])

        then:
        1 * ol.send({ it.baseKind() == 'draft' && it.baseId() == 'D-ENTRY' && it.sendKey() }, []) >> new OutlookClient.Sent('k', 'c')
        1 * repo.closeDraft(8, 'sent') >> true
        0 * ol.deleteDraft(_)
        out.sent

        cleanup:
        s.stopChecker()
    }

    def "a send Outlook did not answer in time is not let through again"() {

        given:
        OutlookClient ol = Mock()
        GmailClientService s = new GmailClientService(repo, triageRepo, new MailTriageService(triageRepo), ol)
        ol.account() >> DemoOutlookClient.ACCOUNT

        when:
        s.compose('a@work.example', null, 'Hi', 'Hello')

        then:
        1 * ol.send(*_) >> { throw new OutlookClient.OutlookException(504, 'Outlook did not answer within 300 seconds. Try again.') }
        OutlookClient.OutlookException ex = thrown()
        ex.message.contains('may have gone')

        when:
        s.compose('a@work.example', null, 'Hi', 'Hello')

        then:
        thrown(SaveRefusedException)
        0 * ol.send(*_)

        cleanup:
        s.stopChecker()
    }

    private String firstInboxId(String subject) {
        outlook.list('inbox', LocalDateTime.now().minusDays(30), null, 100).find { it.subject() == subject }.id()
    }
}
