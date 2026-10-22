SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGrid_ImportFromConfig') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGrid_ImportFromConfig
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGrid_ImportFromConfig
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGrid_ImportFromConfig 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: copy a solution's Configuration tab into its empty Configuration II
                         tab -- each environment becomes a column, and the groups, properties, values, sources, other places and
                         notes come across as they are (every cell observed). The Configuration tab itself is only READ; nothing in
                         it changes. Refused when Configuration II already has a column or a group, or when there is nothing to
                         copy. Returns the counts copied.

*/

SET NOCOUNT ON

IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('That solution no longer exists. Nothing was copied.', 16, 1) RETURN
END
IF EXISTS (SELECT 1 FROM dbo.SolutionConfigGridColumn WHERE SolutionID = @prmSolutionID)
OR EXISTS (SELECT 1 FROM dbo.SolutionConfigGridGroup  WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('Configuration II already has columns or groups, so the Configuration tab can only be copied into an empty one. Nothing was copied.', 16, 1) RETURN
END
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigEnvironment WHERE SolutionID = @prmSolutionID) BEGIN
    RAISERROR('The Configuration tab of this solution has nothing in it to copy. Nothing was copied.', 16, 1) RETURN
END
DECLARE @observed INT = (SELECT SolutionConfigGridCellStateID FROM dbo.SolutionConfigGridCellState WHERE StateCode = 'observed')
IF @observed IS NULL BEGIN RAISERROR('The cell states are missing (Data_SolutionConfigGridCellState.sql). Nothing was copied.', 16, 1) RETURN END

DECLARE @colMap   TABLE (OldID INT PRIMARY KEY, NewID INT)
DECLARE @groupMap TABLE (OldID INT PRIMARY KEY, NewID INT)
DECLARE @propMap  TABLE (OldID INT PRIMARY KEY, NewID INT)
DECLARE @valMap   TABLE (OldID INT PRIMARY KEY, NewID INT)
DECLARE @oldId INT, @newId INT

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION

    DECLARE envCur CURSOR LOCAL FAST_FORWARD FOR
        SELECT ce.SolutionConfigEnvironmentID FROM dbo.SolutionConfigEnvironment ce
        WHERE ce.SolutionID = @prmSolutionID ORDER BY ce.SortOrder, ce.SolutionConfigEnvironmentID
    OPEN envCur
    FETCH NEXT FROM envCur INTO @oldId
    WHILE @@FETCH_STATUS = 0 BEGIN
        INSERT INTO dbo.SolutionConfigGridColumn (SolutionID, ColumnTitle, SortOrder)
        SELECT @prmSolutionID, LEFT(e.EnvironmentName, 100), ce.SortOrder
        FROM   dbo.SolutionConfigEnvironment ce JOIN dbo.SolutionEnvironment e ON e.SolutionEnvironmentID = ce.SolutionEnvironmentID
        WHERE  ce.SolutionConfigEnvironmentID = @oldId
        INSERT INTO @colMap (OldID, NewID) VALUES (@oldId, SCOPE_IDENTITY())
        FETCH NEXT FROM envCur INTO @oldId
    END
    CLOSE envCur
    DEALLOCATE envCur

    DECLARE groupCur CURSOR LOCAL FAST_FORWARD FOR
        SELECT g.SolutionConfigGroupID FROM dbo.SolutionConfigGroup g
        WHERE g.SolutionID = @prmSolutionID ORDER BY g.SortOrder, g.SolutionConfigGroupID
    OPEN groupCur
    FETCH NEXT FROM groupCur INTO @oldId
    WHILE @@FETCH_STATUS = 0 BEGIN
        INSERT INTO dbo.SolutionConfigGridGroup (SolutionID, GroupTitle, SortOrder)
        SELECT @prmSolutionID, g.GroupTitle, g.SortOrder FROM dbo.SolutionConfigGroup g WHERE g.SolutionConfigGroupID = @oldId
        INSERT INTO @groupMap (OldID, NewID) VALUES (@oldId, SCOPE_IDENTITY())
        FETCH NEXT FROM groupCur INTO @oldId
    END
    CLOSE groupCur
    DEALLOCATE groupCur

    -- A property name is unique per solution in both tabs, so the new ids are found by name.
    INSERT INTO dbo.SolutionConfigGridProperty (SolutionID, SolutionConfigGridGroupID, PropertyKey, PropertyDescription, SolutionConfigTypeID, SortOrder, ModifiedBy)
    SELECT @prmSolutionID, gm.NewID, p.PropertyKey, p.PropertyDescription, p.SolutionConfigTypeID, p.SortOrder, p.ModifiedBy
    FROM   dbo.SolutionConfigProperty p JOIN @groupMap gm ON gm.OldID = p.SolutionConfigGroupID
    INSERT INTO @propMap (OldID, NewID)
    SELECT p.SolutionConfigPropertyID, np.SolutionConfigGridPropertyID
    FROM   dbo.SolutionConfigProperty p
    JOIN   @groupMap gm ON gm.OldID = p.SolutionConfigGroupID
    JOIN   dbo.SolutionConfigGridProperty np ON np.SolutionID = @prmSolutionID AND np.PropertyKey = p.PropertyKey

    -- A cell is unique per property and column, so the new ids are found the same way.
    INSERT INTO dbo.SolutionConfigGridValue (SolutionConfigGridPropertyID, SolutionConfigGridColumnID, ValueText, SolutionConfigSourceID, ValueNote,
                                             SolutionConfigGridCellStateID, ModifiedByMemberID)
    SELECT pm.NewID, cm.NewID, cv.ValueText, cv.SolutionConfigSourceID, cv.ValueNote, @observed, cv.ModifiedByMemberID
    FROM   dbo.SolutionConfigValue cv
    JOIN   @propMap pm ON pm.OldID = cv.SolutionConfigPropertyID
    JOIN   @colMap  cm ON cm.OldID = cv.SolutionConfigEnvironmentID
    INSERT INTO @valMap (OldID, NewID)
    SELECT cv.SolutionConfigValueID, nv.SolutionConfigGridValueID
    FROM   dbo.SolutionConfigValue cv
    JOIN   @propMap pm ON pm.OldID = cv.SolutionConfigPropertyID
    JOIN   @colMap  cm ON cm.OldID = cv.SolutionConfigEnvironmentID
    JOIN   dbo.SolutionConfigGridValue nv ON nv.SolutionConfigGridPropertyID = pm.NewID AND nv.SolutionConfigGridColumnID = cm.NewID

    INSERT INTO dbo.SolutionConfigGridValueAlsoSet (SolutionConfigGridValueID, SolutionConfigSourceID)
    SELECT vm.NewID, a.SolutionConfigSourceID
    FROM   dbo.SolutionConfigValueAlsoSet a JOIN @valMap vm ON vm.OldID = a.SolutionConfigValueID

    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH
SELECT (SELECT COUNT(*) FROM @colMap) AS ColumnCount, (SELECT COUNT(*) FROM @groupMap) AS GroupCount,
       (SELECT COUNT(*) FROM @propMap) AS PropertyCount, (SELECT COUNT(*) FROM @valMap) AS ValueCount

GO
