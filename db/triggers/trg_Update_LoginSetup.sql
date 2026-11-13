SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Update_LoginSetup') IS NOT NULL DROP TRIGGER dbo.trg_Update_LoginSetup
GO
/*

Trigger trg_Update_LoginSetup on dbo.LoginSetup.

VERSION HISTORY

Date        Developer      Description
----------  -------------  ----------------------------------------------------------
2026-09-11  Stewart Vance  Scripted out of the live database (Story 3487). The object
                           existed only inside the server; this file is now its source.
11/13/2026  Felix          Story 5653 / Story 5765 (audit fence-5): Echo only. A login that is
                           encrypted (has a LoginSecret row) is logged with the placeholder
                           "(encrypted)", never its plain password. Leonardo keeps a readable
                           twin here on purpose; in Echo that twin would put every work
                           password back into the database and every .bak, the very thing the
                           vault is for. Plain (unencrypted) logins are logged as before.

*/


CREATE TRIGGER [dbo].[trg_Update_LoginSetup] ON [dbo].[LoginSetup] 
    FOR UPDATE
AS

    INSERT INTO dbo.LoginSetup_UPDATED_LOG (CreatedDate, EffectiveDate, EntityID, LoginSetupID, Notes, Pass, ThroughDate, UserID, VendorID)
    -- Story 5653 / Story 5765 (audit fence-5): no plain twin of an encrypted login's password.
    SELECT d.CreatedDate, d.EffectiveDate, d.EntityID, d.LoginSetupID, d.Notes,
           CASE WHEN EXISTS (SELECT 1 FROM dbo.LoginSecret s WHERE s.LoginSetupID = d.LoginSetupID)
                THEN '(encrypted)' ELSE d.Pass END,
           d.ThroughDate, d.UserID, d.VendorID
    FROM Deleted d

GO
