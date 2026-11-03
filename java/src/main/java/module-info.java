module spherets {
    requires javafx.controls;
    requires javafx.graphics;
    requires java.desktop;
    requires java.prefs;

    exports spherets.model;
    exports spherets.plot;
    exports spherets.ui;
    opens spherets.ui to javafx.graphics;
}
