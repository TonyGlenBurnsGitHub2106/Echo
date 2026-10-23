package com.echo.e2e

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import spock.lang.Unroll

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

/**
 * Story 5653: the Settings page (About Echo, the password card's checks, Sign out everywhere,
 * folding cards) and the Help page (its two folds, "Setting up and installing Echo" and
 * "Using Echo", open and close; Print opens the Print menu).
 * The password is never actually changed: every check here stops before the server.
 */
class SettingsHelpE2ESpec extends EchoE2EBaseSpec {

    @Unroll
    def "settings should show About Echo naming #expectedText when #scenario"() {

        given:
        signIn(device(deviceName))
        boolean phone = (boolean) device(deviceName).phone

        when:
        go('/settings/settings.html')
        openCard(page, 'about')

        then:
        assertThat(tid('settings-about')).containsText(expectedText)
        !tid('settings-about').textContent().contains('Loading')
        !tid('settings-about').textContent().contains('failed')
        ['password', 'sessions', 'about'].every { String name -> card(page, name).count() == 1 }
        !phone || sidewaysOverflow() <= 0
        assertNoProblems()

        where:
        scenario  | deviceName || expectedText
        'desktop' | 'desktop'  || 'Echo'
        'phone'   | 'phone'    || 'Echo'
    }

    @Unroll
    def "changePassword should warn #expectedWarning before calling the server when #scenario"() {

        given:
        signIn(device(deviceName))
        go('/settings/settings.html')
        openCard(page, 'password')

        when:
        tid('settings-password-current').fill(current)
        tid('settings-password-new').fill(newPassword)
        tid('settings-password-again').fill(again)
        tid('settings-password-save').click()

        then:
        assertThat(tid('settings-password-warn')).hasText(expectedWarning)
        ['settings-password-current', 'settings-password-new', 'settings-password-again'].every { String id -> tid(id).getAttribute('type') == 'password' }
        tid('picasso-dialog').count() == 0
        assertNoProblems()

        where:
        scenario                         | deviceName | current    | newPassword     | again           || expectedWarning
        'desktop, nothing typed'         | 'desktop'  | ''         | ''              | ''              || 'Type your current password.'
        'phone, short new password'      | 'phone'    | 'anything' | 'short'         | 'short'         || 'A new password needs at least 8 characters.'
        'desktop, new passwords differ'  | 'desktop'  | 'anything' | 'long-enough-1' | 'long-enough-2' || 'The two new passwords are not the same.'
        'phone, new passwords differ'    | 'phone'    | 'anything' | 'long-enough-1' | 'long-enough-2' || 'The two new passwords are not the same.'
    }

    @Unroll
    def "settingsCards fold should be remembered and Fold all should read #expectedFoldAllLabel when #scenario"() {

        given:
        signIn(device(deviceName))
        go('/settings/settings.html')
        Locator foldAll = tid('settings-fold-all')
        if (foldAll.textContent().trim() == 'Open all') {
            foldAll.click()
            page.waitForTimeout(400)
        }

        when:
        card(page, 'about').locator('h2').click()
        page.waitForTimeout(900)
        page.reload()
        settle()
        page.waitForTimeout(1000)

        then:
        isFolded(card(page, 'about'))
        !isFolded(card(page, 'password'))

        when:
        foldAll.click()
        page.waitForTimeout(300)

        then:
        ['password', 'sessions', 'about'].every { String name -> isFolded(card(page, name)) }
        foldAll.textContent().trim() == expectedFoldAllLabel

        when:
        foldAll.click()
        page.waitForTimeout(600)

        then:
        ['password', 'sessions', 'about'].every { String name -> !isFolded(card(page, name)) }
        assertNoProblems()

        where:
        scenario  | deviceName || expectedFoldAllLabel
        'desktop' | 'desktop'  || 'Open all'
        'phone'   | 'phone'    || 'Open all'
    }

    @Unroll
    def "signOutEverywhere should ask first, stay on No and show #expectedPage on Yes when #scenario"() {

        given:
        signIn(device(deviceName))
        go('/settings/settings.html')
        openCard(page, 'sessions')

        when:
        tid('settings-signout-everywhere').click()

        then:
        assertThat(tid('picasso-dialog')).isVisible()
        tid('picasso-dialog-message').textContent().contains('Sign out of Echo everywhere?')

        when:
        tid('picasso-dialog-btn-false').click()

        then:
        assertThat(tid('picasso-dialog')).hasCount(0)
        tid('settings-page').isVisible()

        when:
        tid('settings-signout-everywhere').click()
        tid('picasso-dialog-btn-true').click()
        settle()

        then:
        assertThat(tid(expectedPage)).isVisible()
        assertNoProblems()

        where:
        scenario  | deviceName || expectedPage
        'desktop' | 'desktop'  || 'login-view'
        'phone'   | 'phone'    || 'login-view'
    }

    @Unroll
    def "helpFolds should open to show #expectedSetupHeading and #expectedUsingHeading and close again when #scenario"() {

        given:
        signIn(device(deviceName))
        boolean phone = (boolean) device(deviceName).phone
        go('/help/help.html')
        Locator setup = helpFold(page, 'Setting up and installing Echo')
        Locator using = helpFold(page, 'Using Echo')
        assertThat(setup).hasCount(1)
        assertThat(using).hasCount(1)

        expect:
        setup.getAttribute('open') == null
        using.getAttribute('open') == null

        when:
        setup.locator('summary').click()
        using.locator('summary').click()

        then:
        setup.getAttribute('open') != null
        using.getAttribute('open') != null
        assertThat(heading(page, expectedSetupHeading)).isVisible()
        assertThat(heading(page, expectedUsingHeading)).isVisible()
        !phone || sidewaysOverflow() <= 0

        when:
        setup.locator('summary').click()
        using.locator('summary').click()

        then:
        setup.getAttribute('open') == null
        using.getAttribute('open') == null
        heading(page, expectedUsingHeading).isHidden()
        assertNoProblems()

        where:
        scenario  | deviceName || expectedSetupHeading | expectedUsingHeading
        'desktop' | 'desktop'  || 'Running Echo'       | 'Signing in'
        'phone'   | 'phone'    || 'Running Echo'       | 'Signing in'
    }

    @Unroll
    def "helpPrint should open the Print menu offering #expectedDestinations and close on Cancel when #scenario"() {

        given:
        signIn(device(deviceName))
        go('/help/help.html')

        when:
        page.locator('#lwIconBand [data-lw-band-print]').click()
        assertThat(page.locator('button[data-dest="download"]')).isVisible()

        then:
        page.locator('button[data-dest]:visible').evaluateAll('bs => bs.map(b => b.getAttribute("data-dest")).join(",")') == expectedDestinations

        when:
        page.locator('button[data-dest=""]:visible').click()

        then:
        assertThat(page.locator('button[data-dest="download"]')).isHidden()
        assertNoProblems()

        where:
        scenario  | deviceName || expectedDestinations
        'desktop' | 'desktop'  || 'print,display,download,'
        'phone'   | 'phone'    || 'print,display,download,'
    }

    private static Locator card(Page onPage, String name) {
        onPage.locator('.st-card[data-card="' + name + '"]')
    }

    private static boolean isFolded(Locator element) {
        element.getAttribute('class').contains('folded')
    }

    private static void openCard(Page onPage, String name) {
        if (isFolded(card(onPage, name))) card(onPage, name).locator('h2').click()
    }

    private static Locator helpFold(Page onPage, String summaryText) {
        onPage.locator('#doc details').filter(new Locator.FilterOptions()
            .setHas(onPage.locator('summary', new Page.LocatorOptions().setHasText(summaryText))))
    }

    private static Locator heading(Page onPage, String text) {
        onPage.locator('#doc h3', new Page.LocatorOptions().setHasText(text))
    }
}
