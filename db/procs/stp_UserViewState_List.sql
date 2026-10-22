SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_UserViewState_List') IS NOT NULL DROP PROCEDURE dbo.stp_UserViewState_List
GO
CREATE PROCEDURE dbo.stp_UserViewState_List
    @prmUserID    INT,
    @prmKeyPrefix NVARCHAR(200)    -- every key that STARTS with this, e.g. 'leo.cl.groups.16.'
AS

/*

TEST
----

    EXEC stp_UserViewState_List @prmUserID = 1, @prmKeyPrefix = 'leo.cl.'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/28/2026  Felix        Remembered view state: the keys a page asks for by prefix. The prefix is matched literally --
                         LIKE wildcards in it are escaped -- so the read seeks UX_UserViewState_UserID_StateKey.
                         A blank prefix returns nothing, never the whole table.

*/

SET NOCOUNT ON

SET @prmKeyPrefix = LTRIM(RTRIM(ISNULL(@prmKeyPrefix, '')))
IF @prmKeyPrefix = '' RETURN

DECLARE @pattern NVARCHAR(420) =
    REPLACE(REPLACE(REPLACE(REPLACE(@prmKeyPrefix, '!', '!!'), '%', '!%'), '_', '!_'), '[', '![') + '%'

SELECT  StateKey, StateValue, ModifiedDate
FROM    dbo.UserViewState
WHERE   UserID = @prmUserID
AND     StateKey LIKE @pattern ESCAPE '!'
ORDER BY StateKey

GO
