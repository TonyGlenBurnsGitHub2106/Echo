package com.echo

import java.nio.file.Files
import java.nio.file.Path

import org.spockframework.spring.SpringBean
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import com.echo.SolutionFileVersionModels.FileVersion
import com.echo.SolutionModels.FileRow

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import spock.lang.TempDir
import spock.lang.Unroll

/**
 * Story 5653: files attached to a solution, over HTTP. The service is real (delegated from the bean mock)
 * and keeps its bytes in a @TempDir, so uploads, downloads and the move to trash really happen on disk --
 * inside the temp folder only. Opening Explorer is never run: for a person whose file is on disk the
 * open-folder call answers the folder without starting explorer.exe; every refusal goes to the real service.
 */
@WebMvcTest(SolutionFileController)
class SolutionFileControllerSpec extends ConfigWebSupport {

    @SpringBean SolutionFileService service = Mock()

    @TempDir Path tmp

    SolutionFileRepository files = Mock()
    SolutionFileService real
    Path root
    Map<Integer, FileRow> rows = [:]
    Map<Integer, FileVersion> versionRows = [:]

    def setup() {
        root = tmp.resolve('EchoFiles')
        real = new SolutionFileService(files, access, root.toString())
        real.ensureDirs()
        service.list(*_) >> { a -> real.list(*a) }
        service.get(*_) >> { a -> real.get(*a) }
        service.upload(*_) >> { a -> real.upload(*a) }
        service.update(*_) >> { a -> real.update(*a) }
        service.pathOf(*_) >> { a -> real.pathOf(*a) }
        service.delete(*_) >> { a -> real.delete(*a) }
        service.newVersion(*_) >> { a -> real.newVersion(*a) }
        service.versions(*_) >> { a -> real.versions(*a) }
        service.version(*_) >> { a -> real.version(*a) }
        service.openFolder(*_) >> { SolutionCaller c, int id ->
            FileRow f = rows[id]
            boolean onDisk = f != null && f.storedFileName() != null && Files.isRegularFile(root.resolve(f.storedFileName()))
            (!c.isClient() && onDisk && solutionIds.contains(f.solutionId())) ? root.resolve(f.storedFileName()).parent.toString() : real.openFolder(c, id)
        }

        rows[1] = row(1, 1, '1/abcd1234-a.txt', 'a.txt')
        rows[2] = row(2, 2, '2/efgh5678-b.txt', 'b.txt')
        put(root, '1/abcd1234-a.txt', 'alpha')
        put(root, '2/efgh5678-b.txt', 'bravo')

        files.find(_) >> { int id -> rows[id] }
        files.listForSolution(_, _) >> { int s, Integer item -> rows.values().findAll { it.solutionId() == s } as List }
        files.insert(*_) >> { a ->
            int id = 40 + rows.size()
            rows[id] = row(id, (int) a[0], (String) a[6], (String) a[3])
            id
        }
        files.update(*_) >> true
        files.delete(_) >> true
        files.newVersion(*_) >> 2
        files.versions(_) >> []
        files.version(_) >> { int id -> versionRows[id] }
    }

    private static FileRow row(int id, int solutionId, String stored, String name) {
        new FileRow(id, solutionId, null, null, 'Title ' + id, name, 'local', null, stored, 'text/plain', 5L, null, 'tony', 'Tony', null, null)
    }

    private static Path put(Path base, String relative, String text) {
        Path p = base.resolve(relative)
        Files.createDirectories(p.parent)
        Files.writeString(p, text)
        return p
    }

    private static List<String> tree(Path base) {
        Files.walk(base).filter { Path it -> Files.isRegularFile(it) }.collect { Path it -> base.relativize(it).toString().replace('\\', '/') }.sort()
    }

    /** A request a data table can name: an upload is multipart with one small file, the rest are plain. */
    private static AbstractMockHttpServletRequestBuilder<?> fileRequest(String method, String url, String body) {
        if (method == 'UPLOAD') {
            return multipart(url).file(new MockMultipartFile('file', 'c.txt', 'text/plain', 'charlie'.bytes))
        }
        return request(method, url, body)
    }

    @Unroll
    def "#scenario should answer 401 and touch nothing when no credential is sent"() {

        when:
        ResultActions r = mvc.perform(byAny('none', fileRequest(method, url, body)))

        then:
        0 * files._
        r.andExpect(status().is(expectedStatus))
        tree(tmp) == ['EchoFiles/1/abcd1234-a.txt', 'EchoFiles/2/efgh5678-b.txt']

        where:
        scenario         | method   | url                                   | body            || expectedStatus
        'list'           | 'GET'    | '/api/solutions/1/files'              | null            || 401
        'upload'         | 'UPLOAD' | '/api/solutions/1/files'              | null            || 401
        'get'            | 'GET'    | '/api/solutions/files/1'              | null            || 401
        'update'         | 'PUT'    | '/api/solutions/files/1'              | '{"title":"T"}' || 401
        'content'        | 'GET'    | '/api/solutions/files/1/content'      | null            || 401
        'openFolder'     | 'POST'   | '/api/solutions/files/1/open-folder'  | null            || 401
        'delete'         | 'DELETE' | '/api/solutions/files/1'              | null            || 401
    }

    @Unroll
    def "#scenario should answer 200 when a person, the house or a sharing agent calls"() {

        expect:
        for (String who : PERMITTED) {
            mvc.perform(byAny(who, fileRequest(method, url, body))).andExpect(status().is(expectedStatus))
        }

        where:
        scenario         | method   | url                                   | body            || expectedStatus
        'list'           | 'GET'    | '/api/solutions/1/files'              | null            || 200
        'upload'         | 'UPLOAD' | '/api/solutions/1/files'              | null            || 200
        'get'            | 'GET'    | '/api/solutions/files/1'              | null            || 200
        'update'         | 'PUT'    | '/api/solutions/files/1'              | '{"title":"T"}' || 200
        'content'        | 'GET'    | '/api/solutions/files/1/content'      | null            || 200
    }

    @Unroll
    def "#seq. #scenario should answer 404 and touch nothing when the agent #who was not given the solution"() {

        when:
        ResultActions r = mvc.perform(byAny(who, fileRequest(method, url, body)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * files.insert(*_)
        0 * files.update(*_)
        0 * files.delete(_)
        tree(tmp) == ['EchoFiles/1/abcd1234-a.txt', 'EchoFiles/2/efgh5678-b.txt']

        where:
        seq | scenario         | who   | method   | url                                   | body            || expectedStatus
        1   | 'list'           | 'zed' | 'GET'    | '/api/solutions/1/files'              | null            || 404
        2   | 'upload'         | 'zed' | 'UPLOAD' | '/api/solutions/1/files'              | null            || 404
        3   | 'get'            | 'zed' | 'GET'    | '/api/solutions/files/1'              | null            || 404
        4   | 'update'         | 'zed' | 'PUT'    | '/api/solutions/files/1'              | '{"title":"T"}' || 404
        5   | 'content'        | 'zed' | 'GET'    | '/api/solutions/files/1/content'      | null            || 404
        6   | 'openFolder'     | 'zed' | 'POST'   | '/api/solutions/files/1/open-folder'  | null            || 404
        7   | 'delete'         | 'zed' | 'DELETE' | '/api/solutions/files/1'              | null            || 404
        8   | 'list'           | 'rex' | 'GET'    | '/api/solutions/2/files'              | null            || 404
        9   | 'upload'         | 'rex' | 'UPLOAD' | '/api/solutions/2/files'              | null            || 404
        10  | 'get'            | 'rex' | 'GET'    | '/api/solutions/files/2'              | null            || 404
        11  | 'update'         | 'rex' | 'PUT'    | '/api/solutions/files/2'              | '{"title":"T"}' || 404
        12  | 'content'        | 'rex' | 'GET'    | '/api/solutions/files/2/content'      | null            || 404
        13  | 'openFolder'     | 'rex' | 'POST'   | '/api/solutions/files/2/open-folder'  | null            || 404
        14  | 'delete'         | 'rex' | 'DELETE' | '/api/solutions/files/2'              | null            || 404
    }

    def "list should answer the files of the solution when an item filter is sent"() {

        when:
        ResultActions r = mvc.perform(asAgent(get('/api/solutions/1/files').param('itemId', '7')))

        then:
        1 * files.listForSolution(1, 7) >> [rows[1]]
        r.andExpect(status().isOk()).andExpect(jsonPath('$[0].id').value(1)).andExpect(jsonPath('$[0].originalFileName').value('a.txt'))
    }

    def "upload should store the bytes inside the solution's folder attributed to the agent when an agent uploads"() {

        when:
        ResultActions r = mvc.perform(byAny('rex', multipart('/api/solutions/1/files')
            .file(new MockMultipartFile('file', '../../../escape.html', 'text/html', '<script>x</script>'.bytes))
            .param('title', 'T').param('notes', 'N').param('itemId', '3')))

        then:
        1 * files.insert(1, 3, 'T', 'escape.html', 'local', null, { String s -> s ==~ /1\/[0-9a-f]{8}-escape\.html/ }, 'text/html', 18L, 'N', 'rex') >> 50
        r.andExpect(status().isOk())
        tree(tmp).findAll { String it -> it.endsWith('-escape.html') }.size() == 1
        tree(tmp).every { String it -> it.startsWith('EchoFiles/') }
        tree(tmp).findAll { String it -> it.endsWith('-escape.html') }[0].startsWith('EchoFiles/1/')
    }

    @Unroll
    def "upload should answer 400 'A file is required.' and write nothing when #scenario"() {

        given:
        AbstractMockHttpServletRequestBuilder<?> b = withFile
            ? multipart('/api/solutions/1/files').file(new MockMultipartFile(part, 'a.txt', 'text/plain', new byte[size]))
            : multipart('/api/solutions/1/files').param('title', 'T')

        when:
        ResultActions r = mvc.perform(byAny('user', b))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * files.insert(*_)

        where:
        scenario                          | withFile | part   | size || expectedMessage
        'no file part is sent'            | false    | 'file' | 0    || 'A file is required.'
        'the file part is empty'          | true     | 'file' | 0    || 'A file is required.'
        'the file is under another name'  | true     | 'doc'  | 3    || 'A file is required.'
    }

    def "upload should answer 400 when itemId is not a number"() {

        expect:
        mvc.perform(byAny('user', multipart('/api/solutions/1/files').file(new MockMultipartFile('file', 'a.txt', 'text/plain', 'abc'.bytes))
            .param('itemId', 'seven'))).andExpect(status().isBadRequest())
    }

    def "upload should answer 415 when the request is not multipart"() {

        expect:
        mvc.perform(asUser(request('POST', '/api/solutions/1/files', '{"file":"abc"}'))).andExpect(status().isUnsupportedMediaType())
    }

    def "get should answer one file's details when a person reads it"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/files/1'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.id').value(1))
            .andExpect(jsonPath('$.solutionId').value(1))
            .andExpect(jsonPath('$.storedFileName').value('1/abcd1234-a.txt'))
            .andExpect(jsonPath('$.storageCode').value('local'))
    }

    def "update should merge the details when a partial body is sent"() {

        when:
        mvc.perform(asAgent(request('PUT', '/api/solutions/files/1', '{"title":"New"}'))).andExpect(status().isOk())

        then:
        1 * files.update(1, null, 'New', null) >> true
    }

    @Unroll
    def "update should answer 400 when #scenario"() {

        expect:
        mvc.perform(asUser(json(put('/api/solutions/files/1'), body))).andExpect(status().is(expectedStatus))

        where:
        scenario                 | body             || expectedStatus
        'the body is empty'      | ''               || 400
        'the body is null'       | 'null'           || 400
        'the body is broken'     | '{"title"'       || 400
        'itemId is a word'       | '{"itemId":"x"}' || 400
    }

    def "update should answer 404 when the repository changed nothing"() {

        when:
        ResultActions r = mvc.perform(asUser(request('PUT', '/api/solutions/files/1', '{"title":"x"}')))

        then:
        1 * files.update(*_) >> false
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('File not found.'))
    }

    def "content should always serve an attachment of octet-stream with nosniff and a sandbox when a file is downloaded"() {

        when:
        MockHttpServletResponse resp = mvc.perform(asUser(get('/api/solutions/files/1/content'))).andExpect(status().isOk()).andReturn().response

        then:
        resp.contentType == MediaType.APPLICATION_OCTET_STREAM_VALUE
        resp.getHeader('Content-Disposition').startsWith('attachment')
        resp.getHeader('Content-Disposition').contains('a.txt')
        resp.getHeader('X-Content-Type-Options') == 'nosniff'
        resp.getHeader('Content-Security-Policy').contains('sandbox')
        resp.contentAsString == 'alpha'
    }

    @Unroll
    def "content should never let the original name break the header when #scenario"() {

        given:
        rows[1] = row(1, 1, '1/abcd1234-a.txt', name)

        when:
        MockHttpServletResponse resp = mvc.perform(asUser(get('/api/solutions/files/1/content'))).andExpect(status().isOk()).andReturn().response

        then:
        resp.contentType == MediaType.APPLICATION_OCTET_STREAM_VALUE
        resp.getHeader('Content-Disposition').startsWith('attachment')
        !resp.getHeader('Content-Disposition').contains('\r')
        !resp.getHeader('Content-Disposition').contains('\n')
        resp.getHeader('Set-Cookie') == expectedCookie

        where:
        scenario                         | name                                || expectedCookie
        'the name is an HTML page'       | 'evil.html'                         || null
        'the name is an SVG'             | 'image.svg'                         || null
        'the name carries quotes'        | 'a"b".txt'                          || null
        'the name is not ASCII'          | 'caf\u00e9.txt'                     || null
    }

    @Unroll
    def "content should answer 404 when #scenario"() {

        given:
        put(tmp, 'outside.txt', 'secret')
        rows[1] = row(1, 1, stored, 'a.txt')

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/files/1/content')))

        then:
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        scenario                               | stored                         || expectedMessage
        'the bytes are missing'                | '1/gone.txt'                   || 'File not found.'
        'there is no stored name'              | null                           || 'File not found.'
        'the stored name climbs out'           | '../outside.txt'               || 'File not found.'
        'the stored name climbs out deeper'    | '1/../../outside.txt'          || 'File not found.'
        'the stored name is a folder'          | '1'                            || 'File not found.'
    }

    def "content should answer 404 when the file id does not exist"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/files/99/content'))).andExpect(status().isNotFound())
        mvc.perform(asUser(get('/api/solutions/files/0/content'))).andExpect(status().isNotFound())
    }

    def "openFolder should answer the folder when a person asks for a file on disk"() {

        expect:
        mvc.perform(asUser(post('/api/solutions/files/1/open-folder'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.ok').value(true))
            .andExpect(jsonPath('$.folder').value(root.resolve('1').toString()))
    }

    def "openFolder should answer 403 when an agent that can see the file asks"() {

        expect:
        mvc.perform(asAgent(post('/api/solutions/files/1/open-folder'))).andExpect(status().isForbidden())
            .andExpect(jsonPath('$.error').value('forbidden'))
            .andExpect(jsonPath('$.message').value('Only a person may open a folder on this computer.'))
    }

    def "openFolder should answer 404 when the bytes are missing for a person"() {

        given:
        rows[3] = row(3, 1, '1/gone.txt', 'gone.txt')

        expect:
        mvc.perform(asUser(post('/api/solutions/files/3/open-folder'))).andExpect(status().isNotFound())
    }

    def "delete should move the bytes to trash and answer the id when an agent deletes"() {

        when:
        ResultActions r = mvc.perform(asAgent(delete('/api/solutions/files/1')))

        then:
        1 * files.delete(1) >> true
        r.andExpect(status().isOk()).andExpect(jsonPath('$.ok').value(true)).andExpect(jsonPath('$.deletedId').value(1))
        tree(tmp) == ['EchoFiles/2/efgh5678-b.txt', 'EchoFiles/trash/abcd1234-a.txt']
    }

    def "delete should answer 404 and leave the bytes when the repository refused"() {

        when:
        ResultActions r = mvc.perform(asUser(delete('/api/solutions/files/1')))

        then:
        1 * files.delete(1) >> false
        r.andExpect(status().isNotFound())
        tree(tmp) == ['EchoFiles/1/abcd1234-a.txt', 'EchoFiles/2/efgh5678-b.txt']
    }

    def "content should refuse to build the header and answer 400 when the stored name carries a line break"() {

        given:
        rows[1] = row(1, 1, '1/abcd1234-a.txt', 'a\r\nSet-Cookie: stolen=1.txt')

        when:
        MockHttpServletResponse resp = mvc.perform(asUser(get('/api/solutions/files/1/content'))).andReturn().response

        then:
        resp.status == 400
        resp.getHeader('Set-Cookie') == null
        resp.getHeader('Content-Disposition') == null
    }

    def "file endpoints should match nothing when the id is not a number"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/files/abc'))).andExpect(status().isNotFound())
        mvc.perform(asUser(get('/api/solutions/files/abc/content'))).andExpect(status().isNotFound())
    }

    @Unroll
    def "file endpoints should be rejected with 400 before any controller when #scenario"() {

        when:
        ResultActions r = mvc.perform(by(who, get(url)))

        then:
        0 * files._
        r.andExpect(status().is(expectedStatus))

        where:
        scenario                                  | who    | url                                             || expectedStatus
        'a person sends an encoded slash'         | 'user' | '/api/solutions/files/..%2F..%2Fsecret/content' || 400
        'an agent sends encoded dots'             | 'rex'  | '/api/solutions/files/%2e%2e/content'           || 400
        'an agent sends an encoded backslash'     | 'rex'  | '/api/solutions/files/1%5C..%5C2/content'       || 400
    }

    private static MockHttpServletRequestBuilder put(String url) {
        request('PUT', url, null)
    }

    // ---- Story 5695 (Release 5): new versions ---------------------------------------------------------

    /** An earlier version of a file whose bytes sit at stored, in the given solution. */
    private static FileVersion fileVersion(int id, int fileId, int number, String name, String stored, Integer solutionId) {
        new FileVersion(id, fileId, number, name, stored, 'text/plain', 5L, 'what changed next', null, 'tony', 'Tony', solutionId)
    }

    /** Version 5 is of file 1 (solution 1), version 6 of file 2 (solution 2); both have bytes on disk. */
    private static void earlierVersionsOnDisk(Map<Integer, FileVersion> versions, Path base) {
        versions[5] = fileVersion(5, 1, 1, 'a-old.txt', '1/zzzz0000-a-old.txt', 1)
        versions[6] = fileVersion(6, 2, 1, 'b-old.txt', '2/yyyy0000-b-old.txt', 2)
        put(base, '1/zzzz0000-a-old.txt', 'alpha one')
        put(base, '2/yyyy0000-b-old.txt', 'bravo one')
    }

    @Unroll
    def "the version endpoint to #scenario should answer 401 and touch nothing when no credential is sent"() {

        given:
        earlierVersionsOnDisk(versionRows, root)

        when:
        ResultActions r = mvc.perform(byAny('none', fileRequest(method, url, null)))

        then:
        0 * files._
        r.andExpect(status().is(expectedStatus))
        tree(tmp).size() == 4

        where:
        scenario                   | method   | url                                         || expectedStatus
        'upload a new version'     | 'UPLOAD' | '/api/solutions/files/1/versions'           || 401
        'list the versions'        | 'GET'    | '/api/solutions/files/1/versions'           || 401
        'download a version'       | 'GET'    | '/api/solutions/files/versions/5/content'   || 401
    }

    @Unroll
    def "the version endpoint to #scenario should answer 200 when a person, the house or a sharing agent calls"() {

        given:
        earlierVersionsOnDisk(versionRows, root)

        expect:
        for (String who : PERMITTED) {
            mvc.perform(byAny(who, fileRequest(method, url, null))).andExpect(status().is(expectedStatus))
        }

        where:
        scenario                   | method   | url                                         || expectedStatus
        'upload a new version'     | 'UPLOAD' | '/api/solutions/files/1/versions'           || 200
        'list the versions'        | 'GET'    | '/api/solutions/files/1/versions'           || 200
        'download a version'       | 'GET'    | '/api/solutions/files/versions/5/content'   || 200
    }

    @Unroll
    def "the version endpoint to #scenario should answer 404 and write nothing when the agent #who was not given the solution"() {

        given:
        earlierVersionsOnDisk(versionRows, root)

        when:
        ResultActions r = mvc.perform(byAny(who, fileRequest(method, url, null)))

        then:
        r.andExpect(status().is(expectedStatus)).andExpect(jsonPath('$.message').value('Solution not found.'))
        0 * files.newVersion(*_)
        0 * files.versions(_)
        tree(tmp).size() == 4

        where:
        scenario                   | who   | method   | url                                         || expectedStatus
        'upload a new version'     | 'zed' | 'UPLOAD' | '/api/solutions/files/1/versions'           || 404
        'list the versions'        | 'zed' | 'GET'    | '/api/solutions/files/1/versions'           || 404
        'download a version'       | 'zed' | 'GET'    | '/api/solutions/files/versions/5/content'   || 404
        'upload a new version'     | 'rex' | 'UPLOAD' | '/api/solutions/files/2/versions'           || 404
        'list the versions'        | 'rex' | 'GET'    | '/api/solutions/files/2/versions'           || 404
        'download a version'       | 'rex' | 'GET'    | '/api/solutions/files/versions/6/content'   || 404
    }

    def "newVersion should store the new bytes inside the file's solution folder attributed to the agent and answer the file"() {

        when:
        ResultActions r = mvc.perform(byAny('rex', multipart('/api/solutions/files/1/versions')
            .file(new MockMultipartFile('file', '../../../escape.html', 'text/html', '<script>x</script>'.bytes))
            .param('changeNote', '  QA numbers added  ')))

        then:
        1 * files.newVersion(1, 'escape.html', { String s -> s ==~ /1\/[0-9a-f]{8}-escape\.html/ }, 'text/html', 18L, 'QA numbers added', 'rex') >> 2
        r.andExpect(status().isOk()).andExpect(jsonPath('$.id').value(1)).andExpect(jsonPath('$.solutionId').value(1))
        tree(tmp).findAll { String it -> it.endsWith('-escape.html') }.size() == 1
        tree(tmp).findAll { String it -> it.endsWith('-escape.html') }[0].startsWith('EchoFiles/1/')
        Files.readString(root.resolve('1/abcd1234-a.txt')) == 'alpha'
    }

    def "newVersion should pass no note when none is sent"() {

        when:
        mvc.perform(byAny('user', multipart('/api/solutions/files/1/versions')
            .file(new MockMultipartFile('file', 'a2.txt', 'text/plain', 'abc'.bytes)))).andExpect(status().isOk())

        then:
        1 * files.newVersion(1, 'a2.txt', _, 'text/plain', 3L, null, 'tony') >> 2
    }

    @Unroll
    def "newVersion should answer 400 '#expectedMessage' and write nothing when #scenario"() {

        given:
        AbstractMockHttpServletRequestBuilder<?> b = withFile
            ? multipart('/api/solutions/files/1/versions').file(new MockMultipartFile(part, 'a.txt', 'text/plain', new byte[size]))
            : multipart('/api/solutions/files/1/versions').param('changeNote', 'x')

        when:
        ResultActions r = mvc.perform(byAny('user', b))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value(expectedMessage))
        0 * files.newVersion(*_)
        tree(tmp) == ['EchoFiles/1/abcd1234-a.txt', 'EchoFiles/2/efgh5678-b.txt']

        where:
        scenario                          | withFile | part   | size || expectedMessage
        'no file part is sent'            | false    | 'file' | 0    || 'A file is required.'
        'the file part is empty'          | true     | 'file' | 0    || 'A file is required.'
        'the file is under another name'  | true     | 'doc'  | 3    || 'A file is required.'
    }

    def "newVersion should answer 400 and write nothing when the note is over 500 characters"() {

        when:
        ResultActions r = mvc.perform(byAny('rex', multipart('/api/solutions/files/1/versions')
            .file(new MockMultipartFile('file', 'a2.txt', 'text/plain', 'abc'.bytes))
            .param('changeNote', getReplicatedString('n', 501))))

        then:
        r.andExpect(status().isBadRequest()).andExpect(jsonPath('$.message').value('What changed can be at most 500 characters.'))
        0 * files.newVersion(*_)
        tree(tmp) == ['EchoFiles/1/abcd1234-a.txt', 'EchoFiles/2/efgh5678-b.txt']
    }

    def "newVersion should answer 404 and write nothing when the file does not exist"() {

        when:
        ResultActions r = mvc.perform(byAny('user', fileRequest('UPLOAD', '/api/solutions/files/99/versions', null)))

        then:
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('File not found.'))
        0 * files.newVersion(*_)
        tree(tmp) == ['EchoFiles/1/abcd1234-a.txt', 'EchoFiles/2/efgh5678-b.txt']
    }

    def "newVersion should answer 409, keep the old bytes and move the new ones to trash when the row is refused"() {

        when:
        ResultActions r = mvc.perform(byAny('user', fileRequest('UPLOAD', '/api/solutions/files/1/versions', null)))

        then:
        1 * files.newVersion(*_) >> { throw new SolutionRuleException('That file no longer exists. Nothing was saved.') }
        r.andExpect(status().isConflict()).andExpect(jsonPath('$.message').value('That file no longer exists. Nothing was saved.'))
        tree(tmp).findAll { String it -> it.startsWith('EchoFiles/trash/') && it.endsWith('-c.txt') }.size() == 1
        Files.readString(root.resolve('1/abcd1234-a.txt')) == 'alpha'
    }

    def "newVersion should answer 415 when the request is not multipart"() {

        expect:
        mvc.perform(asUser(request('POST', '/api/solutions/files/1/versions', '{"file":"abc"}'))).andExpect(status().isUnsupportedMediaType())
    }

    def "versions should answer the current number and the earlier versions, newest first"() {

        when:
        ResultActions r = mvc.perform(asAgent(get('/api/solutions/files/1/versions')))

        then:
        1 * files.versions(1) >> [fileVersion(12, 1, 2, 'a-v2.txt', '1/b-a-v2.txt', null), fileVersion(11, 1, 1, 'a-v1.txt', '1/a-a-v1.txt', null)]
        r.andExpect(status().isOk())
            .andExpect(jsonPath('$.currentVersion').value(3))
            .andExpect(jsonPath('$.versions.length()').value(2))
            .andExpect(jsonPath('$.versions[0].id').value(12))
            .andExpect(jsonPath('$.versions[0].fileId').value(1))
            .andExpect(jsonPath('$.versions[0].versionNumber').value(2))
            .andExpect(jsonPath('$.versions[0].originalFileName').value('a-v2.txt'))
            .andExpect(jsonPath('$.versions[0].contentType').value('text/plain'))
            .andExpect(jsonPath('$.versions[0].sizeBytes').value(5))
            .andExpect(jsonPath('$.versions[0].changeNote').value('what changed next'))
            .andExpect(jsonPath('$.versions[0].uploadedByCode').value('tony'))
            .andExpect(jsonPath('$.versions[0].uploadedByName').value('Tony'))
            .andExpect(jsonPath('$.versions[1].versionNumber').value(1))
    }

    def "versions should answer version 1 and an empty list when the file has no earlier versions"() {

        expect:
        mvc.perform(asUser(get('/api/solutions/files/1/versions'))).andExpect(status().isOk())
            .andExpect(jsonPath('$.currentVersion').value(1))
            .andExpect(jsonPath('$.versions.length()').value(0))
    }

    def "versions should answer 404 when the file does not exist"() {

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/files/99/versions')))

        then:
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value('File not found.'))
        0 * files.versions(_)
    }

    def "versionContent should always serve an attachment of octet-stream with nosniff and a sandbox when a version is downloaded"() {

        given:
        earlierVersionsOnDisk(versionRows, root)

        when:
        MockHttpServletResponse resp = mvc.perform(asAgent(get('/api/solutions/files/versions/5/content'))).andExpect(status().isOk())
            .andReturn().response

        then:
        resp.contentType == MediaType.APPLICATION_OCTET_STREAM_VALUE
        resp.getHeader('Content-Disposition').startsWith('attachment')
        resp.getHeader('Content-Disposition').contains('a-old.txt')
        resp.getHeader('X-Content-Type-Options') == 'nosniff'
        resp.getHeader('Content-Security-Policy').endsWith('; sandbox')      // the site policy, with the sandbox carried through (SecurityConfig)
        resp.contentAsString == 'alpha one'
    }

    @Unroll
    def "versionContent should serve an attachment whatever the version claims to be when #scenario"() {

        given:
        versionRows[5] = fileVersion(5, 1, 1, name, '1/zzzz0000-a-old.txt', 1)
        put(root, '1/zzzz0000-a-old.txt', 'alpha one')

        when:
        MockHttpServletResponse resp = mvc.perform(asUser(get('/api/solutions/files/versions/5/content'))).andExpect(status().isOk())
            .andReturn().response

        then:
        resp.contentType == MediaType.APPLICATION_OCTET_STREAM_VALUE
        resp.getHeader('Content-Disposition').startsWith('attachment')
        !resp.getHeader('Content-Disposition').contains('\r')
        !resp.getHeader('Content-Disposition').contains('\n')
        resp.getHeader('Set-Cookie') == expectedCookie

        where:
        scenario                         | name               || expectedCookie
        'the name is an HTML page'       | 'evil.html'        || null
        'the name is an SVG'             | 'image.svg'        || null
        'the name carries quotes'        | 'a"b".txt'         || null
        'the name is not ASCII'          | 'caf\u00e9.txt'    || null
    }

    @Unroll
    def "#seq. versionContent should answer 404 '#expectedMessage' when #scenario"() {

        given:
        put(tmp, 'outside.txt', 'secret')
        Files.createDirectories(root.resolve('1/folder'))
        if (exists) {
            versionRows[5] = fileVersion(5, 1, 1, 'a-old.txt', stored, solutionId)
        }

        when:
        ResultActions r = mvc.perform(asUser(get('/api/solutions/files/versions/' + versionId + '/content')))

        then:
        r.andExpect(status().isNotFound()).andExpect(jsonPath('$.message').value(expectedMessage))

        where:
        seq | scenario                                | versionId | exists | stored                 | solutionId || expectedMessage
        1   | 'there is no such version'              | 99        | false  | null                   | null       || 'Version not found.'
        2   | 'the version id is zero'                | 0         | false  | null                   | null       || 'Version not found.'
        3   | 'the version has no solution'           | 5         | true   | '1/zzzz0000-a.txt'     | null       || 'Version not found.'
        4   | 'the bytes are missing'                 | 5         | true   | '1/gone.txt'           | 1          || 'Version not found.'
        5   | 'there is no stored name'               | 5         | true   | null                   | 1          || 'Version not found.'
        6   | 'the stored name is a folder'           | 5         | true   | '1/folder'             | 1          || 'Version not found.'
        7   | 'the stored name climbs out'            | 5         | true   | '../outside.txt'       | 1          || 'File not found.'
        8   | 'the stored name climbs out deeper'     | 5         | true   | '1/../../outside.txt'  | 1          || 'File not found.'
        9   | 'the version is of a missing solution'  | 5         | true   | '1/zzzz0000-a.txt'     | 9          || 'Solution not found.'
    }

    def "version endpoints should match nothing when an id is not a number"() {

        when:
        mvc.perform(asUser(get('/api/solutions/files/abc/versions'))).andExpect(status().isNotFound())
        mvc.perform(asUser(get('/api/solutions/files/versions/abc/content'))).andExpect(status().isNotFound())
        mvc.perform(byAny('user', fileRequest('UPLOAD', '/api/solutions/files/1x/versions', null))).andExpect(status().isNotFound())

        then:
        0 * files._
    }
}
