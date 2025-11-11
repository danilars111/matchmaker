package org.poolen.frontend.util.interfaces.providers;

import org.poolen.frontend.gui.components.tabs.CharacterManagementTab;
import org.poolen.frontend.gui.components.tabs.GroupManagementTab;
import org.poolen.frontend.gui.components.tabs.SheetsTab;
import org.poolen.frontend.gui.components.tabs.PlayerManagementTab;
import org.poolen.frontend.gui.components.tabs.SettingsTab;

public interface TabProvider {
    CharacterManagementTab getCharacterManagementTab();
    GroupManagementTab getGroupManagementTab();
    SheetsTab getSheetsTab();
    PlayerManagementTab getPlayerManagementTab();
    SettingsTab getSettingsTab();
}
