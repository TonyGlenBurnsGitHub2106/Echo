SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Solution_Search') IS NOT NULL DROP PROCEDURE dbo.stp_Solution_Search
GO
CREATE PROCEDURE dbo.stp_Solution_Search
    @prmSolutionID   INT,
    @prmOrTerms      NVARCHAR(2000) = NULL,   -- any of these, one per line
    @prmAndTerms     NVARCHAR(2000) = NULL,   -- all of these, one per line
    @prmNotTerms     NVARCHAR(2000) = NULL,   -- none of these, one per line
    @prmSources      VARCHAR(400)   = NULL,   -- comma list of tab keys; NULL = every tab
    @prmFromDate     DATETIME       = NULL,
    @prmThroughDate  DATETIME       = NULL,
    @prmTitlesOnly   BIT            = 0,      -- 1 = never look inside the text, only at titles and names
    @prmOpenOnly     BIT            = 0,      -- 1 = skip closed projects, items and conversations
    @prmSource       VARCHAR(30)    = NULL,   -- page ONE tab: @prmOffset / @prmPageSize apply to it alone
    @prmOffset       INT            = 0,
    @prmPageSize     INT            = 25
AS

/*

The Search tab of a solution (2026-10-16): every other tab searched at once, Find Transaction
style -- OR terms, AND terms, NOT terms, a date range, which tabs, titles only, open only.

Every text a tab shows is gathered into one hay set for this solution, one row per thing a
person can open (a project, an item, a log entry, a note, a runbook step, a term, a testing or
QA checklist and each of its tests, a configuration property with its values, a file, a
conversation and each of its messages, a web address). The terms are tested in SQL; the rows
that match come back newest first, the top @prmPageSize of EVERY tab in one call (TotalInSource
says how many there are), or one tab paged when @prmSource is given.

Source keys are the page's tab keys: overview, items, log, notes, terms, tests, qa, config,
grid, files, conversations, web.

HOUSE-ONLY: the web source reads the solution's EntityID and the file source its filing, so
the endpoint that calls this lives outside the outside agents' door (/api/solution-search).

TEST
----

    EXEC stp_Solution_Search 6, N'config'
    EXEC stp_Solution_Search 6, NULL, N'deploy' + NCHAR(10) + N'prod', N'draft', 'log,notes', '2026-09-01', NULL, 0, 0
    EXEC stp_Solution_Search 6, N'the', NULL, NULL, NULL, NULL, NULL, 0, 0, 'log', 25, 25

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/16/2026  Felix        Created for the Solution Search tab.

*/

SET NOCOUNT ON

DECLARE @sid        INT      = @prmSolutionID
DECLARE @titlesOnly BIT      = ISNULL(@prmTitlesOnly, 0)
DECLARE @openOnly   BIT      = ISNULL(@prmOpenOnly, 0)
DECLARE @from       DATETIME = CASE WHEN @prmFromDate IS NULL THEN NULL ELSE CONVERT(DATETIME, CONVERT(DATE, @prmFromDate)) END
DECLARE @through    DATETIME = CASE WHEN @prmThroughDate IS NULL THEN NULL ELSE DATEADD(DAY, 1, CONVERT(DATETIME, CONVERT(DATE, @prmThroughDate))) END
DECLARE @oneSource  VARCHAR(30) = NULLIF(LTRIM(RTRIM(ISNULL(@prmSource, ''))), '')
DECLARE @offset     INT      = CASE WHEN ISNULL(@prmOffset, 0) < 0 THEN 0 ELSE ISNULL(@prmOffset, 0) END
DECLARE @size       INT      = CASE WHEN ISNULL(@prmPageSize, 0) <= 0 THEN 25 WHEN @prmPageSize > 500 THEN 500 ELSE @prmPageSize END
DECLARE @entityId   INT      = (SELECT EntityID FROM dbo.Solution WHERE SolutionID = @sid)

-- ---- the terms ---------------------------------------------------------------------------

DECLARE @or  TABLE (Seq INT, Term NVARCHAR(200), Pattern NVARCHAR(420))
DECLARE @and TABLE (Seq INT, Term NVARCHAR(200), Pattern NVARCHAR(420))
DECLARE @not TABLE (Seq INT, Term NVARCHAR(200), Pattern NVARCHAR(420))
INSERT INTO @or  SELECT Seq, Term, Pattern FROM dbo.fn_Search_Terms(@prmOrTerms)
INSERT INTO @and SELECT Seq, Term, Pattern FROM dbo.fn_Search_Terms(@prmAndTerms)
INSERT INTO @not SELECT Seq, Term, Pattern FROM dbo.fn_Search_Terms(@prmNotTerms)

-- Every positive term, in the order typed: the first one found in a text names the snippet.
DECLARE @positive TABLE (Seq INT, Term NVARCHAR(200), Pattern NVARCHAR(420))
INSERT INTO @positive SELECT Seq, Term, Pattern FROM @or
INSERT INTO @positive SELECT Seq + 1000, Term, Pattern FROM @and

-- ---- which tabs --------------------------------------------------------------------------

DECLARE @sources TABLE (Source VARCHAR(30) PRIMARY KEY)
IF @oneSource IS NOT NULL
    INSERT INTO @sources VALUES (@oneSource)
ELSE IF NULLIF(LTRIM(RTRIM(ISNULL(@prmSources, ''))), '') IS NULL
    INSERT INTO @sources VALUES ('overview'), ('items'), ('log'), ('notes'), ('terms'), ('tests'), ('qa'),
                                ('config'), ('grid'), ('files'), ('conversations'), ('web')
ELSE
    INSERT INTO @sources SELECT DISTINCT LOWER(LTRIM(RTRIM(items))) FROM dbo.Split(@prmSources, ',') WHERE LTRIM(RTRIM(items)) <> ''

-- ---- the hay: one row per thing a person can open ----------------------------------------

CREATE TABLE #hay (
    Source      VARCHAR(30)    NOT NULL,
    Kind        VARCHAR(30)    NOT NULL,
    ID          INT            NOT NULL,
    ParentID    INT            NULL,
    ParentTitle NVARCHAR(500)  NULL,
    Title       NVARCHAR(500)  NOT NULL,
    Body        NVARCHAR(MAX)  NULL,
    ItemDate    DATETIME       NULL,
    StatusName  NVARCHAR(100)  NULL,
    ColorHex    VARCHAR(20)    NULL,
    IsClosed    BIT            NOT NULL DEFAULT 0,
    Extra       NVARCHAR(300)  NULL
)

-- Overview: the projects
IF EXISTS (SELECT 1 FROM @sources WHERE Source = 'overview')
INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
SELECT 'overview', 'project', p.SolutionProjectID, NULL, NULL, p.ProjectName, p.ProjectDescription,
       ISNULL(p.ModifiedDate, p.CreatedDate), st.StatusName, st.ColorHex, ISNULL(st.IsClosed, 0), m.MemberName
FROM   dbo.SolutionProject p
LEFT JOIN dbo.SolutionStatus st ON st.SolutionStatusID = p.SolutionStatusID
LEFT JOIN dbo.SolutionMember m  ON m.SolutionMemberID = p.OwnerMemberID
WHERE  p.SolutionID = @sid

-- Items, at every depth
IF EXISTS (SELECT 1 FROM @sources WHERE Source = 'items')
INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
SELECT 'items', 'item', i.SolutionProjectItemID, p.SolutionProjectID, p.ProjectName, i.ItemTitle, i.ItemDetail,
       ISNULL(i.ModifiedDate, i.CreatedDate), st.StatusName, st.ColorHex, ISNULL(st.IsClosed, 0), m.MemberName
FROM   dbo.SolutionProjectItem i
JOIN   dbo.SolutionProject p    ON p.SolutionProjectID = i.SolutionProjectID
LEFT JOIN dbo.SolutionStatus st ON st.SolutionStatusID = i.SolutionStatusID
LEFT JOIN dbo.SolutionMember m  ON m.SolutionMemberID = i.AssigneeMemberID
WHERE  p.SolutionID = @sid

-- Log entries
IF EXISTS (SELECT 1 FROM @sources WHERE Source = 'log')
INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
SELECT 'log', 'entry', l.SolutionLogID, l.SolutionProjectID, p.ProjectName,
       N'#' + CONVERT(NVARCHAR(12), l.SequenceNumber) + N' ' + ISNULL(t.LogTypeName, N''), l.LogText,
       l.LogDate, NULL, NULL, 0, m.MemberName
FROM   dbo.SolutionLog l
LEFT JOIN dbo.SolutionLogType t ON t.SolutionLogTypeID = l.SolutionLogTypeID
LEFT JOIN dbo.SolutionMember m  ON m.SolutionMemberID = l.AuthorMemberID
LEFT JOIN dbo.SolutionProject p ON p.SolutionProjectID = l.SolutionProjectID
WHERE  l.SolutionID = @sid

-- Notes (the current version) and their runbook steps
IF EXISTS (SELECT 1 FROM @sources WHERE Source = 'notes')
BEGIN
    INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
    SELECT 'notes', 'note', n.SolutionNoteID, n.SolutionNoteID, NULL, n.NoteTitle, v.NoteBody,
           ISNULL(n.ModifiedDate, n.CreatedDate), NULL, NULL, 0, t.NoteTypeName
    FROM   dbo.SolutionNote n
    LEFT JOIN dbo.SolutionNoteVersion v ON v.SolutionNoteID = n.SolutionNoteID AND v.VersionNumber = n.CurrentVersionNumber
    LEFT JOIN dbo.SolutionNoteType t    ON t.SolutionNoteTypeID = n.SolutionNoteTypeID
    WHERE  n.SolutionID = @sid

    INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
    SELECT 'notes', 'step', s.SolutionNoteStepID, n.SolutionNoteID, n.NoteTitle,
           N'Step ' + CONVERT(NVARCHAR(12), s.StepNumber) + N': ' + ISNULL(s.StepTitle, N''),
           ISNULL(s.StepCommand, N'') + NCHAR(10) + ISNULL(s.ExpectedResult, N'') + NCHAR(10) + ISNULL(s.StepNotes, N''),
           ISNULL(s.ModifiedDate, s.CreatedDate), NULL, NULL, 0, NULL
    FROM   dbo.SolutionNoteStep s
    JOIN   dbo.SolutionNote n ON n.SolutionNoteID = s.SolutionNoteID
    WHERE  n.SolutionID = @sid
END

-- Terms
IF EXISTS (SELECT 1 FROM @sources WHERE Source = 'terms')
INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
SELECT 'terms', 'term', t.SolutionTermID, NULL, NULL, t.Term,
       ISNULL(t.Definition, N'') + NCHAR(10) + ISNULL(t.TermSource, N'') + NCHAR(10) + ISNULL(t.TermNotes, N''),
       ISNULL(t.ModifiedDate, t.CreatedDate), NULL, NULL, 0, ty.TypeName
FROM   dbo.SolutionTerm t
LEFT JOIN dbo.SolutionTermType ty ON ty.SolutionTermTypeID = t.SolutionTermTypeID
WHERE  t.SolutionID = @sid

-- Testing and QA checklists: the checklist itself, then every test in it
IF EXISTS (SELECT 1 FROM @sources WHERE Source IN ('tests', 'qa'))
BEGIN
    INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
    SELECT CASE WHEN pl.PlanKindCode = 'qa' THEN 'qa' ELSE 'tests' END, 'checklist', pl.SolutionTestPlanID, pl.SolutionTestPlanID, NULL,
           pl.PlanTitle, pl.PlanDescription, ISNULL(pl.ModifiedDate, pl.CreatedDate), NULL, NULL, 0, NULL
    FROM   dbo.SolutionTestPlan pl
    WHERE  pl.SolutionID = @sid
      AND  EXISTS (SELECT 1 FROM @sources s WHERE s.Source = CASE WHEN pl.PlanKindCode = 'qa' THEN 'qa' ELSE 'tests' END)

    INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
    SELECT CASE WHEN pl.PlanKindCode = 'qa' THEN 'qa' ELSE 'tests' END, 'test', c.SolutionTestCaseID, pl.SolutionTestPlanID,
           pl.PlanTitle + N' / ' + g.GroupTitle, LEFT(c.CaseText, 500),
           ISNULL(c.CaseNote, N'') + NCHAR(10) + ISNULL(r.ResultNote, N''),
           ISNULL(c.ModifiedDate, c.CreatedDate), NULL, NULL, 0, CASE WHEN r.IsFailing = 1 THEN N'failing' ELSE NULL END
    FROM   dbo.SolutionTestCase c
    JOIN   dbo.SolutionTestGroup g  ON g.SolutionTestGroupID = c.SolutionTestGroupID
    JOIN   dbo.SolutionTestPlan pl  ON pl.SolutionTestPlanID = g.SolutionTestPlanID
    LEFT JOIN dbo.SolutionTestResult r ON r.SolutionTestCaseID = c.SolutionTestCaseID
    WHERE  pl.SolutionID = @sid
      AND  EXISTS (SELECT 1 FROM @sources s WHERE s.Source = CASE WHEN pl.PlanKindCode = 'qa' THEN 'qa' ELSE 'tests' END)
END

-- Configuration: a property with every environment's value, source and note
IF EXISTS (SELECT 1 FROM @sources WHERE Source = 'config')
INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
SELECT 'config', 'property', pr.SolutionConfigPropertyID, g.SolutionConfigGroupID, g.GroupTitle, pr.PropertyKey,
       ISNULL(pr.PropertyDescription, N'') + ISNULL((
           SELECT NCHAR(10) + e.EnvironmentName + N': ' + ISNULL(v.ValueText, N'') + ISNULL(N' (' + src.SourceTitle + N')', N'') + ISNULL(N' ' + v.ValueNote, N'')
           FROM   dbo.SolutionConfigValue v
           JOIN   dbo.SolutionConfigEnvironment ce ON ce.SolutionConfigEnvironmentID = v.SolutionConfigEnvironmentID
           JOIN   dbo.SolutionEnvironment e        ON e.SolutionEnvironmentID = ce.SolutionEnvironmentID
           LEFT JOIN dbo.SolutionConfigSource src  ON src.SolutionConfigSourceID = v.SolutionConfigSourceID
           WHERE  v.SolutionConfigPropertyID = pr.SolutionConfigPropertyID
           ORDER BY ce.SortOrder
           FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), N''),
       ISNULL(pr.ModifiedDate, pr.CreatedDate), NULL, NULL, 0, ty.TypeName
FROM   dbo.SolutionConfigProperty pr
JOIN   dbo.SolutionConfigGroup g      ON g.SolutionConfigGroupID = pr.SolutionConfigGroupID
LEFT JOIN dbo.SolutionConfigType ty   ON ty.SolutionConfigTypeID = pr.SolutionConfigTypeID
WHERE  g.SolutionID = @sid

-- Configuration II: a property with every column's value, source and note
IF EXISTS (SELECT 1 FROM @sources WHERE Source = 'grid')
INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
SELECT 'grid', 'property', pr.SolutionConfigGridPropertyID, g.SolutionConfigGridGroupID, g.GroupTitle, pr.PropertyKey,
       ISNULL(pr.PropertyDescription, N'') + ISNULL((
           SELECT NCHAR(10) + c.ColumnTitle + N': ' + ISNULL(v.ValueText, N'') + ISNULL(N' (' + src.SourceTitle + N')', N'') + ISNULL(N' ' + v.ValueNote, N'')
           FROM   dbo.SolutionConfigGridValue v
           JOIN   dbo.SolutionConfigGridColumn c  ON c.SolutionConfigGridColumnID = v.SolutionConfigGridColumnID
           LEFT JOIN dbo.SolutionConfigSource src ON src.SolutionConfigSourceID = v.SolutionConfigSourceID
           WHERE  v.SolutionConfigGridPropertyID = pr.SolutionConfigGridPropertyID
           ORDER BY c.SortOrder
           FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), N''),
       ISNULL(pr.ModifiedDate, pr.CreatedDate), NULL, NULL, 0, ty.TypeName
FROM   dbo.SolutionConfigGridProperty pr
JOIN   dbo.SolutionConfigGridGroup g  ON g.SolutionConfigGridGroupID = pr.SolutionConfigGridGroupID
LEFT JOIN dbo.SolutionConfigType ty   ON ty.SolutionConfigTypeID = pr.SolutionConfigTypeID
WHERE  pr.SolutionID = @sid

-- Files: title, file name, notes
IF EXISTS (SELECT 1 FROM @sources WHERE Source = 'files')
INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
SELECT 'files', 'file', f.SolutionFileID, NULL, NULL, ISNULL(NULLIF(f.FileTitle, N''), f.OriginalFileName),
       ISNULL(f.OriginalFileName, N'') + NCHAR(10) + ISNULL(f.FileNotes, N''),
       ISNULL(f.ModifiedDate, f.CreatedDate), NULL, NULL, 0, m.MemberName
FROM   dbo.SolutionFile f
LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = f.UploadedByMemberID
WHERE  f.SolutionID = @sid

-- Conversations: the thread, then every message in it
IF EXISTS (SELECT 1 FROM @sources WHERE Source = 'conversations')
BEGIN
    INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
    SELECT 'conversations', 'conversation', c.ConversationID, c.ConversationID, NULL, c.Title, NULL,
           ISNULL(c.ModifiedDate, c.CreatedDate), CASE WHEN c.Status = 'closed' THEN N'Resolved' ELSE NULL END, NULL,
           CASE WHEN c.Status = 'closed' THEN 1 ELSE 0 END, NULL
    FROM   dbo.SolutionConversation sc
    JOIN   dbo.Conversation c ON c.ConversationID = sc.ConversationID
    WHERE  sc.SolutionID = @sid

    INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
    SELECT 'conversations', 'message', m.ConversationMessageID, c.ConversationID, c.Title, ISNULL(m.Sender, N''), m.Body,
           m.CreatedDate, NULL, NULL, CASE WHEN c.Status = 'closed' THEN 1 ELSE 0 END, NULL
    FROM   dbo.SolutionConversation sc
    JOIN   dbo.Conversation c        ON c.ConversationID = sc.ConversationID
    JOIN   dbo.ConversationMessage m ON m.ConversationID = c.ConversationID
    WHERE  sc.SolutionID = @sid
END

-- Web: the solution's web addresses (house-only: through its EntityID)
IF EXISTS (SELECT 1 FROM @sources WHERE Source = 'web') AND @entityId IS NOT NULL
INSERT INTO #hay (Source, Kind, ID, ParentID, ParentTitle, Title, Body, ItemDate, StatusName, ColorHex, IsClosed, Extra)
SELECT 'web', 'web', x.EntityWebAddressXRefID, NULL, NULL, ISNULL(NULLIF(w.WebAddressName, ''), w.WebAddress),
       ISNULL(w.WebAddress, '') + NCHAR(10) + ISNULL(w.Notes, ''), x.CreatedDate, NULL, NULL, 0, NULL
FROM   dbo.tblEntityWebAddressXRef x
JOIN   dbo.tblWebAddress w ON w.WebAddressID = x.WebAddressID
WHERE  x.EntityID = @entityId

-- ---- the match ---------------------------------------------------------------------------

DECLARE @anyOr  BIT = CASE WHEN EXISTS (SELECT 1 FROM @or) THEN 1 ELSE 0 END

;WITH matched AS (
    SELECT h.Source, h.Kind, h.ID, h.ParentID, h.ParentTitle, h.Title, h.Body, h.ItemDate, h.StatusName, h.ColorHex, h.IsClosed, h.Extra,
           hay.Text
    FROM   #hay h
    CROSS APPLY (SELECT ISNULL(h.Title, N'') + NCHAR(10) + ISNULL(h.ParentTitle, N'') + NCHAR(10) + ISNULL(h.Extra, N'')
                      + CASE WHEN @titlesOnly = 1 THEN N'' ELSE NCHAR(10) + ISNULL(h.Body, N'') END AS Text) hay
    WHERE  (@openOnly = 0 OR h.IsClosed = 0)
      AND  (@from IS NULL OR h.ItemDate >= @from)
      AND  (@through IS NULL OR h.ItemDate < @through)
      AND  (@anyOr = 0 OR EXISTS (SELECT 1 FROM @or o WHERE hay.Text LIKE o.Pattern))
      AND  NOT EXISTS (SELECT 1 FROM @and a WHERE hay.Text NOT LIKE a.Pattern)
      AND  NOT EXISTS (SELECT 1 FROM @not n WHERE hay.Text LIKE n.Pattern)
),
numbered AS (
    SELECT m.*,
           ROW_NUMBER() OVER (PARTITION BY m.Source ORDER BY m.ItemDate DESC, m.ID DESC) AS RowInSource,
           COUNT(*)     OVER (PARTITION BY m.Source) AS TotalInSource
    FROM   matched m
)
SELECT n.Source, n.Kind, n.ID, n.ParentID, n.ParentTitle, n.Title,
       dbo.fn_Search_Snippet(n.Body, first.Term, 240) AS Snippet,
       first.Term AS MatchTerm,
       n.ItemDate, n.StatusName, n.ColorHex, n.IsClosed, n.Extra, n.TotalInSource
FROM   numbered n
OUTER APPLY (SELECT TOP 1 t.Term FROM @positive t WHERE n.Body LIKE t.Pattern ORDER BY t.Seq) first
WHERE  (@oneSource IS NULL AND n.RowInSource <= @size)
   OR  (@oneSource IS NOT NULL AND n.RowInSource > @offset AND n.RowInSource <= @offset + @size)
ORDER BY n.Source, n.RowInSource

DROP TABLE #hay

GO
