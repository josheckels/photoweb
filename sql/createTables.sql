CREATE TABLE photographer (
	photographer_id     SERIAL         PRIMARY KEY,
	name                VARCHAR( 200 ) NOT NULL,
	copyright           TEXT           NOT NULL
);

CREATE TABLE photo (
	photo_id     SERIAL           PRIMARY KEY,
	filename     VARCHAR( 50 )    NOT NULL,
	caption      VARCHAR( 255 ),
	height       INT              NOT NULL,
	width        INT              NOT NULL,
	movie        BOOLEAN,
	photographer_id BIGINT,
	private       BOOLEAN         NOT NULL,

	CONSTRAINT photo_filename_un UNIQUE ( filename )
);

ALTER TABLE photo ADD CONSTRAINT photo_photographer_fk FOREIGN KEY ( photographer_id )
REFERENCES photographer ( photographer_id );

CREATE INDEX photo_photographer_idx ON photo ( photographer_id );

CREATE TABLE category (
	category_id          SERIAL          PRIMARY KEY,
	parent_category_id   BIGINT          NULL,
	description          VARCHAR( 255 ),
	created_on           DATE            NOT NULL,
	default_photo_id     BIGINT          NULL,
	private              BOOLEAN         NOT NULL 
);

ALTER TABLE category ADD CONSTRAINT category_parent_pk FOREIGN KEY ( parent_category_id )
REFERENCES category ( category_id ) ON DELETE CASCADE;

CREATE INDEX category_parent_category_idx ON category (parent_category_id);

ALTER TABLE category ADD CONSTRAINT category_photo_pk FOREIGN KEY ( default_photo_id )
REFERENCES photo ( photo_id );

CREATE INDEX category_default_photo_idx ON category (default_photo_id);

CREATE TABLE photo_category_link (
	photo_category_link_id    SERIAL   PRIMARY KEY,
	photo_id                  INT      NOT NULL,
	category_id               INT      NOT NULL,
	CONSTRAINT link_photo_id_fk FOREIGN KEY ( photo_id ) REFERENCES photo( photo_id ) ON DELETE CASCADE,
	CONSTRAINT link_photo_category_id_fk FOREIGN KEY ( category_id ) REFERENCES category ( category_id ) ON DELETE CASCADE,
	CONSTRAINT photo_category_link_unique UNIQUE ( photo_id, category_id )
);

CREATE INDEX photo_category_category_idx ON photo_category_link (category_id);
CREATE INDEX photo_category_photo_idx ON photo_category_link (photo_id);

CREATE TABLE comment (
	comment_id                SERIAL   PRIMARY KEY,
	photo_id                  BIGINT      NOT NULL,
	name                      VARCHAR(100)      NOT NULL,
	comment                   TEXT      NOT NULL,
	created_on                TIMESTAMP            NOT NULL,
	remote_ip                 VARCHAR(40),
	remote_host               VARCHAR(100),
	email                     VARCHAR(100),
    CONSTRAINT comment_photo_id_fk FOREIGN KEY ( photo_id ) REFERENCES photo( photo_id ) ON DELETE CASCADE
);

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
    cluster_id         INT
    );

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

ALTER TABLE photo_face ADD COLUMN IF NOT EXISTS ignored BOOLEAN NOT NULL DEFAULT FALSE;