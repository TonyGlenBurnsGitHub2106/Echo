package com.echo

// Story 5653: file categories -- name and color rules, the find-or-create "created" flag,
// the row shape, and how a request names a category.

import spock.lang.Unroll

class FileFilingServiceSpec extends EchoSpockBaseSpec {

    FileFilingRepository repo = Mock()
    FileFilingService service = new FileFilingService(repo)

    // ---- categories -----------------------------------------------------------------------------

    def "categories should map every row to the public shape when rows are listed"() {

        when:
        List<Map<String, Object>> got = service.categories()

        then:
        1 * repo.listCategories() >> [row([UseCount: 3]), row([FileCategoryID: 5, CategoryName: 'Receipts', ColorHex: null, SortOrder: 30])]
        got == [[id: 4, name: 'Tax', color: '#1D4ED8', sortOrder: 20, useCount: 3],
                [id: 5, name: 'Receipts', color: '', sortOrder: 30]]
        got[0].keySet() as List == ['id', 'name', 'color', 'sortOrder', 'useCount']
    }

    def "categories should answer an empty list when there are no categories"() {

        when:
        List<Map<String, Object>> got = service.categories()

        then:
        1 * repo.listCategories() >> []
        got == []
    }

    @Unroll
    def "categories should read a number column as #expected when the value is #scenario"() {

        when:
        List<Map<String, Object>> got = service.categories()

        then:
        1 * repo.listCategories() >> [[FileCategoryID: raw, UseCount: raw] as Map<String, Object>]
        got[0].id == expected
        got[0].useCount == expected
        got[0].name == ''

        where:
        scenario            | raw   || expected
        'an int'            | 7     || 7
        'a long'            | 7L    || 7
        'a double'          | 7.8d  || 7
        'padded text'       | ' 9 ' || 9
        'non-numeric text'  | 'x'   || 0
        'null'              | null  || 0
    }

    def "categories should include useCount only when the row has the column, even if it is null"() {

        when:
        List<Map<String, Object>> got = service.categories()

        then:
        1 * repo.listCategories() >> [row([:]), row([UseCount: null])]
        !got[0].containsKey('useCount')
        got[1].useCount == 0
    }

    // ---- saveCategory -----------------------------------------------------------------------------

    @Unroll
    def "saveCategory should refuse the name when it is #scenario"() {

        given:
        String name = repeat == null ? value : getReplicatedString(value, repeat)

        when:
        service.saveCategory(1, name, null, null)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == message
        0 * repo._

        where:
        scenario                   | value | repeat || message
        'null'                     | null  | null   || 'A category needs a name.'
        'empty'                    | ''    | null   || 'A category needs a name.'
        'blank'                    | '   ' | null   || 'A category needs a name.'
        '101 characters long'      | 'x'   | 101    || 'A category name is 100 characters at most.'
    }

    def "saveCategory should trim the name and accept 100 characters when the name is padded"() {

        given:
        String name = getReplicatedString('n', 100)

        when:
        Map<String, Object> got = service.saveCategory(4, '  ' + name + '  ', null, 5)

        then:
        1 * repo.saveCategory(4, name, null, 5) >> row([CategoryName: name])
        got.name == name
    }

    @Unroll
    def "saveCategory should store the color as #stored when it is #scenario"() {

        when:
        service.saveCategory(4, 'Tax', color, null)

        then:
        1 * repo.saveCategory(4, 'Tax', stored, null) >> row([:])

        where:
        scenario                | color         || stored
        'null'                  | null          || null
        'empty'                 | ''            || null
        'blank'                 | '   '         || null
        'lower case'            | '#1d4ed8'     || '#1D4ED8'
        'upper case'            | '#1D4ED8'     || '#1D4ED8'
        'padded'                | '  #abcdef  ' || '#ABCDEF'
        'black'                 | '#000000'     || '#000000'
    }

    @Unroll
    def "#seq. saveCategory should refuse the color before writing when it is #scenario"() {

        when:
        service.saveCategory(4, 'Tax', color, null)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'Color must look like #1D4ED8.'
        0 * repo._

        where:
        seq | scenario                 | color
        1   | 'missing the hash'       | '1D4ED8'
        2   | 'five digits'            | '#12345'
        3   | 'seven digits'           | '#1234567'
        4   | 'not hex'                | '#GGGGGG'
        5   | 'a color name'           | 'red'
        6   | 'one digit short'        | '#1D4ED'
        7   | 'a doubled hash'         | '##1D4ED8'
        8   | 'a space inside'         | '#1D 4ED8'
        9   | 'an rgb function'        | 'rgb(0,0,0)'
        10  | 'non-ASCII letters'      | '#\u00e9\u00e9\u00e9\u00e9\u00e9\u00e9'
    }

    @Unroll
    def "saveCategory should pass the sort order through when it is #scenario"() {

        when:
        service.saveCategory(4, 'Tax', null, sortOrder)

        then:
        1 * repo.saveCategory(4, 'Tax', null, sortOrder) >> row([:])

        where:
        scenario    | sortOrder
        'null'      | null
        'zero'      | 0
        'negative'  | -5
    }

    def "saveCategory should let a proc refusal through when the repository throws"() {

        when:
        service.saveCategory(4, 'Tax', null, null)

        then:
        1 * repo.saveCategory(*_) >> { throw new IllegalArgumentException('A category with that name exists.') }
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'A category with that name exists.'
    }

    // ---- findOrCreateCategory ------------------------------------------------------------------------

    @Unroll
    def "findOrCreateCategory should refuse the name when it is #scenario"() {

        given:
        String name = repeat == null ? value : getReplicatedString(value, repeat)

        when:
        service.findOrCreateCategory(name)

        then:
        thrown(IllegalArgumentException)
        0 * repo._

        where:
        scenario                | value | repeat
        'null'                  | null  | null
        'empty'                 | ''    | null
        'blank'                 | '  '  | null
        '101 characters long'   | 'y'   | 101
    }

    @Unroll
    def "findOrCreateCategory should answer created=#created when WasCreated is #scenario"() {

        when:
        Map<String, Object> got = service.findOrCreateCategory('  Tax ')

        then:
        1 * repo.findOrCreateCategory('Tax') >> row([WasCreated: raw])
        got == [id: 4, name: 'Tax', color: '#1D4ED8', sortOrder: 20, created: created]

        where:
        scenario          | raw    || created
        'true'            | true   || true
        'the number 1'    | 1      || true
        'false'           | false  || false
        'the number 0'    | 0      || false
        'null'            | null   || false
        'the text true'   | 'true' || false
    }

    def "findOrCreateCategory should answer not created when the WasCreated column is missing"() {

        when:
        Map<String, Object> got = service.findOrCreateCategory('Tax')

        then:
        1 * repo.findOrCreateCategory('Tax') >> row([:])
        got.created == false
    }

    // ---- delete / reorder ----------------------------------------------------------------------------

    def "deleteCategory should answer how many files went back to staging when it deletes"() {

        when:
        int n = service.deleteCategory(4)

        then:
        1 * repo.deleteCategory(4) >> 3
        n == 3
    }

    def "reorderCategories should pass the ids and map the rows when ids are given"() {

        when:
        List<Map<String, Object>> got = service.reorderCategories([5, 4])

        then:
        1 * repo.reorderCategories([5, 4]) >> [row([FileCategoryID: 5]), row([:])]
        got*.id == [5, 4]
    }

    def "reorderCategories should send an empty list when the ids are null"() {

        when:
        List<Map<String, Object>> got = service.reorderCategories(null)

        then:
        1 * repo.reorderCategories([]) >> []
        got == []
    }

    // ---- resolveCategory -----------------------------------------------------------------------------

    @Unroll
    def "#seq. resolveCategory should answer #expected when #scenario"() {

        given:
        repo.findOrCreateCategory('Tax') >> row([FileCategoryID: 11])

        expect:
        service.resolveCategory(id, name, staging) == expected

        where:
        seq | scenario                                   | id   | name    | staging || expected
        1   | 'staging wins over an id'                  | 4    | 'Tax'   | true    || null
        2   | 'staging wins over a name'                 | null | 'Tax'   | true    || null
        3   | 'an id wins over a name'                   | 4    | 'Tax'   | false   || 4
        4   | 'an id alone'                              | 4    | null    | null    || 4
        5   | 'a zero id falls back to the name'         | 0    | 'Tax'   | null    || 11
        6   | 'a negative id falls back to the name'     | -1   | 'Tax'   | false   || 11
        7   | 'a name alone'                             | null | 'Tax'   | null    || 11
        8   | 'a padded name'                            | null | ' Tax ' | null    || 11
        9   | 'nothing is named'                         | null | null    | null    || null
        10  | 'the name is empty'                        | null | ''      | null    || null
        11  | 'the name is blank'                        | null | '   '   | false   || null
        12  | 'a zero id and no name'                    | 0    | null    | false   || null
    }

    def "resolveCategory should never create anything when an id is given"() {

        when:
        service.resolveCategory(4, 'Tax', false)

        then:
        0 * repo._
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Map<String, Object> row(Map<String, Object> extra) {
        Map<String, Object> r = [FileCategoryID: 4, CategoryName: 'Tax', ColorHex: '#1D4ED8', SortOrder: 20]
        r.putAll(extra)
        r
    }
}
