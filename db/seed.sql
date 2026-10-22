SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
/*
Echo seed data (Story 5653): the lookups every solution shares, with the SAME ids Leonardo uses,
so a solution copied over from Leonardo lines up. Idempotent: a row that exists is left alone.
Generated from the Leonardo database on 2026-10-21.
*/

SET IDENTITY_INSERT dbo.SolutionStatus ON
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionStatus WHERE SolutionStatusID = 1) INSERT INTO dbo.SolutionStatus (SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder) VALUES (1, N'todo', N'Not started', 0, N'#6B7280', 1)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionStatus WHERE SolutionStatusID = 2) INSERT INTO dbo.SolutionStatus (SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder) VALUES (2, N'inprogress', N'In progress', 0, N'#2563EB', 2)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionStatus WHERE SolutionStatusID = 3) INSERT INTO dbo.SolutionStatus (SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder) VALUES (3, N'blocked', N'Blocked', 0, N'#DC2626', 3)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionStatus WHERE SolutionStatusID = 4) INSERT INTO dbo.SolutionStatus (SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder) VALUES (4, N'done', N'Done', 1, N'#16A34A', 4)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionStatus WHERE SolutionStatusID = 5) INSERT INTO dbo.SolutionStatus (SolutionStatusID, StatusCode, StatusName, IsClosed, ColorHex, SortOrder) VALUES (5, N'cancelled', N'Skipped', 1, N'#9CA3AF', 5)
SET IDENTITY_INSERT dbo.SolutionStatus OFF
GO
SET IDENTITY_INSERT dbo.SolutionLogType ON
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionLogType WHERE SolutionLogTypeID = 1) INSERT INTO dbo.SolutionLogType (SolutionLogTypeID, LogTypeCode, LogTypeName, SortOrder) VALUES (1, N'note', N'Note', 1)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionLogType WHERE SolutionLogTypeID = 2) INSERT INTO dbo.SolutionLogType (SolutionLogTypeID, LogTypeCode, LogTypeName, SortOrder) VALUES (2, N'command', N'Command', 2)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionLogType WHERE SolutionLogTypeID = 3) INSERT INTO dbo.SolutionLogType (SolutionLogTypeID, LogTypeCode, LogTypeName, SortOrder) VALUES (3, N'result', N'Result', 3)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionLogType WHERE SolutionLogTypeID = 4) INSERT INTO dbo.SolutionLogType (SolutionLogTypeID, LogTypeCode, LogTypeName, SortOrder) VALUES (4, N'decision', N'Decision', 4)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionLogType WHERE SolutionLogTypeID = 5) INSERT INTO dbo.SolutionLogType (SolutionLogTypeID, LogTypeCode, LogTypeName, SortOrder) VALUES (5, N'blocker', N'Blocker', 5)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionLogType WHERE SolutionLogTypeID = 6) INSERT INTO dbo.SolutionLogType (SolutionLogTypeID, LogTypeCode, LogTypeName, SortOrder) VALUES (6, N'question', N'Question', 6)
SET IDENTITY_INSERT dbo.SolutionLogType OFF
GO
SET IDENTITY_INSERT dbo.SolutionNoteType ON
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = 1) INSERT INTO dbo.SolutionNoteType (SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder) VALUES (1, N'runbook', N'Runbook', 8)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = 2) INSERT INTO dbo.SolutionNoteType (SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder) VALUES (2, N'strategy', N'Strategy', 2)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = 3) INSERT INTO dbo.SolutionNoteType (SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder) VALUES (3, N'reference', N'Reference', 9)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = 10) INSERT INTO dbo.SolutionNoteType (SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder) VALUES (10, N'instructions', N'Instructions', 1)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = 11) INSERT INTO dbo.SolutionNoteType (SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder) VALUES (11, N'productionreferences', N'Production - References', 7)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = 12) INSERT INTO dbo.SolutionNoteType (SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder) VALUES (12, N'qareferences', N'QA - References', 6)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = 13) INSERT INTO dbo.SolutionNoteType (SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder) VALUES (13, N'localreferences', N'Local  - References', 5)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = 14) INSERT INTO dbo.SolutionNoteType (SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder) VALUES (14, N'status', N'Status', 3)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionNoteType WHERE SolutionNoteTypeID = 15) INSERT INTO dbo.SolutionNoteType (SolutionNoteTypeID, NoteTypeCode, NoteTypeName, SortOrder) VALUES (15, N'contacts', N'Contacts', 4)
SET IDENTITY_INSERT dbo.SolutionNoteType OFF
GO
SET IDENTITY_INSERT dbo.SolutionConfigType ON
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigType WHERE SolutionConfigTypeID = 1) INSERT INTO dbo.SolutionConfigType (SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder) VALUES (1, N'text', N'Text', 0, 1)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigType WHERE SolutionConfigTypeID = 2) INSERT INTO dbo.SolutionConfigType (SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder) VALUES (2, N'path', N'Folder or file path', 0, 2)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigType WHERE SolutionConfigTypeID = 3) INSERT INTO dbo.SolutionConfigType (SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder) VALUES (3, N'host', N'Host or address', 0, 3)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigType WHERE SolutionConfigTypeID = 4) INSERT INTO dbo.SolutionConfigType (SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder) VALUES (4, N'port', N'Port', 0, 4)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigType WHERE SolutionConfigTypeID = 5) INSERT INTO dbo.SolutionConfigType (SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder) VALUES (5, N'url', N'URL', 0, 5)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigType WHERE SolutionConfigTypeID = 6) INSERT INTO dbo.SolutionConfigType (SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder) VALUES (6, N'number', N'Number or size', 0, 6)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigType WHERE SolutionConfigTypeID = 7) INSERT INTO dbo.SolutionConfigType (SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder) VALUES (7, N'flag', N'On / off', 0, 7)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigType WHERE SolutionConfigTypeID = 8) INSERT INTO dbo.SolutionConfigType (SolutionConfigTypeID, TypeCode, TypeName, IsSecret, SortOrder) VALUES (8, N'secret', N'Secret (value never stored)', 1, 8)
SET IDENTITY_INSERT dbo.SolutionConfigType OFF
GO
SET IDENTITY_INSERT dbo.SolutionTestLevel ON
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTestLevel WHERE SolutionTestLevelID = 4) INSERT INTO dbo.SolutionTestLevel (SolutionTestLevelID, LevelTitle) VALUES (4, N'Manual')
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTestLevel WHERE SolutionTestLevelID = 5) INSERT INTO dbo.SolutionTestLevel (SolutionTestLevelID, LevelTitle) VALUES (5, N'Unit tested (canned)')
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTestLevel WHERE SolutionTestLevelID = 6) INSERT INTO dbo.SolutionTestLevel (SolutionTestLevelID, LevelTitle) VALUES (6, N'End to end')
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTestLevel WHERE SolutionTestLevelID = 7) INSERT INTO dbo.SolutionTestLevel (SolutionTestLevelID, LevelTitle) VALUES (7, N'Unit tested')
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionTestLevel WHERE SolutionTestLevelID = 8) INSERT INTO dbo.SolutionTestLevel (SolutionTestLevelID, LevelTitle) VALUES (8, N'Passed')
SET IDENTITY_INSERT dbo.SolutionTestLevel OFF
GO
SET IDENTITY_INSERT dbo.SolutionConfigGridCellState ON
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigGridCellState WHERE SolutionConfigGridCellStateID = 1) INSERT INTO dbo.SolutionConfigGridCellState (SolutionConfigGridCellStateID, StateCode, StateName, StateDescription, SortOrder) VALUES (1, N'observed', N'Observed', N'Seen on the running system.', 1)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigGridCellState WHERE SolutionConfigGridCellStateID = 2) INSERT INTO dbo.SolutionConfigGridCellState (SolutionConfigGridCellStateID, StateCode, StateName, StateDescription, SortOrder) VALUES (2, N'planned', N'Planned', N'The value it will have -- the place does not exist yet, or has not been deployed.', 2)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionConfigGridCellState WHERE SolutionConfigGridCellStateID = 3) INSERT INTO dbo.SolutionConfigGridCellState (SolutionConfigGridCellStateID, StateCode, StateName, StateDescription, SortOrder) VALUES (3, N'unverified', N'Unverified', N'Written down, but nobody has checked it on the running system yet.', 3)
SET IDENTITY_INSERT dbo.SolutionConfigGridCellState OFF
GO
SET IDENTITY_INSERT dbo.SolutionMember ON
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionMember WHERE SolutionMemberID = 1) INSERT INTO dbo.SolutionMember (SolutionMemberID, MemberCode, MemberName, IsAgent, IsActive, SortOrder) VALUES (1, N'tony', N'Tony', 0, 1, 1)
IF NOT EXISTS (SELECT 1 FROM dbo.SolutionMember WHERE SolutionMemberID = 4) INSERT INTO dbo.SolutionMember (SolutionMemberID, MemberCode, MemberName, IsAgent, IsActive, SortOrder) VALUES (4, N'rex', N'Rex', 1, 1, 4)
SET IDENTITY_INSERT dbo.SolutionMember OFF
GO
