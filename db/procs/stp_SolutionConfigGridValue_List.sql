SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_SolutionConfigGridValue_List') IS NOT NULL DROP PROCEDURE dbo.stp_SolutionConfigGridValue_List
GO
CREATE PROCEDURE dbo.stp_SolutionConfigGridValue_List
    @prmSolutionID INT
AS

/*

TEST
----

    EXEC stp_SolutionConfigGridValue_List 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
10/09/2026  Felix        Story 5611 Solution Configuration II: every filled-in cell of one solution -- value, where it is read
                         from, note, state, and who changed it last.

*/

SET NOCOUNT ON

SELECT v.SolutionConfigGridValueID, v.SolutionConfigGridPropertyID, v.SolutionConfigGridColumnID, v.ValueText,
       src.SourceTitle, v.ValueNote, st.StateCode, m.MemberCode AS ModifiedByCode, m.MemberName AS ModifiedByName,
       v.CreatedDate, v.ModifiedDate
FROM   dbo.SolutionConfigGridValue v
JOIN   dbo.SolutionConfigGridProperty p      ON p.SolutionConfigGridPropertyID   = v.SolutionConfigGridPropertyID
JOIN   dbo.SolutionConfigGridCellState st    ON st.SolutionConfigGridCellStateID = v.SolutionConfigGridCellStateID
LEFT JOIN dbo.SolutionConfigSource src       ON src.SolutionConfigSourceID       = v.SolutionConfigSourceID
LEFT JOIN dbo.SolutionMember m               ON m.SolutionMemberID               = v.ModifiedByMemberID
WHERE  p.SolutionID = @prmSolutionID

GO
