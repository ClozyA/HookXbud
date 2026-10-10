package xyz.fearr.hookcommon;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Shared inspected versions; mismatches are advisory, never an injection gate.
 */
public final class TargetCatalog {
    private TargetCatalog() {
    }

    public record Target(String packageName, String name, String versionName, long versionCode) {
        public boolean matches(String installedName, long installedCode) {
            return versionName.equals(installedName) && versionCode == installedCode;
        }
    }

    public static final List<Target> TARGETS = Collections.unmodifiableList(Arrays.asList(
            new Target("run.xbud.android", "小步点", "2.6.8", 150L)));

    public static Target find(String packageName) {
        for (Target target : TARGETS) if (target.packageName().equals(packageName)) return target;
        return null;
    }
}
