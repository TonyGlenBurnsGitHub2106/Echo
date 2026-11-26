SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Contact_Insert') IS NOT NULL DROP TRIGGER dbo.trg_Contact_Insert
GO
/*

Trigger trg_Contact_Insert on dbo.Contact.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-11-25  Felix        Story 6852 (Daily Work): audit row into Contact_LOG.

*/

CREATE TRIGGER dbo.trg_Contact_Insert ON dbo.Contact AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.Contact_LOG (IsInsertion, ContactID, ContactName, TeamID, IsActive, CreatedDate, ModifiedDate)
    SELECT 1, ContactID, ContactName, TeamID, IsActive, CreatedDate, ModifiedDate FROM Inserted; END;

GO
