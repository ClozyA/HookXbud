package xyz.fearr.hookcommon.xposed;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;

/**
 * Instrumentation fallback: no SDK constructor or lifecycle code is executed.
 */
@SuppressLint("Registered")
public final class NoAdActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        finish();
    }
}
