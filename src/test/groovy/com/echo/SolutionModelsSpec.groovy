package com.echo

import java.sql.Timestamp
import java.time.LocalDateTime

import com.echo.SolutionModels.CodeName
import com.echo.SolutionModels.ConversationSummary
import com.echo.SolutionModels.FileRow
import com.echo.SolutionModels.Item
import com.echo.SolutionModels.LogEntry
import com.echo.SolutionModels.LogPage
import com.echo.SolutionModels.Lookups
import com.echo.SolutionModels.Member
import com.echo.SolutionModels.Message
import com.echo.SolutionModels.Note
import com.echo.SolutionModels.NoteStep
import com.echo.SolutionModels.NoteVersion
import com.echo.SolutionModels.Project
import com.echo.SolutionModels.Solution
import com.echo.SolutionModels.Status

import spock.lang.Unroll

// Story 5653: SolutionModels -- every read model's mapper reads the column the procs return, into the right field.
class SolutionModelsSpec extends EchoSpockBaseSpec {

    private static final LocalDateTime T1 = getLocalDateTime('09/17/2026 10:00')
    private static final LocalDateTime T2 = getLocalDateTime('09/18/2026 11:00')
    private static final LocalDateTime T3 = getLocalDateTime('09/19/2026 12:00')

    def "Status.of should map every column when the row is complete"() {

        given:
        Map<String, Object> row = [SolutionStatusID: 2, StatusCode: 'done', StatusName: 'Done', IsClosed: 1, ColorHex: '#0f0', SortOrder: 4]

        when:
        Status s = Status.of(row)

        then:
        s == new Status(2, 'done', 'Done', true, '#0f0', 4)
    }

    def "Member.of should map every column when the row is complete"() {

        given:
        Map<String, Object> row = [SolutionMemberID: 3, MemberCode: 'rex', MemberName: 'Rex', IsAgent: true, IsActive: false, SortOrder: 9]

        expect:
        Member.of(row) == new Member(3, 'rex', 'Rex', true, false, 9)
    }

    def "Solution.of should map all 29 columns when the row is complete"() {

        given:
        Map<String, Object> row = [SolutionID: 1, SolutionName: 'Alpha', SolutionDescription: 'desc', DescriptionPreview: 'de',
                                   StatusCode: 'todo', StatusName: 'To do', IsClosed: 0, ColorHex: '#111', OwnerCode: 'tony',
                                   OwnerName: 'Tony', StatusSummary: 'going', TargetDate: Timestamp.valueOf(T1), IsArchived: true,
                                   SaveFilesToRepo: 1, FileCount: 2, SharedCount: 3, SortOrder: 4, CreatedDate: T2, ModifiedDate: T3,
                                   ModifiedBy: 'felix', ProjectCount: 5, ItemCount: 6, ClosedItemCount: 7, BlockedItemCount: 8,
                                   LogCount: 9, LastLogDate: T1, NoteCount: 10, AwaitingTonyCount: 11, SolutionAlias: 'claims']

        when:
        Solution s = Solution.of(row)

        then:
        s == new Solution(1, 'Alpha', 'desc', 'de', 'todo', 'To do', false, '#111', 'tony', 'Tony', 'going', T1, true, true,
            2, 3, 4, T2, T3, 'felix', 5, 6, 7, 8, 9, T1, 10, 11, 'claims')
    }

    def "Solution.of should give zeros and nulls, never an error, when the row is empty"() {

        when:
        Solution s = Solution.of([:])

        then:
        s.id() == 0
        s.name() == null
        !s.closed()
        s.targetDate() == null
        s.alias() == null
    }

    def "Project.of should map every column when the row is complete"() {

        given:
        Map<String, Object> row = [SolutionProjectID: 10, SolutionID: 1, ProjectName: 'P', ProjectDescription: 'D', DescriptionPreview: 'd',
                                   StatusCode: 'blocked', StatusName: 'Blocked', IsClosed: false, ColorHex: '#f00', OwnerCode: 'felix',
                                   OwnerName: 'Felix', SortOrder: 2, CreatedDate: T1, ModifiedDate: T2, ModifiedBy: 'rex', ItemCount: 3,
                                   ClosedItemCount: 1, BlockedItemCount: 1]

        expect:
        Project.of(row) == new Project(10, 1, 'P', 'D', 'd', 'blocked', 'Blocked', false, '#f00', 'felix', 'Felix', 2, T1, T2,
            'rex', 3, 1, 1)
    }

    def "Item.of should map every column and read no parent when the parent id is null or zero"() {

        given:
        Map<String, Object> row = [SolutionProjectItemID: 100, SolutionProjectID: 10, SolutionID: 1, ProjectName: 'P', ParentItemID: 99,
                                   ItemTitle: 'T', ItemDetail: 'full', DetailPreview: 'fu', HasDetail: 1, StatusCode: 'done',
                                   StatusName: 'Done', IsClosed: true, ColorHex: '#0f0', AssigneeCode: 'rex', AssigneeName: 'Rex',
                                   Priority: 2, SortOrder: 3, DueDate: T1, CompletedDate: T2, CreatedByCode: 'tony', CreatedDate: T3,
                                   ModifiedDate: T1, ModifiedBy: 'tony', ChildCount: 4, LogCount: 5]

        expect:
        Item.of(row) == new Item(100, 10, 1, 'P', 99, 'T', 'full', 'fu', true, 'done', 'Done', true, '#0f0', 'rex', 'Rex', 2, 3,
            T1, T2, 'tony', T3, T1, 'tony', 4, 5)
        Item.of(row + [ParentItemID: null]).parentItemId() == null
        Item.of(row + [ParentItemID: 0]).parentItemId() == null
    }

    def "LogEntry.of should map every column and read no project or item when those ids are absent"() {

        given:
        Map<String, Object> row = [SolutionLogID: 200, SolutionID: 1, SolutionProjectID: 10, ProjectName: 'P', SolutionProjectItemID: 100,
                                   ItemTitle: 'I', SequenceNumber: 7, LogTypeCode: 'command', LogTypeName: 'Command', AuthorCode: 'rex',
                                   AuthorName: 'Rex', LogText: 'mvn', IsTruncated: 1, LogDate: T1, CreatedDate: T2, ModifiedDate: T3,
                                   StepCount: 2]
        Map<String, Object> bare = row + [SolutionProjectID: 0, SolutionProjectItemID: null]

        expect:
        LogEntry.of(row) == new LogEntry(200, 1, 10, 'P', 100, 'I', 7, 'command', 'Command', 'rex', 'Rex', 'mvn', true, T1, T2, T3, 2)
        LogEntry.of(bare).projectId() == null
        LogEntry.of(bare).itemId() == null
    }

    def "Note.of should carry the steps it is given, null included"() {

        given:
        List<NoteStep> steps = [new NoteStep(1, 300, 1, 's', null, null, null, null, null)]
        Map<String, Object> row = [SolutionNoteID: 300, SolutionID: 1, SolutionProjectID: 10, ProjectName: 'P', NoteTypeCode: 'runbook',
                                   NoteTypeName: 'Runbook', NoteTitle: 'How', CurrentVersionNumber: 3, SortOrder: 1, CreatedDate: T1,
                                   ModifiedDate: T2, ModifiedBy: 'tony', AuthorCode: 'felix', AuthorName: 'Felix', VersionDate: T3,
                                   ChangeNote: 'why', StepCount: 1, BodyPreview: 'bo', NoteBody: 'body']

        expect:
        Note.of(row, steps) == new Note(300, 1, 10, 'P', 'runbook', 'Runbook', 'How', 3, 1, T1, T2, 'tony', 'felix', 'Felix', T3,
            'why', 1, 'bo', 'body', steps)
        Note.of(row + [SolutionProjectID: null], null).projectId() == null
        Note.of(row, null).steps() == null
    }

    def "NoteVersion.of should map every column when the row is complete"() {

        given:
        Map<String, Object> row = [SolutionNoteID: 300, VersionNumber: 2, ChangeNote: 'c', AuthorCode: 'a', AuthorName: 'A',
                                   CreatedDate: T1, BodyLength: 44, IsCurrent: 'true', NoteBody: 'b']

        expect:
        NoteVersion.of(row) == new NoteVersion(300, 2, 'c', 'a', 'A', T1, 44, true, 'b')
    }

    def "NoteStep.of should map every column and read no source when the source ids are absent"() {

        given:
        Map<String, Object> row = [SolutionNoteStepID: 31, SolutionNoteID: 300, StepNumber: 2, StepTitle: 'T', StepCommand: 'c',
                                   ExpectedResult: 'e', StepNotes: 'n', SourceSolutionLogID: 200, SourceSequenceNumber: 7]
        Map<String, Object> bare = row + [SourceSolutionLogID: 0, SourceSequenceNumber: null]

        expect:
        NoteStep.of(row) == new NoteStep(31, 300, 2, 'T', 'c', 'e', 'n', 200, 7)
        NoteStep.of(bare).sourceLogId() == null
        NoteStep.of(bare).sourceSequenceNumber() == null
    }

    def "ConversationSummary.of should map every column when the row is complete"() {

        given:
        Map<String, Object> row = [ConversationID: 5, Title: 't', Status: 'open', CreatedDate: T1, ModifiedDate: T2, MessageCount: 3,
                                   LastSender: 'rex', AwaitingFrom: 'tony', AwaitingTonyCount: 1]

        expect:
        ConversationSummary.of(row) == new ConversationSummary(5, 't', 'open', T1, T2, 3, 'rex', 'tony', 1)
    }

    def "FileRow.of should map every column when the row is complete"() {

        given:
        Map<String, Object> row = [SolutionFileID: 9, SolutionID: 1, SolutionProjectItemID: 100, ItemTitle: 'I', FileTitle: 'F',
                                   OriginalFileName: 'a.txt', StorageCode: 'nook', LeonardoFileID: 0, StoredFileName: 's.txt',
                                   ContentType: 'text/plain', FileSizeBytes: 5_000_000_000L, FileNotes: 'n', UploadedByCode: 'rex',
                                   UploadedByName: 'Rex', CreatedDate: T1, ModifiedDate: T2]

        expect:
        FileRow.of(row) == new FileRow(9, 1, 100, 'I', 'F', 'a.txt', 'nook', null, 's.txt', 'text/plain', 5_000_000_000L, 'n', 'rex',
            'Rex', T1, T2)
    }

    @Unroll
    def "FileRow.of should read size #expected when #scenario"() {

        given:
        Map<String, Object> row = [SolutionFileID: 9, FileSizeBytes: size]

        when:
        FileRow f = FileRow.of(row)

        then:
        f.sizeBytes() == expected

        where:
        scenario                 | size || expected
        "the size is text"       | "12" || 0L
        "the size is null"       | null || 0L
        "the size is an Integer" | 12   || 12L
    }

    def "Message.of should map every column when the row is complete"() {

        given:
        Map<String, Object> row = [ConversationMessageID: 4, ConversationID: 5, Sender: 'rex', Body: 'hi', CreatedDate: T1,
                                   AwaitingResponseFrom: 'tony']

        expect:
        Message.of(row) == new Message(4, 5, 'rex', 'hi', T1, 'tony')
    }

    def "the holder records should keep what they are given when constructed"() {

        given:
        CodeName cn = new CodeName(1, 'note', 'Note', 2)
        LogPage page = new LogPage([], [total: 0], 5, 50, 0)
        Lookups l = new Lookups([], [], [cn], [])

        expect:
        cn.code() == 'note' && cn.sortOrder() == 2
        page.offset() == 5 && page.pageSize() == 50 && page.counts() == [total: 0]
        l.logTypes() == [cn]
    }
}
