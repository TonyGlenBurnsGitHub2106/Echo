SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_WebSiteType_Save') IS NOT NULL DROP PROCEDURE dbo.stp_WebSiteType_Save
GO
CREATE PROCEDURE dbo.stp_WebSiteType_Save
    @prmWebSiteTypeID INT,
    @prmWebSiteType   NVARCHAR(50),
    @prmColorHex      VARCHAR(7) = NULL
AS

/*

Organized Web tab (Story 3525): rename and / or recolour one website type. A name another
type already has is refused (the names are unique; Categories and Types offers a merge
instead). The built-in "Business" type -- the default for a new web address -- keeps its
name. Returns the stp_WebSiteType_List shape.

TEST
----

    BEGIN TRAN; EXEC dbo.stp_WebSiteType_Save 22, N'Blogs', '#0E7490'; ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-24  Felix        New (Story 3525).

*/

SET NOCOUNT ON
SET XACT_ABORT ON

DECLARE @name  VARCHAR(50) = LTRIM(RTRIM(ISNULL(@prmWebSiteType, N'')))
DECLARE @old   VARCHAR(50) = (SELECT WebSiteType FROM dbo.WebSiteType WHERE WebSiteTypeID = @prmWebSiteTypeID)
DECLARE @color VARCHAR(7)  = NULLIF(LTRIM(RTRIM(ISNULL(@prmColorHex, ''))), '')

IF @old IS NULL
BEGIN
    RAISERROR('That website type no longer exists.', 16, 1)
    RETURN
END
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
IF @old = 'Business' AND @name <> @old COLLATE Latin1_General_CS_AS
BEGIN
    RAISERROR('"Business" is the type every new web address starts with, so it keeps its name.', 16, 1)
    RETURN
END
IF EXISTS (SELECT 1 FROM dbo.WebSiteType WHERE WebSiteType = @name AND WebSiteTypeID <> @prmWebSiteTypeID)
BEGIN
    RAISERROR('There is already a website type with that name.', 16, 1)
    RETURN
END

BEGIN TRAN
    IF @name <> @old COLLATE Latin1_General_CS_AS
        UPDATE dbo.WebSiteType SET WebSiteType = @name WHERE WebSiteTypeID = @prmWebSiteTypeID

    IF EXISTS (SELECT 1 FROM dbo.WebSiteTypeStyle WHERE WebSiteTypeID = @prmWebSiteTypeID)
        UPDATE dbo.WebSiteTypeStyle SET ColorHex = @color, ModifiedDate = GETDATE() WHERE WebSiteTypeID = @prmWebSiteTypeID
    ELSE IF @color IS NOT NULL
        INSERT INTO dbo.WebSiteTypeStyle (WebSiteTypeID, ColorHex) VALUES (@prmWebSiteTypeID, @color)
COMMIT

SELECT  t.WebSiteTypeID,
        ISNULL(t.WebSiteType, '')        AS WebSiteType,
        ISNULL(s.ColorHex, '')           AS ColorHex,
        ISNULL(s.SortOrder, 1000000)     AS SortOrder,
        (SELECT COUNT(*) FROM dbo.tblWebAddress w WHERE w.WebSiteTypeID = t.WebSiteTypeID) AS UseCount
FROM    dbo.WebSiteType t LEFT JOIN dbo.WebSiteTypeStyle s ON s.WebSiteTypeID = t.WebSiteTypeID
WHERE   t.WebSiteTypeID = @prmWebSiteTypeID

GO
