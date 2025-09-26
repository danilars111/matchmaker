package org.poolen.backend.db.jpa.repository;

import org.poolen.backend.db.jpa.entities.CharacterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CharacterRepository extends JpaRepository<CharacterEntity, Long> {

    /**
     * Finds a character by their public UUID.
     * Spring Data JPA will automatically implement this method based on its name.
     * @param uuid The UUID of the character to find.
     * @return The found CharacterEntity, or null if not found.
     */
    CharacterEntity findByUuid(UUID uuid);

}
