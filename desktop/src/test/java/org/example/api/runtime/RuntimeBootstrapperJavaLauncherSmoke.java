package org.example.api.runtime;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RuntimeBootstrapperJavaLauncherSmoke {
    private RuntimeBootstrapperJavaLauncherSmoke() {}

    public static void main(String[] args) throws Exception {
        Path runtime = Files.createTempDirectory("dse-java-launcher-");
        try {
            Path bin = Files.createDirectories(runtime.resolve("bin"));
            Path java = Files.createFile(bin.resolve("java.exe"));
            Path javaw = Files.createFile(bin.resolve("javaw.exe"));
            assertEquals(javaw, RuntimeBootstrapper.javaExecutable(runtime, true, true),
                    "Packaged Windows must use the windowless Java launcher");
            assertEquals(java, RuntimeBootstrapper.javaExecutable(runtime, true, false),
                    "IntelliJ/development Windows must keep the console Java launcher");
            Files.delete(javaw);
            assertEquals(java, RuntimeBootstrapper.javaExecutable(runtime, true, true),
                    "Older runtimes without javaw.exe must safely fall back to java.exe");
        } finally {
            Files.walk(runtime).sorted((left, right) -> right.compareTo(left)).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            });
        }
        System.out.println("RuntimeBootstrapperJavaLauncherSmoke passed");
    }

    private static void assertEquals(Path expected, Path actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
