package com.echo

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

import spock.lang.TempDir
import spock.lang.Unroll

/** Story 5653: Settings -> Update Echo -- starting the script and reading back how it went. */
class EchoUpdaterSpec extends EchoSpockBaseSpec {

    @TempDir Path home

    /** An updater that records the command instead of starting PowerShell. */
    static class RecordingUpdater extends EchoUpdater {
        List<List<String>> launched = []
        RecordingUpdater(String home) { super(home) }
        @Override void launch(List<String> command) { launched << command }
    }

    private RecordingUpdater updater() { new RecordingUpdater(home.toString()) }

    private void withScript() {
        Files.createDirectories(home.resolve('scripts'))
        Files.writeString(home.resolve('scripts/Update-Echo.ps1'), '# test')
    }

    private void writeState(String json) {
        Files.createDirectories(home.resolve('logs'))
        Files.writeString(home.resolve('logs/update-state.json'), json)
    }

    def "start should refuse when the update script is not in the Echo folder"() {

        given:
        RecordingUpdater u = updater()

        when:
        u.start()

        then:
        EchoUpdater.UpdateRefusedException ex = thrown()
        ex.message.contains('update-echo.cmd')
        u.launched.isEmpty()
    }

    def "start should write a starting state and launch the script in PowerShell"() {

        given:
        withScript()
        RecordingUpdater u = updater()

        when:
        Map<String, Object> result = u.start()

        then:
        u.launched.size() == 1
        u.launched[0][0] == 'powershell.exe'
        u.launched[0].last() == home.resolve('scripts/Update-Echo.ps1').toString()
        result.state == 'starting'
        result.running == true
    }

    def "start should refuse a second update while the first is starting"() {

        given:
        withScript()
        RecordingUpdater u = updater()
        u.start()

        when:
        u.start()

        then:
        EchoUpdater.UpdateRefusedException ex = thrown()
        ex.message.contains('already running')
        u.launched.size() == 1
    }

    def "start should refuse while the script process is still alive"() {

        given:
        withScript()
        writeState("""{"state":"running","started":"${LocalDateTime.now().withNano(0)}","pid":"${ProcessHandle.current().pid()}"}""")

        when:
        updater().start()

        then:
        thrown(EchoUpdater.UpdateRefusedException)
    }

    def "status should answer none before any update"() {

        expect:
        updater().status().state == 'none'
        updater().status().running == false
    }

    def "status should report failed when the script died while running"() {

        given:
        writeState('{"state":"running","pid":"999999999","started":"2026-10-26T10:00:00"}')

        when:
        Map<String, Object> s = updater().status()

        then:
        s.state == 'failed'
        s.running == false
    }

    def "status should report failed when the script never got past starting"() {

        given:
        writeState("""{"state":"starting","started":"${LocalDateTime.now().minusMinutes(5).withNano(0)}"}""")

        expect:
        updater().status().state == 'failed'
    }

    def "status should answer the message and the last log lines of a finished update"() {

        given:
        writeState('\uFEFF{"state":"done","message":"Echo is updated to release build-1.","log":"update-20261026-150000.log","release":"build-1"}')
        List<String> lines = (1..80).collect { "line $it" }
        Files.write(home.resolve('logs/update-20261026-150000.log'), ('\uFEFF' + lines.join('\r\n')).getBytes(StandardCharsets.UTF_8))

        when:
        Map<String, Object> s = updater().status()

        then:
        s.state == 'done'
        s.message == 'Echo is updated to release build-1.'
        s.release == 'build-1'
        s.logTail.size() == EchoUpdater.LOG_TAIL_LINES
        s.logTail.last() == 'line 80'
    }

    @Unroll
    def "status should not read a log named #name"() {

        given:
        writeState("""{"state":"done","log":"${name.replace('\\', '\\\\')}"}""")

        expect:
        updater().status().logTail == []

        where:
        name << ['..\\config\\echo-install.json', 'echo.log', 'update-x.log']
    }

    def "current release should answer the installed release and empty when built here"() {

        expect:
        updater().currentRelease() == [:]

        when:
        Files.createDirectories(home.resolve('config'))
        Files.writeString(home.resolve('config/echo-release.json'), '{"tag":"build-2","installed":"2026-10-26 15:00"}')

        then:
        updater().currentRelease().tag == 'build-2'
    }

    def "decode should read UTF-16 as PowerShell may write it"() {

        given:
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        bytes.write(0xFF)
        bytes.write(0xFE)
        bytes.write('hi'.getBytes(StandardCharsets.UTF_16LE))

        expect:
        EchoUpdater.decode(bytes.toByteArray()) == 'hi'
    }
}
