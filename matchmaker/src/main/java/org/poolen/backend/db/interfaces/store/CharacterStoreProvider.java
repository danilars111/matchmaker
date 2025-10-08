package org.poolen.backend.db.interfaces.store;

import org.poolen.backend.db.store.CharacterStore;

public interface CharacterStoreProvider {
    CharacterStore getCharacterStore();
}
