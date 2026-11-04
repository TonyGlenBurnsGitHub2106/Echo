SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_Solution_ApplyShape') IS NOT NULL DROP PROCEDURE dbo.stp_Solution_ApplyShape
GO
CREATE PROCEDURE dbo.stp_Solution_ApplyShape
    @prmSolutionID      INT,
    @prmShapeCode       VARCHAR(20),     -- programming | writing | life | copy
    @prmFromSolutionID  INT = NULL,      -- copy: the solution whose shape to take
    @prmMemberCode      VARCHAR(20)
AS

/*

TEST
----

    EXEC stp_Solution_ApplyShape 4600, 'programming', NULL, 'tony'
    EXEC stp_Solution_ApplyShape 4601, 'copy', 6, 'tony'

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
11/04/2026  Felix        Story 5695 (Echo), Release 6: start a solution from a shape. From the deep review of 11/02:
                         every solution is built by hand from nothing, though they fall into a few kinds. A shape gives
                         a NEW, EMPTY solution (no projects, no topics yet) its starting frame:
                           programming  projects Design, Build, Test, Release; kind of part Environment with the
                                        parts Dev, QA and Production; tags Risk and Idea
                           writing      projects Draft, Revise, Publish; kinds of part Chapter (counts words) and
                                        Character; tags Idea and Cut
                           life         projects Gather the facts, Decide, Do it; kind of part Account (counts a
                                        balance); tags Important and Waiting on someone
                           copy         the SKELETON of another solution: its projects (names, descriptions, order,
                                        owners), kinds of part, parts (names, kinds, order), tags, and its term types
                                        and categories. No items, notes, log, files, terms, questions or decisions,
                                        and every status starts again at the first one.
                         Projects and parts start at the first status. One transaction. Returns the counts made.
11/04/2026  Felix        Names are normalized as the save procs do (lower case, runs of spaces made one), and a term
                         type or category the new solution already has is not copied twice.

*/

SET NOCOUNT ON

DECLARE @shape VARCHAR(20) = LOWER(LTRIM(RTRIM(ISNULL(@prmShapeCode, ''))))
IF NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = @prmSolutionID)
BEGIN RAISERROR('That solution no longer exists. Nothing was added.', 16, 1) RETURN END
IF @shape NOT IN ('programming', 'writing', 'life', 'copy')
BEGIN RAISERROR('A shape is programming, writing, life or copy. Nothing was added.', 16, 1) RETURN END
IF EXISTS (SELECT 1 FROM dbo.SolutionProject WHERE SolutionID = @prmSolutionID)
   OR EXISTS (SELECT 1 FROM dbo.SolutionTopic WHERE SolutionID = @prmSolutionID)
   OR EXISTS (SELECT 1 FROM dbo.SolutionTopicType WHERE SolutionID = @prmSolutionID)
BEGIN RAISERROR('A shape is for a new, empty solution. This one has projects or topics already. Nothing was added.', 16, 1) RETURN END
IF @shape = 'copy' AND NOT EXISTS (SELECT 1 FROM dbo.Solution WHERE SolutionID = ISNULL(@prmFromSolutionID, 0))
BEGIN RAISERROR('The solution to copy the shape of no longer exists. Nothing was added.', 16, 1) RETURN END
IF @shape = 'copy' AND @prmFromSolutionID = @prmSolutionID
BEGIN RAISERROR('A solution cannot take its own shape. Nothing was added.', 16, 1) RETURN END

DECLARE @memberId INT = NULL
EXEC dbo.stp_SolutionMember_FindOrCreate @prmMemberCode, @memberId OUTPUT
DECLARE @first INT = (SELECT TOP 1 SolutionStatusID FROM dbo.SolutionStatus ORDER BY SortOrder, SolutionStatusID)
DECLARE @who VARCHAR(20) = LOWER(@prmMemberCode)

-- what to make: one list per thing, from the shape or from the other solution
DECLARE @projects TABLE (Rn INT IDENTITY(1,1), ProjectName NVARCHAR(300), ProjectDescription NVARCHAR(MAX), OwnerMemberID INT)
DECLARE @types    TABLE (Rn INT IDENTITY(1,1), OldID INT, TypeName NVARCHAR(100), FigureLabel NVARCHAR(50), NewID INT)
DECLARE @topics   TABLE (Rn INT IDENTITY(1,1), TopicName NVARCHAR(200), KindCode VARCHAR(20), TypeRn INT, OldTypeID INT)
DECLARE @termTypes TABLE (Rn INT IDENTITY(1,1), TypeName NVARCHAR(100))
DECLARE @termCats  TABLE (Rn INT IDENTITY(1,1), CategoryName NVARCHAR(100))

IF @shape = 'programming'
BEGIN
    INSERT INTO @projects (ProjectName) VALUES (N'Design'), (N'Build'), (N'Test'), (N'Release')
    INSERT INTO @types (TypeName) VALUES (N'Environment')
    INSERT INTO @topics (TopicName, KindCode, TypeRn) VALUES (N'Dev', 'part', 1), (N'QA', 'part', 1), (N'Production', 'part', 1),
                                                           (N'Risk', 'tag', NULL), (N'Idea', 'tag', NULL)
END
IF @shape = 'writing'
BEGIN
    INSERT INTO @projects (ProjectName) VALUES (N'Draft'), (N'Revise'), (N'Publish')
    INSERT INTO @types (TypeName, FigureLabel) VALUES (N'Chapter', N'words'), (N'Character', NULL)
    INSERT INTO @topics (TopicName, KindCode, TypeRn) VALUES (N'Idea', 'tag', NULL), (N'Cut', 'tag', NULL)
END
IF @shape = 'life'
BEGIN
    INSERT INTO @projects (ProjectName) VALUES (N'Gather the facts'), (N'Decide'), (N'Do it')
    INSERT INTO @types (TypeName, FigureLabel) VALUES (N'Account', N'balance')
    INSERT INTO @topics (TopicName, KindCode, TypeRn) VALUES (N'Important', 'tag', NULL), (N'Waiting on someone', 'tag', NULL)
END
IF @shape = 'copy'
BEGIN
    INSERT INTO @projects (ProjectName, ProjectDescription, OwnerMemberID)
    SELECT ProjectName, ProjectDescription, OwnerMemberID FROM dbo.SolutionProject
    WHERE  SolutionID = @prmFromSolutionID ORDER BY SortOrder, SolutionProjectID
    INSERT INTO @types (OldID, TypeName, FigureLabel)
    SELECT SolutionTopicTypeID, TypeName, FigureLabel FROM dbo.SolutionTopicType
    WHERE  SolutionID = @prmFromSolutionID ORDER BY SortOrder, SolutionTopicTypeID
    INSERT INTO @topics (TopicName, KindCode, OldTypeID)
    SELECT TopicName, TopicKindCode, SolutionTopicTypeID FROM dbo.SolutionTopic
    WHERE  SolutionID = @prmFromSolutionID AND TopicKindCode IN ('part', 'tag') ORDER BY TopicKindCode, SortOrder, SolutionTopicID
    INSERT INTO @termTypes (TypeName)
    SELECT TypeName FROM dbo.SolutionTermType WHERE SolutionID = @prmFromSolutionID ORDER BY SortOrder, SolutionTermTypeID
    INSERT INTO @termCats (CategoryName)
    SELECT CategoryName FROM dbo.SolutionTermCategory WHERE SolutionID = @prmFromSolutionID ORDER BY CategoryName
END

SET XACT_ABORT ON
BEGIN TRY
    BEGIN TRANSACTION
    INSERT INTO dbo.SolutionProject (SolutionID, ProjectName, ProjectDescription, SolutionStatusID, OwnerMemberID, SortOrder, ModifiedBy)
    SELECT @prmSolutionID, ProjectName, ProjectDescription, @first, OwnerMemberID, Rn, @who FROM @projects ORDER BY Rn

    DECLARE @rn INT = 1, @n INT = (SELECT COUNT(*) FROM @types)
    WHILE @rn <= @n
    BEGIN
        INSERT INTO dbo.SolutionTopicType (SolutionID, TypeName, NormalizedName, FigureLabel, SortOrder)
        SELECT @prmSolutionID, TypeName, LOWER(TypeName), FigureLabel, Rn FROM @types WHERE Rn = @rn
        UPDATE @types SET NewID = CAST(SCOPE_IDENTITY() AS INT) WHERE Rn = @rn
        SET @rn = @rn + 1
    END

    INSERT INTO dbo.SolutionTopic (SolutionID, TopicName, NormalizedName, TopicSummary, AuthorMemberID, ModifiedBy,
                                   TopicKindCode, SolutionTopicTypeID, SolutionStatusID, FigureValue, AssumptionHolds, SortOrder)
    SELECT @prmSolutionID, t.TopicName, LOWER(t.TopicName), NULL, @memberId, @who, t.KindCode,
           ty.NewID, CASE WHEN t.KindCode = 'part' THEN @first ELSE NULL END, NULL, NULL,
           ROW_NUMBER() OVER (PARTITION BY t.KindCode ORDER BY t.Rn)
    FROM   @topics t
    LEFT JOIN @types ty ON ty.Rn = t.TypeRn OR (t.OldTypeID IS NOT NULL AND ty.OldID = t.OldTypeID)

    -- the same normalized names the save procs write: lower case, runs of spaces made one
    WHILE EXISTS (SELECT 1 FROM dbo.SolutionTopicType WHERE SolutionID = @prmSolutionID AND NormalizedName LIKE N'%  %')
        UPDATE dbo.SolutionTopicType SET NormalizedName = REPLACE(NormalizedName, N'  ', N' ')
        WHERE  SolutionID = @prmSolutionID AND NormalizedName LIKE N'%  %'
    WHILE EXISTS (SELECT 1 FROM dbo.SolutionTopic WHERE SolutionID = @prmSolutionID AND NormalizedName LIKE N'%  %')
        UPDATE dbo.SolutionTopic SET NormalizedName = REPLACE(NormalizedName, N'  ', N' ')
        WHERE  SolutionID = @prmSolutionID AND NormalizedName LIKE N'%  %'

    INSERT INTO dbo.SolutionTermType (SolutionID, TypeName, SortOrder)
    SELECT @prmSolutionID, t.TypeName, t.Rn FROM @termTypes t
    WHERE  NOT EXISTS (SELECT 1 FROM dbo.SolutionTermType x WHERE x.SolutionID = @prmSolutionID AND x.TypeName = t.TypeName)
    INSERT INTO dbo.SolutionTermCategory (SolutionID, CategoryName)
    SELECT @prmSolutionID, c.CategoryName FROM @termCats c
    WHERE  NOT EXISTS (SELECT 1 FROM dbo.SolutionTermCategory x WHERE x.SolutionID = @prmSolutionID AND x.CategoryName = c.CategoryName)
    COMMIT TRANSACTION
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION
    DECLARE @msg NVARCHAR(2000) = ERROR_MESSAGE()
    RAISERROR(@msg, 16, 1)
    RETURN
END CATCH

SELECT (SELECT COUNT(*) FROM @projects) AS ProjectCount, (SELECT COUNT(*) FROM @types) AS KindOfPartCount,
       (SELECT COUNT(*) FROM @topics WHERE KindCode = 'part') AS PartCount, (SELECT COUNT(*) FROM @topics WHERE KindCode = 'tag') AS TagCount,
       (SELECT COUNT(*) FROM @termTypes) AS TermTypeCount, (SELECT COUNT(*) FROM @termCats) AS TermCategoryCount

GO
