package com.echo;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * How a scoped API client is named inside the security context (Story 3493):
 * {@code client:<code>}. The prefix cannot collide with a real user name or with
 * the {@code service} principal, so any gate can tell a client apart with one
 * string test -- and every gate that means "a person or the house itself" must
 * answer NO to it.
 */
public final class ApiClientPrincipal {

    public static final String PREFIX = "client:";

    /** Request attribute carrying the authenticated {@link ApiClientModels.Credential}. */
    public static final String REQUEST_ATTRIBUTE = "leo.apiClient";

    private ApiClientPrincipal() {}

    public static String nameOf(String clientCode) { return PREFIX + clientCode; }

    public static boolean isClient(String principalName) {
        return principalName != null && principalName.startsWith(PREFIX);
    }

    /** True when the CURRENT request was authenticated as an API client. */
    public static boolean currentIsClient() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && isClient(auth.getName());
    }
}
