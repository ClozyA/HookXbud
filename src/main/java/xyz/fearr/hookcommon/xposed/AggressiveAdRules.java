package xyz.fearr.hookcommon.xposed;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/**
 * Aggressive display blocking for the Xbud process, including reward ads.
 */
public final class AggressiveAdRules {
    private static final String XBUD_SPLASH = "run.xbud.android.mvp.ui.other.SplashActivity";
    private static final String XBUD_MAIN = "run.xbud.android.mvp.ui.other.MainActivity";
    private final XposedInterface framework;
    private final String packageName;
    private final String tag;
    private final Set<String> logged = new HashSet<>();

    public AggressiveAdRules(XposedInterface framework, String packageName) {
        this.framework = framework;
        this.packageName = packageName;
        tag = "HookXbud";
    }

    public void install(ClassLoader loader) {
        installSplash(loader);
        installLaunchGuards();
        installViewGuards();
        new CsjRequestBlocker(framework, tag).install(loader);
        HookLog.log(framework, Log.INFO, tag, "Aggressive ad mode enabled: " + packageName + "; rewards disabled");
    }

    private void installSplash(ClassLoader loader) {
        if (!"run.xbud.android".equals(packageName)) return;
        try {
            Class<?> splash = Class.forName(XBUD_SPLASH, false, loader);
            Method create = splash.getDeclaredMethod("onCreate", Bundle.class);
            framework.hook(create).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                // Let the app set up its shell/business state, then skip all ad waiting.
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getThisObject();
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    Intent home = new Intent().setClassName(packageName, XBUD_MAIN)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(home);
                    activity.finish();
                    once("splash-redirect", "Aggressive splash: started MainActivity and finished SplashActivity");
                }
                return result;
            });
            HookLog.log(framework, Log.INFO, tag, "Aggressive splash redirect installed after original onCreate");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn("splash redirect", error);
        }
    }

    private void installLaunchGuards() {
        for (Method method : Instrumentation.class.getDeclaredMethods()) {
            if (!method.getName().startsWith("execStartActivit")) continue;
            if (method.getReturnType().isPrimitive() && method.getReturnType() != void.class)
                continue;
            if (Arrays.stream(method.getParameterTypes()).noneMatch(type -> type == Intent.class || type == Intent[].class))
                continue;
            try {
                framework.hook(method).intercept(chain -> {
                    Object[] args = chain.getArgs().toArray();
                    boolean changed = false;
                    for (int i = 0; i < args.length; i++) {
                        if (args[i] instanceof Intent intent && blocked(intent, args)) return null;
                        if (args[i] instanceof Intent[] intents) {
                            ArrayList<Intent> allowed = new ArrayList<>();
                            for (Intent intent : intents)
                                if (!blocked(intent, args)) allowed.add(intent);
                            if (allowed.size() != intents.length) {
                                if (allowed.isEmpty()) return null;
                                args[i] = allowed.toArray(new Intent[0]);
                                changed = true;
                            }
                        }
                    }
                    return changed ? chain.proceed(args) : chain.proceed();
                });
            } catch (RuntimeException | LinkageError error) {
                warn("launch " + method.getName(), error);
            }
        }
        try {
            framework.hook(Instrumentation.class.getDeclaredMethod("newActivity", ClassLoader.class, String.class, Intent.class))
                    .intercept(chain -> {
                        String name = (String) chain.getArgs().get(1);
                        if (AggressiveAdPolicy.adActivity(name)) {
                            once("instantiate:" + name, "Aggressive ad Activity replaced: " + name);
                            return new NoAdActivity();
                        }
                        return chain.proceed();
                    });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn("Activity fallback", error);
        }
    }

    private boolean blocked(Intent intent, Object[] args) {
        if (intent == null) return false;
        ComponentName component = intent.getComponent();
        if (component == null || !packageName.equals(component.getPackageName())) return false;
        String name = component.getClassName();
        if (AggressiveAdPolicy.adActivity(name)) {
            once("launch:" + name, "Aggressive ad Activity launch blocked: " + name);
            return true;
        }
        if (XBUD_MAIN.equals(name)) {
            for (Object arg : args) {
                if (arg instanceof Activity activity && AggressiveAdPolicy.splash(packageName, activity.getClass().getName())
                        && (activity.isFinishing() || activity.isDestroyed())) {
                    once("late-splash", "Aggressive splash: late navigation cancelled");
                    return true;
                }
            }
        }
        return false;
    }

    private void installViewGuards() {
        try {
            framework.hook(View.class.getDeclaredMethod("setVisibility", int.class)).intercept(chain -> {
                View view = (View) chain.getThisObject();
                if (AggressiveAdPolicy.sdkClass(view.getClass().getName())) {
                    return chain.proceed(new Object[]{View.GONE});
                }
                return chain.proceed();
            });
            framework.hook(ViewGroup.class.getDeclaredMethod("addView", View.class, int.class, ViewGroup.LayoutParams.class))
                    .intercept(chain -> {
                        View view = (View) chain.getArgs().get(0);
                        if (view != null && AggressiveAdPolicy.sdkClass(view.getClass().getName())) {
                            view.setVisibility(View.GONE);
                            once("view:" + view.getClass().getName(), "Aggressive ad View hidden: " + view.getClass().getName());
                        }
                        return chain.proceed();
                    });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn("View visibility", error);
        }
        try {
            framework.hook(Dialog.class.getDeclaredMethod("show")).intercept(chain -> {
                if (AggressiveAdPolicy.sdkClass(chain.getThisObject().getClass().getName()) || sdkCaller()) {
                    once("dialog", "Aggressive SDK dialog blocked");
                    return null;
                }
                return chain.proceed();
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn("SDK dialog", error);
        }
    }

    private static boolean sdkCaller() {
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        for (int i = 0; i < Math.min(frames.length, 24); i++) {
            if (AggressiveAdPolicy.sdkClass(frames[i].getClassName())) return true;
        }
        return false;
    }

    private synchronized void once(String key, String event) {
        if (logged.size() < 80 && logged.add(key)) HookLog.log(framework, Log.INFO, tag, event);
    }

    private void warn(String stage, Throwable error) {
        HookLog.log(framework, Log.WARN, tag, "Aggressive " + stage + " unavailable", error);
    }
}
