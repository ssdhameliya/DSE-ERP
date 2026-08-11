package org.example.app;

import java.lang.management.ManagementFactory;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class Launcher {

    private static final String NATIVE_ACCESS_RELAUNCH = "dse.erp.nativeAccessRelaunch";

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

    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean(NATIVE_ACCESS_RELAUNCH)) {
            System.exit(relaunchWithNativeAccess(args));
            return;
        }
        Main.launch(args);
    }

    /**
     * Java 25 requires native access to be granted before JavaFX loads Glass.
     * Relaunching here keeps IntelliJ, Maven and packaged installations warning-free
     * without relying on a developer-specific VM-options field.
     */
    private static int relaunchWithNativeAccess(String[] applicationArguments) throws Exception {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-agentlib:") || argument.startsWith("-javaagent:")
                    || argument.startsWith("--enable-native-access")) continue;
            command.add(argument);
        }
        String originalClasspath = System.getProperty("java.class.path");
        List<String> classpathEntries = Arrays.asList(originalClasspath.split(
                Pattern.quote(File.pathSeparator)));
        List<String> javafxModules = classpathEntries.stream()
                .filter(Launcher::isPlatformJavaFxJar)
                .toList();
        List<String> applicationClasspath = javafxModules.isEmpty() ? classpathEntries
                : classpathEntries.stream().filter(entry -> !isAnyJavaFxJar(entry)).toList();
        boolean namedJavaFx = !javafxModules.isEmpty()
                || ModuleLayer.boot().findModule("javafx.graphics").isPresent();
        if (!javafxModules.isEmpty()) {
            command.add("--module-path");
            command.add(String.join(File.pathSeparator, javafxModules));
            command.add("--add-modules=javafx.controls,javafx.fxml");
        }
        if (namedJavaFx) {
            command.add("--enable-native-access=javafx.graphics,ALL-UNNAMED");
        } else {
            command.add("--enable-native-access=ALL-UNNAMED");
        }
        command.add("-D" + NATIVE_ACCESS_RELAUNCH + "=true");
        command.add("-cp");
        command.add(String.join(File.pathSeparator, applicationClasspath));
        command.add(Launcher.class.getName());
        command.addAll(Arrays.asList(applicationArguments));

        Process child = new ProcessBuilder(command).inheritIO().start();
        Thread cleanup = new Thread(child::destroy, "dse-erp-launcher-cleanup");
        Runtime.getRuntime().addShutdownHook(cleanup);
        try {
            return child.waitFor();
        } finally {
            try { Runtime.getRuntime().removeShutdownHook(cleanup); }
            catch (IllegalStateException ignored) { }
        }
    }

    private static boolean isPlatformJavaFxJar(String entry) {
        String name = Path.of(entry).getFileName().toString().toLowerCase(Locale.ROOT);
        return name.matches("javafx-(base|controls|graphics|fxml)-.+-(win|mac(-aarch64)?|linux(-aarch64)?)[.]jar");
    }

    private static boolean isAnyJavaFxJar(String entry) {
        String name = Path.of(entry).getFileName().toString().toLowerCase(Locale.ROOT);
        return name.matches("javafx-(base|controls|graphics|fxml)-.+[.]jar");
    }
}
