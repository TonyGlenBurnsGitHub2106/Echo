SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Update_tblEntityType') IS NOT NULL DROP TRIGGER dbo.trg_Update_tblEntityType
GO
CREATE TRIGGER [dbo].[trg_Update_tblEntityType] ON [dbo].[tblEntityType] 
    FOR UPDATE
AS

    INSERT INTO dbo.tblEntityType_UPDATED_LOG (CreatedDate, DeletedRowsArchived, EntityCrossRefTable, EntityCrossRefTableIdentityColumnName, EntityDisplayColumnName, EntityIdentityColumnName, EntityIDRequiredToSave, EntitySuppressList, EntityTable, EntityType, EntityTypeID, Notes, UpdatedRowsArchived, UserSearchable)
    SELECT CreatedDate, DeletedRowsArchived, EntityCrossRefTable, EntityCrossRefTableIdentityColumnName, EntityDisplayColumnName, EntityIdentityColumnName, EntityIDRequiredToSave, EntitySuppressList, EntityTable, EntityType, EntityTypeID, Notes, UpdatedRowsArchived, UserSearchable
    FROM Deleted

GO
