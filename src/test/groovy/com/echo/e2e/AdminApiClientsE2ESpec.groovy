package com.echo.e2e

import spock.lang.Unroll

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

/**
 * Story 5653: Admin -> API Clients hands out the keys an agent (Rex) uses, so it stands behind a
 * re-type-your-password step-up. A wrong password is refused (HTTP 400, never 401: a 401 would
 * read as "signed out"), Cancel goes back to the menu, the right password opens the page.
 * Nothing is created here -- no client, no key.
 */
class AdminApiClientsE2ESpec extends EchoE2EBaseSpec {

    @Unroll
    def "stepUp should refuse #expectedEmptyError then #expectedWrongError, then open the clients on the right password when #scenario"() {

        given:
        signIn(device(deviceName))
        boolean phone = (boolean) device(deviceName).phone
        allow(400, '/api/auth/verify-password$')

        when:
        go('/admin/api-clients.html')

        then:
        assertThat(tid('stepup-overlay')).isVisible()

        when: 'nothing is typed'
        tid('stepup-ok').click()

        then:
        assertThat(tid('stepup-error')).hasText(expectedEmptyError)

        when: 'a wrong password is typed'
        tid('stepup-password').fill('not-the-password-' + RUN)
        tid('stepup-ok').click()

        then:
        assertThat(tid('stepup-error')).hasText(expectedWrongError)
        tid('stepup-overlay').isVisible()

        when: 'the right password is typed'
        tid('stepup-password').fill(password())
        tid('stepup-ok').click()
        settle()

        then:
        assertThat(tid('stepup-overlay')).hasCount(0)
        page.url().contains('/admin/api-clients.html')
        assertThat(tid('api-clients-page')).isVisible()
        !tid('api-clients-list').textContent().contains('Loading')

        when: 'the Access log tab is opened'
        tid('api-clients-tab-access').click()
        page.waitForTimeout(600)

        then:
        assertThat(tid('api-clients-access')).isVisible()
        assertThat(tid('api-clients-access-client-trigger')).isVisible()
        bareSelects().isEmpty()

        when: 'the page is reloaded inside fifteen minutes'
        page.reload()
        settle()
        page.waitForTimeout(800)

        then:
        tid('stepup-overlay').count() == 0
        assertThat(tid('api-clients-page')).isVisible()
        !phone || sidewaysOverflow() <= 0
        assertNoProblems()

        where:
        scenario  | deviceName || expectedEmptyError    | expectedWrongError
        'desktop' | 'desktop'  || 'Type your password.' | 'That password is not right.'
        'phone'   | 'phone'    || 'Type your password.' | 'That password is not right.'
    }

    @Unroll
    def "stepUp cancel should return to #expectedPage when #scenario"() {

        given:
        signIn(device(deviceName))
        go('/admin/api-clients.html')
        assertThat(tid('stepup-overlay')).isVisible()

        when:
        tid('stepup-cancel').click()
        settle()

        then:
        assertThat(tid(expectedPage)).isVisible()
        assertNoProblems()

        where:
        scenario  | deviceName || expectedPage
        'desktop' | 'desktop'  || 'home-page'
        'phone'   | 'phone'    || 'home-page'
    }

    @Unroll
    def "addClient form should open and close on Cancel without saving when #scenario"() {

        given:
        signIn(device(deviceName))
        go('/admin/api-clients.html')
        tid('stepup-password').fill(password())
        tid('stepup-ok').click()
        assertThat(tid('stepup-overlay')).hasCount(0)

        when:
        tid('api-clients-add-btn').click()

        then:
        assertThat(tid('api-clients-edit-overlay')).isVisible()
        tid('api-clients-name').isVisible() == expectedNameField

        when:
        tid('api-clients-cancel').click()

        then:
        assertThat(tid('api-clients-edit-overlay')).isHidden()
        assertNoProblems()

        where:
        scenario  | deviceName || expectedNameField
        'desktop' | 'desktop'  || true
        'phone'   | 'phone'    || true
    }
}
