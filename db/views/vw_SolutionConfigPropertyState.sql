SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.vw_SolutionConfigPropertyState') IS NOT NULL DROP VIEW dbo.vw_SolutionConfigPropertyState
GO
CREATE VIEW dbo.vw_SolutionConfigPropertyState
AS
SELECT  p.SolutionConfigPropertyID,
        g.SolutionID,
        p.SolutionConfigGroupID,
        e.EnvCount,
        ISNULL(v.SetCount, 0)      AS SetCount,
        ISNULL(v.DistinctCount, 0) AS DistinctCount,
        CAST(CASE WHEN t.IsSecret = 1                  THEN 'secret'
                  WHEN ISNULL(v.SetCount, 0) = 0       THEN 'empty'
                  WHEN v.SetCount < e.EnvCount         THEN 'incomplete'
                  WHEN v.DistinctCount = 1             THEN 'all'
                  WHEN v.DistinctCount = e.EnvCount    THEN 'none'
                  ELSE 'some' END AS VARCHAR(12)) AS MatchState
FROM    dbo.SolutionConfigProperty p
JOIN    dbo.SolutionConfigGroup g ON g.SolutionConfigGroupID = p.SolutionConfigGroupID
JOIN    dbo.SolutionConfigType t  ON t.SolutionConfigTypeID  = p.SolutionConfigTypeID
CROSS APPLY (SELECT COUNT(*) AS EnvCount
             FROM   dbo.SolutionConfigEnvironment ce
             WHERE  ce.SolutionID = g.SolutionID) e
OUTER APPLY (SELECT COUNT(*) AS SetCount, COUNT(DISTINCT LTRIM(RTRIM(cv.ValueText))) AS DistinctCount
             FROM   dbo.SolutionConfigValue cv
             JOIN   dbo.SolutionConfigEnvironment ce ON ce.SolutionConfigEnvironmentID = cv.SolutionConfigEnvironmentID
             WHERE  cv.SolutionConfigPropertyID = p.SolutionConfigPropertyID
             AND    ce.SolutionID = g.SolutionID
             AND    LTRIM(RTRIM(ISNULL(cv.ValueText, N''))) <> N'') v;

GO
