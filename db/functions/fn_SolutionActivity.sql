SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.fn_SolutionActivity') IS NOT NULL DROP FUNCTION dbo.fn_SolutionActivity
GO

CREATE FUNCTION dbo.fn_SolutionActivity (@prmSolutionID INT, @prmSince DATETIME)
RETURNS TABLE
AS
RETURN
(
    SELECT  CAST('item' AS VARCHAR(10)) AS Kind, i.SolutionProjectItemID AS RecordID,
            CAST(i.ItemTitle AS NVARCHAR(400)) AS Title, CAST(p.ProjectName AS NVARCHAR(400)) AS Sub,
            CAST(CASE WHEN i.CreatedDate >= @prmSince THEN 'added' ELSE 'changed' END AS VARCHAR(10)) AS Action,
            CAST(COALESCE(i.ModifiedBy, c.MemberCode) AS VARCHAR(20)) AS Who,
            ISNULL(i.ModifiedDate, i.CreatedDate) AS ChangedDate
    FROM    dbo.SolutionProjectItem i
    JOIN    dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
    LEFT JOIN dbo.SolutionMember c ON c.SolutionMemberID = i.CreatedByMemberID
    WHERE   p.SolutionID = @prmSolutionID AND ISNULL(i.ModifiedDate, i.CreatedDate) > @prmSince
    UNION ALL
    SELECT  'log', l.SolutionLogID,
            CAST(N'#' + CAST(l.SequenceNumber AS NVARCHAR(12)) + N' ' +
                 LEFT(LTRIM(REPLACE(REPLACE(REPLACE(l.LogText, NCHAR(13), N' '), NCHAR(10), N' '), NCHAR(9), N' ')), 200) AS NVARCHAR(400)),
            CAST(t.LogTypeName AS NVARCHAR(400)),
            CASE WHEN l.CreatedDate >= @prmSince THEN 'wrote' ELSE 'corrected' END,
            CAST(a.MemberCode AS VARCHAR(20)), ISNULL(l.ModifiedDate, l.CreatedDate)
    FROM    dbo.SolutionLog l
    LEFT JOIN dbo.SolutionLogType t ON t.SolutionLogTypeID = l.SolutionLogTypeID
    LEFT JOIN dbo.SolutionMember a ON a.SolutionMemberID = l.AuthorMemberID
    WHERE   l.SolutionID = @prmSolutionID AND ISNULL(l.ModifiedDate, l.CreatedDate) > @prmSince
    UNION ALL
    SELECT  'note', n.SolutionNoteID, CAST(n.NoteTitle AS NVARCHAR(400)), CAST(t.NoteTypeName AS NVARCHAR(400)),
            CASE WHEN n.CreatedDate >= @prmSince THEN 'added' ELSE 'changed' END,
            CAST(n.ModifiedBy AS VARCHAR(20)), ISNULL(n.ModifiedDate, n.CreatedDate)
    FROM    dbo.SolutionNote n
    LEFT JOIN dbo.SolutionNoteType t ON t.SolutionNoteTypeID = n.SolutionNoteTypeID
    WHERE   n.SolutionID = @prmSolutionID AND ISNULL(n.ModifiedDate, n.CreatedDate) > @prmSince
    UNION ALL
    SELECT  'file', f.SolutionFileID, CAST(ISNULL(NULLIF(LTRIM(RTRIM(f.FileTitle)), N''), f.OriginalFileName) AS NVARCHAR(400)),
            CAST(f.OriginalFileName AS NVARCHAR(400)),
            CASE WHEN f.CreatedDate >= @prmSince THEN 'added' ELSE 'changed' END,
            CAST(u.MemberCode AS VARCHAR(20)), ISNULL(f.ModifiedDate, f.CreatedDate)
    FROM    dbo.SolutionFile f
    LEFT JOIN dbo.SolutionMember u ON u.SolutionMemberID = f.UploadedByMemberID
    WHERE   f.SolutionID = @prmSolutionID AND ISNULL(f.ModifiedDate, f.CreatedDate) > @prmSince
    UNION ALL
    SELECT  'message', c.ConversationID, CAST(ISNULL(NULLIF(c.Title, N''), N'Untitled thread') AS NVARCHAR(400)),
            CAST(LEFT(LTRIM(REPLACE(REPLACE(m.Body, NCHAR(13), N' '), NCHAR(10), N' ')), 200) AS NVARCHAR(400)),
            'wrote', CAST(m.Sender AS VARCHAR(20)), m.CreatedDate
    FROM    dbo.SolutionConversation sc
    JOIN    dbo.Conversation c ON c.ConversationID = sc.ConversationID
    JOIN    dbo.ConversationMessage m ON m.ConversationID = sc.ConversationID
    WHERE   sc.SolutionID = @prmSolutionID AND m.CreatedDate > @prmSince
    UNION ALL
    SELECT  'term', m.SolutionTermID, CAST(m.Term AS NVARCHAR(400)), CAST(LEFT(m.Definition, 200) AS NVARCHAR(400)),
            CASE WHEN m.CreatedDate >= @prmSince THEN 'added' ELSE 'changed' END,
            CAST(w.MemberCode AS VARCHAR(20)), ISNULL(m.ModifiedDate, m.CreatedDate)
    FROM    dbo.SolutionTerm m
    LEFT JOIN dbo.SolutionMember w ON w.SolutionMemberID = m.ModifiedByMemberID
    WHERE   m.SolutionID = @prmSolutionID AND ISNULL(m.ModifiedDate, m.CreatedDate) > @prmSince
    UNION ALL
    SELECT  'topic', o.SolutionTopicID, CAST(o.TopicName AS NVARCHAR(400)), CAST(LEFT(ISNULL(o.TopicSummary, N''), 200) AS NVARCHAR(400)),
            CASE WHEN o.CreatedDate >= @prmSince THEN 'added' ELSE 'changed' END,
            CAST(o.ModifiedBy AS VARCHAR(20)), ISNULL(o.ModifiedDate, o.CreatedDate)
    FROM    dbo.SolutionTopic o
    WHERE   o.SolutionID = @prmSolutionID AND ISNULL(o.ModifiedDate, o.CreatedDate) > @prmSince
    -- Story 5695 (Echo): questions asked or answered, decisions recorded or replaced.
    UNION ALL
    SELECT  'question', q.SolutionQuestionID, CAST(q.QuestionText AS NVARCHAR(400)),
            CAST(CASE q.StatusCode WHEN 'open' THEN N'Open question' WHEN 'answered' THEN N'Answered' ELSE N'Dropped' END AS NVARCHAR(400)),
            CASE WHEN q.CreatedDate >= @prmSince THEN 'asked' ELSE 'changed' END,
            CAST(q.ModifiedBy AS VARCHAR(20)), ISNULL(q.ModifiedDate, q.CreatedDate)
    FROM    dbo.SolutionQuestion q
    WHERE   q.SolutionID = @prmSolutionID AND ISNULL(q.ModifiedDate, q.CreatedDate) > @prmSince
    UNION ALL
    SELECT  'decision', d.SolutionDecisionID, CAST(d.DecisionTitle AS NVARCHAR(400)), CAST(LEFT(ISNULL(d.Outcome, N''), 200) AS NVARCHAR(400)),
            CASE WHEN d.CreatedDate >= @prmSince THEN 'decided' ELSE 'changed' END,
            CAST(d.ModifiedBy AS VARCHAR(20)), ISNULL(d.ModifiedDate, d.CreatedDate)
    FROM    dbo.SolutionDecision d
    WHERE   d.SolutionID = @prmSolutionID AND ISNULL(d.ModifiedDate, d.CreatedDate) > @prmSince
);

GO
