SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestPlan_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestPlan_Save
GO
CREATE PROCEDURE dbo.stp_SolutionTestPlan_Save
    @prmSolutionTestPlanID INT,
    @prmSolutionID         INT,
    @prmPlanTitle          NVARCHAR(200),
    @prmPlanDescription    NVARCHAR(2000),
    @prmSortOrder          INT         = NULL,   -- NULL = keep (edit) / last (new)
    @prmModifiedBy         VARCHAR(40) = NULL,
    @prmPlanKindCode       VARCHAR(10) = NULL    -- dev | qa; new checklists only (NULL = dev). An edit keeps the kind.
AS

/*

TEST
----

    EXEC stp_SolutionTestPlan_Save 0, 1, N'Release test', NULL, NULL, 'felix'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: add (id = 0) or edit one testing checklist. A new checklist starts with the
                         two standard columns, Manual and Unit tested, in one transaction. An edit never moves a
                         checklist to another solution. Returns SolutionTestPlanID.
10/15/2026  Felix        The QA tab: @prmPlanKindCode (dev | qa) on a new checklist. A QA checklist starts with ONE column,
                         Passed (a tester ticks what passed; Failing is already its own column). An unknown kind is refused,
                         never guessed. An edit never changes the kind.

*/

SET NOCOUNT ON

SET @prmPlanTitle = LTRIM(RTRIM(ISNULL(@prmPlanTitle, N'')))
IF @prmPlanTitle = N'' BEGIN RAISERROR('A testing checklist needs a title.', 16, 1) RETURN END
SET @prmPlanDescription = NULLIF(LTRIM(RTRIM(ISNULL(@prmPlanDescription, N''))), N'')
SET @prmPlanKindCode = LOWER(LTRIM(RTRIM(ISNULL(@prmPlanKindCode, 'dev'))))
IF @prmPlanKindCode NOT IN ('dev', 'qa') BEGIN RAISERROR('A checklist kind is dev or qa. Nothing was saved.', 16, 1) RETURN END

IF @prmSolutionTestPlanID > 0 BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTestPlan WHERE SolutionTestPlanID = @prmSolutionTestPlanID) BEGIN
        RAISERROR('That testing checklist no longer exists.', 16, 1) RETURN
    END
    UPDATE dbo.SolutionTestPlan
    SET    PlanTitle = @prmPlanTitle, PlanDescription = @prmPlanDescription,
           SortOrder = ISNULL(@prmSortOrder, SortOrder), ModifiedDate = GETDATE(), ModifiedBy = @prmModifiedBy
    WHERE  SolutionTestPlanID = @prmSolutionTestPlanID
END ELSE BEGIN
    IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
        RAISERROR('That solution no longer exists.', 16, 1) RETURN
    END
    DECLARE @manual INT, @canned INT, @passed INT
    IF @prmPlanKindCode = 'qa'
        EXEC dbo.stp_SolutionTestLevel_FindOrCreate N'Passed', @passed OUTPUT
    ELSE BEGIN
        EXEC dbo.stp_SolutionTestLevel_FindOrCreate N'Manual', @manual OUTPUT
        EXEC dbo.stp_SolutionTestLevel_FindOrCreate N'Unit tested', @canned OUTPUT
    END

    BEGIN TRY
        BEGIN TRANSACTION
        INSERT INTO dbo.SolutionTestPlan (SolutionID, PlanTitle, PlanDescription, SortOrder, ModifiedBy, PlanKindCode)
        VALUES (@prmSolutionID, @prmPlanTitle, @prmPlanDescription,
                ISNULL(@prmSortOrder, ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionTestPlan WHERE SolutionID = @prmSolutionID), 0) + 1),
                @prmModifiedBy, @prmPlanKindCode)
        SET @prmSolutionTestPlanID = SCOPE_IDENTITY()
        IF @prmPlanKindCode = 'qa'
            INSERT INTO dbo.SolutionTestPlanLevel (SolutionTestPlanID, SolutionTestLevelID, SortOrder) VALUES (@prmSolutionTestPlanID, @passed, 1)
        ELSE BEGIN
            INSERT INTO dbo.SolutionTestPlanLevel (SolutionTestPlanID, SolutionTestLevelID, SortOrder) VALUES (@prmSolutionTestPlanID, @manual, 1)
            INSERT INTO dbo.SolutionTestPlanLevel (SolutionTestPlanID, SolutionTestLevelID, SortOrder) VALUES (@prmSolutionTestPlanID, @canned, 2)
        END
        COMMIT TRANSACTION
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
        DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
        RAISERROR(@msg, 16, 1)
        RETURN
    END CATCH
END

SELECT @prmSolutionTestPlanID AS SolutionTestPlanID


GO
