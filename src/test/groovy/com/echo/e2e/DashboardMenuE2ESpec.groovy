package com.echo.e2e

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import spock.lang.Unroll

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

/**
 * Story 5653: every link on the dashboard's menu opens its page with no script error, no console
 * error and no failed or 4xx / 5xx request. Admin -> API Clients stops at its re-type-password
 * step first; AdminApiClientsE2ESpec goes past it.
 */
class DashboardMenuE2ESpec extends EchoE2EBaseSpec {

    @Unroll
    def "#seq. menuLink should open #expectedPage cleanly when #scenario"() {

        given:
        signIn(device(deviceName))
        boolean phone = (boolean) device(deviceName).phone
        Locator link = tid(linkId)
        reveal(page, link)

        when:
        link.click()
        settle()

        then:
        assertThat(tid(expectedPage)).isVisible()
        !phone || sidewaysOverflow() <= 0
        assertNoProblems()

        where:
        seq | scenario                         | deviceName | linkId                    || expectedPage
        1   | 'desktop, Solutions'             | 'desktop'  | 'nav-solutions'           || 'solutions-page'
        2   | 'phone, Solutions'               | 'phone'    | 'nav-solutions'           || 'solutions-page'
        3   | 'desktop, Manage Quick Launch'   | 'desktop'  | 'nav-quick-launch-manage' || 'quick-launch-manage-page'
        4   | 'phone, Manage Quick Launch'     | 'phone'    | 'nav-quick-launch-manage' || 'quick-launch-manage-page'
        5   | 'desktop, API Clients (step-up)' | 'desktop'  | 'nav-api-clients'         || 'stepup-overlay'
        6   | 'phone, API Clients (step-up)'   | 'phone'    | 'nav-api-clients'         || 'stepup-overlay'
        7   | 'desktop, Settings'              | 'desktop'  | 'nav-settings'            || 'settings-page'
        8   | 'phone, Settings'                | 'phone'    | 'nav-settings'            || 'settings-page'
        9   | 'desktop, Help'                  | 'desktop'  | 'nav-help'                || 'help-page'
        10  | 'phone, Help'                    | 'phone'    | 'nav-help'                || 'help-page'
    }

    def "menuLinks should each answer 200 when the dashboard is signed in"() {

        given:
        signIn(DESKTOP)

        when:
        List<String> hrefs = (List<String>) page.evaluate("() => Array.from(document.querySelectorAll('.report-links a')).map(a => a.getAttribute('href'))")
        Map<String, Integer> statuses = hrefs.collectEntries { String href -> [(href): page.request().get(BASE + href).status()] }

        then:
        hrefs.size() == 5
        statuses.every { String href, Integer status -> status == 200 }
        assertNoProblems()
    }

    @Unroll
    def "dashboard should show Quick Launch with tiles or the empty hint when #scenario"() {

        when:
        signIn(device(deviceName))

        then:
        tid('quick-launch-section').isVisible() == expectedSectionShown
        tid('quick-launch-tiles').locator('a, .ql-tile').count() > 0 || tid('quick-launch-empty').isVisible() ||
            page.locator('#qlDashSection.collapsed').count() == 1
        assertNoProblems()

        where:
        scenario  | deviceName || expectedSectionShown
        'desktop' | 'desktop'  || true
        'phone'   | 'phone'    || true
    }

    private static void reveal(Page onPage, Locator link) {
        if (link.isVisible()) return
        onPage.locator('.report-links').filter(new Locator.FilterOptions().setHas(link)).locator('h2').click()
        onPage.waitForTimeout(400)
    }
}
