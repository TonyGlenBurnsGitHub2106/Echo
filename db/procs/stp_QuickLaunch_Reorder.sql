SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_QuickLaunch_Reorder') IS NOT NULL DROP PROCEDURE dbo.stp_QuickLaunch_Reorder
GO
CREATE PROCEDURE dbo.stp_QuickLaunch_Reorder
    @prmQuickLaunchID INT,
    @prmSortOrder     INT
AS

/*

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-05-16  TBurns       Set SortOrder for a single pin. The service calls this in a loop with the new order
                         (10, 20, 30, ...) after a drag-and-drop reorder on the manage page or dashboard.

*/

SET NOCOUNT ON

UPDATE dbo.QuickLaunch
SET    SortOrder   = @prmSortOrder,
       ModifiedUtc = SYSUTCDATETIME()
WHERE  QuickLaunchID = @prmQuickLaunchID

GO
