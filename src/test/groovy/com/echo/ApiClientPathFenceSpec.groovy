package com.echo

// Story 5653: the API-client path fence. An agent may reach /api/solutions/** and NOTHING else.
// Deny is the default; every doubt (dot segments, encodings, doubled slashes, backslashes,
// matrix parameters, case, control and non-ASCII characters) is a refusal.

import spock.lang.Unroll

class ApiClientPathFenceSpec extends EchoSpockBaseSpec {

    private static final String REX = '/api/solutions/'

    // ---- what an agent with the standard allowlist may reach -----------------------------

    @Unroll
    def "#seq. allows should serve the path when it is #scenario"() {

        expect:
        ApiClientPathFence.allows(REX, uri) == expected

        where:
        seq | scenario                         | uri                                         || expected
        1   | 'the subtree root, no slash'     | '/api/solutions'                            || true
        2   | 'the subtree root with a slash'  | '/api/solutions/'                           || true
        3   | 'a solution id'                  | '/api/solutions/1'                          || true
        4   | 'the spec'                       | '/api/solutions/spec'                       || true
        5   | 'whoami'                         | '/api/solutions/whoami'                     || true
        6   | 'a by-alias lookup'              | '/api/solutions/by-alias/rex-1'             || true
        7   | 'a nested collection'            | '/api/solutions/1/conversations'            || true
        8   | 'conversation messages'          | '/api/solutions/conversations/5/messages'   || true
        9   | 'a deep path'                    | '/api/solutions/1/items/2/log'              || true
        10  | 'unreserved punctuation'         | '/api/solutions/a-b_c~d.e'                  || true
        11  | 'a trailing slash'               | '/api/solutions/x/'                         || true
    }

    def "allows should serve the path when it is very long but plain"() {

        given:
        String uri = REX + getReplicatedString('a', 2000)

        expect:
        ApiClientPathFence.allows(REX, uri)
    }

    // ---- everything else is refused ------------------------------------------------------

    @Unroll
    def "#seq. allows should refuse the path when it is #scenario"() {

        expect:
        ApiClientPathFence.allows(REX, uri) == expected

        where:
        seq | scenario                               | uri                                         || expected
        1   | 'empty'                                | ''                                          || false
        2   | 'the root'                             | '/'                                         || false
        3   | 'api without a slash'                  | '/api'                                      || false
        4   | 'api with a slash'                     | '/api/'                                     || false
        5   | 'relative with an id'                  | 'api/solutions/1'                           || false
        6   | 'relative root'                        | 'api/solutions'                             || false
        7   | 'without api'                          | 'solutions/1'                               || false
        8   | 'a shorter name'                       | '/api/solution'                             || false
        9   | 'a shorter name with a slash'          | '/api/solution/'                            || false
        10  | 'a longer name'                        | '/api/solutionsx'                           || false
        11  | 'a longer name with an id'             | '/api/solutionsx/1'                         || false
        12  | 'a hyphenated sibling'                 | '/api/solutions-x/1'                        || false
        13  | 'an extension on the root'             | '/api/solutions.json'                       || false
        14  | 'an underscored sibling'               | '/api/solutions_old/1'                      || false
        15  | 'a sibling of api'                     | '/apix/solutions/1'                         || false
        16  | 'nested under another root'            | '/x/api/solutions/1'                        || false
        17  | 'the static solutions folder'          | '/solutions/'                               || false
        18  | 'the static solutions page'            | '/solutions/index.html'                     || false
        19  | 'the dashboard'                        | '/index.html'                               || false
        20  | 'auth.js'                              | '/auth.js'                                  || false
        21  | 'app.js'                               | '/app.js'                                   || false
        22  | 'the admin page'                       | '/admin/api-clients.html'                   || false
        23  | 'login'                                | '/api/auth/login'                           || false
        24  | 'refresh'                              | '/api/auth/refresh'                         || false
        25  | 'logout'                               | '/api/auth/logout'                          || false
        26  | 'change-password'                      | '/api/auth/change-password'                 || false
        27  | 'verify-password'                      | '/api/auth/verify-password'                 || false
        28  | 'logout-everywhere'                    | '/api/auth/logout-everywhere'               || false
        29  | 'ping'                                 | '/api/ping'                                 || false
        30  | 'system about'                         | '/api/system/about'                         || false
        31  | 'system health'                        | '/api/system/health'                        || false
        32  | 'system config'                        | '/api/system/config'                        || false
        33  | 'demo status'                          | '/api/system/demo/status'                   || false
        34  | 'the client admin root'                | '/api/api-clients'                          || false
        35  | 'the client admin root with a slash'   | '/api/api-clients/'                         || false
        36  | 'one client'                           | '/api/api-clients/1'                        || false
        37  | 'a client key'                         | '/api/api-clients/1/key'                    || false
        38  | 'the client access log'                | '/api/api-clients/access-log'               || false
        39  | 'a webhook'                            | '/api/webhooks/x'                           || false
        40  | 'step-up'                              | '/api/step-up/x'                            || false
        41  | 'version'                              | '/api/version/x'                            || false
        42  | 'quick launch'                         | '/api/quick-launch'                         || false
        43  | 'one quick launch pin'                 | '/api/quick-launch/1'                       || false
        44  | 'view state'                           | '/api/view-state'                           || false
        45  | 'pdf generic'                          | '/api/pdf/generic'                          || false
        46  | 'pdf printers'                         | '/api/pdf/printers'                         || false
        47  | 'file categories'                      | '/api/file-categories'                      || false
        48  | 'file category reorder'                | '/api/file-categories/reorder'              || false
        49  | 'solution config'                      | '/api/solution-config'                      || false
        50  | 'files'                                | '/api/files/1'                              || false
        51  | 'upper-case API'                       | '/API/solutions/'                           || false
        52  | 'all upper case'                       | '/API/SOLUTIONS/1'                          || false
        53  | 'title case'                           | '/Api/Solutions/1'                          || false
        54  | 'a capitalized subtree'                | '/api/Solutions/1'                          || false
        55  | 'an upper-case subtree root'           | '/api/SOLUTIONS'                            || false
        56  | 'a parent segment'                     | '/api/solutions/..'                         || false
        57  | 'a parent segment with a slash'        | '/api/solutions/../'                        || false
        58  | 'a climb to login'                     | '/api/solutions/../auth/login'              || false
        59  | 'a double climb'                       | '/api/solutions/../../api/auth/login'       || false
        60  | 'a climb from an id'                   | '/api/solutions/1/../../system/about'       || false
        61  | 'a climb with a matrix parameter'      | '/api/solutions/..;/auth/login'             || false
        62  | 'three dots'                           | '/api/solutions/...'                        || false
        63  | 'dots inside a segment'                | '/api/solutions/a..b'                       || false
        64  | 'a trailing parent segment'            | '/api/solutions/x/..'                       || false
        65  | 'a leading parent segment'             | '/../api/solutions/1'                       || false
        66  | 'encoded dots'                         | '/api/solutions/%2e%2e/auth/login'          || false
        67  | 'upper-case encoded dots'              | '/api/solutions/%2E%2E/auth/login'          || false
        68  | 'a dot then an encoded dot'            | '/api/solutions/.%2e/auth'                  || false
        69  | 'an encoded dot then a dot'            | '/api/solutions/%2e./auth'                  || false
        70  | 'encoded slashes'                      | '/api/solutions%2f..%2fauth%2flogin'        || false
        71  | 'an encoded slash'                     | '/api/solutions/%2fauth'                    || false
        72  | 'encoded backslashes'                  | '/api/solutions/%5c..%5cauth'               || false
        73  | 'double-encoded dots'                  | '/api/solutions/%252e%252e/auth'            || false
        74  | 'an encoded NUL'                       | '/api/solutions/%00'                        || false
        75  | 'an encoded space'                     | '/api/solutions/%20'                        || false
        76  | 'an encoded letter'                    | '/api/solutions/%41'                        || false
        77  | 'a lone percent'                       | '/api/solutions/%'                          || false
        78  | 'an encoded letter in the prefix'      | '/api/%73olutions/1'                        || false
        79  | 'overlong UTF-8 dots'                  | '/api/solutions/%c0%ae%c0%ae/auth'          || false
        80  | 'IIS-style unicode dots'               | '/api/solutions/%u002e%u002e/auth'          || false
        81  | 'a doubled leading slash'              | '//api/solutions/1'                         || false
        82  | 'a doubled slash after api'            | '/api//solutions/1'                         || false
        83  | 'a doubled slash after the root'       | '/api/solutions//1'                         || false
        84  | 'a doubled trailing slash'             | '/api/solutions//'                          || false
        85  | 'a tripled slash'                      | '/api/solutions///auth'                     || false
        86  | 'backslash parent segments'            | '/api/solutions\\..\\auth\\login'           || false
        87  | 'a backslash separator'                | '/api/solutions\\1'                         || false
        88  | 'all backslashes'                      | '\\api\\solutions\\1'                       || false
        89  | 'a backslash after api'                | '/api\\solutions/1'                         || false
        90  | 'a matrix parameter on the root'       | '/api/solutions;x=1'                        || false
        91  | 'a session id parameter'               | '/api/solutions/1;jsessionid=abc'           || false
        92  | 'a matrix climb'                       | '/api/solutions;/../auth/login'             || false
        93  | 'a bare semicolon'                     | '/api/solutions/;'                          || false
        94  | 'a space inside'                       | '/api/solutions/ 1'                         || false
        95  | 'a leading space'                      | ' /api/solutions/1'                         || false
        96  | 'a trailing space'                     | '/api/solutions/1 '                         || false
        97  | 'a tab'                                | '/api/solutions/\t'                         || false
        98  | 'a line feed'                          | '/api/solutions/\n'                         || false
        99  | 'a carriage return'                    | '/api/solutions/\r'                         || false
        100 | 'a NUL'                                | '/api/solutions/\u0000'                     || false
        101 | 'a unit separator'                     | '/api/solutions/\u001f'                     || false
        102 | 'DEL'                                  | '/api/solutions/\u007f'                     || false
        103 | 'a Latin-1 letter'                     | '/api/solutions/\u00e9'                     || false
        104 | 'full-width dots'                      | '/api/solutions/\uff0e\uff0e/auth'          || false
        105 | 'a division slash'                     | '/api/solutions/\u2215auth'                 || false
        106 | 'one-dot leaders'                      | '/api/solutions/\u2024\u2024/auth'          || false
        107 | 'an emoji'                             | '/api/solutions/\ud83d\ude00'               || false
    }

    // ---- the never-list wins over any allowlist ----------------------------------------

    @Unroll
    def "#seq. allows should refuse a never-listed path even when the allowlist names it: #scenario"() {

        given:
        String generous = '/api/auth/;/api/system/;/api/api-clients/;/api/webhooks/;/api/step-up/;/api/version/;/api/solutions/'

        expect:
        ApiClientPathFence.allows(generous, uri) == expected

        where:
        seq | scenario                     | uri                             || expected
        1   | 'auth root'                  | '/api/auth'                     || false
        2   | 'auth root with a slash'     | '/api/auth/'                    || false
        3   | 'login'                      | '/api/auth/login'               || false
        4   | 'refresh'                    | '/api/auth/refresh'             || false
        5   | 'verify-password'            | '/api/auth/verify-password'     || false
        6   | 'system root'                | '/api/system'                   || false
        7   | 'system root with a slash'   | '/api/system/'                  || false
        8   | 'system about'               | '/api/system/about'             || false
        9   | 'system health'              | '/api/system/health'            || false
        10  | 'client admin root'          | '/api/api-clients'              || false
        11  | 'client admin with a slash'  | '/api/api-clients/'             || false
        12  | 'a client key'               | '/api/api-clients/1/key'        || false
        13  | 'the access log'             | '/api/api-clients/access-log'   || false
        14  | 'webhooks root'              | '/api/webhooks'                 || false
        15  | 'webhooks with a slash'      | '/api/webhooks/'                || false
        16  | 'a webhook'                  | '/api/webhooks/github'          || false
        17  | 'step-up root'               | '/api/step-up'                  || false
        18  | 'step-up with a slash'       | '/api/step-up/'                 || false
        19  | 'step-up verify'             | '/api/step-up/verify'           || false
        20  | 'version root'               | '/api/version'                  || false
        21  | 'version with a slash'       | '/api/version/'                 || false
        22  | 'version latest'             | '/api/version/latest'           || false
    }

    def "allows should still serve the Solutions subtree when the allowlist also names never-listed paths"() {

        expect:
        ApiClientPathFence.allows('/api/auth/;/api/system/;/api/solutions/', '/api/solutions/1')
    }

    @Unroll
    def "allows should match never-listed names on whole segments only when #scenario"() {

        expect:
        ApiClientPathFence.allows(allow, uri) == expected

        where:
        scenario                       | allow             | uri                || expected
        'authors is not auth'          | '/api/authors/'   | '/api/authors/1'   || true
        'systems is not system'        | '/api/systems/'   | '/api/systems/1'   || true
        'versions is not version'      | '/api/versions/'  | '/api/versions/1'  || true
        'auth-help is not auth'        | '/api/auth-help/' | '/api/auth-help/x' || true
    }

    // ---- the allowlist itself ------------------------------------------------------------

    @Unroll
    def "#seq. allows should grant nothing when the prefix is #scenario"() {

        expect:
        ApiClientPathFence.allows(prefix, uri) == expected

        where:
        seq | scenario                         | prefix               | uri                     || expected
        1   | 'empty'                          | ''                   | '/api/solutions/1'      || false
        2   | 'blank'                          | '   '                | '/api/solutions/1'      || false
        3   | 'a lone separator'               | ';'                  | '/api/solutions/1'      || false
        4   | 'only separators'                | ';;;'                | '/api/solutions/1'      || false
        5   | 'the root'                       | '/'                  | '/api/solutions/1'      || false
        6   | 'the whole api'                  | '/api/'              | '/api/solutions/1'      || false
        7   | 'api without a slash'            | '/api'               | '/api/solutions/1'      || false
        8   | 'a prefix without a slash'       | '/api/x'             | '/api/x/1'              || false
        9   | 'solutions without a slash'      | '/api/solutions'     | '/api/solutions/1'      || false
        10  | 'solutions without a slash, root'| '/api/solutions'     | '/api/solutions'        || false
        11  | 'relative'                       | 'api/solutions/'     | 'api/solutions/1'       || false
        12  | 'missing the api slash'          | '/apisolutions/'     | '/apisolutions/1'       || false
        13  | 'outside api'                    | '/other/solutions/'  | '/other/solutions/1'    || false
        14  | 'a doubled slash'                | '/api//'             | '/api//x'               || false
        15  | 'a parent segment'               | '/api/../'           | '/api/../auth'          || false
        16  | 'dots inside'                    | '/api/so..x/'        | '/api/so..x/1'          || false
        17  | 'percent-encoded'                | '/api/s%2f/'         | '/api/s%2f/1'           || false
        18  | 'a matrix parameter'             | '/api/s;x/'          | '/api/s;x/1'            || false
        19  | 'a backslash'                    | '/api/s\\x/'         | '/api/s\\x/1'           || false
        20  | 'a space'                        | '/api/s x/'          | '/api/s x/1'            || false
        21  | 'non-ASCII'                      | '/api/\u00e9/'       | '/api/\u00e9/1'         || false
        22  | 'a star'                         | '*'                  | '/api/solutions/1'      || false
        23  | 'a star segment'                 | '/api/*/'            | '/api/solutions/1'      || false
        24  | 'a double star'                  | '/**'                | '/api/solutions/1'      || false
    }

    @Unroll
    def "allows should honor the shortest usable prefix when #scenario"() {

        expect:
        ApiClientPathFence.isUsablePrefix(prefix) == usable
        ApiClientPathFence.allows(prefix, uri) == served

        where:
        scenario                          | prefix    | uri        || usable | served
        'seven characters, an id'         | '/api/x/' | '/api/x/1' || true   | true
        'seven characters, the root'      | '/api/x/' | '/api/x'   || true   | true
        'six characters'                  | '/api//'  | '/api//x'  || false  | false
    }

    @Unroll
    def "allows should split on semicolons and trim each prefix when #scenario"() {

        given:
        String list = '  /api/quick-launch/ ;; /api/solutions/  ;'

        expect:
        ApiClientPathFence.allows(list, uri) == expected

        where:
        scenario                          | uri                    || expected
        'the second prefix matches'       | '/api/solutions/1'     || true
        'the first prefix matches'        | '/api/quick-launch/5'  || true
        'the first prefix root matches'   | '/api/quick-launch'    || true
        'no prefix matches'               | '/api/view-state'      || false
    }

    @Unroll
    def "allows should keep usable entries when the list also holds unusable ones: #scenario"() {

        expect:
        ApiClientPathFence.allows('/;/api/;/api/solutions/', uri) == expected

        where:
        scenario                          | uri                  || expected
        'the usable entry matches'        | '/api/solutions/2'   || true
        'only an unusable entry would'    | '/api/quick-launch'  || false
    }

    @Unroll
    def "allows should refuse when #scenario"() {

        expect:
        ApiClientPathFence.allows(allow, uri) == expected

        where:
        scenario                   | allow  | uri                 || expected
        'the allowlist is null'    | null   | '/api/solutions/1'  || false
        'the path is null'         | '/api/solutions/' | null      || false
        'both are null'            | null   | null                || false
    }

    // ---- the pieces ------------------------------------------------------------------------

    @Unroll
    def "#seq. isPlainPath should answer #plain when the path is #scenario"() {

        expect:
        ApiClientPathFence.isPlainPath(path) == plain

        where:
        seq | scenario                 | path                     || plain
        1   | 'the root'               | '/'                      || true
        2   | 'a plain path'           | '/api/solutions/1'       || true
        3   | 'unreserved punctuation' | '/a-b_c~d.e/f'           || true
        4   | 'sub-delimiters'         | '/!$&\'()*+,=:@'         || true
        5   | 'empty'                  | ''                       || false
        6   | 'a bare word'            | 'a'                      || false
        7   | 'relative'               | 'api/x'                  || false
        8   | 'a parent segment'       | '/..'                    || false
        9   | 'a climb'                | '/a/../b'                || false
        10  | 'a doubled slash only'   | '//'                     || false
        11  | 'a doubled slash inside' | '/a//b'                  || false
        12  | 'a backslash'            | '/a\\b'                  || false
        13  | 'a percent'              | '/a%2e'                  || false
        14  | 'a semicolon'            | '/a;b'                   || false
        15  | 'a space'                | '/a b'                   || false
        16  | 'a tab'                  | '/a\tb'                  || false
        17  | 'a NUL'                  | '/a\u0000'               || false
        18  | 'a trailing space'       | '/a '               || false
        19  | 'an exclamation mark'    | '/a!'               || true
        20  | 'a tilde'                | '/a~'               || true
        21  | 'DEL'                    | '/a\u007f'               || false
        22  | 'the first C1 control'   | '/a\u0080'               || false
        23  | 'y with diaeresis'       | '/a\u00ff'               || false
        24  | 'the last BMP character' | '/a\uffff'               || false
    }

    def "isPlainPath should throw when the path is null, so allows checks null first"() {

        when:
        ApiClientPathFence.isPlainPath(null)

        then:
        thrown(NullPointerException)
    }

    @Unroll
    def "#seq. isUsablePrefix should answer #usable when the prefix is #scenario"() {

        expect:
        ApiClientPathFence.isUsablePrefix(prefix) == usable

        where:
        seq | scenario                     | prefix              || usable
        1   | 'the Solutions subtree'      | '/api/solutions/'   || true
        2   | 'the shortest usable'        | '/api/x/'           || true
        3   | 'a nested subtree'           | '/api/a/b/'         || true
        4   | 'missing the trailing slash' | '/api/solutions'    || false
        5   | 'the whole api'              | '/api/'             || false
        6   | 'a doubled slash'            | '/api//'            || false
        7   | 'outside api'                | '/apix/'            || false
        8   | 'relative'                   | 'api/solutions/'    || false
        9   | 'upper-case API'             | '/API/solutions/'   || false
        10  | 'a parent segment'           | '/api/../'          || false
        11  | 'a doubled slash inside'     | '/api/a//b/'        || false
        12  | 'percent-encoded'            | '/api/a%2f/'        || false
        13  | 'a matrix parameter'         | '/api/a;b/'         || false
        14  | 'empty'                      | ''                  || false
    }

    // ---- observation: a lone "." segment ----------------------------------------------------

    @Unroll
    def "allows should treat a single-dot segment by position when #scenario"() {
        // The class comment says dot segments are refused; only ".." actually is. A lone "."
        // normalizes to the same directory, so it can never leave the allowed prefix -- this
        // feature records the real behavior so a change to it is noticed.

        expect:
        ApiClientPathFence.allows(REX, uri) == expected

        where:
        scenario                         | uri                       || expected
        'it is inside the subtree'       | '/api/solutions/./1'      || true
        'it ends the subtree path'       | '/api/solutions/.'        || true
        'it splits the prefix'           | '/api/./solutions/1'      || false
        'it leads the path'              | '/./api/solutions/1'      || false
    }
}
