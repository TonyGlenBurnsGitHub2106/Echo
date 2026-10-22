SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTerm_SetFavorite') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTerm_SetFavorite
GO
CREATE PROCEDURE dbo.stp_SolutionTerm_SetFavorite
    @prmSolutionTermID INT,
    @prmIsFavorite     BIT,
    @prmMemberCode     VARCHAR(20) = NULL
AS

/*

TEST
----

    EXEC stp_SolutionTerm_SetFavorite 1, 1, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Solution Terms tab: star or unstar one term. Setting the value it already has is harmless,
                         so a double tap cannot flip it twice. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

UPDATE dbo.SolutionTerm
SET    IsFavorite = ISNULL(@prmIsFavorite, 0),
       ModifiedByMemberID = ISNULL(@memberId, ModifiedByMemberID),
       ModifiedDate = GETDATE()
WHERE  SolutionTermID = @prmSolutionTermID

SELECT @@ROWCOUNT AS RowsAffected

GO
