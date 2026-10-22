SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestCase_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestCase_Save
GO
CREATE PROCEDURE dbo.stp_SolutionTestCase_Save
    @prmSolutionTestCaseID  INT,
    @prmSolutionTestGroupID INT,
    @prmCaseText            NVARCHAR(1000),
    @prmCaseNote            NVARCHAR(2000),
    @prmSortOrder           INT = NULL,     -- NULL = keep (edit, same group) / last
    @prmIncludeInPrint      BIT = NULL      -- NULL = keep (edit) / on (new)
AS

/*

TEST
----

    EXEC stp_SolutionTestCase_Save 0, 1, N'Start a batch with no files waiting', NULL, NULL

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: add (id = 0) or edit one case. An edit may move the case to another group,
                         but only one in the same checklist; a moved case goes to the end of its new group unless a sort order is
                         given. Returns SolutionTestCaseID.
09/21/2026  Felix        Print: @prmIncludeInPrint (NULL = keep on an edit, on for a new case) -- whether the case appears
                         on the printed checklist.

*/

SET NOCOUNT ON

SET @prmCaseText = LTRIM(RTRIM(ISNULL(@prmCaseText, N'')))
IF @prmCaseText = N'' BEGIN RAISERROR('A test case needs some text.', 16, 1) RETURN END
SET @prmCaseNote = NULLIF(LTRIM(RTRIM(ISNULL(@prmCaseNote, N''))), N'')

DECLARE @targetPlan INT = (SELECT SolutionTestPlanID FROM dbo.SolutionTestGroup WHERE SolutionTestGroupID = @prmSolutionTestGroupID)

IF @prmSolutionTestCaseID > 0 BEGIN
    DECLARE @oldGroup INT, @oldPlan INT
    SELECT @oldGroup = c.SolutionTestGroupID, @oldPlan = g.SolutionTestPlanID
    FROM   dbo.SolutionTestCase c JOIN dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID
    WHERE  c.SolutionTestCaseID = @prmSolutionTestCaseID
    IF @oldGroup IS NULL BEGIN RAISERROR('That test case no longer exists.', 16, 1) RETURN END
    IF ISNULL(@prmSolutionTestGroupID, 0) <= 0 BEGIN SET @prmSolutionTestGroupID = @oldGroup SET @targetPlan = @oldPlan END
    IF ISNULL(@targetPlan, 0) <> @oldPlan BEGIN
        RAISERROR('A test case can only move to a group in the same checklist.', 16, 1) RETURN
    END
    IF @prmSolutionTestGroupID <> @oldGroup AND @prmSortOrder IS NULL
        SET @prmSortOrder = ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionTestCase WHERE SolutionTestGroupID = @prmSolutionTestGroupID), 0) + 1
    UPDATE dbo.SolutionTestCase
    SET    SolutionTestGroupID = @prmSolutionTestGroupID, CaseText = @prmCaseText, CaseNote = @prmCaseNote,
           SortOrder = ISNULL(@prmSortOrder, SortOrder), IncludeInPrint = ISNULL(@prmIncludeInPrint, IncludeInPrint),
           ModifiedDate = GETDATE()
    WHERE  SolutionTestCaseID = @prmSolutionTestCaseID
END ELSE BEGIN
    IF @targetPlan IS NULL BEGIN RAISERROR('That group no longer exists.', 16, 1) RETURN END
    INSERT INTO dbo.SolutionTestCase (SolutionTestGroupID, CaseText, CaseNote, SortOrder, IncludeInPrint)
    VALUES (@prmSolutionTestGroupID, @prmCaseText, @prmCaseNote,
            ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionTestCase WHERE SolutionTestGroupID = @prmSolutionTestGroupID), 0) + 1),
            ISNULL(@prmIncludeInPrint, 1))
    SET @prmSolutionTestCaseID = SCOPE_IDENTITY()
END

SELECT @prmSolutionTestCaseID AS SolutionTestCaseID

GO
