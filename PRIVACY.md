# Privacy: hidden people tags, per-person opt-out, and event share links

Three things, built on one visibility rule:

1. **People tags are yours alone.** Person categories are marked `private`, which the existing
   filtering already honours. The photo stays public; only the "who is in it" goes away.
2. **A person can opt out of appearing publicly.** Photos tagged with an opted-out person drop out
   of every anonymous listing and can't be fetched by id.
3. **An event category can be shared by link.** A token on the URL re-reveals the opted-out
   people's photos *from that event only*, on the theory that everyone at the event already saw
   them there.

## Decisions taken

| Question | Decision |
|---|---|
| Image bytes | **Listing-only.** The API hides them; the CDN URL still works if guessed. See [Known limitations](#known-limitations). |
| What a share link unlocks | The opt-out-hidden photos in that category's subtree, and no other photos. Not People tags, not `private` photos, not other categories. Those photos then show wherever they appear — see [What a token unlocks is photos, not places](#what-a-token-unlocks-is-photos-not-places). |
| A token on a person | Allowed, and called an **unhide link**: it shows that person's photos from every event, for the family who'd want them. It never makes the person's category visible, so the tag and the name stay hidden. See [Unhide links](#unhide-links). |
| Owner access | `/includePrivate` is deleted. A token in the `app_setting` table replaces it, minted from the admin UI. |
| Where unlock state lives | A year-long `pw_unlock` cookie holding the raw tokens, re-validated every request. Not the session, which died on browser restart; not signed category ids, which would need a key and wouldn't revoke. Owner and share tokens share the cookie. |
| What counts as "in a photo" | The confirmed category tag only. Unconfirmed face proposals hide nothing. |
| Hiding People tags | Reuse `category.private`. New categories inherit the parent's flag. |
| Share tokens | One per category, regenerable. |
| A hidden cover photo | Category renders with no cover image. |
| Frontend | Backend only. The SPA needs no changes — see [SPA contract](#spa-contract). |

## Data model

Two new columns on `category`, and one small table for the owner's own token.

```sql
ALTER TABLE category ADD COLUMN public_opt_out BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE category ADD COLUMN share_token    VARCHAR(43) NULL;
CREATE UNIQUE INDEX category_share_token_idx ON category (share_token);

CREATE TABLE app_setting (
  setting_name  VARCHAR(64) PRIMARY KEY,
  setting_value VARCHAR(255)
);
```

Applied by `src/main/resources/sql/privacy-schema.sql`. `VARCHAR` rather than `CHAR` for the token because
Postgres pads a `bpchar` on read, which is a trap waiting for the day a token isn't exactly 43 characters.

- **`public_opt_out`** — meaningful only on person categories (descendants of `PeopleCategoryId`).
  True means: hide every photo linked to this category from anonymous visitors.
- **`share_token`** — 32 random bytes, base64url, 43 chars. Null means the category isn't shared.
  Regenerating overwrites it, which kills the old link.
- **`app_setting`** — currently one row, `owner.token`, holding the same kind of secret. In the database
  rather than `src/config.properties` because that file is tracked in git, and because rotating the token
  should be a button rather than an edit and a restart. No row means owner unlock is off.

`Category` gains `_optOut` and `_shareToken` fields mirroring the existing `_private` mapping in
`BaseCategory`.

### Backfill (yours to run)

Person categories need `private = true` set once. The recursive descendants of the People root:

```sql
WITH RECURSIVE people AS (
    SELECT category_id FROM category WHERE category_id = 169   -- PeopleCategoryId
    UNION ALL
    SELECT c.category_id FROM category c JOIN people p ON c.parent_category_id = p.category_id
)
UPDATE category SET private = true WHERE category_id IN (SELECT category_id FROM people);
```

### Inheritance on create

New categories copy the parent's `private` flag, so anything added under People is private without
you thinking about it. Two creation sites:

- `CategoryTree.java:99` (the *Insert* menu item) — `newCategory.setPrivate(parent != null && parent.isPrivate())`.
  Root-level inserts get `false`.
- `PeopleService.java:239` (person categories created by the face code) — same rule, which under a
  private People root means always private.

The flag stays editable afterwards, so a public sub-event under a private parent is still possible.

## The visibility rule

One predicate, one place. A `Visibility` object replaces the `boolean includePrivate` parameter
threaded through `PhotoOperations`:

```java
public final class Visibility {
    public static final Visibility OWNER = ...;   // admin Swing UI, and the owner's session
    boolean owner;
    Set<Integer> hiddenPhotoIds;     // photos linked to any public_opt_out category
    Set<Integer> unlockedPhotoIds;   // photos in the subtree of any unlocked category
    Set<Integer> unlockedCategoryIds;

    public boolean canSee(Photo p) {
        if (owner) return true;
        if (p.isPrivate()) return false;
        return !hiddenPhotoIds.contains(p.getPhotoId())
            || unlockedPhotoIds.contains(p.getPhotoId());
    }
    public boolean canSee(Category c) { return owner || !c.isPrivate(); }
}
```

Both id sets are computed once per request, in `AbstractController`:

```sql
-- hiddenPhotoIds
SELECT DISTINCT pcl.photo_id FROM photo_category_link pcl
  JOIN category c ON c.category_id = pcl.category_id
 WHERE c.public_opt_out = true;
```

`unlockedPhotoIds` is only queried when the visitor holds a token. A **public** category expands to
its whole subtree; a **private** one expands to itself alone. The children map `collectSubtreeIds`
walks is built from the public categories only, so expansion can never descend into a private
subtree — a private category named directly is simply added as a single id.

Holding both as id sets means the rule is written once and applied in memory everywhere — no
parallel JPQL version to drift out of sync. If `hiddenPhotoIds` ever gets big enough to notice, it
caches at the application level with invalidation on opt-out changes and photo/category link writes;
start without the cache.

### What a token unlocks is photos, not places

`unlockedPhotoIds` is a set of photo ids, so the exemption follows the photo rather than the page it
is being rendered on. A guest holding the "Wedding 2024" link sees the opted-out photos from that
event *anywhere they appear* — under "Family", as a cover image on `/api/categories`, by direct id.
It does not give them any photo that isn't in the wedding subtree.

This is deliberate. Photos are many-to-many with categories, so a photo filed under both the event
and something else is the normal case, not an edge one. Keying the exemption on the (photo,
category) pair instead would show the guest a photo under the wedding and a gap where the identical
photo sits under Family, which reads as a bug and buys nothing: the thing being protected is the
person's face, and the link has already shown it to them.

### Unhide links

A token on a **person's** category is allowed, and means something different from an event's: it
lifts the opt-out hiding on that person's photos *everywhere*, not within one event. It exists for
direct family, who want the other events the same relatives were at.

What makes this safe to allow is that photo visibility and category visibility are separate checks.
`canSee(Category)` is `!isPrivate()` and reads neither id set, so **no token can ever make a category
visible.** A person's category stays out of `/api/categories`, out of its parent's subcategory list,
and out of every photo's category list. The holder sees the photos; they never learn the name, the
tag, or that the person is a category at all. `canSee(Photo)` still checks `private` before the
unlock set, so a private photo stays hidden too.

Two rules bound it. `getCategoryIdsByShareTokens` accepts a private category **only when it has
opted out** — `c._private = false OR c._optOut = true` — so a token on an event you later mark
private still stops working, as it always did, and a token on the People root does nothing, the root
not being a person. Then the asymmetric expansion above means an accepted person contributes the
photos linked to their category and nothing else.

The corollary is that an unhide link minted before you tick *Hide photos publicly* does nothing
until you do, since there is nothing hidden for it to put back. The copy dialog says so.

`/unlock` accepts such a token but takes no destination from it — a private category names no page
anyone but the owner can open. Sent alone it lands on the homepage with the photos already unlocked;
sent alongside an event's token, the event supplies the landing page. That is what the admin's
*Copy combined share link* builds.

### Selected categories have to be visible in their own right

`getPhotosInAllCategories` builds its parent → children map from the categories the visitor can see,
which stops it *descending* into a private subtree. That is not the same as refusing a private
category named directly — such a category simply has no children in the map and expands to itself —
so the selected ids are checked against the visible set before expansion. Otherwise
`/api/photos?categories=<a person's category id>` would hand an anonymous caller that person's
non-private photos, which is exactly the tag membership `private` is there to hide. An invisible
selection empties the whole answer, since the endpoint is an intersection.

`Visibility.OWNER` is what the Swing admin passes, so `PhotoOperations` callers inside the admin are
a mechanical `true` → `Visibility.OWNER` substitution.

## Cookies and tokens

### `GET /unlock?t=<token>`

Validates the token with a constant-time compare, records it in a cookie, and 302s to wherever that
token implies. The token already says where it goes, so there's no destination parameter — and
therefore no open-redirect surface to guard.

- `t` matching some `category.share_token` → the token joins the visitor's cookie, and the redirect
  goes to `/category/<that id>`. Multiple tokens accumulate across visits, so a visitor can hold
  links to several events.
- `t` matching the `owner.token` row in `app_setting` → same cookie, redirect to `/`. Equivalent to
  the old `includePrivate`.
- No match → 302 to `/` with nothing set.
- `?forget=1` → clears the cookie. There's no other way to give access back on a shared machine.

**`t` may be repeated**: `/unlock?t=x1&t=x2` unlocks both in one click, which is the link you send
somebody who was at three of the weekend's events rather than making them follow three. Every valid
token is kept; the redirect goes to the **first token naming a category the visitor can open**, and
to `/` if none of them does — so an unhide link contributes access without ever contributing a
destination. Tokens that match nothing are dropped quietly rather than failing the batch, since the
usual reason for one is a link regenerated since the mail went out. All of them together are one
owner-token read and one `share_token IN (…)`, not a query apiece.

If more than the cookie's twenty are presented at once the last twenty win, which is the same
newest-wins rule that applies across visits.

### The cookie holds the tokens, not the answer

`pw_unlock` is a list of tokens separated by `.` (safe, because base64url has no dot), and
`AbstractController.visibility()` re-resolves them against the database on every request. The cookie
is **not an assertion the server trusts** — it's somewhere to keep a credential. A hand-edited cookie
matches no `share_token` and grants nothing, so there is no signature to verify and no signing key to
manage.

Storing the tokens rather than the resolved category ids is what makes *Regenerate share link*
actually revoke. The earlier session-based version remembered the category, so a visitor who had
already unlocked kept their access until their session expired no matter how many times you
regenerated.

| Attribute | Value | Why |
|---|---|---|
| `HttpOnly` | true | The SPA never reads it |
| `Secure` | not set | nginx terminates TLS in front of Tomcat, which only ever speaks HTTP, so the app can't tell a secure request from an insecure one without being taught about forwarded headers. The browser only ever talks to nginx, over HTTPS |
| `SameSite` | `Lax` | A share link arrives as a top-level navigation from an email or a text. `Strict` would drop the cookie on that first navigation, so the page would render empty and then correct itself |
| `Path` | `/` | `/unlock` and `/api/*` both need it |
| `Max-Age` | 365 days, restarted on every request whose tokens still resolve | The point of the exercise |

Twenty tokens max, oldest evicted — about 900 bytes against a 4KB browser limit, so the cap only
exists so somebody who collects links for years can't overflow the cookie and lose all of them at
once.

The expiry is rolling: `visibility()` calls `ShareTokenCookie.refresh()` once a token has actually
resolved, so the year is a year of *not visiting* rather than a year from the day the link was
followed. Refreshing only on a resolved token means a cookie holding nothing but revoked tokens ages
out on its own instead of being renewed forever, and an anonymous visitor — no cookie — gets no
`Set-Cookie` header.

Cost: every request pays the opt-out scan inside `createVisibility`, anonymous visitors included.
A token holder pays two small indexed lookups on top — the `owner.token` row, and
`share_token IN (…)` against a unique index — which is the cheaper half of the pair.

The `HttpSession` is no longer used for visibility. Nothing else was using it, so
`OWNER_ATTRIBUTE` and `UNLOCKED_CATEGORIES_ATTRIBUTE` are gone.

### Deletions

- `IncludePrivateController` and the `/includePrivate` route go away entirely.
- **`AbstractController.includePrivate(Boolean, request)` currently lets any caller *set* the session
  flag from a query parameter.** `/api/category?private=true` and `/api/photos?private=true` are
  unauthenticated back doors to every private photo today. The `private` request param is removed
  from `CategoryController` and `PhotosController`; `AbstractController` only reads session state.

### Config

```properties
# src/config.properties
SiteBaseURL=https://photos.jeckels.com/   # used to build share links in the admin UI
```

That's the only new property. No secret goes in this file — both kinds of token live in the database,
which keeps them out of a tracked file and makes rotating one a button rather than a restart.

## Enforcement points

The rule has to be applied at every point where a photo or category reaches JSON. Four of these are
pre-existing leaks that the People-tags-are-private plan depends on fixing:

| Location | Today | Change |
|---|---|---|
| `Photo.getCategoriesNonRecursive()` (`Photo.java:140`) | Calls `getCategories()` → `getCategories(true)`, so **every photo's JSON already lists its private category ids**. Person tags would leak the moment they're marked private. | Filter through the request's `Visibility`, via a transient field like `Category`'s. |
| `Category.getCategoriesNonRecursive()` (`Category.java:165`) | Walks `getChildCategories()` unfiltered, so **`/api/category/{id}` lists private subcategories** with their descriptions and cover photos. | Filter with `Visibility.canSee(Category)`. |
| `Category.getDefaultPhoto()` and `toNonRecursiveMap()` | Returns the cover photo with no visibility check. | Return `null` when `!canSee(photo)` — this is the "no cover image" decision, and also plugs the existing private-photo-as-cover leak. |
| `Category.getPhotos()` (`Category.java:86`) | Filters on `photo.isPrivate()` only, using a transient `includePrivate` that `getRootCategories` never sets. | Transient becomes `Visibility`, defaulting to the most restrictive value so an unset path fails closed. |
| `PhotoOperations.getPhoto` / `getPhotoByFilename` | `private` check only. | Add the opt-out check; hidden → `null` → controller returns 404, not 403 (don't confirm the photo exists). |
| `PhotoOperations.getPhotosInAllCategories` / `getPhotoIdsInCategories` | `private` check only. | Subtract `hiddenPhotoIds`, add back `unlockedPhotoIds`, and reject a selected category the visitor can't see in the first place. |
| `CategoryRssController`, `getNewestCategories` | `private` check only. | Same filter. Tokens never apply here — the feed is global. |

Categories stay listed even when every photo in them is hidden; an empty-looking event is less
confusing than one that vanishes.

## Admin UI

All on the `CategoryTree` right-click menu, next to the existing Private checkbox:

```
right-click a category:
  Private                    [x]
  Hide photos publicly       [ ]     ← only shown on person categories
  ---
  Create / Copy share link           ← mints a token the first time
  Regenerate share link              ← only when one exists; invalidates the old link
  Remove share link                  ← only when one exists; sets share_token = NULL

right-click a person:
  Hide photos publicly       [x]
  ---
  Create / Copy unhide link          ← the same three items, same column, different meaning
  Regenerate unhide link
  Remove unhide link

right-click <ROOT>:
  Create / Copy owner link           ← the gallery as a whole, not any one category
  Regenerate owner link

select several, right-click:
  Copy combined share link (N categories)
```

*Copy share link* puts `<SiteBaseURL>unlock?t=<token>` on the clipboard. The item's label says whether
a token already exists, so you can tell at a glance which categories are shared. *Regenerate* and
*Remove* only appear once there is one, and both confirm first since they break links already sent.

The unhide items are the same three `JMenuItem`s relabelled — minting, regenerating and removing a
token is one operation either way, and only the wording and the warnings differ. They're offered on
a person whether or not they're currently opted out, so a link can always be revoked; the copy
dialog says when the person isn't hidden and the link is therefore doing nothing yet.

*Copy combined share link* is the multi-select version: one `unlock?t=…&t=…` covering everything
selected, minting tokens for any that don't have one yet. **Selecting an event together with the
relatives who were at it is what it's for** — the event's token shows the event, each person's shows
that person everywhere else too. Tokens go in **tree order rather than click order**, so the visitor
lands on the topmost *public* category selected — a rule you can see on screen rather than having to
remember. People go in; the root and private categories that aren't people are dropped instead of
going in as duds. The item shows on any multi-selection, including one with nothing usable in it,
and says so when clicked — an item that quietly isn't there is a worse answer than one that explains
itself. Selecting more than
`ShareTokens.MAX_HELD` refuses rather than building a link whose first token the browser will
immediately evict; the suggestion is to share a parent category, since one link covers its whole
subtree anyway.

The owner link lives on the root node because the owner's access isn't a property of any category. It
is the only way into private mode, so a gallery with no owner token has nobody who can see private
photos at all — which is also what makes it safe to leave unset.

## SPA contract

No frontend changes. `/unlock` is a server-side redirect that sets an `HttpOnly` cookie, and every
`/api/*` response is already filtered by the time the SPA sees it. The SPA's existing behavior —
render whatever the API returns — is correct for both anonymous and unlocked sessions.

The one thing to know: a share link is `/unlock?t=…`, not `/category/123?t=…`. The server knows
which category the token belongs to and redirects there, so the SPA only ever sees an ordinary
`/category/123` URL. If you'd rather the token ride on the category URL itself, the SPA would have
to forward it on every fetch, which is the version that needs ReactPhoto changes.

## Known limitations

- **Image bytes stay public.** `Resolution` builds URLs straight against `resized.jeckels.com`, and
  resized filenames are deterministic (`{photoId}-{w}x{h}.jpg`). A hidden photo is unlisted, not
  unreachable: anyone walking photo ids against the bucket gets it. This is already true of every
  `private` photo today. Closing it means proxying or signing those URLs — a separate project.
- **Opt-out depends on the tag being there.** A photo of an opted-out person that was never tagged
  stays visible. That's the accepted trade for not acting on unconfirmed face proposals, and it's
  why the "Tagged, no face" column matters.
- **A share link reveals everyone opted-out in that category**, including someone who was in a photo
  taken there but not at the event, or someone whose photo was filed under the event for another
  reason. The unit of sharing is the category, not the guest list. And what it unlocks is those
  photos, so they stay visible to the holder under every other category they're filed in — see
  [What a token unlocks is photos, not places](#what-a-token-unlocks-is-photos-not-places).
- **A token never makes a category visible, and never reveals a `private` photo.** It can unlock
  a private category's *photos* — that's an unhide link — but `canSee(Category)` reads neither id
  set, so the category itself stays hidden from everyone but the owner, and `canSee(Photo)` refuses
  a private photo before it ever consults the unlock set.
- **An unhide link has no end date.** It covers that person's photos in every event, including
  events imported years later. For family that's the point — you don't want to re-send a link on
  every import — but nothing expires and nothing narrows it.
- **One token per person means one revocation.** Send *Wedding + Alice* to one branch of the family
  and *Reunion + Alice* to another, and regenerating Alice's token to cut off the first also cuts
  off the second. Events don't have this problem because each has its own token. The fix would be
  multiple tokens per category, which isn't worth building until it bites.
- **An unhide link makes photos fetchable that appear in no listing.** A photo tagged with that
  person but filed only under categories the visitor can't see is unlocked, so `/api/photo/<id>`
  returns it, even though nothing links to it. Not `private` photos — those stay refused.
- **Tokens don't expire and aren't per-recipient.** One link per category; if it leaks, regenerate,
  and everyone you sent the old one to needs the new one. Regenerating does now take effect
  immediately, including for people who already followed the old link.
- **The cookie lasts a year and there's no visible sign it's there.** Someone who unlocks on a
  borrowed laptop stays unlocked on it. `/unlock?forget=1` clears it, but nothing in the UI points
  at that, and the owner token — which is all-access — sits in the same cookie under the same rules.
  It's the same trust boundary as the browser history that already holds the link, but it lasts
  longer and it's worth knowing about before you follow an owner link on a machine that isn't yours.

## Before this works

The code is in place. Three things are yours to do, and until the first two are done nothing changes
for anyone:

1. **Run the schema change**: `psql photo_gallery -f src/main/resources/sql/privacy-schema.sql`.
   Nothing works until this runs. `HibernateConfig` sets `hbm2ddl.auto=none` on its own
   `EntityManagerFactory`, overriding `spring.jpa.hibernate.ddl-auto=validate`, so the app starts
   fine and then fails on the first query touching the new columns rather than failing at boot.
2. **Run the backfill**, which is the commented-out block at the end of that same file. Marking the
   People subtree private is what actually hides the tags; everything else is machinery waiting for
   it. Review the `SELECT` before running the `UPDATE`.
3. **Mint the owner token**: start the admin UI, right-click `<ROOT>` in the category tree, choose
   **Create owner link**, and open the copied link once in each browser you want private mode in.
   Once per browser is genuinely once now — the cookie survives restarts for a year, and each visit
   restarts the clock. Until you do, `/unlock` grants nobody anything and there is no way into
   private mode —
   `/includePrivate` is gone. Set `SiteBaseURL` in `src/config.properties` first, or the copied link
   will point at `localhost:8080`.

Then, per person: right-click their category → **Hide photos publicly**. Per event: right-click →
**Create share link**.