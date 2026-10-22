SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_QuickLaunch_List') IS NOT NULL DROP PROCEDURE dbo.stp_QuickLaunch_List
GO
CREATE PROCEDURE dbo.stp_QuickLaunch_List
AS

/*

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
2026-05-16  TBurns       List all quick-launch pins for the LeonardoWeb dashboard and manage page.

*/

SET NOCOUNT ON

SELECT QuickLaunchID,
       Label,
       URL,
       IconKey,
       SortOrder,
       CreatedUtc,
       ModifiedUtc
FROM   dbo.QuickLaunch
ORDER  BY SortOrder, QuickLaunchID

GO
