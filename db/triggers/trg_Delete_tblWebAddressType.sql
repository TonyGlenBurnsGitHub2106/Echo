SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Delete_tblWebAddressType') IS NOT NULL DROP TRIGGER dbo.trg_Delete_tblWebAddressType
GO
/*

Trigger trg_Delete_tblWebAddressType on dbo.tblWebAddressType.

VERSION HISTORY

Date        Developer      Description
----------  -------------  ----------------------------------------------------------
2026-09-11  Stewart Vance  Scripted out of the live database (Story 3487). The object
                           existed only inside the server; this file is now its source.

*/


CREATE TRIGGER [dbo].[trg_Delete_tblWebAddressType] ON [dbo].[tblWebAddressType] 
    FOR DELETE
AS

    INSERT INTO dbo.tblWebAddressType_LOG (WebAddressType, WebAddressTypeID)
    SELECT WebAddressType, WebAddressTypeID
    FROM Deleted

GO
