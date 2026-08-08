-- Schema for automatic face tagging (see FACES.md).
--
-- This project has no Flyway/Liquibase, so run this by hand against photo_gallery before starting the admin UI
-- with face tagging for the first time:
--
--     psql photo_gallery -f src/main/resources/sql/faces-schema.sql
--
-- CREATE EXTENSION requires superuser (or a role with the pg_create_extension privilege) the first time.

CREATE EXTENSION IF NOT EXISTS vector;

-- Distinguishes "scanned, found 0 faces" from "not scanned yet", which is what makes the backfill resumable.
ALTER TABLE photo ADD COLUMN IF NOT EXISTS face_scanned_on TIMESTAMP;

CREATE TABLE IF NOT EXISTS photo_face (
  face_id            BIGSERIAL PRIMARY KEY,
  photo_id           INT  NOT NULL REFERENCES photo(photo_id) ON DELETE CASCADE,
  -- Bounding box normalized 0..1 against the ORIGINAL image, so it stays valid regardless of which resized
  -- variant detection happened to run against.
  x REAL, y REAL, w REAL, h REAL,
  detect_score       REAL,
  embedding          vector(128) NOT NULL,
  model_version      TEXT NOT NULL,  -- makes a future model swap tractable
  person_category_id INT  REFERENCES category(category_id) ON DELETE SET NULL,
  confirmed          BOOLEAN NOT NULL DEFAULT FALSE,
  match_score        REAL,
  cluster_id         INT,
  -- A face that is nobody: a poster on the wall, a stranger in the background, a false positive. Excluded from
  -- seeding, propagation and clustering entirely, rather than rejected against one person at a time.
  ignored            BOOLEAN NOT NULL DEFAULT FALSE
);

-- Separately, for databases created before the column existed.
ALTER TABLE photo_face ADD COLUMN IF NOT EXISTS ignored BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS photo_face_photo_id_idx ON photo_face (photo_id);
CREATE INDEX IF NOT EXISTS photo_face_person_category_id_idx ON photo_face (person_category_id) WHERE person_category_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS photo_face_cluster_id_idx ON photo_face (cluster_id) WHERE cluster_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS photo_face_embedding_idx ON photo_face USING hnsw (embedding vector_cosine_ops);

-- Prevents rejected matches from being re-proposed on every propagation round.
CREATE TABLE IF NOT EXISTS face_person_rejection (
  face_id     BIGINT NOT NULL REFERENCES photo_face(face_id)   ON DELETE CASCADE,
  category_id INT    NOT NULL REFERENCES category(category_id) ON DELETE CASCADE,
  PRIMARY KEY (face_id, category_id)
);
