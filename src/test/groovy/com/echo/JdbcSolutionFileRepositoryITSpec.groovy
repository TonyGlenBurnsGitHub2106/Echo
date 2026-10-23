package com.echo

import com.echo.SolutionModels.FileRow

import spock.lang.Unroll

// Story 5653: JdbcSolutionFileRepository against a real EchoTest_ database (stp_SolutionFile_Save / Get /
// ListForSolution / Delete).
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

    private static int add(JdbcSolutionFileRepository repo, int solutionId, Map m) {
        repo.insert(solutionId, m.item as Integer, (m.containsKey('title') ? m.title : 'File ' + uniq()) as String,
            (m.containsKey('original') ? m.original : 'orig.txt') as String, (m.storage ?: 'local') as String,
            m.leo as Integer, 'stored-' + uniq() + '.txt', 'text/plain', 1234L, m.notes as String, 'rex')
    }
}
