SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Delete_LoginSecret') IS NOT NULL DROP TRIGGER dbo.trg_Delete_LoginSecret
GO
CREATE TRIGGER dbo.trg_Delete_LoginSecret ON dbo.LoginSecret
    FOR DELETE
AS
    INSERT INTO dbo.LoginSecret_LOG (IsInsertion, DeletedDate, LoginSecretID, LoginSetupID, PassCipher, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), LoginSecretID, LoginSetupID, PassCipher, CreatedDate, ModifiedDate
    FROM Deleted;

GO
