SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.fn_Search_Snippet') IS NOT NULL DROP FUNCTION dbo.fn_Search_Snippet
GO
CREATE FUNCTION dbo.fn_Search_Snippet (@prmText NVARCHAR(MAX), @prmTerm NVARCHAR(200), @prmWidth INT)
RETURNS NVARCHAR(1000)
AS
BEGIN
    DECLARE @text  NVARCHAR(MAX) = REPLACE(REPLACE(REPLACE(ISNULL(@prmText, N''), NCHAR(13), N' '), NCHAR(10), N' '), NCHAR(9), N' ')
    DECLARE @width INT = CASE WHEN ISNULL(@prmWidth, 0) < 40 THEN 240 WHEN @prmWidth > 900 THEN 900 ELSE @prmWidth END
    DECLARE @len   INT = LEN(@text)
    DECLARE @pos   INT = CASE WHEN NULLIF(LTRIM(RTRIM(ISNULL(@prmTerm, N''))), N'') IS NULL THEN 0 ELSE CHARINDEX(@prmTerm, @text) END
    DECLARE @start INT = CASE WHEN @pos > @width / 3 THEN @pos - @width / 3 ELSE 1 END
    DECLARE @out   NVARCHAR(1000) = SUBSTRING(@text, @start, @width)

    IF @start > 1 SET @out = N'...' + @out
    IF @start + @width <= @len SET @out = @out + N'...'
    RETURN @out
END

GO
