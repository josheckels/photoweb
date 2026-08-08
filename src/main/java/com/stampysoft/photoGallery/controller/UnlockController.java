package com.stampysoft.photoGallery.controller;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.ShareTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The one way visibility changes. Replaces {@code /includePrivate}, which set private mode for anyone who
 * visited the URL.
 * <p>
 * There is no destination parameter: the token itself says where it goes, so there is no redirect target for a
 * caller to point somewhere else. A category token lands on that category, the owner token lands on the homepage,
 * and anything unrecognised lands on the homepage having changed nothing.
 * <p>
 * {@code t} may be repeated - {@code /unlock?t=x1&t=x2} - which is how somebody who was sent several events at
 * once gets them in a single click instead of having to follow each link in turn. Every valid token is kept; the
 * redirect goes to the first one naming a category the visitor can open, and to the homepage if none of them
 * does. An invalid token among good ones is dropped quietly rather than failing the lot, since the usual reason
 * for one is a link regenerated since the mail went out.
 * <p>
 * A token on a <em>private</em> category is accepted but names no destination. That is the person unhide link:
 * it lifts the opt-out hiding on that person's photos wherever they appear, while the category itself stays
 * invisible, so there is no page to send anybody to. Pair it with an event's token and the event supplies the
 * landing page. See PRIVACY.md.
 * <p>
 * The tokens are recorded in a long-lived cookie ({@link ShareTokenCookie}), so the link is something you follow
 * once per browser rather than every time the browser restarts.
 */
@RestController
public class UnlockController extends AbstractController {

    @Autowired
    private PhotoOperations photoOperations;

    @GetMapping(value = "/unlock")
    public ResponseEntity<Void> unlock(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(value = "t", required = false) List<String> presented,
            @RequestParam(value = "forget", required = false) String forget) {

        // Somewhere to put it back, for a borrowed laptop or a browser that was shown to the wrong person.
        if (forget != null)
        {
            ShareTokenCookie.clear(response);
            return redirectTo("/");
        }

        List<String> tokens = new ArrayList<>();
        if (presented != null)
        {
            for (String token : presented)
            {
                if (token != null && !token.isBlank())
                {
                    tokens.add(token);
                }
            }
        }
        if (tokens.isEmpty())
        {
            return redirectTo("/");
        }

        // Both looked up once for the whole batch rather than per token, the same way AbstractController does it.
        String ownerToken = photoOperations.getOwnerToken();
        Map<String, Category> categoriesByToken = photoOperations.getCategoriesByShareTokens(tokens);

        List<String> accepted = new ArrayList<>();
        String destination = null;
        for (String token : tokens)
        {
            Category category = categoriesByToken.get(token);
            boolean owner = ownerToken != null && ShareTokens.equalsConstantTime(ownerToken, token);
            if (!owner && category == null)
            {
                continue;
            }
            accepted.add(token);

            // The first token naming a category the visitor can actually open decides where we land. A person's
            // category is private, so it names no page and simply doesn't offer one - which is why an unhide
            // link is sent alongside an event's, and why a link with nothing but people in it lands on the
            // homepage with everything already unlocked.
            if (destination == null && category != null && !category.isPrivate())
            {
                destination = "/category/" + category.getCategoryId();
            }
        }

        if (accepted.isEmpty())
        {
            return redirectTo("/");
        }

        ShareTokenCookie.add(request, response, accepted);

        return redirectTo(destination == null ? "/" : destination);
    }

    private ResponseEntity<Void> redirectTo(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", location).build();
    }
}
