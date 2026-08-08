package com.stampysoft.photoGallery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The secrets behind share links. Two kinds, both just an opaque random string, and both stored in the database:
 * <ul>
 *   <li>a per-category token in {@code category.share_token}, which lets the people who were at an event see the
 *       photos of anyone who has opted out of appearing publicly - but only within that event;</li>
 *   <li>the single owner token in {@code app_setting}, which replaces the old {@code /includePrivate} URL that
 *       anybody could visit. Read and written through {@link PhotoOperations}.</li>
 * </ul>
 * 32 bytes of {@link SecureRandom}, so guessing one is not a threat model worth defending against; the compare is
 * constant-time anyway because there's no reason for it not to be.
 */
public final class ShareTokens
{
    private static final int TOKEN_BYTES = 32;

    /**
     * How many tokens one browser holds at a time, newest wins. A token is 43 characters, so twenty of them stay
     * an order of magnitude inside the 4KB-per-cookie browser limit; the cap only exists so somebody who collects
     * links for years can't eventually overflow the cookie and lose all of them at once.
     * <p>
     * Lives here rather than in the cookie because the admin needs it too: a combined share link that presents
     * more than this evicts its own first token, which is the one the redirect lands on.
     */
    public static final int MAX_HELD = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ShareTokens()
    {
    }

    /** A fresh token: 43 URL-safe characters. */
    public static String generate()
    {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean equalsConstantTime(String expected, String presented)
    {
        if (expected == null || presented == null)
        {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }
}
