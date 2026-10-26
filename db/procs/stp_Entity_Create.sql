SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Entity_Create') IS NOT NULL DROP PROCEDURE dbo.stp_Entity_Create
GO
CREATE PROCEDURE dbo.stp_Entity_Create
    @prmEntityTypeID        INT,
    @prmTempTypeDescription VARCHAR(50), 
    @prmOutPutIdentity      INT OUTPUT 
AS

/*

TEST
----

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
07/01/2006  TBurns       Create an entity and return the ID. 

*/

SET NOCOUNT ON

INSERT INTO dbo.tblEntity (EntityTypeID, TempTypeDescription)
SELECT @prmEntityTypeID, @prmTempTypeDescription 

SELECT @prmOutPutIdentity = SCOPE_IDENTITY()

GO
