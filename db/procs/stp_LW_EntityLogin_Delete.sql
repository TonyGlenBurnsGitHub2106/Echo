SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LW_EntityLogin_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_LW_EntityLogin_Delete
GO
CREATE PROCEDURE dbo.stp_LW_EntityLogin_Delete
    @prmEntityLoginSetupXRefID INT
AS

SET NOCOUNT ON

DECLARE @LoginSetupID INT

SELECT @LoginSetupID = LoginSetupID FROM dbo.EntityLoginSetupXRef WHERE EntityLoginSetupXRefID = @prmEntityLoginSetupXRefID
DELETE FROM dbo.EntityLoginSetupXRef WHERE EntityLoginSetupXRefID = @prmEntityLoginSetupXRefID

IF @LoginSetupID IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.EntityLoginSetupXRef WHERE LoginSetupID = @LoginSetupID) BEGIN
    DELETE FROM dbo.LoginSetup WHERE LoginSetupID = @LoginSetupID
END

GO
