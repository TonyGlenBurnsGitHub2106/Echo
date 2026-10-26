SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Delete_LoginSetup') IS NOT NULL DROP TRIGGER dbo.trg_Delete_LoginSetup
GO
/*

Trigger trg_Delete_LoginSetup on dbo.LoginSetup.

VERSION HISTORY

Date        Developer      Description
----------  -------------  ----------------------------------------------------------
2026-09-11  Stewart Vance  Scripted out of the live database (Story 3487). The object
                           existed only inside the server; this file is now its source.

*/


CREATE TRIGGER [dbo].[trg_Delete_LoginSetup] ON [dbo].[LoginSetup] 
    FOR DELETE
AS

    INSERT INTO dbo.LoginSetup_LOG (CreatedDate, EffectiveDate, EntityID, LoginSetupID, Notes, Pass, ThroughDate, UserID, VendorID)
    SELECT CreatedDate, EffectiveDate, EntityID, LoginSetupID, Notes, Pass, ThroughDate, UserID, VendorID
    FROM Deleted

GO
