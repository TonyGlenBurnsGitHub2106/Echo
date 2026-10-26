SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LoginSecret_Save') IS NOT NULL DROP PROCEDURE dbo.stp_LoginSecret_Save
GO
CREATE PROCEDURE dbo.stp_LoginSecret_Save

    @prmLoginSetupID    int,
    @prmPassCipher      varbinary(max),
    @prmPlaceholder     nvarchar(50) = N'(encrypted)'

AS

/*

TEST
====

    EXEC dbo.stp_LoginSecret_Save @prmLoginSetupID = 0, @prmPassCipher = 0x00   -- refused

VERSION HISTORY

Date        Developer    Description
==========  ===========  ==========================================================================================
10/14/2026  Felix Vance  Story 5630. Stores one login's password encrypted with the Leonardo login vault key and
                         parks the cosmetic placeholder in LoginSetup.Pass -- both in ONE transaction, so a login
                         can never end up with its plain text removed and no cipher stored.

                         The caller (desktop cLoginSecretDAL, LeonardoWeb EntityLoginService) has already decrypted
                         the cipher again and compared it to the plain text before calling this. This proc refuses:
                           * a missing login;
                           * the "Leonardo Secrets" login, which holds the secrets-backup passphrase and must stay
                             readable from a restored database on a blank machine;
                           * anything that is not a vault cipher (header "LV1"), so a stray DPAPI blob or garbage
                             can never replace a good cipher;
                           * a login whose Pass does not hold the real password right now -- that plain text is
                             what trg_Update_LoginSetup copies, readable, into LoginSetup_UPDATED_LOG, so every
                             cipher has an unencrypted twin.

*/

BEGIN

    SET NOCOUNT ON
    SET XACT_ABORT ON

    IF @prmLoginSetupID IS NULL OR @prmLoginSetupID <= 0
       OR NOT EXISTS (SELECT 1 FROM dbo.LoginSetup WHERE LoginSetupID = @prmLoginSetupID)
    BEGIN
        RAISERROR('That login does not exist. Nothing was saved.', 16, 1)
        RETURN
    END

    IF EXISTS (SELECT 1 FROM dbo.LoginSetup WHERE LoginSetupID = @prmLoginSetupID AND LoginName = 'Leonardo Secrets')
    BEGIN
        RAISERROR('The "Leonardo Secrets" login holds the backup passphrase and must stay readable on a rebuilt machine, so it is never encrypted. Nothing was saved.', 16, 1)
        RETURN
    END

    IF @prmPassCipher IS NULL OR DATALENGTH(@prmPassCipher) < 64
       OR SUBSTRING(@prmPassCipher, 1, 3) <> 0x4C5631
    BEGIN
        RAISERROR('The encrypted password is not a Leonardo vault cipher. Nothing was saved.', 16, 1)
        RETURN
    END

    -- The unencrypted copy (Tony, 2026-10-14: "make sure that all files that are encrypted are
    -- also saved somewhere not encrypted"). Pass must hold the real password at this moment:
    -- the UPDATE below then makes trg_Update_LoginSetup copy it, readable, into
    -- LoginSetup_UPDATED_LOG (and so into every nightly database backup) before the
    -- placeholder replaces it. A cipher written any other way would have no plain copy.
    IF EXISTS (SELECT 1 FROM dbo.LoginSetup
               WHERE LoginSetupID = @prmLoginSetupID
                 AND (Pass IS NULL OR Pass = N'' OR Pass = @prmPlaceholder))
    BEGIN
        RAISERROR('The password to encrypt is not in the login itself, so no readable copy would be kept. Nothing was saved.', 16, 1)
        RETURN
    END

    BEGIN TRANSACTION

        IF EXISTS (SELECT 1 FROM dbo.LoginSecret WHERE LoginSetupID = @prmLoginSetupID)
            UPDATE  dbo.LoginSecret
            SET     PassCipher   = @prmPassCipher,
                    ModifiedDate = GETDATE()
            WHERE   LoginSetupID = @prmLoginSetupID
        ELSE
            INSERT INTO dbo.LoginSecret (LoginSetupID, PassCipher, CreatedDate)
            VALUES (@prmLoginSetupID, @prmPassCipher, GETDATE())

        UPDATE  dbo.LoginSetup
        SET     Pass = @prmPlaceholder
        WHERE   LoginSetupID = @prmLoginSetupID

    COMMIT TRANSACTION

END

GO
