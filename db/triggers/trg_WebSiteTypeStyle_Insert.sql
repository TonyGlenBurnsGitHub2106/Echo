SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_WebSiteTypeStyle_Insert') IS NOT NULL DROP TRIGGER dbo.trg_WebSiteTypeStyle_Insert
GO
CREATE TRIGGER dbo.trg_WebSiteTypeStyle_Insert ON dbo.WebSiteTypeStyle AFTER INSERT AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.WebSiteTypeStyle_LOG (IsInsertion, WebSiteTypeID, ColorHex, SortOrder, ModifiedDate)
    SELECT 1, WebSiteTypeID, ColorHex, SortOrder, ModifiedDate FROM Inserted; END;

GO
