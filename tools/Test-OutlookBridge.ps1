<#
.SYNOPSIS
    Tests scripts\OutlookBridge.ps1 against a pretend Outlook (Story 5654).

.DESCRIPTION
    Run it after any change to the bridge: powershell -ExecutionPolicy Bypass -File tools\Test-OutlookBridge.ps1
    No Outlook is needed (or touched). Exit 0 = every check passed.
#>
# Story 5654: exercises scripts\OutlookBridge.ps1's functions against a pretend Outlook object model
# (PowerShell classes standing in for COM), since this computer has no Outlook profile. Loads the
# bridge's functions without its read loop, swaps Connect-Outlook for the fakes, and runs requests
# through Invoke-Op exactly as the loop would, serializing each answer the way the loop does.
$ErrorActionPreference = 'Stop'
$src = [IO.File]::ReadAllText((Join-Path (Split-Path -Parent $PSScriptRoot) 'scripts\OutlookBridge.ps1'))
$cut = $src.IndexOf('while ($true) {')
$body = $src.Substring(0, $cut)
$body = $body -replace '(?s)\[CmdletBinding\(\)\]\s*param\(\)', ''
# Leave the console streams alone in the harness.
$body = $body -replace '(?m)^\[Console\]::(Input|Output)Encoding = \$utf8\s*$', ''
$body = $body -replace '(?m)^\$stdin = .*$', '' -replace '(?m)^\$stdout = .*$', '' -replace '(?m)^\$stdout\.AutoFlush = .*$', ''

class FakePA {
    [hashtable]$Props = @{}
    [object] GetProperty([string]$tag) {
        if ($this.Props.ContainsKey($tag)) { return $this.Props[$tag] }
        throw "no property $tag"
    }
    [void] SetProperty([string]$tag, [object]$v) { $this.Props[$tag] = $v }
}
class FakeEntry {
    [string]$Type = 'EX'
    [string]$Address = '/o=x/cn=me'
    [object] GetExchangeUser() { return [pscustomobject]@{ PrimarySmtpAddress = 'me@work.example' } }
}
class FakeRecipient {
    [string]$Name
    [string]$Address
    [int]$Type = 1
    [FakePA]$PropertyAccessor = [FakePA]::new()
    [object]$AddressEntry = $null
    FakeRecipient([string]$n, [string]$a, [int]$t, [string]$smtp) {
        $this.Name = $n
        $this.Address = $a
        $this.Type = $t
        if ($smtp) { $this.PropertyAccessor.Props['http://schemas.microsoft.com/mapi/proptag/0x39FE001F'] = $smtp }
    }
}
class FakeList : System.Collections.IEnumerable {
    [System.Collections.ArrayList]$L = [System.Collections.ArrayList]::new()
    [int]$Count = 0
    [System.Collections.IEnumerator] GetEnumerator() { return $this.L.GetEnumerator() }
    [object] Item([int]$i) { return $this.L[$i - 1] }
    [void] Put([object]$x) {
        [void]$this.L.Add($x)
        $this.Count = $this.L.Count
    }
    [void] Remove([int]$i) {
        $this.L.RemoveAt($i - 1)
        $this.Count = $this.L.Count
    }
}
class FakeRecipients : FakeList {
    [object] Add([string]$a) {
        $r = [FakeRecipient]::new($a, $a, 1, $a)
        $this.Put($r)
        return $r
    }
    [bool] ResolveAll() { return $true }
}
class FakeAttachment {
    [string]$FileName
    [FakePA]$PropertyAccessor = [FakePA]::new()
    FakeAttachment([string]$n, [byte[]]$b, [bool]$hidden) {
        $this.FileName = $n
        $this.PropertyAccessor.Props['http://schemas.microsoft.com/mapi/proptag/0x37010102'] = $b
        if ($hidden) { $this.PropertyAccessor.Props['http://schemas.microsoft.com/mapi/proptag/0x7FFE000B'] = $true }
    }
    [void] SaveAsFile([string]$p) { throw 'not used' }
}
class FakeAttachments : FakeList {
    [object] Add([string]$path) {
        $a = [FakeAttachment]::new([IO.Path]::GetFileName($path), [IO.File]::ReadAllBytes($path), $false)
        $this.Put($a)
        return $a
    }
}
class FakeMail {
    [int]$Class = 43
    [string]$EntryID
    [string]$ConversationID
    [string]$Subject
    [string]$Body
    [string]$HTMLBody
    [int]$BodyFormat = 2
    [string]$SenderName
    [string]$SenderEmailAddress
    [string]$SenderEmailType = 'SMTP'
    [datetime]$ReceivedTime
    [datetime]$SentOn
    [datetime]$CreationTime = (Get-Date)
    [datetime]$LastModificationTime = (Get-Date)
    [FakeRecipients]$Recipients = [FakeRecipients]::new()
    [FakeRecipients]$ReplyRecipients = [FakeRecipients]::new()
    [FakeAttachments]$Attachments = [FakeAttachments]::new()
    [FakePA]$PropertyAccessor = [FakePA]::new()
    [string]$To
    [string]$CC
    [string]$BCC
    [bool]$Sent = $false
    [string]$Kind = 'new'
    [object]$Parent = $null
    [object] Reply() {
        $m = [FakeMail]::new()
        $m.Kind = 'reply'
        $m.ConversationID = $this.ConversationID
        [void]$m.Recipients.Add($this.SenderEmailAddress)
        return $m
    }
    [object] ReplyAll() {
        $m = $this.Reply()
        $m.Kind = 'replyAll'
        return $m
    }
    [object] Forward() {
        $m = [FakeMail]::new()
        $m.Kind = 'forward'
        $m.ConversationID = $this.ConversationID
        foreach ($a in $this.Attachments.L) { $m.Attachments.Put($a) }
        return $m
    }
    [void] Send() {
        $this.Sent = $true
        [void]$global:SENT.Add($this)
    }
    [void] Save() {
        if (-not $this.EntryID) {
            $this.EntryID = 'DRAFT' + ($global:DRAFTS.Count + 1)
            $this.Parent = $global:DRAFTSFOLDER
            [void]$global:DRAFTS.Add($this)
        }
    }
    [void] Delete() { $global:DRAFTS.Remove($this) }
}
class FakeItems : System.Collections.IEnumerable {
    [System.Collections.IEnumerator] GetEnumerator() { return $this.L.GetEnumerator() }
    [System.Collections.ArrayList]$L
    [int]$i = 0
    FakeItems([System.Collections.ArrayList]$l) { $this.L = $l }
    [object] Restrict([string]$f) {
        $global:LASTFILTER = $f
        return [FakeItems]::new($this.L)
    }
    [void] Sort([string]$f, [bool]$d) { $this.L = [System.Collections.ArrayList]@($this.L | Sort-Object ReceivedTime -Descending) }
    [object] GetFirst() {
        $this.i = 0
        if ($this.L.Count) { return $this.L[0] }
        return $null
    }
    [object] GetNext() {
        $this.i++
        if ($this.i -lt $this.L.Count) { return $this.L[$this.i] }
        return $null
    }
}


$global:SENT = [System.Collections.ArrayList]::new()
$global:DRAFTS = [System.Collections.ArrayList]::new()
$now = Get-Date
$m1 = [FakeMail]::new(); $m1.EntryID = 'E1'; $m1.ConversationID = 'C1'; $m1.Subject = 'Q4 review'; $m1.Body = "Hi Tony,`r`n  bring the numbers.`r`n"
$m1.HTMLBody = '<p>Hi</p>'; $m1.SenderName = 'Dana W'; $m1.SenderEmailType = 'EX'; $m1.SenderEmailAddress = '/o=ex/cn=dana'
$m1.PropertyAccessor.Props['http://schemas.microsoft.com/mapi/proptag/0x5D01001F'] = 'dana@work.example'
$m1.PropertyAccessor.Props['http://schemas.microsoft.com/mapi/proptag/0x1035001F'] = '<abc@work.example>'
$m1.ReceivedTime = $now.AddHours(-1)
[void]$m1.Recipients.Put([FakeRecipient]::new('Burns, Tony', '/o=ex/cn=tony', 1, 'tony@work.example'))
[void]$m1.Recipients.Put([FakeRecipient]::new('Sam', 'sam@x.example', 2, $null))
[void]$m1.Attachments.Put([FakeAttachment]::new('logo.png', [byte[]](1,2,3), $true))
[void]$m1.Attachments.Put([FakeAttachment]::new('numbers.xlsx', [Text.Encoding]::ASCII.GetBytes('XLSX'), $false))
$meeting = [FakeMail]::new(); $meeting.Class = 53; $meeting.EntryID = 'E2'; $meeting.Subject = 'Meeting'; $meeting.SenderEmailAddress = 'org@x.example'; $meeting.ReceivedTime = $now.AddHours(-2)
$report = [FakeMail]::new(); $report.Class = 46; $report.EntryID = 'E3'; $report.Subject = 'Undeliverable'; $report.ReceivedTime = $now.AddHours(-3)
$sentItem = [FakeMail]::new(); $sentItem.EntryID = 'S1'; $sentItem.Subject = 'RE: Q4 review'; $sentItem.SentOn = $now.AddMinutes(-5); $sentItem.ReceivedTime = $now.AddMinutes(-5)
$sentItem.SenderEmailAddress = 'tony@work.example'; $sentItem.PropertyAccessor.Props['http://schemas.microsoft.com/mapi/string/{00020329-0000-0000-C000-000000000046}/EchoSendKey'] = 'k42'
$byId = @{ E1 = $m1; E2 = $meeting; E3 = $report; S1 = $sentItem }
$folders = @{ 6 = [System.Collections.ArrayList]@($m1, $meeting, $report); 5 = [System.Collections.ArrayList]@($sentItem); 16 = $global:DRAFTS }

$fakeNs = [pscustomobject]@{ CurrentUser = [pscustomobject]@{ AddressEntry = [FakeEntry]::new() } }
$global:DRAFTSFOLDER = [pscustomobject]@{ EntryID = 'DRAFTSFOLDER' }
$fakeNs | Add-Member ScriptMethod GetDefaultFolder { param($n) [pscustomobject]@{ Items = [FakeItems]::new($folders[[int]$n]); EntryID = $(if ([int]$n -eq 16) { 'DRAFTSFOLDER' } else { 'F' + $n }) } }
$fakeNs | Add-Member ScriptMethod GetItemFromID { param($id) if ($byId.ContainsKey($id)) { return $byId[$id] }; foreach ($d in $global:DRAFTS) { if ($d.EntryID -eq $id) { return $d } }; throw 'MAPI_E_NOT_FOUND' }
$fakeOl = [pscustomobject]@{}
$fakeOl | Add-Member ScriptMethod CreateItem { param($t) [FakeMail]::new() }

. ([ScriptBlock]::Create($body))
function Connect-Outlook { $script:ol = $fakeOl; $script:ns = $fakeNs }

$pass = 0; $fail = 0
function Check($name, $cond, $extra) { if ($cond) { $script:pass++; "  ok   $name" } else { $script:fail++; "  FAIL $name $extra" } }
function Req($h) { $d = New-Object 'System.Collections.Generic.Dictionary[string,object]'; foreach ($k in $h.Keys) { $d[$k] = $h[$k] }; return $d }
function Run($h) {
    try { $r = Invoke-Op (Req $h); return $json.DeserializeObject((ConvertTo-JsonText @{ ok = $true; result = $r })) }
    catch { $m = [string]$_.Exception.Message; $st = 500; if ($m -match '^ECHO(\d{3})\|(.*)$') { $st = [int]$Matches[1]; $m = $Matches[2] }; return @{ ok = $false; status = $st; error = $m } }
}

$a = Run @{ op = 'account' }
Check 'account: the Exchange user''s SMTP address' ($a.ok -and $a.result.account -eq 'me@work.example') ($a | ConvertTo-Json -Compress)

$l = Run @{ op = 'list'; folder = 'inbox'; after = $now.AddDays(-30).ToString('yyyy-MM-ddTHH:mm:ss'); max = 100 }
Check 'list: mail and meetings, not reports' ($l.ok -and $l.result.Count -eq 2) ($l | ConvertTo-Json -Compress -Depth 5)
$x = $l.result[0]
Check 'list: newest first' ($x.id -eq 'E1')
Check 'list: Exchange sender resolved to SMTP' ($x.from -eq '"Dana W" <dana@work.example>') $x.from
Check 'list: To with name and SMTP' ($x.to -eq '"Burns, Tony" <tony@work.example>') $x.to
Check 'list: Cc falls back to the address' ($x.cc -eq '"Sam" <sam@x.example>') $x.cc
Check 'list: snippet is one line' ($x.snippet -eq 'Hi Tony, bring the numbers.') $x.snippet
Check 'list: Message-ID and conversation' ($x.messageId -eq '<abc@work.example>' -and $x.threadId -eq 'C1')
Check 'list: received as local ISO time' ($x.received -match '^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d$') $x.received
Check 'list: Restrict uses ReceivedTime' ($global:LASTFILTER -like "[[]ReceivedTime] >= '*'") $global:LASTFILTER

$s = Run @{ op = 'list'; folder = 'sent'; after = $now.AddDays(-30).ToString('yyyy-MM-ddTHH:mm:ss'); max = 100 }
Check 'sent list: carries the send key' ($s.ok -and $s.result[0].sendKey -eq 'k42') ($s | ConvertTo-Json -Compress -Depth 5)
Check 'sent list: Restrict uses SentOn' ($global:LASTFILTER -like "[[]SentOn] >= '*'") $global:LASTFILTER

$b = Run @{ op = 'body'; id = 'E1' }
Check 'body: html and text' ($b.ok -and $b.result.html -eq '<p>Hi</p>' -and $b.result.text -like 'Hi Tony*')
Check 'body: hidden inline picture left out' ($b.result.attachments.Count -eq 1 -and $b.result.attachments[0] -eq 'numbers.xlsx') ($b.result.attachments -join ',')
$at = Run @{ op = 'attachment'; id = 'E1'; index = 0 }
Check 'attachment: the visible one, base64' ($at.ok -and $at.result.name -eq 'numbers.xlsx' -and [Text.Encoding]::ASCII.GetString([Convert]::FromBase64String($at.result.data)) -eq 'XLSX')
$nf = Run @{ op = 'body'; id = 'NOPE' }
Check 'an id Outlook does not have is 404' (-not $nf.ok -and $nf.status -eq 404) ($nf | ConvertTo-Json -Compress)
$ek = Run @{ op = 'body'; id = 'echo:abc' }
Check 'a send key is never looked up' (-not $ek.ok -and $ek.status -eq 404)

New-Item -ItemType Directory -Force (Join-Path (Split-Path -Parent $PSScriptRoot) 'logs') | Out-Null
$tmp = Join-Path (Split-Path -Parent $PSScriptRoot) 'logs\outlook-bridge-test-attachment.txt'   # logs\ is git-ignored
[IO.File]::WriteAllText($tmp, 'hello')
$r = Run @{ op = 'send'; baseKind = 'reply'; baseId = 'E1'; to = @('dana@work.example'); cc = @('sam@x.example'); bcc = @(); subject = 'Re: Q4 review'; text = "Will do.`n`n> bring the numbers"; keepOriginalAttachments = $false; sendKey = 'k77'; files = @($tmp) }
$sm = $global:SENT[$global:SENT.Count - 1]
Check 'send reply: sent from Outlook''s own Reply' ($r.ok -and $sm.Kind -eq 'reply' -and $sm.ConversationID -eq 'C1') ($r | ConvertTo-Json -Compress)
Check 'send reply: plain text, our words' ($sm.BodyFormat -eq 1 -and $sm.Body -like 'Will do.*')
Check 'send reply: recipients replaced by ours' (($sm.Recipients.L | ForEach-Object { "$($_.Type):$($_.Address)" }) -join ',' -eq '1:dana@work.example,2:sam@x.example') (($sm.Recipients.L | ForEach-Object { "$($_.Type):$($_.Address)" }) -join ',')
Check 'send reply: stamped with the send key' ($sm.PropertyAccessor.Props['http://schemas.microsoft.com/mapi/string/{00020329-0000-0000-C000-000000000046}/EchoSendKey'] -eq 'k77')
Check 'send reply: the file attached' ($sm.Attachments.L.Count -eq 1)
Check 'send reply: answers the key and conversation' ($r.result.sendKey -eq 'k77' -and $r.result.threadId -eq 'C1')
$fw = Run @{ op = 'send'; baseKind = 'forward'; baseId = 'E1'; to = @('ap@work.example'); subject = 'Fw'; text = 'see'; keepOriginalAttachments = $false; sendKey = 'k78'; files = @() }
$fm = $global:SENT[$global:SENT.Count - 1]
Check 'forward without the original files' ($fw.ok -and $fm.Kind -eq 'forward' -and $fm.Attachments.L.Count -eq 0)
$fw2 = Run @{ op = 'send'; baseKind = 'forward'; baseId = 'E1'; to = @('ap@work.example'); subject = 'Fw'; text = 'see'; keepOriginalAttachments = $true; sendKey = 'k79'; files = @() }
Check 'forward with the original files (not the hidden inline picture)' ($global:SENT[$global:SENT.Count - 1].Attachments.L.Count -eq 1)
$nw = Run @{ op = 'send'; baseKind = 'reply'; baseId = 'GONE'; to = @('x@y.example'); subject = 'Re: old'; text = 'hi'; sendKey = 'k80'; files = @() }
Check 'a reply to a message Outlook lost goes as new' ($nw.ok -and $global:SENT[$global:SENT.Count - 1].Kind -eq 'new')

$d1 = Run @{ op = 'saveDraft'; baseKind = 'reply'; baseId = 'E1'; to = @('dana@work.example'); subject = 'Re: Q4'; text = 'draft one' }
Check 'save a draft: kept in Drafts with an id and version' ($d1.ok -and $d1.result.draftId -eq 'DRAFT1' -and $d1.result.version.Length -eq 40) ($d1 | ConvertTo-Json -Compress)
$d2 = Run @{ op = 'saveDraft'; draftId = 'DRAFT1'; baseKind = 'reply'; baseId = 'E1'; to = @('dana@work.example'); subject = 'Re: Q4'; text = 'draft one' }
Check 'the same words keep the same version' ($d2.result.version -eq $d1.result.version)
$ld = Run @{ op = 'listDrafts' }
Check 'list drafts' ($ld.ok -and $ld.result.Count -eq 1 -and $ld.result[0].version -eq $d1.result.version)
$dc = Run @{ op = 'draft'; draftId = 'DRAFT1' }
Check 'read a draft' ($dc.ok -and $dc.result.text -eq 'draft one' -and $dc.result.updated -match 'T')
$dd = Run @{ op = 'deleteDraft'; draftId = 'DRAFT1' }
$dd2 = Run @{ op = 'deleteDraft'; draftId = 'DRAFT1' }
Check 'delete a draft; deleting again is not an error' ($dd.ok -and $dd.result.deleted -and $dd2.ok -and -not $dd2.result.deleted)
$sl = Run @{ op = 'list'; folder = 'inbox'; after = $now.AddDays(-3).ToString('yyyy-MM-ddTHH:mm:ss'); before = $now.ToString('yyyy-MM-ddTHH:mm:ss'); max = 100 }
Check 'a time slice asks Outlook for after AND before' ($sl.ok -and $global:LASTFILTER -like "*AND [[]ReceivedTime] < '*") $global:LASTFILTER
$dr = Run @{ op = 'deleteDraft'; draftId = 'E1' }
Check 'deleteDraft never touches a received message' ($dr.ok -and -not $dr.result.deleted -and $byId.ContainsKey('E1'))
$sr = Run @{ op = 'saveDraft'; draftId = 'E1'; to = @('x@y.example'); subject = 'hijack'; text = 'no' }
Check 'saveDraft never rewrites a received message' (-not $sr.ok -and $sr.status -eq 404 -and $m1.Subject -eq 'Q4 review')
$sd = Run @{ op = 'send'; baseKind = 'draft'; baseId = 'E1'; to = @('x@y.example'); subject = 'S'; text = 't'; sendKey = 'k90'; files = @() }
Check 'sending "draft" E1 never sends the received message itself' ($sd.ok -and -not $m1.Sent -and $global:SENT[$global:SENT.Count - 1].Kind -eq 'new')
$m1.Attachments.L[0].PropertyAccessor.Props['http://schemas.microsoft.com/mapi/proptag/0x7FFE000B'] = $true
$rp = Run @{ op = 'send'; baseKind = 'forward'; baseId = 'E1'; to = @('a@b.example'); subject = 'Fw'; text = 'x'; keepOriginalAttachments = $true; sendKey = 'k91'; files = @() }
Check 'a forward keeping files drops the hidden inline pictures' ($global:SENT[$global:SENT.Count - 1].Attachments.L.Count -eq 1)
$bad = Run @{ op = 'format-c' }
Check 'an unknown request is 400' (-not $bad.ok -and $bad.status -eq 400)

"`n$pass passed, $fail failed"
if ($fail) { exit 1 }