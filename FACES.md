# Automatic Face Tagging — Implementation Plan

Detect faces in the library, match them to people, and use the results to extend the
existing category tags. Runs entirely locally (OpenCV YuNet + SFace), with embeddings
stored in Postgres via pgvector.

## Contents

- [Core design decision: a person is a category](#core-design-decision-a-person-is-a-category)
- [Why the existing tags are enough to bootstrap](#why-the-existing-tags-are-enough-to-bootstrap)
- [Phase 0 — Feasibility check](#phase-0--feasibility-check)
- [Phase 1 — Dependencies, models, schema](#phase-1--dependencies-models-schema)
- [Phase 2 — The detector service](#phase-2--the-detector-service)
- [Phase 3 — Backfill](#phase-3--backfill)
- [Phase 4 — Seed from solo photos](#phase-4--seed-from-solo-photos)
- [Phase 5 — Constrained propagation](#phase-5--constrained-propagation)
- [Phase 6 — Cluster the leftovers](#phase-6--cluster-the-leftovers)
- [Phase 7 — Review UI](#phase-7--review-ui)
- [Phase 8 — Iterate](#phase-8--iterate)
- [Phase 9 — New imports](#phase-9--new-imports)
- [Managing people going forward](#managing-people-going-forward)
- [Rules and invariants](#rules-and-invariants)
- [Risks](#risks)
- [Open questions](#open-questions)

---

## Core design decision: a person is a category

People are already modelled as categories under a `People` parent. **Do not introduce a
separate `Person` entity.** Face rows reference `category.category_id` directly.

A shadow `Person` table would hold only `category_id` and a display name — and the display
name is already `Category.description`. Collapsing them eliminates an entire class of sync
bugs and means the following all work with no new code:

| Operation | Already handled by |
|---|---|
| Create a person | `CategoryTree._insertMenuItem` — right-click People → Insert, inline-edit the name |
| Rename a person | `CategoryCellEditor.setDescription` |
| Mark a person private | `Category.private` + the existing `includePrivate` gating |
| Delete a person | `PhotoOperations.deleteCategory` + `ON DELETE SET NULL` |
| Drag a person onto photos | `CategorySelection` transfer handler |
| Browse a person's photos | The entire existing category browse path, web included |

Two details:

- **Identify the People root by ID, not by name.** Matching `description = 'People'` breaks
  the day it gets renamed. Add `PeopleCategoryId=<id>` to `src/config.properties` alongside
  `PhotosDirectory`.
- **Test descendant, not child.** The People subtree is flat today, so a direct
  `parent_category_id` check would work. Use the existing `Category.getPathToRoot()` anyway —
  it costs nothing and keeps `People > Family > Josh` working if the tree is ever nested.

Cost of this choice: every named person is a browsable category. That is already true today,
so it costs nothing. If per-person matching config is ever needed ("stop auto-matching this
one"), add a `person_settings(category_id PK, …)` side table then — not now.

---

## Why the existing tags are enough to bootstrap

Tags are photo-level; faces are region-level. A photo tagged `Alice` + `Bob` with five faces
says "Alice is one of these five," not which one. No service resolves that automatically —
Rekognition's `IndexFaces`/`AssociateFaces` expects a face you have already identified.

Three properties make it tractable anyway:

1. **Solo photos are unambiguous.** One person-tag + one detected face ⇒ that face is that
   person, no inference required. A handful per person gives a solid exemplar set.
2. **Co-occurrence votes.** A face cluster that is genuinely Alice appears overwhelmingly in
   Alice-tagged photos. Score on `|cluster ∩ Alice| / |cluster|` — a *positive* tag is strong
   evidence of presence, but a *missing* tag is weak evidence of absence, so scoring on
   false-positive rate would punish correct clusters given incomplete tagging.
3. **Same-photo faces are a hard negative constraint.** Two faces in one photo are essentially
   never the same person. This improves clustering and gives a free quality check: any cluster
   containing two faces from the same photo is wrong and must be split.

People with no category at all simply become unnamed clusters, named once in the review UI.
This tends to surface recurring people who were never tagged.

---

## Phase 0 — Feasibility check

Run before writing any code. Confirms there are enough unambiguous seeds.

The People subtree is a flat list of person categories directly under the People parent.

```sql
WITH people AS (
  SELECT category_id, description FROM category
  WHERE parent_category_id = :peopleRootId
),
solo AS (
  SELECT l.photo_id, min(l.category_id) AS person_cat
  FROM photo_category_link l JOIN people p USING (category_id)
  GROUP BY l.photo_id HAVING count(*) = 1
)
SELECT p.description, count(*) AS solo_tagged_photos
FROM solo s JOIN people p ON p.category_id = s.person_cat
GROUP BY p.description ORDER BY 2 DESC;
```

Anyone with **≥5 solo-tagged photos** seeds cleanly. Anyone with 0–2 needs manual seeding
(see [Flow C](#c-category-first-faces-later)). This is an upper bound — the real seed count
drops once face counts are known, since a solo-tagged photo with three faces is ambiguous.

---

## Phase 1 — Dependencies, models, schema

### Maven

`opencv-platform` pulls natives for every platform (~1GB). Pin it:

```xml
<properties>
  <javacpp.platform>macosx-arm64</javacpp.platform>
</properties>

<dependency>
  <groupId>org.bytedeco</groupId>
  <artifactId>opencv-platform</artifactId>
  <version>4.10.0-1.5.11</version>
</dependency>
<dependency>
  <groupId>org.hibernate.orm</groupId>
  <artifactId>hibernate-vector</artifactId>
</dependency>
```

If dev (macOS) and prod (Linux) differ, declare `org.bytedeco:opencv` twice with explicit
`<classifier>macosx-arm64</classifier>` and `<classifier>linux-x86_64</classifier>` instead
of using `opencv-platform`. `hibernate-vector`'s version is managed by the Boot 3.4.4 parent
(Hibernate 6.6).

### Models

From [opencv_zoo](https://github.com/opencv/opencv_zoo):

- `face_detection_yunet_2023mar.onnx` (~230KB)
- `face_recognition_sface_2021dec.onnx` (~37MB)

Don't commit the 37MB file. Add `FaceModelsDirectory` to `src/config.properties` and load it
through the existing `Configuration` mechanism, same as `PhotosDirectory`.

### Schema

No Flyway/Liquibase in this project, and `SequenceSynchronizer` implies schema is managed by
hand. Write the DDL directly:

```sql
CREATE EXTENSION IF NOT EXISTS vector;

-- distinguishes "scanned, found 0 faces" from "not scanned yet"
ALTER TABLE photo ADD COLUMN face_scanned_on TIMESTAMP;

CREATE TABLE photo_face (
  face_id            BIGSERIAL PRIMARY KEY,
  photo_id           INT  NOT NULL REFERENCES photo(photo_id) ON DELETE CASCADE,
  x REAL, y REAL, w REAL, h REAL,   -- normalized 0..1 against the ORIGINAL image
  detect_score       REAL,
  embedding          vector(128) NOT NULL,
  model_version      TEXT NOT NULL,  -- makes a future model swap tractable
  person_category_id INT  REFERENCES category(category_id) ON DELETE SET NULL,
  confirmed          BOOLEAN NOT NULL DEFAULT FALSE,
  match_score        REAL,
  cluster_id         INT
);
CREATE INDEX ON photo_face (photo_id);
CREATE INDEX ON photo_face (person_category_id) WHERE person_category_id IS NOT NULL;
CREATE INDEX ON photo_face USING hnsw (embedding vector_cosine_ops);

-- prevents rejected matches from being re-proposed every round
CREATE TABLE face_person_rejection (
  face_id     BIGINT NOT NULL REFERENCES photo_face(face_id)     ON DELETE CASCADE,
  category_id INT    NOT NULL REFERENCES category(category_id)   ON DELETE CASCADE,
  PRIMARY KEY (face_id, category_id)
);
```

Bounding boxes are stored **normalized** so they stay valid regardless of which resized
variant detection ran against.

### JPA

New package `com.stampysoft.photoGallery.faces`, entity `PhotoFace`:

```java
@JdbcTypeCode(SqlTypes.VECTOR)
@Array(length = 128)
private float[] embedding;
```

`person_category_id` maps as `@ManyToOne @JoinColumn(name = "person_category_id")` to the
existing `Category` entity.

---

## Phase 2 — The detector service

`faces/FaceEncoder.java`, wrapping both models:

```java
FaceDetectorYN detector =
    FaceDetectorYN.create(yunetPath, "", new Size(320, 320), 0.7f, 0.3f, 5000);
FaceRecognizerSF recognizer =
    FaceRecognizerSF.create(sfacePath, "");
```

Per image:

1. `imread()` the **1400px** resized variant (`Photo.getRetinaDimensions()`). The 700px
   default loses small faces in group shots; the original is wasteful. Fall back to the
   original if the 1400px doesn't exist.
2. `detector.setInputSize(new Size(img.cols(), img.rows()))` — **required** before every
   `detect()` on a differently-sized image.
3. `detector.detect(img, faces)` → an *N*×15 `CV_32F` Mat:
   `[x, y, w, h, 5 landmark xy pairs, score]`.
4. Per row: `recognizer.alignCrop(img, faceRow, aligned)` then
   `recognizer.feature(aligned, feat)` → 1×128 float.
5. **L2-normalize** the feature before storing, so cosine and inner-product agree.
6. Normalize the bbox by image dimensions.

Two things that will bite otherwise:

- **Neither class is thread-safe.** One `FaceEncoder` instance per worker thread, or
  `synchronize` the whole encode call. Sharing one across a pool produces garbage or segfaults.
- **`Mat` is native memory.** try-with-resources or explicit `.close()`. A leak here OOMs the
  JVM *outside* the heap, which is miserable to diagnose.

Scope this admin-side: guard the Spring bean with the same
`@ConditionalOnProperty(photoweb.admin.enabled)` pattern `AdminFrame` uses, so the deployed
web app never loads OpenCV natives.

---

## Phase 3 — Backfill

`faces/FaceScanJob.java`, modelled on `PhotoAdderThread` (same `JDialog` + `JProgressBar`
shape, so it slots into the admin menu naturally).

- Select photos where `face_scanned_on IS NULL AND movie = false`.
- Batch ~200, commit per batch, set `face_scanned_on` **even when zero faces are found** —
  this is what makes the job resumable after a crash.
- Parallelize across `availableProcessors() - 1` threads, one `FaceEncoder` each.
- Rough throughput: 50–150ms/photo/thread at 1400px. A 50k library is a couple of hours
  single-threaded, well under an hour parallel.

Decide up front whether `private` photos get scanned. Recommended: scan them — the data stays
in local Postgres — but gate any future API exposure behind the existing `includePrivate`
session flag.

---

## Phase 4 — Seed from solo photos

For each photo with exactly one People-descendant category **and** exactly one detected face:
assign that face to that category, `confirmed = true`.

A photo tagged with exactly *N* people containing exactly *N* faces is constrained but the
assignment among them is not — skip those here; Phase 5 resolves them.

---

## Phase 5 — Constrained propagation

For each unassigned face, score against each candidate person.

**Do not use a single centroid per person.** This library spans years; a child's face at 3 and
at 15 will not share a centroid. Instead:

> `score(face, person)` = mean of the top-3 cosine similarities against that person's
> *confirmed* faces.

Then apply, in order:

1. **Tag constraint.** Candidates tagged on this photo get the base threshold; everyone else
   needs a materially higher bar. Positive tags are reliable; missing tags are not evidence of
   absence, so never hard-exclude an untagged person.
2. **One person per photo.** Resolve greedily by descending score; each person consumable once
   per photo.
3. **Rejection ledger.** Exclude any `(face_id, category_id)` pair in `face_person_rejection`.
4. **Thresholds.** OpenCV's documented same-identity cutoff for SFace cosine is **0.363**.
   Auto-propose at ≥0.50; queue 0.363–0.50 for review; discard below.

Write results with `confirmed = false` and `match_score` set. **Nothing auto-confirms.**

---

## Phase 6 — Cluster the leftovers

Faces matching nobody are the people without categories.

Union-find over pairs with cosine ≥ 0.5 — query neighbours through the HNSW index rather than
an N² scan — **rejecting any merge that would place two same-photo faces in one cluster**.
Write `cluster_id`. Clusters of ≥5 faces are worth surfacing; singletons are noise.

---

## Phase 7 — Review UI

This is where the value is. Nothing above ever writes a confirmed tag unattended.

`PhotoAdminScreen` already has `_categoryTabbedPane` with "All Categories" and "Recent
Categories". Add a third tab, **People**, with three sub-views:

1. **Review queue** — proposed matches grouped by person, sorted by descending `match_score`,
   click-to-reject, confirm-batch button. Because it's sorted you confirm the top chunk in one
   click and only scrutinize the tail. Badge the tab with the pending count.
2. **Unknown clusters** — grid of representative crops sorted by cluster size, each with the
   autocompleting name combo described in [Flow A](#a-naming-an-unknown-cluster).
3. **People list** — every person category with its face count and a "find more" button;
   zero-face people flagged for seeding.

In `PhotoInfoPanel`, overlay face boxes on the preview. Confirmed boxes are labelled;
unmatched ones show `?` and open the assign combo on click. This doubles as a visual check on
detection quality during normal photo editing.

Crop rendering: read the normalized bbox, expand ~40% for context, pull from the 1400px
variant.

Wiring: add a `FaceListener` to `AdminModel` following the existing
`PhotoListener`/`CategoryListener` shape.

---

## Phase 8 — Iterate

Recompute from confirmed faces only, re-run Phases 5–6. Two or three rounds converge — each
round of confirmations gives later rounds more exemplars across more ages and angles. Expose
as a "Re-run matching" menu item.

---

## Phase 9 — New imports

Hook `PhotoAdderThread` to run detection on import and propose matches immediately, so the
review queue stays short instead of requiring another full backfill.

---

## Managing people going forward

### Service layer

One new admin-scoped class, `faces/PeopleService`:

```java
Category getPeopleRoot();
boolean  isPerson(Category c);                          // getPathToRoot() contains People root
List<Category> getAllPeople();
Category createPerson(String name);                     // same body as CategoryTree._insertMenuItem
void     assignFace(PhotoFace f, Category person);      // set person, confirmed=true, tag photo
Category adoptCluster(int clusterId, Category person);  // bulk assign + tag + retro-match
void     rejectFace(PhotoFace f, Category person);      // writes to the rejection ledger
void     mergePeople(Category from, Category into);
```

`assignFace` tags the photo through
`PhotoOperations.updatePhotoCategories(photo, List.of(person), List.of())` — the method whose
javadoc warns about double-merging a detached instance. Use that one, **not**
`addCategoryToPhoto` in a loop.

### Four ways a person gets created

#### A. Naming an unknown cluster

The main path going forward. The cluster review panel shows representative crops plus a count;
the name field is an **editable combo box autocompleting over existing People categories**.

That dual behaviour matters: an "unknown" cluster is very often an existing person who had no
seeds. Picking them from the dropdown merges instead of creating a duplicate. Only a name
matching nothing creates a new category.

On commit: create the category if needed → `adoptCluster` → `AdminModel.fireCategoryChanged`
so the tree refreshes → **immediately re-run propagation for just that person**. Going from 0
to 43 exemplars is exactly when the "now find the rest of them" pass should fire; it produces
a follow-up review batch.

#### B. Clicking an unmatched face in `PhotoInfoPanel`

Face boxes overlaid on the preview; unmatched ones show `?`. Click opens the same combo. This
is the path for someone who appears in two photos and will never form a cluster.

#### C. Category first, faces later

Right-click People → Insert, exactly as today. The person exists with zero faces and appears
in the People tab flagged "no faces yet." Flow B seeds it. This handles whoever came back with
0–2 solo photos in Phase 0.

#### D. Nothing at all

A new person in new imports matches nobody, lands in the unknown pool, and once enough faces
accumulate a cluster appears in the review tab on its own. Worth designing for deliberately —
it means new people surface without anyone remembering to do anything.

### Merge and split

SFace **will** both over-split and over-merge, especially across a child's age range. Both
directions are required:

- **Merge** — two clusters that are the same person, or two duplicate person categories.
  `mergePeople` reassigns faces, moves category links, deletes the emptied category.
- **Split** — reject a subset of faces from a person. This is what
  `face_person_rejection` exists for. Without it, every propagation round re-proposes the same
  wrong face and the loop never converges.

---

## Rules and invariants

**Only ever add tags; never remove them.** Confirming a face *adds* a category link to the
photo. Nothing in this system should ever remove one. A photo tagged `Alice` with no Alice
face detected usually means her back was turned, she's in profile, or YuNet missed her — not
that the tag is wrong.

Surface those as a report instead ("tagged, no matching face: N photos"), which doubles as the
detection-recall diagnostic. If that number is large, the problem is Phase 3, not the tags.

**Nothing auto-confirms.** Propagation writes proposals with `confirmed = false`. Only the
review UI sets `confirmed = true`.

**Detection and embedding are separate stages.** `model_version` is recorded per face so a
future model swap only requires re-running Phase 3.

---

## Risks

**SFace is the weak link.** It is meaningfully below ArcFace/InsightFace accuracy. Expect real
manual correction volume, especially on children across years, profiles, and sunglasses.

The mitigation is structural rather than clever: detection and embedding are separate, the
schema records `model_version`, and nothing downstream depends on the vectors being
SFace-specific. If accuracy disappoints, swapping in an InsightFace sidecar means re-running
Phase 3 while Phases 4–8 and every existing confirmation stay intact. Build it this way
regardless — get the pipeline and review UI working with the zero-infrastructure option, and
only pay for better embeddings if needed.

**YuNet will miss faces.** Profiles, heavy backlighting, faces under ~30px. Detection recall
caps everything downstream, so if Phase 3 finds suspiciously few faces per photo, investigate
that before blaming the matching.

**Native memory.** The single most likely source of a mystifying crash. Close the `Mat`s.

**Rekognition was considered and rejected.** It never returns the raw embedding — only FaceIds
and similarity scores — so the co-occurrence clustering this whole plan depends on would be
impossible without O(N) paid API calls, and the result would be vendor-locked. Local
embeddings in pgvector keep clustering, re-clustering, and threshold changes cheap.

---

## Open questions

- **Audit the People children for non-person categories** before Phase 4. Anything like
  "Group shots", "Unknown", "Kids", or a pet is a category that will seed a bogus person if a
  solo-tagged photo happens to contain one face. Maintain an explicit exclusion list in
  `PeopleService` rather than assuming every child of People is an individual.
- Decide the `private` photo scanning policy (recommended: scan, gate on read).
- Decide whether confirming a face should also set the photo as the category's `default_photo`
  when the category has none — a cheap nicety given `Category.defaultPhoto` already exists.

---

## Implementation notes

What this plan turned into, and where the code deviates from it.

### Setup, in order

1. Run `src/main/resources/sql/faces-schema.sql` against `photo_gallery`. The face tables and the
   `pgvector` extension are created by hand; nothing creates them at startup, and the People tab
   explains itself rather than failing if they're missing.
2. Run `src/main/resources/sql/faces-phase0-feasibility.sql` for Phase 0. It also reports the
   People root's `category_id`, which is what `PeopleCategoryId` wants.
3. Fill in `PeopleCategoryId`, `FaceModelsDirectory` and optionally `PeopleExclusions` in
   `src/config.properties`. All three are optional; without them face tagging is simply inert.
4. Download the two ONNX models from opencv_zoo into `FaceModelsDirectory`.
5. Admin UI → **People** tab → *Scan for faces* (Phase 3), then *Match faces* (Phases 4–6).

### Resolved open questions

- **Non-person categories** — `PeopleExclusions` in `src/config.properties`, matched
  case-insensitively against the category description. Excluded categories are skipped by
  seeding, matching and proposals, and an excluded category's whole subtree is skipped with it.
- **Private photos** — scanned. The embeddings stay in local Postgres, and nothing exposes faces
  over the API, so there's nothing new for `includePrivate` to gate yet.
- **`default_photo`** — implemented. `PeopleService.assignFaces` gives a person category a cover
  photo from the first face confirmed for them, but only when it has none.

### Deviations from the plan

- **Thresholds.** Rules 1 and 4 in Phase 5 are implemented as one rule: a person *tagged* on the
  photo is proposed at ≥ 0.363 (the model's own same-identity cutoff), and an untagged person
  needs ≥ 0.50. Both write `confirmed = false`, since "auto-propose" and "queue for review" are
  the same thing once nothing auto-confirms — the review queue is sorted by score, which is what
  makes the distinction visible where it matters.
- **Similarity queries are native SQL**, using pgvector's `<=>` operator with the probe vector
  written into the statement as a literal. That keeps the HNSW index in play and depends only on
  pgvector. `hibernate-vector` is still what maps the `embedding` column itself; it is *not*
  managed by the Spring Boot BOM, so the pom pins it to `${hibernate.version}`.
- **OpenCV natives are pinned by explicit `<classifier>`**, not by the `javacpp.platform`
  property. That property only takes effect as a `-D` command line argument — as a pom property
  it can't activate the profiles inside the javacpp-presets parent, so it silently does nothing
  and all ~1GB of natives come along anyway.
- **OpenCV must be 4.11.0-1.5.12 or newer.** In `4.10.0-1.5.11` the macosx-arm64
  `libopencv_videoio` links against `@loader_path/libOrbbecSDK.1.9.dylib`, which upstream ships
  the licence for but not the library. `FaceDetectorYN`'s class initializer loads videoio
  transitively, so the whole encoder dies with `UnsatisfiedLinkError` on that platform. 4.11 and
  4.13 have no `@loader_path` dependencies at all. `openblas` and `javacpp` versions have to move
  in lockstep — take them from the `opencv` artifact's own pom.
- **`FaceDetectorYN.create` has no 6-argument overload** in the Java bindings. The call passes
  the two extra `backend_id`/`target_id` arguments as 0.
- **Propagation is scoped.** `propagate(photoIds, …)` exists alongside the full pass so that
  Phase 9 can match an import's photos without re-scoring the library, and
  `propagateForPerson` works outwards from one person's exemplars — one indexed query per
  exemplar rather than one per unmatched face in the library.
- **Re-scanning is exposed.** *Re-scan everything* in the People tab clears the scan stamps, with
  the choice of keeping confirmed faces or not. Without it the `model_version` column's whole
  purpose — making a model swap tractable — had no way to be acted on.
- **Phase 7's third sub-view carries the recall report.** "Tagged, no face" is a column in the
  People list rather than a separate report, so the number that says *detection* is the problem
  sits next to the people it's a problem for.
- **The People list is the triage screen, sorted on Coverage %.** Confirmed faces as a percentage
  of photos tagged is the "does this person need curating" number: the review queue's own ordering
  (pending count, descending) surfaces whoever the matcher already has plenty of material for,
  which is the opposite of who needs attention. Double-click or *Review* jumps to that person's
  queue. **"Last photo" is derived from the `YYYY-MM` in filenames**, because the `photo` table has
  no date column of any kind — coverage is 100% for the newest few thousand photos and thins going
  back, which is the right way round for "who is in photos I'm still taking". Unparseable
  filenames report null rather than guessing.
- **The review queue is a keyboard loop.** Enter confirms, Delete rejects, and right-click offers
  the same plus "this is someone else" (which records the rejection *and* the correction in one
  step) and "select this photo". Working a queue of thousands one button-trip at a time is the
  difference between it getting cleared and not.
- **Face crops are cached across refreshes.** A crop is a pure function of a face id — the box and the
  source image are fixed at detection, and ids are never reused — so confirming or rejecting one
  face must not invalidate the grid. Only re-running detection clears the cache. Two things this
  depends on: crops must be *copied* rather than produced by `BufferedImage.getSubimage`, which
  returns a view that pins the whole 1400px source image per entry; and the two expensive sub-tabs
  (cluster summaries, and the "tagged, no face" `NOT EXISTS` aggregate) recompute lazily when shown
  rather than on every action.
- **Corroborated proposals are marked in the grid.** A proposal whose photo is *already* tagged
  with that person doesn't rest on the embedding alone — the hand-tagging agrees with it — so those
  cells get a green border and a `✓` before the score. That set is computed once per queue load,
  not per cell, because cell renderers run during painting.
- **The overlay on the photo preview has the same right-click menu**, which makes single-photo
  tagging — the case that actually matters for new imports — a right-click and one item. It needs
  no queries beyond the People subtree: the photo's categories are already in the panel's list
  model and every face is already in the overlay's box list, so who is tagged and who is spoken
  for are both known on screen.
- **Right-click names the people already tagged on that photo.** A wrong proposal is usually the
  matcher picking the wrong one of the people who were hand-tagged there, so those get a
  one-click "This is Bob" item each — no typing, no dialog. People already *confirmed* on another
  face in the same photo are filtered out by the one-person-per-photo rule; unconfirmed proposals
  are not, since they're guesses and correcting guesses is what this menu is for.
- **Control rows in the People tab must use `WrapLayout`, not `FlowLayout`.** The tab lives in the
  narrow left column, and `BorderLayout.NORTH` grants only the preferred height — which
  `FlowLayout` reports as a single row even though it wraps when it lays out. The overflow gets
  positioned where there is no room to paint it, so it disappears without a trace. This silently
  hid the Reject button.