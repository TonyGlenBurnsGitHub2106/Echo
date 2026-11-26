SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Contact_Update') IS NOT NULL DROP TRIGGER dbo.trg_Contact_Update
GO
/*

Trigger trg_Contact_Update on dbo.Contact.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into Contact_LOG.

*/

CREATE TRIGGER dbo.trg_Contact_Update ON dbo.Contact AFTER UPDATE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.Contact_LOG (IsInsertion, ContactID, ContactName, TeamID, IsActive, CreatedDate, ModifiedDate)
    SELECT 0, ContactID, ContactName, TeamID, IsActive, CreatedDate, ModifiedDate FROM Inserted; END;

GO
