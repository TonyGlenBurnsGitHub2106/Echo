SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.vw_SolutionLinkable') IS NOT NULL DROP VIEW dbo.vw_SolutionLinkable
GO

CREATE VIEW dbo.vw_SolutionLinkable
AS
SELECT  p.SolutionID, CAST('item' AS VARCHAR(10)) AS Kind, i.SolutionProjectItemID AS RecordID,
        CAST(i.ItemTitle AS NVARCHAR(400)) AS Title, CAST(p.ProjectName AS NVARCHAR(400)) AS Sub,
        ISNULL(i.ModifiedDate, i.CreatedDate) AS RecordDate, CAST(ISNULL(s.IsClosed, 0) AS BIT) AS IsClosed
FROM    dbo.SolutionProjectItem i
JOIN    dbo.SolutionProject p ON p.SolutionProjectID = i.SolutionProjectID
LEFT JOIN dbo.SolutionStatus s ON s.SolutionStatusID = i.SolutionStatusID
UNION ALL
SELECT  n.SolutionID, 'note', n.SolutionNoteID, CAST(n.NoteTitle AS NVARCHAR(400)), CAST(t.NoteTypeName AS NVARCHAR(400)),
        ISNULL(n.ModifiedDate, n.CreatedDate), CAST(0 AS BIT)
FROM    dbo.SolutionNote n
LEFT JOIN dbo.SolutionNoteType t ON t.SolutionNoteTypeID = n.SolutionNoteTypeID
UNION ALL
SELECT  l.SolutionID, 'log', l.SolutionLogID,
        CAST(N'#' + CAST(l.SequenceNumber AS NVARCHAR(12)) + N' ' +
             LEFT(LTRIM(REPLACE(REPLACE(REPLACE(l.LogText, NCHAR(13), N' '), NCHAR(10), N' '), NCHAR(9), N' ')), 200) AS NVARCHAR(400)),
        CAST(t.LogTypeName AS NVARCHAR(400)), l.CreatedDate, CAST(0 AS BIT)
FROM    dbo.SolutionLog l
LEFT JOIN dbo.SolutionLogType t ON t.SolutionLogTypeID = l.SolutionLogTypeID
UNION ALL
SELECT  f.SolutionID, 'file', f.SolutionFileID,
        CAST(ISNULL(NULLIF(LTRIM(RTRIM(f.FileTitle)), N''), f.OriginalFileName) AS NVARCHAR(400)),
        CAST(f.OriginalFileName AS NVARCHAR(400)), ISNULL(f.ModifiedDate, f.CreatedDate), CAST(0 AS BIT)
FROM    dbo.SolutionFile f
UNION ALL
SELECT  m.SolutionID, 'term', m.SolutionTermID, CAST(m.Term AS NVARCHAR(400)), CAST(LEFT(m.Definition, 200) AS NVARCHAR(400)),
        ISNULL(m.ModifiedDate, m.CreatedDate), CAST(0 AS BIT)
FROM    dbo.SolutionTerm m
UNION ALL
-- Story 5695 (Echo): a topic's second line starts with its kind (a part: its kind of part); a part whose status is
-- a closed one is closed; an assumption proved false is closed.
SELECT  o.SolutionID, 'topic', o.SolutionTopicID, CAST(o.TopicName AS NVARCHAR(400)),
        CAST(CASE o.TopicKindCode WHEN 'tag' THEN N'Tag' WHEN 'part' THEN ISNULL(y.TypeName, N'Part')
                                  WHEN 'assumption' THEN CASE WHEN o.AssumptionHolds = 0 THEN N'Assumption, proved false' ELSE N'Assumption' END
                                  ELSE N'Topic' END +
             CASE WHEN ISNULL(o.TopicSummary, N'') = N'' THEN N'' ELSE N' | ' + LEFT(o.TopicSummary, 200) END AS NVARCHAR(400)),
        ISNULL(o.ModifiedDate, o.CreatedDate),
        CAST(CASE WHEN o.TopicKindCode = 'assumption' AND o.AssumptionHolds = 0 THEN 1 WHEN ISNULL(st.IsClosed, 0) = 1 THEN 1 ELSE 0 END AS BIT)
FROM    dbo.SolutionTopic o
LEFT JOIN dbo.SolutionTopicType y ON y.SolutionTopicTypeID = o.SolutionTopicTypeID
LEFT JOIN dbo.SolutionStatus st ON st.SolutionStatusID = o.SolutionStatusID
-- Story 5695 (Echo): questions and decisions are linkable too. A question is closed once answered or
-- dropped; a decision once a newer one has replaced it.
UNION ALL
SELECT  q.SolutionID, 'question', q.SolutionQuestionID, CAST(q.QuestionText AS NVARCHAR(400)),
        CAST(CASE q.StatusCode WHEN 'open' THEN N'Open question' WHEN 'answered' THEN N'Answered' ELSE N'Dropped' END AS NVARCHAR(400)),
        ISNULL(q.ModifiedDate, q.CreatedDate), CAST(CASE WHEN q.StatusCode = 'open' THEN 0 ELSE 1 END AS BIT)
FROM    dbo.SolutionQuestion q
UNION ALL
SELECT  d.SolutionID, 'decision', d.SolutionDecisionID, CAST(d.DecisionTitle AS NVARCHAR(400)),
        CAST(CASE d.StatusCode WHEN 'proposed' THEN N'Proposed' WHEN 'accepted' THEN N'Decided' ELSE N'Replaced' END +
             CASE WHEN d.DecidedDate IS NULL THEN N'' ELSE N' ' + CONVERT(NVARCHAR(10), d.DecidedDate, 101) END AS NVARCHAR(400)),
        ISNULL(d.DecidedDate, d.CreatedDate), CAST(CASE WHEN d.StatusCode = 'superseded' THEN 1 ELSE 0 END AS BIT)
FROM    dbo.SolutionDecision d;

GO
