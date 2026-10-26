SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Delete_WebSiteType') IS NOT NULL DROP TRIGGER dbo.trg_Delete_WebSiteType
GO
CREATE TRIGGER [dbo].[trg_Delete_WebSiteType] ON [dbo].[WebSiteType] 
    FOR DELETE
AS

    INSERT INTO dbo.WebSiteType_LOG (EntityID, Notes, WebSiteType, WebSiteTypeID)
    SELECT EntityID, Notes, WebSiteType, WebSiteTypeID
    FROM Deleted

GO
