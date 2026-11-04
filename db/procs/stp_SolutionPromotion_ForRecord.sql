SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionPromotion_ForRecord') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionPromotion_ForRecord
GO
CREATE PROCEDURE dbo.stp_SolutionPromotion_ForRecord
    @prmSolutionID INT,
    @prmKind       VARCHAR(10),
    @prmID         INT
AS

/*

TEST
----

    EXEC stp_SolutionPromotion_ForRecord 6, 'item', 1200

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo), Release 5: where a record came from and what was made from it. Direction 'from' = this
                         record was made from the row's record; 'into' = the row's record was made from this one.
                         Only records still there. A message gives its thread (ConversationID) and its first line.

*/

SET NOCOUNT ON

DECLARE @k VARCHAR(10) = LOWER(LTRIM(RTRIM(ISNULL(@prmKind, ''))))

;WITH Rows AS (
    SELECT 'from' AS Direction, p.SourceKind AS Kind, p.SourceID AS RecordID, p.CreatedDate, p.PromotedByMemberID
    FROM   dbo.SolutionPromotion p WHERE p.SolutionID = @prmSolutionID AND p.TargetKind = @k AND p.TargetID = @prmID
    UNION ALL
    SELECT 'into', p.TargetKind, p.TargetID, p.CreatedDate, p.PromotedByMemberID
    FROM   dbo.SolutionPromotion p WHERE p.SolutionID = @prmSolutionID AND p.SourceKind = @k AND p.SourceID = @prmID
)
SELECT  r.Direction, r.Kind, r.RecordID, r.CreatedDate AS PromotedDate, m.MemberCode AS PromotedByCode, m.MemberName AS PromotedByName,
        CAST(COALESCE(i.ItemTitle, n.NoteTitle, q.QuestionText, LEFT(l.LogText, 200), LEFT(cm.Body, 200)) AS NVARCHAR(400)) AS Title,
        cm.ConversationID
FROM    Rows r
LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = r.PromotedByMemberID
LEFT JOIN dbo.SolutionProjectItem i ON r.Kind = 'item' AND i.SolutionProjectItemID = r.RecordID
LEFT JOIN dbo.SolutionNote n ON r.Kind = 'note' AND n.SolutionNoteID = r.RecordID
LEFT JOIN dbo.SolutionQuestion q ON r.Kind = 'question' AND q.SolutionQuestionID = r.RecordID
LEFT JOIN dbo.SolutionLog l ON r.Kind = 'log' AND l.SolutionLogID = r.RecordID
LEFT JOIN dbo.ConversationMessage cm ON r.Kind = 'message' AND cm.ConversationMessageID = r.RecordID
WHERE   COALESCE(i.SolutionProjectItemID, n.SolutionNoteID, q.SolutionQuestionID, l.SolutionLogID, cm.ConversationMessageID) IS NOT NULL
ORDER BY r.CreatedDate

GO
