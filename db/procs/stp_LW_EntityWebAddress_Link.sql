SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LW_EntityWebAddress_Link') IS NOT NULL DROP PROCEDURE dbo.stp_LW_EntityWebAddress_Link
GO
CREATE PROCEDURE dbo.stp_LW_EntityWebAddress_Link
    @prmParentEntityID INT,
    @prmWebAddressID   INT
AS

/*

LeonardoWeb: attach an EXISTING web address (tblWebAddress row) to a parent
entity by creating a tblEntityWebAddressXRef row only. Unlike
stp_LW_WebAddress_Save this never touches tblWebAddress -- the shared web-address
record is left exactly as it is, so linking the same web address to a second
owner cannot alter the URL, name, or notes the first owner sees. Idempotent: a
second link of the same pair is a no-op. Mirrors stp_LW_EntityPhone_Link. The
xref carries no EffectiveDate / EndDate columns (only CreatedDate), matching
stp_LW_WebAddress_Save.

Pure DML (no result set) on purpose: the repository calls it through
JdbcTemplate.update(), and the SQL Server driver throws if an update-shaped
call produces a result set.

TEST
----

    EXEC dbo.stp_LW_EntityWebAddress_Link @prmParentEntityID = 105435, @prmWebAddressID = 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-06-23  Felix        New proc for LeonardoWeb add-existing-web-address.

*/

SET NOCOUNT ON

IF @prmParentEntityID > 0 AND @prmWebAddressID > 0
   AND NOT EXISTS (SELECT 1 FROM dbo.tblEntityWebAddressXRef
                   WHERE EntityID = @prmParentEntityID AND WebAddressID = @prmWebAddressID)
BEGIN
    INSERT INTO dbo.tblEntityWebAddressXRef (WebAddressID, EntityID, CreatedDate)
    VALUES (@prmWebAddressID, @prmParentEntityID, GETDATE())
END

GO
