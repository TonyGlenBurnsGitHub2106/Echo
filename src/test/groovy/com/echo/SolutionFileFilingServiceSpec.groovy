package com.echo

import java.time.LocalDate
import java.time.LocalDateTime

import spock.lang.PendingFeature
import spock.lang.Unroll

/**
 * Story 5653: the organized Files tab of a solution -- grouping, sorting, paging and searching handed to SQL
 * with every input normalized first; the row and counter shapes the page reads; category, document date
 * and Important. House-only: the controller decides who may call; this service trusts its caller.
 */
class SolutionFileFilingServiceSpec extends EchoSpockBaseSpec {

    SolutionFileFilingRepository repo = Mock()
    FileFilingService categories = Mock()
    SolutionFileFilingService service = new SolutionFileFilingService(repo, categories)

    private static Map<String, Object> fileRow(int id) {
        [SolutionFileID: id, SolutionID: 1, SolutionProjectItemID: null, ItemTitle: null, FileTitle: 'Title ' + id,
         OriginalFileName: 'a.pdf', StorageCode: 'local', ContentType: 'application/pdf', FileSizeBytes: 1234L, NotesPreview: null,
         UploadedByCode: 'tony', UploadedByName: 'Tony', CreatedDate: '2026-10-01 09:30:00', FileCategoryID: 4, CategoryName: 'Bills',
         CategoryColor: null, IsStaged: 0, DocumentDate: '2026-09-15 00:00:00.0', IsImportant: true, Ext: null, TypeFamily: 'pdf',
         GroupKey: 'c4', GroupLabel: 'Bills', GroupColor: null]
    }

    private static List<Map<String, Object>> fileRows(int count) {
        (1..count).collect { int i -> fileRow(i) }
    }

    // ---- the category ----------------------------------------------------------------

    @Unroll
    def "resolveCategory should hand the request to the shared category list when #scenario"() {

        when:
        Integer id = service.resolveCategory(categoryId, categoryName, staging)

        then:
        1 * categories.resolveCategory(categoryId, categoryName, staging) >> answer
        id == answer

        where:
        scenario                     | categoryId | categoryName | staging || answer
        'an id is sent'              | 4          | null         | null    || 4
        'a name is sent'             | null       | 'Taxes'      | null    || 9
        'staging is asked'           | null       | null         | true    || null
    }

    // ---- the list -----------------------------------------------------------------------

    @Unroll
    def "list should ask SQL for group #expectedGroup, sort #expectedSort, search #expectedSearch, size #expectedSize when #scenario"() {

        when:
        Map<String, Object> out = service.list(1, group, sort, false, search, 5, size)

        then:
        1 * repo.listFiled(1, expectedGroup, expectedSort, false, expectedSearch, 5, expectedSize + 1) >> []
        out.groupBy == expectedGroup
        out.sort == expectedSort

        where:
        scenario                         | group       | sort       | search    | size || expectedGroup | expectedSort | expectedSearch | expectedSize
        'everything is left out'         | null        | null       | null      | 0    || 'category'    | 'added'      | null           | 50
        'group and sort are padded caps' | ' TYPE '    | ' Name '   | ' bill '  | 10   || 'type'        | 'name'       | 'bill'         | 10
        'group by year, sort by date'    | 'year'      | 'docdate'  | ''        | 1    || 'year'        | 'docdate'    | null           | 1
        'no grouping, sort by size'      | 'none'      | 'size'     | '   '     | 500  || 'none'        | 'size'       | null           | 500
        'sort by type'                   | 'category'  | 'type'     | 'x'       | 501  || 'category'    | 'type'       | 'x'            | 50
        'unknown group and sort'         | 'owner'     | 'random'   | 'y'       | -3   || 'category'    | 'added'      | 'y'            | 50
        'an injection is tried'          | 'x;drop'    | 'name desc' | 'z'      | 20   || 'category'    | 'added'      | 'z'            | 20
    }

    @Unroll
    def "list should start at #expectedOffset and include counts #expectedCounts when #scenario"() {

        when:
        Map<String, Object> out = service.list(1, null, null, true, null, offset, 10)

        then:
        1 * repo.listFiled(1, 'category', 'added', true, null, expectedOffset, 11) >> []
        countCalls * repo.countsFiled(1, 'category', null) >> []
        out.offset == expectedOffset
        out.containsKey('groups') == expectedCounts
        out.desc == true

        where:
        scenario                    | offset || expectedOffset | expectedCounts | countCalls
        'the first page'            | 0      || 0              | true           | 1
        'a negative offset'         | -20    || 0              | true           | 1
        'a later page'              | 10     || 10             | false          | 0
    }

    @Unroll
    def "list should answer #expectedRows rows and hasMore #expectedMore when SQL returns #scenario"() {

        when:
        Map<String, Object> out = service.list(1, null, null, true, null, 3, 3)

        then:
        1 * repo.listFiled(1, 'category', 'added', true, null, 3, 4) >> fileRows(returned)
        ((List) out.rows).size() == expectedRows
        out.hasMore == expectedMore

        where:
        scenario               | returned || expectedRows | expectedMore
        'fewer than a page'    | 2        || 2            | false
        'exactly a page'       | 3        || 3            | false
        'one more than a page' | 4        || 3            | true
    }

    def "list should shape every row for the page when SQL returns one"() {

        when:
        Map<String, Object> out = service.list(1, null, null, true, null, 1, 50)
        Map<String, Object> r = ((List<Map<String, Object>>) out.rows)[0]

        then:
        1 * repo.listFiled(*_) >> [fileRow(7)]
        r.id == 7
        r.solutionId == 1
        r.itemId == null
        r.title == 'Title 7'
        r.originalFileName == 'a.pdf'
        r.storageCode == 'local'
        r.contentType == 'application/pdf'
        r.sizeBytes == 1234L
        r.notesPreview == ''
        r.uploadedByCode == 'tony'
        r.createdDate == '2026-10-01T09:30:00'
        r.categoryId == 4
        r.categoryName == 'Bills'
        r.categoryColor == ''
        r.staged == false
        r.documentDate == '2026-09-15'
        r.important == true
        r.ext == ''
        r.typeFamily == 'pdf'
        r.groupKey == 'c4'
        r.groupColor == ''
        (out.keySet() as List) == ['groupBy', 'sort', 'desc', 'offset', 'rows', 'hasMore']
    }

    @Unroll
    def "list should read the row values as #scenario"() {

        given:
        Map<String, Object> row = fileRow(1)
        row.FileSizeBytes = size
        row.IsStaged = staged
        row.IsImportant = important
        row.DocumentDate = documentDate
        row.CreatedDate = created

        when:
        Map<String, Object> r = ((List<Map<String, Object>>) service.list(1, null, null, true, null, 1, 50).rows)[0]

        then:
        1 * repo.listFiled(*_) >> [row]
        r.sizeBytes == expectedSize
        r.staged == expectedStaged
        r.important == expectedImportant
        r.documentDate == expectedDate
        r.createdDate == expectedCreated

        where:
        scenario                              | size   | staged | important | documentDate | created || expectedSize | expectedStaged | expectedImportant | expectedDate | expectedCreated
        'nulls everywhere'                    | null   | null   | null      | null         | null    || 0L           | false          | false             | null         | null
        'an int size and number flags'        | 99     | 1      | 0         | '2026-01-02' | 'x y'   || 99L          | true           | false             | '2026-01-02' | 'xTy'
        'a text size and text flags'          | '99'   | 'TRUE' | 'yes'     | '2026'       | 'a'     || 0L           | true           | false             | '2026'       | 'a'
    }

    @Unroll
    def "list should answer a date-only document date when the database sends a #scenario"() {

        given:
        Map<String, Object> row = fileRow(1)
        row.DocumentDate = documentDate == null ? LocalDate.of(2026, 9, 15) : LocalDateTime.of(2026, 9, 15, 13, 45)

        when:
        Map<String, Object> r = ((List<Map<String, Object>>) service.list(1, null, null, true, null, 1, 50).rows)[0]

        then:
        1 * repo.listFiled(*_) >> [row]
        r.documentDate == expected

        where:
        scenario          | documentDate || expected
        'LocalDate'       | null         || '2026-09-15'
        'LocalDateTime'   | 'datetime'   || '2026-09-15'
    }

    // ---- the counters -------------------------------------------------------------------

    def "counts should total the groups and shape each band when SQL returns rows"() {

        when:
        Map<String, Object> out = service.counts(1, 'TYPE', 'q')

        then:
        1 * repo.countsFiled(1, 'type', 'q') >> [
            [GroupKey: 'pdf', GroupLabel: 'PDF', GroupColor: null, FileCategoryID: null, Cnt: 3, StagedCnt: 1],
            [GroupKey: 'img', GroupLabel: 'Images', GroupColor: '#FF0000', FileCategoryID: 2, Cnt: 2L, StagedCnt: 'many']]
        out.total == 5
        out.staged == 1
        ((List<Map<String, Object>>) out.groups)*.key == ['pdf', 'img']
        ((List<Map<String, Object>>) out.groups)[0].color == ''
        ((List<Map<String, Object>>) out.groups)[1].color == '#FF0000'
        ((List<Map<String, Object>>) out.groups)[1].categoryId == 2
        ((List<Map<String, Object>>) out.groups)[1].stagedCount == 0
        (((Map) ((List) out.groups)[0]).keySet() as List) == ['key', 'label', 'color', 'categoryId', 'count', 'stagedCount']
    }

    def "counts should answer zeros when there are no groups"() {

        when:
        Map<String, Object> out = service.counts(1, null, null)

        then:
        1 * repo.countsFiled(1, 'category', null) >> []
        out == [groups: [], total: 0, staged: 0]
    }

    // ---- the date -----------------------------------------------------------------------

    @Unroll
    def "#seq. parseDate should answer #expected when the date is #scenario"() {

        expect:
        SolutionFileFilingService.parseDate(value) == (expected == null ? null : getLocalDate(expected))

        where:
        seq | scenario                               | value                  || expected
        1   | 'null'                                 | null                   || null
        2   | 'empty'                                | ''                     || null
        3   | 'blank'                                | '   '                  || null
        4   | 'ISO'                                  | '2026-10-22'           || '10/22/2026'
        5   | 'ISO padded'                           | ' 2026-10-22 '         || '10/22/2026'
        6   | 'ISO with a time'                      | '2026-10-22T10:30:00'  || '10/22/2026'
        7   | 'US with zeros'                        | '10/02/2026'           || '10/02/2026'
        8   | 'US without zeros'                     | '1/2/2026'             || '01/02/2026'
        9   | 'the day before a leap day'            | '2/28/2028'            || '02/28/2028'
        10  | 'a leap day'                           | '2/29/2028'            || '02/29/2028'
        11  | 'the day after a leap day'             | '3/1/2028'             || '03/01/2028'
        12  | 'the earliest boundary'                | '01/01/1944'           || '01/01/1944'
        13  | 'the latest boundary'                  | '01/01/3000'           || '01/01/3000'
    }

    @Unroll
    def "#seq. parseDate should refuse the date when it is #scenario"() {

        when:
        SolutionFileFilingService.parseDate(value)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == expectedMessage

        where:
        seq | scenario                         | value            || expectedMessage
        1   | 'a word'                         | 'soon'           || 'The document date "soon" is not a date. Use mm/dd/yyyy.'
        2   | 'a padded word'                  | ' later '        || 'The document date "later" is not a date. Use mm/dd/yyyy.'
        3   | 'an impossible US date'          | '13/45/2026'     || 'The document date "13/45/2026" is not a date. Use mm/dd/yyyy.'
        4   | 'an impossible ISO date'         | '2026-02-30'     || 'The document date "2026-02-30" is not a date. Use mm/dd/yyyy.'
        5   | 'a US date with a two-digit year' | '1/2/26'        || 'The document date "1/2/26" is not a date. Use mm/dd/yyyy.'
        6   | 'dashes in US order'             | '10-22-2026'     || 'The document date "10-22-2026" is not a date. Use mm/dd/yyyy.'
        7   | 'a leap day of a common year'    | '2027-02-29'     || 'The document date "2027-02-29" is not a date. Use mm/dd/yyyy.'
        8   | 'a US date with a text month'    | 'Oct/22/2026'    || 'The document date "Oct/22/2026" is not a date. Use mm/dd/yyyy.'
    }

    @PendingFeature(reason = 'BUG: SolutionFileFilingService.parseDate reads mm/dd/yyyy with a SMART resolver, so an impossible US date such as 2/30/2026 is silently saved as 2026-02-28 while the same date in ISO form (2026-02-30) is refused')
    @Unroll
    def "parseDate should refuse an impossible US date when it is #scenario"() {

        when:
        SolutionFileFilingService.parseDate(value)

        then:
        thrown(IllegalArgumentException)

        where:
        scenario                         | value         || unused
        'the thirtieth of February'      | '2/30/2026'   || null
        'the thirty-first of April'      | '4/31/2026'   || null
        'a leap day of a common year'    | '2/29/2027'   || null
    }

    // ---- filing --------------------------------------------------------------------------

    def "setFiling should parse the date and shape the answer when the file is on the solution"() {

        when:
        Map<String, Object> out = service.setFiling(1, 7, 4, '9/15/2026', true, 'tony')

        then:
        1 * repo.setFiling(1, 7, 4, getLocalDate('09/15/2026'), true, 'tony') >> [FileCategoryID: 4, DocumentDate: '2026-09-15 00:00:00', IsImportant: 1]
        out == [fileId: 7, categoryId: 4, documentDate: '2026-09-15', important: true]
        (out.keySet() as List) == ['fileId', 'categoryId', 'documentDate', 'important']
    }

    def "setFiling should pass a null date and answer null when the file is not on the solution"() {

        when:
        Map<String, Object> out = service.setFiling(1, 99, null, '', false, 'felix')

        then:
        1 * repo.setFiling(1, 99, null, null, false, 'felix') >> null
        out == null
    }

    def "setFiling should refuse a bad date before anything is written"() {

        when:
        service.setFiling(1, 7, 4, 'someday', true, 'tony')

        then:
        thrown(IllegalArgumentException)
        0 * repo.setFiling(*_)
    }

    @Unroll
    def "setImportant should pass the flag through and answer #answer when #scenario"() {

        when:
        boolean out = service.setImportant(1, 7, important, 'tony')

        then:
        1 * repo.setImportant(1, 7, important, 'tony') >> answer
        out == answer

        where:
        scenario                  | important || answer
        'a file is flagged'       | true      || true
        'a file is unflagged'     | false     || true
        'the file is elsewhere'   | true      || false
    }

    @Unroll
    def "fileMany should answer 0 without SQL when #scenario"() {

        given:
        List<Integer> ids = idsText == null ? null : []

        when:
        int out = service.fileMany(1, ids, 4, 'tony')

        then:
        0 * repo.fileMany(*_)
        out == expected

        where:
        scenario               | idsText || expected
        'the ids are null'     | null    || 0
        'the ids are empty'    | ''      || 0
    }

    def "fileMany should file the ids under the category and answer how many when ids are sent"() {

        when:
        int out = service.fileMany(1, [7, 8], null, 'rex')

        then:
        1 * repo.fileMany(1, [7, 8], null, 'rex') >> 2
        out == 2
    }

    def "the vocabularies should list every group and sort when read"() {

        expect:
        SolutionFileFilingService.GROUPS == ['category', 'type', 'year', 'none'] as Set
        SolutionFileFilingService.SORTS == ['added', 'name', 'docdate', 'size', 'type'] as Set
    }
}
