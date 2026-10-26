SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_WebSiteType_FindOrCreate') IS NOT NULL DROP PROCEDURE dbo.stp_WebSiteType_FindOrCreate
GO
CREATE PROCEDURE dbo.stp_WebSiteType_FindOrCreate
    @prmWebSiteType NVARCHAR(50)
AS

/*

Organized Web tab (Story 3525): resolve a typed website type to its id, creating it when
nobody has used that name yet -- how a new type is born from the Web tab or the web address
dialog. WebSiteType is EntityID-based (EntityTypeID 55), so a new row gets its EntityID from
stp_Entity_Create first, exactly as the desktop does. Returns the stp_WebSiteType_List shape
plus WasCreated.

TEST
----

    EXEC dbo.stp_WebSiteType_FindOrCreate @prmWebSiteType = N'Business'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-24  Felix        New (Story 3525).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @name VARCHAR(50) = LTRIM(RTRIM(ISNULL(@prmWebSiteType, N'')))
DECLARE @id INT, @created BIT = 0, @entityID INT
IF @name = ''
BEGIN
    RAISERROR('A website type needs a name.', 16, 1)
    RETURN
END
IF LEN(@prmWebSiteType) > 50
BEGIN
    RAISERROR('A website type name is 50 characters at most.', 16, 1)
    RETURN
END

SELECT @id = WebSiteTypeID FROM dbo.WebSiteType WHERE WebSiteType = @name
IF @id IS NULL
BEGIN
    BEGIN TRAN
        EXEC dbo.stp_Entity_Create @prmEntityTypeID = 55, @prmTempTypeDescription = 'WebSiteType',
                                   @prmOutPutIdentity = @entityID OUTPUT
        INSERT INTO dbo.WebSiteType (EntityID, WebSiteType) VALUES (@entityID, @name)
        SET @id = SCOPE_IDENTITY()
    COMMIT
    SET @created = 1
END

SELECT  t.WebSiteTypeID,
        ISNULL(t.WebSiteType, '')        AS WebSiteType,
        ISNULL(s.ColorHex, '')           AS ColorHex,
        ISNULL(s.SortOrder, 1000000)     AS SortOrder,
        (SELECT COUNT(*) FROM dbo.tblWebAddress w WHERE w.WebSiteTypeID = t.WebSiteTypeID) AS UseCount,
        @created AS WasCreated
FROM    dbo.WebSiteType t LEFT JOIN dbo.WebSiteTypeStyle s ON s.WebSiteTypeID = t.WebSiteTypeID
WHERE   t.WebSiteTypeID = @id

GO
