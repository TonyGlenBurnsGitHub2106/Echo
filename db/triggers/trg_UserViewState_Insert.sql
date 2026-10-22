SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_UserViewState_Insert') IS NOT NULL DROP TRIGGER dbo.trg_UserViewState_Insert
GO
/*

Trigger trg_UserViewState_Insert on dbo.UserViewState.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-28  Felix        Audit row into UserViewState_LOG (remembered view state, desktop browser + PWA).

*/

CREATE TRIGGER dbo.trg_UserViewState_Insert ON dbo.UserViewState AFTER INSERT AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.UserViewState_LOG (IsInsertion, UserViewStateID, UserID, StateKey, StateValue, CreatedDate, ModifiedDate)
    SELECT 1, UserViewStateID, UserID, StateKey, StateValue, CreatedDate, ModifiedDate FROM Inserted; END;

GO
