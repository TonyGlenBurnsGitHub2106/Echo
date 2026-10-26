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
-- ---- Web tab (Story 5653 parity, 2026-10-26): entity types, address types, website types, lookup registry ----
-- The four entity types Echo makes entities of, with Leonardo's ids. EntityID 0: Echo does not make the
-- types themselves entities.
SET IDENTITY_INSERT dbo.tblEntityType ON
IF NOT EXISTS (SELECT 1 FROM dbo.tblEntityType WHERE EntityTypeID = 7) INSERT INTO dbo.tblEntityType (EntityTypeID, EntityType, EntityTable, EntityIdentityColumnName, EntityCrossRefTable, EntityCrossRefTableIdentityColumnName, UserSearchable, EntityDisplayColumnName, EntityIDRequiredToSave, DeletedRowsArchived, UpdatedRowsArchived, EntityID) VALUES (7, N'Web Address', N'tblWebAddress', N'WebAddressID', N'tblEntityWebAddressXRef', N'EntityWebAddressXRefID', 0, N'WebAddressName', 1, 1, 1, 0)
IF NOT EXISTS (SELECT 1 FROM dbo.tblEntityType WHERE EntityTypeID = 55) INSERT INTO dbo.tblEntityType (EntityTypeID, EntityType, EntityTable, EntityIdentityColumnName, EntityCrossRefTable, EntityCrossRefTableIdentityColumnName, UserSearchable, EntityDisplayColumnName, EntityIDRequiredToSave, DeletedRowsArchived, UpdatedRowsArchived, EntityID) VALUES (55, N'Website Type', N'WebSiteType', N'WebSiteTypeID', N'', N'', 0, N'WebSiteType', 1, 1, 1, 0)
IF NOT EXISTS (SELECT 1 FROM dbo.tblEntityType WHERE EntityTypeID = 204) INSERT INTO dbo.tblEntityType (EntityTypeID, EntityType, EntityTable, EntityIdentityColumnName, EntityCrossRefTable, EntityCrossRefTableIdentityColumnName, UserSearchable, EntityDisplayColumnName, EntityIDRequiredToSave, DeletedRowsArchived, UpdatedRowsArchived, EntityID) VALUES (204, N'Login Setup', N'LoginSetup', N'LoginSetupID', N'EntityLoginSetupXRef', N'EntityLoginSetupXRefID', 1, N'LoginName', 1, 1, 1, 0)
IF NOT EXISTS (SELECT 1 FROM dbo.tblEntityType WHERE EntityTypeID = 222) INSERT INTO dbo.tblEntityType (EntityTypeID, EntityType, EntityTable, EntityIdentityColumnName, EntityCrossRefTable, EntityCrossRefTableIdentityColumnName, UserSearchable, EntityDisplayColumnName, EntityIDRequiredToSave, DeletedRowsArchived, UpdatedRowsArchived, EntityID) VALUES (222, N'Solution', N'Solution', N'SolutionID', N'EntitySolutionXRef', N'EntitySolutionXRefID', 0, N'SolutionName', 1, 1, 1, 0)
SET IDENTITY_INSERT dbo.tblEntityType OFF
GO
-- Address types (no IDENTITY on this table).
IF NOT EXISTS (SELECT 1 FROM dbo.tblWebAddressType WHERE WebAddressTypeID = 1) INSERT INTO dbo.tblWebAddressType (WebAddressTypeID, WebAddressType) VALUES (1, N'Website')
IF NOT EXISTS (SELECT 1 FROM dbo.tblWebAddressType WHERE WebAddressTypeID = 2) INSERT INTO dbo.tblWebAddressType (WebAddressTypeID, WebAddressType) VALUES (2, N'Email Address')
IF NOT EXISTS (SELECT 1 FROM dbo.tblWebAddressType WHERE WebAddressTypeID = 3) INSERT INTO dbo.tblWebAddressType (WebAddressTypeID, WebAddressType) VALUES (3, N'FTP Address')
GO
-- Website types: the general ones, with Leonardo's ids (8 = Business is the page's default). Each is an
-- entity (type 55), made here when the row is.
DECLARE @e INT
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 1) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (1, @e, N'Employment')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (1, NULL, 90)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 2) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (2, @e, N'Shopping')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (2, NULL, 180)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 3) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (3, @e, N'Services')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (3, NULL, 170)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 4) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (4, @e, N'Social')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (4, NULL, 200)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 5) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (5, @e, N'Message Boards')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (5, NULL, 120)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 6) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (6, @e, N'Code Snippet Site')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (6, NULL, 50)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 7) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (7, @e, N'News')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (7, NULL, 140)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 8) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (8, @e, N'Business')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (8, NULL, 240)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 9) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (9, @e, N'Stock Market')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (9, NULL, 210)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 10) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (10, @e, N'Advice Column Sites')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (10, NULL, 20)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 11) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (11, @e, N'Gaming Sites')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (11, NULL, 100)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 12) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (12, @e, N'Acedemic Sites (Schools)')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (12, NULL, 10)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 13) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (13, @e, N'Personal Home Page')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (13, NULL, 160)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 14) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (14, @e, N'Miscellaneous Website Type')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (14, NULL, 130)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 15) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (15, @e, N'Online Accounting')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (15, NULL, 150)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 16) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (16, @e, N'Email')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (16, NULL, 30)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 17) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (17, @e, N'Writing and Literature')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (17, NULL, 230)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 18) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (18, @e, N'Educational')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (18, NULL, 70)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 20) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (20, @e, N'Volunteer')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (20, NULL, 220)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 21) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (21, @e, N'Download Site')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (21, NULL, 60)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 22) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (22, @e, N'Blog')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (22, NULL, 80)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 23) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (23, @e, N'Skype')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (23, NULL, 190)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 24) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (24, @e, N'Instant Messenger')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (24, NULL, 110)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 26) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (26, @e, N'Forms')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (26, NULL, 250)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 27) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (27, @e, N'Unclaimed Property')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (27, NULL, 270)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 28) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (28, @e, N'DMV')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (28, NULL, 40)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 29) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (29, @e, N'Government')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (29, NULL, 260)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 30) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (30, @e, N'Daily Newsletter')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (30, NULL, 280)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 31) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (31, @e, N'Audio Briefing')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (31, NULL, 290)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 32) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (32, @e, N'Weekly Analysis')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (32, NULL, 300)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 33) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (33, @e, N'Quick Skim Aggregator')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
    INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex, SortOrder) VALUES (33, NULL, 310)
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 39) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (39, @e, N'KB')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 41) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (41, @e, N'Quick Start')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 42) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (42, @e, N'How To')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 43) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (43, @e, N'Tech Stacks')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
END
IF NOT EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteTypeID = 44) BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType', @prmOutPutIdentity = @e OUTPUT
    SET IDENTITY_INSERT dbo.WebSiteType ON
    INSERT INTO dbo.WebSiteType (WebSiteTypeID, EntityID, WebSiteType) VALUES (44, @e, N'Plans')
    SET IDENTITY_INSERT dbo.WebSiteType OFF
END
GO
-- Settings -> lists: deleting a website type moves its web addresses (stp_Lookup_Delete reads these).
IF NOT EXISTS (SELECT 1 FROM dbo.LookupRegistry WHERE LookupKey = 'web-site-type') INSERT INTO dbo.LookupRegistry (LookupKey, LookupLabel, ShelfLabel, ItemNoun, TableName, IdColumn, NameColumn, CodeColumn, ProtectedCodes, ReservedNames, PageUrl, SortOrder) VALUES (N'web-site-type', N'Website types', N'Web', N'type', N'WebSiteType', N'WebSiteTypeID', N'WebSiteType', NULL, NULL, N'Business', N'/contacts/contacts.html', 397)
IF NOT EXISTS (SELECT 1 FROM dbo.LookupReference WHERE LookupKey = 'web-site-type' AND RefTable = N'tblWebAddress' AND RefColumn = N'WebSiteTypeID') INSERT INTO dbo.LookupReference (LookupKey, RefTable, RefColumn, RefKind, XrefOwnerColumn, UsageNoun, SortOrder) VALUES (N'web-site-type', N'tblWebAddress', N'WebSiteTypeID', N'required', NULL, N'web addresses', 1)
GO
-- Every solution is an entity now (its Web tab hangs off the EntityID): give the ones made before this
-- change theirs. Rerunning finds none left to do.
DECLARE @sid INT, @eid INT
DECLARE solutionsWithoutEntity CURSOR LOCAL FAST_FORWARD FOR SELECT SolutionID FROM dbo.Solution WHERE EntityID IS NULL
OPEN solutionsWithoutEntity
FETCH NEXT FROM solutionsWithoutEntity INTO @sid
WHILE @@FETCH_STATUS = 0 BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 222, @prmTempTypeDescription = 'Solution', @prmOutPutIdentity = @eid OUTPUT
    UPDATE dbo.Solution SET EntityID = @eid WHERE SolutionID = @sid
    FETCH NEXT FROM solutionsWithoutEntity INTO @sid
END
CLOSE solutionsWithoutEntity
DEALLOCATE solutionsWithoutEntity
GO
