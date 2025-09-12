package org.poolen.frontend.gui.components.stages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A modal stage for selecting a date.
 */
public class CalendarStage extends Stage {

    private LocalDate selectedDate;

    public CalendarStage(Window owner, LocalDate initialDate) {
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setTitle("Select Event Date");

        DatePicker datePicker = new DatePicker(initialDate);
        Button setDateButton = new Button("Set Date");

        setDateButton.setOnAction(e -> {
            this.selectedDate = datePicker.getValue();
            close();
        });

        VBox layout = new VBox(10, datePicker, setDateButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));

        Scene scene = new Scene(layout);
        setScene(scene);
    }

    public Optional<LocalDate> getSelectedDate() {
        return Optional.ofNullable(selectedDate);
    }
}
