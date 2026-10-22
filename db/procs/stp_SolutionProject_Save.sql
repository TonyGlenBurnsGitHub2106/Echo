SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProject_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProject_Save
GO
CREATE PROCEDURE dbo.stp_SolutionProject_Save
    @prmSolutionProjectID  INT,
    @prmSolutionID         INT,
    @prmProjectName        NVARCHAR(200),
    @prmProjectDescription NVARCHAR(MAX),
    @prmStatusCode         VARCHAR(20),
    @prmOwnerCode          VARCHAR(20),
    @prmSortOrder          INT,
    @prmModifiedBy         VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionProject_Save 0, 1, 'Sample project', NULL, 'todo', NULL, 0, 'felix'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: insert (id = 0) or update one project of a solution. A project never changes
                         solution on update. A duplicate name inside the same solution is refused.

*/

SET NOCOUNT ON

SET @prmProjectName = LTRIM(RTRIM(ISNULL(@prmProjectName, '')))
IF @prmProjectName = '' BEGIN RAISERROR('A project needs a name.', 16, 1) RETURN END
IF @prmSolutionProjectID > 0 SET @prmSolutionID = (SELECT SolutionID FROM dbo.SolutionProject WHERE SolutionProjectID = @prmSolutionProjectID)
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists.', 16, 1) RETURN
END
IF EXISTS (SELECT 1 FROM dbo.SolutionProject WHERE SolutionID = @prmSolutionID AND ProjectName = @prmProjectName
           AND SolutionProjectID <> ISNULL(@prmSolutionProjectID, 0)) BEGIN
    RAISERROR('This solution already has a project with that name.', 16, 1) RETURN
END

DECLARE @statusId INT = (SELECT SolutionStatusID FROM dbo.SolutionStatus WHERE StatusCode = LOWER(LTRIM(RTRIM(ISNULL(@prmStatusCode, '')))))
IF @statusId IS NULL SET @statusId = (SELECT TOP 1 SolutionStatusID FROM dbo.SolutionStatus ORDER BY SortOrder, SolutionStatusID)
DECLARE @ownerId INT = (SELECT SolutionMemberID FROM dbo.SolutionMember WHERE MemberCode = LOWER(LTRIM(RTRIM(ISNULL(@prmOwnerCode, '')))))

IF @prmSolutionProjectID > 0 BEGIN
    UPDATE dbo.SolutionProject
    SET    ProjectName = @prmProjectName, ProjectDescription = @prmProjectDescription, SolutionStatusID = @statusId,
           OwnerMemberID = @ownerId, SortOrder = ISNULL(@prmSortOrder, 0), ModifiedDate = GETDATE(), ModifiedBy = @prmModifiedBy
    WHERE  SolutionProjectID = @prmSolutionProjectID
END ELSE BEGIN
    INSERT INTO dbo.SolutionProject (SolutionID, ProjectName, ProjectDescription, SolutionStatusID, OwnerMemberID, SortOrder, ModifiedBy)
    VALUES (@prmSolutionID, @prmProjectName, @prmProjectDescription, @statusId, @ownerId,
            CASE WHEN ISNULL(@prmSortOrder, 0) > 0 THEN @prmSortOrder
                 ELSE ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionProject WHERE SolutionID = @prmSolutionID), 0) + 1 END,
            @prmModifiedBy)
    SET @prmSolutionProjectID = SCOPE_IDENTITY()
END

SELECT @prmSolutionProjectID AS SolutionProjectID

GO
