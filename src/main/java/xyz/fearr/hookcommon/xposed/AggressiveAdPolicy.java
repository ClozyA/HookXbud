package xyz.fearr.hookcommon.xposed;

/** Xbud ad SDK namespaces observed in the inspected target APK. */
final class AggressiveAdPolicy {
    private static final String[] SDK_PREFIXES = {
            "com.bytedance.sdk.openadsdk.", "com.byazt.", "com.kwad.", "com.qq.e.",
            "com.baidu.mobads.", "com.beizi.", "biz.beizi.adn.", "com.bz.",
            "com.sy.adsdk.", "com.gt.sdk.", "xyz.adscope.amps.", "com.kc.",
            "com.octopus.ad.", "com.qumeng.advlib.", "com.sigmob.sdk.",
            "com.meishu.sdk.", "com.mercury.sdk.", "cj.mobile.", "com.fancy.mpsdk."
    };

    static boolean sdkClass(String name) {
        if (name == null) return false;
        for (String prefix : SDK_PREFIXES) if (name.startsWith(prefix)) return true;
        return false;
    }

    static boolean adActivity(String name) {
        return sdkClass(name);
    }

    static boolean splash(String packageName, String activityName) {
        return "run.xbud.android".equals(packageName)
                && "run.xbud.android.mvp.ui.other.SplashActivity".equals(activityName);
    }

    private AggressiveAdPolicy() {
    }
}
