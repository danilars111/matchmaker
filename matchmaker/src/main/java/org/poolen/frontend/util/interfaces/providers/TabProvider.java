package org.poolen.frontend.util.interfaces.providers;

import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.components.tabs.CharacterManagementTab;
import org.poolen.frontend.gui.components.tabs.GroupManagementTab;
import org.poolen.frontend.gui.components.tabs.PersistenceTab;
import org.poolen.frontend.gui.components.tabs.PlayerManagementTab;
import org.poolen.frontend.gui.components.tabs.SettingsTab;

import java.util.Map;
import java.util.UUID;

public interface TabProvider {
    CharacterManagementTab getCharacterManagementTab();
    GroupManagementTab getGroupManagementTab();
    PersistenceTab getPersistenceTab();
    PlayerManagementTab getPlayerManagementTab();
    SettingsTab getSettingsTab();
}
