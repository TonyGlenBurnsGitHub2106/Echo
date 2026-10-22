package com.echo;

import com.echo.ApiClientModels.ApiClient;

/**
 * The hand-off text for an outside agent (Story 3493): what a person pastes into
 * that agent's standing instructions. Its first job is to get the agent to fetch
 * the real contract, {@code GET /api/solutions/spec}, which is always current;
 * its second is to say how the key is kept and how to talk to the house.
 *
 * THE SERVER NEVER PUTS A KEY IN THIS TEXT, because it does not have one: only a
 * fingerprint of a key is stored. The text carries {@link #KEY_SLOT} where the
 * key goes. The page may fill that slot in the browser -- from the key the
 * person pastes, or at the one moment a key is issued -- and nowhere else. The
 * text never mentions the house key either: an outside agent is not told that
 * one exists.
 *
 * Echo (Story 5653): the base URL is this Echo's own ({@code echo.public.url}), the headers are
 * X-Echo-Client / X-Echo-Client-Key, and the Quizzes section is gone -- Echo has no quizzes.
 */
final class ApiClientBrief {

    /** Where the key goes. The page swaps this for the real key in the browser only. */
    static final String KEY_SLOT = "<PASTE-THE-KEY-HERE>";

    private ApiClientBrief() {}

    static String textFor(ApiClient c, String baseUrl) { return textFor(c, baseUrl, 0, null, null); }

    /** The brief, opened from one shared solution: it names that solution as the place to start. */
    static String textFor(ApiClient c, String baseUrl, int solutionId, String solutionName, String alias) {
        final String BASE_URL = baseUrl;
        String nl = "\n";
        boolean named = solutionId > 0 && solutionName != null && !solutionName.isBlank();
        return "# Working with the Solutions tracker" + nl + nl
            + "You have access to a shared tracker where this effort is planned, tracked and recorded." + nl
            + "It is the ONE place the team keeps its action items, its log of what was done, its runbooks" + nl
            + "and its questions. Use it instead of keeping your own lists." + nl + nl
            + (named ? "The solution shared with you is \"" + solutionName + "\" (id " + solutionId + ")." + nl
                + (alias == null || alias.isBlank() ? "" : "People call it \"" + alias + "\". When your human says \"" + alias + "\", this is the solution they mean;" + nl
                    + "GET " + BASE_URL + "/api/solutions/by-alias/" + alias + " returns it." + nl) + nl : "")
            + "## How to connect" + nl + nl
            + "- Base URL: " + BASE_URL + nl
            + "- Send these two headers on every request, and no other credential:" + nl
            + "  - X-Echo-Client: " + c.clientCode() + nl
            + "  - X-Echo-Client-Key: " + KEY_SLOT + nl
            + "- Content-Type: application/json" + nl + nl
            + "## The key -- read this twice" + nl + nl
            + "The X-Echo-Client-Key value above is a secret. These rules are absolute:" + nl + nl
            + "1. The key must NEVER be written into any file that is, or could ever be, committed to a" + nl
            + "   repository. Not a source file, not a config file, not a default or sample settings file," + nl
            + "   not a script, not a test, not a README, not a notes file, not a commit message." + nl
            + "2. The repository must never know this key exists. No file in it may contain the key, a" + nl
            + "   part of it, an encoded or encrypted copy of it, or a comment saying where it is kept." + nl
            + "3. Keep it only in a secret store or an environment variable that lives OUTSIDE every" + nl
            + "   repository, and read it from there at run time. If you generate code that calls the" + nl
            + "   tracker, that code reads the key from the environment -- it never contains it." + nl
            + "4. Never put the key in the tracker itself (an item, a log entry, a note, a file, a" + nl
            + "   message), in a log line, in an error report, or in anything you show or send to anyone." + nl
            + "5. If you ever find, or even suspect, that the key was written somewhere it should not be," + nl
            + "   stop and tell your human at once so it can be replaced. Do not try to tidy it up quietly." + nl + nl
            + "## First, every session" + nl + nl
            + "1. GET " + BASE_URL + "/api/solutions/spec" + nl
            + "   This is the complete, current contract: every endpoint, every field, every rule." + nl
            + "   Read all of it before doing anything else. If something is not in it, you cannot do it --" + nl
            + "   never guess at a path." + nl
            + "2. GET " + BASE_URL + "/api/solutions/whoami   to confirm the connection works." + nl
            + "3. GET " + BASE_URL + "/api/solutions          to see the solutions you were given." + nl + nl
            + "## The five rules" + nl + nl
            + "1. The tracker is the only list. Every action item goes in as an Item." + nl
            + "2. Log what you did, when you did it -- one entry per action, in order. Exact commands as" + nl
            + "   'command' entries, what came back as 'result' entries." + nl
            + "3. Keep statuses true: inprogress when you start, blocked (with a 'blocker' log entry) when" + nl
            + "   you cannot proceed, done when finished." + nl
            + "4. Ask questions in a Conversation, then wait (see the next section)." + nl
            + "5. You have full rights inside a solution shared with you, deletes included -- and a delete is" + nl
            + "   permanent. Prefer the status 'cancelled' for dropped work so the history stays." + nl + nl
            + "## Communications -- how to ask, and how to get the answer" + nl + nl
            + "A Conversation is how you talk to the people on the other side. It is a thread of messages" + nl
            + "inside a solution. Nobody on the other side is watching it live: a person reads your message" + nl
            + "and relays the answer, which can take hours. Work accordingly." + nl + nl
            + "- See the threads, and whose turn it is (the 'awaitingFrom' field):" + nl
            + "    GET  /api/solutions/{solutionId}/conversations" + nl
            + "- Open a new thread with your first message. One topic per thread, with a title that says" + nl
            + "  what it is about:" + nl
            + "    POST /api/solutions/{solutionId}/conversations      {\"title\": \"...\", \"message\": \"...\"}" + nl
            + "- Add to a thread that already exists rather than opening a second one on the same topic:" + nl
            + "    POST /api/solutions/conversations/{conversationId}/messages      {\"body\": \"...\"}" + nl
            + "- Check for an answer. Remember the id of the last message you saw and ask only for newer:" + nl
            + "    GET  /api/solutions/conversations/{conversationId}/messages?since={lastMessageId}" + nl + nl
            + "How to write a message that gets a useful answer:" + nl + nl
            + "- Make it stand on its own. The reader has not seen your session. Say what you are doing," + nl
            + "  what you tried, exactly what came back, and the one thing you need decided or answered." + nl
            + "- Name the item it is about (its id and title) so the answer can be tied to the work." + nl
            + "- Ask one question per message where you can. Offer the options you see and the one you" + nl
            + "  would pick, so the answer can be a single word." + nl
            + "- If the question blocks an item, set that item to 'blocked' and add a 'blocker' log entry" + nl
            + "  that points at the conversation. Then carry on with work that is not blocked." + nl
            + "- Do not re-ask and do not open a duplicate thread. Check with ?since= at the start of each" + nl
            + "  session and at most every few minutes within one. Never poll in a tight loop." + nl
            + "- When the answer arrives, act on it, log what you did, and unblock the item. If the answer" + nl
            + "  settles something others will need again, write it into a note (a runbook or a" + nl
            + "  strategy) so it is not buried in a thread." + nl
            + "- Tell your human at the start of each session what is waiting on an answer and what came in." + nl
            + "- A conversation is for questions and answers about the work. It is never a place for the" + nl
            + "  key, a password, or any other secret." + nl + nl
            + "## If a call fails" + nl + nl
            + "- 401 or 429: stop and tell your human. Do not retry in a loop." + nl
            + "- 403: that path or action is not yours. Do not retry; ask in a conversation." + nl
            + "- 404: re-read the list you got the id from." + nl
            + "- 409: read the 'message' field -- it says what rule refused the request -- adjust, retry once." + nl;
    }
}
