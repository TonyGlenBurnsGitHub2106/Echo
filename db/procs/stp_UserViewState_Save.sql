SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_UserViewState_Save') IS NOT NULL DROP PROCEDURE dbo.stp_UserViewState_Save
GO
CREATE PROCEDURE dbo.stp_UserViewState_Save
    @prmUserID     INT,
    @prmStateKey   NVARCHAR(200),
    @prmStateValue NVARCHAR(4000)
AS

/*

TEST
----

    EXEC stp_UserViewState_Save @prmUserID = 1, @prmStateKey = 'leo.cl.hideChecked.16', @prmStateValue = '1'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/28/2026  Felix        Remembered view state: upsert one key for one user. An unchanged value writes nothing, so a
                         page re-saving the same state does not touch the row or its _LOG. Returns UserViewStateID.

*/

SET NOCOUNT ON

SET @prmStateKey = NULLIF(LTRIM(RTRIM(ISNULL(@prmStateKey, ''))), '')
IF @prmStateKey IS NULL BEGIN
    RAISERROR('A view state needs a key.', 16, 1)
    RETURN
END

DECLARE @id INT = (SELECT TOP 1 UserViewStateID FROM dbo.UserViewState
                   WHERE UserID = @prmUserID AND StateKey = @prmStateKey)

IF @id IS NULL BEGIN
    INSERT INTO dbo.UserViewState (UserID, StateKey, StateValue)
    VALUES (@prmUserID, @prmStateKey, @prmStateValue)
    SET @id = SCOPE_IDENTITY()
END ELSE BEGIN
    UPDATE dbo.UserViewState
    SET    StateValue   = @prmStateValue,
           ModifiedDate = GETDATE()
    WHERE  UserViewStateID = @id
    AND    ISNULL(StateValue, N'') COLLATE Latin1_General_BIN <> ISNULL(@prmStateValue, N'') COLLATE Latin1_General_BIN
END

SELECT @id AS UserViewStateID

GO
