ALTER TABLE game_semantic_word
    ADD COLUMN hint1 VARCHAR(256) NOT NULL DEFAULT '' COMMENT 'weakest progressive hint (unlock at 10 unique guesses)',
    ADD COLUMN hint2 VARCHAR(256) NOT NULL DEFAULT '' COMMENT 'medium progressive hint (unlock at 20)',
    ADD COLUMN hint3 VARCHAR(256) NOT NULL DEFAULT '' COMMENT 'strongest progressive hint (unlock at 30)';
