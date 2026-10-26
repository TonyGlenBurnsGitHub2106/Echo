SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_EntityWebAddress_CountsFiled') IS NOT NULL DROP PROCEDURE dbo.stp_EntityWebAddress_CountsFiled
GO
CREATE PROCEDURE dbo.stp_EntityWebAddress_CountsFiled
    @prmEntityID INT,
    @prmGroupBy  VARCHAR(20)   = 'type',
    @prmSearch   NVARCHAR(200) = NULL
AS

/*

Organized Web tab (Story 3525): the band headers of the Web tab -- one row per group under
the same grouping and search stp_EntityWebAddress_ListFiled uses, in the same order, with
Cnt and ImportantCnt.

TEST
----

    EXEC dbo.stp_EntityWebAddress_CountsFiled @prmEntityID = 41, @prmGroupBy = 'kind'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-24  Felix        New (Story 3525).

*/

SET NOCOUNT ON

DECLARE @group VARCHAR(20)   = LOWER(ISNULL(@prmGroupBy, 'type'))
DECLARE @q     NVARCHAR(200) = NULLIF(LTRIM(RTRIM(ISNULL(@prmSearch, N''))), N'')
DECLARE @like  NVARCHAR(210) = N'%' + @q + N'%'

;WITH src AS (
    SELECT  v.WebSiteTypeID, v.IsImportant,
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
SELECT  GroupKey, GroupLabel, GroupRank,
        MAX(GroupColor)                                       AS GroupColor,
        CASE WHEN @group = 'type' THEN MAX(WebSiteTypeID) END AS WebSiteTypeID,
        COUNT(*)                                              AS Cnt,
        SUM(CASE WHEN IsImportant = 1 THEN 1 ELSE 0 END)      AS ImportantCnt
FROM    src
GROUP BY GroupKey, GroupLabel, GroupRank
ORDER BY GroupRank, GroupLabel

GO
