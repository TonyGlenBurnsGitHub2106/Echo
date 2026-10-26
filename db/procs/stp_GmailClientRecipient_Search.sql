SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_GmailClientRecipient_Search') IS NOT NULL DROP PROCEDURE dbo.stp_GmailClientRecipient_Search
GO
CREATE PROCEDURE dbo.stp_GmailClientRecipient_Search
    @prmSearch NVARCHAR(200) = NULL,
    @prmTop    INT           = 12
AS

/*

Gmail Client (Story 5575): who to send to. The To / Cc / Bcc fields of the New Message dialog
ask this as Tony types. Two sources, merged and de-duplicated by address:

  - Leonardo's email addresses: tblWebAddress rows of type Email Address, named after the
    contact, vendor or user-defined entity they belong to (tblEntityWebAddressXRef).
  - People who have written to Tony: the From of mail the Gmail Client shows (whitelisted in
    Mail Triage, not hidden), ranked by how many messages they sent.

Returns Address, DisplayName, Source (leonardo | mail), Hits. A Leonardo address wins over the
same address seen in mail. Matches the start of the name or address first, then anywhere;
with nothing typed, the people who write most come first.
Search is LIKE over two small sets (a few hundred rows each), with OPTION (RECOMPILE).

TEST
----

    EXEC dbo.stp_GmailClientRecipient_Search N'ro', 12

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-10-02  Felix        New (Story 5575, Gmail Client New Message).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client. Echo has no Leonardo
                         address book (tblWebAddress, tblContact, tblVendor, UserDefinedEntity), so
                         that source is gone. New: the To addresses of sent mail
                         (GmailClientSentMessage.ToHeader) join the From of shown mail, both as Source
                         'mail'. Same parameters and result columns.

*/

SET NOCOUNT ON

-- Story 5654: @EMAIL_ADDRESS_TYPE is gone with Leonardo's address book (see below).
DECLARE @q    NVARCHAR(200) = NULLIF(LTRIM(RTRIM(@prmSearch)), N'')
DECLARE @any  NVARCHAR(202) = '%' + @q + '%'
DECLARE @lead NVARCHAR(201) = @q + '%'
DECLARE @top  INT = CASE WHEN ISNULL(@prmTop, 0) <= 0 THEN 12 WHEN @prmTop > 50 THEN 50 ELSE @prmTop END

DECLARE @r TABLE (Address NVARCHAR(320) NOT NULL, DisplayName NVARCHAR(320) NULL, Source VARCHAR(10) NOT NULL, Hits INT NOT NULL)

-- Story 5654: Leonardo's first source -- its own email addresses (tblWebAddress named after the
-- tblContact / tblVendor / UserDefinedEntity they belong to) -- does not exist in Echo, so it is gone.

-- Story 5654: each address in the To of sent mail, one row per address. Outlook separates them with
-- semicolons, Gmail-style headers with commas; both are split. A piece with no usable address
-- (an Exchange name with no SMTP address) is dropped by the LIKE below.
;WITH sentTo AS (
    SELECT CAST(NULL AS NVARCHAR(1001)) AS Piece,
           CAST(REPLACE(s.ToHeader, N';', N',') + N',' AS NVARCHAR(1001)) AS Rest
    FROM   dbo.GmailClientSentMessage s
    WHERE  s.IsHidden = 0
      AND  s.ToHeader IS NOT NULL
    UNION ALL
    SELECT CAST(LTRIM(RTRIM(LEFT(t.Rest, CHARINDEX(N',', t.Rest) - 1))) AS NVARCHAR(1001)),
           CAST(SUBSTRING(t.Rest, CHARINDEX(N',', t.Rest) + 1, 1001) AS NVARCHAR(1001))
    FROM   sentTo t
    WHERE  CHARINDEX(N',', t.Rest) > 0
)
-- People who have written ("Name <address>" or a bare address), and -- Story 5654 -- people Tony
-- has written to, counted together as Source 'mail'.
INSERT INTO @r (Address, DisplayName, Source, Hits)
SELECT a.Address, MAX(a.DisplayName), 'mail', COUNT(*)
FROM (
    SELECT LOWER(LTRIM(RTRIM(CASE WHEN CHARINDEX(N'<', m.FromHeader) > 0 AND CHARINDEX(N'>', m.FromHeader) > CHARINDEX(N'<', m.FromHeader)
                                  THEN SUBSTRING(m.FromHeader, CHARINDEX(N'<', m.FromHeader) + 1,
                                                 CHARINDEX(N'>', m.FromHeader) - CHARINDEX(N'<', m.FromHeader) - 1)
                                  ELSE m.FromHeader END))) AS Address,
           NULLIF(LTRIM(RTRIM(REPLACE(CASE WHEN CHARINDEX(N'<', m.FromHeader) > 1
                                           THEN LEFT(m.FromHeader, CHARINDEX(N'<', m.FromHeader) - 1) END, N'"', N''))), N'') AS DisplayName
    FROM   dbo.GmailClientMessage m
    WHERE  m.IsHidden = 0
      AND  m.FromHeader IS NOT NULL
      AND  EXISTS (SELECT 1 FROM dbo.MailTriageRule t WHERE t.SenderKey = m.SenderKey AND t.Disposition = 'white')
    UNION ALL
    -- Story 5654: the To of sent mail, parsed the same way as a From.
    SELECT LOWER(LTRIM(RTRIM(CASE WHEN CHARINDEX(N'<', p.Piece) > 0 AND CHARINDEX(N'>', p.Piece) > CHARINDEX(N'<', p.Piece)
                                  THEN SUBSTRING(p.Piece, CHARINDEX(N'<', p.Piece) + 1,
                                                 CHARINDEX(N'>', p.Piece) - CHARINDEX(N'<', p.Piece) - 1)
                                  ELSE p.Piece END))),
           LEFT(NULLIF(LTRIM(RTRIM(REPLACE(CASE WHEN CHARINDEX(N'<', p.Piece) > 1
                                                THEN LEFT(p.Piece, CHARINDEX(N'<', p.Piece) - 1) END, N'"', N''))), N''), 320)
    FROM   sentTo p
    WHERE  p.Piece IS NOT NULL AND p.Piece <> N''
) a
WHERE a.Address LIKE '%_@_%._%'
  AND LEN(a.Address) <= 320
GROUP BY a.Address
OPTION (MAXRECURSION 1000)   -- Story 5654: a To header of up to 1000 characters splits into at most ~500 pieces

SELECT TOP (@top) r.Address, r.DisplayName, r.Source, r.Hits
FROM (
    SELECT Address, DisplayName, Source, Hits,
           ROW_NUMBER() OVER (PARTITION BY Address ORDER BY CASE Source WHEN 'leonardo' THEN 0 ELSE 1 END, Hits DESC) AS Pick
    FROM   @r
    WHERE  @q IS NULL OR Address LIKE @any OR DisplayName LIKE @any
) r
WHERE r.Pick = 1
ORDER BY CASE WHEN @q IS NULL THEN 0 WHEN r.DisplayName LIKE @lead OR r.Address LIKE @lead THEN 0 ELSE 1 END,
         CASE WHEN @q IS NULL THEN 0 WHEN r.Source = 'leonardo' THEN 0 ELSE 1 END,   -- nothing typed: who writes most
         r.Hits DESC, CASE WHEN r.DisplayName IS NULL THEN 1 ELSE 0 END, r.DisplayName, r.Address
OPTION (RECOMPILE)

GO
