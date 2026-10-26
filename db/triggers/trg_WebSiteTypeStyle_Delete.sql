SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_WebSiteTypeStyle_Delete') IS NOT NULL DROP TRIGGER dbo.trg_WebSiteTypeStyle_Delete
GO
CREATE TRIGGER dbo.trg_WebSiteTypeStyle_Delete ON dbo.WebSiteTypeStyle AFTER DELETE AS
BEGIN SET NOCOUNT ON; INSERT INTO dbo.WebSiteTypeStyle_LOG (IsInsertion, DeletedDate, WebSiteTypeID, ColorHex, SortOrder, ModifiedDate)
    SELECT 0, GETDATE(), WebSiteTypeID, ColorHex, SortOrder, ModifiedDate FROM Deleted; END;

GO
