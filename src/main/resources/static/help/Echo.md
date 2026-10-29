# Echo

Echo is a small app of its own that echoes parts of Leonardo. It has its own sign-in, its own
database and its own address, so it can run on a computer that has no Leonardo on it, such as your
work machine. The first part it echoes is **Solutions**: every feature of Leonardo's Solutions, with
the same screens, buttons and behavior, minus the record links that only make sense inside Leonardo.
It looks and works like LeonardoWeb: the same icon band, Quick Launch, menu and Print menu. (An
Email Client was added on 10/26/2026 and taken out on 10/28/2026: the company's security policy does
not allow a program like Echo to read work mail.)

The help is in two parts; tap a heading to open it.

<details>
<summary><strong>Setting up and installing Echo</strong></summary>

### Running Echo

On the computer Echo is installed on, these files in the Echo folder do the work (double-click, or
run them in a command window):

| File | What it does |
|---|---|
| `install.cmd` | Installs Echo, start to finish. **Run it as administrator** (right-click -> Run as administrator); otherwise it stops and changes nothing. Finds the running SQL Server on this computer by itself (asks which one if several are running), and, if that SQL Server's TCP/IP is off, asks before switching it on and restarting SQL Server (other programs using it are disconnected for a moment; add `-RestartSqlServer` to say yes ahead of time). If the SQL Server the last install used is stopped, it stops and says so rather than use another. At the end it asks for your sign-in password; pressing Enter with nothing typed stops the install there, keeps what is done and does not start Echo -- run it again to finish. Safe to run again: a run over an existing install says so in yellow at the start, keeps your settings and sign-in, and so asks for no password. Each run is written to `logs\install-<date>.log`. |
| `start-echo.cmd` | Starts Echo in the background. |
| `stop-echo.cmd` | Stops it. |
| `update-echo.cmd` | Gets the newest Echo, backs up the database, updates it and restarts -- the same as **Settings -> Update Echo**. Nothing is compiled: it downloads the finished program. If the new Echo does not start, the one you had is put back. |
| `backup-echo.cmd` | Backs up the Echo database to a dated file. |
| `uninstall-echo.cmd` | Takes Echo off the computer again: backs up the database, removes it and Echo's SQL login (kept if another Echo database on that SQL Server still uses it), and moves Echo's settings, logs and files to the Recycle Bin. Run it as administrator. It asks twice before it changes anything: type YES, then type the database name (it tells you how many solutions go with it). Run `install.cmd` to put it back. |
| `set-password.cmd` | Sets a new password (or creates another sign-in). Also how a forgotten password is reset. A password too short or typed differently twice is asked for again; Enter with nothing typed cancels. |
| `export-solution.cmd` / `import-solution.cmd` | Copy a solution from Leonardo (or another Echo) into this Echo -- see above. |

---

### Bringing a solution over from Leonardo

A solution you already keep in Leonardo can be copied into Echo whole -- every project and item,
the log with its numbers, dates and authors, every note with all its versions and steps, the
terms, the testing and QA checklists with their ticks, both Configuration tabs, the file filing,
the conversations, the files themselves and the Web tab's web addresses. Two steps:

1. **At home**, in the Echo folder, write the solution to one file (`-Alias` is the solution's short
   name, or use `-SolutionId`):

   ```
   export-solution.cmd -SqlServer RENAISSANCE\SQL2012EXPRESS -Database Leonardo -Alias claims -OutFile C:\temp\claims.echo.json
   ```

2. **At work**, carry that file over and load it:

   ```
   import-solution.cmd -InFile C:\temp\claims.echo.json
   ```

The import keeps every id, so it refuses -- and changes nothing -- if this Echo already has that
solution (or anything else with the same ids). Who the solution is shared with is not copied: give
the agent its own key in this Echo and share it again. Logins on the Web tab are not copied either;
add them again in Echo if you need them there.

Copied a solution before Echo had a Web tab (before 10/26/2026)? Export it again at home, then at work
add just its web addresses -- nothing else is touched, and one already there is left alone:

```
import-solution.cmd -InFile C:\temp\claims.echo.json -WebAddressesOnly
``` The file holds the whole solution, so keep
it private and remove it yourself once it is in. The same two commands also move a solution from
one Echo to another (`-Database Echo` on the export).

### Technical details

> Everything below is for developers and for troubleshooting. If you just wanted to know what Echo
> does, you are done.

**What it is.** Java 21 / Spring Boot 4 (package `com.echo`), its own git repository
(`TonyGlenBurnsGitHub2106/Echo`, at home `C:\Tony\Dev\java\Echo`), its own SQL Server database
`Echo`, port **8770**, bound to 127.0.0.1 unless `echo.bind` says otherwise. Story 5653.

**Echoed, not shared.** Echo depends on nothing of Leonardo's at run time. The Solutions classes,
static pages and stored procedures were COPIED from LeonardoWeb and the Leonardo database with their
names unchanged, so a later change on either side can be carried across by comparing files of the
same name. Shared front-end files (auth.js, picasso-utils.js, quick-launch.js, the Picasso controls)
are LeonardoWeb's own, with small marked edits (passwords only, Echo's print destinations,
Quick Launch as the only pin destination, Arrange on Manage Quick Launch).

**Sign-in.** `AppUser` (PBKDF2-SHA256 hashes; the `_LOG` copy never holds the hash). The same
two-token shape as LeonardoWeb so its auth.js works unchanged: `POST /api/auth/login` answers an
access token (15 minutes) and sets the `echo_refresh` httpOnly cookie (30 days);
`POST /api/auth/refresh` mints a new access token. `SessionGeneration` is stamped into the refresh
token; a password change or Sign out everywhere bumps it. `POST /api/auth/verify-password` is the
step-up before API Clients (wrong password = 400, never 401, so auth.js does not take it as a lapsed
session). Scripts use the service key, header `X-Echo-Key`.

**The agent door.** `X-Echo-Client` + `X-Echo-Client-Key` (keys start `echo_`), allowlist
`/api/solutions/`, the same three fences as LeonardoWeb (`ApiClientAuthFilter` first in the chain,
`ApiClientPathFence` deny by default, `SolutionAccess`). The contract is
`GET /api/solutions/spec`, with this Echo's own address (`echo.public.url`) in place of
`{{BASE_URL}}`. An agent is refused (403) on every path outside `/api/solutions/**`.

**Database.** `db\tables.sql` (99 tables with their `_LOG` mirrors, scripted out of Leonardo with
SMO), `db\tables-app.sql`, `db\functions`, `db\views`, `db\procs`, `db\triggers` (one file each),
`db\seed.sql` (lookups with Leonardo's ids). Every script is idempotent;
`scripts\Install-EchoDatabase.ps1` runs them in that order. The Leonardo-only columns
`SolutionFile.LeonardoFileID` is kept (always NULL) so every table matches Leonardo's one for one.
Edited from Leonardo's copies: `stp_Solution_Delete` (no record links; unlinks the Web tab),
`stp_SolutionFile_Save` (storage code `local`), `stp_FileCategory_List` / `_Delete` (count and clear
`SolutionFileFiling`), the Quick Launch procs (`QuickLaunch`, not `LWQuickLaunch`).

**The Web tab and the Reader (10/26/2026).** A solution is an entity in Echo too: `stp_Solution_Save`
calls `stp_Entity_Create` (type 222) exactly as Leonardo's does, and an update gives older solutions
theirs. Echoed from Leonardo with their names: `tblEntity`, `tblEntityType` (four types: 7, 55, 204,
222), `tblWebAddress`, `tblWebAddressType`, `tblEntityWebAddressXRef`, `WebSiteType` /
`WebSiteTypeStyle` (the general types, Leonardo's ids), `EntityWebAddressFlag` / `Sequence`,
`LoginSetup`, `EntityLoginSetupXRef` (plus an Echo-only `_LOG`), `LoginSecret`, `LookupRegistry` /
`Reference`, and `ReaderHighlight` / `ReaderMarkNote` / `ReaderPosition`, each with its `_LOG`; their
procedures; the Java (`EntityWebAddress*`, `WebType*`, `EntityLogin*`, `LoginVault`, `Lookup*`,
`Reader*`); the pages (`contacts\entity-detail.js`, `entity-web/logins/phones`, `reader\`,
`picasso-tts.js`). Echo's own pieces: `GET /api/entity-browse?type=222` (`EntityBrowseController`,
`stp_Solution_GetEntity`) answers a solution's EntityID to the Web tab; `EntityRecordErrors` gives the
copied controllers Leonardo's error answers; `contacts\entity-detail-echo.js` switches off Pin / Needs
attention; `reader\reader-echo.css` hides the four Library actions; logins' Owning User is an Echo
sign-in (`AppUser`); `LoginVault` reads `echo.login.vault.key` (none is made, so a login is stored as
Leonardo stores an unencrypted one). All of it is house-only: an agent's key is fenced to
`/api/solutions/`.

**Files.** `echo.files.path` (default `EchoFiles` beside the jar), `EchoFiles\<solutionId>\<random>-<name>`,
StorageCode `local`; a removed file moves to `EchoFiles\trash`. Downloads are always attachments; a web
page is drawn in a sandboxed frame, as in LeonardoWeb.

**Configuration.** `config\application-local.properties` (git-ignored, written by the installer):
`spring.datasource.*`, `echo.jwt.secret`, `echo.service.key`, `echo.port`, `echo.bind`,
optionally `echo.files.path`, `echo.public.url`, `echo.session.inactivity-minutes`.
`config\echo-install.json` records the Java, SQL Server and port the scripts use. Logs:
`logs\echo.log`.

**Which SQL Server.** Without `-SqlServer` the installer (and `Install-EchoDatabase.ps1`,
`Set-EchoPassword.ps1`) uses the one `config\echo-install.json` recorded, else reads the instances
installed on this computer from the registry (`Instance Names\SQL`), tries each RUNNING one, uses
the only one that answers or asks which when several do; a stopped instance is never chosen or
started; when the recorded instance is stopped or does not answer, the installer stops rather than
fall back to another (the kept settings point at the recorded one). The installer and uninstaller
run as administrator (`Assert-EchoAdmin`): a chosen local instance with TCP/IP off, after a Y/N
question (or `-RestartSqlServer`), gets `SuperSocketNetLib\Tcp\Enabled = 1` and a restart of its service
(`Enable-EchoSqlTcp`), since Java connects only over TCP/IP; the services that depend on it (SQL
Server Agent) are started again. The first install at home failed on the old `localhost` default.
`-Password` (a SecureString) on `scripts\Install-Echo.ps1` / `scripts\Set-EchoPassword.ps1`, called
from PowerShell, is for scripted installs (a .cmd cannot pass one). Run elevated, the installer starts Echo through
`explorer.exe` so Echo itself runs as the signed-in person, not as administrator, and a plain
`stop-echo.cmd` can stop it; a re-run stops a running Echo before it rebuilds. Neither the
installer nor the uninstaller will touch Leonardo, Hearth or a system database
(`Assert-EchoDatabaseName`) -- Echo's procedures carry Leonardo's names. The installer marks a database it creates
with the extended property `EchoDatabase` before any table goes in, and both scripts accept only an
empty database or one with that mark or Echo's `AppUser` (`Test-EchoIsEchoDatabase`) -- so a first
install that failed halfway can be finished or uninstalled. The uninstaller checks before it stops
or asks anything, and drops a login only when no other database on the server maps a user to it.
A run given `-AdminSqlPassword` writes no install log (the log's header records the command line).

**SQL Server connection.** The installer turns a named instance into its TCP port (so SQL Browser
need not run) and turns encryption on for SQL Server 2016 and newer only (older ones cannot do the
TLS Java 21 requires). `-Auth SqlLogin` (default) makes the login `echo_app` (db_datareader,
db_datawriter, EXECUTE); `-Auth Windows` uses `integratedSecurity=true` and downloads
`lib\mssql-jdbc_auth-<version>.x64.dll`, passed to Java as `java.library.path`; since Echo runs
without administrator rights, the desktop account gets its own login and database user with the same
rights (recorded as `windowsLogin` in `echo-install.json` when the install made it, so uninstall removes it).

**Copying a solution.** `scripts\EchoSolutionTables.ps1` lists, table by table and parents first,
which rows make up one solution (and the shared lookups it uses). `Export-Solution.ps1` writes
those rows plus each file's bytes (base64) to one JSON file (`format: echo-solution/1`);
`Import-Solution.ps1` checks every id first, then writes everything in ONE transaction with
`SqlBulkCopy` (KeepIdentity, CheckConstraints, FireTriggers -- so the `_LOG` rows are made) and
reseeds each identity. A lookup row that exists with the same id must carry the same code or name.
`Conversation.Linked*` and `SolutionFile.LeonardoFileID` arrive NULL; files arrive as StorageCode
`local`. The solution gets a new EntityID here, and its web addresses (exported by value: name,
address, both types by name, notes, Important, order) are made again with Echo's procedures in the
same transaction -- an address Echo already has is linked, not duplicated. A failed import moves the
files it had written to `EchoFiles\trash`.

**Execution policy.** The `.cmd` files start PowerShell with `-ExecutionPolicy Bypass`, so the
scripts run on a machine whose policy is AllSigned or Restricted. A Group Policy that forbids
Bypass would still stop them.

</details>

<details>
<summary><strong>Using Echo</strong></summary>

### Signing in

Open Echo in a browser on the computer it runs on: [http://localhost:8770](http://localhost:8770). Type your user name
and password and tap **Sign in**. Your user name is the one `install.cmd` made -- **tony** unless it was
told otherwise -- and the password is the one you chose when the installer asked. Forgot it? Run
`set-password.cmd` to choose a new one. Echo keeps you signed in on that browser; tap the **Logout** icon
at the right of the icon band to sign out.

Five wrong passwords in a row lock the account for fifteen minutes.

### The icon band and the main menu

Every page has the icon band across the top, exactly as in LeonardoWeb:

| Icon | What it does |
|---|---|
| Back / Forward | The page you came from, and back again |
| Main menu | Echo's main menu |
| Quick Launch (lightning) | Your pinned pages, from anywhere |
| Search (magnifier) | Find a page in the menu by typing part of its name |
| Pin | Pin the page you are on to Quick Launch |
| Print | The Print menu for the page you are on |
| Logout (right) | Sign out |

The main menu has the **Quick Launch** row, a **Solutions** section, and an **Echo** section for
housekeeping (Manage Quick Launch, API Clients, Settings, Help). Tap a section's heading to fold or
open it; **Fold all** / **Open all** works them all at once. Echo remembers which sections you
folded, in its database, so every browser you use sees the same menu.

### Quick Launch

- **Pin a page**: open it, tap the **Pin** icon, pick an icon and a label, tap **Save**.
- **Open a pin**: tap its tile on the main menu, or tap the lightning icon on any page.
- **Put pins in order**: on the main menu, press and hold a tile and drag it. Or open
  **Echo -> Manage Quick Launch**, tap **Arrange**, drag the rows by their grips, and tap **Done**.
- **Rename, change the icon, or remove a pin**: Echo -> Manage Quick Launch -> **Edit** or **Delete**.
  A delete asks first.

### Solutions

Main menu -> Solutions -> Solutions. Everything here works as it does in LeonardoWeb, tab for tab:
Overview, Items, Log, Notes, Terms, Testing, QA, Configuration, Configuration II, Files,
Conversations, Web and Search, with Arrange, folding, the item tick, View and Edit on every record,
and the printed report. Open a PDF, a picture or a text file in the **Reader** from the Files tab
(**Reader** on the file): reading view, search, contents, bookmarks, highlights with notes, the
Notebook and its Print, and Listen -- and it remembers where you stopped. See *Solutions* in Leonardo's help for the full walkthrough; it describes Echo's
Solutions too.

What is different in Echo:

- **No linked records.** Leonardo can hang a solution on a matter, a person or a vendor; Echo has
  none of those. The Web tab has no **Pin** (pins go to Leonardo's Needs attention).
- **Files come from this computer.** **+ Add Files** offers Upload from this device (pick as many
  files as you like), Scan, Paste text and New text note. There is no Reading Nook or Leonardo Files
  library to pick from. While files are added a spinner says which one it is on ("Uploading 2 of 6"),
  and then the page scrolls to the new files in Staging, lit up (10/29/2026).
- **Files are kept in Echo's own files folder** on the computer Echo runs on (the Files tab says so).
  Removing a file moves it to a trash folder there; nothing is erased. There is no "from the
  Reading Nook" or "from Leonardo Files".
- **The Reader has no Library actions**: no Save Excerpt, Send to Quotes, Define or Send to Reading
  Nook. Research (Wikipedia, a web search) is there.
- **No quizzes.**

### Sharing a solution with an agent (Rex)

An outside agent -- Rex, say -- can work inside a solution you share with it, and nowhere else.

1. **Echo -> API Clients.** Echo asks for your password again first, because this page hands out keys.
2. Add a client (a short code such as `rex` and a name), then **Issue key**. The key is shown once:
   copy it straight into the agent's own secret store.
3. Open the solution -> Overview -> **Share**, and tick the client.
4. **Brief** (in the same Overview row) is the text you paste into the agent's instructions. Paste
   the key into the box and tap **Copy with key** to fold it in; the key never leaves the page.

The agent can read and change everything inside the shared solution. It cannot see any other
solution, cannot delete or share the solution, and cannot reach any other page or setting of Echo.

### The Print menu

Tap **Print** on any page. Echo's Print menu offers **Print on this device**, **Display** (opens the
PDF), **Download**, and **Cancel**. (Leonardo's other destinations -- the home printer, the Reading
Nook, email, a record -- belong to Leonardo.)

### Settings

Main menu -> Echo -> Settings:

- **Change your password.** Type the current one and the new one twice. Every other browser you are
  signed in to is signed out; this one stays signed in.
- **Sign out everywhere.** Ends every Echo session in every browser, this one included.
- **Update Echo.** One button brings Echo up to date: it gets the newest Echo, backs up the
  database, updates it and restarts. Echo is away for a minute or so; the page waits, then says how
  it went (**Show the log** has the details). If Echo was already up to date it says so and changes
  nothing. If the new Echo will not start, the one you had is put back and started.
- **About Echo.** The version, the release it is running, how long it has been running, the
  database it uses, and the folder that holds its files.

</details>
