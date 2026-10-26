SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Delete_tblEntityWebAddressXRef') IS NOT NULL DROP TRIGGER dbo.trg_Delete_tblEntityWebAddressXRef
GO
/*

Trigger trg_Delete_tblEntityWebAddressXRef on dbo.tblEntityWebAddressXRef.

VERSION HISTORY

Date        Developer      Description
----------  -------------  ----------------------------------------------------------
2026-09-11  Stewart Vance  Scripted out of the live database (Story 3487). The object
                           existed only inside the server; this file is now its source.

*/


CREATE TRIGGER [dbo].[trg_Delete_tblEntityWebAddressXRef] ON [dbo].[tblEntityWebAddressXRef] 
    FOR DELETE
AS

    INSERT INTO dbo.tblEntityWebAddressXRef_LOG (CreatedDate, EntityID, EntityWebAddressXRefID, WebAddressID)
    SELECT CreatedDate, EntityID, EntityWebAddressXRefID, WebAddressID
    FROM Deleted

GO
