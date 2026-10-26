SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_EntityLoginSetup_Link') IS NOT NULL DROP PROCEDURE dbo.stp_EntityLoginSetup_Link
GO
CREATE PROCEDURE dbo.stp_EntityLoginSetup_Link
    @prmParentEntityID INT,
    @prmLoginSetupID   INT
AS

/*

Attach an EXISTING login (LoginSetup row) to a parent entity -- a web address, a
vendor, a contact -- by creating an EntityLoginSetupXRef row ONLY. Unlike
stp_LW_LoginSetup_Save this never touches LoginSetup, so linking one login to a
second owner cannot alter the name, user name, or password the first owner sees.
Idempotent: a second link of the same pair is a no-op.

The xref's EffectiveDate / ThroughDate are copied from the login itself so the
list proc's date range renders the same way it does for the original owner.

Pure DML (no result set) on purpose: the repository calls it through
JdbcTemplate.update(), and the SQL Server driver throws if an update-shaped call
produces a result set.

TEST
----

    EXEC dbo.stp_EntityLoginSetup_Link @prmParentEntityID = 98141, @prmLoginSetupID = 85

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-08-06  Felix        New proc for link-an-existing-login.

*/

SET NOCOUNT ON

IF @prmParentEntityID > 0 AND @prmLoginSetupID > 0
   AND EXISTS (SELECT 1 FROM dbo.LoginSetup WHERE LoginSetupID = @prmLoginSetupID)
   AND NOT EXISTS (SELECT 1 FROM dbo.EntityLoginSetupXRef
                   WHERE EntityID = @prmParentEntityID AND LoginSetupID = @prmLoginSetupID)
BEGIN
    INSERT INTO dbo.EntityLoginSetupXRef (EntityID, LoginSetupID, EffectiveDate, ThroughDate, CreatedDate)
    SELECT  @prmParentEntityID, l.LoginSetupID, l.EffectiveDate, l.ThroughDate, GETDATE()
    FROM    dbo.LoginSetup l
    WHERE   l.LoginSetupID = @prmLoginSetupID
END

GO
