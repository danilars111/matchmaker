module com.example.matchmaker {
    requires javafx.controls;
    requires javafx.fxml;
            
            requires com.dlsc.formsfx;
                requires org.kordamp.ikonli.javafx;
                
    opens com.example.matchmaker to javafx.fxml;
    exports com.example.matchmaker;
}