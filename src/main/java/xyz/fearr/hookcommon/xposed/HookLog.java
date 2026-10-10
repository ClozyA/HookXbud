package xyz.fearr.hookcommon.xposed;

import io.github.libxposed.api.XposedInterface;

/** Keep hook diagnostics in the framework log, with no companion app process required. */
public final class HookLog {
    public static void log(XposedInterface framework, int priority, String tag, String message) {
        framework.log(priority, tag, message);
    }

    public static void log(XposedInterface framework, int priority, String tag, String message, Throwable error) {
        framework.log(priority, tag, message, error);
    }

    private HookLog() {
    }
}
