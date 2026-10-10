package xyz.fearr.hookxbud.xposed;

/**
 * Exact allowlist from the supplied APK and the user's ordinary-ad close trace.
 */
final class XbudTargets {
    static final String SPLASH = "run.xbud.android.mvp.ui.other.SplashActivity";
    static final String MAIN = "run.xbud.android.mvp.ui.other.MainActivity";
    static final String PORTRAIT = "com.bz.ptgapi.activity.PtgInteractionPortraitActivity";
    static final String LANDSCAPE = "com.bz.ptgapi.activity.PtgInteractionLandscapeActivity";
    static final String FULL_SCREEN = "com.bytedance.sdk.openadsdk.core.component.reward.activity.TTFullScreenVideoActivity";

    record Button(int id, String resourceName, String expectedType, String logName, int maxChecks) {
        boolean matches(int actualId, String actualResourceName, String actualType) {
            return id == actualId && resourceName.equals(actualResourceName)
                    && (expectedType == null || expectedType.equals(actualType));
        }
    }

    private static final Button SPLASH_BUTTON = new Button(0x7f0a0154, "btnJump", null, "splash", 40);
    private static final Button CLOSE_BUTTON = new Button(0x7f0a01b4, "closeAdvertLayout",
            "com.bz.ptgapi.widget.PtgRoundFrameLayout", "interstitial", 240);
    // Supplied APK resource table + splash XML. Never apply these IDs on reward pages.
    private static final java.util.List<Button> SPLASH_BUTTONS = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
            SPLASH_BUTTON,
            new Button(0x7f0a060f, "ksad_splash_skip_left_view",
                    "com.kwad.components.ad.splashscreen.widget.SkipView", "splash", 40),
            new Button(0x7f0a0610, "ksad_splash_skip_right_view",
                    "com.kwad.components.ad.splashscreen.widget.SkipView", "splash", 40),
            new Button(0x7f0a05f7, "ksad_splash_circle_skip_left_view",
                    "com.kwad.components.ad.splashscreen.widget.CircleSkipView", "splash", 40),
            new Button(0x7f0a05f8, "ksad_splash_circle_skip_right_view",
                    "com.kwad.components.ad.splashscreen.widget.CircleSkipView", "splash", 40),
            new Button(0x7f0a0399, "iv_ad_skip_countdown", "android.widget.TextView", "splash", 40)));

    static Button autoButton(String activityName) {
        if (SPLASH.equals(activityName)) return SPLASH_BUTTON;
        if (PORTRAIT.equals(activityName)) return CLOSE_BUTTON;
        return null;
    }

    static boolean traceClose(String activityName) {
        // User confirmed TTFullScreenVideoActivity is entered from the reward button.
        return PORTRAIT.equals(activityName) || LANDSCAPE.equals(activityName);
    }

    static java.util.List<Button> autoButtons(String activityName) {
        if (SPLASH.equals(activityName)) return SPLASH_BUTTONS;
        if (PORTRAIT.equals(activityName)) return java.util.Collections.singletonList(CLOSE_BUTTON);
        return java.util.Collections.emptyList();
    }

    static boolean inspectUi(String activityName) {
        return SPLASH.equals(activityName) || MAIN.equals(activityName);
    }

    private XbudTargets() {
    }
}
