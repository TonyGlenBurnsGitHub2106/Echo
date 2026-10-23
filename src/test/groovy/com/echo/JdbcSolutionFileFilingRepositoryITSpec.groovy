package com.echo

import java.time.LocalDate

import org.springframework.jdbc.core.JdbcTemplate

import spock.lang.Unroll

// Story 5653: JdbcSolutionFileFilingRepository (stp_SolutionFileFiling_*, stp_SolutionFile_ListFiled / CountsFiled,
// vw_SolutionFileFiled) and FileFilingRepository (stp_FileCategory_*) against a real EchoTest_ database.
class JdbcSolutionFileFilingRepositoryITSpec extends EchoDbITSpec {

    JdbcSolutionFileFilingRepository repo
    FileFilingRepository categories
    int sid

    def setup() {
        repo = new JdbcSolutionFileFilingRepository(new JdbcTemplate(dataSource()))
        categories = new FileFilingRepository(dataSource())
        sid = newSolution()
    }

    // ---- categories ----

    def "saveCategory should store, list with its use count, rename and refuse a blank or duplicate name when categories are saved"() {

        given:
        String name = 'IT Cat ' + uniq()

        when:
        int id = category(categories, name, '#ABCDEF', null)
        Map<String, Object> listed = categories.listCategories().find { it.FileCategoryID == id }

        then:
        listed.CategoryName == name
        listed.ColorHex == '#ABCDEF'
        listed.UseCount == 0
        (listed.SortOrder as int) > 0

        when:
        Map<String, Object> renamed = categories.saveCategory(id, 'Renamed ' + name, null, 5)

        then:
        renamed.CategoryName == 'Renamed ' + name
        renamed.ColorHex == ''
        renamed.SortOrder == 5

        when:
        categories.saveCategory(0, 'Renamed ' + name, null, null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains('already a category')

        when:
        categories.saveCategory(0, '  ', null, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "findOrCreateCategory should find an existing name and create a new one 10 after the last when the name is new"() {

        given:
        String name = 'IT Find ' + uniq()
        int max = count('SELECT ISNULL(MAX(SortOrder), 0) FROM dbo.FileCategory')

        when:
        Map<String, Object> made = categories.findOrCreateCategory(name)
        Map<String, Object> found = categories.findOrCreateCategory('  ' + name + ' ')

        then:
        made.WasCreated == true
        made.SortOrder == max + 10
        found.WasCreated == false
        found.FileCategoryID == made.FileCategoryID

        when:
        categories.findOrCreateCategory(' ')

        then:
        thrown(IllegalArgumentException)
    }

    def "reorderCategories should put the listed categories first 10 apart and the rest after when two are listed"() {

        given:
        int a = category(categories, 'zz IT A ' + uniq(), null, null)
        int b = category(categories, 'zz IT B ' + uniq(), null, null)

        when:
        List<Map<String, Object>> list = categories.reorderCategories([b, a])

        then:
        list[0].FileCategoryID == b
        list[0].SortOrder == 10
        list[1].FileCategoryID == a
        list[1].SortOrder == 20
        list*.SortOrder == list*.SortOrder.sort(false)
    }

    def "deleteCategory should send its files back to Staging and report how many when the category is in use"() {

        given:
        int cat = category(categories, 'IT Cat ' + uniq(), null, null)
        int f1 = newFile(sid, 'a.pdf', null, 10L)
        int f2 = newFile(sid, 'b.pdf', null, 10L)
        repo.fileMany(sid, [f1, f2], cat, 'tony')

        expect:
        categories.listCategories().find { it.FileCategoryID == cat }.UseCount == 2

        when:
        int returned = categories.deleteCategory(cat)

        then:
        returned == 2
        categories.listCategories().every { it.FileCategoryID != cat }
        rows('SELECT FileCategoryID, FiledDate FROM dbo.SolutionFileFiling WHERE SolutionFileID IN (?, ?)', f1, f2)
            .every { it.FileCategoryID == null && it.FiledDate == null }
        logRows('FileCategory', 'FileCategoryID', cat).last().DeletedDate != null
        categories.deleteCategory(cat) == 0
    }

    // ---- filing ----

    def "setFiling should file a document with a category, date and importance and keep the filed date when re-saved"() {

        given:
        int f = newFile(sid, 'scan.pdf', null, 10L)
        int cat = category(categories, 'IT Cat ' + uniq(), null, null)
        LocalDate documentDate = getLocalDate('04/05/2025')

        when:
        Map<String, Object> r = repo.setFiling(sid, f, cat, documentDate, true, 'tony')

        then:
        r.SolutionFileID == f
        r.FileCategoryID == cat
        r.DocumentDate.toString() == '2025-04-05'
        r.IsImportant == true
        r.FiledDate != null

        when:
        Map<String, Object> again = repo.setFiling(sid, f, cat, null, false, 'tony')
        Map<String, Object> staged = repo.setFiling(sid, f, 0, null, false, 'tony')

        then:
        again.FiledDate == r.FiledDate
        again.DocumentDate == null
        again.IsImportant == false
        staged.FileCategoryID == null
        staged.FiledDate == null
        count('SELECT COUNT(*) FROM dbo.SolutionFileFiling WHERE SolutionFileID = ?', f) == 1
        logRows('SolutionFileFiling', 'SolutionFileID', f).size() == 3
    }

    def "setFiling should return nothing and write nothing when the file belongs to another solution"() {

        given:
        int foreign = newFile(newSolution(), 'x.pdf', null, 10L)

        expect:
        repo.setFiling(sid, foreign, null, null, true, 'tony') == null
        count('SELECT COUNT(*) FROM dbo.SolutionFileFiling WHERE SolutionFileID = ?', foreign) == 0
    }

    def "setFiling should refuse with Nothing was saved when the category does not exist"() {

        given:
        int f = newFile(sid, 'x.pdf', null, 10L)

        when:
        repo.setFiling(sid, f, 987654321, null, false, 'tony')

        then:
        IllegalArgumentException ex = thrown()
        ex.message.endsWith('Nothing was saved.')
        count('SELECT COUNT(*) FROM dbo.SolutionFileFiling WHERE SolutionFileID = ?', f) == 0
    }

    def "setImportant should create or update the filing row and answer false when the file belongs to another solution"() {

        given:
        int f = newFile(sid, 'x.pdf', null, 10L)
        int foreign = newFile(newSolution(), 'y.pdf', null, 10L)

        expect:
        repo.setImportant(sid, f, true, 'tony')
        scalar('SELECT IsImportant FROM dbo.SolutionFileFiling WHERE SolutionFileID = ?', f) == true
        repo.setImportant(sid, f, false, 'tony')
        scalar('SELECT IsImportant FROM dbo.SolutionFileFiling WHERE SolutionFileID = ?', f) == false
        !repo.setImportant(sid, foreign, true, 'tony')
        count('SELECT COUNT(*) FROM dbo.SolutionFileFiling WHERE SolutionFileID = ?', foreign) == 0
    }

    def "fileMany should file only this solution's files, skip junk ids and refuse an unknown category when given a mixed list"() {

        given:
        int a = newFile(sid, 'a.pdf', null, 10L)
        int b = newFile(sid, 'b.pdf', null, 10L)
        int foreign = newFile(newSolution(), 'c.pdf', null, 10L)
        int cat = category(categories, 'IT Cat ' + uniq(), null, null)

        when:
        int n = repo.fileMany(sid, [a, b, foreign, a, -4], cat, 'tony')

        then:
        n == 2
        count('SELECT COUNT(*) FROM dbo.SolutionFileFiling WHERE FileCategoryID = ? AND SolutionFileID IN (?, ?)', cat, a, b) == 2
        count('SELECT COUNT(*) FROM dbo.SolutionFileFiling WHERE SolutionFileID = ?', foreign) == 0

        when:
        int back = repo.fileMany(sid, [a], null, 'tony')

        then:
        back == 1
        scalar('SELECT FileCategoryID FROM dbo.SolutionFileFiling WHERE SolutionFileID = ?', a) == null

        when:
        repo.fileMany(sid, [a], 987654321, 'tony')

        then:
        IllegalArgumentException ex = thrown()
        ex.message.endsWith('Nothing was saved.')

        expect:
        repo.fileMany(sid, [], cat, 'tony') == 0
        repo.fileMany(sid, null, cat, 'tony') == 0
    }

    // ---- the filed list and its counts ----

    def "listFiled should put Staging first and important files first inside a group when grouped by category"() {

        given:
        int cat = category(categories, 'IT Group ' + uniq(), '#00FF00', 1)
        int staged = newFile(sid, 'staged.pdf', 'Staged', 10L)
        int plain = newFile(sid, 'plain.pdf', 'Plain', 10L)
        int star = newFile(sid, 'star.pdf', 'Star', 10L)
        repo.fileMany(sid, [plain, star], cat, 'tony')
        repo.setImportant(sid, star, true, 'tony')

        when:
        List<Map<String, Object>> rows = repo.listFiled(sid, 'category', 'name', false, null, 0, 50)

        then:
        rows*.SolutionFileID == [staged, star, plain]
        rows[0].GroupKey == 'staging'
        rows[0].GroupLabel == 'Staging'
        rows[0].IsStaged == true
        rows[1].GroupKey == 'c:' + cat
        rows[1].GroupColor == '#00FF00'
        ((String) rows[1].CategoryName).startsWith('IT Group')
    }

    def "listFiled should group by type family, by year and not at all when asked"() {

        given:
        ['a.pdf', 'b.PNG', 'c.docx', 'd.zip', 'noext'].each { String n -> newFile(sid, n, null, 10L) }

        expect:
        repo.listFiled(sid, 'type', 'added', true, null, 0, 50)*.GroupLabel.unique() == ['PDF', 'Images', 'Documents', 'Other']
        repo.listFiled(sid, 'type', 'added', true, null, 0, 50).find { it.OriginalFileName == 'b.PNG' }.Ext == 'PNG'
        repo.listFiled(sid, 'year', 'added', true, null, 0, 50)*.GroupLabel.unique() == [String.valueOf(LocalDate.now().year)]
        repo.listFiled(sid, 'none', 'added', true, null, 0, 50)*.GroupKey.unique() == ['all']
    }

    @Unroll
    def "listFiled should return #expected when #scenario"() {

        given:
        newFile(sid, 'b.txt', 'Bravo', 30L)
        newFile(sid, 'a.pdf', 'Alpha', 20L)
        newFile(sid, 'c.csv', 'Charlie', 10L)

        when:
        List<Object> got = repo.listFiled(sid, 'none', sortKey, desc, null, offset, size)*.getAt(column)

        then:
        got.join(',') == expected

        where:
        scenario                                  | sortKey | desc  | offset | size | column      || expected
        "sorted by name ascending"                | "name"  | false | 0      | 50   | "FileTitle" || "Alpha,Bravo,Charlie"
        "sorted by name descending"               | "name"  | true  | 0      | 50   | "FileTitle" || "Charlie,Bravo,Alpha"
        "sorted by size ascending"                | "size"  | false | 0      | 50   | "FileTitle" || "Charlie,Alpha,Bravo"
        "sorted by type ascending"                | "type"  | false | 0      | 50   | "Ext"       || "CSV,PDF,TXT"
        "the second row alone is asked for"       | "name"  | false | 1      | 1    | "FileTitle" || "Bravo"
        "the offset is negative and size is zero" | "name"  | false | -3     | 0    | "FileTitle" || "Alpha,Bravo,Charlie"
    }

    def "listFiled should sort by document date and fall back to the upload date when only one file has a document date"() {

        given:
        int old = newFile(sid, 'old.pdf', 'Old', 10L)
        int recent = newFile(sid, 'recent.pdf', 'Recent', 10L)
        repo.setFiling(sid, old, null, getLocalDate('01/01/2001'), false, 'tony')

        expect:
        repo.listFiled(sid, 'none', 'docdate', false, null, 0, 50)*.SolutionFileID == [old, recent]
        repo.listFiled(sid, 'none', 'docdate', true, null, 0, 50)*.SolutionFileID == [recent, old]
        repo.listFiled(sid, 'year', 'added', true, null, 0, 50).find { it.SolutionFileID == old }.GroupLabel == '2001'
    }

    def "listFiled should match title, file name, category, item title or an exact extension when searching"() {

        given:
        int cat = category(categories, 'Taxes ' + uniq(), null, null)
        int byTitle = newFile(sid, 'x1.bin', 'Invoice March', 10L)
        int byName = newFile(sid, 'invoice-april.bin', 'Other', 10L)
        int byCategory = newFile(sid, 'x3.bin', 'Third', 10L)
        int byExt = newFile(sid, 'x4.xlsx', 'Fourth', 10L)
        newFile(sid, 'x5.bin', 'Nothing', 10L)
        repo.fileMany(sid, [byCategory], cat, 'tony')

        expect:
        repo.listFiled(sid, 'none', 'name', false, 'invoice', 0, 50)*.SolutionFileID as Set == [byTitle, byName] as Set
        repo.listFiled(sid, 'none', 'name', false, 'Taxes', 0, 50)*.SolutionFileID == [byCategory]
        repo.listFiled(sid, 'none', 'name', false, 'XLSX', 0, 50)*.SolutionFileID == [byExt]
        repo.listFiled(sid, 'none', 'name', false, '   ', 0, 50).size() == 5
        repo.listFiled(newSolution(), 'none', 'name', false, null, 0, 50).isEmpty()
    }

    @Unroll
    def "countsFiled should agree with listFiled when #scenario"() {

        given:
        int cat = category(categories, 'IT Cat ' + uniq(), null, null)
        int a = newFile(sid, 'a.pdf', null, 10L)
        newFile(sid, 'b.pdf', null, 10L)
        newFile(sid, 'c.png', null, 10L)
        repo.fileMany(sid, [a], cat, 'tony')

        when:
        Map<Object, Object> counts = repo.countsFiled(sid, groupBy, null).collectEntries { [(it.GroupKey): it.Cnt] }
        Map<Object, Integer> listed = repo.listFiled(sid, groupBy, 'added', true, null, 0, 500).countBy { it.GroupKey }

        then:
        counts == listed

        where:
        scenario                     | groupBy    || _
        "grouped by category"        | "category" || _
        "grouped by type"            | "type"     || _
        "grouped by year"            | "year"     || _
        "not grouped"                | "none"     || _
    }

    def "countsFiled should count the staged files and filter by search when files are filed and staged"() {

        given:
        int cat = category(categories, 'IT Cat ' + uniq(), null, null)
        int a = newFile(sid, 'a.pdf', null, 10L)
        newFile(sid, 'b.pdf', null, 10L)
        newFile(sid, 'c.png', null, 10L)
        repo.fileMany(sid, [a], cat, 'tony')

        expect:
        repo.countsFiled(sid, 'category', null).find { it.GroupKey == 'staging' }.StagedCnt == 2
        repo.countsFiled(sid, 'category', null).find { it.GroupKey == 'c:' + cat }.StagedCnt == 0
        repo.countsFiled(sid, 'none', 'png')[0].Cnt == 1
    }


    private static int category(FileFilingRepository categories, String name, String color, Integer sort) {
        ((Number) categories.saveCategory(0, name, color, sort).FileCategoryID).intValue()
    }
}
