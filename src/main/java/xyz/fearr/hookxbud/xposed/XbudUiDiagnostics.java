package xyz.fearr.hookxbud.xposed;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;
import xyz.fearr.hookcommon.xposed.HookLog;

/**
 * Bounded structural evidence only: no text, extras, field values or UI actions.
 */
final class XbudUiDiagnostics {
    private final XposedInterface framework;
    private final Handler main;
    private final Map<Activity, SnapshotTask> tasks = new WeakHashMap<>();
    private final Map<Activity, Integer> dialogs = new WeakHashMap<>();
    private final Map<Activity, Integer> clicks = new WeakHashMap<>();

    XbudUiDiagnostics(XposedInterface framework, Handler main) {
        this.framework = framework;
        this.main = main;
    }

    void install() {
        try {
            framework.hook(Dialog.class.getDeclaredMethod("show")).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Dialog dialog = (Dialog) chain.getThisObject();
                    Activity owner = dialog.getOwnerActivity();
                    if (owner == null) owner = activityOf(dialog.getContext());
                    if (eligible(owner)) {
                        int count = dialogs.getOrDefault(owner, 0);
                        if (count < 3) {
                            dialogs.put(owner, count + 1);
                            WeakReference<Dialog> reference = new WeakReference<>(dialog);
                            WeakReference<Activity> activity = new WeakReference<>(owner);
                            main.postDelayed(() -> {
                                Dialog current = reference.get();
                                Activity host = activity.get();
                                SnapshotTask task = tasks.get(host);
                                if (eligible(host) && task != null && task.active
                                        && !host.isFinishing() && !host.isDestroyed()
                                        && current != null && current.isShowing() && current.getWindow() != null) {
                                    snapshot(host, current.getWindow().getDecorView(), "dialog-" + (count + 1));
                                }
                            }, 500L);
                        }
                    }
                } catch (RuntimeException | LinkageError error) {
                    warn(error);
                }
                return result;
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
        }
        try {
            framework.hook(View.class.getDeclaredMethod("performClick")).intercept(chain -> {
                try {
                    View view = (View) chain.getThisObject();
                    Activity activity = activityOf(view.getContext());
                    if (eligible(activity)) {
                        synchronized (clicks) {
                            int count = clicks.getOrDefault(activity, 0);
                            if (count < 12) {
                                clicks.put(activity, count + 1);
                                HookLog.log(framework, Log.INFO, "HookXbud", "Xbud UI click: activity="
                                        + activity.getClass().getName() + ", " + describe(view));
                            }
                        }
                    }
                } catch (RuntimeException | LinkageError error) {
                    warn(error);
                }
                return chain.proceed();
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
        }
    }

    void onResume(Activity activity) {
        if (!eligible(activity)) return;
        try {
            SnapshotTask task = tasks.get(activity);
            if (task == null) {
                task = new SnapshotTask(activity);
                tasks.put(activity, task);
            }
            task.active = true;
            main.removeCallbacks(task);
            if (task.index < 4) main.post(task);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
        }
    }

    void onPause(Activity activity) {
        SnapshotTask task = tasks.get(activity);
        if (task != null) {
            task.active = false;
            main.removeCallbacks(task);
        }
    }

    void onDestroy(Activity activity) {
        onPause(activity);
        tasks.remove(activity);
        dialogs.remove(activity);
        synchronized (clicks) {
            clicks.remove(activity);
        }
    }

    private final class SnapshotTask implements Runnable {
        private final WeakReference<Activity> owner;
        private int index;
        private boolean active;

        SnapshotTask(Activity activity) {
            owner = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            Activity activity = owner.get();
            if (!active || activity == null || activity.isFinishing() || activity.isDestroyed())
                return;
            try {
                if (activity.getWindow() != null) {
                    snapshot(activity, activity.getWindow().getDecorView(), "resume-" + index);
                }
                index++;
                if (index < 4)
                    main.postDelayed(this, index == 1 ? 1000L : index == 2 ? 3000L : 4000L);
            } catch (RuntimeException | LinkageError error) {
                index = 4;
                warn(error);
            }
        }
    }

    private void snapshot(Activity activity, View root, String reason) {
        try {
            HookLog.log(framework, Log.INFO, "HookXbud", "Xbud UI snapshot: activity="
                    + activity.getClass().getName() + ", reason=" + reason + ", maxNodes=100");
            int[] remaining = {100};
            walk(root, "0", 0, remaining);
            HookLog.log(framework, Log.INFO, "HookXbud", "Xbud UI snapshot end: nodes="
                    + (100 - remaining[0]) + ", budgetReached=" + (remaining[0] == 0));
        } catch (RuntimeException | LinkageError error) {
            warn(error);
        }
    }

    private void walk(View view, String path, int depth, int[] remaining) {
        if (view == null || remaining[0] == 0 || depth > 12) return;
        remaining[0]--;
        HookLog.log(framework, Log.INFO, "HookXbud", "Xbud UI node: path=" + path + ", " + describe(view));
        if (view instanceof ViewGroup group) {
            // Prefer later children, where SDK overlays are commonly attached, under the node cap.
            for (int i = group.getChildCount() - 1; i >= 0 && remaining[0] > 0; i--) {
                walk(group.getChildAt(i), path + "/" + i, depth + 1, remaining);
            }
        }
    }

    private static String describe(View view) {
        String name = "-";
        if (view.getId() != View.NO_ID) {
            try {
                name = view.getResources().getResourceEntryName(view.getId());
            } catch (Resources.NotFoundException ignored) {
                name = "unresolved";
            }
        }
        return "type=" + view.getClass().getName() + ", id=0x" + Integer.toHexString(view.getId())
                + ", resource=" + name + ", visibility=" + view.getVisibility()
                + ", shown=" + view.isShown() + ", enabled=" + view.isEnabled()
                + ", clickable=" + view.isClickable() + ", listener=" + view.hasOnClickListeners()
                + ", size=" + view.getWidth() + "x" + view.getHeight();
    }

    private static boolean eligible(Activity activity) {
        return activity != null && XbudTargets.inspectUi(activity.getClass().getName());
    }

    private static Activity activityOf(Context context) {
        for (int depth = 0; depth < 10 && context != null; depth++) {
            if (context instanceof Activity activity) return activity;
            if (!(context instanceof ContextWrapper wrapper)) return null;
            Context next = wrapper.getBaseContext();
            if (next == context) return null;
            context = next;
        }
        return null;
    }

    private void warn(Throwable error) {
        HookLog.log(framework, Log.WARN, "HookXbud", "Xbud UI diagnostics unavailable; original UI retained", error);
    }
}
