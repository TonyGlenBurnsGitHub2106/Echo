package com.echo;

import com.echo.ApiClientModels.Credential;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Works out the {@link SolutionCaller} of the current request (Story 3493).
 *
 *   an API client          -> CLIENT, attributed to its client code, limited to its solutions
 *   the X-Echo-Key (house) -> SERVICE, attributed to the X-Echo-Actor header when it names
 *                             one ("stewart"), otherwise to "felix"
 *   a signed-in person     -> USER, attributed to "tony", the only person who signs in
 *                             to Leonardo. Never the login name: Tony signs in as "a", and filing
 *                             that made his posts read "Last from A" (2026-10-06)
 *   nobody (demo mode)     -> USER "demo"
 *
 * X-Echo-Actor is honoured ONLY for the house key. A client can never choose who
 * its writes are attributed to: its code is taken from the credential the filter
 * authenticated, not from anything the request says about itself.
 */
@Component
public class SolutionCallerResolver {

    static final String HEADER_ACTOR  = "X-Echo-Actor";
    static final String DEFAULT_ACTOR = "felix";
    static final String PERSON_CODE   = "tony";

    public SolutionCaller resolve(HttpServletRequest req) {
        Object attr = req.getAttribute(ApiClientPrincipal.REQUEST_ATTRIBUTE);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth == null ? null : auth.getName();

        if (attr instanceof Credential client)
            return new SolutionCaller(SolutionCaller.Kind.CLIENT, client.clientCode(), client.id());
        if (ApiClientPrincipal.isClient(name))      // a client principal with no credential attached: trust nothing
            throw new SolutionNotFoundException("Solution");

        if ("service".equals(name)) {
            String actor = req.getHeader(HEADER_ACTOR);
            String code = (actor == null || !actor.trim().matches("[A-Za-z0-9-]{1,20}")) ? DEFAULT_ACTOR : actor.trim().toLowerCase();
            return new SolutionCaller(SolutionCaller.Kind.SERVICE, code, null);
        }
        if (name == null || name.isBlank() || "anonymousUser".equals(name))
            return new SolutionCaller(SolutionCaller.Kind.USER, "demo", null);
        return new SolutionCaller(SolutionCaller.Kind.USER, PERSON_CODE, null);
    }
}
