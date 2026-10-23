package com.echo.e2e

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import spock.lang.PendingFeature
import spock.lang.Unroll

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

/**
 * Story 5653: Quick Launch -- pin a page with the band's Pin button, see it on the dashboard and
 * in Manage Quick Launch, put pins in order with Arrange (grips, never arrows), add a custom pin,
 * edit it, and remove pins through the Cancel-default confirm that names the pin.
 * Every pin made here is labeled "zz-e2e <run> ..." and removed by the feature that made it.
 */
class QuickLaunchE2ESpec extends EchoE2EBaseSpec {

    @Unroll
    def "pinButton should put #pinnedPath on Quick Launch, the dashboard and Manage, and Manage should remove it when #scenario"() {

        given:
        signIn(device(deviceName))
        String label = name('band pin')
        go(pinnedPath)
        Locator pin = page.locator('#lwPinBtn')

        when:
        pin.click()

        then:
        assertThat(page.locator('#qlPinModal.show')).isVisible()
        assertThat(page.locator('#qlModalTitle')).hasText(expectedModalTitle)
        page.locator('#qlUrlReadonly').textContent().contains(pinnedPath)

        when:
        tid('pin-dest-quick-launch').check()
        page.locator('#qlLabel').fill(label)
        page.locator('#qlIcons .ql-icon-tile').first().click()
        page.locator('#qlSave').click()
        okMessage()

        then:
        assertThat(pin).hasAttribute('data-pinned', '1')

        when: 'the dashboard is opened'
        tid('nav-menu').click()
        settle()
        page.waitForTimeout(800)

        then:
        tid('quick-launch-tiles').textContent().contains(label)

        when: 'Manage Quick Launch is opened'
        go('/quick-launch/manage.html')

        then:
        assertThat(row(page, label)).isVisible()
        row(page, label).textContent().contains(pinnedPath)

        when:
        List<String> said = removeOnManage(label)

        then:
        said[0] == expectedConfirmTitle
        said[1].contains(label)
        assertNoProblems()

        where:
        scenario        | deviceName | pinnedPath        || expectedModalTitle | expectedConfirmTitle
        'desktop, Help' | 'desktop'  | '/help/help.html' || 'Pin this page'    | 'Remove this pin?'
        'phone, Help'   | 'phone'    | '/help/help.html' || 'Pin this page'    | 'Remove this pin?'
    }

    @Unroll
    def "arrange should show grips and no arrows, and #expectedOnLabel / Escape should end it when #scenario"() {

        given:
        signIn(device(deviceName))
        go('/quick-launch/manage.html')
        String label = name('arrange pin')
        tid('ql-add-custom-btn').click()
        fillPinModal(label, '/settings/settings.html')
        Locator arrange = tid('ql-arrange-btn')

        when:
        arrange.click()

        then:
        assertThat(arrange).hasText(expectedOnLabel)
        page.locator('#qlList.arranging').count() == 1
        row(page, label).getAttribute('draggable') == 'true'
        row(page, label).locator('.ql-grip').isVisible()
        page.locator('.ql-row button.arrow').count() == 0
        page.locator('.ql-row button').allTextContents().every { String text -> text.trim() in ['Edit', 'Delete'] }

        when: 'Done is pressed'
        arrange.click()

        then:
        arrange.textContent().contains('Arrange')
        page.locator('#qlList.arranging').count() == 0

        when: 'Arrange is pressed, then Escape'
        arrange.click()
        page.keyboard().press('Escape')

        then:
        arrange.textContent().contains('Arrange')
        row(page, label).getAttribute('draggable') == 'false'

        when:
        removeOnManage(label)

        then:
        assertNoProblems()

        where:
        scenario  | deviceName || expectedOnLabel
        'desktop' | 'desktop'  || 'Done'
        'phone'   | 'phone'    || 'Done'
    }

    @Unroll
    def "customPin should be added, edited and removed through #expectedAddTitle and #expectedEditTitle when #scenario"() {

        given:
        signIn(device(deviceName))
        go('/quick-launch/manage.html')
        String label = name('custom pin')

        when:
        tid('ql-add-custom-btn').click()
        assertThat(page.locator('#qlModalTitle')).hasText(expectedAddTitle)
        fillPinModal(label, '/help/help.html')

        then:
        assertThat(row(page, label)).isVisible()

        when:
        row(page, label).locator('button', new Locator.LocatorOptions().setHasText('Edit')).click()
        assertThat(page.locator('#qlModalTitle')).hasText(expectedEditTitle)
        String labelShown = page.locator('#qlLabel').inputValue()
        fillPinModal(label + ' edited', null)

        then:
        labelShown == label
        assertThat(row(page, label + ' edited')).isVisible()

        when:
        List<String> said = removeOnManage(label + ' edited')

        then:
        said[1].contains(label + ' edited')
        assertNoProblems()

        where:
        scenario  | deviceName || expectedAddTitle   | expectedEditTitle
        'desktop' | 'desktop'  || 'Add a custom pin' | 'Edit pin'
        'phone'   | 'phone'    || 'Add a custom pin' | 'Edit pin'
    }

    @PendingFeature(reason = 'BUG (minor): saving a pin opens a "Pinned." / "Pin updated." Picasso Dialog that waits for OK. The house rule: a routine save shows nothing (isRoutineSave in picasso-dialog.js does not know "Pinned").')
    def "customPin save should show no message to dismiss when the save is routine"() {

        given:
        signIn(DESKTOP)
        go('/quick-launch/manage.html')
        String label = name('quiet pin')
        tid('ql-add-custom-btn').click()

        when:
        String said = fillPinModal(label, '/help/help.html')

        then:
        said == null

        cleanup:
        if (page) removeOnManage(label)
    }

    def "arrange drag should change the order and keep it after a reload when two pins are swapped on the desktop"() {

        given:
        signIn(DESKTOP)
        go('/quick-launch/manage.html')
        String first = name('order A')
        String second = name('order B')
        tid('ql-add-custom-btn').click()
        fillPinModal(first, '/help/help.html')
        tid('ql-add-custom-btn').click()
        fillPinModal(second, '/settings/settings.html')
        List<String> before = orderOf(page, PREFIX + RUN + ' order')

        when:
        tid('ql-arrange-btn').click()
        row(page, second).dragTo(row(page, first))
        page.waitForTimeout(800)
        tid('ql-arrange-btn').click()
        page.reload()
        settle()
        page.waitForTimeout(800)

        then:
        before == [first, second]
        orderOf(page, PREFIX + RUN + ' order') == [second, first]

        when:
        removeOnManage(first)
        removeOnManage(second)

        then:
        assertNoProblems()
    }

    /** Manage Quick Launch's Delete, through its confirm; returns the confirm's title and subtitle. */
    List<String> removeOnManage(String label) {
        row(page, label).locator('button.danger').click()
        assertThat(page.locator('#qlConfirmModal.show')).isVisible()
        List<String> said = [page.locator('#qlConfirmTitle').textContent(), page.locator('#qlConfirmSub').textContent()]
        page.locator('#qlConfirmYes').click()
        okMessage(800)
        assertThat(row(page, label)).hasCount(0)
        return said
    }

    /** The Pin modal's custom / edit form: label, URL, first icon, Save. Returns the message Save showed, or null. */
    String fillPinModal(String label, String url) {
        assertThat(page.locator('#qlPinModal.show')).isVisible()
        page.locator('#qlLabel').fill(label)
        if (url != null) page.locator('#qlUrl').fill(url)
        page.locator('#qlIcons .ql-icon-tile').first().click()
        page.locator('#qlSave').click()
        assertThat(page.locator('#qlPinModal.show')).hasCount(0)
        return okMessage()
    }

    private static Locator row(Page onPage, String label) {
        testId(onPage, 'ql-row').filter(new Locator.FilterOptions().setHasText(label))
    }

    private static List<String> orderOf(Page onPage, String labelPrefix) {
        onPage.locator('.ql-row .ql-row-label').allTextContents().findAll { String text -> text.startsWith(labelPrefix) }
    }
}
