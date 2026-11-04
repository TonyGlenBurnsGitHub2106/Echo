package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionSavedViewModels.SavedView

import spock.lang.Unroll

/**
 * Story 5695 (Release 5): the shape of a member's saved view of a solution's tab -- the row mapper of one view
 * (SavedView, its filters kept as the page wrote them) and the tabs a view can be saved for (SCOPES).
 */
class SolutionSavedViewModelsSpec extends EchoSpockBaseSpec {

    static final LocalDateTime CREATED = getLocalDateTime('11/04/2026 10:15')
    static final LocalDateTime MODIFIED = getLocalDateTime('11/04/2026 11:30')

    def "SCOPES should allow views of the items tab only"() {

        expect:
        SolutionSavedViewModels.SCOPES == ['items'] as Set
    }

    def "SavedView.of should read every column when the row is complete"() {

        when:
        SavedView v = SavedView.of([SolutionSavedViewID: 4, SolutionID: 6, ScopeCode: 'items', ViewName: 'My open submit work',
                                    FilterJson: '{"project":"41","assignee":"tony","hideClosed":true}', SortOrder: 2,
                                    CreatedDate: Timestamp.valueOf(CREATED), ModifiedDate: Timestamp.valueOf(MODIFIED)])

        then:
        v == new SavedView(4, 6, 'items', 'My open submit work', '{"project":"41","assignee":"tony","hideClosed":true}', 2,
                           CREATED, MODIFIED)
    }

    def "SavedView.of should answer zeros and nulls when the row is empty"() {

        expect:
        SavedView.of([:]) == new SavedView(0, 0, null, null, null, 0, null, null)
    }

    @Unroll
    def "SavedView.of should read the sort order as #expected when the driver answers #scenario"() {

        expect:
        SavedView.of([SolutionSavedViewID: 4, SortOrder: value]).sortOrder() == expected

        where:
        scenario     | value || expected
        'an int'     | 3     || 3
        'a long'     | 5L    || 5
        'text'       | ' 7 ' || 7
        'null'       | null  || 0
    }

    def "SavedView.of should keep a view never edited without a modified date"() {

        expect:
        SavedView.of([SolutionSavedViewID: 4, CreatedDate: Timestamp.valueOf(CREATED), ModifiedDate: null]).modifiedDate() == null
    }
}
