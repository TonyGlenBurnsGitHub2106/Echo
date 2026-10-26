<#
.SYNOPSIS
    Echo's bridge to the classic Outlook app on this computer (Story 5654, the Email Client).

.DESCRIPTION
    Echo starts this script once and keeps it running. Echo writes one request per line on its
    standard input, as JSON ({"op":"list", ...}); the script answers each with one line of JSON on
    its standard output: {"ok":true,"result":...} or {"ok":false,"status":404,"error":"..."}.

    It ATTACHES to the Outlook that is already running (it never starts Outlook, so it never shows
    a profile prompt) and reads through Outlook's own object model -- no password, token or app
    registration is kept anywhere. Outlook signs in; Echo only asks it.

    What it does in Outlook: reads the Inbox, Sent Items and Drafts; sends mail Tony wrote in Echo;
    keeps Echo's drafts in the Drafts folder. What it NEVER does: create, change or delete a rule,
    or move, flag, categorize or delete a received message. (Deleting a DRAFT Echo is done with
    moves it to Deleted Items, as Outlook's own Discard does.)

    Status codes in an answer: 404 = Outlook no longer has that item, 409 = Outlook is not
    running, 400 = a request Echo should not have sent, 500 = anything else Outlook said.

    Runs under Windows PowerShell 5.1. ASCII only.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding $false
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$stdin = New-Object System.IO.StreamReader ([Console]::OpenStandardInput()), $utf8
$stdout = New-Object System.IO.StreamWriter ([Console]::OpenStandardOutput()), $utf8
$stdout.AutoFlush = $true

Add-Type -AssemblyName System.Web.Extensions
$json = New-Object System.Web.Script.Serialization.JavaScriptSerializer
$json.MaxJsonLength = [int]::MaxValue

# MAPI properties read through PropertyAccessor.
$PR_SMTP_ADDRESS        = 'http://schemas.microsoft.com/mapi/proptag/0x39FE001F'
$PR_SENDER_SMTP_ADDRESS = 'http://schemas.microsoft.com/mapi/proptag/0x5D01001F'
$PR_INTERNET_MESSAGE_ID = 'http://schemas.microsoft.com/mapi/proptag/0x1035001F'
$PR_ATTACHMENT_HIDDEN   = 'http://schemas.microsoft.com/mapi/proptag/0x7FFE000B'
$PR_ATTACH_DATA_BIN     = 'http://schemas.microsoft.com/mapi/proptag/0x37010102'
# The key Echo stamps on mail it sends, so Check mail can find Outlook's copy in Sent Items.
$ECHO_SEND_KEY = 'http://schemas.microsoft.com/mapi/string/{00020329-0000-0000-C000-000000000046}/EchoSendKey'

$OL_INBOX = 6; $OL_SENT = 5; $OL_DRAFTS = 16; $OL_MAIL_ITEM = 0
$OL_FORMAT_PLAIN = 1
# olMail, and the meeting request / response / cancellation classes (they sit in the Inbox too).
$MAIL_CLASSES = @(43, 53, 54, 55, 56, 57)

$script:ol = $null
$script:ns = $null
$script:smtpCache = @{}

function Fail([int]$status, [string]$message) { throw ("ECHO{0}|{1}" -f $status, $message) }

function Connect-Outlook {
    if ($script:ol) {
        try { $null = $script:ns.CurrentUser; return } catch { $script:ol = $null; $script:ns = $null }
    }
    try { $script:ol = [Runtime.InteropServices.Marshal]::GetActiveObject('Outlook.Application') }
    catch {
        $id = [Security.Principal.WindowsIdentity]::GetCurrent()
        if ((New-Object Security.Principal.WindowsPrincipal $id).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
            Fail 409 'Echo is running as administrator, so it cannot see Outlook. Run stop-echo.cmd, then start-echo.cmd (not as administrator).'
        }
        Fail 409 'Outlook is not running on this computer. Open Outlook, then try again.'
    }
    $script:ns = $script:ol.GetNamespace('MAPI')
}

function Get-OutlookItem([string]$id) {
    if (-not $id -or $id.StartsWith('echo:')) { Fail 404 'Outlook does not have that message.' }
    try { return $script:ns.GetItemFromID($id) }
    catch { Fail 404 'Outlook no longer has that message.' }
}

function Get-Str($v) { if ($null -eq $v) { return $null } return [string]$v }

# Only an item that is still an unsent message in the Drafts folder is ever changed, sent as a draft
# or deleted -- never received mail, never something already sent (on some stores a sent item keeps
# its EntryID). Anything else answers $null, and the caller treats it as gone.
function Get-UnsentDraft([string]$id) {
    if (-not $id -or $id.StartsWith('echo:')) { return $null }
    $m = $null
    try { $m = $script:ns.GetItemFromID($id) } catch { return $null }
    try {
        if ([int]$m.Class -ne 43 -or $m.Sent) { return $null }
        $drafts = $script:ns.GetDefaultFolder($OL_DRAFTS)
        if ([string]$m.Parent.EntryID -ne [string]$drafts.EntryID) { return $null }
    } catch { return $null }
    return $m
}

function Format-Address([string]$name, [string]$addr) {
    if (-not $addr) { return $name }
    if ($name -and $name -ne $addr) { return ('"' + ($name -replace '"', '') + '" <' + $addr + '>') }
    return $addr
}

function Get-RecipientSmtp($r) {
    $raw = Get-Str $r.Address
    if ($raw -and $script:smtpCache.ContainsKey($raw)) { return $script:smtpCache[$raw] }
    $smtp = $null
    try { $smtp = Get-Str $r.PropertyAccessor.GetProperty($PR_SMTP_ADDRESS) } catch { }
    if (-not $smtp) {
        try {
            $ae = $r.AddressEntry
            if ($ae -and $ae.Type -eq 'EX') { $u = $ae.GetExchangeUser(); if ($u) { $smtp = Get-Str $u.PrimarySmtpAddress } }
        } catch { }
    }
    if (-not $smtp) { $smtp = $raw }
    if ($raw) { $script:smtpCache[$raw] = $smtp }
    return $smtp
}

function Get-SenderSmtp($item) {
    try { if ($item.SenderEmailType -ne 'EX') { return Get-Str $item.SenderEmailAddress } } catch { }
    try { $v = Get-Str $item.PropertyAccessor.GetProperty($PR_SENDER_SMTP_ADDRESS); if ($v) { return $v } } catch { }
    try { $u = $item.Sender.GetExchangeUser(); if ($u) { return Get-Str $u.PrimarySmtpAddress } } catch { }
    try { return Get-Str $item.SenderEmailAddress } catch { return $null }
}

# Recipients of one type (1 To, 2 Cc, 3 Bcc) as "Name" <address>, comma separated.
function Get-Recipients($item, [int]$type) {
    $out = New-Object System.Collections.Generic.List[string]
    try {
        foreach ($r in $item.Recipients) {
            if ($r.Type -eq $type) { $out.Add((Format-Address (Get-Str $r.Name) (Get-RecipientSmtp $r))) }
            Release $r
        }
    } catch { }
    return ($out -join ', ')
}

function Get-ReplyTo($item) {
    $out = New-Object System.Collections.Generic.List[string]
    try { foreach ($r in $item.ReplyRecipients) { $out.Add((Format-Address (Get-Str $r.Name) (Get-RecipientSmtp $r))) } } catch { }
    return ($out -join ', ')
}

function Get-Prop($item, [string]$tag) {
    try { return Get-Str $item.PropertyAccessor.GetProperty($tag) } catch { return $null }
}

function Get-When($item, [bool]$sent) {
    $d = $null
    try { if ($sent) { $d = $item.SentOn } else { $d = $item.ReceivedTime } } catch { }
    if ($null -eq $d -or $d.Year -gt 4000) { try { $d = $item.CreationTime } catch { $d = Get-Date } }
    return $d.ToString('yyyy-MM-ddTHH:mm:ss', [Globalization.CultureInfo]::InvariantCulture)
}

function Get-Meta($item, [bool]$sent) {
    $text = ''
    try { $text = Get-Str $item.Body } catch { }
    $snip = (($text -replace '\s+', ' ')).Trim()
    if ($snip.Length -gt 300) { $snip = $snip.Substring(0, 300) }
    $senderName = $null
    try { $senderName = Get-Str $item.SenderName } catch { }
    return @{
        id        = Get-Str $item.EntryID
        threadId  = (Get-Str $item.ConversationID)
        from      = (Format-Address $senderName (Get-SenderSmtp $item))
        to        = (Get-Recipients $item 1)
        cc        = (Get-Recipients $item 2)
        replyTo   = (Get-ReplyTo $item)
        subject   = Get-Str $item.Subject
        snippet   = $snip
        messageId = (Get-Prop $item $PR_INTERNET_MESSAGE_ID)
        received  = (Get-When $item $sent)
        sendKey   = (Get-Prop $item $ECHO_SEND_KEY)
    }
}

function Release($o) { if ($null -ne $o) { try { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($o) } catch { } } }

function Invoke-List($req) {
    $sent = ($req['folder'] -eq 'sent')
    $folder = $script:ns.GetDefaultFolder($(if ($sent) { $OL_SENT } else { $OL_INBOX }))
    $after = [datetime]::ParseExact([string]$req['after'], 'yyyy-MM-ddTHH:mm:ss', [Globalization.CultureInfo]::InvariantCulture)
    $max = [int]$req['max']; if ($max -le 0) { $max = 5000 }
    $field = $(if ($sent) { '[SentOn]' } else { '[ReceivedTime]' })
    # Restrict wants the date in this computer's own short format, without seconds. Echo asks one
    # slice of days at a time (after .. before), so no one call holds Outlook for long.
    $filter = $field + " >= '" + $after.ToString('g') + "'"
    if ($req['before']) {
        $before = [datetime]::ParseExact([string]$req['before'], 'yyyy-MM-ddTHH:mm:ss', [Globalization.CultureInfo]::InvariantCulture)
        $filter += ' AND ' + $field + " < '" + $before.ToString('g') + "'"
    }
    $items = $folder.Items.Restrict($filter)
    $items.Sort($field, $true)
    $out = New-Object System.Collections.ArrayList
    $it = $items.GetFirst()
    while ($null -ne $it -and $out.Count -lt $max) {
        try {
            if ($MAIL_CLASSES -contains [int]$it.Class) { [void]$out.Add((Get-Meta $it $sent)) }
        } catch { }
        Release $it
        $it = $items.GetNext()
    }
    return ,$out
}

# The attachments a reader sees: hidden (inline picture) ones are left out. Body and attachment
# downloads both use this list, so an index means the same file in both.
function Get-VisibleAttachments($item) {
    $out = New-Object System.Collections.ArrayList
    foreach ($a in $item.Attachments) {
        $hidden = $false
        try { $hidden = [bool]$a.PropertyAccessor.GetProperty($PR_ATTACHMENT_HIDDEN) } catch { }
        if (-not $hidden) { [void]$out.Add($a) }
    }
    return ,$out
}

function Invoke-Body($req) {
    $item = Get-OutlookItem $req['id']
    $html = $null
    try { if ([int]$item.BodyFormat -ne $OL_FORMAT_PLAIN) { $html = Get-Str $item.HTMLBody } } catch { }
    $text = $null
    try { $text = Get-Str $item.Body } catch { }
    $names = New-Object System.Collections.ArrayList
    foreach ($a in (Get-VisibleAttachments $item)) { [void]$names.Add((Get-Str $a.FileName)) }
    return @{ text = $text; html = $html; attachments = $names }
}

function Invoke-Attachment($req) {
    $item = Get-OutlookItem $req['id']
    $list = Get-VisibleAttachments $item
    $i = [int]$req['index']
    if ($i -lt 0 -or $i -ge $list.Count) { Fail 404 'That message has no such attachment.' }
    $a = $list[$i]
    $name = Get-Str $a.FileName
    $bytes = $null
    try { $bytes = [byte[]]$a.PropertyAccessor.GetProperty($PR_ATTACH_DATA_BIN) } catch { $bytes = $null }
    if ($null -eq $bytes) {
        # A big file is not handed over as one property; Outlook writes it to Echo's own scratch folder.
        $dir = [string]$req['dir']
        if (-not $dir) { Fail 400 'No folder to read the attachment through.' }
        [void](New-Item -ItemType Directory -Force $dir)
        $path = Join-Path $dir ([guid]::NewGuid().ToString('N'))
        $a.SaveAsFile($path)
        $bytes = [IO.File]::ReadAllBytes($path)
        [IO.File]::Delete($path)   # Echo's own scratch copy, made a moment ago
    }
    return @{ name = $name; data = [Convert]::ToBase64String($bytes) }
}

function Set-Recipients($m, $req) {
    while ($m.Recipients.Count -gt 0) { $m.Recipients.Remove(1) }
    foreach ($pair in @(@('to', 1), @('cc', 2), @('bcc', 3))) {
        $list = $req[$pair[0]]
        if ($null -eq $list) { continue }
        foreach ($addr in $list) {
            if (-not $addr) { continue }
            $r = $m.Recipients.Add([string]$addr)
            $r.Type = $pair[1]
        }
    }
    [void]$m.Recipients.ResolveAll()
}

function Set-MailContent($m, $req) {
    Set-Recipients $m $req
    $m.Subject = [string]$req['subject']
    $text = [string]$req['text']
    # A draft written in Outlook keeps its own formatting (and signature, pictures) unless its
    # words were changed in Echo; then it becomes the plain text Echo has.
    $same = $false
    try { $same = ((Get-Str $m.Body) -replace '\s+', ' ').Trim() -eq ($text -replace '\s+', ' ').Trim() } catch { }
    if ($same -and [int]$m.BodyFormat -ne $OL_FORMAT_PLAIN) { return }
    $m.BodyFormat = $OL_FORMAT_PLAIN
    $m.Body = $text
}

# Reply / Forward bring the original's hidden inline pictures along; a plain-text message would send
# them as ordinary attachments, so they come off.
function Remove-HiddenAttachments($m) {
    for ($i = $m.Attachments.Count; $i -ge 1; $i--) {
        $hidden = $false
        try { $hidden = [bool]$m.Attachments.Item($i).PropertyAccessor.GetProperty($PR_ATTACHMENT_HIDDEN) } catch { }
        if ($hidden) { $m.Attachments.Remove($i) }
    }
}

# A new item to send or keep as a draft, started from the message it answers when Outlook has it.
function New-OutgoingItem($req) {
    $kind = [string]$req['baseKind']
    $base = $null
    $id = [string]$req['baseId']
    if ($kind -ne 'draft' -and $id -and -not $id.StartsWith('echo:')) { try { $base = $script:ns.GetItemFromID($id) } catch { $base = $null } }
    $m = $null
    if ($kind -eq 'draft') {
        $m = Get-UnsentDraft $id   # only an unsent draft in Drafts; anything else goes as new
    } elseif ($null -ne $base) {
        switch ($kind) {
            'reply'    { $m = $base.Reply(); Remove-HiddenAttachments $m }
            'replyAll' { $m = $base.ReplyAll(); Remove-HiddenAttachments $m }
            'forward'  {
                $m = $base.Forward()
                if (-not $req['keepOriginalAttachments']) { while ($m.Attachments.Count -gt 0) { $m.Attachments.Remove(1) } }
                else { Remove-HiddenAttachments $m }
            }
        }
    }
    if ($null -eq $m) { $m = $script:ol.CreateItem($OL_MAIL_ITEM) }
    return $m
}

function Get-DraftVersion($m) {
    $s = (Get-Str $m.To) + "`n" + (Get-Str $m.CC) + "`n" + (Get-Str $m.BCC) + "`n" + (Get-Str $m.Subject) + "`n" + (Get-Str $m.Body)
    $sha = [System.Security.Cryptography.SHA1]::Create()
    $h = $sha.ComputeHash($utf8.GetBytes($s))
    return (($h | ForEach-Object { $_.ToString('x2') }) -join '')
}

function Invoke-Send($req) {
    $m = New-OutgoingItem $req
    Set-MailContent $m $req
    if ($req['sendKey']) { $m.PropertyAccessor.SetProperty($ECHO_SEND_KEY, [string]$req['sendKey']) }
    foreach ($p in @($req['files'])) { if ($p) { [void]$m.Attachments.Add([string]$p) } }
    $thread = $null
    try { $thread = Get-Str $m.ConversationID } catch { }
    $m.Send()
    return @{ sendKey = $req['sendKey']; threadId = $thread }
}

function Invoke-SaveDraft($req) {
    $did = [string]$req['draftId']
    if ($did) {
        $m = Get-UnsentDraft $did
        if ($null -eq $m) { Fail 404 'Outlook no longer has that draft.' }
    } else { $m = New-OutgoingItem $req }
    Set-MailContent $m $req
    $m.Save()
    return @{ draftId = (Get-Str $m.EntryID); version = (Get-DraftVersion $m); threadId = (Get-Str $m.ConversationID) }
}

function Invoke-DeleteDraft($req) {
    $did = [string]$req['draftId']
    if (-not $did) { return @{ deleted = $false } }
    $m = Get-UnsentDraft $did   # never anything but an unsent draft in Drafts
    if ($null -eq $m) { return @{ deleted = $false } }
    $m.Delete()
    return @{ deleted = $true }
}

function Invoke-ListDrafts {
    $folder = $script:ns.GetDefaultFolder($OL_DRAFTS)
    $out = New-Object System.Collections.ArrayList
    foreach ($m in $folder.Items) {
        try {
            if ([int]$m.Class -eq 43) {
                [void]$out.Add(@{ draftId = (Get-Str $m.EntryID); version = (Get-DraftVersion $m); threadId = (Get-Str $m.ConversationID) })
            }
        } catch { }
    }
    return ,$out
}

function Invoke-Draft($req) {
    $m = Get-UnsentDraft ([string]$req['draftId'])
    if ($null -eq $m) { Fail 404 'Outlook no longer has that draft.' }
    $html = $null
    try { if ([int]$m.BodyFormat -ne $OL_FORMAT_PLAIN) { $html = Get-Str $m.HTMLBody } } catch { }
    return @{
        draftId  = (Get-Str $m.EntryID)
        version  = (Get-DraftVersion $m)
        threadId = (Get-Str $m.ConversationID)
        to       = (Get-Recipients $m 1)
        cc       = (Get-Recipients $m 2)
        bcc      = (Get-Recipients $m 3)
        subject  = (Get-Str $m.Subject)
        text     = (Get-Str $m.Body)
        html     = $html
        updated  = $m.LastModificationTime.ToString('yyyy-MM-ddTHH:mm:ss', [Globalization.CultureInfo]::InvariantCulture)
    }
}

function Invoke-Account {
    $addr = $null
    try {
        $ae = $script:ns.CurrentUser.AddressEntry
        if ($ae.Type -eq 'EX') { $u = $ae.GetExchangeUser(); if ($u) { $addr = Get-Str $u.PrimarySmtpAddress } }
        if (-not $addr) { $addr = Get-Str $ae.Address }
    } catch { }
    if (-not $addr) { try { $addr = Get-Str $script:ns.Accounts.Item(1).SmtpAddress } catch { } }
    if (-not $addr) { Fail 409 'Outlook is running but has no mailbox signed in.' }
    return @{ account = $addr }
}

# What PowerShell functions return comes wrapped in PSObject, and a JSON serializer handed such a
# value walks the wrapper's own members (and loops forever on them). So the answer is written here,
# walking it by hand; only plain .NET strings ever reach the serializer, for their escaping.
function ConvertTo-JsonText($o) {
    if ($null -eq $o) { return 'null' }
    if ($o -is [System.Management.Automation.PSObject]) { $o = $o.psobject.BaseObject }
    if ($o -is [string]) { return $json.Serialize([string]$o) }
    if ($o -is [bool]) { if ($o) { return 'true' } else { return 'false' } }
    if ($o -is [int] -or $o -is [long] -or $o -is [double] -or $o -is [decimal]) {
        return ([IFormattable]$o).ToString($null, [Globalization.CultureInfo]::InvariantCulture)
    }
    if ($o -is [System.Collections.IDictionary]) {
        $parts = New-Object System.Collections.Generic.List[string]
        foreach ($k in @($o.Keys)) { $parts.Add($json.Serialize([string]$k) + ':' + (ConvertTo-JsonText $o[$k])) }
        return '{' + ($parts -join ',') + '}'
    }
    if ($o -is [System.Collections.IEnumerable]) {
        $parts = New-Object System.Collections.Generic.List[string]
        foreach ($x in $o) { $parts.Add((ConvertTo-JsonText $x)) }
        return '[' + ($parts -join ',') + ']'
    }
    return $json.Serialize([string]$o)
}

function Invoke-Op($req) {
    Connect-Outlook
    switch ([string]$req['op']) {
        'account'     { return Invoke-Account }
        'list'        { return ,(Invoke-List $req) }   # the comma keeps a list of one (or none) a list
        'meta'        { return (Get-Meta (Get-OutlookItem $req['id']) ([bool]$req['sent'])) }
        'body'        { return Invoke-Body $req }
        'attachment'  { return Invoke-Attachment $req }
        'send'        { return Invoke-Send $req }
        'saveDraft'   { return Invoke-SaveDraft $req }
        'deleteDraft' { return Invoke-DeleteDraft $req }
        'listDrafts'  { return ,(Invoke-ListDrafts) }
        'draft'       { return Invoke-Draft $req }
        default       { Fail 400 ('Unknown request: ' + [string]$req['op']) }
    }
}

while ($true) {
    $line = $stdin.ReadLine()
    if ($null -eq $line) { break }
    if (-not $line.Trim()) { continue }
    $answer = $null
    try {
        $req = $json.DeserializeObject($line)
        $result = Invoke-Op $req
        $answer = @{ ok = $true; result = $result }
    } catch {
        $msg = [string]$_.Exception.Message
        $status = 500
        if ($msg -match '^ECHO(\d{3})\|(.*)$') { $status = [int]$Matches[1]; $msg = $Matches[2] }
        elseif ($_.Exception.HResult -eq -2147221233) { $status = 404; $msg = 'Outlook no longer has that item.' }
        $answer = @{ ok = $false; status = $status; error = $msg }
    }
    $stdout.WriteLine((ConvertTo-JsonText $answer))
}
