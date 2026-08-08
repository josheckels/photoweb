package com.stampysoft.photoGallery.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.ShareTokens;
import com.stampysoft.photoGallery.Visibility;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Set;

public class AbstractController {

    protected final ObjectMapper objectMapper;

    public AbstractController() {
        this.objectMapper = new ObjectMapper();
        objectMapper.setFilterProvider(new SimpleFilterProvider().
                addFilter("photoFilter", SimpleBeanPropertyFilter.serializeAllExcept()).
                addFilter("categoryFilter", SimpleBeanPropertyFilter.serializeAllExcept()));
    }

    /**
     * What this request is allowed to see, derived from the tokens in the visitor's cookie and nothing else.
     * <p>
     * Deliberately not settable from a request parameter. It used to be: {@code ?private=true} on any of the API
     * endpoints turned private mode on for the session, with nothing to stop anyone doing it. The only way in is
     * now to present a token that matches one in the database.
     * <p>
     * The tokens are resolved on every request rather than cached in the session, which costs at most two small
     * indexed lookups and only for visitors who hold a token at all. In exchange, revoking a share link takes
     * effect at once instead of whenever the holder's session happens to expire. Every request costs the opt-out
     * scan inside {@link PhotoOperations#createVisibility} regardless, anonymous visitors included; the token
     * lookups sit on top of that rather than being the whole cost.
     * <p>
     * A visitor whose tokens still resolve gets their cookie's expiry restarted here, which is what makes the
     * year a year of not visiting rather than a year from the day they followed the link.
     */
    protected Visibility visibility(HttpServletRequest request, HttpServletResponse response, PhotoOperations photoOperations)
    {
        List<String> tokens = ShareTokenCookie.read(request);
        if (tokens.isEmpty())
        {
            return photoOperations.createVisibility(Set.of());
        }

        // Fetched once and compared here rather than asking the database per token, which would be a query
        // apiece for a cookie holding twenty of them.
        String ownerToken = photoOperations.getOwnerToken();
        if (ownerToken != null)
        {
            for (String token : tokens)
            {
                if (ShareTokens.equalsConstantTime(ownerToken, token))
                {
                    ShareTokenCookie.refresh(request, response);
                    return Visibility.OWNER;
                }
            }
        }

        Set<Integer> unlockedCategoryIds = photoOperations.getCategoryIdsByShareTokens(tokens);
        if (!unlockedCategoryIds.isEmpty())
        {
            ShareTokenCookie.refresh(request, response);
        }
        return photoOperations.createVisibility(unlockedCategoryIds);
    }
}
