SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.fn_Search_Terms') IS NOT NULL DROP FUNCTION dbo.fn_Search_Terms
GO
CREATE FUNCTION dbo.fn_Search_Terms (@prmList NVARCHAR(2000))
RETURNS @terms TABLE (Seq INT NOT NULL, Term NVARCHAR(200) NOT NULL, Pattern NVARCHAR(420) NOT NULL)
AS
BEGIN
    DECLARE @s NVARCHAR(2000) = REPLACE(ISNULL(@prmList, N''), NCHAR(13), N'')
    DECLARE @seq INT = 0, @p INT, @t NVARCHAR(2000)

    WHILE LEN(@s) > 0
    BEGIN
        SET @p = CHARINDEX(NCHAR(10), @s)
        IF @p > 0
        BEGIN
            SET @t = LEFT(@s, @p - 1)
            SET @s = SUBSTRING(@s, @p + 1, 2000)
        END
        ELSE
        BEGIN
            SET @t = @s
            SET @s = N''
        END
        SET @t = LEFT(LTRIM(RTRIM(@t)), 200)
        IF @t <> N''
        BEGIN
            SET @seq = @seq + 1
            INSERT INTO @terms (Seq, Term, Pattern)
            VALUES (@seq, @t, N'%' + REPLACE(REPLACE(REPLACE(@t, N'[', N'[[]'), N'%', N'[%]'), N'_', N'[_]') + N'%')
        END
    END
    RETURN
END

GO
