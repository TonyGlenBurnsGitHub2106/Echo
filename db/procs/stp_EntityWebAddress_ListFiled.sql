SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_EntityWebAddress_ListFiled') IS NOT NULL DROP PROCEDURE dbo.stp_EntityWebAddress_ListFiled
GO
CREATE PROCEDURE dbo.stp_EntityWebAddress_ListFiled
    @prmEntityID INT,
    @prmGroupBy  VARCHAR(20)   = 'type',   -- type | kind | none
    @prmSortKey  VARCHAR(20)   = 'name',   -- name | address | type | added | order
    @prmSortDesc BIT           = 0,
    @prmSearch   NVARCHAR(200) = NULL,
    @prmOffset   INT           = 0,
    @prmPageSize INT           = 50
AS

/*

Organized Web tab (Story 3525): one page of a record's Web tab. Grouping AND sorting happen
here so page 2 continues page 1: rows come back by group (website types in the order Tony
set, or by kind -- website / email address), Important addresses first inside a group, then
the chosen sort. The 'order' sort is Tony's own arrangement inside each group
(dbo.EntityWebAddressSequence, set by stp_EntityWebAddress_Reorder) and does NOT lift
Important addresses -- what he arranged is what he sees. Each row carries GroupKey / GroupLabel / GroupColor. @prmSearch matches
the description, the address or the website type.

TEST
----

    EXEC dbo.stp_EntityWebAddress_ListFiled @prmEntityID = 41, @prmGroupBy = 'type'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-24  Felix        New (Story 3525).
2026-10-04  Felix        'order' sort -- Tony's own order inside a group.

*/

SET NOCOUNT ON

DECLARE @group  VARCHAR(20)   = LOWER(ISNULL(@prmGroupBy, 'type'))
DECLARE @sort   VARCHAR(20)   = LOWER(ISNULL(@prmSortKey, 'name'))
DECLARE @desc   BIT           = ISNULL(@prmSortDesc, 0)
DECLARE @q      NVARCHAR(200) = NULLIF(LTRIM(RTRIM(ISNULL(@prmSearch, N''))), N'')
DECLARE @like   NVARCHAR(210) = N'%' + @q + N'%'
DECLARE @offset INT           = CASE WHEN ISNULL(@prmOffset, 0) < 0 THEN 0 ELSE ISNULL(@prmOffset, 0) END
DECLARE @size   INT           = CASE WHEN ISNULL(@prmPageSize, 0) BETWEEN 1 AND 500 THEN @prmPageSize ELSE 50 END

;WITH src AS (
    SELECT  v.*,
            CASE @group
                WHEN 'kind'  THEN 'k:' + CAST(v.WebAddressTypeID AS VARCHAR(12))
                WHEN 'none'  THEN 'all'
                ELSE 't:' + CAST(v.WebSiteTypeID AS VARCHAR(12))
            END AS GroupKey,
            CASE @group
                WHEN 'kind'  THEN CASE WHEN v.WebAddressType = '' THEN 'Other' ELSE v.WebAddressType END
                WHEN 'none'  THEN 'All web addresses'
                ELSE v.WebSiteType
            END AS GroupLabel,
            CASE @group
                WHEN 'kind'  THEN v.WebAddressTypeID
                WHEN 'none'  THEN 0
                ELSE v.TypeSortOrder
            END AS GroupRank,
            CASE WHEN @group = 'type' THEN v.TypeColor ELSE '' END AS GroupColor
    FROM    dbo.vw_EntityWebAddressFiled v
    WHERE   v.EntityID = @prmEntityID
      AND  (@q IS NULL
            OR v.WebAddressName LIKE @like
            OR v.WebAddress     LIKE @like
            OR v.WebSiteType    LIKE @like)
)
SELECT  EntityWebAddressXRefID, EntityID, WebAddressID, WebAddressEntityID, WebAddressName, WebAddress,
        WebAddressTypeID, WebAddressType, WebSiteTypeID, WebSiteType, TypeColor, Notes, NotesPreview,
        IsImportant, AssociatedDate, ItemSortOrder,
        GroupKey, GroupLabel, GroupRank, GroupColor
FROM    src
ORDER BY GroupRank, GroupLabel,
        CASE WHEN @sort = 'order' THEN 0 ELSE CAST(IsImportant AS INT) END DESC,
        CASE WHEN @sort = 'order'   AND @desc = 0 THEN ItemSortOrder END ASC,
        CASE WHEN @sort = 'order'   AND @desc = 1 THEN ItemSortOrder END DESC,
        CASE WHEN @sort = 'address' AND @desc = 0 THEN WebAddress END ASC,
        CASE WHEN @sort = 'address' AND @desc = 1 THEN WebAddress END DESC,
        CASE WHEN @sort = 'type'    AND @desc = 0 THEN WebSiteType END ASC,
        CASE WHEN @sort = 'type'    AND @desc = 1 THEN WebSiteType END DESC,
        CASE WHEN @sort = 'added'   AND @desc = 0 THEN AssociatedDate END ASC,
        CASE WHEN @sort = 'added'   AND @desc = 1 THEN AssociatedDate END DESC,
        CASE WHEN @desc = 0 THEN CASE WHEN WebAddressName = '' THEN WebAddress ELSE WebAddressName END END ASC,
        CASE WHEN @desc = 1 THEN CASE WHEN WebAddressName = '' THEN WebAddress ELSE WebAddressName END END DESC,
        EntityWebAddressXRefID
OFFSET @offset ROWS FETCH NEXT @size ROWS ONLY

GO
