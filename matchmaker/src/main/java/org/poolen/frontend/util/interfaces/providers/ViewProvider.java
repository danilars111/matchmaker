package org.poolen.frontend.util.interfaces.providers;

import org.poolen.frontend.gui.components.views.GroupDisplayView;
import org.poolen.frontend.gui.components.views.forms.CharacterFormView;
import org.poolen.frontend.gui.components.views.forms.GroupFormView;
import org.poolen.frontend.gui.components.views.forms.PlayerFormView;
import org.poolen.frontend.gui.components.views.tables.GroupTableView;
import org.poolen.frontend.gui.components.views.tables.rosters.CharacterRosterTableView;
import org.poolen.frontend.gui.components.views.tables.rosters.GroupAssignmentRosterTableView;
import org.poolen.frontend.gui.components.views.tables.rosters.PlayerManagementRosterTableView;

public interface ViewProvider {
    CharacterFormView getCharacterFormView();
    GroupFormView getGroupFormView();
    PlayerFormView getPlayerFormView();
    CharacterRosterTableView getCharacterRosterTableView();
    GroupAssignmentRosterTableView getGroupAssignmentRosterTableView();
    PlayerManagementRosterTableView getPlayerManagementRosterTableView();
    GroupTableView getGroupTableView();
    GroupDisplayView getGroupDisplayView();
}
