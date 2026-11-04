package com.echo

import com.echo.SolutionModels.FileRow

import spock.lang.PendingFeature
import spock.lang.Unroll

// Story 5653: JdbcSolutionFileRepository against a real EchoTest_ database (stp_SolutionFile_Save / Get /
// ListForSolution / Delete). Story 5695 (Release 5): versions -- stp_SolutionFile_NewVersion,
// stp_SolutionFileVersion_ListForFile and _Get, the versions stp_SolutionFile_Delete takes with it, and the
// audit rows of both tables.
class JdbcSolutionFileRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionFileRepository repo
    int sid
    int pid
    int item

    def setup() {
        repo = new JdbcSolutionFileRepository(dataSource())
        sid = newSolution()
        pid = newProject(sid)
        item = newItem(pid, 'Attach here')
    }

    def "insert should store every field so find reads it back when a file is added"() {

        when:
        int id = repo.insert(sid, item, '  Title  ', 'report.pdf', 'repo', 42, 'abc.pdf', 'application/pdf', 5_000_000_000L, 'notes', 'Rex')
        FileRow f = repo.find(id)

        then:
        f.id() == id
        f.solutionId() == sid
        f.itemId() == item
        f.itemTitle() == 'Attach here'
        f.title() == 'Title'
        f.originalFileName() == 'report.pdf'
        f.storageCode() == 'repo'
        f.leonardoFileId() == 42
        f.storedFileName() == 'abc.pdf'
        f.contentType() == 'application/pdf'
        f.sizeBytes() == 5_000_000_000L
        f.notes() == 'notes'
        f.uploadedByCode() == 'rex'
        f.uploadedByName() == 'Rex'
        f.createdDate() != null
    }

    @Unroll
    def "insert should store title #expectedTitle and file name #expectedName when #scenario"() {

        when:
        FileRow f = repo.find(add(repo, sid, [title: title, original: original, leo: leo]))

        then:
        f.title() == expectedTitle
        f.originalFileName() == expectedName
        f.leonardoFileId() == expectedLeo

        where:
        scenario                               | title | original | leo  || expectedTitle | expectedName | expectedLeo
        "the title is blank"                   | " "   | "x.csv"  | null || "x.csv"       | "x.csv"      | null
        "the title and file name are missing"  | null  | null     | null || "file"        | "file"       | null
        "the Leonardo file id is 0"            | "T"   | "o.txt"  | 0    || "T"           | "o.txt"      | null
    }

    @Unroll
    def "insert should accept the storage code when it is #scenario"() {

        expect:
        repo.find(add(repo, sid, [storage: storage])).storageCode() == storage

        where:
        scenario  | storage || _
        "repo"    | "repo"  || _
        "nook"    | "nook"  || _
        "local"   | "local" || _
    }

    @Unroll
    def "insert should refuse when #scenario"() {

        given:
        int foreignItem = newItem(newProject(newSolution()))
        int solution = target == 'missing-solution' ? -1 : sid
        Integer itemId = target == 'foreign-item' ? foreignItem : null

        when:
        add(repo, solution, [storage: storage, item: itemId])

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)

        where:
        scenario                               | storage | target             || message
        "the storage code is unknown"          | "cloud" | "none"             || "stored in the repo"
        "the solution does not exist"          | "local" | "missing-solution" || "solution no longer exists"
        "the item is from another solution"    | "local" | "foreign-item"     || "item does not belong"
    }

    def "listForSolution should list newest first and filter by item when files are attached"() {

        given:
        int a = add(repo, sid, [:])
        int b = add(repo, sid, [item: item])
        int c = add(repo, sid, [:])
        add(repo, newSolution(), [:])

        expect:
        repo.listForSolution(sid, null)*.id() == [c, b, a]
        repo.listForSolution(sid, item)*.id() == [b]
        repo.listForSolution(sid, 0)*.id() == [c, b, a]
    }

    def "update should change title, notes and item and leave the stored file when a file is edited"() {

        given:
        int id = add(repo, sid, [title: 'Old', notes: 'n1'])
        FileRow before = repo.find(id)

        when:
        boolean ok = repo.update(id, item, 'New', 'n2')
        FileRow f = repo.find(id)

        then:
        ok
        f.title() == 'New'
        f.notes() == 'n2'
        f.itemId() == item
        f.storedFileName() == before.storedFileName()
        f.sizeBytes() == before.sizeBytes()
        f.storageCode() == before.storageCode()
        f.modifiedDate() != null

        when:
        repo.update(id, null, '  ', null)
        f = repo.find(id)

        then:
        f.title() == 'New'
        f.notes() == null
        f.itemId() == null
    }

    @Unroll
    def "update should refuse when #scenario"() {

        given:
        int id = add(repo, sid, [:])
        int foreignItem = newItem(newProject(newSolution()))
        int fileId = unknownFile ? 987654321 : id
        Integer itemId = foreign ? foreignItem : null

        when:
        repo.update(fileId, itemId, 'x', null)

        then:
        SolutionRuleException ex = thrown()
        ex.message.contains(message)

        where:
        scenario                               | unknownFile | foreign || message
        "the file does not exist"              | true        | false   || "solution no longer exists"
        "the item is from another solution"    | false       | true    || "item does not belong"
    }

    def "delete should remove the row and its filing, audit both and answer false the second time"() {

        given:
        int id = add(repo, sid, [:])
        sql.queryForList("EXEC dbo.stp_SolutionFileFiling_SetImportant @prmSolutionID=?, @prmSolutionFileID=?, @prmIsImportant=1, @prmModifiedBy='tony'", sid, id)
        assert count('SELECT COUNT(*) FROM dbo.SolutionFileFiling WHERE SolutionFileID = ?', id) == 1

        when:
        boolean gone = repo.delete(id)

        then:
        gone
        repo.find(id) == null
        count('SELECT COUNT(*) FROM dbo.SolutionFileFiling WHERE SolutionFileID = ?', id) == 0
        logRows('SolutionFile', 'SolutionFileID', id).last().DeletedDate != null
        count('SELECT COUNT(*) FROM dbo.SolutionFileFiling_LOG WHERE SolutionFileID = ? AND DeletedDate IS NOT NULL', id) == 1
        !repo.delete(id)
    }

    def "find should return null when the id is unknown"() {

        expect:
        repo.find(-1) == null
    }

    // ---- Story 5695 (Release 5): versions (stp_SolutionFile_NewVersion, stp_SolutionFileVersion_ListForFile / _Get) ----

    /** The file's own row, with the number its current bytes are. */
    private static Map<String, Object> fileRowOf(EchoDbITSpec db, int id) {
        db.row('SELECT * FROM dbo.SolutionFile WHERE SolutionFileID = ?', id)
    }

    def "newVersion should keep what the file had as version 1, give the file the new bytes and answer 2"() {

        given:
        int id = repo.insert(sid, item, 'Charset sheet', 'charset.xlsx', 'local', null, sid + '/aaaa1111-charset.xlsx',
                             'application/vnd.ms-excel', 18233L, 'notes', 'rex')
        Map<String, Object> before = fileRowOf(this, id)

        when:
        int number = repo.newVersion(id, 'charset-v2.xlsx', sid + '/bbbb2222-charset-v2.xlsx', 'text/csv', 20000L,
                                     '  QA numbers added  ', 'Tony')
        FileRow f = repo.find(id)
        List<SolutionFileVersionModels.FileVersion> earlier = repo.versions(id)

        then:
        number == 2
        fileRowOf(this, id).CurrentVersionNumber == 2
        f.originalFileName() == 'charset-v2.xlsx'
        f.storedFileName() == sid + '/bbbb2222-charset-v2.xlsx'
        f.contentType() == 'text/csv'
        f.sizeBytes() == 20000L
        f.uploadedByCode() == 'tony'
        f.modifiedDate() != null
        earlier.size() == 1
        earlier[0].id() > 0
        earlier[0].fileId() == id
        earlier[0].versionNumber() == 1
        earlier[0].originalFileName() == 'charset.xlsx'
        earlier[0].storedFileName() == sid + '/aaaa1111-charset.xlsx'
        earlier[0].contentType() == 'application/vnd.ms-excel'
        earlier[0].sizeBytes() == 18233L
        earlier[0].changeNote() == 'QA numbers added'
        earlier[0].uploadedByCode() == 'rex'
        earlier[0].uploadedByName() == 'Rex'
        earlier[0].uploadedDate() == SolutionRows.dateOf(before, 'CreatedDate')
        earlier[0].solutionId() == null
    }

    def "newVersion should leave the file's title, item, notes, storage, filing and links as they were"() {

        given:
        int id = add(repo, sid, [title: 'Keep me', item: item, notes: 'n1'])
        sql.queryForList("EXEC dbo.stp_SolutionFileFiling_SetImportant @prmSolutionID=?, @prmSolutionFileID=?, @prmIsImportant=1, @prmModifiedBy='tony'", sid, id)
        new JdbcSolutionLinkRepository(dataSource()).link(sid, 'file', id, 'item', item, 'tony')
        FileRow before = repo.find(id)

        when:
        repo.newVersion(id, 'v2.txt', sid + '/cccc3333-v2.txt', 'text/plain', 3L, null, 'tony')
        FileRow f = repo.find(id)

        then:
        f.id() == id
        f.title() == before.title()
        f.itemId() == item
        f.notes() == 'n1'
        f.storageCode() == before.storageCode()
        count('SELECT COUNT(*) FROM dbo.SolutionFileFiling WHERE SolutionFileID = ?', id) == 1
        count("SELECT COUNT(*) FROM dbo.SolutionLink WHERE (FromKind = 'file' AND FromID = ?) OR (ToKind = 'file' AND ToID = ?)", id, id) == 1
    }

    def "versions should list the earlier versions newest first, each with the note of the version after it"() {

        given:
        int id = add(repo, sid, [original: 'v1.txt'])
        repo.newVersion(id, 'v2.txt', sid + '/v2.txt', 'text/plain', 2L, 'second', 'tony')
        int third = repo.newVersion(id, 'v3.txt', sid + '/v3.txt', 'text/plain', 3L, 'third', 'tony')

        when:
        List<SolutionFileVersionModels.FileVersion> earlier = repo.versions(id)

        then:
        third == 3
        fileRowOf(this, id).CurrentVersionNumber == 3
        repo.find(id).originalFileName() == 'v3.txt'
        earlier*.versionNumber() == [2, 1]
        earlier*.originalFileName() == ['v2.txt', 'v1.txt']
        earlier*.changeNote() == ['third', 'second']
        earlier*.uploadedByCode() == ['tony', 'rex']
    }

    @Unroll
    def "newVersion should store the note as #expectedNote when it is #scenario"() {

        given:
        int id = add(repo, sid, [:])

        when:
        repo.newVersion(id, 'v2.txt', sid + '/v2.txt', 'text/plain', 2L, note, 'tony')

        then:
        repo.versions(id)*.changeNote() == [expectedNote]

        where:
        scenario    | note          || expectedNote
        'null'      | null          || null
        'empty'     | ''            || null
        'blank'     | '   '         || null
        'padded'    | '  Fixed  '   || 'Fixed'
    }

    def "version should read one earlier version with the solution it belongs to"() {

        given:
        int id = add(repo, sid, [original: 'v1.txt'])
        repo.newVersion(id, 'v2.txt', sid + '/v2.txt', 'text/plain', 2L, 'second', 'tony')
        int versionId = repo.versions(id)[0].id()

        when:
        SolutionFileVersionModels.FileVersion v = repo.version(versionId)

        then:
        v.id() == versionId
        v.fileId() == id
        v.versionNumber() == 1
        v.originalFileName() == 'v1.txt'
        v.changeNote() == 'second'
        v.uploadedByCode() == 'rex'
        v.solutionId() == sid
    }

    def "version and versions should answer nothing when there is nothing"() {

        expect:
        repo.version(-1) == null
        repo.version(987654321) == null
        repo.versions(add(repo, sid, [:])) == []
        repo.versions(987654321) == []
    }

    @Unroll
    def "newVersion should refuse and write nothing when #scenario"() {

        given:
        int id = add(repo, sid, [:])
        int fileId = missingFile ? 987654321 : id
        Map<String, Object> before = fileRowOf(this, id)

        when:
        repo.newVersion(fileId, 'v2.txt', stored, 'text/plain', 2L, null, 'tony')

        then:
        SolutionRuleException ex = thrown()
        ex.message == expectedMessage
        fileRowOf(this, id) == before
        count('SELECT COUNT(*) FROM dbo.SolutionFileVersion WHERE SolutionFileID = ?', id) == 0

        where:
        scenario                          | missingFile | stored       || expectedMessage
        'the file does not exist'         | true        | 'x/v2.txt'   || 'That file no longer exists. Nothing was saved.'
        'there is no stored file'         | false       | null         || 'The new version has no stored file. Nothing was saved.'
        'the stored file name is blank'   | false       | '   '        || 'The new version has no stored file. Nothing was saved.'
    }

    def "newVersion should write audit rows for the version and carry the version number into SolutionFile_LOG"() {

        given:
        int id = add(repo, sid, [:])

        when:
        repo.newVersion(id, 'v2.txt', sid + '/v2.txt', 'text/plain', 2L, null, 'tony')
        int versionId = repo.versions(id)[0].id()

        then:
        logRows('SolutionFile', 'SolutionFileID', id).first().CurrentVersionNumber == 1
        logRows('SolutionFile', 'SolutionFileID', id).last().CurrentVersionNumber == 2
        logRows('SolutionFile', 'SolutionFileID', id).last().OriginalFileName == 'v2.txt'
        logRows('SolutionFileVersion', 'SolutionFileVersionID', versionId)*.IsInsertion == [true]
        logRows('SolutionFileVersion', 'SolutionFileVersionID', versionId).first().VersionNumber == 1

        when:
        sql.update('UPDATE dbo.SolutionFileVersion SET ChangeNote = ? WHERE SolutionFileVersionID = ?', 'edited', versionId)

        then:
        logRows('SolutionFileVersion', 'SolutionFileVersionID', versionId)*.IsInsertion == [true, false]
        logRows('SolutionFileVersion', 'SolutionFileVersionID', versionId).last().ChangeNote == 'edited'
        logRows('SolutionFileVersion', 'SolutionFileVersionID', versionId).last().DeletedDate == null
    }

    def "delete should take the file's earlier versions with it and audit them"() {

        given:
        int id = add(repo, sid, [:])
        int other = add(repo, sid, [:])
        repo.newVersion(id, 'v2.txt', sid + '/v2.txt', 'text/plain', 2L, null, 'tony')
        repo.newVersion(other, 'o2.txt', sid + '/o2.txt', 'text/plain', 2L, null, 'tony')
        int versionId = repo.versions(id)[0].id()

        when:
        boolean gone = repo.delete(id)

        then:
        gone
        count('SELECT COUNT(*) FROM dbo.SolutionFileVersion WHERE SolutionFileID = ?', id) == 0
        repo.version(versionId) == null
        logRows('SolutionFileVersion', 'SolutionFileVersionID', versionId).last().DeletedDate != null
        logRows('SolutionFile', 'SolutionFileID', id).last().CurrentVersionNumber == 2
        repo.versions(other).size() == 1
    }

    def "newVersion should date the earlier version by when its bytes were uploaded even after the title was edited"() {

        given:
        int id = add(repo, sid, [:])
        sql.update("UPDATE dbo.SolutionFile SET CreatedDate = '2026-01-15T09:00:00' WHERE SolutionFileID = ?", id)
        repo.update(id, null, 'Retitled', null)

        when:
        repo.newVersion(id, 'v2.txt', sid + '/v2.txt', 'text/plain', 2L, null, 'tony')

        then:
        repo.versions(id)[0].uploadedDate() == getLocalDateTime('01/15/2026 09:00')
    }

    private static int add(JdbcSolutionFileRepository repo, int solutionId, Map m) {
        repo.insert(solutionId, m.item as Integer, (m.containsKey('title') ? m.title : 'File ' + uniq()) as String,
            (m.containsKey('original') ? m.original : 'orig.txt') as String, (m.storage ?: 'local') as String,
            m.leo as Integer, 'stored-' + uniq() + '.txt', 'text/plain', 1234L, m.notes as String, 'rex')
    }
}
