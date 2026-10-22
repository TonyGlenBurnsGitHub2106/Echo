SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionMember_FindOrCreate') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionMember_FindOrCreate
GO
CREATE PROCEDURE dbo.stp_SolutionMember_FindOrCreate
    @prmMemberCode       VARCHAR(20),
    @prmSolutionMemberID INT OUTPUT
AS

/*

TEST
----

    DECLARE @id INT; EXEC stp_SolutionMember_FindOrCreate 'rex', @id OUTPUT; SELECT @id

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: resolve a member code to its id, creating the member when the code is new (an API
                         client writing for the first time). Reports through the OUTPUT parameter and returns
                         no result set, so a save proc can call it without leaking rows to its own caller.

*/

SET NOCOUNT ON

SET @prmSolutionMemberID = NULL
SET @prmMemberCode = LOWER(LTRIM(RTRIM(ISNULL(@prmMemberCode, ''))))
IF @prmMemberCode = '' RETURN

SELECT @prmSolutionMemberID = SolutionMemberID FROM dbo.SolutionMember WHERE MemberCode = @prmMemberCode
IF @prmSolutionMemberID IS NULL BEGIN
    INSERT INTO dbo.SolutionMember (MemberCode, MemberName, IsAgent, IsActive, SortOrder)
    VALUES (@prmMemberCode, UPPER(LEFT(@prmMemberCode, 1)) + SUBSTRING(@prmMemberCode, 2, 100), 1, 1,
            ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionMember), 0) + 1)
    SET @prmSolutionMemberID = SCOPE_IDENTITY()
END

GO
