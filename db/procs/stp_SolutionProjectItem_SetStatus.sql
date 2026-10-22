SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProjectItem_SetStatus') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProjectItem_SetStatus
GO
CREATE PROCEDURE dbo.stp_SolutionProjectItem_SetStatus
    @prmSolutionProjectItemID INT,
    @prmStatusCode            VARCHAR(20),
    @prmModifiedBy            VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionProjectItem_SetStatus 1, 'done', 'rex'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: change one item's status by code. Closing stamps CompletedDate; reopening clears
                         it. An unknown status code is refused. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @statusId INT = (SELECT SolutionStatusID FROM dbo.SolutionStatus WHERE StatusCode = LOWER(LTRIM(RTRIM(ISNULL(@prmStatusCode, '')))))
IF @statusId IS NULL BEGIN RAISERROR('That is not a status.', 16, 1) RETURN END
DECLARE @isClosed BIT = (SELECT IsClosed FROM dbo.SolutionStatus WHERE SolutionStatusID = @statusId)

UPDATE dbo.SolutionProjectItem
SET    SolutionStatusID = @statusId,
       CompletedDate = CASE WHEN @isClosed = 1 THEN ISNULL(CompletedDate, GETDATE()) ELSE NULL END,
       ModifiedDate = GETDATE(), ModifiedBy = LOWER(@prmModifiedBy)
WHERE  SolutionProjectItemID = @prmSolutionProjectItemID

SELECT @@ROWCOUNT AS RowsAffected

GO
