package xyz.fearr.hookxbud.xposed;

import org.junit.Test;
import static org.junit.Assert.*;

public final class XbudTargetsTest {
    @Test public void knownRewardAndSharedWebPagesNeverReceiveAutomaticClicksOrTracing() {
        String[] excluded = {
                "com.bz.ptgapi.activity.PtgRewardVideoPortraitActivity",
                "com.bz.ptgapi.activity.PtgRewardVideoLandscapeActivity",
                "com.kwad.sdk.api.proxy.app.KsRewardVideoActivity",
                "com.bytedance.sdk.openadsdk.core.component.reward.activity.TTRewardVideoActivity",
                XbudTargets.FULL_SCREEN,
                "com.bz.ptgapi.activity.PtgWebActivity",
                "run.xbud.android.mvp.ui.other.XBDFlutterActivity"
        };
        for (String name : excluded) {
            assertNull(name, XbudTargets.autoButton(name));
            assertTrue(name, XbudTargets.autoButtons(name).isEmpty());
            assertFalse(name, XbudTargets.traceClose(name));
            assertFalse(name, XbudTargets.inspectUi(name));
        }
    }

    @Test public void portraitCloseRequiresTheObservedIdResourceNameAndViewType() {
        XbudTargets.Button button = XbudTargets.autoButton(XbudTargets.PORTRAIT);
        assertNotNull(button);
        String type = "com.bz.ptgapi.widget.PtgRoundFrameLayout";
        assertTrue(button.matches(0x7f0a01b4, "closeAdvertLayout", type));
        assertFalse(button.matches(0x7f0a01b5, "closeAdvertLayout", type));
        assertFalse(button.matches(0x7f0a01b4, "downloadButton", type));
        assertFalse(button.matches(0x7f0a01b4, "closeAdvertLayout", "android.widget.FrameLayout"));
    }

    @Test public void otherFullscreenVariantsAreTraceOnlyUntilTheirCloseControlIsVerified() {
        for (String name : new String[]{XbudTargets.LANDSCAPE}) {
            assertNull(XbudTargets.autoButton(name));
            assertTrue(XbudTargets.traceClose(name));
        }
        assertNull(XbudTargets.autoButton(XbudTargets.PORTRAIT + "$Unknown"));
    }

    @Test public void sdkSkipControlsAreRestrictedToSplashAndExactResourceAndType() {
        assertEquals(6, XbudTargets.autoButtons(XbudTargets.SPLASH).size());
        XbudTargets.Button sdk = XbudTargets.autoButtons(XbudTargets.SPLASH).get(1);
        assertTrue(sdk.matches(0x7f0a060f, "ksad_splash_skip_left_view",
                "com.kwad.components.ad.splashscreen.widget.SkipView"));
        assertFalse(sdk.matches(0x7f0a060f, "ksad_splash_skip_left_view", "android.widget.TextView"));
        assertTrue(XbudTargets.autoButtons(XbudTargets.MAIN).isEmpty());
        assertTrue(XbudTargets.inspectUi(XbudTargets.MAIN));
        assertFalse(XbudTargets.inspectUi(XbudTargets.PORTRAIT));
    }
}
