SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Contact_Delete') IS NOT NULL DROP TRIGGER dbo.trg_Contact_Delete
GO
/*

Trigger trg_Contact_Delete on dbo.Contact.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into Contact_LOG.

*/

CREATE TRIGGER dbo.trg_Contact_Delete ON dbo.Contact AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.Contact_LOG (IsInsertion, DeletedDate, ContactID, ContactName, TeamID, IsActive, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), ContactID, ContactName, TeamID, IsActive, CreatedDate, ModifiedDate FROM Deleted; END;

GO
