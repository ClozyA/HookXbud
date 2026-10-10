package xyz.fearr.hookcommon.xposed;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/**
 * Fail closed for all void load operations from the SDK's actual TTAdNative contract.
 */
final class CsjRequestBlocker {
    private static final String API = "com.bytedance.sdk.openadsdk.";
    private final XposedInterface framework;
    private final String tag;
    private final Set<Method> hooked = new HashSet<>();
    private final Set<Method> logged = new HashSet<>();

    CsjRequestBlocker(XposedInterface framework, String tag) {
        this.framework = framework;
        this.tag = tag;
    }

    void install(ClassLoader loader) {
        // Exact implementation from five fresh 0.6.0 phone processes, before requests can start.
        try {
            installLoads(Class.forName("com.byazt.io.c$c", false, loader));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn("direct implementation", error);
        }
        try {
            Class<?> sdk = Class.forName(API + "TTAdSdk", false, loader);
            Method getter = sdk.getMethod("getAdManager");
            if (!Modifier.isStatic(getter.getModifiers()))
                throw new NoSuchMethodException("Non-static manager getter");
            framework.hook(getter).intercept(chain -> {
                Object manager = chain.proceed();
                if (manager != null) {
                    try {
                        installFactory(manager.getClass());
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                        warn("factory", error);
                    }
                }
                return manager;
            });
            HookLog.log(framework, Log.INFO, tag, "Aggressive CSJ manager watch installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn("entry", error);
        }
    }

    private synchronized void installFactory(Class<?> type) throws ReflectiveOperationException {
        Method factory = type.getMethod("createAdNative", Context.class);
        if (hooked.contains(factory)) return;
        if (Modifier.isAbstract(factory.getModifiers()))
            throw new NoSuchMethodException("Abstract factory");
        framework.hook(factory).intercept(chain -> {
            Object adLoader = chain.proceed();
            if (adLoader != null) {
                try {
                    installLoads(adLoader.getClass());
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    warn("loader", error);
                }
            }
            return adLoader;
        });
        hooked.add(factory);
    }

    synchronized void installLoads(Class<?> implementation) throws ReflectiveOperationException {
        Class<?> contract = Class.forName(API + "TTAdNative", false, implementation.getClassLoader());
        if (!contract.isAssignableFrom(implementation))
            throw new IllegalArgumentException("Not a TTAdNative implementation");
        int count = 0;
        for (Method operation : contract.getMethods()) {
            if (!operation.getName().startsWith("load") || operation.getReturnType() != void.class)
                continue;
            Method request = implementation.getMethod(operation.getName(), operation.getParameterTypes());
            if (hooked.contains(request)) continue;
            if (Modifier.isAbstract(request.getModifiers()) || Modifier.isStatic(request.getModifiers()))
                continue;
            framework.hook(request).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                synchronized (logged) {
                    if (logged.add(request)) HookLog.log(framework, Log.INFO, tag,
                            "Aggressive CSJ request blocked: " + request.getName() + "; no callback, no reward");
                }
                return null;
            });
            hooked.add(request);
            count++;
        }
        HookLog.log(framework, Log.INFO, tag, "Aggressive CSJ load hooks: type=" + implementation.getName() + ", added=" + count);
    }

    private void warn(String stage, Throwable error) {
        HookLog.log(framework, Log.WARN, tag, "Aggressive CSJ " + stage + " unavailable; display guards remain active", error);
    }
}
