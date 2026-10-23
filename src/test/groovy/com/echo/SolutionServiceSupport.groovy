package com.echo

import com.echo.SolutionModels.Solution

/**
 * Story 5653: the base of the Solutions-family SERVICE specs. A real {@link SolutionAccess} over a mocked
 * {@link SolutionRepository} and a mocked {@link ApiClientService}, and the callers every spec uses:
 *
 *   TONY   a signed-in person (USER, "tony")
 *   HOUSE  the service key (SERVICE, "felix")
 *   REX    API client 7 -- solution 1 is shared with him, solution 2 is not
 *   ZED    API client 8 -- nothing is shared with him
 *
 * Solutions 1 and 2 exist; every other id does not. Data tables name a caller by its upper-case name
 * and {@link #callerOf} turns the name into the caller in given:.
 */
abstract class SolutionServiceSupport extends EchoSpockBaseSpec {

    static final SolutionCaller TONY  = new SolutionCaller(SolutionCaller.Kind.USER, 'tony', null)
    static final SolutionCaller HOUSE = new SolutionCaller(SolutionCaller.Kind.SERVICE, 'felix', null)
    static final SolutionCaller REX   = new SolutionCaller(SolutionCaller.Kind.CLIENT, 'rex', 7)
    static final SolutionCaller ZED   = new SolutionCaller(SolutionCaller.Kind.CLIENT, 'zed', 8)

    SolutionRepository solutionRepo = Mock()
    ApiClientService clients = Mock()
    SolutionAccess access = new SolutionAccess(solutionRepo, clients)

    Set<Integer> solutionIds = [1, 2] as Set
    Set<List<Integer>> shares = [[7, 1]] as Set

    def setup() {
        solutionRepo.findSolution(_) >> { int id -> solutionIds.contains(id) ? solution(id) : null }
        clients.maySee(_, _) >> { int c, int s -> shares.contains([c, s]) }
    }

    static Solution solution(int id) {
        Solution.of([SolutionID: id, SolutionName: 'Solution ' + id])
    }

    /** TONY | HOUSE | REX | ZED to the caller; null stays null. */
    static SolutionCaller callerOf(String name) {
        switch (name) {
            case 'TONY':  return TONY
            case 'HOUSE': return HOUSE
            case 'REX':   return REX
            case 'ZED':   return ZED
            default:      return null
        }
    }

    /** A text of the given length (for length limits); null stays null. */
    static String textOf(Integer length) {
        length == null ? null : getReplicatedString('x', length)
    }

    /** "a,b" to ["a", "b"]; "" to an empty list; null stays null; the token NULL is a null element. */
    static List<String> listOf(String csv) {
        if (csv == null) {
            return null
        }
        if (csv.isEmpty()) {
            return []
        }
        List<String> out = []
        for (String s : csv.split(',', -1)) {
            out << (s == 'NULL' ? null : s)
        }
        return out
    }

    /** "1,2" to [1, 2]; "" to an empty list; null stays null. */
    static List<Integer> intsOf(String csv) {
        csv == null ? null : (csv.isEmpty() ? [] : csv.split(',').collect { String s -> Integer.valueOf(s.trim()) })
    }
}
