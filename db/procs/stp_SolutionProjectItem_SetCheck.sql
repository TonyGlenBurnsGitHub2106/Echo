SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionProjectItem_SetCheck') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionProjectItem_SetCheck
GO
CREATE PROCEDURE dbo.stp_SolutionProjectItem_SetCheck
    @prmSolutionProjectItemID INT,
    @prmCheckCode             VARCHAR(20),
    @prmResultCode            VARCHAR(10),
    @prmModifiedBy            VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_SolutionProjectItem_SetCheck 1, 'reviewed', 'pass', 'tony'
    EXEC stp_SolutionProjectItem_SetCheck 1, 'automated', 'wontdo', 'tony'
    EXEC stp_SolutionProjectItem_SetCheck 1, 'qa', NULL, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/06/2026  Felix        Story 5653: set one of an item's three check boxes. @prmCheckCode is reviewed, qa or
                         automated. Reviewed and QA take pass or fail; Automated takes done or wontdo; NULL or ''
                         empties the box (not done). Anything else is refused before the write. Returns
                         RowsAffected (0 = no such item).

*/

SET NOCOUNT ON

DECLARE @check  VARCHAR(20) = LOWER(LTRIM(RTRIM(ISNULL(@prmCheckCode, ''))))
DECLARE @result VARCHAR(10) = NULLIF(LOWER(LTRIM(RTRIM(ISNULL(@prmResultCode, '')))), '')

IF @check NOT IN ('reviewed', 'qa', 'automated')
BEGIN RAISERROR('That is not a check. Use reviewed, qa or automated. Nothing was saved.', 16, 1) RETURN END
IF @check IN ('reviewed', 'qa') AND @result IS NOT NULL AND @result NOT IN ('pass', 'fail')
BEGIN RAISERROR('Reviewed and QA take pass, fail or nothing (not done). Nothing was saved.', 16, 1) RETURN END
IF @check = 'automated' AND @result IS NOT NULL AND @result NOT IN ('done', 'wontdo')
BEGIN RAISERROR('Automated takes done, wontdo or nothing (not done). Nothing was saved.', 16, 1) RETURN END

UPDATE dbo.SolutionProjectItem
SET    ReviewedCode  = CASE WHEN @check = 'reviewed'  THEN @result ELSE ReviewedCode  END,
       QaCode        = CASE WHEN @check = 'qa'        THEN @result ELSE QaCode        END,
       AutomatedCode = CASE WHEN @check = 'automated' THEN @result ELSE AutomatedCode END,
       ModifiedDate = GETDATE(), ModifiedBy = LOWER(@prmModifiedBy)
WHERE  SolutionProjectItemID = @prmSolutionProjectItemID

SELECT @@ROWCOUNT AS RowsAffected

GO
