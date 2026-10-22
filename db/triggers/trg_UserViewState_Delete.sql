SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_UserViewState_Delete') IS NOT NULL DROP TRIGGER dbo.trg_UserViewState_Delete
GO
/*

Trigger trg_UserViewState_Delete on dbo.UserViewState.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ----------------------------------------------------------
2026-09-28  Felix        Audit row into UserViewState_LOG (remembered view state, desktop browser + PWA).

*/

CREATE TRIGGER dbo.trg_UserViewState_Delete ON dbo.UserViewState AFTER DELETE AS
BEGIN SET NOCOUNT ON;
    INSERT INTO dbo.UserViewState_LOG (IsInsertion, DeletedDate, UserViewStateID, UserID, StateKey, StateValue, CreatedDate, ModifiedDate)
    SELECT 0, GETDATE(), UserViewStateID, UserID, StateKey, StateValue, CreatedDate, ModifiedDate FROM Deleted; END;

GO
