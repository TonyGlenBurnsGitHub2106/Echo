SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionLink_Save') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionLink_Save
GO

CREATE PROCEDURE dbo.stp_SolutionLink_Save
    @prmSolutionID INT,
    @prmFromKind   VARCHAR(20),
    @prmFromID     INT,
    @prmToKind     VARCHAR(20),
    @prmToID       INT,
    @prmMemberCode VARCHAR(20) = NULL
AS

/*

TEST
----

    EXEC stp_SolutionLink_Save 6, 'item', 1200, 'topic', 1, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/02/2026  Felix        Solution links: join two records of one solution (item, note, log entry, file, term,
                         topic). Both must exist IN THIS solution (vw_SolutionLinkable), a record never links to
                         itself, and a pair already linked -- either way round -- is refused ("... Nothing was
                         saved."). The pair is stored lower kind first, then lower id, so A-B and B-A are one row
                         and UX_SolutionLink_Pair backs the refusal up. Returns SolutionLinkID.

*/

SET NOCOUNT ON

DECLARE @fromKind VARCHAR(20) = LOWER(LTRIM(RTRIM(ISNULL(@prmFromKind, ''))))
DECLARE @toKind   VARCHAR(20) = LOWER(LTRIM(RTRIM(ISNULL(@prmToKind, ''))))
DECLARE @fromId INT = ISNULL(@prmFromID, 0), @toId INT = ISNULL(@prmToID, 0)

IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID)
BEGIN RAISERROR('That solution no longer exists. Nothing was saved.', 16, 1) RETURN END
-- Story 5695 (Echo): questions and decisions are linkable too.
IF @fromKind NOT IN ('item', 'note', 'log', 'file', 'term', 'topic', 'question', 'decision')
   OR @toKind NOT IN ('item', 'note', 'log', 'file', 'term', 'topic', 'question', 'decision')
BEGIN RAISERROR('A link joins two of: item, note, log, file, term, topic, question, decision. Nothing was saved.', 16, 1) RETURN END
IF @fromKind = @toKind AND @fromId = @toId
BEGIN RAISERROR('A record cannot be linked to itself. Nothing was saved.', 16, 1) RETURN END
IF NOT EXISTS (SELECT 1 FROM dbo.vw_SolutionLinkable WHERE SolutionID = @prmSolutionID AND Kind = @fromKind AND RecordID = @fromId)
BEGIN RAISERROR('The record to link from is not in this solution. Nothing was saved.', 16, 1) RETURN END
IF NOT EXISTS (SELECT 1 FROM dbo.vw_SolutionLinkable WHERE SolutionID = @prmSolutionID AND Kind = @toKind AND RecordID = @toId)
BEGIN RAISERROR('The record to link to is not in this solution. Nothing was saved.', 16, 1) RETURN END

-- One order for every pair: the lower kind first, then the lower id.
DECLARE @aKind VARCHAR(10), @aId INT, @bKind VARCHAR(10), @bId INT
IF @fromKind < @toKind OR (@fromKind = @toKind AND @fromId < @toId)
    SELECT @aKind = @fromKind, @aId = @fromId, @bKind = @toKind, @bId = @toId
ELSE
    SELECT @aKind = @toKind, @aId = @toId, @bKind = @fromKind, @bId = @fromId

IF EXISTS (SELECT 1 FROM dbo.SolutionLink WHERE SolutionID = @prmSolutionID AND FromKind = @aKind AND FromID = @aId AND ToKind = @bKind AND ToID = @bId)
BEGIN RAISERROR('Those two are already linked. Nothing was saved.', 16, 1) RETURN END

DECLARE @memberId INT = NULL
IF ISNULL(@prmMemberCode, '') <> '' EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT

-- XACT_ABORT only from here: a refusal above must never doom a caller's transaction.
SET XACT_ABORT ON

DECLARE @id INT = 0
BEGIN TRY
    BEGIN TRANSACTION
    INSERT INTO dbo.SolutionLink (SolutionID, FromKind, FromID, ToKind, ToID, CreatedByMemberID)
    VALUES (@prmSolutionID, @aKind, @aId, @bKind, @bId, @memberId)
    SET @id = CAST(SCOPE_IDENTITY() AS INT)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = CASE WHEN ERROR_NUMBER() IN (2601, 2627)
        THEN N'Those two are already linked. Nothing was saved.' ELSE ERROR_MESSAGE() END
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT @id AS SolutionLinkID

GO
