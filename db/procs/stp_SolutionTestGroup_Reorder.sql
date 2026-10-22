SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestGroup_Reorder') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestGroup_Reorder
GO
CREATE PROCEDURE dbo.stp_SolutionTestGroup_Reorder
    @prmSolutionTestPlanID INT,
    @prmOrderedIDs         NVARCHAR(MAX)    -- the checklist's group ids in their new order, comma separated
AS

/*

TEST
----

    EXEC stp_SolutionTestGroup_Reorder 1, N'2,1'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: put the groups of one testing checklist in the order given (drag to
                         reorder). Ids that are not in this checklist are ignored; any group left out keeps its place after the
                         ones given. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @ids TABLE (Position INT IDENTITY(1,1), ItemID INT)
DECLARE @rest NVARCHAR(MAX) = @prmOrderedIDs, @piece NVARCHAR(20), @cut INT
WHILE @rest IS NOT NULL AND LEN(@rest) > 0 BEGIN
    SET @cut = CHARINDEX(N',', @rest)
    IF @cut = 0 BEGIN SET @piece = @rest SET @rest = NULL END
    ELSE BEGIN SET @piece = LEFT(@rest, @cut - 1) SET @rest = SUBSTRING(@rest, @cut + 1, 2147483647) END
    SET @piece = LTRIM(RTRIM(@piece))
    IF @piece <> N'' AND @piece NOT LIKE N'%[^0-9]%' INSERT INTO @ids (ItemID) VALUES (CAST(@piece AS INT))
END

DECLARE @n INT = (SELECT COUNT(*) FROM @ids)
UPDATE g
SET    SortOrder = CASE WHEN i.Position IS NOT NULL THEN i.Position ELSE @n + g.SortOrder END, ModifiedDate = GETDATE()
FROM   dbo.SolutionTestGroup g
LEFT JOIN (SELECT ItemID, MIN(Position) AS Position FROM @ids GROUP BY ItemID) i ON i.ItemID = g.SolutionTestGroupID
WHERE  g.SolutionTestPlanID = @prmSolutionTestPlanID

SELECT @@ROWCOUNT AS RowsAffected

GO
