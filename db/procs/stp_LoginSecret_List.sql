SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LoginSecret_List') IS NOT NULL DROP PROCEDURE dbo.stp_LoginSecret_List
GO
CREATE PROCEDURE dbo.stp_LoginSecret_List

AS

/*

TEST
====

    EXEC dbo.stp_LoginSecret_List

VERSION HISTORY

Date        Developer    Description
==========  ===========  ==========================================================================================
10/14/2026  Felix Vance  Story 5630. Every encrypted login's cipher, keyed by LoginSetupID. The table holds one row
                         per encrypted login, so one read beats a lookup per row. LeonardoWeb uses it to show the real
                         password on a record's Logins tab and to know which logins must be re-encrypted on save.

*/

BEGIN

    SET NOCOUNT ON

    SELECT  LoginSetupID,
            PassCipher
    FROM    dbo.LoginSecret
    ORDER BY LoginSetupID

END

GO
