SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LW_Lookup_WebSiteTypes') IS NOT NULL DROP PROCEDURE dbo.stp_LW_Lookup_WebSiteTypes
GO
CREATE PROCEDURE dbo.stp_LW_Lookup_WebSiteTypes
AS
SET NOCOUNT ON
SELECT  WebSiteTypeID AS Id,
        WebSiteType   AS Name
FROM    dbo.WebSiteType
ORDER BY WebSiteType

GO
