SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTerm_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTerm_Get
GO
CREATE PROCEDURE dbo.stp_SolutionTerm_Get
    @prmSolutionTermID INT
AS

/*

TEST
----

    EXEC stp_SolutionTerm_Get 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Solution Terms tab: one term, whole -- the full definition and notes, its type, its
                         categories (CategoryList, one per line) and who wrote and last changed it. Also how the
                         page finds a term's solution from a Leonardo Find link (?term=).

*/

SET NOCOUNT ON

SELECT '' AS GroupKey, N'' AS GroupLabel,
       t.SolutionTermID, t.SolutionID, t.Term, t.Definition,
       CAST(0 AS BIT) AS IsTruncated,
       t.SolutionTermTypeID, ty.TypeName, t.TermSource, t.TermNotes,
       CAST(CASE WHEN LEN(ISNULL(t.TermNotes, N'')) > 0 THEN 1 ELSE 0 END AS BIT) AS HasNotes,
       t.IsFavorite,
       ISNULL((SELECT NCHAR(10) + c.CategoryName
               FROM   dbo.SolutionTermCategoryXRef x
               JOIN   dbo.SolutionTermCategory c ON c.SolutionTermCategoryID = x.SolutionTermCategoryID
               WHERE  x.SolutionTermID = t.SolutionTermID
               ORDER BY c.CategoryName
               FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), N'') AS CategoryList,
       a.MemberCode AS AuthorCode, a.MemberName AS AuthorName,
       m.MemberCode AS ModifiedByCode, m.MemberName AS ModifiedByName,
       t.CreatedDate, t.ModifiedDate
FROM   dbo.SolutionTerm t
LEFT JOIN dbo.SolutionTermType ty ON ty.SolutionTermTypeID = t.SolutionTermTypeID
LEFT JOIN dbo.SolutionMember a ON a.SolutionMemberID = t.AuthorMemberID
LEFT JOIN dbo.SolutionMember m ON m.SolutionMemberID = t.ModifiedByMemberID
WHERE  t.SolutionTermID = @prmSolutionTermID

GO
