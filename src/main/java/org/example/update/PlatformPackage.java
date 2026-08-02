package org.example.update;

import java.util.*;

public final class PlatformPackage {
    public enum Platform { WINDOWS, MACOS_X64, MACOS_ARM64, UNSUPPORTED }
    private PlatformPackage() {}

    public static Platform current() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return Platform.WINDOWS;
        if (os.contains("mac")) return arch.contains("aarch64") || arch.contains("arm64") ? Platform.MACOS_ARM64 : Platform.MACOS_X64;
        return Platform.UNSUPPORTED;
    }

    public static Optional<UpdateRelease.Asset> select(UpdateRelease release) {
        Platform platform = current();
        List<UpdateRelease.Asset> candidates = release.assets().stream().filter(a -> matches(a.name(), platform)).toList();
        if (candidates.isEmpty() && (platform == Platform.MACOS_X64 || platform == Platform.MACOS_ARM64)) {
            candidates = release.assets().stream().filter(a -> a.name().toLowerCase(Locale.ROOT).matches(".*\\.(dmg|pkg)$")).toList();
        }
        return candidates.stream().max(Comparator.comparingInt(a -> score(a.name(), platform)));
    }

    private static boolean matches(String name, Platform platform) {
        String n=name.toLowerCase(Locale.ROOT);
        return switch(platform) {
            case WINDOWS -> n.endsWith(".exe") || n.endsWith(".msi");
            case MACOS_ARM64 -> (n.endsWith(".dmg")||n.endsWith(".pkg")) && (n.contains("arm64")||n.contains("aarch64")||n.contains("apple-silicon"));
            case MACOS_X64 -> (n.endsWith(".dmg")||n.endsWith(".pkg")) && (n.contains("x64")||n.contains("x86_64")||n.contains("intel"));
            default -> false;
        };
    }
    private static int score(String name, Platform platform) { String n=name.toLowerCase(Locale.ROOT); int s=0; if(n.contains("dse"))s+=4; if(n.contains("erp"))s+=4; if(platform==Platform.WINDOWS&&n.endsWith(".msi"))s+=2; if(n.contains("setup")||n.contains("installer"))s+=2; return s; }
}
