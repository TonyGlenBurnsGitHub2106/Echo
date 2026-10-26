SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Update_LoginSecret') IS NOT NULL DROP TRIGGER dbo.trg_Update_LoginSecret
GO
CREATE TRIGGER dbo.trg_Update_LoginSecret ON dbo.LoginSecret
    FOR UPDATE
AS
    INSERT INTO dbo.LoginSecret_LOG (IsInsertion, LoginSecretID, LoginSetupID, PassCipher, CreatedDate, ModifiedDate)
    SELECT 0, LoginSecretID, LoginSetupID, PassCipher, CreatedDate, ModifiedDate
    FROM Deleted;

GO
