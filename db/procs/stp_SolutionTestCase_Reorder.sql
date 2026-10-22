SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTestCase_Reorder') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTestCase_Reorder
GO
CREATE PROCEDURE dbo.stp_SolutionTestCase_Reorder
    @prmSolutionTestGroupID INT,
    @prmOrderedIDs          NVARCHAR(MAX)    -- the group's case ids in their new order, comma separated
AS

/*

TEST
----

    EXEC stp_SolutionTestCase_Reorder 1, N'3,1,2'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/21/2026  Felix        Solution testing checklists: put the cases of one group in the order given (drag to reorder). Ids that
                         are not in this group are ignored; any case of the group left out keeps its place after the ones given.
                         Returns RowsAffected.

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
UPDATE c
SET    SortOrder = CASE WHEN i.Position IS NOT NULL THEN i.Position ELSE @n + c.SortOrder END, ModifiedDate = GETDATE()
FROM   dbo.SolutionTestCase c
LEFT JOIN (SELECT ItemID, MIN(Position) AS Position FROM @ids GROUP BY ItemID) i ON i.ItemID = c.SolutionTestCaseID
WHERE  c.SolutionTestGroupID = @prmSolutionTestGroupID

SELECT @@ROWCOUNT AS RowsAffected

GO
