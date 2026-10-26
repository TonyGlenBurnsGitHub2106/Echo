SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_TypeLookup_FindOrCreate') IS NOT NULL DROP PROCEDURE dbo.stp_TypeLookup_FindOrCreate
GO
CREATE PROCEDURE dbo.stp_TypeLookup_FindOrCreate
    @prmLookup VARCHAR(60),
    @prmName   NVARCHAR(100)
AS

/*

Add-in-place for the simple "type" pick-lists (Story 3550): a user typing a type
name that does not exist yet in a record dialog taps the green "+ Add" row, and
this resolves the name to its id -- creating the row when nobody has used that
name yet -- so the dialog never has to be left to set up a type first.

@prmLookup is the same slug the read side uses (GET /api/lookups/{slug}):

    vendor-types, contact-types, address-types, phone-types, web-address-types,
    user-defined-entity-types, list-types

Every branch is static SQL against one whitelisted table; an unknown slug is an
error. None of these type tables is EntityID-based (checked in tblEntityType:
only WebSiteType is, and it has its own stp_WebSiteType_FindOrCreate). Three of
them have no IDENTITY on their key (tblAddressType, tblPhoneType,
tblWebAddressType), so their new id is MAX + 1 under an exclusive app lock.

Returns one row: Id, Name, WasCreated -- the GET /api/lookups row shape plus the flag.

TEST
----

    BEGIN TRAN
    EXEC dbo.stp_TypeLookup_FindOrCreate @prmLookup = 'vendor-types', @prmName = N'Test Type 3550'
    EXEC dbo.stp_TypeLookup_FindOrCreate @prmLookup = 'vendor-types', @prmName = N'test type 3550'
    ROLLBACK

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-28  Felix        New (Story 3550).
2026-10-26  Felix        Story 5653 (Echo): only web-address-types -- Echo has none of the other type tables.

*/

SET NOCOUNT ON

DECLARE @name VARCHAR(50)
DECLARE @id INT
DECLARE @created BIT = 0
DECLARE @lookup VARCHAR(60) = LTRIM(RTRIM(ISNULL(@prmLookup, '')))
DECLARE @typed NVARCHAR(100) = LTRIM(RTRIM(ISNULL(@prmName, N'')))

IF @typed = N''
BEGIN
    RAISERROR('A type needs a name.', 16, 1)
    RETURN
END
IF LEN(@typed) > 50
BEGIN
    RAISERROR('A type name is 50 characters at most.', 16, 1)
    RETURN
END
SET @name = CAST(@typed AS VARCHAR(50))

IF @lookup NOT IN ('web-address-types')   -- Story 5653: Echo has only this type list
BEGIN
    RAISERROR('That list does not support adding a type in place.', 16, 1)
    RETURN
END

BEGIN TRAN

-- One writer at a time, so two taps in the same instant cannot both insert,
-- and the MAX + 1 keys below cannot collide.
EXEC sp_getapplock @Resource = 'stp_TypeLookup_FindOrCreate', @LockMode = 'Exclusive',
                   @LockOwner = 'Transaction', @LockTimeout = 10000

IF @lookup = 'web-address-types'
BEGIN
    SELECT @id = WebAddressTypeID FROM dbo.tblWebAddressType WHERE LTRIM(RTRIM(WebAddressType)) = @name
    IF @id IS NULL
    BEGIN
        SELECT @id = ISNULL(MAX(WebAddressTypeID), 0) + 1 FROM dbo.tblWebAddressType
        INSERT INTO dbo.tblWebAddressType (WebAddressTypeID, WebAddressType) VALUES (@id, @name)
        SET @created = 1
    END
    SELECT @name = WebAddressType FROM dbo.tblWebAddressType WHERE WebAddressTypeID = @id
END

COMMIT

SELECT @id AS Id, @name AS Name, @created AS WasCreated

GO
