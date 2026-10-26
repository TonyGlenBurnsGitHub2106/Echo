SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.fn_IsoDate_IsValid') IS NOT NULL DROP FUNCTION dbo.fn_IsoDate_IsValid
GO
/*

Function fn_IsoDate_IsValid.

Returns 1 when @prmDate is NULL, empty, or a real yyyy-mm-dd date with a year from 1900
through 2099; 0 otherwise. A save proc calls it for every date parameter BEFORE it
writes anything, and refuses the whole save on a 0.

Why: CONVERT(DATETIME, '0202-09-30', 120) raises error 242, which ends only that one
statement -- the proc carries on, inserts the row with the date blanked, and THEN hands
the error back. The page reports "Could not save", the user presses Save again, and
every press adds another row (Debt Resolution, 2026-09-30: four Quicksilver rows).

The database runs at compatibility level 90, so TRY_CONVERT is not available.
ISDATE is fed the separator-free yyyymmdd form, which SQL Server reads the same way
under every DATEFORMAT / language setting.

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-30  Felix Vance  New (double-posting fix on the Debt Resolution page).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (the Gmail Client
                         procs validate dates with it). Unchanged.

*/

CREATE FUNCTION dbo.fn_IsoDate_IsValid (@prmDate VARCHAR(30))
RETURNS BIT
AS
BEGIN
    IF @prmDate IS NULL OR LTRIM(RTRIM(@prmDate)) = ''
        RETURN 1

    IF LEN(@prmDate) <> 10 OR @prmDate NOT LIKE '[12][0-9][0-9][0-9]-[01][0-9]-[0-3][0-9]'
        RETURN 0

    IF LEFT(@prmDate, 4) < '1900' OR LEFT(@prmDate, 4) > '2099'
        RETURN 0

    IF ISDATE(REPLACE(@prmDate, '-', '')) = 0
        RETURN 0

    RETURN 1
END

GO
