SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LW_EntityWebAddress_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_LW_EntityWebAddress_Delete
GO
CREATE PROCEDURE dbo.stp_LW_EntityWebAddress_Delete
    @prmEntityWebAddressXRefID INT
AS

SET NOCOUNT ON

DECLARE @WebAddressID INT

SELECT @WebAddressID = WebAddressID FROM dbo.tblEntityWebAddressXRef WHERE EntityWebAddressXRefID = @prmEntityWebAddressXRefID
DELETE FROM dbo.tblEntityWebAddressXRef WHERE EntityWebAddressXRefID = @prmEntityWebAddressXRefID

IF @WebAddressID IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.tblEntityWebAddressXRef WHERE WebAddressID = @WebAddressID) BEGIN
    DELETE FROM dbo.tblWebAddress WHERE WebAddressID = @WebAddressID
END

GO
