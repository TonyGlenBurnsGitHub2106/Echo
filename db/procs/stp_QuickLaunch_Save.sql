SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_QuickLaunch_Save') IS NOT NULL DROP PROCEDURE dbo.stp_QuickLaunch_Save
GO
CREATE PROCEDURE dbo.stp_QuickLaunch_Save
    @prmQuickLaunchID INT,
    @prmLabel         NVARCHAR(80),
    @prmURL           NVARCHAR(400),
    @prmIconKey       NVARCHAR(8)
AS

/*

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-05-16  TBurns       Save a quick-launch pin. INSERT auto-appends (SortOrder = MAX + 10) so the user never
                         touches a number field. UPDATE preserves the existing SortOrder; reorder is a separate
                         proc.

*/

SET NOCOUNT ON

DECLARE @intNewID INT

IF @prmQuickLaunchID <= 0 BEGIN

    DECLARE @intNextSort INT
    SELECT @intNextSort = ISNULL(MAX(SortOrder), 0) + 10 FROM dbo.QuickLaunch

    INSERT INTO dbo.QuickLaunch (Label, URL, IconKey, SortOrder, ModifiedUtc)
    SELECT @prmLabel, @prmURL, @prmIconKey, @intNextSort, SYSUTCDATETIME()

    SET @intNewID = SCOPE_IDENTITY()

END ELSE BEGIN

    UPDATE dbo.QuickLaunch
    SET    Label       = @prmLabel,
           URL         = @prmURL,
           IconKey     = @prmIconKey,
           ModifiedUtc = SYSUTCDATETIME()
    WHERE  QuickLaunchID = @prmQuickLaunchID

    SET @intNewID = @prmQuickLaunchID

END

SELECT @intNewID AS QuickLaunchID

GO
