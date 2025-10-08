package org.poolen.frontend.util.interfaces.providers;

import javafx.scene.Node;
import org.poolen.frontend.gui.components.dialogs.BaseDialog;
import org.poolen.frontend.gui.components.overlays.LoadingOverlay;

public interface CoreProvider {
    LoadingOverlay createLoadingOverlay();
    BaseDialog createDialog(BaseDialog.DialogType type, String content, Node owner);
}
