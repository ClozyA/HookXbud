package xyz.fearr.hookxbud.xposed;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import xyz.fearr.hookcommon.xposed.HookLog;

/**
 * Xbud 2.6.8: original splash skip / verified ordinary-ad close controls, no reward hooks.
 */
final class XbudRules {
    private final XposedInterface framework;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Activity, ButtonTask> tasks = new WeakHashMap<>();
    private final Map<Activity, Integer> clickCounts = new WeakHashMap<>();
    private final XbudUiDiagnostics diagnostics;

    XbudRules(XposedInterface framework) {
        this.framework = framework;
        diagnostics = new XbudUiDiagnostics(framework, main);
    }

    void install(ClassLoader loader) {
        installActivityHooks();
        inventory(loader, XbudTargets.PORTRAIT);
        inventory(loader, XbudTargets.LANDSCAPE);
        installCloseTrace();
        diagnostics.install();
    }

    private void installActivityHooks() {
        List<XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            // The shell replaces Application after attach; observe the base Activity lifecycle.
            handles.add(framework.hook(Activity.class.getDeclaredMethod("onResume")).intercept(chain -> {
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getThisObject();
                diagnostics.onResume(activity);
                XbudTargets.Button target = XbudTargets.autoButton(activity.getClass().getName());
                if (target != null) {
                    try {
                        ButtonTask task = tasks.get(activity);
                        if (task == null) {
                            task = new ButtonTask(activity, target);
                            tasks.put(activity, task);
                            HookLog.log(framework, Log.INFO, "HookXbud", "Xbud " + target.logName()
                                    + ": observed Activity resume; app=" + activity.getApplication().getClass().getName());
                        }
                        if (!task.attempt.isDone()) {
                            task.active = true;
                            main.removeCallbacks(task);
                            main.post(task);
                        }
                    } catch (RuntimeException | LinkageError error) {
                        HookLog.log(framework, Log.WARN, "HookXbud", "Xbud button scheduling unavailable", error);
                    }
                }
                return result;
            }));
            handles.add(framework.hook(Activity.class.getDeclaredMethod("onPause")).intercept(chain -> {
                Object result = chain.proceed();
                diagnostics.onPause((Activity) chain.getThisObject());
                ButtonTask task = tasks.get((Activity) chain.getThisObject());
                if (task != null) {
                    task.active = false;
                    main.removeCallbacks(task);
                }
                return result;
            }));
            handles.add(framework.hook(Activity.class.getDeclaredMethod("onDestroy")).intercept(chain -> {
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getThisObject();
                diagnostics.onDestroy(activity);
                ButtonTask task = tasks.remove(activity);
                if (task != null) {
                    task.attempt.cancel();
                    main.removeCallbacks(task);
                }
                synchronized (clickCounts) {
                    clickCounts.remove(activity);
                }
                return result;
            }));
            HookLog.log(framework, Log.INFO, "HookXbud", "Installed Xbud Activity hooks: splash skip + verified portrait interstitial close");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            for (XposedInterface.HookHandle handle : handles) handle.unhook();
            HookLog.log(framework, Log.WARN, "HookXbud", "Xbud Activity hooks unavailable; original UI retained", error);
        }
    }

    private final class ButtonTask implements Runnable {
        private final WeakReference<Activity> owner;
        private final XbudTargets.Button target;
        private final SkipAttempt attempt;
        private boolean active;
        private final Set<Integer> observed = new HashSet<>();
        private final Set<Integer> mismatched = new HashSet<>();

        ButtonTask(Activity activity, XbudTargets.Button target) {
            owner = new WeakReference<>(activity);
            this.target = target;
            attempt = new SkipAttempt(target.maxChecks());
        }

        @Override
        public void run() {
            Activity activity = owner.get();
            if (activity == null) {
                attempt.cancel();
                return;
            }
            try {
                View ready = null;
                XbudTargets.Button readyTarget = null;
                for (XbudTargets.Button candidate : XbudTargets.autoButtons(activity.getClass().getName())) {
                    View view = activity.findViewById(candidate.id());
                    if (view == null) continue;
                    if (!candidate.matches(view.getId(),
                            view.getResources().getResourceEntryName(view.getId()), view.getClass().getName())) {
                        if (mismatched.add(candidate.id())) HookLog.log(framework, Log.WARN, "HookXbud",
                                "Xbud " + target.logName() + ": control differs; ignoring " + candidate.resourceName());
                        continue;
                    }
                    if (observed.add(candidate.id())) HookLog.log(framework, Log.INFO, "HookXbud",
                            "Xbud " + target.logName() + ": found " + candidate.resourceName()
                                    + "; shown=" + view.isShown() + ", enabled=" + view.isEnabled()
                                    + ", listener=" + view.hasOnClickListeners());
                    if (view.isShown() && view.isEnabled() && view.hasOnClickListeners()) {
                        ready = view;
                        readyTarget = candidate;
                        break;
                    }
                }
                final View skip = ready;
                final XbudTargets.Button chosen = readyTarget;
                boolean again = attempt.tick(new SkipAttempt.Host() {
                    @Override
                    public boolean isActive() {
                        return active && !activity.isFinishing() && !activity.isDestroyed();
                    }

                    @Override
                    public boolean canSkip() {
                        return skip != null && skip.isShown() && skip.isEnabled()
                                && skip.hasOnClickListeners();
                    }

                    @Override
                    public void clickSkip() {
                        HookLog.log(framework, Log.INFO, "HookXbud", "Xbud " + target.logName()
                                + ": activating original " + chosen.resourceName() + " listener once");
                        boolean handled = skip.performClick();
                        HookLog.log(framework, Log.INFO, "HookXbud", "Xbud " + target.logName()
                                + ": original click returned " + handled);
                    }
                });
                if (again) main.postDelayed(this, 250L);
                else if (active && !activity.isFinishing() && !activity.isDestroyed()) {
                    HookLog.log(framework, Log.INFO, "HookXbud", "Xbud " + target.logName()
                            + ": checks ended; original navigation retained");
                }
            } catch (RuntimeException | LinkageError error) {
                attempt.cancel();
                HookLog.log(framework, Log.WARN, "HookXbud", "Xbud " + target.logName()
                        + " automatic click unavailable; original UI retained", error);
            }
        }
    }

    private void installCloseTrace() {
        try {
            framework.hook(Activity.class.getDeclaredMethod("finish")).intercept(chain -> {
                Activity activity = (Activity) chain.getThisObject();
                if (isInterstitial(activity))
                    trace("Xbud interstitial finish: " + activity.getClass().getName());
                return chain.proceed();
            });
            framework.hook(View.class.getDeclaredMethod("performClick")).intercept(chain -> {
                View view = (View) chain.getThisObject();
                Activity activity = activityOf(view.getContext());
                if (isInterstitial(activity)) {
                    int count;
                    synchronized (clickCounts) {
                        count = clickCounts.getOrDefault(activity, 0);
                        clickCounts.put(activity, Math.min(count + 1, 12));
                    }
                    if (count < 12) {
                        HookLog.log(framework, Log.INFO, "HookXbud", "Xbud interstitial click: activity="
                                + activity.getClass().getName() + ", type=" + view.getClass().getName()
                                + ", id=0x" + Integer.toHexString(view.getId()));
                    }
                }
                return chain.proceed();
            });
            HookLog.log(framework, Log.INFO, "HookXbud", "Installed Xbud ordinary interstitial close tracing; reward Activities excluded");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            HookLog.log(framework, Log.WARN, "HookXbud", "Xbud close tracing unavailable", error);
        }
    }

    private void trace(String event) {
        HookLog.log(framework, Log.INFO, "HookXbud", event);
        StackTraceElement[] frames = Thread.currentThread().getStackTrace();
        int count = 0;
        for (StackTraceElement frame : frames) {
            String name = frame.getClassName();
            if (name.startsWith("java.lang.Thread") || name.startsWith("xyz.fearr.hookxbud.")
                    || name.startsWith("xyz.fearr.hookcommon.")
                    || name.startsWith("io.github.libxposed.") || name.startsWith("org.lsposed."))
                continue;
            HookLog.log(framework, Log.INFO, "HookXbud", "Xbud close frame: " + frame);
            if (++count == 16) break;
        }
    }

    private void inventory(ClassLoader loader, String name) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            for (int depth = 0; depth < 2 && type != null && !type.getName().startsWith("android."); depth++) {
                Method[] methods = type.getDeclaredMethods();
                Arrays.sort(methods, Comparator.comparing(Method::toString));
                for (int i = 0; i < Math.min(methods.length, 60); i++) {
                    HookLog.log(framework, Log.INFO, "HookXbud", "Xbud interstitial method: " + methods[i]);
                }
                Field[] fields = type.getDeclaredFields();
                Arrays.sort(fields, Comparator.comparing(Field::getName));
                for (int i = 0; i < Math.min(fields.length, 30); i++) {
                    HookLog.log(framework, Log.INFO, "HookXbud", "Xbud interstitial field: " + type.getName()
                            + "." + fields[i].getName() + " : " + fields[i].getType().getName());
                }
                type = type.getSuperclass();
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            HookLog.log(framework, Log.WARN, "HookXbud", "Xbud interstitial inventory unavailable: " + name, error);
        }
    }

    private static boolean isInterstitial(Activity activity) {
        return activity != null && XbudTargets.traceClose(activity.getClass().getName());
    }

    private static Activity activityOf(Context context) {
        for (int depth = 0; depth < 10 && context != null; depth++) {
            if (context instanceof Activity) return (Activity) context;
            if (!(context instanceof ContextWrapper)) return null;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) return null;
            context = next;
        }
        return null;
    }
}
