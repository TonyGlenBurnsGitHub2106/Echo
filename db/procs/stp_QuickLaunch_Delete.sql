SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_QuickLaunch_Delete') IS NOT NULL DROP PROCEDURE dbo.stp_QuickLaunch_Delete
GO
CREATE PROCEDURE dbo.stp_QuickLaunch_Delete
    @prmQuickLaunchID INT
AS

/*

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-05-16  TBurns       Remove a quick-launch pin.

*/

SET NOCOUNT ON

DELETE FROM dbo.QuickLaunch
WHERE QuickLaunchID = @prmQuickLaunchID

GO
