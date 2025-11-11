package org.poolen.frontend.util.interfaces.providers;

import org.poolen.frontend.gui.components.stages.ExportGroupsStage;
import org.poolen.frontend.gui.components.stages.ImportMatcherStage;
import org.poolen.frontend.gui.components.stages.ManagementStage;
import org.poolen.frontend.gui.components.stages.SetupStage;
import org.poolen.frontend.gui.components.stages.email.AccessRequestStage;
import org.poolen.frontend.gui.components.stages.email.BugReportStage;
import org.poolen.frontend.gui.components.stages.email.CrashReportStage;

public interface StageProvider {
    SetupStage createSetupStage();
    ManagementStage getManagementStage();
    ExportGroupsStage getExportGroupsStage();
    ImportMatcherStage getImportMatcherStage();
    AccessRequestStage createAccessRequestStage();
    BugReportStage createBugReportStage();
    CrashReportStage createCrashReportStage(Throwable e);

}
