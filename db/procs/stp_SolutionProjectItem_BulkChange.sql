SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProjectItem_BulkChange') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProjectItem_BulkChange
GO
CREATE PROCEDURE dbo.stp_SolutionProjectItem_BulkChange
    @prmSolutionID  INT,
    @prmItemIDs     VARCHAR(8000),     -- comma separated item ids, all of this solution
    @prmField       VARCHAR(10),       -- status | project | assignee | due | priority | topic
    @prmValue       NVARCHAR(400),     -- a status code, a project id, a member code ('' = nobody), a date yyyy-mm-dd
                                       -- ('' = none), a priority 1..5, a topic id
    @prmReason      NVARCHAR(1000),    -- why: required, it goes in the log entry
    @prmMemberCode  VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionProjectItem_BulkChange 6, '1200,1201,1202', 'status', N'cancelled', N'The partner app writes the 837 itself.', 'tony'
    EXEC stp_SolutionProjectItem_BulkChange 6, '1200,1201', 'assignee', N'rex', N'Rex owns the parser now.', 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo), Release 5: change many items at once, WITH A REASON. From the deep review of
                         11/02: the dead 837 path was cancelled by hand in 20 items, and 326 items sit unassigned.
                         One field changes on every item named: its status, its project (each item moves with its
                         sub-items to the top level of that project, after what is there), who has it, its due date,
                         its priority, or a topic it is linked to. Every item must be this solution's, every value
                         must be good, and the reason must be given, all before the first write. One transaction. One
                         log entry (Note) says what changed and why, and is linked to every item changed. An item
                         already as asked is left alone and not counted. Returns SolutionLogID (0 when nothing needed
                         changing) and ItemCount.
11/04/2026  Felix        As one item's status change does (SolutionItemService.wakeAncestors): a status other than
                         Not started moves every ancestor item and project still at Not started to In progress.
                         The log-to-item links are stored in the house order (item before log).

*/

SET NOCOUNT ON

DECLARE @field VARCHAR(10) = LOWER(LTRIM(RTRIM(ISNULL(@prmField, ''))))
DECLARE @value NVARCHAR(400) = LTRIM(RTRIM(ISNULL(@prmValue, N'')))
DECLARE @reason NVARCHAR(1000) = LTRIM(RTRIM(ISNULL(@prmReason, N'')))

IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID)
BEGIN RAISERROR('That solution no longer exists. Nothing was changed.', 16, 1) RETURN END
IF @field NOT IN ('status', 'project', 'assignee', 'due', 'priority', 'topic')
BEGIN RAISERROR('What to change must be status, project, assignee, due, priority or topic. Nothing was changed.', 16, 1) RETURN END
IF @reason = N''
BEGIN RAISERROR('Say why. The reason goes in the log. Nothing was changed.', 16, 1) RETURN END

DECLARE @items TABLE (ItemID INT PRIMARY KEY)
INSERT INTO @items (ItemID)
SELECT DISTINCT CAST(LTRIM(RTRIM(s.items)) AS INT)
FROM   dbo.Split(ISNULL(@prmItemIDs, ''), ',') s
WHERE  LTRIM(RTRIM(s.items)) <> '' AND LTRIM(RTRIM(s.items)) NOT LIKE '%[^0-9]%' AND LEN(LTRIM(RTRIM(s.items))) <= 9
IF NOT EXISTS (SELECT 1 FROM @items)
BEGIN RAISERROR('No items were chosen. Nothing was changed.', 16, 1) RETURN END
IF EXISTS (SELECT 1 FROM @items x
           WHERE NOT EXISTS (SELECT 1 FROM dbo.SolutionProjectItem i JOIN dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
                             WHERE i.SolutionProjectItemID = x.ItemID AND p.SolutionID = @prmSolutionID))
BEGIN RAISERROR('One of those items is not in this solution any more. Nothing was changed.', 16, 1) RETURN END

-- the value, read and checked for the field
DECLARE @statusId INT, @isClosed BIT, @statusName NVARCHAR(100), @projectId INT, @projectName NVARCHAR(300),
        @assigneeId INT, @assigneeName NVARCHAR(200), @due DATETIME, @priority INT, @topicId INT, @topicName NVARCHAR(300)
IF @field = 'status'
BEGIN
    SELECT @statusId = SolutionStatusID, @isClosed = IsClosed, @statusName = StatusName FROM dbo.SolutionStatus WHERE StatusCode = LOWER(@value)
    IF @statusId IS NULL BEGIN RAISERROR('That is not a status. Nothing was changed.', 16, 1) RETURN END
END
IF @field = 'project'
BEGIN
    IF @value LIKE '%[^0-9]%' OR @value = N'' OR LEN(@value) > 9 BEGIN RAISERROR('Choose a project to move them to. Nothing was changed.', 16, 1) RETURN END
    SELECT @projectId = SolutionProjectID, @projectName = ProjectName FROM dbo.SolutionProject WHERE SolutionProjectID = CAST(@value AS INT) AND SolutionID = @prmSolutionID
    IF @projectId IS NULL BEGIN RAISERROR('That project is not in this solution. Nothing was changed.', 16, 1) RETURN END
END
IF @field = 'assignee' AND @value <> N''
BEGIN
    SELECT @assigneeId = SolutionMemberID, @assigneeName = MemberName FROM dbo.SolutionMember WHERE MemberCode = LOWER(@value)
    IF @assigneeId IS NULL BEGIN RAISERROR('That is not a member. Nothing was changed.', 16, 1) RETURN END
END
IF @field = 'due' AND @value <> N''
BEGIN
    -- yyyymmdd is read the same under every language and date format setting; yyyy-mm-dd is not
    DECLARE @ymd VARCHAR(8) = REPLACE(CAST(@value AS VARCHAR(10)), '-', '')
    IF @value NOT LIKE '[12][0-9][0-9][0-9]-[01][0-9]-[0-3][0-9]' OR ISDATE(@ymd) = 0
    BEGIN RAISERROR('A due date is written yyyy-mm-dd. Nothing was changed.', 16, 1) RETURN END
    SET @due = CONVERT(DATETIME, @ymd, 112)
END
IF @field = 'priority'
BEGIN
    IF @value NOT IN (N'1', N'2', N'3', N'4', N'5') BEGIN RAISERROR('A priority is 1 to 5. Nothing was changed.', 16, 1) RETURN END
    SET @priority = CAST(@value AS INT)
END
IF @field = 'topic'
BEGIN
    IF @value LIKE '%[^0-9]%' OR @value = N'' OR LEN(@value) > 9 BEGIN RAISERROR('Choose a topic. Nothing was changed.', 16, 1) RETURN END
    SELECT @topicId = SolutionTopicID, @topicName = TopicName FROM dbo.SolutionTopic WHERE SolutionTopicID = CAST(@value AS INT) AND SolutionID = @prmSolutionID
    IF @topicId IS NULL BEGIN RAISERROR('That topic is not in this solution. Nothing was changed.', 16, 1) RETURN END
END

-- only what needs changing
DECLARE @todo TABLE (ItemID INT PRIMARY KEY)
INSERT INTO @todo (ItemID)
SELECT x.ItemID
FROM   @items x JOIN dbo.SolutionProjectItem i ON i.SolutionProjectItemID = x.ItemID
WHERE  (@field = 'status'   AND i.SolutionStatusID <> @statusId)
   OR  (@field = 'project'  AND i.SolutionProjectID <> @projectId)
   OR  (@field = 'assignee' AND ISNULL(i.AssigneeMemberID, 0) <> ISNULL(@assigneeId, 0))
   OR  (@field = 'due'      AND ISNULL(i.DueDate, '19000101') <> ISNULL(@due, '19000101'))
   OR  (@field = 'priority' AND i.Priority <> @priority)
   OR  (@field = 'topic'    AND NOT EXISTS (SELECT 1 FROM dbo.SolutionLink k WHERE k.SolutionID = @prmSolutionID
                                AND ((k.FromKind = 'item' AND k.FromID = x.ItemID AND k.ToKind = 'topic' AND k.ToID = @topicId)
                                  OR (k.FromKind = 'topic' AND k.FromID = @topicId AND k.ToKind = 'item' AND k.ToID = x.ItemID))))
DECLARE @count INT = (SELECT COUNT(*) FROM @todo)
IF @count = 0 BEGIN SELECT 0 AS SolutionLogID, 0 AS ItemCount RETURN END

-- moving: each chosen item whose parent is not also chosen moves, its sub-items with it
DECLARE @roots TABLE (ItemID INT PRIMARY KEY, Rn INT)
DECLARE @moved TABLE (ItemID INT PRIMARY KEY)
IF @field = 'project'
BEGIN
    -- below a chosen item, at any depth: those travel with it and keep their place under it
    DECLARE @below TABLE (ItemID INT PRIMARY KEY)
    ;WITH Down AS (
        SELECT c.SolutionProjectItemID AS ItemID FROM dbo.SolutionProjectItem c JOIN @todo t ON c.ParentItemID = t.ItemID
        UNION ALL
        SELECT c.SolutionProjectItemID FROM dbo.SolutionProjectItem c JOIN Down d ON c.ParentItemID = d.ItemID)
    INSERT INTO @below (ItemID) SELECT DISTINCT ItemID FROM Down
    INSERT INTO @roots (ItemID, Rn)
    SELECT t.ItemID, ROW_NUMBER() OVER (ORDER BY i.SolutionProjectID, i.SortOrder, i.SolutionProjectItemID)
    FROM   @todo t JOIN dbo.SolutionProjectItem i ON i.SolutionProjectItemID = t.ItemID
    WHERE  NOT EXISTS (SELECT 1 FROM @below b WHERE b.ItemID = t.ItemID)
    ;WITH Tree AS (
        SELECT ItemID FROM @roots
        UNION ALL
        SELECT c.SolutionProjectItemID FROM dbo.SolutionProjectItem c JOIN Tree t ON c.ParentItemID = t.ItemID)
    INSERT INTO @moved (ItemID) SELECT DISTINCT ItemID FROM Tree
END

DECLARE @memberId INT = NULL
EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT
DECLARE @what NVARCHAR(600) = CASE @field
    WHEN 'status'   THEN N'status to ' + @statusName
    WHEN 'project'  THEN N'moved to the project ' + @projectName
    WHEN 'assignee' THEN CASE WHEN @assigneeId IS NULL THEN N'given to nobody' ELSE N'given to ' + @assigneeName END
    WHEN 'due'      THEN CASE WHEN @due IS NULL THEN N'due date taken off' ELSE N'due ' + CONVERT(NVARCHAR(10), @due, 101) END
    WHEN 'priority' THEN N'priority ' + @value
    ELSE N'linked to the topic ' + @topicName END
DECLARE @text NVARCHAR(MAX) = N'**Changed ' + CAST(@count AS NVARCHAR(10)) + CASE WHEN @count = 1 THEN N' item' ELSE N' items' END +
    N': ' + @what + N'.**' + NCHAR(10) + NCHAR(10) + N'Why: ' + @reason
DECLARE @logTypeId INT = (SELECT SolutionLogTypeID FROM dbo.SolutionLogType WHERE LogTypeCode = 'note')
DECLARE @logId INT = 0, @now DATETIME = GETDATE()

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    IF @field = 'status'
        UPDATE i SET SolutionStatusID = @statusId,
                     CompletedDate = CASE WHEN @isClosed = 1 THEN ISNULL(i.CompletedDate, @now) ELSE NULL END,
                     ModifiedDate = @now, ModifiedBy = LOWER(@prmMemberCode)
        FROM dbo.SolutionProjectItem i JOIN @todo t ON t.ItemID = i.SolutionProjectItemID
    IF @field = 'status' AND LOWER(@value) <> 'todo'
    BEGIN
        -- work on an item is work on everything above it: what is still Not started above it is now In progress
        DECLARE @todoStatus INT = (SELECT SolutionStatusID FROM dbo.SolutionStatus WHERE StatusCode = 'todo')
        DECLARE @goingStatus INT = (SELECT SolutionStatusID FROM dbo.SolutionStatus WHERE StatusCode = 'inprogress')
        DECLARE @above TABLE (ItemID INT PRIMARY KEY)
        ;WITH Up AS (
            SELECT i.ParentItemID AS ItemID FROM dbo.SolutionProjectItem i JOIN @todo t ON t.ItemID = i.SolutionProjectItemID
            WHERE  i.ParentItemID IS NOT NULL
            UNION ALL
            SELECT p.ParentItemID FROM dbo.SolutionProjectItem p JOIN Up u ON p.SolutionProjectItemID = u.ItemID
            WHERE  p.ParentItemID IS NOT NULL)
        INSERT INTO @above (ItemID) SELECT DISTINCT ItemID FROM Up
        IF @todoStatus IS NOT NULL AND @goingStatus IS NOT NULL
        BEGIN
            UPDATE i SET SolutionStatusID = @goingStatus, ModifiedDate = @now, ModifiedBy = LOWER(@prmMemberCode)
            FROM   dbo.SolutionProjectItem i JOIN @above a ON a.ItemID = i.SolutionProjectItemID
            WHERE  i.SolutionStatusID = @todoStatus AND NOT EXISTS (SELECT 1 FROM @todo t WHERE t.ItemID = i.SolutionProjectItemID)
            UPDATE p SET SolutionStatusID = @goingStatus, ModifiedDate = @now, ModifiedBy = LOWER(@prmMemberCode)
            FROM   dbo.SolutionProject p
            WHERE  p.SolutionStatusID = @todoStatus
              AND  p.SolutionProjectID IN (SELECT i.SolutionProjectID FROM dbo.SolutionProjectItem i JOIN @todo t ON t.ItemID = i.SolutionProjectItemID)
        END
    END
    IF @field = 'assignee'
        UPDATE i SET AssigneeMemberID = @assigneeId, ModifiedDate = @now, ModifiedBy = LOWER(@prmMemberCode)
        FROM dbo.SolutionProjectItem i JOIN @todo t ON t.ItemID = i.SolutionProjectItemID
    IF @field = 'due'
        UPDATE i SET DueDate = @due, ModifiedDate = @now, ModifiedBy = LOWER(@prmMemberCode)
        FROM dbo.SolutionProjectItem i JOIN @todo t ON t.ItemID = i.SolutionProjectItemID
    IF @field = 'priority'
        UPDATE i SET Priority = @priority, ModifiedDate = @now, ModifiedBy = LOWER(@prmMemberCode)
        FROM dbo.SolutionProjectItem i JOIN @todo t ON t.ItemID = i.SolutionProjectItemID
    IF @field = 'project'
    BEGIN
        DECLARE @last INT = ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionProjectItem WITH (UPDLOCK, HOLDLOCK)
                                    WHERE SolutionProjectID = @projectId AND ParentItemID IS NULL), 0)
        UPDATE i SET SolutionProjectID = @projectId, ModifiedDate = @now, ModifiedBy = LOWER(@prmMemberCode)
        FROM dbo.SolutionProjectItem i JOIN @moved m ON m.ItemID = i.SolutionProjectItemID
        UPDATE i SET ParentItemID = NULL, SortOrder = @last + r.Rn
        FROM dbo.SolutionProjectItem i JOIN @roots r ON r.ItemID = i.SolutionProjectItemID
    END
    IF @field = 'topic'
        INSERT INTO dbo.SolutionLink (SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID)
        SELECT @prmSolutionID, 'item', t.ItemID, 'topic', @topicId, @memberId FROM @todo t

    DECLARE @seq INT = ISNULL((SELECT MAX(SequenceNumber) FROM dbo.SolutionLog WITH (UPDLOCK, HOLDLOCK) WHERE SolutionID = @prmSolutionID), 0) + 1
    INSERT INTO dbo.SolutionLog (SolutionID, SolutionProjectID, SolutionProjectItemID, SequenceNumber, SolutionLogTypeID, AuthorMemberID, LogText, LogDate)
    VALUES (@prmSolutionID, CASE WHEN @field = 'project' THEN @projectId ELSE NULL END, NULL, @seq, @logTypeId, @memberId, @text, @now)
    SET @logId = CAST(SCOPE_IDENTITY() AS INT)
    -- one order for every pair, as stp_SolutionLink_Save keeps it: the lower kind first (item before log)
    INSERT INTO dbo.SolutionLink (SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID)
    SELECT @prmSolutionID, 'item', t.ItemID, 'log', @logId, @memberId FROM @todo t
    IF @field = 'topic'
        INSERT INTO dbo.SolutionLink (SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID)
        VALUES (@prmSolutionID, 'log', @logId, 'topic', @topicId, @memberId)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @logId AS SolutionLogID, @count AS ItemCount

GO
