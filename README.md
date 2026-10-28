# Echo

Echo is a small app of its own that echoes parts of Leonardo -- its own sign-in, its own database,
its own address -- so it can run on a computer that has no Leonardo on it. The first part it echoes
is **Solutions**, whole, in the same LeonardoWeb look: the icon band, Quick Launch, the main menu
and the Print menu. Built for Story 5653.

What you can do in it is in Echo's Help (main menu -> Echo -> Help, or `src\main\resources\static\help\Echo.md`).

## Putting Echo on a computer

You need: **Java 21 or newer**, a **SQL Server** you can create a database on (Express is fine),
and **git**. Nothing else is installed by hand.

1. Get Echo:

   ```
   git clone https://github.com/TonyGlenBurnsGitHub2106/Echo.git C:\Echo
   ```

2. Install it. In `C:\Echo`, right-click `install.cmd` and choose **Run as administrator** (or run it
   from a Command Prompt opened as administrator). Without administrator rights it stops at once and
   changes nothing:

   ```
   install.cmd
   ```

   It finds the SQL Server on this computer by itself (if more than one is running, it lists them
   and asks you to pick one by number; a stopped one is never used). If that SQL Server has TCP/IP
   switched off -- SQL Server Express ships that way, and Echo can only connect over TCP/IP -- it
   asks, then switches it on and restarts that SQL Server (`-RestartSqlServer` says yes ahead of time). To use a particular one, or one on another computer, name
   it: `install.cmd -SqlServer .\SQLEXPRESS`. It finds Java, creates the Echo database, makes a SQL login for Echo with a random password,
   writes Echo's settings (with fresh random keys), builds Echo, asks you for the password you want
   to sign in with (at least 8 characters; a wrong one is simply asked for again, and pressing
   Enter with nothing typed cancels), and starts it. It is safe to run again.

3. Open `http://localhost:8770` and sign in as `tony` with that password.

Choices you may want on the `install.cmd` line:

| Add | When |
|---|---|
| `-Auth Windows` | The SQL Server takes Windows sign-ins only (the installer notices and switches by itself). |
| `-AutoStart` | Start Echo every time you sign in to Windows. |
| `-JavaHome C:\path\to\jdk-21` | Use this Java. Without it the installer picks the newest Java 21+ it finds (JAVA_HOME, the PATH, the registry, Program Files, `C:\Java`, `C:\JDK`, unzipped JDK folders, and every folder beside a Java it finds), and if none qualifies it lists the versions it did find and asks you to type the folder of one (Enter cancels). It must be a JDK (with `bin\javac.exe`), since Echo is built from source. |
| `-AdminSqlUser sa -AdminSqlPassword ...` | You do not administer the SQL Server; use an account that may create a database. |
| `-RestartSqlServer` | Restart SQL Server without asking if its TCP/IP must be switched on. |
| `-Port 8771` | Something else already uses 8770. |
| `-Bind 0.0.0.0` | Other computers should reach Echo too (it answers only this computer by default). |

## Day to day

| File | What it does |
|---|---|
| `start-echo.cmd` / `stop-echo.cmd` | Start or stop Echo. |
| `update-echo.cmd` | git pull, download the newest Echo release (no compiling), back up the database, update it, restart. If the new program does not start, the old one is put back. `-Build` compiles from source instead. Settings -> Update Echo does the same from the browser. |
| `backup-echo.cmd` | Back up the Echo database to a dated `.bak`. |
| `uninstall-echo.cmd` | Take Echo off this computer: backs the database up, drops it and Echo's SQL login (kept if another Echo database still uses it), and moves `config\`, `logs\`, `target\`, `lib\` and `EchoFiles\` to the Recycle Bin. It drops only a database that holds Echo's own tables -- never Leonardo or Hearth. Run as administrator; asks twice -- type YES, then the database name. `install.cmd` puts it back. |
| `set-password.cmd` | A new password, or another sign-in (`-Username someone`). Also resets a forgotten one. |
| `export-solution.cmd` / `import-solution.cmd` | Copy a whole solution from Leonardo (or another Echo) into this Echo. See Echo's Help, "Bringing a solution over from Leonardo". |

**Updates come from GitHub releases** (Story 5653). On the development computer, after a change is
committed and pushed, `tools\Publish-EchoRelease.ps1` builds that exact commit in a folder of its own and
posts `echo.jar` as a release (its notes carry the commit and the jar's sha256). `update-echo.cmd` and
Settings -> Update Echo download the newest release, check the hash, and swap it in; the program that was
running is kept in `target\previous\`, downloads in `target\download\`, and the release in use is recorded
in `config\echo-release.json`. Each update writes `logs\update-<date>.log` and `logs\update-state.json`.

Every `.cmd` starts its PowerShell script with `-ExecutionPolicy Bypass`, so the scripts run even
where PowerShell asks for signed scripts.

---

## Tests

Echo is tested with Spock specifications (Groovy, in `src\test\groovy\com\echo`), written to the
spock-tests rules: data tables with a `scenario` column, `||` before the expected results, nothing but
plain values in `where:`, every spec extending `EchoSpockBaseSpec`. Installing and updating never compile
or run them.

| Suite | How to run | Needs |
|---|---|---|
| Unit and web specs (`*Spec`) | `test-echo.cmd` (or `test-echo.cmd -Test SolutionServiceSpec`) | nothing -- no database, no browser |
| Database specs (`*ITSpec`) | `scripts\Test-EchoDatabase.ps1 -SqlServer <instance> -Port <port> -SqlUser <login> -SqlPassword <password>` | a SQL Server; it creates and uses only a throwaway database whose name starts with `EchoTest_` |
| Browser specs (`*E2ESpec`) | `set ECHO_E2E_PASSWORD=...` then `mvnw -Pe2e test -Decho.e2e.user=<user>` | a running Echo and a sign-in made for testing; the first run downloads Chromium |

A known bug is kept visible, not hidden: its spec is marked `@PendingFeature(reason='BUG: ...')`, so the
suite stays green while the bug is open, and the spec fails loudly once the bug is fixed (remove the
annotation then).

## Technical details

> For developers and troubleshooting.

- Java 21 / Spring Boot 4, package `com.echo`. Port 8770, bound to 127.0.0.1 by default.
- Database scripts in `db\` (tables, Echo's own tables, functions, views, procs, triggers, seed),
  every one idempotent; `scripts\Install-EchoDatabase.ps1` runs them in order.
- `config\application-local.properties` and `config\echo-install.json` are this computer's settings,
  written by the installer and git-ignored. So are `EchoFiles\` (solution files), `logs\`,
  `backups\` and `lib\`. Never commit them.
- Echo depends on nothing of Leonardo's at run time. Its Solutions code, pages and procedures were
  copied from LeonardoWeb with their names unchanged, so a change can be echoed across by comparing
  files of the same name. The full technical notes are at the end of `help\Echo.md`.
- `tools\Compare-WithLeonardo.ps1` (at home, where both databases live) reports every procedure,
  view, trigger or table column that differs between Leonardo and Echo -- the way to carry a later
  Leonardo change across.
- Building by hand: `mvnw package -DskipTests` with `JAVA_HOME` pointing at a Java 21 JDK; the jar is
  `target\echo.jar`.

Third-party code shipped in `src\main\resources\static`: marked (MIT), pdf.js (`reader\`, Apache 2.0),
both copied from LeonardoWeb.
