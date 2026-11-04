SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionTopicType_Get') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionTopicType_Get
GO
CREATE PROCEDURE dbo.stp_SolutionTopicType_Get
    @prmSolutionTopicTypeID INT
AS

/*

TEST
----

    EXEC stp_SolutionTopicType_Get 3

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo): one kind of part, with its solution and how many parts it has.

*/

SET NOCOUNT ON

SELECT  t.SolutionTopicTypeID, t.SolutionID, t.TypeName, t.FigureLabel, t.SortOrder,
        (SELECT COUNT(*) FROM dbo.SolutionTopic o WHERE o.SolutionTopicTypeID = t.SolutionTopicTypeID) AS PartCount
FROM    dbo.SolutionTopicType t
WHERE   t.SolutionTopicTypeID = @prmSolutionTopicTypeID

GO
