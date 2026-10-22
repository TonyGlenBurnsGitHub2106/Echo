SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProjectItem_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProjectItem_Save
GO
CREATE PROCEDURE dbo.stp_SolutionProjectItem_Save
    @prmSolutionProjectItemID INT,
    @prmSolutionProjectID     INT,
    @prmParentItemID          INT,
    @prmItemTitle             NVARCHAR(400),
    @prmItemDetail            NVARCHAR(MAX),
    @prmStatusCode            VARCHAR(20),
    @prmAssigneeCode          VARCHAR(20),
    @prmPriority              INT,
    @prmSortOrder             INT,
    @prmDueDate               DATETIME,
    @prmAuthorCode            VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionProjectItem_Save 0, 1, NULL, 'Sample item', NULL, 'todo', 'rex', 3, 0, NULL, 'felix'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: insert (id = 0) or update one item. Status, assignee and author arrive as CODES. A
                         parent must live in the same project, and an item may not become its own descendant.
                         Closing an item stamps CompletedDate; reopening clears it. A new item lands last among
                         its siblings unless a sort order is given. Returns the id as SolutionProjectItemID.

*/

SET NOCOUNT ON

SET @prmItemTitle = LTRIM(RTRIM(ISNULL(@prmItemTitle, '')))
IF @prmItemTitle = '' BEGIN RAISERROR('An item needs a title.', 16, 1) RETURN END
IF ISNULL(@prmParentItemID, 0) <= 0 SET @prmParentItemID = NULL
-- An edit never changes project (stp_SolutionProjectItem_Move does): the item's own project always wins,
-- so a parent can never be taken from a project the caller merely named.
IF @prmSolutionProjectItemID > 0
    SET @prmSolutionProjectID = (SELECT SolutionProjectID FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = @prmSolutionProjectItemID)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionProject WHERE SolutionProjectID = @prmSolutionProjectID) BEGIN
    RAISERROR('That project no longer exists.', 16, 1) RETURN
END
IF @prmParentItemID IS NOT NULL BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.SolutionProjectItem WHERE SolutionProjectItemID = @prmParentItemID AND SolutionProjectID = @prmSolutionProjectID) BEGIN
        RAISERROR('The parent item must be in the same project.', 16, 1) RETURN
    END
    IF @prmSolutionProjectItemID > 0 BEGIN
        IF @prmParentItemID = @prmSolutionProjectItemID BEGIN
            RAISERROR('An item cannot be its own parent.', 16, 1) RETURN
        END
        DECLARE @isDescendant BIT = 0
        ;WITH Tree AS (
            SELECT SolutionProjectItemID FROM dbo.SolutionProjectItem WHERE ParentItemID = @prmSolutionProjectItemID
            UNION ALL
            SELECT c.SolutionProjectItemID FROM dbo.SolutionProjectItem c JOIN Tree t ON c.ParentItemID = t.SolutionProjectItemID)
        SELECT @isDescendant = 1 FROM Tree WHERE SolutionProjectItemID = @prmParentItemID
        IF @isDescendant = 1 BEGIN
            RAISERROR('An item cannot be moved under one of its own sub-items.', 16, 1) RETURN
        END
    END
END

DECLARE @statusId INT = (SELECT SolutionStatusID FROM dbo.SolutionStatus WHERE StatusCode = LOWER(LTRIM(RTRIM(ISNULL(@prmStatusCode, '')))))
IF @statusId IS NULL SET @statusId = (SELECT TOP 1 SolutionStatusID FROM dbo.SolutionStatus ORDER BY SortOrder, SolutionStatusID)
DECLARE @isClosed BIT = (SELECT IsClosed FROM dbo.SolutionStatus WHERE SolutionStatusID = @statusId)
DECLARE @assigneeId INT = (SELECT SolutionMemberID FROM dbo.SolutionMember WHERE MemberCode = LOWER(LTRIM(RTRIM(ISNULL(@prmAssigneeCode, '')))))
DECLARE @authorId INT
EXEC dbo.stp_SolutionMember_FindOrCreate @prmAuthorCode, @authorId OUTPUT
IF ISNULL(@prmPriority, 0) NOT BETWEEN 1 AND 5 SET @prmPriority = 3

IF @prmSolutionProjectItemID > 0 BEGIN
    UPDATE dbo.SolutionProjectItem
    SET    ParentItemID = @prmParentItemID, ItemTitle = @prmItemTitle, ItemDetail = @prmItemDetail,
           SolutionStatusID = @statusId, AssigneeMemberID = @assigneeId, Priority = @prmPriority,
           SortOrder = CASE WHEN ISNULL(@prmSortOrder, 0) > 0 THEN @prmSortOrder ELSE SortOrder END,
           DueDate = @prmDueDate,
           CompletedDate = CASE WHEN @isClosed = 1 THEN ISNULL(CompletedDate, GETDATE()) ELSE NULL END,
           ModifiedDate = GETDATE(), ModifiedBy = LOWER(@prmAuthorCode)
    WHERE  SolutionProjectItemID = @prmSolutionProjectItemID
END ELSE BEGIN
    INSERT INTO dbo.SolutionProjectItem (SolutionProjectID, ParentItemID, ItemTitle, ItemDetail, SolutionStatusID,
                                         AssigneeMemberID, Priority, SortOrder, DueDate, CompletedDate, CreatedByMemberID, ModifiedBy)
    VALUES (@prmSolutionProjectID, @prmParentItemID, @prmItemTitle, @prmItemDetail, @statusId, @assigneeId, @prmPriority,
            CASE WHEN ISNULL(@prmSortOrder, 0) > 0 THEN @prmSortOrder
                 ELSE ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionProjectItem
                              WHERE SolutionProjectID = @prmSolutionProjectID AND ISNULL(ParentItemID, 0) = ISNULL(@prmParentItemID, 0)), 0) + 1 END,
            @prmDueDate, CASE WHEN @isClosed = 1 THEN GETDATE() ELSE NULL END, @authorId, LOWER(@prmAuthorCode))
    SET @prmSolutionProjectItemID = SCOPE_IDENTITY()
END

SELECT @prmSolutionProjectItemID AS SolutionProjectItemID

GO
