package com.echo.e2e

import com.microsoft.playwright.Locator

import spock.lang.Unroll

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

/**
 * Story 5653: the sign-in card, the dashboard it opens on, signing out, and the dashboard's
 * folding menu sections (remembered in the database, so they survive a reload).
 */
class SignInE2ESpec extends EchoE2EBaseSpec {

    @Unroll
    def "signIn should show #expectedError and stay on the card when #scenario"() {

        given:
        open(device(deviceName))
        allow(401, '/api/auth/login$')
        String typedUser = typeUser ? USER : ''
        String typedPassword = typePassword ? 'not-the-password-' + RUN : ''

        when:
        go('/')
        tid('login-username').fill(typedUser)
        tid('login-password').fill(typedPassword)
        tid('login-submit-btn').click()

        then:
        assertThat(tid('login-error')).hasText(expectedError)
        tid('login-view').isVisible()
        !tid('home-page').isVisible()
        assertNoProblems()

        where:
        scenario                           | deviceName | typeUser | typePassword || expectedError
        'desktop, wrong password'          | 'desktop'  | true     | true         || 'That user name and password do not match.'
        'phone, wrong password'            | 'phone'    | true     | true         || 'That user name and password do not match.'
        'desktop, nothing typed'           | 'desktop'  | false    | false        || 'Enter username and password.'
        'phone, user name but no password' | 'phone'    | true     | false        || 'Enter username and password.'
    }

    @Unroll
    def "signIn should land on the dashboard titled #expectedTitle when #scenario"() {

        when:
        signIn(device(deviceName))

        then:
        tid('home-page').isVisible()
        tid('page-title').first().textContent().trim() == expectedTitle
        tid('home-status').textContent() =~ /^Echo \S+ +\| +\S+/
        tid('nav-solutions').isVisible()
        !tid('login-view').isVisible()
        sidewaysOverflow() <= 0
        assertNoProblems()

        where:
        scenario                         | deviceName || expectedTitle
        'desktop, right password'        | 'desktop'  || 'Echo'
        'phone, right password'          | 'phone'    || 'Echo'
    }

    @Unroll
    def "reload should keep the dashboard showing #expectedVisible when #scenario"() {

        given:
        signIn(device(deviceName))

        when:
        page.reload()
        settle()

        then:
        assertThat(tid(expectedVisible)).isVisible()
        !tid('login-view').isVisible()
        assertNoProblems()

        where:
        scenario                         | deviceName || expectedVisible
        'desktop, signed in'             | 'desktop'  || 'home-page'
        'phone, signed in'               | 'phone'    || 'home-page'
    }

    @Unroll
    def "signOut should return to #expectedVisible and stay signed out when #scenario"() {

        given:
        signIn(device(deviceName))

        when:
        tid('nav-logout').click()
        settle()

        then:
        assertThat(tid(expectedVisible)).isVisible()
        !tid('home-page').isVisible()

        when: 'the page is reloaded'
        page.reload()
        settle()

        then:
        assertThat(tid(expectedVisible)).isVisible()

        when: 'a deep page is opened while signed out (its first API calls answer 401 before the redirect)'
        allow(401, '^/api/solutions')
        go('/solutions/solutions.html')

        then:
        assertThat(tid(expectedVisible)).isVisible()
        assertNoProblems()

        where:
        scenario                         | deviceName || expectedVisible
        'desktop, band Sign out icon'    | 'desktop'  || 'login-view'
        'phone, band Sign out icon'      | 'phone'    || 'login-view'
    }

    @Unroll
    def "dashboard should offer only the band's Sign out icon when #scenario"() {

        when:
        signIn(device(deviceName))

        then:
        tid('home-logout-btn').isVisible() == expectedTextButton
        tid('nav-logout').isVisible() == expectedIcon
        assertNoProblems()

        where:
        scenario                         | deviceName || expectedTextButton | expectedIcon
        'desktop, signed in'             | 'desktop'  || false              | true
        'phone, signed in'               | 'phone'    || false              | true
    }

    @Unroll
    def "menuSection fold should be remembered after a reload and Open all should reopen it when #scenario"() {

        given:
        signIn(device(deviceName))
        Locator section = page.locator('.report-links[data-section="' + sectionName + '"]')
        if (isFolded(section)) {
            section.locator('h2').click()
            page.waitForTimeout(600)
        }

        when:
        section.locator('h2').click()
        page.waitForTimeout(900)
        page.reload()
        settle()
        assertThat(tid('home-page')).isVisible()
        page.waitForTimeout(1200)

        then:
        isFolded(section) == expectedFoldedAfterReload
        !tid(linkInSection).isVisible()

        when: 'Open all is pressed (Fold all first when something is still open)'
        if (tid('home-fold-all-btn').textContent().trim() == 'Fold all') {
            tid('home-fold-all-btn').click()
            page.waitForTimeout(300)
        }
        tid('home-fold-all-btn').click()
        page.waitForTimeout(600)

        then:
        tid('home-fold-all-btn').textContent().trim() == 'Fold all'
        !isFolded(section)
        tid(linkInSection).isVisible()
        assertNoProblems()

        where:
        scenario                         | deviceName | sectionName | linkInSection  || expectedFoldedAfterReload
        'desktop, Echo section'          | 'desktop'  | 'echo'      | 'nav-settings' || true
        'phone, Echo section'            | 'phone'    | 'echo'      | 'nav-settings' || true
    }

    private static boolean isFolded(Locator section) {
        section.getAttribute('class').contains('collapsed')
    }
}
