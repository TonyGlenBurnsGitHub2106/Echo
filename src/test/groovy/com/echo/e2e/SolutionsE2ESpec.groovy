package com.echo.e2e

import java.util.function.Predicate

import com.microsoft.playwright.Dialog
import com.microsoft.playwright.Download
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Response

import spock.lang.PendingFeature
import spock.lang.Unroll

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

/**
 * Story 5653: Solutions, Echo's first vertical, the way a person uses it -- the list, a new
 * solution, a project, an item, a note and a log entry added through the page, each edited and
 * viewed, the solution's search finding them, and the delete that names what it deletes. Then
 * every tab of a seeded solution, the Print menu, and the PicassoSelect dropdowns.
 * Everything made here is named "zz-e2e <run> ..." and deleted by the feature that made it.
 */
class SolutionsE2ESpec extends EchoE2EBaseSpec {

    static final List<String> TABS = ['overview', 'items', 'log', 'notes', 'terms', 'tests', 'qa', 'config', 'grid', 'files', 'conversations', 'search']

    @Unroll
    def "solution should be created, filled, edited, viewed, found and deleted with a confirm naming #expectedCounts when #scenario"() {

        given:
        signIn(device(deviceName))
        String solName = name('solution')
        String projName = name('project')
        String itemTitle = name('item')
        String noteTitle = name('note')
        String logText = name('log entry') + ' was written by the browser test'
        int solutionId = 0

        // ---- the list, and a new solution ----
        when:
        tid('nav-solutions').click()
        settle()
        tid('solutions-add-btn').click()

        then:
        assertThat(tid('solutions-edit-overlay')).isVisible()
        bareSelects().isEmpty()
        assertThat(tid('solutions-status-select-trigger')).isVisible()

        when:
        tid('solutions-name').fill(solName)
        tid('solutions-description').fill('Made by **SolutionsE2ESpec**.')
        tid('solutions-save').click()
        page.waitForURL('**/solutions/solution.html?id=*')
        settle()
        solutionId = Integer.parseInt(page.url().replaceAll('^.*[?&]id=(\\d+).*$', '$1'))

        then:
        solutionId > 0
        assertThat(tid('page-title').first()).hasText(solName)

        // ---- a project ----
        when:
        tid('solution-add-project').click()
        assertThat(tid('project-overlay')).isVisible()
        tid('project-name').fill(projName)
        tid('project-save').click()
        settle()

        then:
        assertThat(withText(page, 'project-card', projName)).isVisible()

        // ---- an item, viewed, then edited ----
        when:
        tab(page, 'items')
        tid('items-add-btn').click()

        then:
        assertThat(tid('item-overlay')).isVisible()
        bareSelects().isEmpty()

        when:
        tid('item-title').fill(itemTitle)
        tid('item-detail').fill('Detail written by the browser test.')
        tid('item-save').click()
        settle()

        then:
        assertThat(withText(page, 'items-row', itemTitle)).isVisible()

        when:
        withText(page, 'items-row', itemTitle).locator('[data-testid="items-view-btn"]').click()

        then:
        assertThat(tid('item-view-overlay')).isVisible()
        assertThat(tid('item-view-title')).hasText(itemTitle)
        tid('item-view-body').textContent().contains('Detail written by the browser test.')

        when:
        tid('item-view-close').click()
        withText(page, 'items-row', itemTitle).locator('[data-testid="items-edit-btn"]').click()
        assertThat(tid('item-overlay')).isVisible()
        tid('item-title').fill(itemTitle + ' edited')
        tid('item-save').click()
        settle()

        then:
        assertThat(withText(page, 'items-row', itemTitle + ' edited')).isVisible()

        // ---- a note, viewed, then edited ----
        when:
        tab(page, 'notes')
        tid('notes-add-btn').click()
        assertThat(tid('note-overlay')).isVisible()
        tid('note-title').fill(noteTitle)
        tid('note-body').fill('A note **written** by the browser test.')
        tid('note-save').click()
        settle()

        then:
        assertThat(tid('notes-open-header')).containsText(noteTitle)

        when:
        tid('notes-view-btn').click()

        then:
        assertThat(tid('note-view-overlay')).isVisible()
        assertThat(tid('note-view-title')).hasText(noteTitle)
        tid('note-view-body').textContent().contains('written by the browser test')

        when:
        tid('note-view-close').click()
        tid('notes-edit-btn').click()
        assertThat(tid('note-overlay')).isVisible()
        tid('note-title').fill(noteTitle + ' edited')
        tid('note-save').click()
        settle()

        then:
        assertThat(tid('notes-open-header')).containsText(noteTitle + ' edited')

        // ---- a log entry, then corrected ----
        when:
        tab(page, 'log')
        tid('log-new-type').fill('Note')
        tid('log-new-type').press('Tab')
        tid('log-new-text').fill(logText)
        tid('log-new-save').click()
        String logged = okMessage()
        settle()

        then:
        logged =~ /^Logged as #\d+$/
        assertThat(withText(page, 'log-entry', logText)).isVisible()

        when:
        withText(page, 'log-entry', logText).locator('[data-testid="log-correct-btn"]').click()
        assertThat(tid('log-overlay')).isVisible()
        tid('log-edit-text').fill(logText + ', then corrected')
        tid('log-edit-save').click()
        settle()

        then:
        assertThat(withText(page, 'log-entry', logText + ', then corrected')).isVisible()

        // ---- the solution itself, edited ----
        when:
        tab(page, 'overview')
        tid('solution-edit-btn').click()
        assertThat(tid('solution-edit-overlay')).isVisible()

        then:
        bareSelects().isEmpty()

        when:
        tid('solution-summary').fill('Where it stands: written by the browser test.')
        tid('solution-save').click()
        settle()
        if (page.locator('[data-testid="solution-hero"].folded').count() > 0) tid('solution-hero-toggle').click()

        then:
        assertThat(tid('solution-summary-text')).containsText('written by the browser test')

        // ---- the solution's search finds what was made ----
        when:
        tab(page, 'search')
        if (page.locator('#searchForm.folded').count() > 0) tid('search-form-toggle').click()
        tid('search-sAnd-1').fill(RUN)
        tid('search-go').click()
        assertThat(tid('search-band').first()).isVisible()
        openFolded(page, '.sl-band.collapsed [data-testid="search-band-toggle"]')

        then:
        assertThat(tid('search-hit').first()).isVisible()
        tid('search-results').textContent().contains(itemTitle + ' edited')
        tid('search-results').textContent().contains(noteTitle + ' edited')

        // ---- delete: the question names the solution and what goes with it ----
        when:
        tab(page, 'overview')
        List<String> asked = answerNextConfirm(page, true)
        tid('solution-delete-btn').click()
        page.waitForURL('**/solutions/solutions.html')
        settle()

        then:
        asked.size() == 1
        asked[0].startsWith('Delete the solution "' + solName + '"?')
        asked[0].contains(expectedCounts)
        asked[0].contains('This cannot be undone.')
        !tid('solutions-list').textContent().contains(solName)
        assertNoProblems()

        cleanup:
        // Already gone when the feature passed (a 404 here comes after the last check); sweep() is the backstop.
        if (page && solutionId) {
            try { api('DELETE', '/api/solutions/' + solutionId) } catch (Exception ignored) { }
        }

        where:
        scenario  | deviceName || expectedCounts
        'desktop' | 'desktop'  || '1 projects, 1 items, 1 log entries and 1 notes'
        'phone'   | 'phone'    || '1 projects, 1 items, 1 log entries and 1 notes'
    }

    @Unroll
    def "delete should ask first naming #expectedItemQuestion, keep the item on Cancel and remove it on OK when #scenario"() {

        given:
        signIn(device(deviceName))
        int solutionId = seedSolution('deletes')
        String itemTitle = name('seeded item')
        String logText = name('seeded log entry')
        go('/solutions/solution.html?id=' + solutionId)
        tab(page, 'items')
        openFolded(page, '.sl-band.collapsed [data-testid="items-band-toggle"]')

        when: 'Cancel is chosen for the item'
        List<String> askedCancel = answerNextConfirm(page, false)
        withText(page, 'items-row', itemTitle).locator('[data-testid="items-delete-btn"]').click()
        page.waitForTimeout(500)

        then:
        askedCancel.size() == 1
        askedCancel[0].startsWith(expectedItemQuestion + ' "' + itemTitle + '"')
        askedCancel[0].contains('This cannot be undone.')
        withText(page, 'items-row', itemTitle).count() == 1

        when: 'OK is chosen for the item'
        List<String> askedOk = answerNextConfirm(page, true)
        withText(page, 'items-row', itemTitle).locator('[data-testid="items-delete-btn"]').click()
        settle()

        then:
        askedOk.size() == 1
        assertThat(withText(page, 'items-row', itemTitle)).hasCount(0)

        when: 'OK is chosen for the log entry'
        tab(page, 'log')
        List<String> askedLog = answerNextConfirm(page, true)
        withText(page, 'log-entry', logText).locator('[data-testid="log-delete-btn"]').click()
        settle()

        then:
        askedLog.size() == 1
        askedLog[0].startsWith(expectedLogQuestion)
        askedLog[0].contains(logText)
        assertThat(withText(page, 'log-entry', logText)).hasCount(0)
        assertNoProblems()

        cleanup:
        if (page && solutionId) api('DELETE', '/api/solutions/' + solutionId)

        where:
        scenario  | deviceName || expectedItemQuestion | expectedLogQuestion
        'desktop' | 'desktop'  || 'Delete the item'    | 'Delete log entry #'
        'phone'   | 'phone'    || 'Delete the item'    | 'Delete log entry #'
    }

    @Unroll
    def "solutionTabs should all open with #expectedWebTabCount Web tabs and no sideways scroll when #scenario"() {

        given:
        signIn(device(deviceName))
        boolean phone = (boolean) device(deviceName).phone
        int solutionId = seedSolution('tabs')
        go('/solutions/solution.html?id=' + solutionId)
        List<String> notOpening = []
        List<String> tooWide = []

        when:
        TABS.each { String tabName ->
            tid('solution-tab-' + tabName).click()
            page.waitForTimeout(500)
            if (!tid('solution-pane-' + tabName).isVisible()) notOpening << tabName
            if (phone && sidewaysOverflow() > 0) tooWide << (tabName + ' (' + sidewaysOverflow() + 'px)')
        }

        then:
        notOpening.isEmpty()
        tooWide.isEmpty()
        tid('solution-tab-web').count() == expectedWebTabCount
        bareSelects().isEmpty()
        assertNoProblems()

        cleanup:
        if (page && solutionId) api('DELETE', '/api/solutions/' + solutionId)

        where:
        scenario  | deviceName || expectedWebTabCount
        'desktop' | 'desktop'  || 0
        'phone'   | 'phone'    || 0
    }

    @Unroll
    def "picassoSelect should open #expectedMinimumOptions or more options and set the hidden select when #scenario"() {

        given:
        signIn(device(deviceName))
        go('/solutions/solutions.html')
        tid('solutions-add-btn').click()
        String before = tid('solutions-status-select').inputValue()

        when:
        tid('solutions-status-select-trigger').click()

        then:
        assertThat(tid('solutions-status-select-popup')).isVisible()
        tid('solutions-status-select-popup').locator('.ps-option').count() >= expectedMinimumOptions

        when:
        tid('solutions-status-select-popup').locator('.ps-option:not(.is-selected)').first().click()
        page.waitForTimeout(300)

        then:
        tid('solutions-status-select').inputValue() != before
        tid('solutions-status-select-popup').isHidden()

        when: 'Cancel leaves without saving anything'
        tid('solutions-cancel').click()

        then:
        assertThat(tid('solutions-edit-overlay')).isHidden()
        assertNoProblems()

        where:
        scenario                   | deviceName || expectedMinimumOptions
        'desktop, solution status' | 'desktop'  || 2
        'phone, solution status'   | 'phone'    || 2
    }

    @Unroll
    def "printMenu should offer #expectedDestinations, download a PDF and display one when #scenario"() {

        given:
        signIn(device(deviceName))
        int solutionId = seedSolution('print')
        go('/solutions/solution.html?id=' + solutionId)
        Locator printButton = page.locator('#lwIconBand [data-lw-band-print]')
        Predicate<Response> isPdf = { Response r -> (r.headerValue('content-type') ?: '').contains('pdf') } as Predicate<Response>

        when:
        printButton.click()
        assertThat(page.locator('button[data-dest="download"]')).isVisible()

        then:
        page.locator('button[data-dest]:visible').evaluateAll('bs => bs.map(b => b.getAttribute("data-dest")).join(",")') == expectedDestinations

        when: 'Cancel is chosen'
        page.locator('button[data-dest=""]:visible').click()

        then:
        assertThat(page.locator('button[data-dest="print"]')).isHidden()

        when: 'Download is chosen'
        printButton.click()
        Download download = page.waitForDownload { page.locator('button[data-dest="download"]:visible').click() }
        okMessage(1500)

        then:
        download.suggestedFilename().toLowerCase().endsWith(expectedExtension)
        download.failure() == null

        when: 'Display is chosen (headless Chromium has no PDF viewer, so the PDF answer and the new window are what is checked)'
        printButton.click()
        List<Page> viewers = []
        Response pdf = page.waitForResponse(isPdf) {
            viewers << context.waitForPage { page.locator('button[data-dest="display"]:visible').click() }
        }

        then:
        viewers.size() == 1
        pdf.status() == 200
        assertNoProblems()

        cleanup:
        if (page && solutionId) api('DELETE', '/api/solutions/' + solutionId)

        where:
        scenario  | deviceName || expectedDestinations      | expectedExtension
        'desktop' | 'desktop'  || 'print,display,download,' | '.pdf'
        'phone'   | 'phone'    || 'print,display,download,' | '.pdf'
    }

    @PendingFeature(reason = 'BUG: Items -> Arrange shows Move up / Move down arrow buttons (items-up-btn / items-down-btn, class sl-arrange-only). The house rule is Arrange with grips only, never arrows.')
    @Unroll
    def "itemsArrange should show grips and #expectedArrowCount arrow buttons when #scenario"() {

        given:
        signIn(device(deviceName))
        int solutionId = seedSolution('arrange')
        go('/solutions/solution.html?id=' + solutionId)
        tab(page, 'items')
        openFolded(page, '.sl-band.collapsed [data-testid="items-band-toggle"]')

        when:
        tid('items-arrange-btn').click()
        page.waitForTimeout(400)

        then:
        tid('items-grip').first().isVisible()
        page.locator('[data-testid="items-up-btn"]:visible, [data-testid="items-down-btn"]:visible').count() == expectedArrowCount

        cleanup:
        if (page && solutionId) api('DELETE', '/api/solutions/' + solutionId)

        where:
        scenario  | deviceName || expectedArrowCount
        'desktop' | 'desktop'  || 0
        'phone'   | 'phone'    || 0
    }

    private static Locator withText(Page onPage, String id, String text) {
        testId(onPage, id).filter(new Locator.FilterOptions().setHasText(text))
    }

    private static void tab(Page onPage, String tabName) {
        testId(onPage, 'solution-tab-' + tabName).click()
        assertThat(testId(onPage, 'solution-pane-' + tabName)).isVisible()
        onPage.waitForTimeout(400)
    }

    /** Clicks every folded toggle the selector finds until none is left. */
    private static void openFolded(Page onPage, String selector) {
        Locator shut = onPage.locator(selector)
        while (shut.count() > 0) {
            shut.first().click()
            onPage.waitForTimeout(300)
        }
    }

    /** Answers the next window.confirm (OK or Cancel) and hands back what it asked. */
    private static List<String> answerNextConfirm(Page onPage, boolean ok) {
        List<String> asked = []
        onPage.onceDialog { Dialog d ->
            asked << d.message()
            if (ok) d.accept() else d.dismiss()
        }
        return asked
    }
}
