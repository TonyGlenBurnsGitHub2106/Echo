# Which rows make up one solution, table by table, in the order they can be written (parents
# first). Shared by Export-Solution.ps1 and Import-Solution.ps1 (Story 5653). The tables are the
# same in Leonardo and in Echo -- that is why Echo kept Leonardo's columns -- so one list serves both.
#
#   Name     the table
#   Where    which rows belong to solution @sid (a WHERE clause; @sid is the solution id)
#   Lookup   a table every solution shares: rows are copied only when the id is not there yet, and
#            a row whose id IS there must carry the same natural key (Key), or the import stops.

$EchoSolutionTables = @(
    # ---- shared lookups (only the rows this solution uses, or the small fixed lists whole) ----
    @{ Name = 'SolutionStatus';     Lookup = $true; Key = 'StatusCode';   Where = '1 = 1' },
    @{ Name = 'SolutionLogType';    Lookup = $true; Key = 'LogTypeCode';  Where = '1 = 1' },
    @{ Name = 'SolutionNoteType';   Lookup = $true; Key = 'NoteTypeCode'; Where = '1 = 1' },
    @{ Name = 'SolutionConfigType'; Lookup = $true; Key = 'TypeCode';     Where = '1 = 1' },
    @{ Name = 'SolutionConfigGridCellState'; Lookup = $true; Key = 'StateCode'; Where = '1 = 1' },
    @{ Name = 'SolutionMember';     Lookup = $true; Key = 'MemberCode';   Where = '1 = 1' },
    @{ Name = 'SolutionTestLevel';  Lookup = $true; Key = 'LevelTitle';
       Where = 'SolutionTestLevelID IN (SELECT pl.SolutionTestLevelID FROM dbo.SolutionTestPlanLevel pl JOIN dbo.SolutionTestPlan p ON p.SolutionTestPlanID = pl.SolutionTestPlanID WHERE p.SolutionID = @sid)' },
    @{ Name = 'SolutionEnvironment'; Lookup = $true; Key = 'EnvironmentName';
       Where = 'SolutionEnvironmentID IN (SELECT SolutionEnvironmentID FROM dbo.SolutionConfigEnvironment WHERE SolutionID = @sid)' },
    @{ Name = 'SolutionConfigSource'; Lookup = $true; Key = 'SourceTitle';
       Where = 'SolutionConfigSourceID IN (SELECT v.SolutionConfigSourceID FROM dbo.SolutionConfigValue v JOIN dbo.SolutionConfigProperty p ON p.SolutionConfigPropertyID = v.SolutionConfigPropertyID JOIN dbo.SolutionConfigGroup g ON g.SolutionConfigGroupID = p.SolutionConfigGroupID WHERE g.SolutionID = @sid
              UNION SELECT a.SolutionConfigSourceID FROM dbo.SolutionConfigValueAlsoSet a JOIN dbo.SolutionConfigValue v ON v.SolutionConfigValueID = a.SolutionConfigValueID JOIN dbo.SolutionConfigProperty p ON p.SolutionConfigPropertyID = v.SolutionConfigPropertyID JOIN dbo.SolutionConfigGroup g ON g.SolutionConfigGroupID = p.SolutionConfigGroupID WHERE g.SolutionID = @sid
              UNION SELECT v.SolutionConfigSourceID FROM dbo.SolutionConfigGridValue v JOIN dbo.SolutionConfigGridProperty p ON p.SolutionConfigGridPropertyID = v.SolutionConfigGridPropertyID WHERE p.SolutionID = @sid
              UNION SELECT a.SolutionConfigSourceID FROM dbo.SolutionConfigGridValueAlsoSet a JOIN dbo.SolutionConfigGridValue v ON v.SolutionConfigGridValueID = a.SolutionConfigGridValueID JOIN dbo.SolutionConfigGridProperty p ON p.SolutionConfigGridPropertyID = v.SolutionConfigGridPropertyID WHERE p.SolutionID = @sid)' },
    @{ Name = 'FileCategory'; Lookup = $true; Key = 'CategoryName';
       Where = 'FileCategoryID IN (SELECT fl.FileCategoryID FROM dbo.SolutionFileFiling fl JOIN dbo.SolutionFile f ON f.SolutionFileID = fl.SolutionFileID WHERE f.SolutionID = @sid)' },

    # ---- the solution itself ----
    @{ Name = 'Solution';            Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionProject';     Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionProjectItem'; Where = 'SolutionProjectID IN (SELECT SolutionProjectID FROM dbo.SolutionProject WHERE SolutionID = @sid)'; ParentsFirst = 'ParentItemID' },
    @{ Name = 'SolutionLog';         Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionNote';        Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionNoteVersion'; Where = 'SolutionNoteID IN (SELECT SolutionNoteID FROM dbo.SolutionNote WHERE SolutionID = @sid)' },
    @{ Name = 'SolutionNoteStep';    Where = 'SolutionNoteID IN (SELECT SolutionNoteID FROM dbo.SolutionNote WHERE SolutionID = @sid)' },
    @{ Name = 'SolutionTermType';     Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionTermCategory'; Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionTerm';         Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionTermCategoryXRef'; Where = 'SolutionTermID IN (SELECT SolutionTermID FROM dbo.SolutionTerm WHERE SolutionID = @sid)' },
    @{ Name = 'SolutionTestPlan';      Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionTestPlanLevel'; Where = 'SolutionTestPlanID IN (SELECT SolutionTestPlanID FROM dbo.SolutionTestPlan WHERE SolutionID = @sid)' },
    @{ Name = 'SolutionTestGroup';     Where = 'SolutionTestPlanID IN (SELECT SolutionTestPlanID FROM dbo.SolutionTestPlan WHERE SolutionID = @sid)' },
    @{ Name = 'SolutionTestCase';      Where = 'SolutionTestGroupID IN (SELECT g.SolutionTestGroupID FROM dbo.SolutionTestGroup g JOIN dbo.SolutionTestPlan p ON p.SolutionTestPlanID = g.SolutionTestPlanID WHERE p.SolutionID = @sid)' },
    @{ Name = 'SolutionTestCheck';     Where = 'SolutionTestCaseID IN (SELECT c.SolutionTestCaseID FROM dbo.SolutionTestCase c JOIN dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID JOIN dbo.SolutionTestPlan p ON p.SolutionTestPlanID = g.SolutionTestPlanID WHERE p.SolutionID = @sid)' },
    @{ Name = 'SolutionTestResult';    Where = 'SolutionTestCaseID IN (SELECT c.SolutionTestCaseID FROM dbo.SolutionTestCase c JOIN dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID JOIN dbo.SolutionTestPlan p ON p.SolutionTestPlanID = g.SolutionTestPlanID WHERE p.SolutionID = @sid)' },
    @{ Name = 'SolutionTestSkip';      Where = 'SolutionTestCaseID IN (SELECT c.SolutionTestCaseID FROM dbo.SolutionTestCase c JOIN dbo.SolutionTestGroup g ON g.SolutionTestGroupID = c.SolutionTestGroupID JOIN dbo.SolutionTestPlan p ON p.SolutionTestPlanID = g.SolutionTestPlanID WHERE p.SolutionID = @sid)' },
    @{ Name = 'SolutionConfigEnvironment'; Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionConfigGroup';       Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionConfigProperty';    Where = 'SolutionConfigGroupID IN (SELECT SolutionConfigGroupID FROM dbo.SolutionConfigGroup WHERE SolutionID = @sid)' },
    @{ Name = 'SolutionConfigValue';       Where = 'SolutionConfigPropertyID IN (SELECT p.SolutionConfigPropertyID FROM dbo.SolutionConfigProperty p JOIN dbo.SolutionConfigGroup g ON g.SolutionConfigGroupID = p.SolutionConfigGroupID WHERE g.SolutionID = @sid)' },
    @{ Name = 'SolutionConfigValueAlsoSet'; Where = 'SolutionConfigValueID IN (SELECT v.SolutionConfigValueID FROM dbo.SolutionConfigValue v JOIN dbo.SolutionConfigProperty p ON p.SolutionConfigPropertyID = v.SolutionConfigPropertyID JOIN dbo.SolutionConfigGroup g ON g.SolutionConfigGroupID = p.SolutionConfigGroupID WHERE g.SolutionID = @sid)' },
    @{ Name = 'SolutionConfigGridAxis';      Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionConfigGridAxisValue'; Where = 'SolutionConfigGridAxisID IN (SELECT SolutionConfigGridAxisID FROM dbo.SolutionConfigGridAxis WHERE SolutionID = @sid)' },
    @{ Name = 'SolutionConfigGridColumn';    Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionConfigGridColumnTag'; Where = 'SolutionConfigGridColumnID IN (SELECT SolutionConfigGridColumnID FROM dbo.SolutionConfigGridColumn WHERE SolutionID = @sid)' },
    @{ Name = 'SolutionConfigGridGroup';     Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionConfigGridProperty';  Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionConfigGridValue';     Where = 'SolutionConfigGridPropertyID IN (SELECT SolutionConfigGridPropertyID FROM dbo.SolutionConfigGridProperty WHERE SolutionID = @sid)' },
    @{ Name = 'SolutionConfigGridValueAlsoSet'; Where = 'SolutionConfigGridValueID IN (SELECT v.SolutionConfigGridValueID FROM dbo.SolutionConfigGridValue v JOIN dbo.SolutionConfigGridProperty p ON p.SolutionConfigGridPropertyID = v.SolutionConfigGridPropertyID WHERE p.SolutionID = @sid)' },
    @{ Name = 'SolutionFile';        Where = 'SolutionID = @sid' },
    @{ Name = 'SolutionFileFiling';  Where = 'SolutionFileID IN (SELECT SolutionFileID FROM dbo.SolutionFile WHERE SolutionID = @sid)' },
    @{ Name = 'Conversation';        Where = 'ConversationID IN (SELECT ConversationID FROM dbo.SolutionConversation WHERE SolutionID = @sid)' },
    @{ Name = 'ConversationMessage'; Where = 'ConversationID IN (SELECT ConversationID FROM dbo.SolutionConversation WHERE SolutionID = @sid)' },
    @{ Name = 'SolutionConversation'; Where = 'SolutionID = @sid' }
)
