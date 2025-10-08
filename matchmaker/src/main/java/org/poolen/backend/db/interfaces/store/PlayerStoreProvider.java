package org.poolen.backend.db.interfaces.store;

import org.poolen.backend.db.store.PlayerStore;

public interface PlayerStoreProvider {
    PlayerStore getPlayerStore();
}
