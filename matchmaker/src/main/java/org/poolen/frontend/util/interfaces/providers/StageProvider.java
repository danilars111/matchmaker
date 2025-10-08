package org.poolen.frontend.util.interfaces.providers;

import org.poolen.frontend.gui.components.stages.ExportGroupsStage;
import org.poolen.frontend.gui.components.stages.ManagementStage;
import org.poolen.frontend.gui.components.stages.SetupStage;

public interface StageProvider {
    SetupStage createSetupStage();
    ManagementStage getManagementStage();
    ExportGroupsStage getExportGroupsStage();
}
