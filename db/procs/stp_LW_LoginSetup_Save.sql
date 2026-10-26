SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_LW_LoginSetup_Save') IS NOT NULL DROP PROCEDURE dbo.stp_LW_LoginSetup_Save
GO
CREATE PROCEDURE dbo.stp_LW_LoginSetup_Save
    @prmEntityLoginSetupXRefID INT,
    @prmLoginSetupID           INT,
    @prmParentEntityID         INT,
    @prmLoginName              VARCHAR(50),
    @prmLoginUserName          VARCHAR(50),
    @prmPass                   VARCHAR(50),
    @prmUserID                 INT,
    @prmNotes                  VARCHAR(5500),
    @prmEffectiveDate          VARCHAR(10),
    @prmThroughDate            VARCHAR(10)
AS

/*

LeonardoWeb: insert or update a LoginSetup row attached to a parent entity.
Creates tblEntity (type 204) on insert; always (re)writes EntityLoginSetupXRef.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-05-10  Claude       New LeonardoWeb proc.

*/

SET NOCOUNT ON

DECLARE @EntityID INT,
        @LoginID  INT,
        @XrefID   INT,
        @EffDt    DATETIME,
        @ThruDt   DATETIME

SET @LoginID = @prmLoginSetupID
SET @XrefID  = @prmEntityLoginSetupXRefID
IF @prmEffectiveDate IS NULL OR @prmEffectiveDate = '' SET @EffDt  = GETDATE()
ELSE                                                    SET @EffDt  = CAST(@prmEffectiveDate AS DATETIME)
IF @prmThroughDate   IS NULL OR @prmThroughDate   = '' SET @ThruDt = NULL
ELSE                                                    SET @ThruDt = CAST(@prmThroughDate   AS DATETIME)

IF @prmLoginSetupID = 0 BEGIN
    EXEC dbo.stp_Entity_Create @prmEntityTypeID = 204,
                               @prmTempTypeDescription = 'LoginSetup',
                               @prmOutPutIdentity = @EntityID OUTPUT

    INSERT INTO dbo.LoginSetup (EntityID, LoginName, LoginUserName, Pass, UserID, VendorID, Notes, EffectiveDate, ThroughDate)
    SELECT @EntityID, @prmLoginName, @prmLoginUserName, @prmPass, @prmUserID, 0, @prmNotes, @EffDt, @ThruDt

    SET @LoginID = SCOPE_IDENTITY()
END ELSE BEGIN
    UPDATE dbo.LoginSetup
    SET LoginName     = @prmLoginName,
        LoginUserName = @prmLoginUserName,
        Pass          = @prmPass,
        UserID        = @prmUserID,
        Notes         = @prmNotes,
        EffectiveDate = @EffDt,
        ThroughDate   = @ThruDt
    WHERE LoginSetupID = @prmLoginSetupID
END

IF @prmParentEntityID > 0 BEGIN
    IF @prmEntityLoginSetupXRefID = 0 BEGIN
        INSERT INTO dbo.EntityLoginSetupXRef (LoginSetupID, EntityID, EffectiveDate, ThroughDate)
        SELECT @LoginID, @prmParentEntityID, @EffDt, @ThruDt
        SET @XrefID = SCOPE_IDENTITY()
    END ELSE BEGIN
        UPDATE dbo.EntityLoginSetupXRef
        SET EffectiveDate = @EffDt,
            ThroughDate   = @ThruDt
        WHERE EntityLoginSetupXRefID = @prmEntityLoginSetupXRefID
    END
END

SELECT @LoginID AS LoginSetupID, @XrefID AS EntityLoginSetupXRefID

GO
