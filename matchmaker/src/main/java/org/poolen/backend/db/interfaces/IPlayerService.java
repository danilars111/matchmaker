package org.poolen.backend.db.interfaces;

import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.jpa.entities.PlayerEntity;

/**
 * A specific service interface for Players, extending the generic IService.
 * This includes methods unique to Player business logic, like shallow conversions.
 */
public interface IPlayerService extends IService<Player, PlayerEntity> {

    /**
     * Converts a PlayerEntity to a Player domain object WITHOUT its character list.
     * This shallow conversion is used to break infinite recursion loops.
     * @param entity The entity to convert.
     * @return The converted Player domain object.
     */
    Player toDomainObjectShallow(PlayerEntity entity);
}
