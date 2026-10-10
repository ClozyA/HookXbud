package xyz.fearr.hookcommon.xposed;

import com.bytedance.sdk.openadsdk.TTAdNative;
import io.github.libxposed.api.XposedInterface;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public final class CsjRequestBlockerTest {
    public static final class Loader implements TTAdNative {
        int requests;
        @Override public void loadSplashAd(Object slot, Object listener, int timeout) { requests++; }
        @Override public void loadRewardVideoAd(Object slot, Object listener) { requests++; }
        @Override public void loadFeedAd(Object slot, Object listener) { requests++; }
        @Override public String getSdkState() { return "ready"; }
        @Override public String loadToken() { return "token"; }
    }

    @Test public void splashIsBlockedEvenWithNoListenerOrErrorConstructor() throws Throwable {
        Harness harness = new Harness();
        new CsjRequestBlocker(harness.framework(), "HookFivePlay").installLoads(Loader.class);
        Loader loader = new Loader();
        Method method = Loader.class.getMethod("loadSplashAd", Object.class, Object.class, int.class);
        harness.call(method, loader, null, null, 3500);
        harness.call(method, loader, null, new Object(), 3500);
        assertEquals(0, loader.requests);
        assertEquals(XposedInterface.ExceptionMode.PASSTHROUGH, harness.modes.get(method));
    }

    @Test public void rewardAndFeedLoadsAreBlockedForEveryLoaderInstance() throws Throwable {
        Harness harness = new Harness();
        new CsjRequestBlocker(harness.framework(), "HookFivePlay").installLoads(Loader.class);
        Loader first = new Loader();
        Loader second = new Loader();
        harness.call(Loader.class.getMethod("loadRewardVideoAd", Object.class, Object.class), first, null, null);
        harness.call(Loader.class.getMethod("loadFeedAd", Object.class, Object.class), second, null, null);
        assertEquals(0, first.requests + second.requests);
    }

    @Test public void repeatInstallationDoesNotAddHooksAndNonVoidOperationsRemainOriginal() throws Throwable {
        Harness harness = new Harness();
        CsjRequestBlocker blocker = new CsjRequestBlocker(harness.framework(), "HookFivePlay");
        blocker.installLoads(Loader.class);
        blocker.installLoads(Loader.class);
        assertEquals(3, harness.registrations);
        assertEquals("ready", harness.call(Loader.class.getMethod("getSdkState"), new Loader()));
        assertEquals("token", harness.call(Loader.class.getMethod("loadToken"), new Loader()));
    }

    private static final class Harness {
        final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();
        final Map<Method, XposedInterface.ExceptionMode> modes = new HashMap<>();
        int registrations;

        XposedInterface framework() {
            return proxy(XposedInterface.class, (instance, method, args) -> switch (method.getName()) {
                case "log" -> null;
                case "hook" -> {
                    Method origin = (Method) args[0];
                    yield proxy(XposedInterface.HookBuilder.class, (builder, operation, values) -> switch (operation.getName()) {
                        case "setExceptionMode" -> { modes.put(origin, (XposedInterface.ExceptionMode) values[0]); yield builder; }
                        case "intercept" -> {
                            hooks.put(origin, (XposedInterface.Hooker) values[0]);
                            registrations++;
                            yield proxy(XposedInterface.HookHandle.class, (handle, action, unused) -> null);
                        }
                        default -> throw new UnsupportedOperationException(operation.toString());
                    });
                }
                default -> throw new UnsupportedOperationException(method.toString());
            });
        }

        Object call(Method method, Object target, Object... args) throws Throwable {
            XposedInterface.Hooker hook = hooks.get(method);
            if (hook == null) {
                try { return method.invoke(target, args); }
                catch (InvocationTargetException error) { throw error.getCause(); }
            }
            // Any proceed() call would execute a real request and is a regression here.
            return hook.intercept(proxy(XposedInterface.Chain.class, (instance, operation, values) -> {
                throw new AssertionError("Blocked hook must not access/proceed SDK request: " + operation);
            }));
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        }
    }
}
