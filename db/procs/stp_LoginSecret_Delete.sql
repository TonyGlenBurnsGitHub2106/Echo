SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LoginSecret_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_LoginSecret_Delete
GO
CREATE PROCEDURE dbo.stp_LoginSecret_Delete

    @prmLoginSetupID    int

AS

/*

TEST
====

    EXEC dbo.stp_LoginSecret_Delete @prmLoginSetupID = 0   -- refused

VERSION HISTORY

Date        Developer    Description
==========  ===========  ==========================================================================================
10/14/2026  Felix Vance  Story 5630. Drops one login's cipher -- used ONLY when that login's Pass column already holds
                         a newer plain-text password (a save that could not be re-encrypted), so the stale cipher
                         cannot show or back up the old password. Refuses while Pass still holds the placeholder:
                         then the cipher is the only copy of the password, and dropping it would lose it.
                         trg_Delete_LoginSecret keeps the dropped cipher in LoginSecret_LOG.

*/

BEGIN

    SET NOCOUNT ON
    SET XACT_ABORT ON

    IF EXISTS (SELECT 1 FROM dbo.LoginSetup
               WHERE LoginSetupID = @prmLoginSetupID AND (Pass IS NULL OR Pass = N'' OR Pass = N'(encrypted)'))
    BEGIN
        RAISERROR('This login has no plain-text password to fall back on, so its encrypted password was kept. Nothing was changed.', 16, 1)
        RETURN
    END

    DELETE FROM dbo.LoginSecret
    WHERE  LoginSetupID = @prmLoginSetupID

END

GO
