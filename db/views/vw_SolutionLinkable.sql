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
SELECT  o.SolutionID, 'topic', o.SolutionTopicID, CAST(o.TopicName AS NVARCHAR(400)), CAST(LEFT(ISNULL(o.TopicSummary, N''), 200) AS NVARCHAR(400)),
        ISNULL(o.ModifiedDate, o.CreatedDate), CAST(0 AS BIT)
FROM    dbo.SolutionTopic o;

GO
