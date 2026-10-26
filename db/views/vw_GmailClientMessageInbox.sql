SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.vw_GmailClientMessageInbox') IS NOT NULL DROP VIEW dbo.vw_GmailClientMessageInbox
GO
-- vw_GmailClientMessageInbox.sql
--
-- Gmail Client (Story 5608) -- the one definition of "which inbox is this message in?".
--
-- Inboxes file mail by SENDER KEY: a domain (example.com) for most mail, the full address for
-- free mail (gmail.com and the like) -- the key Mail Triage uses. Story 5608 adds sender-level
-- overrides: "Default to domain level, but allow sender level overrides. If we have sender level,
-- we use it, else domain level." An override is a GmailClientSenderInbox row keyed by one full
-- address (greg@example.com) while the message's own key is its domain. It wins whenever it
-- exists -- even with no inbox, which files that one sender in Unclassified while the rest of
-- the domain stays in its inbox.
--
-- One row per message:
--   SenderAddress             the address in the From header, lower case ("Pat <p@x.com>" -> p@x.com)
--   GmailClientSenderInboxID  the filing row that decided it (the override, else the key's row), or NULL
--   GmailClientInboxID        the inbox it shows in; NULL = Unclassified
--   IsSenderLevel             1 when a sender-level override decided it
--   DomainInboxID             the inbox the sender's key (its domain) is filed in, override or not
--
-- Read-only. Used by fn_GmailClientMessage_Match, stp_GmailClientMessage_Get and
-- stp_GmailClientSenderInbox_ListForInbox.
--
-- Story 5654: echoed from Leonardo for Echo's Email Client (Outlook as the mail source).
-- Unchanged.

CREATE VIEW dbo.vw_GmailClientMessageInbox
AS
SELECT  m.GmailClientMessageID,
        a.SenderAddress,
        CASE WHEN sa.GmailClientSenderInboxID IS NOT NULL THEN sa.GmailClientSenderInboxID ELSE sd.GmailClientSenderInboxID END
            AS GmailClientSenderInboxID,
        CASE WHEN sa.GmailClientSenderInboxID IS NOT NULL THEN sa.GmailClientInboxID ELSE sd.GmailClientInboxID END
            AS GmailClientInboxID,
        CAST(CASE WHEN sa.GmailClientSenderInboxID IS NOT NULL THEN 1 ELSE 0 END AS BIT) AS IsSenderLevel,
        sd.GmailClientInboxID AS DomainInboxID
FROM    dbo.GmailClientMessage m
        CROSS APPLY (SELECT LOWER(LTRIM(RTRIM(
                         CASE WHEN CHARINDEX(N'<', m.FromHeader) > 0
                               AND CHARINDEX(N'>', m.FromHeader, CHARINDEX(N'<', m.FromHeader)) > CHARINDEX(N'<', m.FromHeader) + 1
                              THEN SUBSTRING(m.FromHeader, CHARINDEX(N'<', m.FromHeader) + 1,
                                             CHARINDEX(N'>', m.FromHeader, CHARINDEX(N'<', m.FromHeader)) - CHARINDEX(N'<', m.FromHeader) - 1)
                              ELSE m.FromHeader END))) AS SenderAddress) a
        -- The override: a row for the full address, when the message is filed by its domain.
        LEFT JOIN dbo.GmailClientSenderInbox sa
               ON sa.SenderKey = a.SenderAddress
              AND a.SenderAddress <> ISNULL(m.SenderKey, N'')
        LEFT JOIN dbo.GmailClientSenderInbox sd ON sd.SenderKey = m.SenderKey;

GO
