SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.trg_Delete_tblEntityType') IS NOT NULL DROP TRIGGER dbo.trg_Delete_tblEntityType
GO
CREATE TRIGGER [dbo].[trg_Delete_tblEntityType] ON [dbo].[tblEntityType] 
    FOR DELETE
AS

    INSERT INTO dbo.tblEntityType_LOG (CreatedDate, DeletedRowsArchived, EntityCrossRefTable, EntityCrossRefTableIdentityColumnName, EntityDisplayColumnName, EntityIdentityColumnName, EntityIDRequiredToSave, EntitySuppressList, EntityTable, EntityType, EntityTypeID, Notes, UpdatedRowsArchived, UserSearchable)
    SELECT CreatedDate, DeletedRowsArchived, EntityCrossRefTable, EntityCrossRefTableIdentityColumnName, EntityDisplayColumnName, EntityIdentityColumnName, EntityIDRequiredToSave, EntitySuppressList, EntityTable, EntityType, EntityTypeID, Notes, UpdatedRowsArchived, UserSearchable
    FROM Deleted

GO
