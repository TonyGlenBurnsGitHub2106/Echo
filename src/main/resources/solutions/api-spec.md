# Solutions API -- the contract

You are an API client of the Solutions program: a shared place where a team plans,
tracks and records a technical effort. Everything you may do is on this page. If it
is not on this page, you may not do it -- do not guess at other paths.

Read this whole page before your first call. Fetch it again at the start of every
working session; it is always current.

> **Changed 2026-09-22 -- Documents are now Notes.** Every `/documents` path is now `/notes`
> (`/api/solutions/{id}/notes`, `/api/solutions/notes/{noteId}/...`), `lookups.documentTypes` is
> `noteTypes`, a solution's `documentCount` is `noteCount`, and the `documentId` on a version or a
> step is `noteId`. The old paths no longer exist. Nothing else changed; the note types
> are still `runbook`, `strategy` and `reference`.

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

## 1. Connecting

- Base URL: `{{BASE_URL}}`
- Every request carries BOTH headers:
  - `X-Echo-Client: <your client code>`
  - `X-Echo-Client-Key: <your key>`
- Send and expect JSON: `Content-Type: application/json`.
- Send NO other credential header of any kind (no `Authorization`, no cookies, no
  other key). A request that carries one is refused outright.
- Check your connection first: `GET /api/solutions/whoami`. It answers with the
  member code your writes are attributed to.

Answers you will see:

| Status | Meaning | What to do |
|---|---|---|
| 200 | Done. The body is the saved or requested record. | Carry on. |
| 400 | The request was malformed. `message` says which field. | Fix the request. |
| 401 | Wrong client code or key, or the client is switched off. | Stop. Tell your human. Do not retry in a loop. |
| 403 | The path is outside what you may reach, or the action is the owner's (creating or deleting a whole solution, sharing it, choosing where its files are saved, managing members). | Do not retry. Ask in a conversation instead. |
| 404 | No such record -- or it is not one you were given -- or no such address (check the path against this contract). | Re-read the list you got the id from, and the path. |
| 409 | A rule refused it (duplicate name, wrong parent, a code that does not exist). `message` is written for you. | Read `message`, adjust, retry once. |
| 429 | Too many failed attempts. | Stop for fifteen minutes. |
| 500 | A fault on the server, not in your request. The body carries an `errorId`. | Do not retry in a loop. Tell your human the `errorId`. |

Error bodies look like `{"ok": false, "error": "rule", "message": "..."}`.

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

- status codes: `todo`, `inprogress`, `blocked`, `done`, `cancelled`
- log type codes: `note`, `command`, `result`, `decision`, `blocker`, `question`
- note type codes: `runbook`, `strategy`, `reference`
- member codes: whoever is listed under `members` (you are one of them)

**A code that does not exist is refused, not guessed at.** `statusCode: "finished"` or
`assigneeCode: "bob"` answers 409, saves nothing, and the `message` lists the valid codes. You
may always use your own member code (the one `whoami` gives you), even on your first call.

Dates are `2026-09-17` or `2026-09-17T14:30`. Priority is 1 (highest) to 5; 3 is normal.

**Every PUT is a merge.** Send only the fields you want to change; anything you leave
out keeps its current value. To empty a text field on purpose, send it as `""`. You
cannot blank a name, a title or a status by accident.

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
| `GET /api/solutions/whoami` | `{kind, memberCode, scoped}` |
| `GET /api/solutions/lookups` | `{statuses, members, logTypes, noteTypes}` |
| `GET /api/solutions/spec` | this page |
| `GET /api/solutions` | the solutions you were given, each with counts |
| `GET /api/solutions/{id}` | one solution with its full description |
| `GET /api/solutions/by-alias/{alias}` | the same, found by its `alias` -- the short name people say out loud ("claims"). When your human names a solution by a short word, look it up here. 404 if there is none you may see. |
| `PUT /api/solutions/{id}` | update it -- body `{name, description, statusCode, ownerCode, statusSummary, targetDate, alias}`. `alias` is lower-case letters, digits and hyphens with at least one letter, unique; send `""` to clear it. Use `statusSummary` for the one-paragraph "where are we". |
| `GET /api/solutions/{id}/summary-age` | how old `statusSummary` is: `{writtenDate, ageDays, stale}` -- when its text was last WRITTEN (not when the solution was last saved). `stale: true` at 14 days or more: if you know the state of play, write a fresh `statusSummary`. All null without a summary. |

### Projects

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/projects` | |
| `POST /api/solutions/{id}/projects` | `{name, description, statusCode, ownerCode, sortOrder}` -- `name` required, unique in the solution |
| `GET /api/solutions/projects/{projectId}` | one project with its full `description` (the list cuts it to a 300-character `descriptionPreview`) |
| `PUT /api/solutions/projects/{projectId}` | same fields. `sortOrder` (1, 2, 3...) is the order projects are shown in; send it alone to move one. |
| `DELETE /api/solutions/projects/{projectId}` | see *Deleting* |

### Items

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/items` | optional `?projectId=`. Flat list; build the tree from `parentItemId`. `detailPreview` is cut at 300 characters. |
| `GET /api/solutions/items/{itemId}` | one item with its full `detail` |
| `POST /api/solutions/projects/{projectId}/items` | `{title, detail, parentItemId, statusCode, assigneeCode, priority, dueDate}` -- `title` required. Give `parentItemId` to make a sub-item. |
| `PUT /api/solutions/items/{itemId}` | same fields, but send ONLY what changes. A field you leave out keeps its value. |
| `POST /api/solutions/items/{itemId}/status` | `{statusCode}` -- the cheap way to change only the status |
| `POST /api/solutions/items/{itemId}/move` | `{projectId, parentItemId, sortOrder}` -- 0 or absent means "same project", "top level", "last" |

### Log

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/log` | one page, newest first. Filters: `projectId`, `itemId`, `author`, `type`, `search`, `since=<sequenceNumber>` (only newer entries), `ascending=true` (the order things were done), `offset`, `pageSize` (default 50). The answer is `{entries, counts, offset, pageSize, total}`. `text` is cut at 2,000 characters with `truncated: true`. |
| `GET /api/solutions/log/{logId}` | one entry with its full text |
| `POST /api/solutions/{id}/log` | `{text, typeCode, itemId, projectId}` -- `text` required. Name the `itemId` the work was for whenever there is one. Instead of `typeCode` you may send `typeName`, the kind's name (`"Decision"`); a name no kind has is refused -- you cannot add a kind. The answer carries the `sequenceNumber` it was given. |
| `PUT /api/solutions/log/{logId}` | correct an entry (same fields). It keeps its number and its author. |

### Notes

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/notes` | optional `?type=runbook`. Headers with a 300-character `bodyPreview`. |
| `GET /api/solutions/notes/{noteId}` | the note: current `body` (markdown) and its `steps` |
| `POST /api/solutions/{id}/notes` | `{title, typeCode, body, changeNote, projectId}` -- `title` required. Instead of `typeCode` you may send `typeName`, the kind's name (`"Runbook"`); a name no kind has is refused -- you cannot add a kind. |
| `PUT /api/solutions/notes/{noteId}` | same fields. A changed `body` is saved as a NEW VERSION; nothing is ever overwritten. Say why in `changeNote`. |
| `PUT /api/solutions/note-types/order` | `{ids}` -- a person reorders the note kinds. Not for an agent: kinds are shared by every solution, so this answers 403. |
| `GET /api/solutions/notes/{noteId}/versions` | who changed it, when, why |
| `GET /api/solutions/notes/{noteId}/versions/{n}` | the wording of version n |
| `POST /api/solutions/notes/{noteId}/steps` | `{title, command, expectedResult, notes, stepNumber}` -- `title` required; `stepNumber` 0 or absent appends |
| `PUT /api/solutions/notes/{noteId}/steps/{stepId}` | same fields (does not renumber) |
| `POST /api/solutions/notes/{noteId}/steps/{stepId}/move` | `{stepNumber}` |
| `POST /api/solutions/notes/{noteId}/steps/from-log` | `{logId}` -- turns a log entry into the next step of this runbook. This is how "what we did" becomes "how to do it again". |

Every step call answers with the whole note, so you always hold the current numbering.

### Terms

A solution's glossary. A term has a `term` (the word), a `definition`, an optional `type` (one name),
`categories` (a list of names), `source` and `notes`, and a `favorite` star. A type or category is
found by its name, any case, and made the first time a term names it -- inside THIS solution only.
A solution never has the same term twice (case and repeated spaces ignored); a repeat is refused
with 409 and a sentence saying which term it already has.

| Call | Note |
|---|---|
| `GET /api/solutions/{id}/terms` | one page, `{terms, counts, offset, pageSize, total}`. Optional `search` (term, definition, source, notes, type, category), `typeId`, `categoryId`, `favorites=true`, `groupBy=type|category|none` (default `type`), `reverse=true`, `offset`, `pageSize` (default 50, at most 500). Each row has `groupKey` / `groupLabel`; grouped by category, a term appears once per category it carries. `definition` is a 600-character preview (`truncated: true` when there is more) and `notes` is left out (`hasNotes` says whether there are any). `counts`: `total` (matching), `all`, `favorites`, `types`, `categories`, and `group:<groupKey>` per group. |
| `GET /api/solutions/terms/{termId}` | the whole term, notes included |
| `POST /api/solutions/{id}/terms` | `{term, definition, type, categories, source, notes}` -- `term` and `definition` required |
| `PUT /api/solutions/terms/{termId}` | same fields. A merge: a field you leave out is kept. `"type":""` takes the type off; `"categories":[]` takes every category off; `""` clears `source` or `notes`. |
| `PUT /api/solutions/terms/{termId}/favorite` | `{favorite: true|false}` |
| `DELETE /api/solutions/terms/{termId}` | takes only the term; its type and categories stay |
| `GET /api/solutions/{id}/terms/lookups` | `{types, categories}`, each `{id, name, usageCount}`, A to Z |
| `PUT /api/solutions/{id}/terms/types/{typeId}` | `{name}` -- rename a type (`/categories/{categoryId}` for a category). Refused if the solution already has one by that name. Answers with the new lookups. |
| `DELETE /api/solutions/{id}/terms/types/{typeId}` | its terms are left with no type (`/categories/{categoryId}`: the category is taken off its terms). No term is removed. Answers with the new lookups. |

### Links and topics

A LINK joins two records of one solution. A record is named by its `kind` and its id: `item`,
`note`, `log` (a log entry), `file`, `term`, `topic`, `question` or `decision`. A link has no direction, so once item 42 is
linked to note 7, item 42's links show note 7 AND note 7's links show item 42 -- a record's links are
also its backlinks. Link instead of writing "see item X" or "file 211" into a text: a link survives
a rename and opens the record in one tap.

A TOPIC is a named subject inside the solution ("Charset", "Remit dupe") with a `summary` -- where
it stands now, the current answer. A record joins a topic by being linked to it (`toKind: "topic"`),
so a topic's records are simply its links. Use a topic when one subject runs across many projects,
items, log entries and notes; keep its `summary` current when the answer changes.

Both ends must be in the solution; a record never links to itself; a pair already linked (either way
round) is refused with 409. A topic name is used once per solution (case and repeated spaces ignored).
Deleting a record takes its links with it; deleting a topic takes its links, never the records.

| Call | Note |
|---|---|
| `GET /api/solutions/{id}/links?kind=item&recordId=42` | `{kind, id, links, offset, pageSize, total}`. Each link is the OTHER end: `{linkId, kind, id, title, sub, recordDate, closed, linkedDate, linkedByCode, linkedByName}`. Topics first, then newest first. Optional `offset`, `pageSize` (default 50). `title` of a log entry reads `#<sequenceNumber> <its first words>`. |
| `POST /api/solutions/{id}/links` | `{fromKind, fromId, toKind, toId}` -- all four required. Answers with the FROM record's links (as the GET above). |
| `DELETE /api/solutions/{id}/links/{linkId}` | takes the link away; neither record is touched |
| `GET /api/solutions/{id}/link-targets?q=charset` | records of the solution whose title holds the words, best first (a title starting with them, then topics, then newest), at most 30 (`pageSize` up to 100). Add `kind` and `recordId` of the record you are linking FROM: it is left out, and `alreadyLinked: true` marks what it is already joined to. Each row `{kind, id, title, sub, recordDate, closed, alreadyLinked}`. |
| `GET /api/solutions/{id}/topics` | one page by name, `{topics, counts, offset, pageSize, total}`. Optional `search` (name and summary), `offset`, `pageSize`. Each topic `{id, solutionId, name, summary, truncated, memberCount, latestDate, authorCode, modifiedBy, createdDate, modifiedDate}`; `summary` is a 600-character preview (`truncated: true` when there is more). |
| `GET /api/solutions/topics/{topicId}` | the whole topic. Its records: `GET /api/solutions/{id}/links?kind=topic&recordId={topicId}` |
| `POST /api/solutions/{id}/topics` | `{name, summary}` -- `name` required |
| `PUT /api/solutions/topics/{topicId}` | same fields. A merge: a field you leave out is kept; `"summary":""` clears it. |
| `DELETE /api/solutions/topics/{topicId}` | the topic and its links; the records stay |

### Since you were last here

What OTHER people and agents changed in a solution since your last session -- items, log entries,
notes, files, conversation messages, terms, topics, questions and decisions -- newest first. Your own changes are left out.
A session counts as new when your last call to `visit` was more than an hour before; within the hour
the same "since" holds. On your first visit ever, "since" is a week back (`firstVisit: true`).

| Call | Note |
|---|---|
| `POST /api/solutions/{id}/visit` | call it at the START of a working session. Counts the visit and answers `{since, firstVisit, changes, counts, offset, pageSize, total}`. Each change `{kind, id, title, sub, action, who, whoName, changedDate}`; `kind` is `item`, `log`, `note`, `file`, `message` (then `id` is the conversation), `term`, `topic`, `question` or `decision`; `action` is `added`, `changed`, `wrote`, `corrected`, `asked` (a question) or `decided` (a decision). `counts`: `total` and `kind:<kind>`. |
| `GET /api/solutions/{id}/since` | the same answer without counting a visit. Page with `offset`, `pageSize` (default 50). |
| `POST /api/solutions/{id}/caught-up` | you have read it all: "since" starts now |

### Questions and decisions

A QUESTION is something the solution needs answered: `{text, detail, ownerCode}` -- `ownerCode` is who
should answer it. It is `open` until it is `answered` (by a decision, or by hand) or `dropped`.

A DECISION is what was decided: a one-line `title`, the `context` (why it came up), the `options`
weighed and the `outcome`, with a status -- `proposed` (still to be agreed), `accepted`, or
`superseded`. Recording one also writes a journal entry of kind Decision (`logId`, `logSequenceNumber`),
so the log keeps its single "what happened, in order". When you decide something, RECORD IT HERE, not
only in a log entry, and link it (`links`) to the questions it answers and the items it settles.

A changed mind is never an edit of the outcome: `POST .../supersede` records a NEW decision; the old one
becomes `superseded`, pointing at the new one (`supersededById`), and the two are linked. Setting
`statusCode: "superseded"` yourself is refused (400).

A decision CLOSES what it settles: `GET .../open-links` lists the linked questions still open and the
linked items not closed; `POST .../settle` with the ones to close answers those questions BY the decision
and marks those items done, and writes one journal entry saying so. Only records that are linked and
still open can be named (else 400, and nothing changes); a proposed or superseded decision closes nothing
(409). Many decisions are already in the log as Decision entries: `POST /api/solutions/log/{logId}/decision`
makes one a decision in place (its first line is the title), and `.../question` does the same for a
question.

| Call | Note |
|---|---|
| `GET /api/solutions/{id}/questions` | one page, open first: `{questions, counts, offset, pageSize, total}`. Optional `status` (`open`, `answered`, `dropped`), `search` (question and detail), `offset`, `pageSize`. Each `{id, text, detail, truncated, statusCode, askedByCode, ownerCode, answeredByDecisionId, answeredByTitle, answeredDate, logId, logSequenceNumber, linkCount, createdDate, modifiedDate}`; `detail` is a 300-character preview. `counts`: `total`, `all`, `status:<code>`. |
| `GET /api/solutions/questions/{questionId}` | the whole question |
| `POST /api/solutions/{id}/questions` | `{text, detail, ownerCode, links}` -- `text` required (one line, at most 800 characters). `links`: `[{kind, id}]` records to link it to at once. The same question while it is still open is refused (409). |
| `PUT /api/solutions/questions/{questionId}` | `{text, detail, ownerCode}`. A merge: a field you leave out is kept; `""` clears `detail` or `ownerCode`. |
| `POST /api/solutions/questions/{questionId}/status` | `{statusCode}` -- `open`, `answered` (by hand) or `dropped`. Reopening clears the answer. |
| `DELETE /api/solutions/questions/{questionId}` | the question and its links |
| `POST /api/solutions/log/{logId}/question` | the log entry becomes an open question, asked by the entry's author; the two are linked |
| `GET /api/solutions/{id}/decisions` | the register, one page, proposed first: `{decisions, counts, offset, pageSize, total}`. Optional `status` (`proposed`, `accepted`, `superseded`, or `current` = not superseded), `search` (title, context, options, outcome), `offset`, `pageSize`. Each `{id, title, outcome, truncated, statusCode, decidedDate, decidedByCode, logId, logSequenceNumber, supersededById, supersededByTitle, answeredCount, linkCount, createdDate, modifiedDate}`; `outcome` is a 300-character preview and `context` / `options` come only with the GET of one. |
| `GET /api/solutions/decisions/{decisionId}` | the whole decision, with `supersedesId` / `supersedesTitle` when it replaced one |
| `POST /api/solutions/{id}/decisions` | `{title, context, options, outcome, statusCode, decidedDate, links}` -- `title` required (one line, at most 400 characters). `statusCode` `accepted` (the default) or `proposed`. |
| `PUT /api/solutions/decisions/{decisionId}` | same fields, a merge. Accept a proposal with `{"statusCode":"accepted"}`. Never touches its journal entry. |
| `POST /api/solutions/decisions/{decisionId}/supersede` | `{title, context, options, outcome, links}` -- the NEW decision; answers with it |
| `GET /api/solutions/decisions/{decisionId}/open-links` | `[{kind, id, title, sub}]` -- `kind` is `question` or `item` |
| `POST /api/solutions/decisions/{decisionId}/settle` | `{questionIds, itemIds}` -- close them. Answers `{decision, closed, stillOpen, logId, logSequenceNumber}`. |
| `DELETE /api/solutions/decisions/{decisionId}` | the questions it answered open again; the decision it superseded is current again; its journal entry stays |
| `POST /api/solutions/log/{logId}/decision` | the log entry becomes an accepted decision (decided by the entry's author, on its date); the two are linked |

### Deleting

| Call | Note |
|---|---|
| `DELETE /api/solutions/projects/{projectId}` | takes its items; log entries and notes are kept |
| `DELETE /api/solutions/items/{itemId}` | takes the item's whole sub-tree; the answer says how many went (`itemsRemoved`) |
| `DELETE /api/solutions/log/{logId}` | the number is never reused |
| `DELETE /api/solutions/notes/{noteId}` | takes its steps and every saved version |
| `DELETE /api/solutions/notes/{noteId}/steps/{stepId}` | the later steps are renumbered |

### Files

Attach a file to the solution, optionally to one item. Upload is `multipart/form-data`, not JSON.

| Call | Note |
|---|---|
| `GET /api/solutions/{id}/files` | optional `?itemId=` |
| `POST /api/solutions/{id}/files` | multipart fields: `file` (required), `title`, `notes`, `itemId`. At most 50 MB. A file the solution already holds -- the same bytes, under any name -- is refused with 409 and a message naming the copy that is there; nothing is added. A file whose name ends `.leosec` (an encrypted copy) is refused with 409 too: upload the original instead. |
| `GET /api/solutions/files/{fileId}` | the file's details |
| `PUT /api/solutions/files/{fileId}` | JSON `{title, notes, itemId}` |
| `GET /api/solutions/files/{fileId}/content` | the bytes, always as a download (`application/octet-stream`) |
| `DELETE /api/solutions/files/{fileId}` | detaches it |

### Conversations

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/conversations` | the threads of a solution; `awaitingFrom` says whose turn it is |
| `POST /api/solutions/{id}/conversations` | `{title, message}` -- opens a thread with your first message. The answer carries its `conversationId`. |
| `GET /api/solutions/conversations/{convId}/messages?since=<lastMessageId>` | only messages newer than the one you last saw |
| `POST /api/solutions/conversations/{convId}/messages` | `{body}` |
| `DELETE /api/solutions/{id}/conversations/{convId}` | takes the thread off this solution. You will not see it again. Only for a thread opened by mistake. |

- `awaitingFrom` on a thread is whose turn it is. After you post, it is a person's turn. When a
  person answers, it becomes yours -- **until you read the messages**, which marks them read and
  clears it. So note the answer when you read it; the flag will not remind you twice.
- Remember the `id` of the last message you saw and pass it as `since` next time.
- You cannot edit or delete a message once posted, or rename a thread -- a conversation is the
  record of what was asked and answered, and only the owner may correct it. If you got something
  wrong, post a correction.
- A thread's `status` is `active` or `closed`. The owner closes (resolves) a thread when the
  question is settled; a closed thread waits on nobody. You cannot resolve or reopen a thread
  (`PUT /api/solutions/conversations/{convId}/resolved` answers 403 to you), but posting a new
  message to a closed thread opens it again, so ask there if the topic comes back.

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
| `GET /api/solutions/{id}/test-plans?kind=qa` (or `dev`, or no kind for both) | the checklists, each with `kind`, `groupCount`, `caseCount`, `levelCount` (columns), `checkedCount`, `skippedCount` and `cellCount` |
| `POST /api/solutions/{id}/test-plans` | `{title, description, kind}` -- `title` required; `kind` is `dev` (default) or `qa`; anything else is refused (400) |
| `GET /api/solutions/test-plans/{planId}` | the whole grid: `columns`, `groups` (each with its `cases`), and the counters |
| `PUT /api/solutions/test-plans/{planId}` | `{title, description, sortOrder}` -- send only what changes |
| `DELETE /api/solutions/test-plans/{planId}` | the checklist and everything in it |
| `POST /api/solutions/test-plans/{planId}/columns` | `{title}` -- the same name twice in one checklist is refused (409) |
| `PUT /api/solutions/test-plans/columns/{columnId}` | `{title, sortOrder}` -- renaming keeps every tick |
| `DELETE /api/solutions/test-plans/columns/{columnId}` | takes that column's ticks; the last column cannot be removed (409) |
| `POST /api/solutions/test-plans/{planId}/groups` | `{title}` |
| `PUT` / `DELETE /api/solutions/test-plans/groups/{groupId}` | `{title, sortOrder}`; a delete takes the group's cases |
| `POST /api/solutions/test-plans/groups/{groupId}/cases` | `{text, note}` -- `text` required: what to try and what should happen |
| `PUT /api/solutions/test-plans/cases/{caseId}` | `{text, note, groupId, sortOrder, includeInPrint}` -- send only what changes; `groupId` moves it to another group of the SAME checklist; `includeInPrint: false` leaves the case off the printed checklist (every case prints unless told otherwise) |
| `DELETE /api/solutions/test-plans/cases/{caseId}` | |
| `PUT /api/solutions/test-plans/cases/{caseId}/checks/{columnId}` | ONE of: `{checked: true}` / `{checked: false}` to tick or untick, or `{skipped: true}` / `{skipped: false}` to skip or unskip. Both at once, or neither, is refused (400). Skipping unticks the cell; ticking unskips it |
| `PUT /api/solutions/test-plans/cases/{caseId}/result` | `{failing, note}` -- send only what changes (at least one): `failing: true` marks the case failing, `false` clears the mark; `note` says what is failing (`""` clears it) |
| `POST /api/solutions/test-plans/{planId}/clear` | `{columnId}` unticks one column; `{}` unticks the whole checklist and clears every failing mark (result notes stay), to run the tests again. Skipped cells stay skipped either way |
| `PUT /api/solutions/test-plans/groups/{groupId}/case-order` | `{ids: [caseId, ...]}` -- the group's cases in their new order |
| `PUT /api/solutions/test-plans/{planId}/group-order` | `{ids: [groupId, ...]}` -- the checklist's groups in their new order |

Every call except the list and a checklist delete answers with the WHOLE grid, so you always hold
the current counters. In it: `caseCount`, `checkedCount`, `skippedCount`, `failingCount` and `cellCount` (cases x columns) for the
checklist; `checkedCount` and `skippedCount` on each column; `caseCount`, `checkedCount`, `skippedCount`, `failingCount`,
`checkedByColumn` and `skippedByColumn` (`{columnId: count}`) on each group; and on each case, `failing`, `failingDate`,
`resultNote`, `resultDate`, `resultByName`, `checks` -- ONLY the ticked cells, each
`{columnId, checked, checkedDate, checkedByCode, checkedByName}` -- and `skips` -- ONLY the skipped cells, each
`{columnId, skippedDate, skippedByCode, skippedByName}`. A cell in neither list is not ticked and not skipped.

### Configuration

A solution's configuration: every property that matters, in GROUPS, with its value in each
ENVIRONMENT (Local, QA, Production to begin with; more may be added) and WHERE that value is read
from. When a property is set in several places, `source` is the one that WINS (the effective
value) and `alsoSetIn` lists the others. Record the EFFECTIVE value -- never a commented-out or
declared-but-overridden one.

Each property has a `typeCode`: `text`, `path`, `host`, `port`, `url`, `number`, `flag` or
`secret`. A **secret never stores a value** -- whatever you send as `value` is thrown away; only
`source`, `alsoSetIn` and `note` are kept. Use `secret` for anything restricted. A blank means
UNKNOWN, never "same as the default" -- leave a value blank rather than guess, and say what you know
in `note`.

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/config` | the whole tab: `environments`, `groups` (each with its `properties`, each with its `values` -- one per environment that has anything), `types`, `sources` (the place names already in use), `counts` |
| `POST /api/solutions/{id}/config/environments/defaults` | adds Local, QA and Production -- only when the solution has no environments yet |
| `POST /api/solutions/{id}/config/environments` | `{title}` |
| `PUT` / `DELETE /api/solutions/config/environments/{envId}` | `{title, sortOrder}`; a delete takes that environment's values; the last one cannot go (409) |
| `POST /api/solutions/{id}/config/groups` | `{title}` |
| `PUT` / `DELETE /api/solutions/config/groups/{groupId}` | `{title, sortOrder}`; a delete takes its properties |
| `POST /api/solutions/config/groups/{groupId}/properties` | `{key, description, typeCode}` -- `key` required, and unique within the solution (409 otherwise) |
| `PUT /api/solutions/config/properties/{propertyId}` | `{key, description, typeCode, groupId, sortOrder}` -- send only what changes. Changing to `secret` wipes the stored values. |
| `DELETE /api/solutions/config/properties/{propertyId}` | |
| `PUT /api/solutions/config/properties/{propertyId}/values/{envId}` | `{value, source, alsoSetIn: [..], note}` -- ONE cell. A field you leave out keeps its value; `""` or `[]` clears it. A place name cannot contain `\|`. |

Every call except a list answers with the whole tab. Each property carries `matchState`, worked
out on the server across its environments: `all` (every environment the same -- green), `some`
(some match -- orange), `none` (all different -- red), `incomplete` (some environment has no value),
`empty`, `secret`. Values are compared trimmed and ignoring case.

### Configuration II

The successor to Configuration, built beside it (Configuration keeps working, unchanged, until the
owner retires it). Same properties, groups, types, `source` / `alsoSetIn` / `note` rules and the same
SECRET rule -- but the columns are yours to define, and you can say how to compare them.

- **Columns** -- as many as needed (`QA - Tomcat 11`, ...). Each has an optional `color` (`#RRGGBB`).
  ONE column may be the **default** (`isDefault: true`, "applies to all"): its value stands in for
  any other column left blank. A second default is refused (409).
- **Axes** -- your own ways of describing a column, each with VALUES: `Environment` = Local / QA /
  Prod, `Server` = Tomcat 9 / Tomcat 11. A column carries at most one value per axis, set by NAME in
  `tags`: `{"Environment":"QA","Server":"Tomcat 11"}`. An axis or value you name that does not exist
  yet is created (on this solution only). `""` as a value removes that tag. Axis values may carry a
  `color` too, so the page can color columns by an axis.
- **Cell state** -- `state` on a cell: `observed` (the default -- seen on the running system),
  `planned` (the value it WILL have; the place does not exist yet or is not deployed), `unverified`
  (written down, not yet checked). Use `planned` for anything that is a deploy plan.

| Call | Body |
|---|---|
| `GET /api/solutions/{id}/config-grid` | the whole tab: `columns` (each with `tags`), `axes` (each with `values`), `groups` -> `properties` -> `values` (cells) and `matches`, `types`, `states`, `sources`, `counts`, `canImport` |
| `POST /api/solutions/{id}/config-grid/import` | copies the Configuration tab in (each environment becomes a column, every cell `observed`); only while Configuration II is empty (409 otherwise). Configuration itself is not changed. |
| `POST /api/solutions/{id}/config-grid/columns` | `{title, color, isDefault, tags: {axis: value}}` -- `title` required, unique within the solution |
| `PUT /api/solutions/config-grid/columns/{columnId}` | `{title, color, isDefault, sortOrder, tags}` -- send only what changes; `tags` names only the axes to change; `"color": ""` clears the color |
| `DELETE /api/solutions/config-grid/columns/{columnId}` | takes that column's cells; the properties stay |
| `POST /api/solutions/{id}/config-grid/axes` | `{title}` |
| `PUT` / `DELETE /api/solutions/config-grid/axes/{axisId}` | `{title, sortOrder}`; a delete takes its values and every column's tag on it (the columns stay) |
| `POST /api/solutions/config-grid/axes/{axisId}/values` | `{title, color}` |
| `PUT` / `DELETE /api/solutions/config-grid/axis-values/{valueId}` | `{title, color, sortOrder}`; a delete removes the tag from the columns that had it |
| `POST /api/solutions/{id}/config-grid/groups` | `{title}` |
| `PUT` / `DELETE /api/solutions/config-grid/groups/{groupId}` | `{title, sortOrder}`; a delete takes its properties |
| `POST /api/solutions/config-grid/groups/{groupId}/properties` | `{key, description, typeCode}` -- `key` unique within the solution |
| `PUT` / `DELETE /api/solutions/config-grid/properties/{propertyId}` | `{key, description, typeCode, groupId, sortOrder}` -- send only what changes |
| `PUT /api/solutions/config-grid/properties/{propertyId}/values/{columnId}` | `{value, source, alsoSetIn: [..], note, state}` -- ONE cell; a field you leave out keeps its value; `""` / `[]` clears it. A cell with no value, source, note or other place is removed. |

**How the colors are worked out -- `matches`.** Every property carries `matches`, keyed by the way of
comparing: `"all"` (every non-default column in one set) and each axis id as a string (one set per
value of that axis -- e.g. under Environment, `Local - Tomcat 9` is compared only with
`Local - Tomcat 11`; columns without a value on that axis form one more set). Each entry is
`{state, sets: [{valueId, columnIds, setCount, distinctCount, state}]}`. A set's state: `all` (green),
`some` (orange), `none` (red -- every column of the set differs), `incomplete` (a gap beside a known
value), `empty`, `single` (one column, nothing to compare), `secret`. The property's `state` is its
worst set. A blank column takes the default column's value before comparing. `counts` holds
`properties` and `state:<comparison>.<state>`, e.g. `state:all.some`, `state:12.none`.

## 5. Seeing everything

Nothing in a solution is hidden from you. To read all of it:

| You want | Call | Then, for the full text of one |
|---|---|---|
| The solution itself, and where things stand | `GET /api/solutions/{id}` | |
| Its projects (the main list), in order | `GET /api/solutions/{id}/projects` | `GET /api/solutions/projects/{projectId}` |
| Every item at every depth | `GET /api/solutions/{id}/items` | `GET /api/solutions/items/{itemId}` |
| The items of one project | `GET /api/solutions/{id}/items?projectId={projectId}` | |
| The log, in the order things were done | `GET /api/solutions/{id}/log?ascending=true` (page with `offset`, `pageSize`; `total` says how many) | `GET /api/solutions/log/{logId}` |
| The log of one item | `GET /api/solutions/{id}/log?itemId={itemId}&ascending=true` | |
| Notes (runbooks, strategies, references) | `GET /api/solutions/{id}/notes` | `GET /api/solutions/notes/{noteId}` -- body and steps |
| Older wordings of a note | `GET /api/solutions/notes/{noteId}/versions` | `GET .../versions/{n}` |
| Terms (the glossary) | `GET /api/solutions/{id}/terms?groupBy=none` (page with `offset`, `pageSize`; `total` says how many) | `GET /api/solutions/terms/{termId}` -- with its notes |
| Topics, and what is in each | `GET /api/solutions/{id}/topics` | `GET /api/solutions/{id}/links?kind=topic&recordId={topicId}` |
| What any record is linked to | `GET /api/solutions/{id}/links?kind={kind}&recordId={id}` | |
| What changed since your last session | `POST /api/solutions/{id}/visit` | |
| Files | `GET /api/solutions/{id}/files` (or `?itemId=`) | `GET /api/solutions/files/{fileId}/content` -- the bytes |
| Conversations | `GET /api/solutions/{id}/conversations` | `GET /api/solutions/conversations/{convId}/messages` |
| Testing checklists | `GET /api/solutions/{id}/test-plans` | `GET /api/solutions/test-plans/{planId}` -- the whole grid |
| Configuration | `GET /api/solutions/{id}/config` -- everything, in one call | |
| Configuration II | `GET /api/solutions/{id}/config-grid` -- everything, in one call | |

**Building the item tree.** The item list is flat. Each row has `id`, `projectId`,
`parentItemId` (null at the top level), `sortOrder` and `childCount`. Group rows by `projectId`;
within a project, the top-level items are those with no `parentItemId`; the sub-items of item X
are the rows whose `parentItemId` is X, in `sortOrder`; repeat to any depth. The list arrives
already in display order, so a single pass in the order given visits parents before children.

Lists cut long text short (`descriptionPreview`, `detailPreview`, `bodyPreview`, and log `text`
with `truncated: true`). When a preview is not enough, fetch the single record.

## 6. Every operation, one example each

`B` is the base URL. `H` stands for the two headers: `-H "X-Echo-Client: <code>" -H "X-Echo-Client-Key: $ECHO_KEY"`.
Read the key from your environment; never write it into a command you save.

```
# ---- projects
curl $H -X POST   $B/api/solutions/1/projects            -H "Content-Type: application/json" -d '{"name":"Deployment","statusCode":"todo"}'
curl $H           $B/api/solutions/1/projects
curl $H -X PUT    $B/api/solutions/projects/5            -H "Content-Type: application/json" -d '{"statusCode":"inprogress"}'
curl $H -X PUT    $B/api/solutions/projects/5            -H "Content-Type: application/json" -d '{"sortOrder":1}'
curl $H -X DELETE $B/api/solutions/projects/5

# ---- items and sub-items
curl $H -X POST   $B/api/solutions/projects/5/items      -H "Content-Type: application/json" -d '{"title":"Upgrade the server","assigneeCode":"<code>","priority":2,"dueDate":"2026-10-15"}'
curl $H -X POST   $B/api/solutions/projects/5/items      -H "Content-Type: application/json" -d '{"title":"Back up the config first","parentItemId":42}'
curl $H           $B/api/solutions/1/items
curl $H           $B/api/solutions/items/42
curl $H -X PUT    $B/api/solutions/items/42              -H "Content-Type: application/json" -d '{"detail":"Found two configs, not one."}'
curl $H -X POST   $B/api/solutions/items/42/status       -H "Content-Type: application/json" -d '{"statusCode":"blocked"}'
curl $H -X POST   $B/api/solutions/items/43/move         -H "Content-Type: application/json" -d '{"parentItemId":0,"sortOrder":1}'
curl $H -X DELETE $B/api/solutions/items/42

# ---- log
curl $H -X POST   $B/api/solutions/1/log                 -H "Content-Type: application/json" -d '{"typeCode":"command","itemId":42,"text":"mvn -q clean verify"}'
curl $H           "$B/api/solutions/1/log?ascending=true&pageSize=50&offset=0"
curl $H           "$B/api/solutions/1/log?since=118"
curl $H           $B/api/solutions/log/119
curl $H -X PUT    $B/api/solutions/log/119               -H "Content-Type: application/json" -d '{"text":"mvn -q clean verify -DskipITs"}'
curl $H -X DELETE $B/api/solutions/log/119

# ---- notes, versions, steps
curl $H -X POST   $B/api/solutions/1/notes               -H "Content-Type: application/json" -d '{"title":"How to deploy","typeCode":"runbook","body":"# Deploy\n...","changeNote":"first draft"}'
curl $H           "$B/api/solutions/1/notes?type=runbook"
curl $H           $B/api/solutions/notes/7
curl $H -X PUT    $B/api/solutions/notes/7               -H "Content-Type: application/json" -d '{"body":"# Deploy\n(revised)","changeNote":"added the rollback"}'
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
curl $H -X DELETE $B/api/solutions/1/terms/categories/5
curl $H -X DELETE $B/api/solutions/terms/12

# ---- links and topics
curl $H           "$B/api/solutions/1/link-targets?q=charset&kind=item&recordId=42"
curl $H -X POST   $B/api/solutions/1/links               -H "Content-Type: application/json" -d '{"fromKind":"item","fromId":42,"toKind":"note","toId":7}'
curl $H           "$B/api/solutions/1/links?kind=item&recordId=42"
curl $H -X DELETE $B/api/solutions/1/links/15
curl $H -X POST   $B/api/solutions/1/topics              -H "Content-Type: application/json" -d '{"name":"Charset","summary":"Read files as UTF-8; remit and submit stay windows-1252."}'
curl $H           $B/api/solutions/1/topics
curl $H -X POST   $B/api/solutions/1/links               -H "Content-Type: application/json" -d '{"fromKind":"log","fromId":505,"toKind":"topic","toId":3}'
curl $H           "$B/api/solutions/1/links?kind=topic&recordId=3"
curl $H -X PUT    $B/api/solutions/topics/3              -H "Content-Type: application/json" -d '{"summary":"Decided 23 Oct: UTF-8 in, windows-1252 for remit and submit."}'
curl $H -X DELETE $B/api/solutions/topics/3

# ---- questions and decisions
curl $H -X POST   $B/api/solutions/1/questions           -H "Content-Type: application/json" -d '{"text":"Which charset do the 837 files use?","ownerCode":"tony"}'
curl $H           "$B/api/solutions/1/questions?status=open"
curl $H -X POST   $B/api/solutions/1/decisions           -H "Content-Type: application/json" -d '{"title":"Read files in as UTF-8","outcome":"Remit and submit stay windows-1252.","links":[{"kind":"question","id":4},{"kind":"item","id":1200}]}'
curl $H           $B/api/solutions/decisions/9/open-links
curl $H -X POST   $B/api/solutions/decisions/9/settle    -H "Content-Type: application/json" -d '{"questionIds":[4],"itemIds":[1200]}'
curl $H -X POST   $B/api/solutions/decisions/9/supersede -H "Content-Type: application/json" -d '{"title":"Read everything as UTF-8"}'
curl $H           "$B/api/solutions/1/decisions?status=current"
curl $H -X POST   $B/api/solutions/log/505/decision
curl $H           $B/api/solutions/1/summary-age

# ---- since you were last here
curl $H -X POST   $B/api/solutions/1/visit
curl $H           "$B/api/solutions/1/since?offset=50&pageSize=50"
curl $H -X POST   $B/api/solutions/1/caught-up

# ---- files (upload is multipart, NOT json -- do not send a Content-Type header yourself)
curl $H -X POST   $B/api/solutions/1/files               -F "file=@./build.log" -F "title=Build log" -F "itemId=42"
curl $H           $B/api/solutions/1/files
curl $H           $B/api/solutions/files/9
curl $H -X PUT    $B/api/solutions/files/9               -H "Content-Type: application/json" -d '{"title":"Build log, second run"}'
curl $H           $B/api/solutions/files/9/content -o build.log
curl $H -X DELETE $B/api/solutions/files/9

# ---- conversations
curl $H -X POST   $B/api/solutions/1/conversations       -H "Content-Type: application/json" -d '{"title":"Which port does alpha use?","message":"Item 42. The config names 8080 and 8443. Which one is live? I would pick 8443."}'
curl $H           $B/api/solutions/1/conversations
curl $H           "$B/api/solutions/conversations/7/messages?since=19"
curl $H -X POST   $B/api/solutions/conversations/7/messages      -H "Content-Type: application/json" -d '{"body":"Thanks. Using 8443; item 42 is unblocked."}'

# ---- testing checklists
curl $H -X POST   $B/api/solutions/1/test-plans          -H "Content-Type: application/json" -d '{"title":"Upgrade testing","description":"Does the new stack do what the old one did?"}'
curl $H           $B/api/solutions/1/test-plans
curl $H -X POST   $B/api/solutions/1/test-plans          -H "Content-Type: application/json" -d '{"title":"QA acceptance","kind":"qa"}'
curl $H           $B/api/solutions/1/test-plans?kind=qa
curl $H           $B/api/solutions/test-plans/4
curl $H -X PUT    $B/api/solutions/test-plans/4          -H "Content-Type: application/json" -d '{"description":"Tick only what you saw pass."}'
curl $H -X POST   $B/api/solutions/test-plans/4/columns  -H "Content-Type: application/json" -d '{"title":"End to end"}'
curl $H -X PUT    $B/api/solutions/test-plans/columns/11 -H "Content-Type: application/json" -d '{"title":"End to end (local)"}'
curl $H -X POST   $B/api/solutions/test-plans/4/groups   -H "Content-Type: application/json" -d '{"title":"Payments coming in"}'
curl $H -X PUT    $B/api/solutions/test-plans/groups/20  -H "Content-Type: application/json" -d '{"title":"Payments coming in (835)"}'
curl $H -X POST   $B/api/solutions/test-plans/groups/20/cases -H "Content-Type: application/json" -d '{"text":"A payment file is picked up and archived","note":"Look in the archive folder"}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/301  -H "Content-Type: application/json" -d '{"groupId":21}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/301/checks/11 -H "Content-Type: application/json" -d '{"checked":true}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/301/checks/12 -H "Content-Type: application/json" -d '{"skipped":true}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/301/checks/12 -H "Content-Type: application/json" -d '{"skipped":false}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/302/result    -H "Content-Type: application/json" -d '{"failing":true,"note":"The file is picked up but never archived"}'
curl $H -X PUT    $B/api/solutions/test-plans/cases/302/result    -H "Content-Type: application/json" -d '{"failing":false}'
curl $H -X POST   $B/api/solutions/test-plans/4/clear    -H "Content-Type: application/json" -d '{"columnId":11}'
curl $H -X DELETE $B/api/solutions/test-plans/cases/301
curl $H -X DELETE $B/api/solutions/test-plans/groups/20
curl $H -X DELETE $B/api/solutions/test-plans/columns/11
curl $H -X PUT    $B/api/solutions/test-plans/groups/20/case-order -H "Content-Type: application/json" -d '{"ids":[303,301,302]}'
curl $H -X PUT    $B/api/solutions/test-plans/4/group-order -H "Content-Type: application/json" -d '{"ids":[21,20]}'
curl $H -X DELETE $B/api/solutions/test-plans/4

# ---- configuration
curl $H           $B/api/solutions/1/config
curl $H -X POST   $B/api/solutions/1/config/environments/defaults
curl $H -X POST   $B/api/solutions/1/config/environments    -H "Content-Type: application/json" -d '{"title":"Staging"}'
curl $H -X PUT    $B/api/solutions/config/environments/12   -H "Content-Type: application/json" -d '{"title":"Pre-production"}'
curl $H -X POST   $B/api/solutions/1/config/groups          -H "Content-Type: application/json" -d '{"title":"Scheduling"}'
curl $H -X POST   $B/api/solutions/config/groups/8/properties -H "Content-Type: application/json" -d '{"key":"claimsRemitTrigger.repeatInterval","description":"How often the remit folders are scanned","typeCode":"number"}'
curl $H -X PUT    $B/api/solutions/config/properties/40     -H "Content-Type: application/json" -d '{"description":"Suspected dead config"}'
curl $H -X PUT    $B/api/solutions/config/properties/40/values/2 -H "Content-Type: application/json" -d '{"value":"300000","source":"External properties file (via additional-location)","alsoSetIn":["Packaged application.properties"],"note":"From the upper Confluence page"}'
curl $H -X PUT    $B/api/solutions/config/properties/40/values/2 -H "Content-Type: application/json" -d '{"note":"Confirmed on a real host"}'
curl $H -X PUT    $B/api/solutions/config/properties/41/values/3 -H "Content-Type: application/json" -d '{"source":"Windows service -D args / OS environment"}'
curl $H -X DELETE $B/api/solutions/config/properties/40
curl $H -X DELETE $B/api/solutions/config/groups/8
# ---- configuration II (columns you define, axes, cell state)
curl $H           $B/api/solutions/1/config-grid
curl $H -X POST   $B/api/solutions/1/config-grid/import
curl $H -X POST   $B/api/solutions/1/config-grid/columns     -H "Content-Type: application/json" -d '{"title":"QA - Tomcat 11","tags":{"Environment":"QA","Server":"Tomcat 11"}}'
curl $H -X POST   $B/api/solutions/1/config-grid/columns     -H "Content-Type: application/json" -d '{"title":"All","isDefault":true}'
curl $H -X PUT    $B/api/solutions/config-grid/columns/31    -H "Content-Type: application/json" -d '{"tags":{"Server":"Tomcat 11"},"color":"#2563EB"}'
curl $H -X POST   $B/api/solutions/1/config-grid/axes        -H "Content-Type: application/json" -d '{"title":"Region"}'
curl $H -X POST   $B/api/solutions/config-grid/axes/4/values -H "Content-Type: application/json" -d '{"title":"East","color":"#16A34A"}'
curl $H -X PUT    $B/api/solutions/config-grid/axis-values/9 -H "Content-Type: application/json" -d '{"color":"#DC2626"}'
curl $H -X POST   $B/api/solutions/1/config-grid/groups      -H "Content-Type: application/json" -d '{"title":"Scheduling"}'
curl $H -X POST   $B/api/solutions/config-grid/groups/8/properties -H "Content-Type: application/json" -d '{"key":"claimsRemitTrigger.repeatInterval","typeCode":"number"}'
curl $H -X PUT    $B/api/solutions/config-grid/properties/40/values/31 -H "Content-Type: application/json" -d '{"value":"300000","source":"External properties file","state":"planned","note":"Deploy plan for QA on Tomcat 11"}'
curl $H -X PUT    $B/api/solutions/config-grid/properties/40/values/31 -H "Content-Type: application/json" -d '{"state":"observed"}'
curl $H -X DELETE $B/api/solutions/config-grid/columns/31
```

Every create and update answers with the saved record, so you never need a second call to learn
an id. Every delete answers 200 with a small confirmation.

## 7. A working session, start to finish

```
GET  /api/solutions/spec                                   read the contract
GET  /api/solutions/whoami                                 confirm who you are
GET  /api/solutions                                        pick the solution -> id 1
POST /api/solutions/1/visit                                what others changed since my last session
GET  /api/solutions/1/items                                see what is open and what is yours
POST /api/solutions/items/42/status   {"statusCode":"inprogress"}
  ... do the work ...
POST /api/solutions/1/log   {"typeCode":"command","itemId":42,"text":"mvn -q clean verify"}
POST /api/solutions/1/log   {"typeCode":"result","itemId":42,"text":"BUILD SUCCESS, 412 tests"}
POST /api/solutions/1/links {"fromKind":"item","fromId":42,"toKind":"topic","toId":3}   it belongs to the Charset topic
POST /api/solutions/items/42/status   {"statusCode":"done"}
GET  /api/solutions/conversations/7/messages?since=19      any answer to my question yet?
```
