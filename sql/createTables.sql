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