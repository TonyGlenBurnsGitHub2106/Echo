SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Delete_tblWebAddress') IS NOT NULL DROP TRIGGER dbo.trg_Delete_tblWebAddress
GO
CREATE TRIGGER [dbo].[trg_Delete_tblWebAddress] ON [dbo].[tblWebAddress] 
    FOR DELETE
AS

    INSERT INTO dbo.tblWebAddress_LOG (EntityID, Notes, WebAddress, WebAddressID, WebAddressName, WebAddressTypeID, WebSiteTypeID)
    SELECT EntityID, Notes, WebAddress, WebAddressID, WebAddressName, WebAddressTypeID, WebSiteTypeID
    FROM Deleted

GO
