package com.echo.e2e

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import com.google.gson.Gson
import com.microsoft.playwright.APIRequestContext
import com.microsoft.playwright.APIResponse
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.Response
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.assertions.PlaywrightAssertions
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.RequestOptions
import com.microsoft.playwright.options.WaitForSelectorState

import com.echo.EchoSpockBaseSpec

import spock.lang.Shared

/*
 * =====================================================================================
 * Story 5653: Echo's browser end-to-end tests -- Playwright FOR JAVA, driven from Spock.
 * =====================================================================================
 *
 * WHAT THEY ARE
 *   Specs named *E2ESpec in this package. They drive a real Chromium against a RUNNING Echo
 *   (they never start one), at a desktop viewport (1280 x 900) and a phone viewport
 *   (412 x 860, touch), and fail on any page script error, console error, failed request or
 *   4xx / 5xx answer from Echo that the feature did not say it expects.
 *
 * OPT-IN ONLY
 *   The normal build (mvnw test, install.cmd, update-echo.cmd) neither compiles nor runs them:
 *   gmavenplus leaves out src/test/groovy/com/echo/e2e and surefire excludes **\/*E2ESpec.class.
 *   Only the Maven profile "e2e" adds the Playwright dependency, compiles this package and runs
 *   *E2ESpec -- and only those.
 *
 * HOW TO RUN (from the Echo folder, with Echo running)
 *   set ECHO_E2E_PASSWORD=SECRET
 *   mvnw -Pe2e test -Decho.e2e.user=NAME
 *   The password goes in the environment (or -Decho.e2e.passwordFile=C:\path\pw.txt), NOT in
 *   -Decho.e2e.password: surefire copies every -D into target\surefire-reports\TEST-*.xml.
 *   Optional:
 *     -Decho.e2e.url=http://localhost:8770   the Echo to test (this is the default)
 *     -Decho.e2e.headed=true                  watch the browser
 *     -Dtest=SolutionsE2ESpec                 one spec
 *   From Git Bash:
 *     ECHO_E2E_PASSWORD="$(cat pw.txt)" ./mvnw.cmd -q test -Pe2e -Decho.e2e.user=NAME
 *   Use a TEST sign-in, never a real person's: the specs create and delete solutions and Quick
 *   Launch pins, and fold and unfold menus. Make one with
 *   scripts\Set-EchoPassword.ps1 -Username zz-e2e-user and remove its AppUser row afterwards.
 *   CAUTION (Story 5653 finding): Echo keeps view state (folds) under user 1 whoever signs in,
 *   so until that is fixed these specs fold and unfold the FIRST user's menu, settings cards
 *   and solution search panels too. Quick Launch pins are shared by every user as well.
 *
 * FIRST RUN
 *   Playwright downloads its Chromium into the ms-playwright cache
 *   (%LOCALAPPDATA%\ms-playwright on Windows) -- a one-time download of a few hundred MB.
 *   Behind a proxy, set HTTPS_PROXY first. To use an installed Chrome instead of the download,
 *   PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 and -Decho.e2e.channel=chrome.
 *
 * WHAT THEY LEAVE BEHIND
 *   Nothing: every name a spec creates starts "zz-e2e <run>" and each feature deletes what it
 *   made; cleanupSpec() sweeps anything of this run's that a failure left. The test user's
 *   folds (UserViewState rows) remain until the test user is removed.
 *
 * HOW THEY ARE WRITTEN
 *   The spock-tests skill, as Echo applies it (ECHO-SPOCK.md): this base extends
 *   EchoSpockBaseSpec; every parameterized feature is @Unroll with a data table (scenario first,
 *   seq first when more than seven rows, || before the expected outputs, plain values only); a
 *   device column ("desktop" / "phone") is turned into a viewport in given: with device(...);
 *   feature names read "action should behavior when condition"; private helpers are static and
 *   take the Page they work on.
 *
 * ON FAILURE
 *   A full-page screenshot of what the browser showed is saved in target/e2e-shots
 *   (ShotsOnFailure). A feature marked @PendingFeature(reason = 'BUG: ...') is a known Echo
 *   bug: it passes while the bug is there and fails -- telling you to remove the marker --
 *   once the bug is fixed.
 */
@ShotsOnFailure
abstract class EchoE2EBaseSpec extends EchoSpockBaseSpec {

    static final String BASE = (System.getProperty('echo.e2e.url') ?: 'http://localhost:8770').replaceAll('/+$', '')
    static final String USER = System.getProperty('echo.e2e.user') ?: ''
    static final String PREFIX = 'zz-e2e '
    /** This JVM's run: every name a spec makes carries it, so the sweep touches only our own. */
    static final String RUN = Long.toString(System.currentTimeMillis() % 100000000L, 36)
    static final Path SHOTS = Paths.get('target', 'e2e-shots')
    static final Gson GSON = new Gson()

    /** The two devices every feature runs on; a data table names one in its "device" column. */
    static final Map<String, Object> DESKTOP = [name: 'desktop', width: 1280, height: 900, phone: false]
    static final Map<String, Object> PHONE = [name: 'phone', width: 412, height: 860, phone: true]

    /** "desktop" or "phone" (a data-table value) to its viewport. */
    static Map<String, Object> device(String deviceName) {
        Map<String, Object> found = [DESKTOP, PHONE].find { Map<String, Object> d -> d.name == deviceName }
        if (found == null) throw new IllegalArgumentException('Unknown device: ' + deviceName)
        return found
    }

    @Shared Playwright playwright
    @Shared Browser browser

    BrowserContext context
    Page page
    String viewportName = 'none'
    /** Everything that went wrong in the browser during this feature. */
    List<String> problems = Collections.synchronizedList([])
    /** [status, url regex] pairs this feature expects (a wrong password, say). */
    List<List> allowed = [[401, '/api/auth/refresh$']]

    // ---- the browser -------------------------------------------------------------------

    def setupSpec() {
        if (!USER || !password())
            throw new IllegalStateException('Set -Decho.e2e.user and the ECHO_E2E_PASSWORD environment variable (or -Decho.e2e.passwordFile) for a TEST sign-in.')
        playwright = Playwright.create()
        assertEchoIsUp(playwright)
        BrowserType.LaunchOptions o = new BrowserType.LaunchOptions().setHeadless(!Boolean.getBoolean('echo.e2e.headed'))
        if (System.getProperty('echo.e2e.channel')) o.setChannel(System.getProperty('echo.e2e.channel'))
        browser = playwright.chromium().launch(o)
        PlaywrightAssertions.setDefaultAssertionTimeout(10000)
    }

    def cleanupSpec() {
        try { sweep() } finally {
            browser?.close()
            playwright?.close()
        }
    }

    def cleanup() {
        context?.close()
        context = null
        page = null
    }

    /**
     * The password: the environment variable ECHO_E2E_PASSWORD (preferred), else the file
     * -Decho.e2e.passwordFile names, else -Decho.e2e.password. Never printed.
     */
    static String password() {
        String fromEnv = System.getenv('ECHO_E2E_PASSWORD')
        if (fromEnv) return fromEnv
        String p = System.getProperty('echo.e2e.password')
        String f = System.getProperty('echo.e2e.passwordFile')
        if (!f) return p ?: ''
        return new String(Files.readAllBytes(Paths.get(f)), 'UTF-8').trim()
    }

    private static void assertEchoIsUp(Playwright pw) {
        APIRequestContext rq = pw.request().newContext()
        try {
            APIResponse r = rq.get(BASE + '/api/system/health')
            if (r.status() != 200) throw new IllegalStateException('Echo at ' + BASE + ' answered ' + r.status() + ' to /api/system/health')
        } catch (PlaywrightException e) {
            throw new IllegalStateException('Echo is not running at ' + BASE + ' (start it with start-echo.cmd): ' + e.message)
        } finally {
            rq.dispose()
        }
    }

    /** A fresh browser (cookies and all) at one viewport, watching for anything that goes wrong. */
    Page open(Map<String, Object> vp) {
        viewportName = vp.name
        Browser.NewContextOptions o = new Browser.NewContextOptions()
            .setViewportSize(vp.width as int, vp.height as int)
            .setAcceptDownloads(true)
        if (vp.phone) o.setHasTouch(true).setIsMobile(true).setDeviceScaleFactor(2)
        context = browser.newContext(o)
        context.setDefaultTimeout(15000)
        context.onWebError { e -> problems << ('page script error: ' + e.error().message()) }
        context.onConsoleMessage { m ->
            // "Failed to load resource" repeats a bad answer the response watcher judges itself.
            if (m.type() == 'error' && !m.text().startsWith('Failed to load resource'))
                problems << ('console error on ' + m.page()?.url() + ': ' + m.text())
        }
        context.onResponse { Response r -> judge(r) }
        context.onRequestFailed { req ->
            String why = req.failure() ?: ''
            if (req.url().startsWith(BASE) && !why.contains('ERR_ABORTED'))
                problems << ('request failed: ' + req.method() + ' ' + req.url() + ' ' + why)
        }
        page = context.newPage()
        return page
    }

    /** Records an answer from Echo of 400 or more that this feature did not allow. */
    void judge(Response r) {
        if (r.status() < 400 || !r.url().startsWith(BASE)) return
        String path = r.url().substring(BASE.length())
        if (allowed.any { a -> a[0] == r.status() && path =~ a[1] }) return
        problems << ('HTTP ' + r.status() + ' ' + r.request().method() + ' ' + path)
    }

    /** This feature expects Echo to answer {@code status} to a path matching {@code urlRegex}. */
    void allow(int status, String urlRegex) { allowed << [status, urlRegex] }

    /** The feature's last word: no script error, console error, failed request or unexpected 4xx / 5xx. */
    void assertNoProblems() {
        page?.waitForTimeout(300)
        List<String> seen = new ArrayList<>(problems)
        assert seen.isEmpty(), 'Browser problems at ' + viewportName + ':\n  ' + seen.join('\n  ')
    }

    /** Called by ShotsOnFailure. */
    void shoot(String name) {
        if (page == null || page.isClosed()) return
        try {
            Files.createDirectories(SHOTS)
            String file = (getClass().simpleName + '-' + name + '-' + viewportName).replaceAll('[^A-Za-z0-9._-]+', '_')
            if (file.length() > 150) file = file.substring(0, 150)
            page.screenshot(new Page.ScreenshotOptions().setPath(SHOTS.resolve(file + '.png')).setFullPage(true))
        } catch (Exception ignored) { /* a screenshot must never hide the real failure */ }
    }

    // ---- moving around --------------------------------------------------------------------

    Locator tid(String id) { testId(page, id) }

    /** The element(s) with this data-testid on a page. */
    static Locator testId(Page onPage, String id) { onPage.locator('[data-testid="' + id + '"]') }

    void go(String path) {
        page.navigate(BASE + path)
        settle()
    }

    void settle() {
        try { page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000)) }
        catch (TimeoutError ignored) { /* a page that keeps polling is still usable */ }
    }

    /** Signs in on the sign-in card and waits for the dashboard to finish drawing. */
    void signIn(Map<String, Object> vp) {
        if (page == null) open(vp)
        go('/')
        tid('login-username').waitFor()
        tid('login-username').fill(USER)
        tid('login-password').fill(password())
        tid('login-submit-btn').click()
        PlaywrightAssertions.assertThat(tid('home-page')).isVisible()
        PlaywrightAssertions.assertThat(tid('home-status')).containsText(' | ')
        settle()
    }

    /**
     * Picasso Dialog messages wait for OK (Story 5606) -- "Pinned.", "Logged as #4". Reads and
     * closes one if it shows within a moment; returns its text, or null when none came.
     */
    String okMessage(int waitMs = 2500) {
        Locator dlg = tid('picasso-dialog')
        try { dlg.waitFor(new Locator.WaitForOptions().setTimeout(waitMs)) }
        catch (TimeoutError ignored) { return null }
        String text = tid('picasso-dialog-message').textContent()
        tid('picasso-dialog-btn-ok').click()
        dlg.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED))
        return text
    }

    /** A name for something this run creates: "zz-e2e <run> <what> <viewport>". */
    String name(String what) { PREFIX + RUN + ' ' + what + ' ' + viewportName }

    // ---- Echo's own API, as the signed-in page --------------------------------------------

    /** Calls Echo through auth.js on the current page (the page must be signed in). */
    Map api(String method, String path, Map body = null) {
        Object r = page.evaluate('''async ([m, p, b]) => {
            const r = m === 'GET' ? await window.LWAuth.apiGet(p) : await window.LWAuth.apiPost(p, b, m);
            return { ok: !!(r && r.ok), status: r ? r.status : 0, data: r ? r.data : null };
        }''', [method, path, body])
        return (Map) r
    }

    /** A solution with a project, an item, a log entry and a note, made through the API. Returns its id. */
    int seedSolution(String what) {
        Map s = api('POST', '/api/solutions', [name: name(what), description: 'A **zz-e2e** solution.', statusCode: 'inprogress'])
        assert s.ok, 'seed solution: ' + s
        int sid = ((s.data.id ?: s.data.solution?.id) as int)
        Map pj = api('POST', "/api/solutions/${sid}/projects", [name: name('project'), statusCode: 'todo'])
        assert pj.ok, 'seed project: ' + pj
        int pid = ((pj.data.id ?: pj.data.project?.id) as int)
        assert api('POST', "/api/solutions/projects/${pid}/items", [title: name('seeded item'), statusCode: 'todo']).ok
        assert api('POST', "/api/solutions/${sid}/log", [typeName: 'Note', text: name('seeded log entry')]).ok
        assert api('POST', "/api/solutions/${sid}/notes", [title: name('seeded note'), typeName: 'Reference', body: 'Seeded.']).ok
        return sid
    }

    // ---- checks shared by several specs ---------------------------------------------------

    /** Every select on the page is a PicassoSelect (enhanced, hidden, with its trigger) -- never a bare one. */
    List<String> bareSelects() {
        page.waitForTimeout(400)
        return (List<String>) page.evaluate('''() => Array.from(document.querySelectorAll('select'))
            .filter(s => s.dataset.psEnhanced !== '1' || getComputedStyle(s).display !== 'none')
            .map(s => s.id || s.getAttribute('data-testid') || s.outerHTML.slice(0, 60))''')
    }

    /** Pixels the page scrolls sideways beyond the viewport (0 = none). */
    int sidewaysOverflow() {
        return (page.evaluate('() => document.documentElement.scrollWidth - window.innerWidth') as Number).intValue()
    }

    // ---- the sweep ---------------------------------------------------------------------------

    /** Deletes anything of THIS run's (name starts "zz-e2e <run>") that a failed feature left behind. */
    void sweep() {
        if (playwright == null) return
        APIRequestContext rq = playwright.request().newContext()
        try {
            APIResponse login = rq.post(BASE + '/api/auth/login', RequestOptions.create()
                .setHeader('Content-Type', 'application/json')
                .setData(GSON.toJson([username: USER, password: password()])))
            if (!login.ok()) return
            String token = GSON.fromJson(login.text(), Map).accessToken
            RequestOptions auth = RequestOptions.create().setHeader('Authorization', 'Bearer ' + token)
            String mine = PREFIX + RUN
            List<Map> sols = GSON.fromJson(rq.get(BASE + '/api/solutions?includeArchived=true', auth).text(), List)
            sols.findAll { it.name?.startsWith(mine) }.each { rq.delete(BASE + '/api/solutions/' + ((it.id as Number).longValue()), auth) }
            List<Map> pins = GSON.fromJson(rq.get(BASE + '/api/quick-launch', auth).text(), List)
            pins.findAll { it.label?.startsWith(mine) }.each { rq.delete(BASE + '/api/quick-launch/' + ((it.quickLaunchId as Number).longValue()), auth) }
        } catch (Exception ignored) {
            /* the final SELECT COUNT(*) check is the backstop */
        } finally {
            rq.dispose()
        }
    }
}
