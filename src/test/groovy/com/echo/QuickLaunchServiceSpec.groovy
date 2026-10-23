package com.echo

// Story 5653: Quick Launch pins -- trimming, the eight-character icon, and re-numbering.

import spock.lang.PendingFeature
import spock.lang.Unroll

class QuickLaunchServiceSpec extends EchoSpockBaseSpec {

    QuickLaunchRepository repo = Mock()
    QuickLaunchService service = new QuickLaunchService(repo)

    def "list should pass through when called"() {

        given:
        List<QuickLaunch> pins = [new QuickLaunch(quickLaunchId: 1, label: 'A')]

        when:
        List<QuickLaunch> got = service.list()

        then:
        1 * repo.list() >> pins
        got.is(pins)
    }

    def "save should trim label, url and icon and answer the repository's id when they are padded"() {

        when:
        int id = service.save(0, '  Solutions  ', ' /solutions/ ', ' S ')

        then:
        1 * repo.save(0, 'Solutions', '/solutions/', 'S') >> 12
        id == 12
    }

    @Unroll
    def "save should store empty strings when #scenario"() {

        when:
        service.save(3, label, url, null)

        then:
        1 * repo.save(3, expectedLabel, expectedUrl, null)

        where:
        scenario                 | label | url  || expectedLabel | expectedUrl
        'both are null'          | null  | null || ''            | ''
        'the url is null'        | 'A'   | null || 'A'           | ''
        'the label is null'      | null  | '/x' || ''            | '/x'
    }

    @Unroll
    def "#seq. save should store the icon as #stored when it is #scenario"() {

        given:
        String icon = repeat == null ? value : getReplicatedString(value, repeat)
        String expected = storedRepeat == null ? stored : getReplicatedString(stored, storedRepeat)

        when:
        service.save(1, 'L', '/u', icon)

        then:
        1 * repo.save(1, 'L', '/u', expected)

        where:
        seq | scenario                    | value            | repeat || stored           | storedRepeat
        1   | 'null'                      | null             | null   || null             | null
        2   | 'empty'                     | ''               | null   || null             | null
        3   | 'blank'                     | '    '           | null   || null             | null
        4   | 'one letter'                | 'S'              | null   || 'S'              | null
        5   | 'eight characters'          | '12345678'       | null   || '12345678'       | null
        6   | 'nine characters'           | '123456789'      | null   || '12345678'       | null
        7   | 'nine characters padded'    | '  123456789  '  | null   || '12345678'       | null
        8   | 'a hundred characters'      | 'x'              | 100    || 'x'              | 8
        9   | 'a BMP symbol'              | '\u2605'         | null   || '\u2605'         | null
        10  | 'an emoji'                  | '\ud83d\ude80'   | null   || '\ud83d\ude80'   | null
    }

    @PendingFeature(reason = 'BUG: QuickLaunchService.save cuts the icon at 8 UTF-16 chars, which can split an emoji surrogate pair and store a broken character')
    def "save should never cut an emoji in half when the icon is longer than eight characters"() {

        given:
        String icon = 'abcdefg' + '\ud83d\ude80'
        String stored = null

        when:
        service.save(1, 'L', '/u', icon)

        then:
        1 * repo.save(1, 'L', '/u', _) >> { int id, String l, String u, String i -> stored = i; 1 }
        !Character.isHighSurrogate(stored.charAt(stored.length() - 1))
    }

    def "delete should pass through when called"() {

        when:
        service.delete(9)

        then:
        1 * repo.delete(9)
    }

    def "reorder should number the pins 10, 20, 30 in the order given when every id is valid"() {

        when:
        service.reorder([5, 3, 8])

        then:
        1 * repo.setSortOrder(5, 10)

        then:
        1 * repo.setSortOrder(3, 20)

        then:
        1 * repo.setSortOrder(8, 30)
        0 * repo._
    }

    def "reorder should skip null, zero and negative ids without leaving a gap when they are mixed in"() {

        when:
        service.reorder([null, 4, 0, -2, 6])

        then:
        1 * repo.setSortOrder(4, 10)
        1 * repo.setSortOrder(6, 20)
        0 * repo._
    }

    @Unroll
    def "reorder should write nothing when the ids are #scenario"() {

        given:
        List<Integer> ids = idsOf(kind)

        when:
        service.reorder(ids)

        then:
        0 * repo._

        where:
        scenario                    | kind
        'null'                      | 'null'
        'empty'                     | 'empty'
        'one null'                  | 'one-null'
        'zero and negative'         | 'zero-negative'
    }

    def "reorder should write a repeated id each time when it appears twice"() {

        when:
        service.reorder([2, 2])

        then:
        1 * repo.setSortOrder(2, 10)
        1 * repo.setSortOrder(2, 20)
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static List<Integer> idsOf(String kind) {
        switch (kind) {
            case 'null': return null
            case 'empty': return []
            case 'one-null': return [null]
            case 'zero-negative': return [0, -1]
            default: throw new IllegalArgumentException(kind)
        }
    }
}
