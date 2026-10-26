SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_WebSiteTypeStyle_Update') IS NOT NULL DROP TRIGGER dbo.trg_WebSiteTypeStyle_Update
GO
CREATE TRIGGER dbo.trg_WebSiteTypeStyle_Update ON dbo.WebSiteTypeStyle AFTER UPDATE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.WebSiteTypeStyle_LOG (IsInsertion, WebSiteTypeID, ColorHex, SortOrder, ModifiedDate)
    SELECT 0, WebSiteTypeID, ColorHex, SortOrder, ModifiedDate FROM Inserted; END;

GO
