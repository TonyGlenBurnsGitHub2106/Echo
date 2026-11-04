package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionFileVersionModels.FileVersion
import com.echo.SolutionFileVersionModels.Versions

import spock.lang.Unroll

/**
 * Story 5695 (Release 5): the shapes of a file's earlier versions -- the row mapper of one version (FileVersion,
 * whose solution only the Get of one version reads) and the answer of the versions list (Versions), whose current
 * version is 1 until a new version is uploaded and otherwise one past the newest earlier version.
 */
class SolutionFileVersionModelsSpec extends EchoSpockBaseSpec {

    static final LocalDateTime T = getLocalDateTime('11/04/2026 10:15')

    private static FileVersion version(int id, int number) {
        new FileVersion(id, 211, number, 'charset-v' + number + '.xlsx', '6/abcd1234-charset-v' + number + '.xlsx',
                        'application/vnd.ms-excel', 100L * number, null, null, 'tony', 'Tony', null)
    }

    // ---- FileVersion ----

    def "FileVersion.of should read every column when the row of one version is complete"() {

        when:
        FileVersion v = FileVersion.of([SolutionFileVersionID: 3, SolutionFileID: 211, VersionNumber: 1,
                                         OriginalFileName: 'charset.xlsx', StoredFileName: '6/abcd1234-charset.xlsx',
                                         ContentType: 'application/vnd.ms-excel', FileSizeBytes: 18233L,
                                         ChangeNote: 'QA numbers added', UploadedDate: Timestamp.valueOf(T),
                                         UploadedByCode: 'rex', UploadedByName: 'Rex', SolutionID: 6])

        then:
        v == new FileVersion(3, 211, 1, 'charset.xlsx', '6/abcd1234-charset.xlsx', 'application/vnd.ms-excel', 18233L,
                             'QA numbers added', T, 'rex', 'Rex', 6)
    }

    def "FileVersion.of should answer zeros and nulls when the row is empty"() {

        expect:
        FileVersion.of([:]) == new FileVersion(0, 0, 0, null, null, null, 0L, null, null, null, null, null)
    }

    @Unroll
    def "FileVersion.of should read the size as #expected when the driver answers #scenario"() {

        expect:
        FileVersion.of([SolutionFileVersionID: 3, FileSizeBytes: value]).sizeBytes() == expected

        where:
        scenario                 | value         || expected
        'a long'                 | 5000000000L   || 5000000000L
        'an int'                 | 42            || 42L
        'null'                   | null          || 0L
        'text it does not parse' | '42'          || 0L
    }

    @Unroll
    def "FileVersion.of should read the solution as #expected when SolutionID is #scenario"() {

        expect:
        FileVersion.of([SolutionFileVersionID: 3, SolutionID: value]).solutionId() == expected

        where:
        scenario                      | value || expected
        'a solution id'               | 6     || 6
        'zero'                        | 0     || null
        'missing, as in the list'     | null  || null
    }

    // ---- Versions ----

    def "Versions.of should answer version 1 and no earlier versions when the file has none"() {

        when:
        Versions v = Versions.of([])

        then:
        v.currentVersion() == 1
        v.versions() == []
    }

    @Unroll
    def "Versions.of should answer current version #expected when the file has #scenario"() {

        given:
        List<FileVersion> earlier = (newest..1).collect { int n -> version(10 + n, n) }

        when:
        Versions v = Versions.of(earlier)

        then:
        v.currentVersion() == expected
        v.versions().is(earlier)

        where:
        scenario         | newest || expected
        'one earlier'    | 1      || 2
        'two earlier'    | 2      || 3
        'five earlier'   | 5      || 6
    }

    def "Versions.of should count from the first row of the list when the list is newest first"() {

        when:
        Versions v = Versions.of([version(14, 4), version(12, 2)])

        then:
        v.currentVersion() == 5
        v.versions()*.versionNumber() == [4, 2]
    }
}
