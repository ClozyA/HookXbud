package xyz.fearr.hookcommon.xposed;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import io.github.libxposed.api.XposedModule;
import xyz.fearr.hookcommon.TargetCatalog;

/** Shared process and packed-class-loader lifecycle for a single-target Hook module. */
public abstract class TargetHookModule extends XposedModule {
    private final Set<ClassLoader> inspectedLoaders =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ClassLoader> configuredLoaders =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private String processName;

    protected abstract String targetPackage();

    protected abstract String moduleTag();

    protected abstract String[] classLoaderProbeClasses();

    protected abstract void installTargetRules(ClassLoader loader);

    protected void inspectTargetBeforeRules(ClassLoader loader) {
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        HookLog.log(this, Log.INFO, moduleTag(), "Module loaded: process=" + processName + ", API=" + getApiVersion());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!targetPackage().equals(packageName) || !packageName.equals(processName)) return;
        HookLog.log(this, Log.INFO, moduleTag(), "Package ready: " + packageName);
        inspectLoader("package-ready", param.getClassLoader());

        String applicationName = param.getApplicationInfo().className;
        if (applicationName == null) return;
        try {
            Class<?> applicationClass = Class.forName(applicationName, false, param.getClassLoader());
            Method attach = applicationClass.getDeclaredMethod("attachBaseContext", Context.class);
            hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Context context = (Context) chain.getArgs().get(0);
                    inspectLoader("after-attach-context", context.getClassLoader());
                    configure(packageName, context, context.getClassLoader());
                    Context application = (Context) chain.getThisObject();
                    inspectLoader("after-attach-application", application.getClassLoader());
                    configure(packageName, context, application.getClassLoader());
                } catch (RuntimeException | LinkageError error) {
                    HookLog.log(this, Log.WARN, moduleTag(), "Loader inspection failed: " + packageName, error);
                }
                return result;
            });
            HookLog.log(this, Log.INFO, moduleTag(), "Watching " + applicationName + ".attachBaseContext");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            HookLog.log(this, Log.WARN, moduleTag(), "Cannot watch packed Application: " + applicationName, error);
        }
    }

    private void configure(String packageName, Context context, ClassLoader loader) {
        if (loader == null) return;
        synchronized (configuredLoaders) {
            if (!configuredLoaders.add(loader)) return;
        }
        try {
            try {
                PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
                long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
                HookLog.log(this, Log.INFO, moduleTag(),
                        "Target version: " + packageName + " " + info.versionName + " (" + code + ")");
                TargetCatalog.Target target = TargetCatalog.find(packageName);
                if (target != null && !target.matches(info.versionName, code)) {
                    HookLog.log(this, Log.WARN, moduleTag(),
                            "Version differs from inspected APK; continuing best-effort rules: " + packageName
                                    + ", inspected=" + target.versionName() + " (" + target.versionCode() + ")");
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException error) {
                HookLog.log(this, Log.WARN, moduleTag(),
                        "Target version unavailable; continuing best-effort rules: " + packageName, error);
            }

            inspectTargetBeforeRules(loader);
            installTargetRules(loader);
        } catch (RuntimeException | LinkageError error) {
            HookLog.log(this, Log.WARN, moduleTag(), "Cannot configure target: " + packageName, error);
        }
    }

    private void inspectLoader(String stage, ClassLoader loader) {
        if (loader == null) return;
        // A shell can mutate an existing loader during attachBaseContext; re-probe after attach.
        if (!"package-ready".equals(stage)) {
            synchronized (inspectedLoaders) {
                if (!inspectedLoaders.add(loader)) return;
            }
        }
        for (String name : classLoaderProbeClasses()) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                HookLog.log(this, Log.INFO, moduleTag(), stage + ": found " + name + ", loader="
                        + type.getClassLoader().getClass().getName());
            } catch (ClassNotFoundException error) {
                HookLog.log(this, Log.INFO, moduleTag(), stage + ": not yet available " + name);
            } catch (RuntimeException | LinkageError error) {
                HookLog.log(this, Log.WARN, moduleTag(), stage + ": cannot resolve " + name, error);
            }
        }
    }
}
