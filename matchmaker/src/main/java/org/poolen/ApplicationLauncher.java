package org.poolen;

import com.google.ortools.Loader;
import javafx.application.Application;
import org.poolen.frontend.gui.LoginApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApplicationLauncher {

    public static void main(String[] args) {
        Loader.loadNativeLibraries();
        Application.launch(LoginApplication.class, args);
    }

}
