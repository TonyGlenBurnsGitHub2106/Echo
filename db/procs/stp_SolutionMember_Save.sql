SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionMember_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionMember_Save
GO
CREATE PROCEDURE dbo.stp_SolutionMember_Save
    @prmSolutionMemberID INT,
    @prmMemberCode       VARCHAR(20),
    @prmMemberName       NVARCHAR(100),
    @prmIsAgent          BIT,
    @prmIsActive         BIT,
    @prmSortOrder        INT
AS

/*

TEST
----

    EXEC stp_SolutionMember_Save 0, 'pat', 'Pat', 0, 1, 0

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: insert (id = 0) or update one member. The code is the stable handle the API uses.
                         A duplicate code is refused with a message written for the user.

*/

SET NOCOUNT ON

SET @prmMemberCode = LOWER(LTRIM(RTRIM(ISNULL(@prmMemberCode, ''))))
SET @prmMemberName = LTRIM(RTRIM(ISNULL(@prmMemberName, '')))
IF @prmMemberCode = '' BEGIN RAISERROR('A member needs a code.', 16, 1) RETURN END
IF @prmMemberName = '' BEGIN RAISERROR('A member needs a name.', 16, 1) RETURN END
IF EXISTS (SELECT 1 FROM dbo.SolutionMember WHERE MemberCode = @prmMemberCode AND SolutionMemberID <> ISNULL(@prmSolutionMemberID, 0)) BEGIN
    RAISERROR('There is already a member with that code.', 16, 1) RETURN
END

IF @prmSolutionMemberID > 0 BEGIN
    UPDATE dbo.SolutionMember
    SET    MemberCode = @prmMemberCode, MemberName = @prmMemberName, IsAgent = ISNULL(@prmIsAgent, 0),
           IsActive = ISNULL(@prmIsActive, 1), SortOrder = ISNULL(@prmSortOrder, 0), ModifiedDate = GETDATE()
    WHERE  SolutionMemberID = @prmSolutionMemberID
END ELSE BEGIN
    INSERT INTO dbo.SolutionMember (MemberCode, MemberName, IsAgent, IsActive, SortOrder)
    VALUES (@prmMemberCode, @prmMemberName, ISNULL(@prmIsAgent, 0), ISNULL(@prmIsActive, 1),
            CASE WHEN ISNULL(@prmSortOrder, 0) > 0 THEN @prmSortOrder ELSE ISNULL((SELECT MAX(SortOrder) FROM dbo.SolutionMember), 0) + 1 END)
    SET @prmSolutionMemberID = SCOPE_IDENTITY()
END

SELECT @prmSolutionMemberID AS SolutionMemberID

GO
