-- =============================================
-- This script creates the complete initial database schema for the Matchmaker application.
-- =============================================

-- Main 'players' table
-- Stores the core information for each player.
-- =============================================
CREATE TABLE players (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         uuid BINARY(16) NOT NULL UNIQUE,
                         name VARCHAR(255) NOT NULL,
                         is_dungeon_master BOOLEAN NOT NULL DEFAULT FALSE,
                         last_seen DATE
);

-- =============================================
-- 'characters' table
-- Stores characters, each linked to a single player.
-- =============================================
CREATE TABLE characters (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            uuid BINARY(16) NOT NULL UNIQUE,
                            player_id BIGINT NOT NULL,
                            name VARCHAR(255) NOT NULL,
                            house VARCHAR(255),
                            is_main BOOLEAN NOT NULL DEFAULT FALSE,
                            is_retired BOOLEAN NOT NULL DEFAULT FALSE,
                            CONSTRAINT fk_character_player
                                FOREIGN KEY (player_id) REFERENCES players(id)
                                    ON DELETE CASCADE
);

-- =============================================
-- Join table for the player buddylist (@ManyToMany)
-- Connects players to the players on their buddylist.
-- =============================================
CREATE TABLE player_buddylist (
                                  player_id BIGINT NOT NULL,
                                  buddy_id BIGINT NOT NULL,
                                  PRIMARY KEY (player_id, buddy_id),
                                  CONSTRAINT fk_buddylist_player
                                      FOREIGN KEY (player_id) REFERENCES players(id)
                                          ON DELETE CASCADE,
                                  CONSTRAINT fk_buddylist_buddy
                                      FOREIGN KEY (buddy_id) REFERENCES players(id)
                                          ON DELETE CASCADE
);

-- =============================================
-- Join table for the player blacklist (@ManyToMany)
-- Connects players to the players they have blacklisted.
-- =============================================
CREATE TABLE player_blacklist (
                                  player_id BIGINT NOT NULL,
                                  blacklisted_player_id BIGINT NOT NULL,
                                  PRIMARY KEY (player_id, blacklisted_player_id),
                                  CONSTRAINT fk_blacklist_player
                                      FOREIGN KEY (player_id) REFERENCES players(id)
                                          ON DELETE CASCADE,
                                  CONSTRAINT fk_blacklist_blacklisted
                                      FOREIGN KEY (blacklisted_player_id) REFERENCES players(id)
                                          ON DELETE CASCADE
);

-- =============================================
-- Join table for the DM blacklist (@ManyToMany)
-- Connects players to the DMs they have blacklisted.
-- =============================================
CREATE TABLE player_dm_blacklist (
                                     player_id BIGINT NOT NULL,
                                     blacklisted_dm_id BIGINT NOT NULL,
                                     PRIMARY KEY (player_id, blacklisted_dm_id),
                                     CONSTRAINT fk_dm_blacklist_player
                                         FOREIGN KEY (player_id) REFERENCES players(id)
                                             ON DELETE CASCADE,
                                     CONSTRAINT fk_dm_blacklist_dm
                                         FOREIGN KEY (blacklisted_dm_id) REFERENCES players(id)
                                             ON DELETE CASCADE
);

-- =============================================
-- Table for the player play log
-- Stores the history of who a player has played with and when.
-- =============================================
CREATE TABLE player_playlog (
                                player_id BIGINT NOT NULL,
                                played_with_player_id BIGINT NOT NULL,
                                last_played_date DATE NOT NULL,
                                PRIMARY KEY (player_id, played_with_player_id, last_played_date),
                                CONSTRAINT fk_playlog_owner_player
                                    FOREIGN KEY (player_id) REFERENCES players(id)
                                        ON DELETE CASCADE,
                                CONSTRAINT fk_playlog_played_with_player
                                    FOREIGN KEY (played_with_player_id) REFERENCES players(id)
                                        ON DELETE CASCADE
);

-- =============================================
-- 'settings' table
-- Stores application-wide settings as key-value pairs.
-- =============================================
CREATE TABLE settings (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          name VARCHAR(255) NOT NULL UNIQUE,
                          description VARCHAR(1024) NOT NULL,
                          setting_value VARCHAR(1024)
);

