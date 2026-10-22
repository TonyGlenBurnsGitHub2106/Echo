SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTermType_Rename') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTermType_Rename
GO
CREATE PROCEDURE dbo.stp_SolutionTermType_Rename
    @prmSolutionID INT,
    @prmID         INT,
    @prmName       NVARCHAR(4000)
AS

/*

TEST
----

    EXEC stp_SolutionTermType_Rename 6, 0, N'New name'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Solution Terms tab: rename one type of one solution. Refused when it is not this solution's, when
                         the name is blank or too long, or when the solution already has a type by that name. Every term
                         keeps it -- they point at it by id. Returns SolutionTermTypeID.

*/

SET NOCOUNT ON

DECLARE @name NVARCHAR(4000) = LTRIM(RTRIM(ISNULL(@prmName, N'')))
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTermType WHERE SolutionTermTypeID = @prmID AND SolutionID = @prmSolutionID)
BEGIN RAISERROR('That type is not part of this solution. Nothing was saved.', 16, 1) RETURN END
IF @name = N'' BEGIN RAISERROR('A type needs a name. Nothing was saved.', 16, 1) RETURN END
IF LEN(@name) > 200 BEGIN RAISERROR('A type name can be at most 200 characters. Nothing was saved.', 16, 1) RETURN END
IF EXISTS (SELECT 1 FROM dbo.SolutionTermType WHERE SolutionID = @prmSolutionID AND TypeName = @name AND SolutionTermTypeID <> @prmID)
BEGIN RAISERROR('This solution already has a type by that name. Nothing was saved.', 16, 1) RETURN END

BEGIN TRY
    UPDATE dbo.SolutionTermType SET TypeName = @name, ModifiedDate = GETDATE() WHERE SolutionTermTypeID = @prmID
END TRY
BEGIN CATCH
    DECLARE @msg NVARCHAR(2000) = CASE WHEN ERROR_NUMBER() IN (2601, 2627)
        THEN N'This solution already has a type by that name. Nothing was saved.' ELSE ERROR_MESSAGE() END
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @prmID AS SolutionTermTypeID

GO
