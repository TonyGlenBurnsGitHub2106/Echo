SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Solution_Promote') IS NOT NULL DROP PROCEDURE dbo.stp_Solution_Promote
GO
CREATE PROCEDURE dbo.stp_Solution_Promote
    @prmSolutionID  INT,
    @prmSourceKind  VARCHAR(10),      -- message | log | note | item
    @prmSourceID    INT,              -- for a message: its ConversationMessageID
    @prmTargetKind  VARCHAR(10),      -- item | note | question  (a decision from a log entry: stp_SolutionDecision_FromLog)
    @prmProjectID   INT = NULL,       -- an item needs its project; a note may have one
    @prmTitle       NVARCHAR(400) = NULL,   -- NULL = the source's first line
    @prmMemberCode  VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_Solution_Promote 6, 'message', 9012, 'item', 41, NULL, 'tony'
    EXEC stp_Solution_Promote 6, 'note', 77, 'item', 41, N'Write the runbook for the QA move', 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo), Release 5: promote anything. From the deep review of 11/02: work that
                         starts as a message, a log line or a note has to be retyped to become an item. One record
                         becomes another, the source kept as it was:
                           a thread message  -> an item, a note or a question
                           a log entry       -> an item or a note (a decision or a question: Make into, Release 2)
                           a note            -> an item
                           an item           -> a note
                         The title is the source's first line (cut at a sentence when long) unless one is given; the
                         whole text goes in the new record (an item's detail, a note's body, a question's detail).
                         A message must be in a thread of this solution and says who wrote it and when. The two are
                         linked (a message cannot be: SolutionPromotion remembers it), and a SolutionPromotion row
                         refuses promoting the same source into the same kind twice while that record still exists.
                         Everything is checked before the first write. One transaction. Returns Kind and ID.
11/04/2026  Felix        The link is stored in the house order (lower kind first, as stp_SolutionLink_Save does), a
                         first line holding only spaces or ** is skipped, and a note made from something is a
                         Reference note when that kind exists.

*/

SET NOCOUNT ON

DECLARE @src VARCHAR(10) = LOWER(LTRIM(RTRIM(ISNULL(@prmSourceKind, ''))))
DECLARE @dst VARCHAR(10) = LOWER(LTRIM(RTRIM(ISNULL(@prmTargetKind, ''))))
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID)
BEGIN RAISERROR('That solution no longer exists. Nothing was added.', 16, 1) RETURN END
IF NOT ((@src = 'message' AND @dst IN ('item', 'note', 'question')) OR (@src = 'log' AND @dst IN ('item', 'note'))
     OR (@src = 'note' AND @dst = 'item') OR (@src = 'item' AND @dst = 'note'))
BEGIN RAISERROR('That cannot be made into that. A message becomes an item, a note or a question, a log entry an item or a note, a note an item, an item a note. Nothing was added.', 16, 1) RETURN END

-- the source: its text, who wrote it, when
DECLARE @text NVARCHAR(MAX), @who NVARCHAR(200), @when DATETIME, @srcProject INT
IF @src = 'message'
    SELECT @text = m.Body, @who = ISNULL(sm.MemberName, m.Sender), @when = m.CreatedDate
    FROM   dbo.ConversationMessage m
    JOIN   dbo.SolutionConversation sc ON sc.ConversationID = m.ConversationID AND sc.SolutionID = @prmSolutionID
    LEFT JOIN dbo.SolutionMember sm ON sm.MemberCode = LOWER(m.Sender)
    WHERE  m.ConversationMessageID = @prmSourceID
IF @src = 'log'
    SELECT @text = l.LogText, @when = l.LogDate, @srcProject = l.SolutionProjectID
    FROM   dbo.SolutionLog l WHERE l.SolutionLogID = @prmSourceID AND l.SolutionID = @prmSolutionID
IF @src = 'note'
    SELECT @text = CASE WHEN ISNULL(v.NoteBody, N'') = N'' THEN n.NoteTitle ELSE n.NoteTitle + NCHAR(10) + NCHAR(10) + v.NoteBody END,
           @when = n.CreatedDate, @srcProject = n.SolutionProjectID
    FROM   dbo.SolutionNote n
    LEFT JOIN dbo.SolutionNoteVersion v ON v.SolutionNoteID = n.SolutionNoteID AND v.VersionNumber = n.CurrentVersionNumber
    WHERE  n.SolutionNoteID = @prmSourceID AND n.SolutionID = @prmSolutionID
IF @src = 'item'
    SELECT @text = CASE WHEN ISNULL(i.ItemDetail, N'') = N'' THEN i.ItemTitle ELSE i.ItemTitle + NCHAR(10) + NCHAR(10) + i.ItemDetail END,
           @when = i.CreatedDate, @srcProject = i.SolutionProjectID
    FROM   dbo.SolutionProjectItem i JOIN dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
    WHERE  i.SolutionProjectItemID = @prmSourceID AND p.SolutionID = @prmSolutionID
IF @when IS NULL
BEGIN RAISERROR('That is not in this solution any more. Nothing was added.', 16, 1) RETURN END

-- the same source already made into this kind, and that record is still there?
DECLARE @earlier INT = (SELECT TargetID FROM dbo.SolutionPromotion WHERE SourceKind = @src AND SourceID = @prmSourceID AND TargetKind = @dst)
IF @earlier IS NOT NULL AND (
       (@dst = 'item'     AND EXISTS (SELECT 1 FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = @earlier))
    OR (@dst = 'note'     AND EXISTS (SELECT 1 FROM dbo.SolutionNote WHERE SolutionNoteID = @earlier))
    OR (@dst = 'question' AND EXISTS (SELECT 1 FROM dbo.SolutionQuestion WHERE SolutionQuestionID = @earlier)))
BEGIN
    DECLARE @dup NVARCHAR(400) = N'That was already made into ' + CASE @dst WHEN 'item' THEN N'an item' ELSE N'a ' + @dst END +
        N' (#' + CAST(@earlier AS NVARCHAR(10)) + N'). Nothing was added.'
    RAISERROR(@dup, 16, 1) RETURN
END

-- the project: given, else the source's own; an item must have one
DECLARE @project INT = NULLIF(ISNULL(@prmProjectID, 0), 0)
IF @project IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.SolutionProject WHERE SolutionProjectID = @project AND SolutionID = @prmSolutionID)
BEGIN RAISERROR('That project is not in this solution. Nothing was added.', 16, 1) RETURN END
IF @project IS NULL SET @project = @srcProject
IF @dst = 'item' AND @project IS NULL
BEGIN RAISERROR('Choose the project the item goes in. Nothing was added.', 16, 1) RETURN END

-- the title: given, else the first line with anything on it, without Markdown bold marks, cut when long
DECLARE @body NVARCHAR(MAX) = LTRIM(RTRIM(REPLACE(REPLACE(ISNULL(@text, N''), NCHAR(13), N''), NCHAR(9), N' ')))
WHILE LEFT(@body, 1) = NCHAR(10) SET @body = SUBSTRING(@body, 2, LEN(@body))
-- a first line with nothing on it but spaces or Markdown bold marks is not the title: drop it
WHILE CHARINDEX(NCHAR(10), @body) > 0
      AND LTRIM(RTRIM(REPLACE(LEFT(@body, CHARINDEX(NCHAR(10), @body) - 1), N'**', N''))) = N''
    SET @body = LTRIM(SUBSTRING(@body, CHARINDEX(NCHAR(10), @body) + 1, LEN(@body)))
DECLARE @title NVARCHAR(400) = LTRIM(RTRIM(ISNULL(@prmTitle, N'')))
IF @title = N''
BEGIN
    DECLARE @nl INT = CHARINDEX(NCHAR(10), @body)
    DECLARE @first NVARCHAR(MAX) = LTRIM(RTRIM(REPLACE(CASE WHEN @nl > 0 THEN LEFT(@body, @nl - 1) ELSE @body END, N'**', N'')))
    IF LEN(@first) > 200
    BEGIN
        DECLARE @stop INT = PATINDEX(N'%[.?!] %', LEFT(@first, 201))
        IF @stop >= 20 SET @first = LEFT(@first, @stop)
        ELSE
        BEGIN
            DECLARE @cut NVARCHAR(200) = LEFT(@first, 197)
            DECLARE @space INT = 197 - CHARINDEX(N' ', REVERSE(@cut))
            SET @first = LEFT(@cut, CASE WHEN CHARINDEX(N' ', @cut) > 0 AND @space > 100 THEN @space ELSE 197 END) + N'...'
        END
    END
    SET @title = @first
END
IF @title = N'' BEGIN RAISERROR('That has no text to make anything of. Nothing was added.', 16, 1) RETURN END
IF @dst = 'note' AND LEN(@title) > 300 SET @title = LEFT(@title, 297) + N'...'
IF @dst = 'question' AND LEN(@title) > 800 SET @title = LEFT(@title, 797) + N'...'
IF @dst = 'question' AND EXISTS (SELECT 1 FROM dbo.SolutionQuestion WHERE SolutionID = @prmSolutionID AND StatusCode = 'open' AND QuestionText = @title)
BEGIN RAISERROR('That question is already open. Nothing was added.', 16, 1) RETURN END

-- a message says who wrote it and when, at the top of the new record's text
IF @src = 'message'
    SET @body = N'*From ' + ISNULL(@who, N'someone') + N', ' + CONVERT(NVARCHAR(10), @when, 101) + N', in the thread:*' + NCHAR(10) + NCHAR(10) + @body

DECLARE @memberId INT = NULL
EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT
DECLARE @id INT = 0

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    DELETE FROM dbo.SolutionPromotion WHERE SourceKind = @src AND SourceID = @prmSourceID AND TargetKind = @dst   -- its record is gone
    IF @dst = 'item'
    BEGIN
        INSERT INTO dbo.SolutionProjectItem (SolutionProjectID, ParentItemID, ItemTitle, ItemDetail, SolutionStatusID,
                                             AssigneeMemberID, Priority, SortOrder, DueDate, CompletedDate, CreatedByMemberID, ModifiedBy)
        VALUES (@project, NULL, @title, @body,
                (SELECT TOP 1 SolutionStatusID FROM dbo.SolutionStatus ORDER BY SortOrder, SolutionStatusID), NULL, 3,
                ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionProjectItem WITH (UPDLOCK, HOLDLOCK)
                        WHERE SolutionProjectID = @project AND ParentItemID IS NULL), 0) + 1,
                NULL, NULL, @memberId, LOWER(@prmMemberCode))
        SET @id = CAST(SCOPE_IDENTITY() AS INT)
    END
    IF @dst = 'note'
    BEGIN
        INSERT INTO dbo.SolutionNote (SolutionID, SolutionProjectID, SolutionNoteTypeID, NoteTitle, CurrentVersionNumber, SortOrder, ModifiedBy)
        VALUES (@prmSolutionID, @project, ISNULL((SELECT SolutionNoteTypeID FROM dbo.SolutionNoteType WHERE NoteTypeCode = 'reference'),
                                         (SELECT TOP 1 SolutionNoteTypeID FROM dbo.SolutionNoteType ORDER BY SortOrder, SolutionNoteTypeID)),
                @title, 1, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionNote WITH (UPDLOCK, HOLDLOCK) WHERE SolutionID = @prmSolutionID), 0) + 1,
                LOWER(@prmMemberCode))
        SET @id = CAST(SCOPE_IDENTITY() AS INT)
        INSERT INTO dbo.SolutionNoteVersion (SolutionNoteID, VersionNumber, NoteBody, ChangeNote, AuthorMemberID)
        VALUES (@id, 1, @body, N'Made from ' + CASE @src WHEN 'message' THEN N'a thread message' WHEN 'log' THEN N'a log entry' ELSE N'an item' END, @memberId)
    END
    IF @dst = 'question'
    BEGIN
        INSERT INTO dbo.SolutionQuestion (SolutionID, QuestionText, QuestionDetail, StatusCode, AskedByMemberID, ModifiedBy)
        VALUES (@prmSolutionID, @title, @body, 'open', @memberId, LOWER(@prmMemberCode))
        SET @id = CAST(SCOPE_IDENTITY() AS INT)
    END
    INSERT INTO dbo.SolutionPromotion (SolutionID, SourceKind, SourceID, TargetKind, TargetID, PromotedByMemberID)
    VALUES (@prmSolutionID, @src, @prmSourceID, @dst, @id, @memberId)
    IF @src <> 'message'
        INSERT INTO dbo.SolutionLink (SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID)
        SELECT @prmSolutionID,
               CASE WHEN @dst < @src THEN @dst ELSE @src END, CASE WHEN @dst < @src THEN @id ELSE @prmSourceID END,
               CASE WHEN @dst < @src THEN @src ELSE @dst END, CASE WHEN @dst < @src THEN @prmSourceID ELSE @id END, @memberId
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @dst AS Kind, @id AS ID

GO
