SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Insert_LoginSecret') IS NOT NULL DROP TRIGGER dbo.trg_Insert_LoginSecret
GO
CREATE TRIGGER dbo.trg_Insert_LoginSecret ON dbo.LoginSecret
    FOR INSERT
AS
    INSERT INTO dbo.LoginSecret_LOG (IsInsertion, LoginSecretID, LoginSetupID, PassCipher, CreatedDate, ModifiedDate)
    SELECT 1, LoginSecretID, LoginSetupID, PassCipher, CreatedDate, ModifiedDate
    FROM Inserted;

GO
