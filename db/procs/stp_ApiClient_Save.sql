SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.stp_ApiClient_Save') IS NOT NULL DROP PROCEDURE dbo.stp_ApiClient_Save
GO
CREATE PROCEDURE dbo.stp_ApiClient_Save
    @prmApiClientID         INT,
    @prmClientCode          VARCHAR(40),
    @prmClientName          NVARCHAR(200),
    @prmAllowedPathPrefixes NVARCHAR(1000),
    @prmClientNotes         NVARCHAR(1000),
    @prmIsEnabled           BIT
AS

/*

TEST
----

    EXEC stp_ApiClient_Save 0, 'sample', 'Sample client', '/api/solutions/', NULL, 1

VERSION HISTORY

Date        Developer    Description
----------  -----------  ------------------------------------------------------------------------------------------
09/17/2026  Felix        Story 3493: insert (id = 0) or update one API client. Never touches the key (stp_ApiClient_SetKey
                         does). Every allowed prefix must start with /api/ and end with / so a prefix can never
                         match more than the subtree it names; a blank allowlist is refused rather than read as
                         "everything". A new client starts with no key, so it can reach nothing until one is issued.
11/13/2026  Felix        Story 5653 (carrying Leonardo Stories 5764 / 5766): refuses the house's own names as a
                         client code, a code longer than 20 characters, and any allowed path inside the never-list.
11/13/2026  Felix        Story 5653 / Story 5765 (audit fence-5): the never-list gains /api/entities/ and
                         /api/web-addresses/ -- the record Web tab, where the logins and their passwords live.

*/

SET NOCOUNT ON

SET @prmClientCode = LOWER(LTRIM(RTRIM(ISNULL(@prmClientCode, ''))))
SET @prmClientName = LTRIM(RTRIM(ISNULL(@prmClientName, '')))
SET @prmAllowedPathPrefixes = LTRIM(RTRIM(ISNULL(@prmAllowedPathPrefixes, '')))
IF @prmClientCode = '' BEGIN RAISERROR('A client needs a code.', 16, 1) RETURN END
IF @prmClientCode LIKE '%[^a-z0-9-]%' BEGIN RAISERROR('A client code may hold only lower-case letters, digits and hyphens.', 16, 1) RETURN END
-- Story 5653 (carrying Story 5766 solutions-7): SolutionMember.MemberCode and every author / awaiting column is 20 wide.
IF LEN(@prmClientCode) > 20 BEGIN RAISERROR('A client code may be at most 20 characters. Nothing was saved.', 16, 1) RETURN END
-- Story 5653 (carrying Story 5764 fence-2): the house's own party names are never a client code.
-- ApiClientService refuses the same list.
IF @prmClientCode IN ('claude', 'stewart', 'felix', 'tony', 'service', 'timer', 'demo', 'house', 'admin', 'system') BEGIN
    RAISERROR('That code is one of the house''s own names. Choose another client code. Nothing was saved.', 16, 1) RETURN
END
IF @prmClientName = '' BEGIN RAISERROR('A client needs a name.', 16, 1) RETURN END
IF @prmAllowedPathPrefixes = '' BEGIN RAISERROR('A client needs at least one allowed path.', 16, 1) RETURN END
IF EXISTS (SELECT 1 FROM dbo.ApiClient WHERE ClientCode = @prmClientCode AND ApiClientID <> ISNULL(@prmApiClientID, 0)) BEGIN
    RAISERROR('There is already a client with that code.', 16, 1) RETURN
END

-- Walk the semicolon-separated allowlist and refuse any entry that is not /api/.../
DECLARE @rest NVARCHAR(1000) = @prmAllowedPathPrefixes + ';', @one NVARCHAR(1000), @pos INT
WHILE LEN(@rest) > 0 BEGIN
    SET @pos = CHARINDEX(';', @rest)
    SET @one = LTRIM(RTRIM(LEFT(@rest, @pos - 1)))
    SET @rest = SUBSTRING(@rest, @pos + 1, 1000)
    IF @one = '' CONTINUE
    IF LEFT(@one, 5) <> '/api/' OR RIGHT(@one, 1) <> '/' OR LEN(@one) < 7 OR @one LIKE '%..%' OR @one LIKE '%[*?]%' BEGIN
        RAISERROR('Every allowed path must look like /api/something/ -- starting with /api/ and ending with a slash.', 16, 1) RETURN
    END
    -- Story 5653 (carrying Story 5764 fence-3 / 5766 security-5): subtrees no client may ever hold,
    -- mirroring ApiClientPathFence.NEVER, each with its whole subtree.
    IF @one LIKE '/api/auth/%' OR @one LIKE '/api/system/%' OR @one LIKE '/api/api-clients/%'
       OR @one LIKE '/api/webhooks/%' OR @one LIKE '/api/step-up/%' OR @one LIKE '/api/version/%'
       -- Story 5653 / Story 5765 (audit fence-5): the logins (and their passwords) hang off these two.
       OR @one LIKE '/api/entities/%' OR @one LIKE '/api/web-addresses/%' BEGIN
        RAISERROR('An outside client may never be given %s -- that part of Echo is for the house only. Nothing was saved.', 16, 1, @one) RETURN
    END
END

IF @prmApiClientID > 0 BEGIN
    UPDATE dbo.ApiClient
    SET    ClientCode = @prmClientCode, ClientName = @prmClientName, AllowedPathPrefixes = @prmAllowedPathPrefixes,
           ClientNotes = NULLIF(LTRIM(RTRIM(ISNULL(@prmClientNotes, ''))), ''), IsEnabled = ISNULL(@prmIsEnabled, 1),
           ModifiedDate = GETDATE()
    WHERE  ApiClientID = @prmApiClientID
END ELSE BEGIN
    INSERT INTO dbo.ApiClient (ClientCode, ClientName, AllowedPathPrefixes, ClientNotes, IsEnabled)
    VALUES (@prmClientCode, @prmClientName, @prmAllowedPathPrefixes, NULLIF(LTRIM(RTRIM(ISNULL(@prmClientNotes, ''))), ''), ISNULL(@prmIsEnabled, 1))
    SET @prmApiClientID = SCOPE_IDENTITY()
END

SELECT @prmApiClientID AS ApiClientID

GO
