SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopic_Reorder') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopic_Reorder
GO
CREATE PROCEDURE dbo.stp_SolutionTopic_Reorder
    @prmSolutionID INT,
    @prmTopicIDs   NVARCHAR(MAX)    -- '12,9,30': the topics in their new order
AS

/*

TEST
----

    EXEC stp_SolutionTopic_Reorder 200, N'12,9,30'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): Arrange for the parts of a solution (its chapters, its accounts): the
                         topics named take SortOrder 1, 2, 3 ... in the order given. Every id must be a topic of
                         this solution, or nothing is changed. Returns RowsAffected.

*/

SET NOCOUNT ON

DECLARE @order TABLE (Position INT IDENTITY(1,1) PRIMARY KEY, TopicID INT NOT NULL)
DECLARE @rest NVARCHAR(MAX) = LTRIM(RTRIM(ISNULL(@prmTopicIDs, N''))) + N',', @piece NVARCHAR(40), @at INT
WHILE LEN(@rest) > 0
BEGIN
    SET @at = CHARINDEX(N',', @rest)
    SET @piece = LTRIM(RTRIM(LEFT(@rest, @at - 1)))
    SET @rest = SUBSTRING(@rest, @at + 1, LEN(@rest))
    IF @piece = N'' CONTINUE
    IF @piece LIKE N'%[^0-9]%' BEGIN RAISERROR('The order is a list of topic ids. Nothing was changed.', 16, 1) RETURN END
    INSERT INTO @order (TopicID) VALUES (CAST(@piece AS INT))
END
IF NOT EXISTS (SELECT 1 FROM @order) BEGIN RAISERROR('Name the topics in their new order. Nothing was changed.', 16, 1) RETURN END
IF EXISTS (SELECT 1 FROM @order o WHERE NOT EXISTS (SELECT 1 FROM dbo.SolutionTopic t WHERE t.SolutionTopicID = o.TopicID AND t.SolutionID = @prmSolutionID))
BEGIN RAISERROR('A topic in that order is not in this solution. Nothing was changed.', 16, 1) RETURN END

SET XACT_ABORT ON
UPDATE t SET SortOrder = o.Position, ModifiedDate = GETDATE()
FROM   dbo.SolutionTopic t JOIN @order o ON o.TopicID = t.SolutionTopicID
WHERE  t.SortOrder <> o.Position
SELECT @@ROWCOUNT AS RowsAffected

GO
