package com.echo;

/**
 * Who is making a Solutions request (Story 3493), reduced to the two things the
 * family needs: the MEMBER CODE their writes are attributed to (tony, felix,
 * stewart, rex ...), and -- for a scoped API client -- the client id that limits
 * which solutions they may see. A person or the house itself has no client id
 * and sees everything.
 */
public record SolutionCaller(Kind kind, String memberCode, Integer apiClientId) {

    public enum Kind { USER, SERVICE, CLIENT }

    public boolean isClient() { return kind == Kind.CLIENT; }
}
