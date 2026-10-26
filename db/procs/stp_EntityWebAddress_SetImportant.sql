SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_EntityWebAddress_SetImportant') IS NOT NULL DROP PROCEDURE dbo.stp_EntityWebAddress_SetImportant
GO
CREATE PROCEDURE dbo.stp_EntityWebAddress_SetImportant
    @prmEntityID    INT,
    @prmXRefID      INT,
    @prmIsImportant BIT
AS

/*

Organized Web tab (Story 3525): flag one web address on a record Important, or clear it. The
flag belongs to the address's place on THIS record. Returns Changed (0 when the xref is not
on it).

TEST
----

    BEGIN TRAN; EXEC dbo.stp_EntityWebAddress_SetImportant 41, 1, 1; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-24  Felix        New (Story 3525).

*/

SET NOCOUNT ON

IF NOT EXISTS (SELECT 1 FROM dbo.tblEntityWebAddressXRef WHERE EntityWebAddressXRefID = @prmXRefID AND EntityID = @prmEntityID)
BEGIN
    SELECT 0 AS Changed
    RETURN
END

IF EXISTS (SELECT 1 FROM dbo.EntityWebAddressFlag WHERE EntityWebAddressXRefID = @prmXRefID)
    UPDATE dbo.EntityWebAddressFlag SET IsImportant = ISNULL(@prmIsImportant, 0), ModifiedDate = GETDATE()
    WHERE  EntityWebAddressXRefID = @prmXRefID
ELSE
    INSERT INTO dbo.EntityWebAddressFlag (EntityWebAddressXRefID, IsImportant) VALUES (@prmXRefID, ISNULL(@prmIsImportant, 0))

SELECT 1 AS Changed

GO
