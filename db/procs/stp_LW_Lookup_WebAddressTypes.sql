SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LW_Lookup_WebAddressTypes') IS NOT NULL DROP PROCEDURE dbo.stp_LW_Lookup_WebAddressTypes
GO
CREATE PROCEDURE dbo.stp_LW_Lookup_WebAddressTypes
AS
SET NOCOUNT ON
SELECT  WebAddressTypeID AS Id,
        WebAddressType   AS Name
FROM    dbo.tblWebAddressType
ORDER BY WebAddressType

GO
