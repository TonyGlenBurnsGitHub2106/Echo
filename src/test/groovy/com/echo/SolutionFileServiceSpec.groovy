package com.echo

import java.nio.file.Files
import java.nio.file.Path

import com.echo.SolutionModels.FileRow

import spock.lang.TempDir
import spock.lang.Unroll

/**
 * Story 5653: files attached to a solution, on disk. Everything happens inside a @TempDir: the files folder
 * is (tmp)/EchoFiles, and a few "outside" files sit beside it to prove nothing escapes. What an agent sends
 * is hostile -- names are rebuilt, every path is proven to stay inside, and a delete is a MOVE to trash.
 * Openings of Explorer are never exercised on the success path: that would start explorer.exe on this machine.
 */
class SolutionFileServiceSpec extends SolutionServiceSupport {

    @TempDir Path tmp

    SolutionFileRepository files = Mock()
    SolutionFileService service
    Path root
    Map<Integer, FileRow> rows = [:]

    def setup() {
        root = tmp.resolve('EchoFiles')
        service = new SolutionFileService(files, access, root.toString())
        service.ensureDirs()
        files.find(_) >> { int id -> rows[id] }
    }

    private static FileRow row(int id, int solutionId, String stored, String name = 'a.txt', Integer itemId = null, String notes = null) {
        new FileRow(id, solutionId, itemId, null, 'Title ' + id, name, 'local', null, stored, 'text/plain', 3L, notes, 'tony', 'Tony',
                    null, null)
    }

    /** Writes a text file at base/relative and answers its path. */
    private static Path put(Path base, String relative, String text) {
        Path p = base.resolve(relative)
        Files.createDirectories(p.parent)
        Files.writeString(p, text)
        return p
    }

    /** Every regular file under base, relative, with forward slashes, sorted. */
    private static List<String> tree(Path base) {
        Files.walk(base).filter { Path it -> Files.isRegularFile(it) }.collect { Path it -> base.relativize(it).toString().replace('\\', '/') }.sort()
    }

    // ---- the safe name ------------------------------------------------------------------

    @Unroll
    def "#seq. safeName should answer '#expected' when #scenario"() {

        expect:
        SolutionFileService.safeName(original) == expected

        where:
        seq | scenario                                   | original                              || expected
        1   | 'the name is null'                         | null                                  || 'file'
        2   | 'the name is empty'                        | ''                                    || 'file'
        3   | 'the name is blank'                        | '   '                                 || 'file'
        4   | 'the name is plain'                        | 'report.pdf'                          || 'report.pdf'
        5   | 'the name uses every allowed sign'         | 'a b(1)-x_y.txt'                      || 'a b(1)-x_y.txt'
        6   | 'the name climbs with slashes'             | '../../etc/passwd'                    || 'passwd'
        7   | 'the name climbs with backslashes'         | '..\\..\\Windows\\win.ini'            || 'win.ini'
        8   | 'the name is a Windows absolute path'      | 'C:\\Users\\tony\\secret.txt'         || 'secret.txt'
        9   | 'the name is a Unix absolute path'         | '/absolute/path/x.sh'                 || 'x.sh'
        10  | 'the name is a UNC path'                   | '\\\\server\\share\\y.doc'            || 'y.doc'
        11  | 'the name is a folder'                     | 'dir/'                                || 'file'
        12  | 'the name is the root'                     | '/'                                   || 'file'
        13  | 'the name is two dots'                     | '..'                                  || 'file'
        14  | 'the name is three dots'                   | '...'                                 || 'file'
        15  | 'the name is a dot file'                   | '.htaccess'                           || 'htaccess'
        16  | 'the name starts with two dots'            | '..hidden.txt'                        || 'hidden.txt'
        17  | 'the name is a padded dot file'            | ' .env '                              || 'env'
        18  | 'the name carries markup with a slash'     | '<script>alert(1)</script>.html'      || 'script_.html'
        19  | 'the name carries shell signs'             | 'semi;colon|pipe&amp$.sh'             || 'semi_colon_pipe_amp_.sh'
        20  | 'the name carries quotes'                  | 'quote"s\'.txt'                       || 'quote_s_.txt'
        21  | 'the name carries a tab'                   | 'tab\there.txt'                       || 'tab_here.txt'
        22  | 'the name carries a NUL'                   | 'nul\u0000byte.txt'                   || 'nul_byte.txt'
        23  | 'the name carries a line break'            | 'new\r\nline.txt'                     || 'new__line.txt'
        24  | 'the name is not ASCII'                    | 'na\u00efve caf\u00e9.txt'            || 'na_ve caf_.txt'
        25  | 'the name is percent-encoded'              | '%2e%2e%2fx'                          || '_2e_2e_2fx'
        26  | 'the name has dots inside'                 | 'a..b.txt'                            || 'a..b.txt'
        27  | 'the name names an NTFS stream'            | 'file.txt:stream'                     || 'file.txt_stream'
        28  | 'the name carries wildcards'               | 'star*?.txt'                          || 'star__.txt'
    }

    def "safeName should keep the last 120 characters so the extension survives when the name is long"() {

        given:
        String name = getReplicatedString('a', 200) + '.txt'

        expect:
        SolutionFileService.safeName(name).length() == 120
        SolutionFileService.safeName(name).endsWith('.txt')
        SolutionFileService.safeName(getReplicatedString('b', 120)) == getReplicatedString('b', 120)
        SolutionFileService.safeName(getReplicatedString('c', 121)) == getReplicatedString('c', 120)
    }

    @Unroll
    def "#seq. safeName should answer a name inside its alphabet when #scenario"() {

        when:
        String s = SolutionFileService.safeName(original)

        then:
        !s.isEmpty()
        !s.contains('/')
        !s.contains('\\')
        !s.startsWith('.')
        (s ==~ /[A-Za-z0-9._ ()\-]+/) == expected

        where:
        seq | scenario                            | original            || expected
        1   | 'the name climbs'                   | '../x'              || true
        2   | 'the name is only separators'       | '..\\..\\'          || true
        3   | 'the name is control characters'    | '\u0001\u0002'      || true
        4   | 'the name is dots and slashes'      | '.../...'           || true
        5   | 'the name is spaced dots'           | '. . .'             || true
        6   | 'the name hides a right-to-left'    | '\u202Eexe.txt'     || true
        7   | 'the name is a device name'         | 'CON'               || true
        8   | 'the name is a colon'               | ':'                 || true
        9   | 'the name is a pipe'                | '|'                 || true
    }

    // ---- the files folder ----------------------------------------------------------------

    def "the constructor should resolve the folder to a normal absolute path when ensureDirs makes it"() {

        given:
        Path nested = tmp.resolve('a/b/../c')
        SolutionFileService s = new SolutionFileService(files, access, nested.toString())

        when:
        s.ensureDirs()

        then:
        Files.isDirectory(tmp.resolve('a/c'))
        !Files.exists(tmp.resolve('a/b'))
    }

    // ---- list and get ---------------------------------------------------------------------

    @Unroll
    def "list should answer the solution's files when #scenario"() {

        when:
        List<FileRow> l = service.list(REX, 1, itemId)

        then:
        1 * files.listForSolution(1, itemId) >> [row(1, 1, '1/x')]
        l*.id() == intsOf(expectedIds)

        where:
        scenario                 | itemId || expectedIds
        'no item is named'       | null   || '1'
        'one item is named'      | 7      || '1'
    }

    def "list should read nothing when the solution is hidden"() {

        when:
        service.list(REX, 2, null)

        then:
        thrown(SolutionNotFoundException)
        0 * files.listForSolution(*_)
    }

    def "get should answer the file when the caller may see it"() {

        given:
        rows[1] = row(1, 1, '1/x')
        rows[2] = row(2, 2, '2/y')

        expect:
        service.get(REX, 1).id() == 1
        service.get(TONY, 2).id() == 2
    }

    @Unroll
    def "get should throw not found when #scenario"() {

        given:
        rows[1] = row(1, 1, '1/x')
        rows[2] = row(2, 2, '2/y')
        SolutionCaller caller = callerOf(callerName)

        when:
        service.get(caller, fileId)

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        scenario                         | callerName | fileId || expectedMessage
        'the id is zero'                 | 'TONY'     | 0      || 'File not found.'
        'the id is negative'             | 'TONY'     | -1     || 'File not found.'
        'the file does not exist'        | 'TONY'     | 99     || 'File not found.'
        'the client was not given it'    | 'REX'      | 2      || 'Solution not found.'
        'the client was given nothing'   | 'ZED'      | 1      || 'Solution not found.'
    }

    // ---- upload ---------------------------------------------------------------------------

    def "upload should store the bytes under a random prefix and a safe name when a client uploads"() {

        given:
        byte[] bytes = 'hello world'.bytes
        String stored = null

        when:
        FileRow f = service.upload(REX, 1, 7, 'My title', 'notes', 'report.pdf', 'application/pdf', bytes.length, new ByteArrayInputStream(bytes))

        then:
        1 * files.insert(1, 7, 'My title', 'report.pdf', 'local', null, _, 'application/pdf', 11L, 'notes', 'rex') >> { a ->
            stored = a[6]; rows[40] = row(40, 1, stored); 40 }
        f.id() == 40
        stored ==~ /1\/[0-9a-f]{8}-report\.pdf/
        Files.readAllBytes(root.resolve(stored)) == bytes
        tree(tmp) == ['EchoFiles/' + stored]
    }

    @Unroll
    def "upload should keep the bytes inside the solution's folder when #scenario"() {

        given:
        String stored = null

        when:
        service.upload(TONY, 1, null, null, null, name, null, 3, new ByteArrayInputStream('abc'.bytes))

        then:
        1 * files.insert(1, null, null, expectedName, 'local', null, _, null, 3L, null, 'tony') >> { a -> stored = a[6]; 41 }
        stored.startsWith('1/')
        stored.endsWith('-' + expectedName)
        tree(tmp) == ['EchoFiles/' + stored]

        where:
        scenario                               | name                         || expectedName
        'the name climbs with slashes'         | '../../../escape.txt'        || 'escape.txt'
        'the name climbs with backslashes'     | '..\\..\\..\\escape.txt'     || 'escape.txt'
        'the name is a Windows absolute path'  | 'C:\\Windows\\Temp\\abs.txt' || 'abs.txt'
        'the name is a Unix absolute path'     | '/etc/passwd'                || 'passwd'
        'the name is two dots'                 | '..'                         || 'file'
        'the name is a dot file'               | '.bashrc'                    || 'bashrc'
        'the name climbs out of trash'         | 'trash/../../x.txt'          || 'x.txt'
    }

    def "upload should record the size it read when the request claimed another"() {

        when:
        service.upload(TONY, 1, null, null, null, 'a.txt', null, 999, new ByteArrayInputStream('abc'.bytes))

        then:
        1 * files.insert(1, null, null, 'a.txt', 'local', null, _, null, 3L, null, 'tony') >> 41
    }

    @Unroll
    def "upload should refuse an empty file and write nothing when #scenario"() {

        when:
        service.upload(TONY, 1, null, null, null, 'a.txt', null, size, new ByteArrayInputStream(new byte[0]))

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage
        0 * files.insert(*_)
        tree(tmp) == []

        where:
        scenario                          | size || expectedMessage
        'the declared size is zero'       | 0L   || 'The file is empty.'
        'the declared size is negative'   | -1L  || 'The file is empty.'
        'the stream is empty'             | 10L  || 'The file is empty.'
    }

    def "upload should refuse a declared size over 50 MB before reading anything"() {

        given:
        InputStream stream = Mock()

        when:
        service.upload(TONY, 1, null, null, null, 'big.bin', null, SolutionFileService.MAX_BYTES + 1, stream)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'A file may be at most 50 MB.'
        0 * stream._
        0 * files.insert(*_)
    }

    def "upload should refuse a stream longer than 50 MB and write nothing when the request claimed less"() {

        given: 'a stream that never ends'
        InputStream endless = new InputStream() {
            int read() { 120 }
            int read(byte[] b, int off, int len) { Arrays.fill(b, off, off + len, (byte) 120); len }
        }

        when:
        service.upload(TONY, 1, null, null, null, 'big.bin', null, 10, endless)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'A file may be at most 50 MB.'
        0 * files.insert(*_)
        tree(tmp) == []
    }

    def "upload should accept the file when it is exactly 50 MB"() {

        given:
        byte[] bytes = new byte[(int) SolutionFileService.MAX_BYTES]

        when:
        service.upload(TONY, 1, null, null, null, 'max.bin', null, bytes.length, new ByteArrayInputStream(bytes))

        then:
        1 * files.insert(*_) >> 42
        SolutionFileService.MAX_BYTES == 50L * 1024 * 1024
        Files.size(root.resolve('1').toFile().listFiles()[0].toPath()) == SolutionFileService.MAX_BYTES
    }

    @Unroll
    def "upload should read and write nothing when #scenario"() {

        given:
        InputStream stream = Mock()
        SolutionCaller caller = callerOf(callerName)

        when:
        service.upload(caller, solutionId, null, null, null, 'a.txt', null, 3, stream)

        then:
        thrown(SolutionNotFoundException)
        0 * stream._
        0 * files.insert(*_)
        tree(tmp) == []

        where:
        scenario                         | callerName | solutionId || unused
        'the client was not given it'    | 'REX'      | 2          || null
        'the client was given nothing'   | 'ZED'      | 1          || null
        'the solution id is zero'        | 'TONY'     | 0          || null
    }

    def "upload should move the written bytes to trash and rethrow when the record is refused"() {

        when:
        service.upload(TONY, 1, null, null, null, 'a.txt', null, 3, new ByteArrayInputStream('abc'.bytes))

        then:
        1 * files.insert(*_) >> { throw new SolutionRuleException('Nothing was saved.') }
        SolutionRuleException ex = thrown()
        ex.message == 'Nothing was saved.'
        List<String> left = tree(tmp)
        left.size() == 1
        left[0] ==~ /EchoFiles\/trash\/[0-9a-f]{8}-a\.txt/
        Files.readString(root.resolve(left[0].substring('EchoFiles/'.length()))) == 'abc'
    }

    def "upload should never overwrite a file when the same name is uploaded twice"() {

        when:
        service.upload(TONY, 1, null, null, null, 'same.txt', null, 3, new ByteArrayInputStream('abc'.bytes))
        service.upload(TONY, 1, null, null, null, 'same.txt', null, 3, new ByteArrayInputStream('abc'.bytes))

        then:
        2 * files.insert(*_) >> 43
        tree(tmp).findAll { String it -> it.endsWith('-same.txt') }.size() == 2
    }

    // ---- update -----------------------------------------------------------------------------

    @Unroll
    def "update should merge item and notes and pass the title through when #scenario"() {

        given:
        rows[1] = row(1, 1, '1/x', 'a.txt', 7, 'old notes')

        when:
        FileRow f = service.update(REX, 1, itemId, title, notes)

        then:
        1 * files.update(1, expectedItem, title, expectedNotes) >> true
        f.id() == 1

        where:
        scenario                         | itemId | title | notes || expectedItem | expectedNotes
        'everything is left out'         | null   | null  | null  || 7            | 'old notes'
        'the item and notes are cleared' | 0      | 'T'   | ''    || 0            | ''
        'everything is sent'             | 9      | ''    | 'new' || 9            | 'new'
    }

    def "update should write nothing for a hidden file and throw not found when nothing changed"() {

        given:
        rows[1] = row(1, 1, '1/x')
        rows[2] = row(2, 2, '2/x')

        when:
        service.update(REX, 2, null, 'x', null)

        then:
        thrown(SolutionNotFoundException)
        0 * files.update(*_)

        when:
        service.update(TONY, 1, null, 'x', null)

        then:
        1 * files.update(*_) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'File not found.'
    }

    // ---- the path of a download ------------------------------------------------------------------

    @Unroll
    def "pathOf should answer the stored file when #scenario"() {

        given:
        Path p = put(root, '1/abcd1234-a.txt', 'abc')

        expect:
        service.pathOf(row(1, 1, stored)) == p.toAbsolutePath().normalize()

        where:
        scenario                            | stored                  || unused
        'the name uses a slash'             | '1/abcd1234-a.txt'      || null
        'the name uses a backslash'         | '1\\abcd1234-a.txt'     || null
    }

    @Unroll
    def "#seq. pathOf should throw not found when the stored name escapes the folder #scenario"() {

        given:
        put(tmp, 'outside.txt', 'secret')
        put(tmp, 'EchoFiles2/x.txt', 'sibling')
        put(root, '1/inside.txt', 'abc')
        String name = stored.startsWith('ABS:') ? tmp.resolve(stored.substring(4)).toAbsolutePath().toString() : stored

        when:
        service.pathOf(row(1, 1, name))

        then:
        SolutionNotFoundException ex = thrown()
        ex.message == expectedMessage

        where:
        seq | scenario                            | stored                              || expectedMessage
        1   | 'one level up'                      | '../outside.txt'                    || 'File not found.'
        2   | 'one level up with a backslash'     | '..\\outside.txt'                   || 'File not found.'
        3   | 'in and out again'                  | '1/../../outside.txt'               || 'File not found.'
        4   | 'with a dot segment'                | '1/./../../outside.txt'             || 'File not found.'
        5   | 'into a sibling with a like name'   | '../EchoFiles2/x.txt'               || 'File not found.'
        6   | 'out of trash'                      | 'trash/../../outside.txt'           || 'File not found.'
        7   | 'an absolute path outside'          | 'ABS:outside.txt'                   || 'File not found.'
        8   | 'an absolute sibling path'          | 'ABS:EchoFiles2/x.txt'              || 'File not found.'
        9   | 'a UNC path'                        | '\\\\localhost\\c$\\outside.txt'    || 'File not found.'
    }

    @Unroll
    def "pathOf should throw not found when #scenario"() {

        given:
        Files.createDirectories(root.resolve('1/folder'))

        when:
        service.pathOf(row(1, 1, stored))

        then:
        thrown(SolutionNotFoundException)

        where:
        scenario                       | stored          || unused
        'there is no stored name'      | null            || null
        'the file is missing'          | '1/missing.txt' || null
        'the name is a folder'         | '1/folder'      || null
        'the name is a solution'       | '1'             || null
        'the name is empty'            | ''              || null
        'the name is a dot'            | '.'             || null
    }

    // ---- open folder -------------------------------------------------------------------------

    def "openFolder should refuse a client that can see the file before anything runs"() {

        given:
        rows[1] = row(1, 1, '1/a.txt')
        put(root, '1/a.txt', 'abc')

        when:
        service.openFolder(REX, 1)

        then:
        SolutionForbiddenException ex = thrown()
        ex.message == 'Only a person may open a folder on this computer.'
    }

    @Unroll
    def "openFolder should throw not found before Explorer is asked when #scenario"() {

        given:
        rows[2] = row(2, 2, '2/a.txt')
        rows[3] = row(3, 1, '1/gone.txt')
        SolutionCaller caller = callerOf(callerName)

        when:
        service.openFolder(caller, fileId)

        then:
        thrown(SolutionNotFoundException)

        where:
        scenario                         | callerName | fileId || unused
        'the file is hidden'             | 'REX'      | 2      || null
        'the bytes are missing'          | 'TONY'     | 3      || null
        'the row does not exist'         | 'TONY'     | 99     || null
    }

    // ---- delete --------------------------------------------------------------------------------

    def "delete should remove the row and move the bytes to trash when the file is visible"() {

        given:
        rows[1] = row(1, 1, '1/abcd1234-a.txt')
        put(root, '1/abcd1234-a.txt', 'keep me')

        when:
        service.delete(REX, 1)

        then:
        1 * files.delete(1) >> true
        tree(tmp) == ['EchoFiles/trash/abcd1234-a.txt']
        Files.readString(root.resolve('trash/abcd1234-a.txt')) == 'keep me'
    }

    def "delete should number the trash copy when the name is already in trash"() {

        given:
        put(root, 'trash/x.txt', 'first')
        put(root, 'trash/2-x.txt', 'second')
        rows[1] = row(1, 1, '1/x.txt')
        put(root, '1/x.txt', 'third')

        when:
        service.delete(TONY, 1)

        then:
        1 * files.delete(1) >> true
        Files.readString(root.resolve('trash/x.txt')) == 'first'
        Files.readString(root.resolve('trash/2-x.txt')) == 'second'
        Files.readString(root.resolve('trash/3-x.txt')) == 'third'
        !Files.exists(root.resolve('1/x.txt'))
    }

    @Unroll
    def "delete should just remove the row when #scenario"() {

        given:
        rows[1] = row(1, 1, stored)

        when:
        service.delete(TONY, 1)

        then:
        1 * files.delete(1) >> true
        noExceptionThrown()
        tree(tmp) == []

        where:
        scenario                       | stored        || unused
        'the bytes are already gone'   | '1/gone.txt'  || null
        'there is no stored name'      | null          || null
    }

    def "delete should touch neither row nor bytes when the file is hidden"() {

        given:
        rows[2] = row(2, 2, '2/a.txt')
        put(root, '2/a.txt', 'abc')

        when:
        service.delete(REX, 2)

        then:
        thrown(SolutionNotFoundException)
        0 * files.delete(_)
        tree(tmp) == ['EchoFiles/2/a.txt']
    }

    def "delete should leave the bytes in place and throw not found when the repository refused"() {

        given:
        rows[1] = row(1, 1, '1/a.txt')
        put(root, '1/a.txt', 'abc')

        when:
        service.delete(TONY, 1)

        then:
        1 * files.delete(1) >> false
        SolutionNotFoundException ex = thrown()
        ex.message == 'File not found.'
        tree(tmp) == ['EchoFiles/1/a.txt']
    }

    def "delete should never move a file from outside the folder when the stored name escapes it"() {

        given:
        put(tmp, 'outside.txt', 'secret')
        rows[1] = row(1, 1, '../outside.txt')

        when:
        service.delete(TONY, 1)

        then:
        1 * files.delete(1) >> true
        thrown(SolutionNotFoundException)
        Files.readString(tmp.resolve('outside.txt')) == 'secret'
        !Files.exists(root.resolve('trash'))
    }
}
