package org.example.app;

public class Launcher {

    static {
        /*
         * The Windows Direct3D JavaFX pipeline can intermittently create a
         * presentable surface with the invalid size -1 x -1 while the main
         * ERP window is being resized or its content is replaced.  The
         * result is a blank page even though the FXML/controller loaded.
         * Select the stable software pipeline before any JavaFX class is
         * initialized.  A caller can still override this with an explicit
         * -Dprism.order value if required on another installation.
         */
        if (System.getProperty("prism.order") == null) {
            System.setProperty("prism.order", "sw");
        }
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
