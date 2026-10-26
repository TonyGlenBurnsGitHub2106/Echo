SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Update_tblWebAddress') IS NOT NULL DROP TRIGGER dbo.trg_Update_tblWebAddress
GO
/*

Trigger trg_Update_tblWebAddress on dbo.tblWebAddress.

VERSION HISTORY

Date        Developer      Description
----------  -------------  ----------------------------------------------------------
2026-09-11  Stewart Vance  Scripted out of the live database (Story 3487). The object
                           existed only inside the server; this file is now its source.

*/


CREATE TRIGGER [dbo].[trg_Update_tblWebAddress] ON [dbo].[tblWebAddress] 
    FOR UPDATE
AS

    INSERT INTO dbo.tblWebAddress_UPDATED_LOG (EntityID, Notes, WebAddress, WebAddressID, WebAddressName, WebAddressTypeID, WebSiteTypeID)
    SELECT EntityID, Notes, WebAddress, WebAddressID, WebAddressName, WebAddressTypeID, WebSiteTypeID
    FROM Deleted

GO
