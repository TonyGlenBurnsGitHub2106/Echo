SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopicType_ListForSolution') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopicType_ListForSolution
GO
CREATE PROCEDURE dbo.stp_SolutionTopicType_ListForSolution
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionTopicType_ListForSolution 200

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): the kinds of part one solution is made of (Chapter, Character, Account,
                         Environment ...), in their order, each with what its number counts and how many parts
                         it has.

*/

SET NOCOUNT ON

SELECT  t.SolutionTopicTypeID, t.SolutionID, t.TypeName, t.FigureLabel, t.SortOrder,
        (SELECT COUNT(*) FROM dbo.SolutionTopic o WHERE o.SolutionTopicTypeID = t.SolutionTopicTypeID) AS PartCount
FROM    dbo.SolutionTopicType t
WHERE   t.SolutionID = @prmSolutionID
ORDER BY t.SortOrder, t.TypeName

GO
