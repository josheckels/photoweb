# Proxying image bytes through the app

The gap PRIVACY.md ends on. Today the API hides a photo's *metadata* and the browser fetches its
*bytes* straight from a public S3 bucket, so a hidden photo is unlisted rather than unreachable:

> **Image bytes stay public.** `Resolution` builds URLs straight against `resized.jeckels.com`, and
> resized filenames are deterministic (`{photoId}-{w}x{h}.jpg`). A hidden photo is unlisted, not
> unreachable: anyone walking photo ids against the bucket gets it. — PRIVACY.md, Known limitations

This closes it. Every image request goes through the app, gets the same `Visibility` check the JSON
already gets, and is answered with a redirect to a short-lived S3 URL. The buckets become private,
and a URL nobody was given is a 404 rather than a photograph.

## Decisions taken

| Question | Decision |
|---|---|
| Where the check happens | A new `/img/**` endpoint on the app, using the same `Visibility.canSee(Photo)` as everything else. One rule, still in one place. |
| Who serves the bytes | S3, via a 302 to a presigned URL. The instance never touches an image byte. |
| Public vs restricted photos | **Handled identically.** No split bucket, no separate URL scheme, no branch in the contract. The restricted-id cache below is an optimisation, not a second code path. |
| Link lifetime | Presigned for 7 days, rotated every 3. Long by design — see [Why the window is long](#why-the-window-is-long). |
| Denied and missing | Both 404. The endpoint never confirms that a photo exists, matching `getPhoto`'s existing null → 404. |
| Old bucket URLs | Break. Nothing else reads those buckets — the Android app is gone, the JSPs are deleted, and `CategoryRssController` emits no image URLs. |
| Dev machine | Same endpoint, reads the local resized/originals directories instead of S3. No AWS round trip on the laptop. |
| Frontend | Two functions in `ReactPhoto/utils/urlHelpers.ts` and one `fetch` in `Photo.tsx`. |

## URL shape

```
GET /img/r/{photoId}-{width}x{height}.jpg     → resized bucket
GET /img/o/{photoId}/{filename}               → originals bucket
```

Both carry the photo id in the path, so the permission check needs no database lookup to know *which*
photo is being asked for. That is the whole reason for the shape.

The resized form is exactly the filename `Resolution` already generates (`Resolution.java:44`), so the
SPA keeps composing URLs from the `filename` the API already sends. The originals form carries the
filename as well as the id because the filename is not derivable from the id, and because a later
nginx variant needs to build the S3 key from the path alone.

Which of the two applies is a decision the SPA already makes: `Resolution` names the *original* file
when a requested size equals the original dimensions, and `getS3ImageUrl` already tests for that with
`RESIZED_REGEX`. Same branch, different prefix.

Keys in the bucket are `S3KeyPrefix + filename`, and `S3KeyPrefix` is commented out today, so the key
is the filename. `S3Uploader` is unchanged — this is a read-path project.

## The rule

```java
Photo photo = photoOperations.getPhoto(photoId, visibility(request, response, photoOperations));
if (photo == null) return 404;
```

That is the entire check, and it is the same one `PhotoController` makes. `getPhoto` runs the result
through `visibleOrNull`, so `private`, opt-out and share-token handling all come along for free, and
a photo an unhide-link holder may see resolves for them and 404s for everyone else.

### The restricted-id cache

Applied naively that is a `Visibility` construction plus a `Photo` load **per image**, and a grid page
fires around sixty of them. `createVisibility` alone runs `getOptOutHiddenPhotoIds()` — a scan across
`photo_category_link` — on every one.

So the endpoint keeps a cached set:

```java
restrictedPhotoIds = { photoId : p._private = true } ∪ { photoId : tagged with an opt-out category }
```

If the requested id is not in it, the photo is visible to everybody and the request is served with no
query at all. If it is, the full check above runs.

This changes nothing about the contract — every photo goes through the same endpoint and the same
rule, and the answer is identical either way. It is a way of not asking the database a question whose
answer is already known.

Only the resized path uses it. An original has to load the photo regardless, to check that the
filename asked for really is that photo's — otherwise `/img/o/{a public id}/{a private photo's
filename}` would pass a permission check on one photo and hand back another's bytes. That costs a
query per download rather than per thumbnail, which is not a number worth optimising.

The set is rebuilt on a timer with a flush from the admin UI, rather than being invalidated precisely
on every opt-out change and photo/category-link write. That is a deliberate downgrade from what
PRIVACY.md:118 sketches, and it is safe here because the inputs barely move: a photo's privacy and a
person's opt-out are set once and then left alone. The window between an admin change and the cache
noticing is minutes, and during it the *listings* have already updated — only direct image URLs lag.

## Presigning

`S3Presigner` ships inside `software.amazon.awssdk:s3:2.25.29`, already a dependency. Presigning is
an HMAC over the request; it makes no network call and needs no permission of its own beyond the
credentials being able to `s3:GetObject`.

### Why the window is long

A presigned URL is a bearer token for its lifetime — pulled out of devtools it works for anyone until
it expires. Short windows narrow that, at the cost of the thing that actually matters here: **every
time the URL rotates, every cached copy in every browser becomes a miss and the whole grid
re-downloads.**

Since a photo that is visible today is visible next week, a short window buys very little. So take as
much as SigV4 allows:

- **Signatures are valid for 7 days**, which is the protocol's hard ceiling for a query-string
  signature rather than a preference. It is also capped by the lifetime of the credentials that signed
  it — a long-lived IAM user key reaches the full week, temporary STS credentials expire with the
  session whatever we ask for.
- **A given object's URL is reused for 3 days**, so every visitor in that stretch gets a byte-identical
  URL and S3's own caching works normally. Shorter than the signature lifetime on purpose: a URL handed
  out just before a rotation still has four days left on it, so nothing expires mid-page.
- The 302 carries `Cache-Control: private, max-age=<seconds until this URL rotates>`, so a returning
  visitor inside the window skips the app entirely.
- The image itself keeps `Cache-Control: public, max-age=31536000, immutable` on the S3 object.
  Filenames are content-addressed by id and size and are never rewritten, so that is honest.

Reuse is a memo table keyed on the bucket and key, not a snapped signing clock — the AWS SDK signs
with its own clock and does not offer a clean way to lie to it. The consequence is that a restart
re-signs everything and costs returning visitors one round of downloads.

The table is capped at **40 MB**, which matters on an instance with a couple of gigabytes. The cap is
on bytes rather than on entries, because an entry count only bounds memory if you already know how
long a presigned URL is — and that moves with the bucket name, the key prefix and the credential
scope. Measured against these two buckets a signed URL is 343–370 characters, or 638–692 bytes an
entry, so the budget holds roughly 60,000 images; the library has ~293k resized objects, so a full
crawl still overflows it and simply pays for a re-sign.

Overflow empties the table wholesale rather than evicting least-recently-used, because a re-sign is
an HMAC against an in-process key — microseconds, plus one round of browser re-downloads. Nothing
about the cache is worth more machinery than that.

Net effect: a full re-download of a visitor's cached images roughly every three days. That is the
price of the redirect design, and [the upgrade path](#the-upgrade-path) removes it without changing
any URL.

## Dev and prod

The endpoint has two sources, chosen by config:

| | `photoweb.image.source=local` | `photoweb.image.source=s3` |
|---|---|---|
| Where | The laptop | LightSail |
| Bytes from | `ResolutionUtil.RESIZED_PHOTOS_DIRECTORY_VALUE` / `getPhotosDirectory()` | Presigned S3 redirect |
| Response | 200 with the file | 302 |

Both directory values are read at class-init from `src/config.properties`, so — unlike
`Resolution.PHOTO_BASE_URL` — they are populated in a server-only run without `ResolutionUtil.init()`.
Nothing in this project needs `init()` wired into the Boot startup path, because the API still emits
only `filename` and the SPA still composes the URL. `Resolution.getURI()` stays unserialized and stays
`null`-prefixed in a web-only process, exactly as today.

The instance and both buckets are in `us-west-2`, so the S3 leg is in-region: no cross-region transfer
charge, and a presigned redirect costs one local round trip plus S3's usual latency.

**LightSail has no IAM instance roles.** `DefaultCredentialsProvider` therefore has to find
credentials somewhere else on the box — `~/.aws/credentials` for the service user, or environment
variables in the unit file. An IAM user with `s3:GetObject` on the two buckets is enough for the web
app; the admin's uploader needs `PutObject`/`DeleteObject` and runs on the laptop, so the two do not
have to share a principal.

## What changes

| Location | Change |
|---|---|
| `controller/ImageController.java` (new) | `/img/r/**` and `/img/o/**`; the rule above; local-file or presigned-redirect by config. |
| `PhotoOperations` | Expose the two id queries behind one cached `restrictedPhotoIds()`. `getOptOutHiddenPhotoIds()` is private today; the private-photo half is a new `select p.photoId from Photo p where p._private = true`. |
| `config/CorsConfig.java` | Currently maps `/api/**` only. The dev SPA on `:3000` fetching a download from `:8080` needs `/img/**` added; `<img src>` does not, but the download path does. |
| `ReactPhoto/utils/urlHelpers.ts:62-79` | `getS3ImageUrl` and `getOriginalS3ImageUrl` point at `/img/r/…` and `/img/o/{id}/…` instead of the two hardcoded S3 hosts. Same `RESIZED_REGEX` branch. |
| `ReactPhoto/src/pages/Photo.tsx:262` | `credentials: 'omit'` → `'include'`. Better still, drop the blob dance for a plain `<a href>` and let the endpoint set `Content-Disposition: attachment`. |
| S3 | Remove public-read from `photo.jeckels.com` and `resized.jeckels.com`. Presigned URLs work against a private bucket; nothing else has to change. |
| `src/config.properties` | Nothing new — `S3Region`, `S3OriginalsBucket`, `S3ResizedBucket`, `S3KeyPrefix`, `PhotosDirectory` and `ResizedDirectory` all already exist. |
| `application-prod.properties` | `photoweb.image.source=s3`. This is also the moment to stop it being byte-identical to the dev file. |

## The upgrade path

If the weekly re-download becomes annoying, the same endpoint becomes a permanent-URL proxy without
the frontend changing again:

- Add an nginx `auth_request` pointing at `/img/**`, and have the controller return `X-Accel-Redirect`
  instead of a 302 when `photoweb.image.accel=true`.
- Restrict the buckets to the instance's static IP with a bucket policy, so nginx can `proxy_pass` to
  the ordinary S3 endpoint and never needs to speak SigV4.
- Turn on `proxy_cache` so repeat views are served off the instance's SSD.

URLs then never rotate, `immutable` caching works properly, and repeat visits cost nothing. The auth
subrequest can be cached hard in nginx for the same reason the restricted-id set can be cached in
Java. Deferred rather than built because it needs an nginx config that this repo does not currently
have, and it does not work on the laptop.

## Known limitations

- **A presigned URL is a bearer token for up to 7 days.** Anyone who extracts one from a page can hand
  it on, and there is no revocation short of rotating the bucket's contents. Accepted deliberately:
  the alternative costs browser caching, and a photo's visibility does not change.
- **A resized request can tell "hidden" from "absent", and that is fine.** A restricted photo 404s
  from the app; an id matching no row gets a redirect and then a 404 from S3. So a visitor can learn
  that photo 7 exists between photo 6 and photo 8 — and nothing else. No signature is minted for a
  restricted photo, so there are no bytes; `/api/photo/{id}` 404s the same photo, so there is no
  caption, no dimensions and no category; and an originals URL needs a filename that only the API
  hands out. Existence is not the thing being protected. Closing it would mean caching every known
  photo id alongside the restricted ones, which is cheap but buys nothing.
- **Marking something private no longer takes effect instantly for image bytes.** The restricted-id
  cache lags by its refresh interval, and a presigned URL already handed out stays good for its
  window. Listings update immediately, as they do today.
- **Every image request now hits the app**, where none did before. Sixty per grid page on a cold
  cache. The redirect carries no body and the fast path does no query, but it is not nothing, and it
  is the number to watch.
- **The bytes are still S3's to serve**, so this is a permission check on the *link*, not on the
  transfer. That is the trade that keeps the instance out of the data path.
- **Nothing here protects against a visitor who is legitimately shown a photo.** A share-token holder
  can save and forward what they can see, exactly as before. The unit of protection is unchanged.

## Before this works

The endpoint is built and verified against the production server, and the SPA now uses it. **Step 4
is the only one left, and until it is done nothing is actually protected** — the buckets are still
public, so every old URL still works and a hidden photo is still reachable by guessing its filename.

1. **Build and deploy the app change**, with `photoweb.image.source=s3` in the prod profile.
2. **Put S3 credentials on the LightSail box** — no instance role exists to fall back on. Verify by
   hitting `/img/r/<some public photo>` before step 4.
3. **Rebuild and copy the SPA**: `npm run build` in `ReactPhoto`, then `dist/*` into
   `src/main/resources/static/`. Until this lands, the deployed bundle still points at the buckets.
4. **Remove public-read from both buckets.** Do this last: between steps 3 and 4 both paths work, and
   after step 4 only the new one does. This is the irreversible-feeling step, and it is also the only
   one that actually closes the hole.
