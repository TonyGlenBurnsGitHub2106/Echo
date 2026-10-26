package com.echo

// Story 5654: the Email Client's whitelist and blacklist -- kept in Echo, never an Outlook rule.

import spock.lang.Unroll

class MailTriageServiceSpec extends EchoSpockBaseSpec {

    MailTriageRepository repo = Mock()
    MailTriageService service = new MailTriageService(repo)

    private static Map rule(int id, String key, String side, boolean isProtected = false) {
        [MailTriageRuleID: id, SenderKey: key, KeyKind: key.contains('@') ? 'address' : 'domain', Disposition: side,
         IsProtected: isProtected, Note: null, CreatedDate: null, ModifiedDate: null]
    }

    @Unroll
    def "decide should key #typed as #key (#kind) and save it on the #side list"() {

        when:
        Map out = service.decide(typed, disposition, 'Email Client')

        then:
        1 * repo.getRuleByKey(key) >> null
        1 * repo.saveRule(key, kind, side, null, null, 'Email Client') >> rule(5, key, side)
        1 * repo.counts(null) >> [White: 1, Black: 0]
        out.rule.senderKey == key
        out.rule.disposition == side
        out.counts.white == 1

        where:
        typed                                | disposition  | key                     | kind      | side
        'dana.w@mail.work.example'           | 'white'      | 'work.example'          | 'domain'  | 'white'
        'sam.ortiz@gmail.com'                | 'whitelist'  | 'sam.ortiz@gmail.com'   | 'address' | 'white'
        '@acme.co.uk'                        | 'blacklist'  | 'acme.co.uk'            | 'domain'  | 'black'
        'news.monitoring.example'            | 'delete'     | 'monitoring.example'    | 'domain'  | 'black'
    }

    @Unroll
    def "decide should refuse #why"() {

        when:
        service.decide(typed, disposition, null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains(message)
        0 * repo.saveRule(*_)

        where:
        why                               | typed           | disposition | message
        'a free-mail domain whole'        | 'gmail.com'     | 'black'     | 'never ruled on whole'
        'something that is not a sender'  | 'not a sender'  | 'white'     | 'Give a domain'
        'Junk, which Echo does not have'  | 'example.com'   | 'junk'      | 'Choose Whitelist or Blacklist'
    }

    def "decide should never blacklist a protected sender"() {

        when:
        service.decide('irs.example', 'black', null)

        then:
        1 * repo.getRuleByKey('irs.example') >> rule(9, 'irs.example', 'white', true)
        SaveRefusedException ex = thrown()
        ex.message.endsWith('Nothing was saved.')
        0 * repo.saveRule(*_)
    }

    def "remove should take a sender off its list and say which"() {

        when:
        Map out = service.remove(4)

        then:
        1 * repo.getRule(4) >> rule(4, 'monitoring.example', 'black')
        1 * repo.deleteRule(4) >> true
        1 * repo.counts(null) >> [White: 2, Black: 0]
        out.removed
        out.senderKey == 'monitoring.example'
    }

    def "remove should answer null for no such rule, and refuse a protected one"() {

        when:
        Map none = service.remove(77)

        then:
        1 * repo.getRule(77) >> null
        none == null

        when:
        service.remove(9)

        then:
        1 * repo.getRule(9) >> rule(9, 'irs.example', 'white', true)
        thrown(IllegalArgumentException)
        0 * repo.deleteRule(_)
    }

    @Unroll
    def "rules should ask for disposition #asked as #sent"() {

        when:
        Map out = service.rules(asked, '  Work.Example ', 0, 0)

        then:
        1 * repo.listRules(sent, 'work.example', 0, 51) >> [rule(1, 'work.example', 'white')]
        1 * repo.counts('work.example') >> [White: 1, Black: 0, MatchAll: 1, MatchWhite: 1, MatchBlack: 0]
        out.rows.size() == 1
        !out.hasMore
        out.counts.matchWhite == 1

        where:
        asked   | sent
        'white' | 'white'
        'black' | 'black'
        'junk'  | 'all'
        null    | 'all'
    }
}
