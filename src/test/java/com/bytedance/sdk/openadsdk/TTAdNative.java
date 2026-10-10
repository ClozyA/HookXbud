package com.bytedance.sdk.openadsdk;

/** Test-only contract fixture. SDK objects/error constructors are deliberately absent. */
public interface TTAdNative {
    void loadSplashAd(Object slot, Object listener, int timeout);
    void loadRewardVideoAd(Object slot, Object listener);
    void loadFeedAd(Object slot, Object listener);
    String getSdkState();
    String loadToken();
}
