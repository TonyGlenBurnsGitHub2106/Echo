SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_MailTriageRule_List') IS NOT NULL DROP PROCEDURE dbo.stp_MailTriageRule_List
GO
CREATE PROCEDURE dbo.stp_MailTriageRule_List
    @prmDisposition VARCHAR(10)   = 'all',  -- all | white | black | junk
    @prmSearch      NVARCHAR(254) = NULL,
    @prmOffset      INT           = 0,
    @prmPageSize    INT           = 50
AS

/*

Mail Triage (Story 3561): one page of sender rules, alphabetical by sender, optionally only the
whitelist or the blacklist, optionally filtered by a fragment of the sender. The table holds one
row per sender Tony has ruled on (hundreds, not millions), so the LIKE filter is cheap.

TEST
----

    EXEC dbo.stp_MailTriageRule_List 'all', NULL, 0, 50

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------
2026-09-30  Felix        New (Story 3561, Mail Triage).
2026-10-26  Felix        Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail
                         source). Unchanged.

*/

SET NOCOUNT ON

DECLARE @disp VARCHAR(10)   = LOWER(ISNULL(@prmDisposition, 'all'))
DECLARE @q    NVARCHAR(254) = NULLIF(LOWER(LTRIM(RTRIM(@prmSearch))), N'')

SELECT r.MailTriageRuleID, r.SenderKey, r.KeyKind, r.Disposition, r.IsProtected, r.GmailFilterID,
       r.FilterMode, r.Note, r.CreatedDate, r.ModifiedDate
FROM   dbo.MailTriageRule r
WHERE  (@disp = 'all' OR r.Disposition = @disp)
  AND  (@q IS NULL OR r.SenderKey LIKE N'%' + @q + N'%')
ORDER BY r.SenderKey
OFFSET ISNULL(@prmOffset, 0) ROWS FETCH NEXT ISNULL(NULLIF(@prmPageSize, 0), 50) ROWS ONLY

GO
