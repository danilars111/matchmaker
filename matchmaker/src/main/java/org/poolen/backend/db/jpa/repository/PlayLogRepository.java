package org.poolen.backend.db.jpa.repository;

import org.poolen.backend.db.jpa.entities.PlayLogEntity;
import org.poolen.backend.db.jpa.entities.ids.PlayLogEntityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayLogRepository extends JpaRepository<PlayLogEntity, PlayLogEntityId> {
    // Spring Data JPA provides all the necessary CRUD methods automatically.
}
