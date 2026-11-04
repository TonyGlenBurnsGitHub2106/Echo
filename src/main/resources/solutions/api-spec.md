# Solutions API -- the contract

You are an API client of the Solutions program: a shared place where a team plans,
tracks and records a technical effort. Everything you may do is on this page. If it
is not on this page, you may not do it -- do not guess at other paths.

Read this whole page before your first call. Fetch it again at the start of every
working session; it is always current.

## What is on this page

1. Connecting -- the headers, the answers you will see, the error body, paging
2. The shape of the data -- codes, dates, ids, and how a PUT merges
3. The five rules
4. Endpoints -- Orientation, Projects, Items, Log, Notes, Terms, Links and topics, Kinds of topic,
   Waits, Many at once and Make into, Since you were last here, Questions and decisions, Deleting,
   Files, Conversations, Testing checklists, Configuration, Configuration II
5. Seeing everything -- how to read a whole solution
6. Every operation, one example each
7. A working session, start to finish

> **Changed 2026-09-22 -- Documents are now Notes.** Every `/documents` path is now `/notes`
> (`/api/solutions/{id}/notes`, `/api/solutions/notes/{noteId}/...`), `lookups.documentTypes` is
> `noteTypes`, a solution's `documentCount` is `noteCount`, and the `documentId` on a version or a
> step is `noteId`. The old paths no longer exist. Nothing else changed; the note type
> codes did not change (read them from `noteTypes`).

> **Added 2026-10-09 -- Terms.** A solution now has a glossary: the words, acronyms, rules and names
> it uses, each with a definition. See *Terms* in section 4. Its types and categories belong to the
> solution alone. Nothing else changed.

> **Added 2026-11-02 -- Links, Topics, and Since you were last here.** Any two records of a solution
> (item, note, log entry, file, term, topic) can now be LINKED, and a link shows from both ends. A
> TOPIC is a named subject ("Charset") that records join by being linked to it. And `POST .../visit`
> tells you what others changed since your last session. See *Links and topics* and *Since you were
> last here* in section 4. Nothing else changed.

> **Added 2026-11-04 -- Questions and Decisions.** A solution now keeps its open QUESTIONS and a
> DECISION register. A decision can close what it settles: the open questions and items linked to it.
> A changed mind is a NEW decision that supersedes the old one. Links also join `question` and
> `decision` records, and `GET .../summary-age` says how old `statusSummary` is. See *Questions and
> decisions* in section 4. Nothing else changed.

> **Added 2026-11-04 -- Kinds of topic, the premise switch, and waits.** A topic now has a `kind`:
> `subject` (as before), `tag`, `part` (a piece of what the solution is made of -- a chapter, an account,
> an environment -- with a kind of part, a status and a number) or `assumption` (something the plan rests
> on; proving it false cancels the open items resting on it in one act). And an item can WAIT ON another.
> See *Kinds of topic* and *Waits* in section 4. Existing topic calls work as before.
>
> **Added 2026-11-04 -- Now / Next / Waiting, a note for next time, and the activity feed.**
> `GET .../lens` sorts the open items into now, waiting and next, and flags idle ones. `GET` / `PUT
> .../left-off` keeps ONE note per member per solution for their next session: write one at the end of
> every session ("Next time, start with ...") and read yours at the start. `GET .../activity` lists
> everyone's changes, yours too, narrowed by who, kind, days and topic. See *Orientation* in section 4.
>
> **Added 2026-11-04 -- Many at once, Make into, saved views, file versions.** `POST .../items/bulk` changes
> one field on many items with a REASON (one log entry, linked to each). `POST .../promote` makes a message,
> a log entry, a note or an item into another kind without retyping. Files keep VERSIONS. See *Many at
> once and Make into* and *Files* in section 4.

## 1. Connecting

- Base URL: `{{BASE_URL}}`
- Every request carries BOTH headers:
  - `X-Echo-Client: <your client code>`
  - `X-Echo-Client-Key: <your key>`
- Send and expect JSON: `Content-Type: application/json`. A JSON body sent without that header answers
  415. File uploads are the one exception: they are `multipart/form-data` (see *Files*).
- Send NO other credential of any kind (no `Authorization` header, no other key header, no cookies).
  A request that also carries an `Authorization` header or another key header is refused outright
  with 401.
- Check your connection first: `GET /api/solutions/whoami`. It answers with the
  member code your writes are attributed to.
- Every path id (`{id}`, `{itemId}` ...) is a whole number. A path with anything else in that place is
  no address at all (404).

### Answers and errors

Answers you will see:

| Status | Meaning | What to do |
|---|---|---|
| 200 | Done. The body is the saved or requested record. A delete answers a small confirmation such as `{"ok": true, "deletedId": 42}` (each section says when it answers something else). | Carry on. |
| 400 | The request was malformed: a required field missing or blank, a value of the wrong form (a date, a kind, a status filter), or a text over its limit. `message` says which field. Also when the body is not valid JSON, a value has the wrong JSON type (text where a number goes), or a query parameter that must be a number is not -- then the body is the server's plain error (below), with no `message`. | Fix the request. Do not resend it unchanged. |
| 401 | Wrong client code or key, the client is switched off, or the request also carried another credential. | Stop. Tell your human. Do not retry in a loop. |
| 403 | Either the path is outside what you may reach, or the action is the owner's (creating or deleting a whole solution, sharing it, choosing where its files are saved, managing members, reordering the note kinds, renaming or resolving a conversation, changing or removing a message). | Do not retry. Ask in a conversation instead. |
| 404 | No such record -- or it is not one you were given -- or no such address (check the path against this contract). A record in a solution not shared with you answers "Solution not found.", never "forbidden". | Re-read the list you got the id from, and the path. |
| 405 | That path exists, but not with that method. | Check the method against this contract. |
| 409 | A rule refused it (duplicate name, wrong parent, a code that does not exist). `message` is written for you. Most such messages end "Nothing was saved." (or "changed", "added", "copied"): nothing was written. Where a 409 can come after part of the work was saved, the section says so. | Read `message`, adjust, retry once. |
| 413 | An upload over 60 MB in all. | Send a smaller file. |
| 415 | The body is not of the type the call takes (JSON without its `Content-Type`, or an upload that is not multipart). | Fix the header. |
| 429 | Ten failed key attempts from your address inside fifteen minutes. | Stop for fifteen minutes. |
| 500 | A fault on the server, not in your request. | Do not retry in a loop. Tell your human the call and the time it failed. |

Error bodies look like `{"ok": false, "error": "rule", "message": "..."}`.

There are three shapes, and only the first carries `message`:

| Who answers | Body | When |
|---|---|---|
| Solutions itself | `{"ok": false, "error": "<code>", "message": "<a sentence for you>"}`. `error` is `bad_request` (400), `forbidden` (403), `not_found` (404, for example "Item not found.") or `rule` (409). | Every refusal described on this page. Act on `message`. |
| The door, before Solutions sees the request | `{"ok": false, "error": "<a sentence>"}` -- the sentence is in `error`: "Unknown API client or wrong key." or "An API client may not present any other credential." (401), "This API client may not reach that path." (403), "Too many failed attempts. Try again later." (429). | Your credentials or the path. |
| The web server | `{"timestamp": "...", "status": 400, "error": "Bad Request", "path": "/api/solutions/..."}` -- no `message`, no detail. | JSON it could not read, a wrong JSON type, a query parameter of the wrong type or missing when required, no such address (404), wrong method (405), too large (413), wrong body type (415), and every 500. |

A field you send that a call does not take is ignored, silently: check the spelling of every field
against this page.

### Paging

The lists that can grow long come one page at a time. They take `offset` (how many rows to skip;
default 0; below 0 counts as 0) and `pageSize` (default 50; 0 or less means 50; above the call's
maximum it is cut to that maximum, never refused -- the maximum is 500 unless a call says otherwise).
The answer is an object that holds the rows (under a name such as `entries`, `terms`, `links`,
`topics`, `questions`, `decisions` or `changes`) and `offset`, `pageSize` (as applied) and `total` (how
many rows match, over every page). For the next page add `pageSize` to `offset`; stop when `offset`
reaches `total`.

| Paged that way | Not paged: the whole list, as a plain array |
|---|---|
| log, terms, links, topics, questions, decisions, since, activity | solutions, projects, items, notes, note versions, files, file versions (an object), conversations, messages, testing checklists, waits, kinds of part, saved views |

Three are different, and their rows say how: `link-targets` (at most 100 rows, no `offset`, a plain
array), `lens` (pages one bucket at a time, `counts` instead of `total`) and `visit` (always the first
page; read on with `since`).

## 2. The shape of the data

```
Solution                 a whole effort
  Project                a workstream inside it
    Item                 an action item; parentItemId nests it under another item, to any depth
  Log                    HOW DID WE DO IT -- an append-only journal, numbered in order
  Note                   HOW DO YOU DO IT -- runbooks (with ordered steps), strategies, references
  Conversation           questions and answers between you and the house
```

People and statuses are always CODES, never ids. Get the valid codes from
`GET /api/solutions/lookups`:

- status codes: `todo`, `inprogress`, `blocked`, `done`, `cancelled` (shown as "Skipped"). `done` and
  `cancelled` are the closed ones.
- log type codes: `note`, `command`, `result`, `decision`, `blocker`, `question` -- a person may add
  more; read `logTypes` in lookups.
- note type codes: `instructions`, `strategy`, `status`, `contacts`, `localreferences`, `qareferences`,
  `productionreferences`, `runbook`, `reference` -- a person may add more, so always read `noteTypes`
  in lookups.
- member codes: whoever is listed under `members` with `active: true` (you are one of them)

**A code that does not exist is refused, not guessed at.** `statusCode: "finished"` or
`assigneeCode: "bob"` answers 409, saves nothing, and the `message` lists the valid codes, for
example: `There is no status code 'finished'. The status codes are: todo, inprogress, blocked, done,
cancelled. Nothing was saved. GET /api/solutions/lookups lists every code.` Codes are matched
without regard to case. You may always use your own member code (the one `whoami` gives you), even
on your first call.

Priority is 1 (highest) to 5; 3 is normal. A number outside 1 to 5 is not refused: on a create it
becomes 3; on an update 0 or less keeps the current value and above 5 becomes 3.

**Dates and times.** Send a date as `2026-09-17`, or a date and time as `2026-09-17T14:30` or
`2026-09-17T14:30:00` (no time zone; the server's local time). A blank date is the same as none.
Anything else answers 400 "<field> must look like 2026-09-17 or 2026-09-17T14:30." Dates come back as
date-times, `"2026-09-17T14:30:00"`, often with a fraction of a second (`"2026-09-17T14:30:00.123"`),
or `null`. A date sent without a time comes back as midnight (`"2026-09-17T00:00:00"`). One value
takes a stricter form: a bulk change's `due` is exactly `yyyy-mm-dd`.

**Ids and nulls.** Every id is a whole number. In a body, an optional id of `0` means "none": no
parent, no project, no item. Every field of an answer is always there; one with no value is `null`
(a list with nothing in it is `[]`).

**Every PUT is a merge** (the one exception, `PUT .../left-off`, says so). Send only the fields you
want to change; anything you leave out keeps its current value. To empty a text field on purpose,
send it as `""` (an owner or assignee `""` unassigns). You cannot blank a name, a title, a status, or
a log entry's text or type by accident: a blank one keeps its value. A date (`targetDate`, `dueDate`)
cannot be emptied once set: `""` keeps it.

**Text limits.** Each row gives the limit of a field. Where a row says "longer is cut", a longer text
is shortened without an error; everywhere else a longer text is refused with 400 or 409.

## 3. The five rules

1. **The tracker is the only list.** Every action item you know about goes in here as
   an Item. Do not keep a private list anywhere else; if it is not here, the rest of
   the team cannot see it.
2. **Log what you did, when you did it.** After any real action -- a command run, a
   change made, a decision taken, a dead end hit -- append a Log entry. One entry per
   action, in the order it happened. Put the exact command in a `command` entry and
   what came back in a `result` entry.
3. **Keep statuses true.** When you start an item set it `inprogress`; when you cannot
   proceed set it `blocked` and log a `blocker` entry saying why; when it is finished
   set it `done`.
4. **Ask in a conversation, then wait.** A question goes in a Conversation. A person
   reads it and relays the answer; that can take hours. Do not re-ask. Poll for the
   answer with `since`, at most every few minutes, and carry on with other work.
5. **You have full rights inside a solution shared with you -- use them with care.** You may
   add, edit and delete projects, items, log entries, notes, steps and files. A delete is
   permanent and an item takes its whole sub-tree with it, so prefer the status `cancelled`
   when work was simply dropped -- the history stays. What you can never do: create or delete
   a solution itself, share it, or change where its files are saved. Those are the owner's.

## 4. Endpoints

### Orientation

| Call | Returns |
|---|---|
| `GET /api/solutions/whoami` | `{kind, memberCode, scoped}`. For you `kind` is `"client"`, `memberCode` is your client code and `scoped` is `true` (you see only the solutions shared with you). Every write you make is attributed to that member code; you cannot write as anyone else. |
| `GET /api/solutions/lookups` | `{statuses, members, logTypes, noteTypes}`, the same for every solution. Each status is `{id, code, name, closed, colorHex, sortOrder}` (`closed: true` for `done` and `cancelled`; the `cancelled` status is named "Skipped"). Each member is `{id, code, name, agent, active, sortOrder}`; members with `active: false` are listed too, but you cannot name them as an owner or an assignee. Each log type and each note type is `{id, code, name, sortOrder}`. Always send the `code`, never the `id`. |
| `GET /api/solutions/spec` | this page, as plain text (markdown) |
| `GET /api/solutions` | the solutions you were given, as an array, in `sortOrder` then name. Archived ones are left out; add `?includeArchived=true` to see them too (they come last). Each solution is `{id, name, description, descriptionPreview, statusCode, statusName, closed, colorHex, ownerCode, ownerName, statusSummary, targetDate, archived, saveFilesToRepo, fileCount, sharedCount, sortOrder, createdDate, modifiedDate, modifiedBy, projectCount, itemCount, closedItemCount, blockedItemCount, logCount, lastLogDate, noteCount, awaitingTonyCount, alias}`. In the list `description` is null and `descriptionPreview` holds its first 300 characters. The counts: `projectCount`; `itemCount` (every item and sub-item); `closedItemCount` (done or cancelled); `blockedItemCount`; `logCount` and `lastLogDate` (the newest entry's `logDate`); `noteCount`; `fileCount`; `sharedCount` (how many API clients it is shared with); `awaitingTonyCount` (messages in its conversations waiting for an answer from a person). `targetDate`, `createdDate`, `modifiedDate` and `lastLogDate` are date-times (`"2026-10-15T00:00:00"`) or null. |
| `GET /api/solutions/{id}` | one solution, the same fields, with its full `description` (here `descriptionPreview` is null). 404 "Solution not found." for one you were not given. |
| `GET /api/solutions/by-alias/{alias}` | the same, found by its `alias` -- the short name people say out loud ("claims"). When your human names a solution by a short word, look it up here. Case does not matter, and archived solutions are found too. The alias in the path is letters, digits and hyphens only. 404 "Solution not found." if there is none you may see. |
| `PUT /api/solutions/{id}` | update it -- body `{name, description, statusCode, ownerCode, statusSummary, targetDate, alias, archived, sortOrder}`, every field optional (a merge). Answers the saved solution, as `GET /api/solutions/{id}` does. `name`: at most 200 characters (longer is cut), unique across all solutions -- 409 "There is already a solution with that name."; blank keeps the current name. `description`: `""` empties it. `statusCode`: blank keeps it. `ownerCode`: a member code, `""` clears the owner. `statusSummary`: the one-paragraph "where are we", at most 2,000 characters (longer is cut), `""` clears it. `targetDate`: `2026-09-17` (400 for anything unreadable); once set it cannot be emptied -- `""` keeps it. `alias`: up to 40 lower-case letters, digits and hyphens with at least one letter (upper case is lowered for you), else 409 "An alias is up to 40 letters, digits and hyphens, with at least one letter -- for example 'claims'."; one another solution uses is 409 "Another solution already uses that alias."; `""` clears it. `archived`: `true` hides it from `GET /api/solutions`, `false` brings it back. `sortOrder`: its place in the list (1, 2, 3 ...); 0 or absent keeps it. |
| `GET /api/solutions/{id}/summary-age` | how old `statusSummary` is: `{writtenDate, ageDays, stale}`. `writtenDate` is when its current text was WRITTEN (a date-time; not when the solution was last saved), `ageDays` the whole days since then, and `stale` is true at 14 days or more. With no summary, `writtenDate` and `ageDays` are null and `stale` is false. When `stale` is true and you know the state of play, write a fresh `statusSummary` with `PUT /api/solutions/{id}`. |
| `GET /api/solutions/{id}/lens` | the OPEN items (not done, not cancelled) sorted for action: `{items, counts, bucket, offset, pageSize}`. Every open item lands in one bucket: `waiting` = blocked, or it waits on an item that is still open; `now` = in progress, or due within 7 days (overdue included); `next` = the rest. Waiting wins over now. Query: `bucket` (`now`, `next` or `waiting`; any other answers 400 "The lens has three buckets: now, next and waiting."), `offset` (default 0; used only with `bucket`), `pageSize` (default 50, at most 500). With no `bucket` you get the first `pageSize` items of EACH bucket, `now` first, then `waiting`, then `next`, and `bucket` is null in the answer; with one, a page of that bucket alone (`?bucket=next&offset=50`). `counts` is always `{now, waiting, next}`, each bucket's whole size whatever the page. Each item: `{id, title, parentItemId, projectId, projectName, statusCode, statusName, colorHex, priority, dueDate, assigneeCode, assigneeName, bucket, idleDays, idle, mine, overdue, openWaits, rowInBucket, totalInBucket}`. `dueDate` is a date-time or null; `idleDays` = days since the item last changed; `idle: true` = a `now` item unchanged for 7 days or more; `mine` = assigned to you; `overdue` = due before today; `openWaits` = how many open items it waits on; `rowInBucket` counts from 1. Order: in `now`, overdue first, then by due date; then, in every bucket, your own items first, then by priority, project and order. |
| `GET /api/solutions/{id}/left-off` | YOUR note for next time on this solution: `{note, writtenDate}` (`writtenDate` a date-time), both null when there is none. Read it at the start of a session. |
| `PUT /api/solutions/{id}/left-off` | write it -- body `{note}`. This PUT is NOT a merge: the note replaces your last one, and `""`, a missing `note` or `{}` takes it away. At most 1,000 characters after trimming (400 "A note for next time can be at most 1,000 characters."). Answers `{note, writtenDate}` as now saved. Each member has their own, so yours never touches your human's. Write one before you stop: where you were and what comes first next time. If you never called `visit` on this solution, writing a note counts as your first visit (your "since" starts then). |
| `GET /api/solutions/{id}/activity` | everyone's changes, yours included, newest first: `{changes, since, offset, pageSize, total}`. `since` is the date-time the window starts (now minus `days`); `total` counts every match, whatever the page. Each change: `{kind, id, title, sub, action, who, whoName, changedDate}`; `action` is `added`, `changed`, `wrote` (a log entry or a message), `corrected` (a log entry edited), `asked` (a question) or `decided` (a decision). A record shows once, at its latest change, and a record deleted since is gone; each conversation message is its own row, and for `message` the `id` is the conversation. Query: `days` (default 30, also for 0 or less; at most 3650), `who` (a member code; not checked, so an unknown code finds nothing), `kind` (item, note, log, file, term, topic, question, decision, message; any other answers 400 "kind must be one of item, note, log, file, term, topic, question, decision, message."), `topicId` (that topic and every record linked to it), `offset` (default 0), `pageSize` (default 50, at most 500). Unlike `since`, this is not about you: it is the solution's whole story. |

### Projects

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/projects` | none. Answers an array in `sortOrder`, then name. Each project is `{id, solutionId, name, description, descriptionPreview, statusCode, statusName, closed, colorHex, ownerCode, ownerName, sortOrder, createdDate, modifiedDate, modifiedBy, itemCount, closedItemCount, blockedItemCount}`. In the list `description` is null and `descriptionPreview` holds its first 300 characters. The counts take in every item and sub-item of the project; `closedItemCount` is done or cancelled. |
| `POST /api/solutions/{id}/projects` | `{name, description, statusCode, ownerCode, sortOrder}` -- `name` required (400 "A name is required."), at most 200 characters, unique in the solution (409 "This solution already has a project with that name."). `statusCode` absent means `todo`. `sortOrder` 0 or absent puts it last. Answers the saved project. |
| `GET /api/solutions/projects/{projectId}` | one project with its full `description` (here `descriptionPreview` is null). 404 "Project not found." |
| `PUT /api/solutions/projects/{projectId}` | same fields, a merge. A blank `name` or `statusCode` keeps the current one; `description` `""` empties it; `ownerCode` `""` clears the owner. `sortOrder` (1, 2, 3 ...) is the order projects are shown in; send it alone to move one. It sets this project's number only -- the others are not renumbered, and two projects with the same number are shown by name -- so to reorder several, send each its new number. 0 or absent keeps it. |
| `DELETE /api/solutions/projects/{projectId}` | see *Deleting*. Answers `{ok: true, deletedId}`. |

A project at `todo` is moved to `inprogress` for you the moment one of its items is saved at any
status other than `todo` (see *Items*).

### Items

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/items` | optional `?projectId=` (a project of another solution gives an empty list). A flat array; build the tree from `parentItemId`. The order is: by project (its `sortOrder`, then name), then the top-level items, then the sub-items grouped by `parentItemId`, each group in `sortOrder`. It is not tree order. `detail` is null here and `detailPreview` holds its first 300 characters (`""` when there is none). |
| `GET /api/solutions/items/{itemId}` | one item with its full `detail` (null when there is none; here `detailPreview` is null). 404 "Item not found." |
| `POST /api/solutions/projects/{projectId}/items` | `{title, detail, parentItemId, statusCode, assigneeCode, priority, dueDate, sortOrder}` -- `title` required (400 "A title is required."), at most 400 characters (longer is cut). Give `parentItemId` to make a sub-item; it must be an item of the same project (409 "The parent item must be in the same project."). `statusCode` absent means `todo`. `priority` 1 (highest) to 5; absent, 0 or out of range means 3. `sortOrder` 0 or absent puts it last among its siblings. `dueDate` is `2026-10-15` or `2026-10-15T14:30`. 404 "Project not found." Answers the saved item. |
| `PUT /api/solutions/items/{itemId}` | same fields, but send ONLY what changes. A field you leave out keeps its value. A blank `title` or `statusCode` keeps the current one. `detail` `""` empties it; `assigneeCode` `""` unassigns it. `priority` 0, negative or absent keeps it; above 5 becomes 3. A `dueDate`, once set, cannot be emptied -- `""` keeps it. `parentItemId` re-parents it inside its project, and `0` makes it top level; refusals as for *move*, plus 409 "An item cannot be its own parent.". An edit never changes project and never renumbers the siblings: to reorder or to change project, use `.../move`. |
| `POST /api/solutions/items/{itemId}/status` | `{statusCode}` -- the cheap way to change only the status. 400 "A status code is required." when it is missing or blank. Answers the item. |
| `POST /api/solutions/items/{itemId}/move` | `{projectId, parentItemId, sortOrder}`. `projectId` 0 or absent means "same project"; `parentItemId` 0 or absent means "top level" -- so to reorder a sub-item among its siblings, send its `parentItemId` again or it goes to the top; `sortOrder` 0, absent, or past the end means "last". The item's whole sub-tree goes with it, and the siblings it lands among are renumbered 1, 2, 3 ... with it in its place. Answers the moved item. |

Item fields (the list and the single item alike): `{id, projectId, solutionId, projectName, parentItemId,
title, detail, detailPreview, hasDetail, statusCode, statusName, closed, colorHex, assigneeCode,
assigneeName, priority, sortOrder, dueDate, completedDate, createdByCode, createdDate, modifiedDate,
modifiedBy, childCount, logCount}`. `parentItemId` is null at the top level. `childCount` counts the direct
sub-items only; `logCount` counts the log entries that name this item. `dueDate`, `completedDate`,
`createdDate` and `modifiedDate` are date-times (`"2026-10-15T00:00:00"`) or null.

What else happens:
- Setting an item to a closed status (`done`, `cancelled`) stamps `completedDate`; reopening it clears it.
- Saving an item at any status other than `todo` (create, edit or `.../status`) moves every ancestor
  item still at `todo` to `inprogress`, and its project too. Nothing is moved backwards: a blocked or
  done parent stays as it is. A move does not do this.
- If an item's current assignee is no longer an active member, any `PUT` of it is refused with the
  "There is no member code" 409 until you also send a new `assigneeCode` (or `""`). The same holds for a
  project's or a solution's owner.

Move refusals (409 unless said):
- 404 "Project not found." -- `projectId` is not a project, or is one in a solution not shared with you
  (then 404 "Solution not found.").
- "An item can only move between projects of the same solution."
- "An item cannot be moved under itself or one of its own sub-items."
- "The new parent must be in the destination project." -- also when `parentItemId` is not an item at all.

### Log

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/log` | one page, newest first. Filters, all optional: `projectId`, `itemId`, `author` (a member code), `type` (a log type code), `search` (entries whose text contains it; `%` and `_` act as wildcards), `since=<sequenceNumber>` (only entries with a higher number), `ascending=true` (oldest first, the order things were done), `offset` (default 0), `pageSize` (default 50, at most 500 -- a larger one is cut to 500). An unknown author or type is not refused; it just matches nothing. The answer is `{entries, counts, offset, pageSize, total}`: `offset` and `pageSize` as applied, `total` = how many entries match the filters (all pages). `counts` is a map under the same filters: `"total"`, then `"type:<code>"` and `"author:<code>"` for each kind and author with at least one entry (`"author:unknown"` for an entry with no author), e.g. `{"total":120,"type:command":40,"type:result":38,"author:rex":60}`. In the page `text` is cut at 2,000 characters with `truncated: true`. |
| `GET /api/solutions/log/{logId}` | one entry with its full text (`truncated: false`). 404 "Log entry not found." |
| `POST /api/solutions/{id}/log` | `{text, typeCode, itemId, projectId, logDate}` -- `text` required (400 "Some text is required."). Name the `itemId` the work was for whenever there is one: it must be an item of this solution (409 "That item does not belong to this solution."), and the entry takes the item's project, whatever `projectId` says. A `projectId` alone must be a project of this solution (409 "That project does not belong to this solution."). `typeCode` absent means `note`. Instead of `typeCode` you may send `typeName`, the kind's name (`"Decision"`); a name no kind has is refused -- 409 "There is no log kind named '...'. The log kinds are: ... Nothing was saved." -- you cannot add a kind. `logDate` (`2026-09-17` or `2026-09-17T14:30`) back-dates the entry; absent means now. The author is you. Answers the saved entry, carrying the `sequenceNumber` it was given (the next number in this solution). |
| `PUT /api/solutions/log/{logId}` | correct an entry: `{text, typeCode, typeName, itemId, projectId}`, a merge. A blank `text` or `typeCode` keeps the current one. `itemId` 0 unlinks the item; `projectId` 0 unlinks the project (unless the entry still names an item, whose project it keeps). The same 409 refusals as `POST`. It keeps its number, its author and its `logDate` (a `logDate` sent here is ignored). |

A log entry is `{id, solutionId, projectId, projectName, itemId, itemTitle, sequenceNumber, typeCode,
typeName, authorCode, authorName, text, truncated, logDate, createdDate, modifiedDate, stepCount}`.
`projectId`, `projectName`, `itemId` and `itemTitle` are null when it names none. `stepCount` is how many
runbook steps were made from it. `logDate` is when the thing happened, `createdDate` when it was written;
both are date-times (`"2026-09-17T14:30:00"`).

### Notes

A note is HOW DO YOU DO IT: a runbook (with ordered steps), a strategy or a reference. Its `body` is
markdown and is never overwritten: every save that changes it writes a new VERSION.

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/notes` | optional `?type=runbook` (a note type code; one no kind has gives `[]`). A plain list, not paged, in the order of the note kinds, then `sortOrder`, then title. Each note carries a 300-character `bodyPreview`; `body` is null and `steps` is `[]` (the GET of one has them). |
| `GET /api/solutions/notes/{noteId}` | the note: current `body` (markdown) and its `steps`, in step order. `bodyPreview` is null here. |
| `POST /api/solutions/{id}/notes` | `{title, typeCode, body, changeNote, projectId, sortOrder}` -- `title` required (at most 300 characters; longer is cut). `typeCode` absent means the first kind in `lookups.noteTypes` (the lowest `sortOrder`; `instructions` in a new install). Instead of `typeCode` you may send `typeName`, the kind's name (`"Runbook"`); a name no kind has is refused -- you cannot add a kind. `projectId` ties it to a project of this solution (optional). `sortOrder` absent or 0 puts it last. The first save is version 1, and `changeNote` (at most 500) is that version's reason. Answers the whole note. |
| `PUT /api/solutions/notes/{noteId}` | same fields, a merge: a field you leave out keeps its value. A blank `title` or `typeCode` is kept, not cleared. `"projectId":0` takes it off its project. A `body` that differs from the current one is saved as a NEW VERSION (`currentVersionNumber` goes up by one); nothing is ever overwritten. Say why in `changeNote`. Sending the same body, or no body, writes no version, and then `changeNote` is not kept anywhere. `"body":""` is a new, empty version. Answers the whole note. |
| `PUT /api/solutions/note-types/order` | `{ids}` -- a person reorders the note kinds. Not for an agent: kinds are shared by every solution, so this answers 403 to you (`"message":"Reordering the note kinds"`). |
| `GET /api/solutions/notes/{noteId}/versions` | who changed it, when, why, newest first: `[{noteId, versionNumber, changeNote, authorCode, authorName, createdDate, bodyLength, current, body}]`. `body` is null here; `current: true` marks the one the note shows. |
| `GET /api/solutions/notes/{noteId}/versions/{n}` | version number `n`, the same fields with its `body` |
| `POST /api/solutions/notes/{noteId}/steps` | `{title, command, expectedResult, notes, stepNumber}` -- `title` required (at most 400 characters; longer is cut). `stepNumber` 0, absent or past the end appends; any other number puts the step there and moves the later steps down one. |
| `PUT /api/solutions/notes/{noteId}/steps/{stepId}` | same fields, a merge: a field you leave out keeps its value, `""` clears `command`, `expectedResult` or `notes`, a blank `title` is kept. `stepNumber` is ignored here (it does not renumber); use `move`. |
| `POST /api/solutions/notes/{noteId}/steps/{stepId}/move` | `{stepNumber}` -- 1 or more (400 otherwise); a number past the end makes it the last step. The other steps close up around it. |
| `POST /api/solutions/notes/{noteId}/steps/from-log` | `{logId}` -- turns a log entry into the LAST step of this runbook: its first line is the step's `title` (cut at 400; `"Step from log entry"` when blank), the whole text goes in `command` for a `command` entry and in `notes` for any other kind, and `sourceLogId` / `sourceSequenceNumber` point back at the entry. This is how "what we did" becomes "how to do it again". |

Every step call (add, change, move, from-log, delete) answers with the whole note, so you always hold
the current numbering.

**A note** serializes as `{id, solutionId, projectId, projectName, typeCode, typeName, title,
currentVersionNumber, sortOrder, createdDate, modifiedDate, modifiedBy, authorCode, authorName,
versionDate, changeNote, stepCount, bodyPreview, body, steps}`. `authorCode`, `authorName`,
`versionDate` and `changeNote` describe the CURRENT version (who wrote this wording, when, why), not
who made the note. `projectId` and `projectName` are null for a note on no project. Dates are
date-times (`"2026-09-17T14:30:00"`). **A step** is `{id, noteId, stepNumber, title, command,
expectedResult, notes, sourceLogId, sourceSequenceNumber}`; the last two are null unless it came from
the log.

Refusals:

- 400 `"A title is required."` -- a new note or a new step without one. `"A step number is required."`
  -- a move without a `stepNumber` of 1 or more. `"A log entry id is required."` -- from-log without
  `logId`.
- 404 `"Note not found."`, `"Step not found."` (a step id that is not a step of THAT note), `"Version
  not found."`, `"Log entry not found."` -- and `"Solution not found."` for a solution you were not
  given.
- 409 `"There is no note type code 'x'. The note type codes are: ... Nothing
  was saved. GET /api/solutions/lookups lists every code."` -- a `typeCode` no kind has. `"There is no
  note kind named 'X'. The note kinds are: ... Nothing was saved."` -- a
  `typeName` no kind has. `"That project does not belong to this solution."` -- a `projectId` from
  elsewhere. `"The log entry and the note belong to different solutions."` -- from-log across
  solutions.

### Terms

A solution's glossary. A term has a `term` (the word), a `definition`, an optional `type` (one name),
`categories` (a list of names), `source` and `notes`, and a `favorite` star. A type or category is
found by its name, any case, and made the first time a term names it -- inside THIS solution only.
A type or category stays when no term uses it any more; remove it with the DELETE below.
A solution never has the same term twice (case and repeated spaces ignored); a repeat is refused
with 409 and a sentence saying which term it already has.

| Call | Note |
|---|---|
| `GET /api/solutions/{id}/terms` | one page, `{terms, counts, offset, pageSize, total}`. Optional `search` (term, definition, source, notes, type, category; `%` and `_` are plain characters), `typeId`, `categoryId`, `favorites=true`, `groupBy=type|category|none` (default `type`; any other value counts as `type`), `reverse=true`, `offset`, `pageSize` (default 50, at most 500; more is cut to 500). Order: groups A to Z (Z to A with `reverse`), the no-type / uncategorized group always last, then terms A to Z (with `groupBy=none`, `reverse` turns the terms Z to A). Each row has `groupKey` / `groupLabel`: `"type:<typeId>"` / the type's name, `"type:0"` / `"(No type)"`, `"category:<categoryId>"` / its name, `"category:0"` / `"(Uncategorized)"`, or `""` / `""` with `groupBy=none`. Grouped by category, a term appears once per category it carries, so the rows can outnumber `total`: keep paging until a page comes back shorter than `pageSize`. `definition` is a 600-character preview (`truncated: true` when there is more), and `notes`, `modifiedByCode` and `modifiedByName` are null in the list (`hasNotes` says whether there are notes). `counts`: `total` (matching terms), `all`, `favorites`, `types`, `categories`, and `group:<groupKey>` per group (for example `group:type:3`; none with `groupBy=none`). |
| `GET /api/solutions/terms/{termId}` | the whole term, notes and `modifiedByCode` / `modifiedByName` included (`groupKey` and `groupLabel` are `""`) |
| `POST /api/solutions/{id}/terms` | `{term, definition, type, categories, source, notes}` -- `term` and `definition` required. `term` at most 400 characters (line breaks and tabs in it become spaces), `type` at most 200, `source` at most 500, each category at most 200; `categories` is a list of names, blanks and repeats dropped. Answers the whole term. |
| `PUT /api/solutions/terms/{termId}` | same fields. A merge: a field you leave out is kept. `"type":""` takes the type off; `"categories":[]` takes every category off, and any other list REPLACES the term's categories with exactly those; `""` clears `source` or `notes`. `term` and `definition` cannot be blanked. Answers the whole term. |
| `PUT /api/solutions/terms/{termId}/favorite` | `{favorite: true|false}` -- answers the whole term |
| `DELETE /api/solutions/terms/{termId}` | takes the term and its links; its type and categories stay. Answers `{ok: true, deletedId}`. |
| `GET /api/solutions/{id}/terms/lookups` | `{types, categories}`, each `{id, name, usageCount}`, A to Z |
| `PUT /api/solutions/{id}/terms/types/{typeId}` | `{name}` -- rename a type (at most 200 characters). Refused if the solution already has one by that name. Answers with the new lookups. |
| `PUT /api/solutions/{id}/terms/categories/{categoryId}` | `{name}` -- rename a category, the same way |
| `DELETE /api/solutions/{id}/terms/types/{typeId}` | its terms are left with no type. No term is removed. Answers with the new lookups. |
| `DELETE /api/solutions/{id}/terms/categories/{categoryId}` | the category is taken off its terms and removed. No term is removed. Answers with the new lookups. |

**A term** serializes as `{id, solutionId, groupKey, groupLabel, term, definition, truncated, typeId,
type, source, notes, hasNotes, favorite, categories, authorCode, authorName, modifiedByCode,
modifiedByName, createdDate, modifiedDate}`. `type` is the type's name (null for none), `categories`
a list of names A to Z (`[]` for none), dates are date-times.

Refusals:

- 400 `"The term is required."`, `"A definition is required."` (a POST without them), `"favorite (true
  or false) is required."`, `"A name is required."` (a rename without one).
- 404 `"Term not found."`, or `"Solution not found."` for a solution you were not given.
- 409, each ending "Nothing was saved." (or "Nothing was removed." for a delete): `This solution
  already has the term "SLA".` -- a repeat; `A term cannot be blank.` / `A definition cannot be
  blank.` -- a PUT that blanks one; `A term can be at most 400 characters.`, `A type name can be at
  most 200 characters.`, `A source can be at most 500 characters.`, `A category name can be at most
  200 characters.`; `That type is not part of this solution.` / `That category is not part of this
  solution.` -- a `typeId` or `categoryId` of another solution, or none; `This solution already has a
  type by that name.` / `... a category by that name.` -- a rename onto an existing name.

### Links and topics

A LINK joins two records of one solution. A record is named by its `kind` and its id: `item`,
`note`, `log` (a log entry), `file`, `term`, `topic`, `question` or `decision`. A kind is read without
regard to case or spaces around it; any other word answers 400 "kind must be one of item, note, log,
file, term, topic, question, decision." A link has no direction, so once item 42 is linked to note 7,
item 42's links show note 7 AND note 7's links show item 42 -- a record's links are also its backlinks.
Link instead of writing "see item X" or "file 211" into a text: a link survives a rename and opens the
record in one tap.

A TOPIC is a named subject inside the solution ("Charset", "Remit dupe") with a `summary` -- where
it stands now, the current answer. A record joins a topic by being linked to it (`toKind: "topic"`),
so a topic's records are simply its links. Use a topic when one subject runs across many projects,
items, log entries and notes; keep its `summary` current when the answer changes. A topic also has a
kind (subject, tag, part, assumption): see *Kinds of topic*.

Both ends must be in the solution; a record never links to itself; a pair already linked (either way
round) is refused with 409. A topic name is used once per solution (case and repeated spaces ignored).
Deleting a record takes its links with it; deleting a topic takes its links, never the records.

| Call | Note |
|---|---|
| `GET /api/solutions/{id}/links?kind=item&recordId=42` | everything linked to one record. `kind` and `recordId` are both required (a missing one is a plain 400). Optional `offset` (default 0) and `pageSize` (default 50, at most 500; more is cut to 500). Answers `{kind, id, links, offset, pageSize, total}`; `total` counts every link of the record, whatever the page. Each link is the OTHER end: `{linkId, kind, id, title, sub, recordDate, closed, linkedDate, linkedByCode, linkedByName, topicKind}` (see *What a linked record shows* below). Order: topics first, then decisions, then questions, then the rest, each group newest first. A record that does not exist simply has no links: an empty list, not a 404. |
| `POST /api/solutions/{id}/links` | `{fromKind, fromId, toKind, toId}` -- all four required. Answers with the FROM record's links, the new one among them (the first 50, in the shape of the GET above). |
| `DELETE /api/solutions/{id}/links/{linkId}` | takes the link away; neither record is touched. Answers `{ok: true, deletedId}`. 404 "Link not found." when the solution has no such link. |
| `GET /api/solutions/{id}/link-targets?q=charset` | what you may link to: records of the solution whose `title` contains `q` (read as one piece of text, any case; `%`, `_` and `[` match themselves). Best first: a title that STARTS with `q`, then topics, then newest. Without `q`: topics first, then the newest records. Optional `kind` and `recordId` name the record you are linking FROM: it is left out, and `alreadyLinked: true` marks what it is already joined to. Optional `targetKind` (one of the eight kinds; `targetKind=item` offers items only, for example the item to wait on). `pageSize` defaults to 30, at most 100 (more is cut to 100); there is no `offset`. Answers a plain list (not wrapped): `[{kind, id, title, sub, recordDate, closed, alreadyLinked}]`. A `kind` or `targetKind` that is not one of the eight answers 400 with the message above. |
| `GET /api/solutions/{id}/topics` | one page, `{topics, counts, offset, pageSize, total}`. Optional `search` (in the name, the summary and a part's kind of part), `kind` (`subject`, `tag`, `part` or `assumption`; any other answers 400 "A topic's kind is one of subject, tag, part, assumption."), `offset` (default 0), `pageSize` (default 50, at most 500). Order: parts first (by their kind of part, in that kind's order, then each part's `sortOrder`), then assumptions, then subjects, then tags, each by name. `summary` is a 600-character preview (`truncated: true` when there is more). `counts`: `total` (what matches `search` and `kind`; the same as the top-level `total`), `all` (every topic of the solution), and `kind:<code>` for each kind that matches `search` -- these ignore `kind`, so one call shows how many of each there are. |
| `GET /api/solutions/topics/{topicId}` | the whole topic, `summary` in full. Its records: `GET /api/solutions/{id}/links?kind=topic&recordId={topicId}`. 404 "Topic not found." |
| `POST /api/solutions/{id}/topics` | `{name, summary}`, plus the kind fields in *Kinds of topic* -- `name` required (400 "A topic name is required."). Answers the whole topic. |
| `PUT /api/solutions/topics/{topicId}` | same fields. A merge: a field you leave out is kept; `"summary":""` clears it; a `name` cannot be blanked (409). Answers the whole topic. |
| `DELETE /api/solutions/topics/{topicId}` | the topic and every link to it; the records stay. Answers `{ok: true, deletedId}`. |

What a linked record shows (a link end, and a link target):

| `kind` | `title` | `sub` | `closed` is true when |
|---|---|---|---|
| `item` | its title | its project's name | its status is a closed one (`done`, `cancelled`) |
| `note` | its title | its note type's name | never |
| `log` | `#<sequenceNumber> <its first 200 characters>` | its log type's name | never |
| `file` | its title, else its file name | its file name | never |
| `term` | the term | the first 200 characters of its definition | never |
| `topic` | its name | `Topic`, `Tag`, the part's kind of part (`Part` when it has none), `Assumption` or `Assumption, proved false`; then, when it has a summary, a vertical bar and the summary's first 200 characters | a part's status is a closed one, or an assumption was proved false |
| `question` | its text | `Open question`, `Answered` or `Dropped` | it is answered or dropped |
| `decision` | its title | `Proposed`, `Decided` or `Replaced`, then the decided date as `mm/dd/yyyy` when there is one | it was superseded |

`recordDate` is when the record was last changed (when it was made, if never; a log entry's is when it
was written; a decision's is its decided date, else when it was made). `linkedDate` is when the link was
made, and `linkedByCode` / `linkedByName` who made it (null when not known). `topicKind` is the topic's
kind when the other end is a topic, else null. All dates are date-times (`2026-11-02T14:30:00`).

A topic, as every topic call answers it:

| Field | What it holds |
|---|---|
| `id`, `solutionId` | numbers |
| `name` | at most 200 characters; line breaks and tabs become spaces |
| `summary` | text or null. In a list, the first 600 characters |
| `truncated` | true in a list when `summary` was cut; always false on one topic |
| `authorCode`, `authorName` | who made it. Always null in a list: read the topic itself |
| `modifiedBy` | the member code of the last change (null until one) |
| `createdDate`, `modifiedDate` | date-times; `modifiedDate` is null until the first change |
| `memberCount` | how many records are linked to it |
| `latestDate` | in a list, the newest `recordDate` among its records (null with none). Always null on one topic |
| `kindCode` | `subject`, `tag`, `part` or `assumption` |
| `typeId`, `typeName`, `figureLabel` | a part's kind of part and what its number counts; null otherwise |
| `statusCode`, `statusName`, `colorHex` | a part's status; null otherwise |
| `closed` | true when a part's status is a closed one. It is NOT set for an assumption proved false: read `holds` |
| `figure` | a part's number, to two decimals (`4200.00`); null otherwise |
| `holds` | an assumption: true while it holds, false once proved false. Null for every other kind |
| `sortOrder` | a part's place within its kind of part (1, 2, 3 ...); 0 for a topic never put in order |

Refusals on `POST` / `PUT` topics (409 unless said): `This solution already has the topic "<name>". Nothing
was saved.`; "A topic name can be at most 200 characters. Nothing was saved."; "A topic name cannot be
blank. Nothing was saved." (a `PUT` with `"name":""`); 404 "Topic not found." on a `PUT` to no such topic.

### Kinds of topic

Every topic has a kind. You SEND it as `kind`; a topic ANSWERS with it as `kindCode`:

- `subject` -- one subject that runs across the solution ("Charset"). The default; every older topic is one.
- `tag` -- a quick flag put on records: Important, Risk, Idea, Parking lot, or your own. Link a record to it.
- `part` -- a piece of what the solution is MADE OF: a chapter, a character, an account, an environment.
  It carries a kind of part (`typeName`), a `statusCode` (one of the solution statuses) and an optional
  `figure` (a number: words, a balance). Link the records ABOUT that part to it; its topic View then
  shows everything about it.
- `assumption` -- something the plan rests on ("The 837 submit path is live"). `holds` is true until it is
  proved false. Link the items that REST on it to it.

`POST /api/solutions/{id}/topics` and `PUT /api/solutions/topics/{topicId}` take these fields beside
`name` and `summary`. On a `PUT`, a field you leave out is kept.

| Field | For | Meaning |
|---|---|---|
| `kind` | any | `subject`, `tag`, `part` or `assumption`, any case. Left out on an add: `subject`. Left out or `""` on a `PUT`: kept. Any other word answers 400 "A topic's kind is one of subject, tag, part, assumption." Changing the kind takes off what the old kind carried (a part's kind of part, status and number; an assumption's `holds`); a topic changed to an assumption starts out holding. |
| `typeName` | part | its kind of part, found by name (any case, repeated spaces ignored) or made, last in order, when the solution has none by that name. At most 100 characters. `""` takes it off. |
| `statusCode` | part | a status code from `GET /api/solutions/lookups`. `""` takes it off. A code that does not exist answers 409 "That is not a status. Nothing was saved." (the message does not list the codes: read them from lookups). |
| `figure` | part | a number, kept to two decimals. |
| `clearFigure` | part | `true` takes the number off (it wins over a `figure` sent with it). |
| `holds` | assumption | `true` or `false`. A new assumption holds unless you send `false`. Sending `false` only MARKS it: it cancels nothing and writes nothing in the log. When the plan really changes, use the premise switch below. |

Refusals (409, nothing is saved): "Only a part carries a kind of part, a status and a number. Nothing was
saved." when a `typeName` or `statusCode` other than `""`, or a `figure`, is sent for a topic that is not, or
will not be, a part; "Only an assumption holds or is proved false. Nothing was saved." when `holds` is sent
for one that is not an assumption; "A kind of part can be at most 100 characters. Nothing was saved."

A new part goes last among the parts of its kind of part. `GET /api/solutions/{id}/topics?kind=part` lists
one kind (see *Links and topics* for the order and the `kind:<code>` counts). A link to a topic carries its
`topicKind`.

THE PREMISE SWITCH. When an assumption turns out false, do not cancel its items one by one: read
`GET /api/solutions/topics/{topicId}/open-items`, then `POST /api/solutions/topics/{topicId}/proved-false`
with the reason and the items to cancel. The assumption is marked proved false (`holds: false`), each named
item becomes `cancelled` (shown as Skipped) exactly as `POST /api/solutions/items/{itemId}/status` would do
it, and ONE log entry of type `result`, linked to the assumption, records it. Its text reads
`**Assumption proved false:** <name>`, then your reason, then "Cancelled because of it:" with the title of
each item cancelled.

| Call | Note |
|---|---|
| `GET /api/solutions/topic-kinds` | `{"kinds": ["subject", "tag", "part", "assumption"], "starterTags": ["Important", "Risk", "Idea", "Parking lot"]}` -- two lists of plain words. A starter tag is only a suggestion until a topic is made with that name: `POST /api/solutions/{id}/topics` `{"name":"Risk","kind":"tag"}`. Look first with `GET /api/solutions/{id}/topics?kind=tag`, because a name the solution already has is refused (409). |
| `GET /api/solutions/{id}/topic-types` | the kinds of part, by their `sortOrder`, then name: `[{id, solutionId, name, figureLabel, sortOrder, partCount}]`. `partCount` = how many parts carry it; `figureLabel` is null when none was given. |
| `POST /api/solutions/{id}/topic-types` | `{name, figureLabel}` -- `name` required (400 "A name for the kind of part is required."), at most 100 characters; `figureLabel` says what a part's number counts ("words"), at most 40 characters (409 "What a number counts can be at most 40 characters. Nothing was saved."). When the solution already has one by that name (any case), that one is answered and nothing is added; a `figureLabel` sent with it replaces its label (`""` clears it). A new one goes last. Answers one row, as in the list. |
| `PUT /api/solutions/topic-types/{typeId}` | `{name, figureLabel}` -- a merge; `""` clears the label. Answers the row. Refusals: 409 "This solution already has that kind of part. Nothing was saved."; 409 "A kind of part cannot be blank. Nothing was saved." (`"name":""`); 404 "Kind of part not found." |
| `DELETE /api/solutions/topic-types/{typeId}` | its parts stay, with no kind of part. Answers `{ok: true, deletedId}`; 404 "Kind of part not found." |
| `PUT /api/solutions/{id}/topics/order` | `{ids}` -- the parts of one kind of part in their new order. Each topic named gets its place as `sortOrder` (1, 2, 3 ...); a topic not named keeps its own. Any topic of the solution may be named, but only a part's order shows. Answers `{ok: true, solutionId}`. Refusals: 400 "Name the topics in their new order." (missing or empty); 400 "The order is a list of topic ids." (a null, 0 or negative id); 409 "A topic in that order is not in this solution. Nothing was changed." |
| `GET /api/solutions/topics/{topicId}/open-items` | the OPEN items (status not closed) linked to the topic, of any kind of topic, in project order then item order: `[{id, title, sub}]`. `sub` is the project's name and the item's status name, joined by a vertical bar. 404 "Topic not found." |
| `POST /api/solutions/topics/{topicId}/proved-false` | `{reason, itemIds}` -- `reason` required; `itemIds` may be empty or left out (then nothing is cancelled, but the assumption is still marked and the log entry still written); a repeated id counts once. Answers `{topic, cancelled, stillOpen, logId, logSequenceNumber}`: `topic` is the whole topic as it is now, `cancelled` the items cancelled (`[{id, title, sub}]`, `sub` as it read before), `stillOpen` the open items still linked to it. To say it holds again: `PUT /api/solutions/topics/{topicId}` `{"holds":true}` (the items cancelled stay cancelled). |

Refusals of `proved-false`, checked in this order and before anything changes:

- 404 "Topic not found."
- 409 "Only an assumption can be proved false. Nothing was changed."
- 409 "That assumption was already proved false. Nothing was changed."
- 400 "Say why it no longer holds. The journal keeps the reason." (no `reason`, or only spaces)
- 400 "The item <id> is not an open item resting on this assumption. Nothing was changed." (an id that is
  not an open item linked to it)

### Waits

An item can WAIT ON another item of the same solution (a wait has a direction, so it is not a link).
An item never waits on itself, never waits on the same item twice, and a wait never closes a circle
(A waits on B waits on A): each is refused (409). Deleting an item takes its waits, both what it waited
on and what waited on it. To find the item to wait on, use `GET /api/solutions/{id}/link-targets` with
`targetKind=item`.

| Call | Note |
|---|---|
| `GET /api/solutions/items/{itemId}/waits` | `[{id, direction, itemId, title, projectName, statusCode, statusName, colorHex, closed, createdDate}]`. `id` is the WAIT's id (for the `DELETE`); `itemId`, `title`, `projectName` and the status fields are the OTHER item's; `closed` is true when that item's status is a closed one; `createdDate` is when the wait was made. `direction` is `waitsOn` (what this item waits on) or `blocks` (an item waiting on this one). The `waitsOn` rows come first, open before closed, then by title. 404 "Item not found." |
| `POST /api/solutions/items/{itemId}/waits` | `{dependsOnItemId}` -- this item now waits on that one; answers with this item's waits, as the `GET` above. |
| `DELETE /api/solutions/waits/{waitId}` | the wait goes; both items stay. Answers `{ok: true, deletedId}`; 404 "Wait not found." |
| `GET /api/solutions/{id}/waits` | every item of the solution that waits on at least one item: `[{itemId, waits, openWaits}]` -- `waits` how many it waits on, `openWaits` how many of those are still open (0 when all of them are closed). In no set order. |

Refusals of `POST /api/solutions/items/{itemId}/waits`:

- 400 "dependsOnItemId is required." (missing, 0 or negative)
- 404 "Item not found." (either item, or one you may not see)
- 409 "An item can only wait on an item of the same solution. Nothing was saved."
- 409 "An item cannot wait on itself. Nothing was saved."
- 409 "It already waits on that item. Nothing was saved."
- 409 "That would go round in a circle: the other item already waits, step by step, on this one. Nothing was saved."

### Many at once and Make into

Change one field on many items in one call, with a reason, instead of item by item. Every item and the
value are checked before the first write: one bad item or value and NOTHING changes.

| Call | Note |
|---|---|
| `POST /api/solutions/{id}/items/bulk` | `{itemIds:[...], field, value, reason}` -- one field changed on every item named. `itemIds` (numbers) is required: duplicates and ids of 0 or less are dropped, and at most 500 are left. `field` is one of the six below. `value` is a STRING (`"41"`, `"3"`), at most 400 characters. `reason` is required, at most 1,000 characters: it goes in the one log entry the change writes. Answers `{logId, itemCount}`. |
| `POST /api/solutions/{id}/promote` | `{sourceKind, sourceId, targetKind, projectId, title}` -- a NEW record made from another, the source kept as it was. See *Make into* below. Answers `{kind, id}` of the new record, for example `{"kind":"item","id":1305}`. |
| `GET /api/solutions/{id}/provenance?kind=&recordId=` | where a record came from and what was made from it. `kind` (`message`, `log`, `note`, `item` or `question`) and `recordId` are both required. Answers a list, oldest first: `[{direction, kind, id, title, conversationId, promotedDate, promotedByCode, promotedByName}]`. `direction` `"from"` = this record was made from that one; `"into"` = that one was made from this. Only records still there are listed; `title` of a log entry or a message is the first 200 characters of its text; `conversationId` is the thread for a message, else null; `promotedDate` is a date-time. A record nothing was made from or into answers `[]` (not 404). Only `promote` is remembered here: a question or decision made from a log entry shows as a link and as its `logId` instead. |

**The bulk fields**

| `field` | `value` | What changes |
|---|---|---|
| `status` | a status code (`todo`, `inprogress`, `blocked`, `done`, `cancelled`), any case | each item's status. A closed status stamps the item's completed date and an open one clears it. Unlike the one-item status call, a not-started parent or project is NOT moved to in progress. |
| `project` | a project id of this solution | each named item moves to the TOP level of that project, after what is there, with its whole sub-tree. A named item under another named item keeps its place under it. |
| `assignee` | a member code, any case; `""` = nobody | who has each item. The code must already be a member of the house (unlike elsewhere, your own code is not added on the fly). |
| `due` | exactly `yyyy-mm-dd`; `""` = no due date | each item's due date |
| `priority` | `"1"` to `"5"` | each item's priority |
| `topic` | a topic id of this solution | each item is LINKED to the topic, and so is the log entry |

What is written: ONE log entry of type `note`, by you, reading for example "**Changed 2 items: status to
Skipped.**" then a blank line and "Why: <your reason>". It is linked to every item it changed (for
`project` it also belongs to that project). An item already as asked is left alone: not counted, not
linked. `itemCount` counts only the items changed (sub-items that travel with a moved item are not
counted). When none needed changing the answer is `{"logId":0,"itemCount":0}` and nothing is written.

Refusals of a bulk change:

- 400 (nothing checked further): "field must be one of status, project, assignee, due, priority, topic." /
  "Choose the items to change." / "At most 500 items can be changed at once." / "Say why. The reason goes
  in the log." / "A reason can be at most 1,000 characters." / "That value is too long."
- 409, each ending "Nothing was changed.": "One of those items is not in this solution any more." /
  "That is not a status." / "Choose a project to move them to." (empty or not a number) / "That project
  is not in this solution." / "That is not a member." / "A due date is written yyyy-mm-dd." / "A priority
  is 1 to 5." / "Choose a topic." (empty or not a number) / "That topic is not in this solution."

**Make into** (`POST .../promote`). The pairs the code allows, and nothing else:

| `sourceKind` (`sourceId` is its id) | `targetKind` |
|---|---|
| `message` (a conversation message's `id`, from `GET /api/solutions/conversations/{convId}/messages`) | `item`, `note` or `question` |
| `log` | `item` or `note` |
| `note` | `item` |
| `item` | `note` |

A decision or a question from a log entry is not made here: use `POST /api/solutions/log/{logId}/decision`
and `POST /api/solutions/log/{logId}/question` (see *Questions and decisions*).

- `sourceKind`, `sourceId` and `targetKind` are required. `projectId` is optional: the project the new
  record goes in, by default the source's own (a message has none). An item must end up with one; a note
  may have none.
- `title` is optional, at most 400 characters. Left out or blank, it is the source's first line with text,
  without `**` bold marks, cut at its first sentence (or near 197 characters, with "...") when over 200
  characters. A new note's title over 300 characters is cut to 297 plus "...".
- The WHOLE source text goes in the new record: an item's `detail`, a note's `body`, a question's `detail`.
  A note's source text is its title and current body; an item's is its title and detail. A message's text
  starts with "*From <name>, <mm/dd/yyyy>, in the thread:*".
- The new item is top-level at the end of its project, status `todo`, priority 3, nobody assigned, no due
  date. The new note is version 1, of the kind `reference` (the first kind when there is no `reference`), with the change note "Made from a thread
  message" (or "a log entry", "an item"); change its type with `PUT /api/solutions/notes/{noteId}`. The
  new question is open, asked by you, with no owner.
- The source is not changed. The new record is linked to its source; a message cannot be linked, so only
  `provenance` remembers it.
- Making the same source into the same kind again is refused while the first one still exists (delete it
  and you may make it again).

Refusals of a promote:

- 400: "sourceKind must be one of message, log, note, item." / "targetKind must be one of item, note,
  question." / "Say which record to make it from (sourceId)." / "A title can be at most 400 characters."
- 409, each ending "Nothing was added.": "That cannot be made into that. A message becomes an item, a note
  or a question, a log entry an item or a note, a note an item, an item a note." (any other pair) / "That
  is not in this solution any more." (no such source in this solution) / "That was already made into an
  item (#1305)." (or "a note (#..)", "a question (#..)": the id is the earlier record) / "That project is
  not in this solution." / "Choose the project the item goes in." / "That has no text to make anything
  of." / "That question is already open." (a question with exactly that text is open)

**Saved views.** A saved view is a name for a set of Items-tab filters. They matter only to the page, but
you may keep your own. Each member has their own: you see, replace and delete only the views saved under
YOUR member code, never your human's, and nobody sees yours.

| Call | Note |
|---|---|
| `GET /api/solutions/{id}/views` | your views of one tab, in their order: `[{id, solutionId, scope, name, filter, sortOrder, createdDate, modifiedDate}]` (`filter` is the string as saved). Query `scope`: `items`, the default and the only one. `[]` when you have none. |
| `POST /api/solutions/{id}/views` | `{scope, name, filter}` -- `scope` `items` (the default when left out); `name` required, at most 100 characters; `filter` a JSON object sent AS A STRING, at most 2,000 characters, for example `"{\"project\":\"41\",\"status\":\"\",\"assignee\":\"rex\",\"hideClosed\":true,\"view\":\"tree\"}"`. Only its first and last characters (`{`, `}`) are checked. The page reads `project` (a project id), `status` and `assignee` (codes), `hideClosed` and `view` (`tree` or `now`). A view of the same name, in any case, is replaced (its name and filter). At most 30 per member per tab per solution. Answers your views of that tab, as the GET does. |
| `DELETE /api/solutions/views/{viewId}` | one of YOUR views. Answers `{"ok":true,"deleted":true}`. Someone else's view or a missing id: 404 "View not found." |

Refusals of a view: 400 "Views can be saved for the items tab (scope=items)." (any other `scope`, GET or
POST) / "A view needs a name." / "A view's name can be at most 100 characters." / "filter must be a JSON
object, as a string." / "A view's filters can be at most 2,000 characters." A `filter` sent as an object
rather than a string is a wrong JSON type (400 with no message). 409 "You have 30 saved views here
already. Delete one first. Nothing was saved."

### Since you were last here

What OTHER people and agents changed in a solution since your last session -- items, log entries,
notes, files, conversation messages, terms, topics, questions and decisions -- newest first. A record
shows ONCE, as it is now, dated by its latest change; a conversation message is one row each. Your own
changes are left out (a change whose author is not known is shown).

Where "since" starts: each `visit` moves it to the time of your previous visit, but only when that
previous visit was more than an hour before; within the hour the same "since" holds. Until one of your
visits comes more than an hour after the visit before it -- and if you never called `visit` at all --
"since" is a week back from now and `firstVisit` is `true`. `caught-up` moves "since" to now.

| Call | Note |
|---|---|
| `POST /api/solutions/{id}/visit` | call it at the START of a working session. Counts the visit and answers the first page (`offset` 0, 50 changes): `{since, firstVisit, changes, counts, offset, pageSize, total}`. It takes no query parameters; read further pages with `GET /api/solutions/{id}/since`. |
| `GET /api/solutions/{id}/since` | the same answer without counting a visit. Optional `offset` (default 0) and `pageSize` (default 50, at most 500; more is cut to 500). |
| `POST /api/solutions/{id}/caught-up` | you have read it all: "since" starts now. Answers the same shape, with `since` now, `firstVisit: false` and (almost always) no changes. |

The answer: `since` is a date-time (it may carry a fraction of a second); `total` and `counts.total` count
every change since then; `counts` also has `kind:<kind>` for each kind present. Each change is
`{kind, id, title, sub, action, who, whoName, changedDate}`; `who` is a member code and `whoName` its
name, either null when not known.

| `kind` | `id` is | `title` / `sub` | `action` |
|---|---|---|---|
| `item` | the item | title / project name | `added` (made since) or `changed` |
| `log` | the entry | `#<sequenceNumber> <first 200 characters>` / its log type | `wrote` or `corrected` |
| `note` | the note | title / its note type | `added` or `changed` |
| `file` | the file | title, else file name / file name | `added` or `changed` |
| `message` | the CONVERSATION | the thread's title (`Untitled thread` when it has none) / the message's first 200 characters | `wrote` |
| `term` | the term | the term / the first 200 characters of its definition | `added` or `changed` |
| `topic` | the topic | its name / the first 200 characters of its summary | `added` or `changed` |
| `question` | the question | its text / `Open question`, `Answered` or `Dropped` | `asked` or `changed` |
| `decision` | the decision | its title / the first 200 characters of its outcome | `decided` or `changed` |

### Questions and decisions

A QUESTION is something the solution needs answered: its `text`, a `detail` and an `ownerCode` (who
should answer it). It is `open` until it is `answered` (by a decision, or by hand) or `dropped`.

A DECISION is what was decided: a one-line `title`, the `context` (why it came up), the `options`
weighed and the `outcome`, with a status: `proposed` (still to be agreed), `accepted`, or `superseded`.
Recording one also writes a journal entry of type `decision` ("**Decided:** <title>", or "**Proposed:**
<title>", then the outcome), linked to it, and the decision carries it as `logId` and
`logSequenceNumber`, so the log keeps its single "what happened, in order". When you decide something,
RECORD IT HERE, not only in a log entry, and link it (`links`) to the questions it answers and the items
it settles.

A changed mind is never an edit of the outcome: `POST .../supersede` records a NEW accepted decision;
the old one becomes `superseded`, pointing at the new one (`supersededById`), and the two are linked.
You can never set `statusCode: "superseded"` yourself (400).

A decision CLOSES what it settles: `GET .../open-links` lists the linked questions still open and the
linked items not closed; `POST .../settle` with the ones to close answers those questions BY the decision
and marks those items `done`, and writes one journal entry of type `result` saying so. Only records that
are linked and still open can be named (else 400, and nothing changes); a proposed or superseded decision
closes nothing (409). Many decisions are already in the log as Decision entries:
`POST /api/solutions/log/{logId}/decision` makes one a decision in place, and
`POST /api/solutions/log/{logId}/question` does the same for a question.

Both lists page the same way: `offset` (default 0), `pageSize` (default 50, at most 500; more is cut to
500), `search` (at most 200 characters are used). `counts` holds `total`
(what matches your `status` and `search`), `all` (every question, or every decision, of the solution)
and `status:<code>` (what matches your `search`, per status; a status with none is left out).

| Call | Note |
|---|---|
| `GET /api/solutions/{id}/questions` | one page, open first, then answered, then dropped, the most recently changed first: `{questions, counts, offset, pageSize, total}`. Optional `status` (`open`, `answered`, `dropped`; any other answers 400 "A question's status is one of open, answered, dropped."), `search` (question and detail), `offset`, `pageSize`. Each `{id, solutionId, text, detail, truncated, statusCode, askedByCode, askedByName, ownerCode, ownerName, answeredByDecisionId, answeredByTitle, answeredDate, logId, logSequenceNumber, linkCount, modifiedBy, createdDate, modifiedDate}`. In the list `detail` is a 300-character preview (`truncated: true` when there is more). `logId` / `logSequenceNumber` are the journal entry it was made from, else null. Dates are date-times. |
| `GET /api/solutions/questions/{questionId}` | the whole question, same fields, `detail` in full |
| `POST /api/solutions/{id}/questions` | `{text, detail, ownerCode, links}` -- `text` required, at most 800 characters; line breaks and tabs in it become spaces. `ownerCode` a member code (409 lists the valid codes). `links`: `[{kind, id}]`, records to link it to at once (kinds as in *Links and topics*). Answers the question. |
| `PUT /api/solutions/questions/{questionId}` | `{text, detail, ownerCode}`. A merge: a field you leave out is kept; `""` clears `detail` or `ownerCode`; `text` cannot be blanked. `links` is ignored here. Answers the question. |
| `POST /api/solutions/questions/{questionId}/status` | `{statusCode}` -- `open`, `answered` (by hand: stamps `answeredDate`) or `dropped`. `open` and `dropped` clear the answer (`answeredByDecisionId`, `answeredDate`). Answers the question. |
| `DELETE /api/solutions/questions/{questionId}` | the question and its links go; the decision that answered it and the journal entry it came from stay. Answers `{"ok":true,"deletedId":4}`. |
| `POST /api/solutions/log/{logId}/question` | no body. The log entry (any type) becomes an open question, in place, asked by the entry's author: its first line with text (no `**` marks; over 300 characters, cut at its first sentence) is the question, the rest its detail (the whole entry when the line was cut). The entry is not changed; the two are linked. Answers the question. |
| `GET /api/solutions/{id}/decisions` | the register, one page, proposed first, then accepted, then superseded, the most recently decided first: `{decisions, counts, offset, pageSize, total}`. Optional `status` (`proposed`, `accepted`, `superseded`, or `current` = not superseded; any other answers 400 "A decision's status is one of proposed, accepted, superseded."), `search` (title, context, options, outcome), `offset`, `pageSize`. Each `{id, solutionId, title, context, options, outcome, truncated, statusCode, decidedDate, decidedByCode, decidedByName, logId, logSequenceNumber, supersededById, supersededByTitle, supersedesId, supersedesTitle, answeredCount, linkCount, modifiedBy, createdDate, modifiedDate}`. In the list `outcome` is a 300-character preview, `context`, `options`, `supersedesId` and `supersedesTitle` are null, and `truncated: true` means the GET of one has more. `answeredCount` = questions it answered. Dates are date-times. |
| `GET /api/solutions/decisions/{decisionId}` | the whole decision, with `supersedesId` / `supersedesTitle` when it replaced one |
| `POST /api/solutions/{id}/decisions` | `{title, context, options, outcome, statusCode, decidedDate, links}` -- `title` required, at most 400 characters; line breaks in it become spaces. `statusCode` `accepted` (the default) or `proposed`. `decidedDate` a date; an accepted decision without one is dated now, a proposed one stays null. Decided by you. Writes its journal entry (see above). `links` as for a question. Answers the decision. |
| `PUT /api/solutions/decisions/{decisionId}` | same fields except `links` (ignored), a merge: a field left out is kept; `""` clears `context`, `options` or `outcome`; `title` cannot be blanked. Accept a proposal with `{"statusCode":"accepted"}` (it is dated now if it had no date). Never touches its journal entry. Answers the decision. |
| `POST /api/solutions/decisions/{decisionId}/supersede` | `{title, context, options, outcome, decidedDate, links}` -- the NEW decision (`title` required; `statusCode` is ignored, it is always `accepted`). It gets its own journal entry; the old one becomes `superseded` and the two are linked. Questions the old one answered stay answered by it. Answers the NEW decision. |
| `GET /api/solutions/decisions/{decisionId}/open-links` | `[{kind, id, title, sub}]`, questions first: `kind` `question` (`sub` "Question") or `item` (`sub` "<project> \| <status name>") |
| `POST /api/solutions/decisions/{decisionId}/settle` | `{questionIds:[...], itemIds:[...]}` -- close them: each question becomes `answered` by this decision, each item `done` (a not-started parent or project moves to in progress, as with the one-item status call). One journal entry of type `result`, "**Closed by the decision:** <title>" then one line per record. Answers `{decision, closed, stillOpen, logId, logSequenceNumber}`: `closed` and `stillOpen` are lists shaped like `open-links`; `logId` is the new entry. Duplicate ids are ignored. |
| `DELETE /api/solutions/decisions/{decisionId}` | the questions it answered open again; the decision it superseded is `accepted` again; its links go; its journal entry (and any `result` entry from a settle) stays, and items it marked done stay done. Answers `{"ok":true,"deletedId":9}`. |
| `POST /api/solutions/log/{logId}/decision` | no body. The log entry (any type) becomes an `accepted` decision, in place, decided by the entry's author on the entry's date: its first line with text (no `**` marks; over 200 characters, cut at its first sentence) is the title, and the whole entry is the outcome when it has more than that line. No new journal entry is written: `logId` is this entry. The two are linked. Answers the decision. |

**Links on a new question or decision.** Each link needs a kind and an id (400 "Each link needs a kind
and an id.", or "kind must be one of item, note, log, file, term, topic, question, decision."), checked
before anything is saved. Whether the record exists is checked only AFTER the question or decision is
saved: a link to a record not in this solution, or the same record twice, answers 409 ("The record to
link to is not in this solution. Nothing was saved." / "Those two are already linked. Nothing was
saved.") but the question or decision IS saved, with the links before the bad one. Do not resend it (that
is refused as a duplicate): find it in the list and add the missing links with `POST /api/solutions/{id}/links`.

Refusals:

- 400: "The question is required." / "A decision title is required." / "The new decision's title is
  required." (supersede) / "statusCode is required." (question status) / "A decision is superseded by
  recording a newer one (supersede), never by setting its status." / "Name at least one open question
  or item linked to this decision." / "The question 4 is not an open question linked to this decision.
  Nothing was changed." (or "The item 1200 is not an open item ...") / `decidedDate` "decidedDate must
  look like 2026-09-17 or 2026-09-17T14:30."
- 404: "Question not found.", "Decision not found.", "Log entry not found."
- 409 for a question: "A question can be at most 800 characters. Put the rest in its detail. Nothing was
  saved." / "That question is already open in this solution. Nothing was saved." (the same text is open
  already; from a log entry the message ends "Nothing was added.") / "A question cannot be blank. Nothing
  was saved." / "That log entry is already a question. Nothing was added." / "That log entry has no text
  to make a question of. Nothing was added." / "There is no member code '...'. The member codes are: ..."
- 409 for a decision: "A decision title can be at most 400 characters. Put the rest in the outcome.
  Nothing was saved." / "This solution already has that decision. Nothing was saved." (a decision not
  superseded has that title; a supersede may reuse the old one's title) / "A decision title cannot be
  blank. Nothing was saved." / "A superseded decision keeps that status: record a new decision instead.
  Nothing was saved." (a PUT with `statusCode` on a superseded one) / "That decision was already replaced
  by a newer one. Nothing was added." (supersede) / "That log entry is already a decision. Nothing was
  added." / "That log entry has no text to make a decision of. Nothing was added."
- 409 for settle: "A superseded decision closes nothing. The decision that replaced it does. Nothing was
  changed." / "Accept the decision first. A proposal closes nothing. Nothing was changed."

### Deleting

A delete is permanent. Prefer the status `cancelled` when work was simply dropped.

| Call | Note |
|---|---|
| `DELETE /api/solutions/projects/{projectId}` | takes its items and sub-items, with their links and waits. Log entries and notes are kept (they no longer name the project or the item), and so are files (they no longer name the item). Answers `{ok: true, deletedId}`. |
| `DELETE /api/solutions/items/{itemId}` | takes the item's whole sub-tree, with their links and waits; log entries and files that named them are kept. The answer says how many went: `{ok: true, deletedId, itemsRemoved}`. |
| `DELETE /api/solutions/log/{logId}` | its links go; runbook steps, questions and decisions made from it stay but no longer name it. Answers `{ok: true, deletedId}`. Numbers are not renumbered, but deleting the NEWEST entry frees its number: the next entry appended takes it again. |
| `DELETE /api/solutions/notes/{noteId}` | takes its steps, every saved version and its links. Answers `{ok: true, deletedId}`. The log entries its steps came from stay. |
| `DELETE /api/solutions/notes/{noteId}/steps/{stepId}` | the later steps are renumbered. Answers the whole note. |

Every other delete is in its own section: links, topics, kinds of part, waits, questions, decisions,
terms, files, saved views, a conversation (taken off the solution), and the parts of a testing
checklist or a configuration.

### Files

Attach a file to the solution, optionally to one item. Upload is `multipart/form-data`, not JSON
(anything else is answered 415). A file keeps VERSIONS: when a document changes, upload a new version
of it, not a second file.

| Call | Note |
|---|---|
| `GET /api/solutions/{id}/files` | every file of the solution, newest first, a plain list (not paged). Optional `?itemId=` for one item's files (0 = all). |
| `POST /api/solutions/{id}/files` | multipart fields: `file` (required, not empty), `title` (at most 300; absent means the file's name), `notes` (at most 1,000; longer is cut), `itemId` (an item of this solution). At most 50 MB (52,428,800 bytes). The stored name is made safe: the folder part is dropped, any character other than letters, digits, `.`, `_`, space, `(`, `)` and `-` becomes `_`, leading dots go, and it keeps at most its last 120 characters -- `originalFileName` shows the name as stored. The same bytes uploaded twice make two files; nothing checks for a copy. Answers the file. |
| `GET /api/solutions/files/{fileId}` | the file's details |
| `PUT /api/solutions/files/{fileId}` | JSON `{title, notes, itemId}`, a merge: a field you leave out keeps its value, a blank `title` is kept, `""` clears `notes`, `"itemId":0` takes it off its item. The bytes never change here. Answers the file. |
| `GET /api/solutions/files/{fileId}/content` | the current bytes, always as a download: `Content-Type: application/octet-stream`, `Content-Disposition: attachment` named by its `originalFileName`, `X-Content-Type-Options: nosniff`, whatever the file is. 404 `"File not found."` when the bytes are missing on the server. |
| `DELETE /api/solutions/files/{fileId}` | detaches it from the solution: the file, its earlier versions, the way a person filed it and its links are all gone, and no version can be downloaded through this API again. The bytes, the current ones and every earlier version's, are moved to the server's trash, not erased (a person can still recover them). Answers `{ok: true, deletedId}`. |
| `POST /api/solutions/files/{fileId}/versions` | a NEW VERSION of the file -- multipart fields `file` (required, not empty, at most 50 MB) and `changeNote` (what changed, at most 500). The file keeps its id, title, notes, item, links and filing and takes the new bytes, name, type and size (kept on the server, `storageCode` `"local"`), and you become its `uploadedByCode`; what it had becomes an earlier version, whose bytes stay downloadable. Answers the file. |
| `GET /api/solutions/files/{fileId}/versions` | `{currentVersion, versions:[{id, fileId, versionNumber, originalFileName, storedFileName, contentType, sizeBytes, changeNote, uploadedDate, uploadedByCode, uploadedByName, solutionId}]}` -- the EARLIER ones, newest first (`[]` and `currentVersion: 1` for a file never replaced). `changeNote` says what the version after it changed. `uploadedDate` is when that version's bytes arrived. `storedFileName` is where the server keeps the bytes and `solutionId` is always null in this list: ignore both. |
| `GET /api/solutions/files/versions/{versionId}/content` | an earlier version's bytes, as a download with the same headers. `versionId` is the version's `id` from the list above, NOT its `versionNumber`. |

**A file** serializes as `{id, solutionId, itemId, itemTitle, title, originalFileName, storageCode,
..., storedFileName, contentType, sizeBytes, notes, uploadedByCode, uploadedByName,
createdDate, modifiedDate}`. `itemId` / `itemTitle` are null for a file on no item, `storageCode` is
`"local"`, one more id field next to it is always null (ignore it), `storedFileName` is where the server keeps the bytes (you
never need it), `contentType` is the type your upload declared, `sizeBytes` a number, and
`uploadedByCode` is who uploaded the CURRENT version. Dates are date-times. The current version
number is not on the file; `GET .../versions` gives it as `currentVersion`.

Refusals:

- 400 `"A file is required."` -- no `file` part, or an empty one. `"A file may be at most 50 MB."`
  `"What changed can be at most 500 characters."` -- a new version's `changeNote`.
- A request over 60 MB in all is stopped by the server before Solutions sees it: 413 with no
  `message`, and the connection may be closed.
- 400 `"Nothing to change."` -- a `PUT` with no body.
- 404 `"File not found."`, `"Version not found."`, or `"Solution not found."` for a solution you were
  not given.
- 409 `"That item does not belong to this solution."` -- an `itemId` from elsewhere.

### Conversations

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/conversations` | the threads of a solution, the most recently active first. Each row is shown below under "A thread". No paging: every thread comes back. |
| `POST /api/solutions/{id}/conversations` | `{title, message}` -- opens a thread with your first message. `title` is required (400 `A title is required.`), at most 200 characters (a longer title is cut off without an error). `message` is optional: leave it out and the thread starts empty. The answer is the new thread's row (below), so it carries its `conversationId`. |
| `GET /api/solutions/conversations/{convId}/messages?since=<lastMessageId>` | the messages with an `id` GREATER than `since`, oldest first. `since` is a whole number; leave it out (or send 0) for the whole thread. There is no paging and no limit. An `id` past the newest message answers `[]`. Reading marks the thread read for you (see below). |
| `POST /api/solutions/conversations/{convId}/messages` | `{body}` -- required (400 `Some text is required.`). No length limit; leading and trailing spaces are trimmed. The answer is the new message (below). Posting to a closed thread opens it again. |
| `DELETE /api/solutions/{id}/conversations/{convId}` | takes the thread off this solution: it unlinks it, it does not delete it. The thread and its messages are kept for the owner, but you will not see the thread again (every call on it answers 404 `Conversation not found.`), unless it is also part of another solution shared with you. Answers `{"ok":true,"unlinkedId":<convId>}`. Only for a thread opened by mistake. |

**A thread** (a row of the list, and the answer to opening one):

| Field | What it is |
|---|---|
| `conversationId` | the thread's id |
| `title` | its title |
| `status` | `active` or `closed` |
| `createdDate`, `modifiedDate` | date-times (`2026-09-17T14:30:00`, often with a fraction of a second). `modifiedDate` moves with every new message and every close or reopen. |
| `messageCount` | how many messages it holds |
| `lastSender` | who wrote the newest message (a member code, for example your client code or `tony`); `null` when the thread is empty |
| `awaitingFrom` | whose turn it is: `tony` (a person), your own client code (you), or `null` (nobody) |
| `awaitingTonyCount` | how many messages are still waiting on a person |

**A message** (the list of messages, and the answer to posting one):

| Field | What it is |
|---|---|
| `id` | the message id. Pass the newest one you have seen as `since` next time. |
| `conversationId` | its thread |
| `sender` | who wrote it: your client code, `tony` (a person), or the house's own code (for example `felix`) |
| `body` | the text |
| `createdDate` | a date-time, as above |
| `awaitingFrom` | whom this message is still waiting on: `tony`, your client code, or `null` |

- `awaitingFrom` on a thread is whose turn it is. After you post, it is `tony`: a person's turn.
  When a person answers, it becomes your client code: your turn -- **until you read the messages**,
  which marks them read and clears it. So note the answer when you read it; the flag will not
  remind you twice. Reading clears every flag in the thread that names you, whatever `since` you
  pass, even when the call answers `[]`. It never clears a flag that names a person.
- When a solution is shared with more than one agent, a person's answer waits on the first agent
  the solution is shared with. If it is not you, `awaitingFrom` will not name you; read the
  thread you asked in anyway.
- Remember the `id` of the last message you saw and pass it as `since` next time.
- You cannot edit or delete a message once posted, or rename a thread -- a conversation is the
  record of what was asked and answered, and only the owner may correct it. If you got something
  wrong, post a correction. These calls exist for the owner and **answer 403 to you**:
  `PUT /api/solutions/conversations/{convId}` (rename; `Only a person may rename a conversation.`),
  `PUT /api/solutions/conversations/{convId}/messages/{msgId}` (change a message; `Only a person may change a message.`),
  `DELETE /api/solutions/conversations/{convId}/messages/{msgId}` (remove a message; `Only a person may remove a message.`).
- A thread's `status` is `active` or `closed`. The owner closes (resolves) a thread when the
  question is settled; closing clears every `awaitingFrom` in it, so a closed thread waits on
  nobody. You cannot resolve or reopen a thread:
  `PUT /api/solutions/conversations/{convId}/resolved` answers 403 to you
  (`Only a person may resolve or reopen a conversation.`; a body without `resolved` is a 400 first).
  But posting a new message to a closed thread opens it again (`status` goes back to `active`), so
  ask there if the topic comes back.
- Refusals: a solution that is not shared with you answers 404 `Solution not found.`; a thread that
  does not exist, or is not part of a solution shared with you, answers 404 `Conversation not found.`
  A `since` that is not a whole number is a plain 400 with no `message`.

A good question names the item it is about, says what you tried, and says what you
need to know to continue. One question per thread.

### Testing checklists

A solution can hold testing checklists. A checklist is a grid: test CASES, gathered into GROUPS,
down the side; COLUMNS across the top, one per level of testing. A new checklist starts with two
columns, `Manual` and `Unit tested`; add others (for example `End to end`). Every cell -- a
case in a column -- is ticked, SKIPPED, or neither, and a tick or a skip records who and when. Tick a
cell only for what you have actually seen pass, at that level. Skip a cell for a test that will
deliberately not be run at that level (for example, no unit test is possible): a skipped cell is
neither a pass nor a fail, and it drops out of every "N of M" -- count what is left to run as
`cellCount - skippedCount`. A cell is never both: skipping unticks it, ticking unskips it.

A case can also be marked FAILING, with a result note that says what is failing. The case's own
`note` is the set-up note (how to run the test); the result note is what happened when it was run.

Every checklist has a `kind`. `dev` (the default) is the developer's checklist, shown on the Testing tab.
`qa` is what a QA tester does from the outside, grouped by area (for example 835, 837, Deposits), shown
on the QA tab and printed as a handout. A `qa` checklist starts with one column, `Passed`, instead of
Manual and Unit tested. Write each QA case as what to do and what should happen, in a tester's words,
and put the test data and where to look in its `note`. A checklist's kind is set when it is made and
never changes.

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/test-plans?kind=qa` (or `dev`, or no kind for both) | the checklists, in their `sortOrder`. Each row is shown below under "A checklist on the list". Any other `kind` is refused: 400 `A checklist kind is dev or qa, not "<what you sent>".` |
| `POST /api/solutions/{id}/test-plans` | `{title, description, kind}` -- `title` required (400 `A title is required.`), at most 200 characters; `description` optional, at most 2000; `kind` is `dev` (default) or `qa`, anything else is refused (400 `A checklist kind is dev or qa, not "<what you sent>".`). A new checklist goes last on the list. Answers the whole grid. |
| `GET /api/solutions/test-plans/{planId}` | the whole grid (below) |
| `PUT /api/solutions/test-plans/{planId}` | `{title, description, sortOrder}` -- send only what changes. A blank `title` keeps the old one; `description: ""` clears it. `kind` is ignored here. |
| `DELETE /api/solutions/test-plans/{planId}` | the checklist and everything in it: columns, groups, cases, ticks, skips and results. Answers `{"ok":true,"deletedId":<planId>}`. |
| `POST /api/solutions/test-plans/{planId}/columns` | `{title}` -- required (400 `A column name is required.`), at most 100 characters; it goes last. The same name twice in one checklist is refused: 409 `This checklist already has a column named "<title>".` |
| `PUT /api/solutions/test-plans/columns/{columnId}` | `{title, sortOrder}` -- send only what changes; a blank `title` keeps the old one. Renaming keeps every tick; renaming to a name the checklist already has is the same 409. |
| `DELETE /api/solutions/test-plans/columns/{columnId}` | takes that column's ticks and skips; the last column cannot be removed: 409 `A testing checklist needs at least one column.` |
| `POST /api/solutions/test-plans/{planId}/groups` | `{title}` -- required (400 `A group name is required.`), at most 200 characters; it goes last |
| `PUT` / `DELETE /api/solutions/test-plans/groups/{groupId}` | PUT `{title, sortOrder}` -- send only what changes; a blank `title` keeps the old one. A delete takes the group's cases with their ticks, skips and results. |
| `POST /api/solutions/test-plans/groups/{groupId}/cases` | `{text, note}` -- `text` required (400 `Some text is required.`), at most 1000 characters: what to try and what should happen. `note` optional, at most 2000. The case goes last in its group and prints. |
| `PUT /api/solutions/test-plans/cases/{caseId}` | `{text, note, groupId, sortOrder, includeInPrint}` -- send only what changes. A blank `text` keeps the old one; `note: ""` clears it. `groupId` moves it to another group of the SAME checklist (it goes last there unless you send `sortOrder`); a group of another checklist, or one that does not exist, is refused: 409 `A test case can only move to a group in the same checklist.` `includeInPrint: false` leaves the case off the printed checklist (every case prints unless told otherwise). |
| `DELETE /api/solutions/test-plans/cases/{caseId}` | the case with its ticks, skips and result |
| `PUT /api/solutions/test-plans/cases/{caseId}/checks/{columnId}` | ONE of: `{checked: true}` / `{checked: false}` to tick or untick, or `{skipped: true}` / `{skipped: false}` to skip or unskip. Both at once, or neither, is refused: 400 `Send checked (true or false) or skipped (true or false) -- one of the two.` Skipping unticks the cell; ticking unskips it. Unticking leaves a skip alone and unskipping does not tick. A column of another checklist: 409 `That column belongs to a different checklist.` |
| `PUT /api/solutions/test-plans/cases/{caseId}/result` | `{failing, note}` -- send only what changes, at least one (else 400 `failing (true or false) or note is required.`). `failing: true` marks the case failing (its `failingDate` is when it first went failing), `false` clears the mark and the date. `note` says what is failing, at most 4000 characters; `""` clears it. |
| `POST /api/solutions/test-plans/{planId}/clear` | `{columnId}` unticks one column (failing marks stay). `{}`, or no body at all, unticks the whole checklist and clears every failing mark (result notes stay), to run the tests again. Skipped cells stay skipped either way. A column of another checklist: 409 `That column belongs to a different checklist.` |
| `PUT /api/solutions/test-plans/groups/{groupId}/case-order` | `{ids: [caseId, ...]}` -- the group's cases in their new order. An id that is not a case of this group is ignored, not refused. Cases you leave out keep their order, after the ones you list. `ids` missing: 400 `ids (the new order) is required.` |
| `PUT /api/solutions/test-plans/{planId}/group-order` | `{ids: [groupId, ...]}` -- the checklist's groups in their new order. Same rules: an id that is not a group of this checklist is ignored, groups left out follow the ones you list, and a missing `ids` is the same 400. |

Refusals common to every call above: a checklist, column, group or case that does not exist answers
404 `Testing checklist not found.`, `Column not found.`, `Group not found.` or `Test case not found.`;
one on a solution not shared with you answers 404 `Solution not found.` A title or text longer than
its limit is cut off without an error.

**A checklist on the list** (`GET /api/solutions/{id}/test-plans`): `id`, `solutionId`, `title`,
`description` (`null` when empty), `sortOrder`, `kind`, `groupCount`, `caseCount`, `levelCount` (columns),
`checkedCount`, `skippedCount`, `cellCount` (cases x columns), `createdDate`, `modifiedDate` and
`modifiedBy` (the member code that last saved the checklist itself). The list has no `failingCount`;
read the grid for that.

**The whole grid.** Every call except the list and a checklist delete answers with the WHOLE grid, so
you always hold the current counters. Its fields:

- The checklist: `id`, `solutionId`, `title`, `description`, `sortOrder`, `kind`, `caseCount`,
  `checkedCount`, `skippedCount`, `cellCount` (cases x columns), `failingCount`, `modifiedDate`,
  `modifiedBy`, `columns`, `groups`, and `counts` (the raw counters behind all the others; you do
  not need it).
- Each column (in `columns`, left to right): `id` (the column id every tick, skip and clear uses),
  `levelId` (the shared id of the column's name, not needed), `title`, `sortOrder`, `checkedCount`,
  `skippedCount`.
- Each group (in `groups`, top to bottom): `id`, `title`, `sortOrder`, `caseCount`, `checkedCount`,
  `skippedCount`, `failingCount`, `checkedByColumn` and `skippedByColumn` (`{"<columnId>": count}`,
  one entry per column, the key a string), and `cases`.
- Each case (in its group's `cases`, top to bottom): `id`, `groupId`, `text`, `note`, `sortOrder`,
  `includeInPrint`, `failing`, `failingDate`, `resultNote`, `resultDate` (when the result was last
  changed), `resultByName` (who changed it), `checks` -- ONLY the ticked cells, each
  `{caseId, columnId, checked, checkedDate, checkedByCode, checkedByName}` -- and `skips` -- ONLY the
  skipped cells, each `{caseId, columnId, skippedDate, skippedByCode, skippedByName}`. A cell in
  neither list is not ticked and not skipped. A case never run has `failing: false` and `null` result
  fields.

Every date here is a date-time (`2026-09-21T10:15:00`, often with a fraction of a second), or `null`.

### Configuration

A solution's configuration: every property that matters, in GROUPS, with its value in each
ENVIRONMENT (Local, QA, Production to begin with; more may be added) and WHERE that value is read
from. When a property is set in several places, `source` is the one that WINS (the effective
value) and `alsoSetIn` lists the others. Record the EFFECTIVE value -- never a commented-out or
declared-but-overridden one.

Each property has a `typeCode`: `text`, `path`, `host`, `port`, `url`, `number`, `flag` or
`secret` (the `types` list in the tab names them all). Case does not matter; leave it out or send
`""` when adding and the property is `text`. A **secret never stores a value** -- whatever you send
as `value` is thrown away; only `source`, `alsoSetIn` and `note` are kept. Use `secret` for anything
restricted. A blank means UNKNOWN, never "same as the default" -- leave a value blank rather than
guess, and say what you know in `note`.

The environments come by themselves: adding the first group or property to a solution that has no
environments gives it Local, QA and Production first. A solution's environment names are unique,
ignoring case, and so are its property keys.

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/config` | the whole tab (below) |
| `POST /api/solutions/{id}/config/environments/defaults` | no body. Adds Local, QA and Production, in that order -- only when the solution has no environments yet. When it has any, nothing changes and you still get the tab (no refusal) |
| `POST /api/solutions/{id}/config/environments` | `{title}` -- required, at most 100 characters. Added at the end |
| `PUT /api/solutions/config/environments/{envId}` | `{title, sortOrder}` -- send only what changes; `title` left out or `""` keeps the name. Renaming keeps every value in the column |
| `DELETE /api/solutions/config/environments/{envId}` | takes that environment's values (and their other places); the last environment cannot go (409) |
| `POST /api/solutions/{id}/config/groups` | `{title}` -- required, at most 200 characters |
| `PUT /api/solutions/config/groups/{groupId}` | `{title, sortOrder}` -- send only what changes; `title` left out or `""` keeps it |
| `DELETE /api/solutions/config/groups/{groupId}` | takes its properties and every value of them; the environments stay |
| `POST /api/solutions/config/groups/{groupId}/properties` | `{key, description, typeCode}` -- `key` required (at most 200), unique within the solution; `description` at most 2000. Added at the end of the group |
| `PUT /api/solutions/config/properties/{propertyId}` | `{key, description, typeCode, groupId, sortOrder}` -- send only what changes. `key` or `typeCode` left out or `""` keeps it; `description: ""` clears it. `groupId` moves it to another group of the SAME solution (to the end of it unless you send `sortOrder`). Changing to `secret` wipes the stored values (`source`, `alsoSetIn` and `note` stay) |
| `DELETE /api/solutions/config/properties/{propertyId}` | takes its value in every environment |
| `PUT /api/solutions/config/properties/{propertyId}/values/{envId}` | `{value, source, alsoSetIn: [..], note}` -- ONE cell; see below |

**One cell.** `value` (at most 2000 characters), `source` (where it is read from, at most 200),
`note` (at most 2000) and `alsoSetIn` (a list of the other place names, each at most 200). A field
you leave out keeps what the cell has; `""` clears that field and `[]` clears the other places.
Everything is trimmed. A place name cannot contain `|`. A place equal to `source`, or named twice,
is kept once (as `source`). Place names are shared: a name already in use is reused, a new one is
created. A cell left with no value, no source, no note and no other place is removed. The
environment must belong to the same solution as the property.

Every call answers with the whole tab:

- `solutionId`
- `environments`: `[{id, environmentNameId, title, sortOrder}]` in order. `id` is the `envId` the
  paths take; `environmentNameId` is the shared name it uses (ignore it).
- `groups`: `[{id, title, sortOrder, propertyCount, states, properties}]`. `states` counts the
  group's properties by `matchState`, only the states that occur: `{"all": 3, "incomplete": 1}`.
- each property: `{id, groupId, key, description, typeCode, typeName, secret, matchState, setCount,
  sortOrder, values}`. `setCount` is how many environments have a value.
- `values`: one cell per environment that has anything in it, `{environmentId, value, source,
  alsoSetIn, note, modifiedByCode, modifiedByName, modifiedDate}`. `value` is `null` for a secret;
  `alsoSetIn` is `[]` when there are no other places, sorted by name; `modifiedDate` is a date-time
  (`2026-09-17T14:30:00`), the last time the cell was written.
- `types`: `[{code, name, secret, sortOrder}]`.
- `sources`: the place names this solution's Configuration already uses (sources and other places),
  sorted -- reuse them rather than spelling a place a new way.
- `counts`: `properties`, `state:<matchState>` (e.g. `state:some`), `group:<groupId>` and
  `groupstate:<groupId>.<matchState>`. A state with no properties is absent.

`matchState` is worked out on the server across ALL the solution's environments: `secret`; `empty`
(no environment has a value); `incomplete` (some environment has no value); `all` (every environment
the same -- green); `none` (all different -- red); `some` (some match -- orange). Values are compared
trimmed and ignoring case.

Refusals:

- 400 `<What> is required.` for a missing or blank `title` / `key` on an add ("An environment name",
  "A group name", "A property name").
- 400 `<What> may be at most N characters (this one is M).` -- for "An environment name", "A group
  name", "A property name", "A description", "A value", "Where it is read from", "A note", "A place
  name".
- 400 `A place name cannot contain |.`
- 404 `Solution not found.` for a solution, or for any environment / group / property of a solution,
  not shared with you; `Environment not found.`, `Group not found.`, `Property not found.` for an id
  that does not exist.
- 409 `This solution already has an environment named "<name>".`
- 409 `A solution needs at least one environment.` (deleting the last one).
- 409 `This solution already has a property named "<key>".`
- 409 `"<code>" is not a property type. Use text, path, host, port, url, number, flag or secret.`
- 409 `A property can only move to a group of the same solution.` (also for a `groupId` that does not
  exist).
- 409 `That environment belongs to a different solution.` (a cell whose `envId` is on another
  solution).

### Configuration II

The successor to Configuration, built beside it (Configuration keeps working, unchanged, until the
owner retires it). Same properties, groups, types, `source` / `alsoSetIn` / `note` rules and the same
SECRET rule -- but the columns are yours to define, and you can say how to compare them. The two tabs
are separate: nothing you write in one appears in the other. Configuration II never adds columns by
itself.

- **Columns** -- as many as needed (`QA - Tomcat 11`, ...). Each has an optional `color`, written
  `#RRGGBB` (stored upper case). ONE column may be the **default** (`isDefault: true`, "applies to
  all"): its value stands in for any other column left blank. A second default is refused (409); to
  move the default, first send `isDefault: false` to the old one.
- **Axes** -- your own ways of describing a column, each with VALUES: `Environment` = Local / QA /
  Prod, `Server` = Tomcat 9 / Tomcat 11. A column carries at most one value per axis, set by NAME in
  `tags`: `{"Environment":"QA","Server":"Tomcat 11"}`. Names are matched ignoring case. An axis or
  value you name that does not exist yet is created (on this solution only). `""` (or `null`) as a
  value removes that tag. Axis values may carry a `color` too, so the page can color columns by an
  axis.
- **Cell state** -- `state` on a cell: `observed` (the default -- seen on the running system),
  `planned` (the value it WILL have; the place does not exist yet or is not deployed), `unverified`
  (written down, not yet checked). Case does not matter. Use `planned` for anything that is a deploy
  plan.

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/config-grid` | the whole tab (below) |
| `POST /api/solutions/{id}/config-grid/import` | no body. Copies the Configuration tab in: each environment becomes a column (same name and order, no color, no tags, not the default), each group, property and cell is copied with its source, other places and note, every cell `observed`. Refused (409) once Configuration II has any column or group (axes do not count), and when the Configuration tab has no environments. Configuration itself is not changed. `canImport` tells you beforehand |
| `POST /api/solutions/{id}/config-grid/columns` | `{title, color, isDefault, tags: {axis: value}}` -- `title` required (at most 100), unique within the solution; `color` optional; `isDefault` defaults to `false`. Added at the end |
| `PUT /api/solutions/config-grid/columns/{columnId}` | `{title, color, isDefault, sortOrder, tags}` -- send only what changes. `title` left out or `""` keeps it; `"color": ""` clears the color; `tags` names only the axes to change (`{"Server": ""}` removes that one tag) |
| `DELETE /api/solutions/config-grid/columns/{columnId}` | takes that column's cells and tags; the properties stay |
| `POST /api/solutions/{id}/config-grid/axes` | `{title}` -- required, at most 100, unique within the solution |
| `PUT /api/solutions/config-grid/axes/{axisId}` | `{title, sortOrder}` -- send only what changes; `title` left out or `""` keeps it |
| `DELETE /api/solutions/config-grid/axes/{axisId}` | takes its values and every column's tag on it (the columns stay) |
| `POST /api/solutions/config-grid/axes/{axisId}/values` | `{title, color}` -- `title` required (at most 100), unique within the axis |
| `PUT /api/solutions/config-grid/axis-values/{valueId}` | `{title, color, sortOrder}` -- send only what changes; `title` left out or `""` keeps it; `"color": ""` clears the color |
| `DELETE /api/solutions/config-grid/axis-values/{valueId}` | removes the tag from the columns that had it (the columns stay) |
| `POST /api/solutions/{id}/config-grid/groups` | `{title}` -- required, at most 200 |
| `PUT /api/solutions/config-grid/groups/{groupId}` | `{title, sortOrder}` -- send only what changes; `title` left out or `""` keeps it |
| `DELETE /api/solutions/config-grid/groups/{groupId}` | takes its properties and all their cells |
| `POST /api/solutions/config-grid/groups/{groupId}/properties` | `{key, description, typeCode}` -- `key` required (at most 200), unique within the solution; `description` at most 2000; `typeCode` as in Configuration (left out = `text`) |
| `PUT /api/solutions/config-grid/properties/{propertyId}` | `{key, description, typeCode, groupId, sortOrder}` -- send only what changes; same rules as in Configuration (`description: ""` clears it; changing to `secret` wipes the stored values) |
| `DELETE /api/solutions/config-grid/properties/{propertyId}` | takes its cell in every column |
| `PUT /api/solutions/config-grid/properties/{propertyId}/values/{columnId}` | `{value, source, alsoSetIn: [..], note, state}` -- ONE cell; see below |

**One cell.** The same fields, limits and rules as a Configuration cell (`value` at most 2000,
`source` 200, `note` 2000, each place 200; a place name cannot contain `|`), plus `state`. A field
you leave out keeps what the cell has; `""` clears `value`, `source` or `note` and `[]` clears the
other places. `state` is different: left out or `""`, it keeps the cell's state (a new cell is
`observed`); it cannot be cleared, only set to another state. A cell with no value, source, note or
other place is removed, whatever its state. The column must belong to the same solution as the
property; the default column takes cells like any other.

Every call answers with the whole tab:

- `solutionId`, and `canImport`: `true` while this tab has no columns and no groups and the
  Configuration tab has at least one environment.
- `columns`: `[{id, title, color, isDefault, sortOrder, tags}]` in order; `color` is `null` when
  unset; each tag `{axisId, axisTitle, valueId, value}`, in axis order.
- `axes`: `[{id, title, sortOrder, values}]`; each value `{id, axisId, title, color, sortOrder}`.
- `groups`: `[{id, title, sortOrder, propertyCount, states, properties}]`. `states` counts the
  group's properties by `<comparison>.<state>`: `{"all.some": 3, "12.none": 1}`.
- each property: `{id, groupId, key, description, typeCode, typeName, secret, sortOrder, values,
  matches}`.
- `values`: one cell per column that has anything, `{columnId, value, source, alsoSetIn, note,
  state, modifiedByCode, modifiedByName, modifiedDate}` (`modifiedDate` a date-time, as above).
- `types`: `[{code, name, secret, sortOrder}]`; `states`: the cell states,
  `[{code, name, description, sortOrder}]`.
- `sources`: the place names Configuration II already uses on this solution, sorted.
- `counts`: `properties`, and `state:<comparison>.<state>` for each property, e.g. `state:all.some`,
  `state:12.none`.

**How the colors are worked out -- `matches`.** Every property carries `matches`, keyed by the way of
comparing: `"all"` (every non-default column in one set) and each axis id as a string (one set per
value of that axis that some column carries -- e.g. under Environment, `Local - Tomcat 9` is compared
only with `Local - Tomcat 11`; columns without a value on that axis form one more set, last, with
`valueId` 0). Each entry is `{state, sets: [{valueId, columnIds, setCount, distinctCount, state}]}`;
the `"all"` set has `valueId` 0. `setCount` is how many columns of the set have a value,
`distinctCount` how many different values. A blank column takes the default column's value before
comparing; the default column itself is never in a set. Values are compared trimmed and ignoring
case. A set's state: `secret` (a secret property), `empty` (no column has a value), `single` (one
column, nothing to compare), `incomplete` (a gap beside a known value), `all` (green), `none` (red --
every column of the set differs), `some` (orange). The entry's `state` is its worst set: `none`, then
`some`, then `incomplete` (an `empty` set beside an `all` or `single` set also counts as
`incomplete`), then `all`, then `single`, then `empty`.

Refusals:

- 400 `<What> is required.` for a missing or blank name on an add ("An axis name", "An axis value",
  "A column name", "A group name", "A property name"), and "An axis name" for a blank key in `tags`.
- 400 `<What> may be at most N characters (this one is M).` -- "An axis name", "An axis value", "A
  column name" (100); "A group name", "A property name", "Where it is read from", "A place name"
  (200); "A description", "A value", "A note" (2000).
- 400 `A Color is written #RRGGBB, for example #2563EB.`
- 400 `A place name cannot contain |.`
- 404 `Solution not found.` for a solution, or for anything on a solution, not shared with you;
  `Axis not found.`, `Axis value not found.`, `Column not found.`, `Group not found.`,
  `Property not found.` for an id that does not exist.
- 409 `This solution already has a column named "<title>". Nothing was saved.`
- 409 `"<title>" is already the column that applies to all. Only one column can be. Nothing was saved.`
- 409 `This solution already has an axis named "<title>". Nothing was saved.`
- 409 `This axis already has a value named "<title>". Nothing was saved.`
- 409 `This solution already has a property named "<key>". Nothing was saved.`
- 409 `"<code>" is not a property type. Use text, path, host, port, url, number, flag or secret. Nothing was saved.`
- 409 `"<state>" is not a cell state. Use observed, planned or unverified. Nothing was saved.`
- 409 `A property can only move to a group of the same solution.` (also for a `groupId` that does not
  exist).
- 409 `That column belongs to a different solution.`
- 409 `Configuration II already has columns or groups, so the Configuration tab can only be copied into an empty one. Nothing was copied.`
- 409 `The Configuration tab of this solution has nothing in it to copy. Nothing was copied.`

## 5. Seeing everything

Nothing in a solution is hidden from you. To read all of it:

| You want | Call | Then, for the full text of one |
|---|---|---|
| The solution itself, and where things stand | `GET /api/solutions/{id}` | |
| How fresh "where things stand" is | `GET /api/solutions/{id}/summary-age` | |
| Its projects (the main list), in order | `GET /api/solutions/{id}/projects` | `GET /api/solutions/projects/{projectId}` |
| Every item at every depth | `GET /api/solutions/{id}/items` | `GET /api/solutions/items/{itemId}` |
| The items of one project | `GET /api/solutions/{id}/items?projectId={projectId}` | |
| The open items, sorted into now, waiting and next | `GET /api/solutions/{id}/lens` | |
| What every item waits on | `GET /api/solutions/{id}/waits` | `GET /api/solutions/items/{itemId}/waits` |
| The log, in the order things were done | `GET /api/solutions/{id}/log?ascending=true` (page with `offset`, `pageSize`; `total` says how many) | `GET /api/solutions/log/{logId}` |
| The log of one item | `GET /api/solutions/{id}/log?itemId={itemId}&ascending=true` | |
| Notes (runbooks, strategies, references) | `GET /api/solutions/{id}/notes` | `GET /api/solutions/notes/{noteId}` -- body and steps |
| Older wordings of a note | `GET /api/solutions/notes/{noteId}/versions` | `GET .../versions/{n}` |
| Terms (the glossary) | `GET /api/solutions/{id}/terms?groupBy=none` (page with `offset`, `pageSize`; `total` says how many) | `GET /api/solutions/terms/{termId}` -- with its notes |
| Topics, and what is in each | `GET /api/solutions/{id}/topics` | `GET /api/solutions/{id}/links?kind=topic&recordId={topicId}` |
| The kinds of part | `GET /api/solutions/{id}/topic-types` | |
| What any record is linked to | `GET /api/solutions/{id}/links?kind={kind}&recordId={id}` | |
| Where a record came from, and what was made from it | `GET /api/solutions/{id}/provenance?kind={kind}&recordId={id}` | |
| Open questions | `GET /api/solutions/{id}/questions?status=open` | `GET /api/solutions/questions/{questionId}` |
| The decision register | `GET /api/solutions/{id}/decisions?status=current` | `GET /api/solutions/decisions/{decisionId}` -- with context and options |
| What changed since your last session | `POST /api/solutions/{id}/visit` | |
| Everything anyone changed lately | `GET /api/solutions/{id}/activity` | |
| Your note from last time | `GET /api/solutions/{id}/left-off` | |
| Files | `GET /api/solutions/{id}/files` (or `?itemId=`) | `GET /api/solutions/files/{fileId}/content` -- the bytes |
| Earlier versions of a file | `GET /api/solutions/files/{fileId}/versions` | `GET /api/solutions/files/versions/{versionId}/content` |
| Conversations | `GET /api/solutions/{id}/conversations` | `GET /api/solutions/conversations/{convId}/messages` |
| Testing checklists | `GET /api/solutions/{id}/test-plans` | `GET /api/solutions/test-plans/{planId}` -- the whole grid |
| Configuration | `GET /api/solutions/{id}/config` -- everything, in one call | |
| Configuration II | `GET /api/solutions/{id}/config-grid` -- everything, in one call | |

**Building the item tree.** The item list is flat. Each row has `id`, `projectId`,
`parentItemId` (null at the top level), `sortOrder` and `childCount`. Group rows by `projectId`;
within a project, the top-level items are those with no `parentItemId`; the sub-items of item X
are the rows whose `parentItemId` is X, in `sortOrder`; repeat to any depth. The list is NOT in tree
order: it comes project by project, then the top-level items, then the sub-items grouped by
`parentItemId`. A sub-item can come before its parent in the list, so read the whole list into a map
by `id` first, then build the tree.

Lists cut long text short (`descriptionPreview`, `detailPreview`, `bodyPreview`, log `text`, a term's
`definition`, a topic's `summary`, a question's `detail`, a decision's `outcome`, with `truncated: true`
where the row has that field). When a preview is not enough, fetch the single record.

## 6. Every operation, one example each

`B` is the base URL. `H` stands for the two headers: `-H "X-Echo-Client: <code>" -H "X-Echo-Client-Key: $ECHO_KEY"`.
Read the key from your environment; never write it into a command you save.

```
# ---- orientation: who you are, the codes, the solutions
curl $H           $B/api/solutions/whoami
curl $H           $B/api/solutions/lookups
curl $H           $B/api/solutions/spec
curl $H           $B/api/solutions
curl $H           "$B/api/solutions?includeArchived=true"
curl $H           $B/api/solutions/1
curl $H           $B/api/solutions/by-alias/claims
curl $H -X PUT    $B/api/solutions/1                     -H "Content-Type: application/json" -d '{"statusSummary":"Server upgraded; the 835 parser is next.","targetDate":"2026-12-01"}'

# ---- projects
curl $H -X POST   $B/api/solutions/1/projects            -H "Content-Type: application/json" -d '{"name":"Deployment","statusCode":"todo"}'
curl $H           $B/api/solutions/1/projects
curl $H           $B/api/solutions/projects/5
curl $H -X PUT    $B/api/solutions/projects/5            -H "Content-Type: application/json" -d '{"statusCode":"inprogress"}'
curl $H -X PUT    $B/api/solutions/projects/5            -H "Content-Type: application/json" -d '{"sortOrder":1}'
curl $H -X DELETE $B/api/solutions/projects/5

# ---- items and sub-items
curl $H -X POST   $B/api/solutions/projects/5/items      -H "Content-Type: application/json" -d '{"title":"Upgrade the server","assigneeCode":"<code>","priority":2,"dueDate":"2026-10-15"}'
curl $H -X POST   $B/api/solutions/projects/5/items      -H "Content-Type: application/json" -d '{"title":"Back up the config first","parentItemId":42}'
curl $H           $B/api/solutions/1/items
curl $H           "$B/api/solutions/1/items?projectId=5"
curl $H           $B/api/solutions/items/42
curl $H -X PUT    $B/api/solutions/items/42              -H "Content-Type: application/json" -d '{"detail":"Found two configs, not one."}'
curl $H -X POST   $B/api/solutions/items/42/status       -H "Content-Type: application/json" -d '{"statusCode":"blocked"}'
curl $H -X POST   $B/api/solutions/items/43/move         -H "Content-Type: application/json" -d '{"parentItemId":0,"sortOrder":1}'
curl $H -X DELETE $B/api/solutions/items/42

# ---- log
curl $H -X POST   $B/api/solutions/1/log                 -H "Content-Type: application/json" -d '{"typeCode":"command","itemId":42,"text":"mvn -q clean verify"}'
curl $H           "$B/api/solutions/1/log?ascending=true&pageSize=50&offset=0"
curl $H           "$B/api/solutions/1/log?since=118"
curl $H           "$B/api/solutions/1/log?itemId=42&type=command"
curl $H           $B/api/solutions/log/119
curl $H -X PUT    $B/api/solutions/log/119               -H "Content-Type: application/json" -d '{"text":"mvn -q clean verify -DskipITs"}'
curl $H -X DELETE $B/api/solutions/log/119

# ---- notes, versions, steps
curl $H -X POST   $B/api/solutions/1/notes               -H "Content-Type: application/json" -d '{"title":"How to deploy","typeCode":"runbook","body":"# Deploy\n...","changeNote":"first draft"}'
curl $H           "$B/api/solutions/1/notes?type=runbook"
curl $H           $B/api/solutions/notes/7
curl $H -X PUT    $B/api/solutions/notes/7               -H "Content-Type: application/json" -d '{"body":"# Deploy\n(revised)","changeNote":"added the rollback"}'
curl $H -X PUT    $B/api/solutions/note-types/order      -H "Content-Type: application/json" -d '{"ids":[2,1,3]}'    # a person's call: answers 403 to you
curl $H           $B/api/solutions/notes/7/versions
curl $H           $B/api/solutions/notes/7/versions/1
curl $H -X POST   $B/api/solutions/notes/7/steps         -H "Content-Type: application/json" -d '{"title":"Stop the service","command":"sc stop alpha","expectedResult":"STOPPED"}'
curl $H -X PUT    $B/api/solutions/notes/7/steps/31      -H "Content-Type: application/json" -d '{"notes":"Wait for it to reach STOPPED."}'
curl $H -X POST   $B/api/solutions/notes/7/steps/31/move     -H "Content-Type: application/json" -d '{"stepNumber":1}'
curl $H -X POST   $B/api/solutions/notes/7/steps/from-log    -H "Content-Type: application/json" -d '{"logId":119}'
curl $H -X DELETE $B/api/solutions/notes/7/steps/31
curl $H -X DELETE $B/api/solutions/notes/7

# ---- terms (the glossary)
curl $H           "$B/api/solutions/1/terms?groupBy=none&search=deploy"
curl $H           $B/api/solutions/1/terms/lookups
curl $H -X POST   $B/api/solutions/1/terms               -H "Content-Type: application/json" -d '{"term":"SLA","definition":"Service level agreement: what the platform promises its callers.","type":"Acronym","categories":["Contracts"],"source":"The service contract"}'
curl $H           $B/api/solutions/terms/12
curl $H -X PUT    $B/api/solutions/terms/12               -H "Content-Type: application/json" -d '{"notes":"Measured monthly.","categories":["Contracts","Operations"]}'
curl $H -X PUT    $B/api/solutions/terms/12/favorite      -H "Content-Type: application/json" -d '{"favorite":true}'
curl $H -X PUT    $B/api/solutions/1/terms/types/3        -H "Content-Type: application/json" -d '{"name":"Abbreviation"}'
curl $H -X PUT    $B/api/solutions/1/terms/categories/5   -H "Content-Type: application/json" -d '{"name":"Operations and support"}'
curl $H -X DELETE $B/api/solutions/1/terms/types/3
curl $H -X DELETE $B/api/solutions/1/terms/categories/5
curl $H -X DELETE $B/api/solutions/terms/12

# ---- links and topics
curl $H           "$B/api/solutions/1/link-targets?q=charset&kind=item&recordId=42"
curl $H -X POST   $B/api/solutions/1/links               -H "Content-Type: application/json" -d '{"fromKind":"item","fromId":42,"toKind":"note","toId":7}'
curl $H           "$B/api/solutions/1/links?kind=item&recordId=42"
curl $H -X DELETE $B/api/solutions/1/links/15
curl $H -X POST   $B/api/solutions/1/topics              -H "Content-Type: application/json" -d '{"name":"Charset","summary":"Read files as UTF-8; remit and submit stay windows-1252."}'
curl $H           "$B/api/solutions/1/topics?search=charset&offset=0&pageSize=50"
curl $H           $B/api/solutions/topics/3
curl $H -X POST   $B/api/solutions/1/links               -H "Content-Type: application/json" -d '{"fromKind":"log","fromId":505,"toKind":"topic","toId":3}'
curl $H           "$B/api/solutions/1/links?kind=topic&recordId=3"
curl $H -X PUT    $B/api/solutions/topics/3              -H "Content-Type: application/json" -d '{"summary":"Decided 23 Oct: UTF-8 in, windows-1252 for remit and submit."}'
curl $H -X DELETE $B/api/solutions/topics/3

# ---- kinds of topic, the premise switch
curl $H           $B/api/solutions/topic-kinds
curl $H -X POST   $B/api/solutions/1/topics              -H "Content-Type: application/json" -d '{"name":"Risk","kind":"tag"}'
curl $H -X POST   $B/api/solutions/1/topics              -H "Content-Type: application/json" -d '{"name":"QA environment","kind":"part","typeName":"Environment","statusCode":"inprogress"}'
curl $H -X PUT    $B/api/solutions/topics/6              -H "Content-Type: application/json" -d '{"figure":4200,"statusCode":"done"}'
curl $H -X POST   $B/api/solutions/1/topics              -H "Content-Type: application/json" -d '{"name":"The 837 submit path is live","kind":"assumption"}'
curl $H           "$B/api/solutions/1/topics?kind=part"
curl $H           $B/api/solutions/1/topic-types
curl $H -X POST   $B/api/solutions/1/topic-types         -H "Content-Type: application/json" -d '{"name":"Chapter","figureLabel":"words"}'
curl $H -X PUT    $B/api/solutions/topic-types/2         -H "Content-Type: application/json" -d '{"figureLabel":"pages"}'
curl $H -X DELETE $B/api/solutions/topic-types/2
curl $H -X PUT    $B/api/solutions/1/topics/order        -H "Content-Type: application/json" -d '{"ids":[12,9,30]}'
curl $H           $B/api/solutions/topics/8/open-items
curl $H -X POST   $B/api/solutions/topics/8/proved-false -H "Content-Type: application/json" -d '{"reason":"The partner app writes the 837 itself.","itemIds":[1200,1201]}'
curl $H -X PUT    $B/api/solutions/topics/8              -H "Content-Type: application/json" -d '{"holds":true}'

# ---- waits
curl $H           "$B/api/solutions/1/link-targets?q=deploy&targetKind=item&kind=item&recordId=1201"
curl $H -X POST   $B/api/solutions/items/1201/waits      -H "Content-Type: application/json" -d '{"dependsOnItemId":1200}'
curl $H           $B/api/solutions/items/1201/waits
curl $H           $B/api/solutions/1/waits
curl $H -X DELETE $B/api/solutions/waits/4

# ---- many at once, make into, saved views
curl $H -X POST   $B/api/solutions/1/items/bulk          -H "Content-Type: application/json" -d '{"itemIds":[1200,1201],"field":"status","value":"cancelled","reason":"The partner app writes the 837 itself."}'
curl $H -X POST   $B/api/solutions/1/items/bulk          -H "Content-Type: application/json" -d '{"itemIds":[1200,1201],"field":"project","value":"41","reason":"Submit work moves to the QA project."}'
curl $H -X POST   $B/api/solutions/1/promote             -H "Content-Type: application/json" -d '{"sourceKind":"message","sourceId":9012,"targetKind":"item","projectId":41}'
curl $H           "$B/api/solutions/1/provenance?kind=item&recordId=1305"
curl $H           "$B/api/solutions/1/views?scope=items"
curl $H -X POST   $B/api/solutions/1/views               -H "Content-Type: application/json" -d '{"scope":"items","name":"My open submit work","filter":"{\"project\":\"41\",\"status\":\"\",\"assignee\":\"rex\",\"hideClosed\":true,\"view\":\"tree\"}"}'
curl $H -X DELETE $B/api/solutions/views/3

# ---- since you were last here
curl $H -X POST   $B/api/solutions/1/visit
curl $H           "$B/api/solutions/1/since?offset=50&pageSize=50"
curl $H -X POST   $B/api/solutions/1/caught-up

# ---- questions and decisions
curl $H -X POST   $B/api/solutions/1/questions           -H "Content-Type: application/json" -d '{"text":"Which charset do the 837 files use?","ownerCode":"tony","links":[{"kind":"item","id":1200}]}'
curl $H           "$B/api/solutions/1/questions?status=open&offset=0&pageSize=50"
curl $H           $B/api/solutions/questions/4
curl $H -X PUT    $B/api/solutions/questions/4           -H "Content-Type: application/json" -d '{"detail":"Remit says windows-1252; submit is not known."}'
curl $H -X POST   $B/api/solutions/questions/4/status    -H "Content-Type: application/json" -d '{"statusCode":"dropped"}'
curl $H -X DELETE $B/api/solutions/questions/4
curl $H -X POST   $B/api/solutions/log/412/question
curl $H -X POST   $B/api/solutions/1/decisions           -H "Content-Type: application/json" -d '{"title":"Read files in as UTF-8","outcome":"Remit and submit stay windows-1252.","links":[{"kind":"question","id":4},{"kind":"item","id":1200}]}'
curl $H           "$B/api/solutions/1/decisions?status=current"
curl $H           $B/api/solutions/decisions/9
curl $H -X PUT    $B/api/solutions/decisions/9           -H "Content-Type: application/json" -d '{"statusCode":"accepted"}'
curl $H           $B/api/solutions/decisions/9/open-links
curl $H -X POST   $B/api/solutions/decisions/9/settle    -H "Content-Type: application/json" -d '{"questionIds":[4],"itemIds":[1200]}'
curl $H -X POST   $B/api/solutions/decisions/9/supersede -H "Content-Type: application/json" -d '{"title":"Read everything as UTF-8","outcome":"The partner fixed the remit encoding."}'
curl $H -X DELETE $B/api/solutions/decisions/9
curl $H -X POST   $B/api/solutions/log/505/decision
curl $H           $B/api/solutions/1/summary-age

# ---- now / next / waiting, the note for next time, the activity feed
curl $H           $B/api/solutions/1/lens
curl $H           "$B/api/solutions/1/lens?bucket=next&offset=50&pageSize=50"
curl $H           $B/api/solutions/1/left-off
curl $H -X PUT    $B/api/solutions/1/left-off            -H "Content-Type: application/json" -d '{"note":"Next time: rerun the 835 parse, Calvin owes the QA dump."}'
curl $H           "$B/api/solutions/1/activity?days=7&who=tony&kind=log&offset=0&pageSize=50"

# ---- files (upload is multipart, NOT json -- do not send a Content-Type header yourself)
curl $H -X POST   $B/api/solutions/1/files               -F "file=@./build.log" -F "title=Build log" -F "itemId=42"
curl $H           $B/api/solutions/1/files
curl $H           "$B/api/solutions/1/files?itemId=42"
curl $H           $B/api/solutions/files/9
curl $H -X PUT    $B/api/solutions/files/9               -H "Content-Type: application/json" -d '{"title":"Build log, second run"}'
curl $H           $B/api/solutions/files/9/content -o build.log
curl $H -X POST   $B/api/solutions/files/9/versions      -F "file=@./build.log" -F "changeNote=Second run, warnings fixed"
curl $H           $B/api/solutions/files/9/versions
curl $H           $B/api/solutions/files/versions/57/content -o build-v1.log
curl $H -X DELETE $B/api/solutions/files/9

# ---- conversations
curl $H -X POST   $B/api/solutions/1/conversations       -H "Content-Type: application/json" -d '{"title":"Which port does alpha use?","message":"Item 42. The config names 8080 and 8443. Which one is live? I would pick 8443."}'
curl $H           $B/api/solutions/1/conversations
curl $H           "$B/api/solutions/conversations/7/messages?since=19"
curl $H -X POST   $B/api/solutions/conversations/7/messages      -H "Content-Type: application/json" -d '{"body":"Thanks. Using 8443; item 42 is unblocked."}'
curl $H -X DELETE $B/api/solutions/1/conversations/8

# ---- conversations: the owner's calls -- each of these answers 403 to you
curl $H -X PUT    $B/api/solutions/conversations/7           -H "Content-Type: application/json" -d '{"title":"Which port alpha uses"}'
curl $H -X PUT    $B/api/solutions/conversations/7/resolved  -H "Content-Type: application/json" -d '{"resolved":true}'
curl $H -X PUT    $B/api/solutions/conversations/7/messages/20 -H "Content-Type: application/json" -d '{"body":"Corrected text"}'
curl $H -X DELETE $B/api/solutions/conversations/7/messages/20

# ---- testing checklists
curl $H -X POST   $B/api/solutions/1/test-plans          -H "Content-Type: application/json" -d '{"title":"Upgrade testing","description":"Does the new stack do what the old one did?"}'
curl $H           $B/api/solutions/1/test-plans
curl $H -X POST   $B/api/solutions/1/test-plans          -H "Content-Type: application/json" -d '{"title":"QA acceptance","kind":"qa"}'
curl $H           "$B/api/solutions/1/test-plans?kind=qa"
curl $H           $B/api/solutions/test-plans/4
curl $H -X PUT    $B/api/solutions/test-plans/4          -H "Content-Type: application/json" -d '{"description":"Tick only what you saw pass."}'
curl $H -X POST   $B/api/solutions/test-plans/4/columns  -H "Content-Type: application/json" -d '{"title":"End to end"}'
curl $H -X PUT    $B/api/solutions/test-plans/columns/11 -H "Content-Type: application/json" -d '{"title":"End to end (local)"}'
curl $H -X POST   $B/api/solutions/test-plans/4/groups   -H "Content-Type: application/json" -d '{"title":"Payments coming in"}'
curl $H -X PUT    $B/api/solutions/test-plans/groups/20  -H "Content-Type: application/json" -d '{"title":"Payments coming in (835)"}'
curl $H -X POST   $B/api/solutions/test-plans/groups/20/cases -H "Content-Type: application/json" -d '{"text":"A payment file is picked up and archived","note":"Look in the archive folder"}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/301  -H "Content-Type: application/json" -d '{"groupId":21}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/301  -H "Content-Type: application/json" -d '{"includeInPrint":false}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/301/checks/11 -H "Content-Type: application/json" -d '{"checked":true}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/301/checks/12 -H "Content-Type: application/json" -d '{"skipped":true}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/301/checks/12 -H "Content-Type: application/json" -d '{"skipped":false}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/302/result    -H "Content-Type: application/json" -d '{"failing":true,"note":"The file is picked up but never archived"}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/302/result    -H "Content-Type: application/json" -d '{"failing":false}'
curl $H -X POST   $B/api/solutions/test-plans/4/clear    -H "Content-Type: application/json" -d '{"columnId":11}'
curl $H -X POST   $B/api/solutions/test-plans/4/clear    -H "Content-Type: application/json" -d '{}'
curl $H -X PUT    $B/api/solutions/test-plans/groups/20/case-order -H "Content-Type: application/json" -d '{"ids":[303,301,302]}'
curl $H -X PUT    $B/api/solutions/test-plans/4/group-order -H "Content-Type: application/json" -d '{"ids":[21,20]}'
curl $H -X DELETE $B/api/solutions/test-plans/cases/301
curl $H -X DELETE $B/api/solutions/test-plans/groups/20
curl $H -X DELETE $B/api/solutions/test-plans/columns/11
curl $H -X DELETE $B/api/solutions/test-plans/4

# ---- configuration
curl $H           $B/api/solutions/1/config
curl $H -X POST   $B/api/solutions/1/config/environments/defaults
curl $H -X POST   $B/api/solutions/1/config/environments    -H "Content-Type: application/json" -d '{"title":"Staging"}'
curl $H -X PUT    $B/api/solutions/config/environments/12   -H "Content-Type: application/json" -d '{"title":"Pre-production"}'
curl $H -X PUT    $B/api/solutions/config/environments/12   -H "Content-Type: application/json" -d '{"sortOrder":2}'
curl $H -X DELETE $B/api/solutions/config/environments/12
curl $H -X POST   $B/api/solutions/1/config/groups          -H "Content-Type: application/json" -d '{"title":"Scheduling"}'
curl $H -X PUT    $B/api/solutions/config/groups/8          -H "Content-Type: application/json" -d '{"title":"Scheduling and timers"}'
curl $H -X POST   $B/api/solutions/config/groups/8/properties -H "Content-Type: application/json" -d '{"key":"claimsRemitTrigger.repeatInterval","description":"How often the remit folders are scanned","typeCode":"number"}'
curl $H -X PUT    $B/api/solutions/config/properties/40     -H "Content-Type: application/json" -d '{"description":"Suspected dead config"}'
curl $H -X PUT    $B/api/solutions/config/properties/40     -H "Content-Type: application/json" -d '{"groupId":9}'
curl $H -X PUT    $B/api/solutions/config/properties/40/values/2 -H "Content-Type: application/json" -d '{"value":"300000","source":"External properties file (via additional-location)","alsoSetIn":["Packaged application.properties"],"note":"From the upper Confluence page"}'
curl $H -X PUT    $B/api/solutions/config/properties/40/values/2 -H "Content-Type: application/json" -d '{"note":"Confirmed on a real host"}'
curl $H -X PUT    $B/api/solutions/config/properties/40/values/2 -H "Content-Type: application/json" -d '{"alsoSetIn":[]}'
curl $H -X PUT    $B/api/solutions/config/properties/41/values/3 -H "Content-Type: application/json" -d '{"source":"Windows service -D args / OS environment"}'
curl $H -X DELETE $B/api/solutions/config/properties/40
curl $H -X DELETE $B/api/solutions/config/groups/8

# ---- configuration II (columns you define, axes, cell state)
curl $H           $B/api/solutions/1/config-grid
curl $H -X POST   $B/api/solutions/1/config-grid/import
curl $H -X POST   $B/api/solutions/1/config-grid/columns     -H "Content-Type: application/json" -d '{"title":"QA - Tomcat 11","tags":{"Environment":"QA","Server":"Tomcat 11"}}'
curl $H -X POST   $B/api/solutions/1/config-grid/columns     -H "Content-Type: application/json" -d '{"title":"All","isDefault":true}'
curl $H -X PUT    $B/api/solutions/config-grid/columns/31    -H "Content-Type: application/json" -d '{"tags":{"Server":"Tomcat 11"},"color":"#2563EB"}'
curl $H -X PUT    $B/api/solutions/config-grid/columns/31    -H "Content-Type: application/json" -d '{"tags":{"Server":""},"color":""}'
curl $H -X DELETE $B/api/solutions/config-grid/columns/31
curl $H -X POST   $B/api/solutions/1/config-grid/axes        -H "Content-Type: application/json" -d '{"title":"Region"}'
curl $H -X PUT    $B/api/solutions/config-grid/axes/4        -H "Content-Type: application/json" -d '{"title":"Data center"}'
curl $H -X DELETE $B/api/solutions/config-grid/axes/4
curl $H -X POST   $B/api/solutions/config-grid/axes/4/values -H "Content-Type: application/json" -d '{"title":"East","color":"#16A34A"}'
curl $H -X PUT    $B/api/solutions/config-grid/axis-values/9 -H "Content-Type: application/json" -d '{"color":"#DC2626"}'
curl $H -X DELETE $B/api/solutions/config-grid/axis-values/9
curl $H -X POST   $B/api/solutions/1/config-grid/groups      -H "Content-Type: application/json" -d '{"title":"Scheduling"}'
curl $H -X PUT    $B/api/solutions/config-grid/groups/8      -H "Content-Type: application/json" -d '{"sortOrder":1}'
curl $H -X DELETE $B/api/solutions/config-grid/groups/8
curl $H -X POST   $B/api/solutions/config-grid/groups/8/properties -H "Content-Type: application/json" -d '{"key":"claimsRemitTrigger.repeatInterval","typeCode":"number"}'
curl $H -X PUT    $B/api/solutions/config-grid/properties/40 -H "Content-Type: application/json" -d '{"typeCode":"secret"}'
curl $H -X DELETE $B/api/solutions/config-grid/properties/40
curl $H -X PUT    $B/api/solutions/config-grid/properties/40/values/31 -H "Content-Type: application/json" -d '{"value":"300000","source":"External properties file","state":"planned","note":"Deploy plan for QA on Tomcat 11"}'
curl $H -X PUT    $B/api/solutions/config-grid/properties/40/values/31 -H "Content-Type: application/json" -d '{"state":"observed"}'
```

Every create and update answers with the saved record (or, where its row says so, the whole note,
grid or tab), so you never need a second call to learn an id. A delete answers 200 with a small
confirmation, `{"ok": true, "deletedId": 42}`, unless its row says it answers something else.

## 7. A working session, start to finish

```
GET  /api/solutions/spec                                   read the contract
GET  /api/solutions/whoami                                 confirm who you are
GET  /api/solutions                                        pick the solution -> id 1
POST /api/solutions/1/visit                                what others changed since my last session
GET  /api/solutions/1/left-off                             my own note from last time
GET  /api/solutions/1/lens                                 what is now, what is waiting
GET  /api/solutions/1/items                                see what is open and what is yours
POST /api/solutions/items/42/status   {"statusCode":"inprogress"}
  ... do the work ...
POST /api/solutions/1/log   {"typeCode":"command","itemId":42,"text":"mvn -q clean verify"}
POST /api/solutions/1/log   {"typeCode":"result","itemId":42,"text":"BUILD SUCCESS, 412 tests"}
POST /api/solutions/1/links {"fromKind":"item","fromId":42,"toKind":"topic","toId":3}   it belongs to the Charset topic
POST /api/solutions/items/42/status   {"statusCode":"done"}
GET  /api/solutions/conversations/7/messages?since=19      any answer to my question yet?
PUT  /api/solutions/1/left-off {"note":"Next: the 835 parser. 42 is done."}   for my next session
```
