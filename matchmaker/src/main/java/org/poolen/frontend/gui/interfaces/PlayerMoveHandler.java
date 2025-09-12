package org.poolen.frontend.gui.interfaces;

import org.poolen.backend.db.entities.Group;
import java.util.UUID;

@FunctionalInterface
public interface PlayerMoveHandler {
    void onMove(UUID sourceGroupUuid, UUID playerUuid, Group targetGroup);
}
