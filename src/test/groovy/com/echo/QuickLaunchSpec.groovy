package com.echo

// Story 5653: the Quick Launch row -- a plain public-field object the controller serializes.

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.time.LocalDateTime

class QuickLaunchSpec extends EchoSpockBaseSpec {

    def "a new pin should be empty when constructed"() {

        when:
        QuickLaunch q = new QuickLaunch()

        then:
        q.quickLaunchId == 0
        q.label == null
        q.url == null
        q.iconKey == null
        q.sortOrder == 0
        q.createdUtc == null
        q.modifiedUtc == null
    }

    def "every field should be readable when it has been set"() {

        given:
        LocalDateTime t = getLocalDateTime('10/22/2026 09:00')

        when:
        QuickLaunch q = new QuickLaunch(quickLaunchId: 4, label: 'Solutions', url: '/solutions/', iconKey: 'S',
                                        sortOrder: 30, createdUtc: t, modifiedUtc: t.plusDays(1))

        then:
        q.quickLaunchId == 4
        q.label == 'Solutions'
        q.url == '/solutions/'
        q.iconKey == 'S'
        q.sortOrder == 30
        q.createdUtc == t
        q.modifiedUtc == t.plusDays(1)
    }

    def "the class should have exactly the seven documented public fields when inspected"() {

        when:
        List<Field> fields = QuickLaunch.declaredFields.findAll { Field f -> !f.synthetic }

        then:
        fields*.name.sort() == ['createdUtc', 'iconKey', 'label', 'modifiedUtc', 'quickLaunchId', 'sortOrder', 'url']
        fields.every { Field f -> Modifier.isPublic(f.modifiers) }
    }
}
