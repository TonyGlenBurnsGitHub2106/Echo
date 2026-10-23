package com.echo.e2e

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import spock.lang.PendingFeature
import spock.lang.Unroll

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

/**
 * Story 5653: the Main Menu Tool Bar Strip ("the icon band") that quick-launch.js injects on every
 * page -- Back, Forward, Main menu, Quick Launch, Pin and Print on the left, Sign out alone on the
 * right -- and what each icon does. The dashboard carries the band without Pin (it IS Quick
 * Launch) and without Print, exactly as LeonardoWeb's dashboard does.
 */
class IconBandE2ESpec extends EchoE2EBaseSpec {

    @Unroll
    def "dashboard should show the nav cluster with #expectedPinCount Pin buttons when #scenario"() {

        when:
        signIn(device(deviceName))

        then:
        navClusterVisible(page)
        page.locator('#lwPinBtn').count() == expectedPinCount
        assertNoProblems()

        where:
        scenario  | deviceName || expectedPinCount
        'desktop' | 'desktop'  || 0
        'phone'   | 'phone'    || 0
    }

    @Unroll
    def "#seq. iconBand should carry Pin and #expectedPrintCount Print button on #path when #scenario"() {

        given:
        signIn(device(deviceName))
        int viewportWidth = (int) device(deviceName).width

        when:
        go(path)

        then:
        navClusterVisible(page)
        assertThat(page.locator('#lwPinBtn')).isVisible()
        assertThat(bandPrint(page)).isVisible()
        bandPrint(page).count() == expectedPrintCount
        tid('icon-band').boundingBox().width <= viewportWidth
        assertNoProblems()

        where:
        seq | scenario               | deviceName | path                         || expectedPrintCount
        1   | 'desktop, Solutions'   | 'desktop'  | '/solutions/solutions.html'  || 1
        2   | 'phone, Solutions'     | 'phone'    | '/solutions/solutions.html'  || 1
        3   | 'desktop, Settings'    | 'desktop'  | '/settings/settings.html'    || 1
        4   | 'phone, Settings'      | 'phone'    | '/settings/settings.html'    || 1
        5   | 'desktop, Help'        | 'desktop'  | '/help/help.html'            || 1
        6   | 'phone, Help'          | 'phone'    | '/help/help.html'            || 1
        7   | 'desktop, API Clients' | 'desktop'  | '/admin/api-clients.html'    || 1
        8   | 'phone, API Clients'   | 'phone'    | '/admin/api-clients.html'    || 1
    }

    @Unroll
    def "solutionPage should carry the whole band with #expectedPrintCount Print button when #scenario"() {

        given:
        signIn(device(deviceName))
        int solutionId = seedSolution('band')

        when:
        go('/solutions/solution.html?id=' + solutionId)

        then:
        navClusterVisible(page)
        assertThat(page.locator('#lwPinBtn')).isVisible()
        assertThat(bandPrint(page)).isVisible()
        bandPrint(page).count() == expectedPrintCount
        assertNoProblems()

        cleanup:
        if (page && solutionId) api('DELETE', '/api/solutions/' + solutionId)

        where:
        scenario  | deviceName || expectedPrintCount
        'desktop' | 'desktop'  || 1
        'phone'   | 'phone'    || 1
    }

    @PendingFeature(reason = 'BUG: Manage Quick Launch (quick-launch/manage.html) has no icon band at all -- its <body> carries ql-no-pin without ql-band, and it keeps its own "Home" link instead. Same as LeonardoWeb\'s copy.')
    @Unroll
    def "manageQuickLaunch should carry the icon band when #scenario"() {

        given:
        signIn(device(deviceName))

        when:
        go('/quick-launch/manage.html')

        then:
        tid('icon-band').count() == expectedBandCount
        navClusterVisible(page)

        where:
        scenario  | deviceName || expectedBandCount
        'desktop' | 'desktop'  || 1
        'phone'   | 'phone'    || 1
    }

    @Unroll
    def "back and forward should walk to #expectedBack and #expectedForward when #scenario"() {

        given:
        signIn(device(deviceName))

        when:
        tid('nav-solutions').click()
        settle()

        then:
        assertThat(tid(expectedForward)).isVisible()

        when:
        tid('nav-back').click()
        settle()

        then:
        assertThat(tid(expectedBack)).isVisible()

        when:
        tid('nav-forward').click()
        settle()

        then:
        assertThat(tid(expectedForward)).isVisible()
        assertNoProblems()

        where:
        scenario                      | deviceName || expectedBack | expectedForward
        'desktop, dashboard to list'  | 'desktop'  || 'home-page'  | 'solutions-page'
        'phone, dashboard to list'    | 'phone'    || 'home-page'  | 'solutions-page'
    }

    @Unroll
    def "mainMenu should go to #expectedPath when #scenario"() {

        given:
        signIn(device(deviceName))
        go(startPath)

        when:
        tid('nav-menu').click()
        settle()

        then:
        assertThat(tid('home-page')).isVisible()
        page.url().replaceAll('[?#].*$', '') == BASE + expectedPath
        assertNoProblems()

        where:
        scenario            | deviceName | startPath                 || expectedPath
        'desktop, Settings' | 'desktop'  | '/settings/settings.html' || '/'
        'phone, Settings'   | 'phone'    | '/settings/settings.html' || '/'
    }

    @Unroll
    def "quickLaunchSheet should open with Manage and close on its own button when #scenario"() {

        given:
        signIn(device(deviceName))
        go('/help/help.html')

        when:
        tid('nav-quicklaunch').click()

        then:
        assertThat(tid('nav-quicklaunch-sheet')).isVisible()
        tid('nav-quicklaunch-manage').isVisible() == expectedManageShown

        when:
        tid('nav-quicklaunch-close').click()

        then:
        assertThat(tid('nav-quicklaunch-sheet')).isHidden()
        assertNoProblems()

        where:
        scenario        | deviceName || expectedManageShown
        'desktop, Help' | 'desktop'  || true
        'phone, Help'   | 'phone'    || true
    }

    @Unroll
    def "menuSearch should find #query and close on its own button when #scenario"() {

        given:
        signIn(device(deviceName))
        go('/help/help.html')

        when:
        tid('nav-menu-search').click()
        tid('menu-search-input').fill(query)
        page.waitForTimeout(500)

        then:
        assertThat(tid('menu-search-sheet')).isVisible()
        (tid('menu-search-row').filter(new Locator.FilterOptions().setHasText(query)).count() >= 1) == expectedFound

        when:
        tid('menu-search-close').click()

        then:
        assertThat(tid('menu-search-sheet')).isHidden()
        assertNoProblems()

        where:
        scenario            | deviceName | query      || expectedFound
        'desktop, Settings' | 'desktop'  | 'Settings' || true
        'phone, Settings'   | 'phone'    | 'Settings' || true
    }

    private static Locator bandPrint(Page onPage) {
        onPage.locator('#lwIconBand [data-lw-band-print]')
    }

    private static boolean navClusterVisible(Page onPage) {
        assertThat(testId(onPage, 'icon-band')).isVisible()
        ['nav-back', 'nav-forward', 'nav-menu', 'nav-quicklaunch', 'nav-logout'].each { String id ->
            assertThat(testId(onPage, id)).isVisible()
        }
        return true
    }
}
