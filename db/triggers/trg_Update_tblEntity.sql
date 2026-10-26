SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Update_tblEntity') IS NOT NULL DROP TRIGGER dbo.trg_Update_tblEntity
GO
/*

Trigger trg_Update_tblEntity on dbo.tblEntity.

VERSION HISTORY

Date        Developer      Description
----------  -------------  ----------------------------------------------------------
2026-09-11  Stewart Vance  Scripted out of the live database (Story 3487). The object
                           existed only inside the server; this file is now its source.

*/


CREATE TRIGGER [dbo].[trg_Update_tblEntity] ON [dbo].[tblEntity] 
    FOR UPDATE
AS

    INSERT INTO dbo.tblEntity_UPDATED_LOG (EntityID, EntityTypeID, TempTypeDescription)
    SELECT EntityID, EntityTypeID, TempTypeDescription
    FROM Deleted

GO
