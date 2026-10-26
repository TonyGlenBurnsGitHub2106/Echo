SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Solution_Save') IS NOT NULL DROP PROCEDURE dbo.stp_Solution_Save
GO
CREATE PROCEDURE dbo.stp_Solution_Save
    @prmSolutionID          INT,
    @prmSolutionName        NVARCHAR(200),
    @prmSolutionDescription NVARCHAR(MAX),
    @prmStatusCode          VARCHAR(20),
    @prmOwnerCode           VARCHAR(20),
    @prmStatusSummary       NVARCHAR(2000),
    @prmTargetDate          DATETIME,
    @prmIsArchived          BIT,
    @prmSortOrder           INT,
    @prmModifiedBy          VARCHAR(20),
    @prmSolutionAlias       VARCHAR(40) = NULL
AS

/*

TEST
----

    EXEC stp_Solution_Save 0, 'Sample solution', 'What it is.', 'todo', 'tony', NULL, NULL, 0, 0, 'felix', 'sample'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: insert (id = 0) or update one solution. Status and owner arrive as CODES and are
                         resolved here; an unknown status falls back to the first one, an unknown owner to none.
                         A duplicate name is refused. Returns the id as SolutionID.
09/17/2026  Felix        Story 3493: @prmSolutionAlias -- the short handle a person says in place of the name. Stored
                         lower case; letters, digits and hyphens; blank clears it; a duplicate is refused.
10/01/2026  Felix        Story 5566: a new solution is a true entity -- its EntityID comes from stp_Entity_Create in
                         the same transaction as the insert (SET XACT_ABORT ON, so a failed insert rolls the new
                         entity back with it). An update never touches EntityID.

*/

SET NOCOUNT ON
SET XACT_ABORT ON      -- a failed insert rolls the new entity back with it

SET @prmSolutionName = LTRIM(RTRIM(ISNULL(@prmSolutionName, '')))
IF @prmSolutionName = '' BEGIN RAISERROR('A solution needs a name.', 16, 1) RETURN END
IF EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionName = @prmSolutionName AND SolutionID <> ISNULL(@prmSolutionID, 0)) BEGIN
    RAISERROR('There is already a solution with that name.', 16, 1) RETURN
END

SET @prmSolutionAlias = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmSolutionAlias, '')))), '')
IF @prmSolutionAlias IS NOT NULL BEGIN
    IF @prmSolutionAlias LIKE '%[^a-z0-9-]%' OR @prmSolutionAlias NOT LIKE '%[a-z]%' BEGIN
        RAISERROR('An alias is letters, digits and hyphens, with at least one letter.', 16, 1) RETURN
    END
    IF EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionAlias = @prmSolutionAlias AND SolutionID <> ISNULL(@prmSolutionID, 0)) BEGIN
        RAISERROR('Another solution already uses that alias.', 16, 1) RETURN
    END
END

DECLARE @statusId INT = (SELECT SolutionStatusID FROM dbo.SolutionStatus WHERE StatusCode = LOWER(LTRIM(RTRIM(ISNULL(@prmStatusCode, '')))))
IF @statusId IS NULL SET @statusId = (SELECT TOP 1 SolutionStatusID FROM dbo.SolutionStatus ORDER BY SortOrder, SolutionStatusID)
DECLARE @ownerId INT = (SELECT SolutionMemberID FROM dbo.SolutionMember WHERE MemberCode = LOWER(LTRIM(RTRIM(ISNULL(@prmOwnerCode, '')))))

IF @prmSolutionID > 0 BEGIN
    UPDATE dbo.Solution
    SET    SolutionName = @prmSolutionName, SolutionDescription = @prmSolutionDescription, SolutionStatusID = @statusId,
           OwnerMemberID = @ownerId, StatusSummary = NULLIF(LTRIM(RTRIM(ISNULL(@prmStatusSummary, ''))), ''),
           TargetDate = @prmTargetDate, IsArchived = ISNULL(@prmIsArchived, 0), SortOrder = ISNULL(@prmSortOrder, 0),
           SolutionAlias = @prmSolutionAlias,
           ModifiedDate = GETDATE(), ModifiedBy = @prmModifiedBy
    WHERE  SolutionID = @prmSolutionID
END ELSE BEGIN
    DECLARE @entityTypeId INT, @entityId INT
    SELECT @entityTypeId = EntityTypeID FROM dbo.tblEntityType WHERE EntityTable = 'Solution'
    IF @entityTypeId IS NULL BEGIN RAISERROR('The Solution entity type is missing. Nothing was saved.', 16, 1) RETURN END

    BEGIN TRANSACTION

    EXEC dbo.stp_Entity_Create @prmEntityTypeID = @entityTypeId,
                               @prmTempTypeDescription = 'Solution',
                               @prmOutPutIdentity = @entityId OUTPUT

    INSERT INTO dbo.Solution (EntityID, SolutionName, SolutionDescription, SolutionStatusID, OwnerMemberID, StatusSummary,
                              TargetDate, IsArchived, SortOrder, ModifiedBy, SolutionAlias)
    VALUES (@entityId, @prmSolutionName, @prmSolutionDescription, @statusId, @ownerId, NULLIF(LTRIM(RTRIM(ISNULL(@prmStatusSummary, ''))), ''),
            @prmTargetDate, ISNULL(@prmIsArchived, 0),
            CASE WHEN ISNULL(@prmSortOrder, 0) > 0 THEN @prmSortOrder ELSE ISNULL((SELECT MAX(SortOrder) FROM dbo.Solution), 0) + 1 END,
            @prmModifiedBy, @prmSolutionAlias)
    SET @prmSolutionID = SCOPE_IDENTITY()

    COMMIT TRANSACTION
END

SELECT @prmSolutionID AS SolutionID

GO
