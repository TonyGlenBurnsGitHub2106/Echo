SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.fn_GmailClientSearchTerm') IS NOT NULL DROP FUNCTION dbo.fn_GmailClientSearchTerm
GO
-- fn_GmailClientSearchTerm.sql
--
-- Gmail Client (Story 5608): the search box's words reach the procs as ONE parameter, one word or
-- quoted phrase per line (the service splits what Tony typed and takes the from: / to: / has: words
-- out first). This returns the n-th of them (1-based), trimmed, as a LIKE pattern ('%word%') -- or
-- NULL when there is no n-th, so "every word must match" is six NULL-or-LIKE tests in
-- fn_GmailClientMessage_Match / fn_GmailClientSent_Match. A plain one-line search is term 1, so a
-- caller that passes a single phrase behaves exactly as before.
--
-- Called on parameters, never per row.
--
-- Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail source).
-- Unchanged.

CREATE FUNCTION dbo.fn_GmailClientSearchTerm (@prmSearch NVARCHAR(1000), @prmN INT)
RETURNS NVARCHAR(210)
AS
BEGIN
    DECLARE @s NVARCHAR(1000) = REPLACE(ISNULL(@prmSearch, N''), NCHAR(13), N'')
    DECLARE @i INT = 1, @p INT, @t NVARCHAR(1000)

    WHILE @i < @prmN
    BEGIN
        SET @p = CHARINDEX(NCHAR(10), @s)
        IF @p = 0 RETURN NULL
        SET @s = SUBSTRING(@s, @p + 1, 1000)
        SET @i = @i + 1
    END

    SET @p = CHARINDEX(NCHAR(10), @s)
    SET @t = LTRIM(RTRIM(CASE WHEN @p > 0 THEN LEFT(@s, @p - 1) ELSE @s END))
    RETURN CASE WHEN @t = N'' THEN NULL ELSE N'%' + LEFT(@t, 200) + N'%' END
END

GO
