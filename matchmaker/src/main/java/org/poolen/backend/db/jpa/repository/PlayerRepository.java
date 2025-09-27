package org.poolen.backend.db.jpa.repository;

import org.poolen.backend.db.jpa.entities.PlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlayerRepository extends JpaRepository<PlayerEntity, Long> {
    PlayerEntity findByUuid(UUID uuid);

    @Query("SELECT DISTINCT p FROM PlayerEntity p " +
            "LEFT JOIN FETCH p.characters " +
            "LEFT JOIN FETCH p.blacklist " +
            "LEFT JOIN FETCH p.buddylist " +
            "LEFT JOIN FETCH p.dmBlacklist " +
            "LEFT JOIN FETCH p.playerLog")
    List<PlayerEntity> findAllWithDetails();
}
