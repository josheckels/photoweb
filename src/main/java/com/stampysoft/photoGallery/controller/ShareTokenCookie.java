package com.stampysoft.photoGallery.controller;

import com.stampysoft.photoGallery.ShareTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Where a visitor's share tokens live between visits. The session used to hold the <em>answer</em> - which
 * categories are unlocked - which meant unlocking lasted until the browser closed, the session idled out, or the
 * app restarted, and every one of those sent people back to the original link.
 * <p>
 * This cookie holds the tokens themselves, and they are re-validated against the database on every request. That
 * is the important property: the cookie is not a claim the server trusts, it is somewhere to keep a credential.
 * A hand-edited cookie matches no {@code category.share_token} and grants nothing, so there is no signature to
 * check and no signing key to manage. It also means <em>Regenerate share link</em> revokes immediately, which was
 * never quite true of the session version - a visitor who had already unlocked kept their access until their
 * session expired, because the session remembered the category rather than the token.
 * <p>
 * Not read by the SPA; see PRIVACY.md.
 */
final class ShareTokenCookie
{
    static final String COOKIE_NAME = "pw_unlock";

    /**
     * Tokens are base64url ({@code A-Za-z0-9-_}), so a dot can never appear inside one and needs no escaping.
     * Commas and semicolons would have to be quoted, which is why they aren't used here.
     */
    private static final String SEPARATOR = ".";

    /** Long enough that a link sent for last summer's party still works next summer. */
    private static final Duration MAX_AGE = Duration.ofDays(365);

    /** Newest wins once this many are held. Defined on {@link ShareTokens}, because the admin needs it too. */
    private static final int MAX_TOKENS = ShareTokens.MAX_HELD;

    private ShareTokenCookie()
    {
    }

    /** The tokens this visitor is holding, oldest first. Empty for anyone who has never used a share link. */
    static List<String> read(HttpServletRequest request)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
        {
            return List.of();
        }
        for (Cookie cookie : cookies)
        {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isEmpty())
            {
                List<String> tokens = new ArrayList<>();
                for (String token : cookie.getValue().split("\\" + SEPARATOR))
                {
                    if (!token.isBlank())
                    {
                        tokens.add(token);
                    }
                }
                return tokens;
            }
        }
        return List.of();
    }

    /**
     * Rewrites the cookie the visitor already has with a fresh expiry, so a year is a year of not visiting
     * rather than a year from the day the link was followed. Called from
     * {@link AbstractController#visibility} once a token has actually resolved to something, which means a
     * cookie holding only revoked tokens ages out instead of being renewed forever.
     * <p>
     * Does nothing for a visitor with no cookie, so the common anonymous request adds no header.
     */
    static void refresh(HttpServletRequest request, HttpServletResponse response)
    {
        List<String> tokens = read(request);
        if (!tokens.isEmpty())
        {
            write(response, String.join(SEPARATOR, tokens), MAX_AGE);
        }
    }

    /**
     * Adds tokens to whatever the visitor is already holding and writes the cookie back, in one header rather
     * than one apiece. Re-presenting a token already held moves it to the end rather than duplicating it, so the
     * ones just used are the last to be evicted; the given order is preserved among themselves, so presenting
     * more than {@link #MAX_TOKENS} at once keeps the last few rather than the first. Also restarts the expiry,
     * as {@link #refresh} does on every ordinary request.
     */
    static void add(HttpServletRequest request, HttpServletResponse response, List<String> newTokens)
    {
        LinkedHashSet<String> tokens = new LinkedHashSet<>(read(request));
        tokens.removeAll(newTokens);
        tokens.addAll(newTokens);

        List<String> kept = new ArrayList<>(tokens);
        if (kept.size() > MAX_TOKENS)
        {
            kept = kept.subList(kept.size() - MAX_TOKENS, kept.size());
        }

        write(response, String.join(SEPARATOR, kept), MAX_AGE);
    }

    /**
     * Forgets every token this browser holds. A persistent cookie on a borrowed or shared machine is exactly the
     * thing that lingers, so there has to be a way to put it back.
     */
    static void clear(HttpServletResponse response)
    {
        write(response, "", Duration.ZERO);
    }

    private static void write(HttpServletResponse response, String value, Duration maxAge)
    {
        // No Secure flag: nginx terminates TLS and Tomcat only ever speaks HTTP, so the app can't tell a secure
        // request from an insecure one without teaching it about forwarded headers. The browser only ever talks
        // to nginx, over HTTPS.
        //
        // SameSite=Lax, because a share link arrives as a top-level navigation from an email or a text message,
        // which Lax allows. Strict would drop the cookie on that first navigation, so the page would render as
        // though the visitor had nothing and then correct itself on the next request.
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .path("/")
                .httpOnly(true)
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
