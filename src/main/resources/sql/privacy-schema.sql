-- Schema for per-person opt-out and event share links (see PRIVACY.md).
--
-- This project has no Flyway/Liquibase, so run this by hand against photo_gallery before starting the
-- application with the privacy changes for the first time:
--
--     psql photo_gallery -f src/main/resources/sql/privacy-schema.sql

-- Only meaningful on a person's category (a descendant of PeopleCategoryId): this person has asked not to appear
-- publicly, so every photo tagged with them is withheld from anonymous visitors. It hides photos, not the category.
ALTER TABLE category ADD COLUMN IF NOT EXISTS public_opt_out BOOLEAN NOT NULL DEFAULT FALSE;

-- The secret in this category's share link, or NULL if it isn't shared. One per category: regenerating it is what
-- revokes a link that has been sent out too widely. VARCHAR rather than CHAR because the entity maps it as a
-- String, and ddl-auto=validate rejects bpchar for one of those.
ALTER TABLE category ADD COLUMN IF NOT EXISTS share_token VARCHAR(43) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS category_share_token_idx ON category (share_token);

-- Makes "every photo tagged with someone who opted out" cheap, which is the one extra query each anonymous
-- request runs. Partial, because the true rows are the handful that matter.
CREATE INDEX IF NOT EXISTS category_public_opt_out_idx ON category (category_id) WHERE public_opt_out;

-- Installation-wide settings. Currently just 'owner.token', the owner's all-access secret, which replaces the old
-- unauthenticated /includePrivate URL. Kept here rather than in config.properties because that file is tracked in
-- git, and because rotating the token should be a button in the admin UI rather than an edit and a restart.
--
-- No row means owner unlock is switched off. Create one from the admin UI: right-click the tree root (<ROOT>) and
-- choose "Create owner link".
CREATE TABLE IF NOT EXISTS app_setting (
  setting_name  VARCHAR(64) PRIMARY KEY,
  setting_value VARCHAR(255)
);


-- ---------------------------------------------------------------------------------------------------------------
-- One-time backfill: make the People subtree private, which is what hides person tags from everyone but the owner.
--
-- Not run automatically, and worth checking before you commit it - review the SELECT first, then the UPDATE.
-- 169 is PeopleCategoryId from src/config.properties; change it if yours differs.
-- ---------------------------------------------------------------------------------------------------------------

-- WITH RECURSIVE people AS (
--     SELECT category_id, description FROM category WHERE category_id = 169
--     UNION ALL
--     SELECT c.category_id, c.description FROM category c JOIN people p ON c.parent_category_id = p.category_id
-- )
-- SELECT category_id, description FROM people ORDER BY description;

-- WITH RECURSIVE people AS (
--     SELECT category_id FROM category WHERE category_id = 169
--     UNION ALL
--     SELECT c.category_id FROM category c JOIN people p ON c.parent_category_id = p.category_id
-- )
-- UPDATE category SET private = TRUE WHERE category_id IN (SELECT category_id FROM people);
