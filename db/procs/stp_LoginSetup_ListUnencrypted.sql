SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LoginSetup_ListUnencrypted') IS NOT NULL DROP PROCEDURE dbo.stp_LoginSetup_ListUnencrypted
GO
CREATE PROCEDURE dbo.stp_LoginSetup_ListUnencrypted

AS

/*

TEST
====

    EXEC dbo.stp_LoginSetup_ListUnencrypted

VERSION HISTORY

Date        Developer    Description
==========  ===========  ==========================================================================================
11/13/2026  Felix        Story 5653 / Story 5765 (audit fence-5): Echo only. Every login whose password is still kept
                         as plain text -- no LoginSecret row, and a real password in Pass (not empty, not the
                         "(encrypted)" placeholder). LoginVaultSweep encrypts each one when Echo starts with a login
                         vault key. The "Leonardo Secrets" login is left out: stp_LoginSecret_Save refuses it.

*/

BEGIN

    SET NOCOUNT ON

    SELECT  ls.LoginSetupID,
            ls.Pass
    FROM    dbo.LoginSetup ls
    WHERE   NOT EXISTS (SELECT 1 FROM dbo.LoginSecret s WHERE s.LoginSetupID = ls.LoginSetupID)
      AND   ls.Pass IS NOT NULL
      AND   ls.Pass <> ''
      AND   ls.Pass <> '(encrypted)'
      AND   ls.LoginName <> 'Leonardo Secrets'
    ORDER BY ls.LoginSetupID

END

GO
