SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LW_WebAddress_Save') IS NOT NULL DROP PROCEDURE dbo.stp_LW_WebAddress_Save
GO
CREATE PROCEDURE dbo.stp_LW_WebAddress_Save
    @prmEntityWebAddressXRefID INT,
    @prmWebAddressID           INT,
    @prmParentEntityID         INT,
    @prmWebAddressName         VARCHAR(500),
    @prmWebAddress             VARCHAR(900),
    @prmWebAddressTypeID       INT,
    @prmWebSiteTypeID          INT,
    @prmNotes                  VARCHAR(5000)
AS

/*

LeonardoWeb: insert or update a web address attached to a parent entity. Creates
tblEntity (type 7) on insert; always (re)writes tblEntityWebAddressXRef.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-05-10  Claude       New LeonardoWeb proc -- first save proc for web addresses.

*/

SET NOCOUNT ON

DECLARE @EntityID     INT,
        @WebAddressID INT,
        @XrefID       INT

SET @WebAddressID = @prmWebAddressID
SET @XrefID       = @prmEntityWebAddressXRefID

IF @prmWebAddressID = 0 BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 7,
                               @prmTempTypeDescription = 'tblWebAddress',
                               @prmOutPutIdentity = @EntityID OUTPUT

    INSERT INTO dbo.tblWebAddress (WebAddressName, WebAddress, WebAddressTypeID, WebSiteTypeID, EntityID, Notes)
    SELECT @prmWebAddressName, @prmWebAddress, @prmWebAddressTypeID, @prmWebSiteTypeID, @EntityID, @prmNotes

    SET @WebAddressID = SCOPE_IDENTITY()
END ELSE BEGIN
    UPDATE dbo.tblWebAddress
    SET WebAddressName   = @prmWebAddressName,
        WebAddress       = @prmWebAddress,
        WebAddressTypeID = @prmWebAddressTypeID,
        WebSiteTypeID    = @prmWebSiteTypeID,
        Notes            = @prmNotes
    WHERE WebAddressID = @prmWebAddressID
END

IF @prmParentEntityID > 0 BEGIN
    IF @prmEntityWebAddressXRefID = 0 BEGIN
        INSERT INTO dbo.tblEntityWebAddressXRef (WebAddressID, EntityID)
        SELECT @WebAddressID, @prmParentEntityID
        SET @XrefID = SCOPE_IDENTITY()
    END
    -- No xref-only fields to update (no effective dates on this xref).
END

SELECT @WebAddressID AS WebAddressID, @XrefID AS EntityWebAddressXRefID

GO
