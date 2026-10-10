package xyz.fearr.hookcommon.xposed;

import io.github.libxposed.api.XposedInterface;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class HookLogTest {
    @Test
    public void forwardsFrameworkLogArguments() {
        List<Object[]> calls = new ArrayList<>();
        XposedInterface framework = (XposedInterface) Proxy.newProxyInstance(
                XposedInterface.class.getClassLoader(), new Class<?>[]{XposedInterface.class},
                (proxy, method, args) -> { if (method.getName().equals("log")) calls.add(args); return null; });
        IllegalStateException error = new IllegalStateException("probe");
        HookLog.log(framework, 4, "HookXbud", "event");
        HookLog.log(framework, 5, "HookFivePlay", "failure", error);
        assertArrayEquals(new Object[]{4, "HookXbud", "event"}, calls.get(0));
        assertArrayEquals(new Object[]{5, "HookFivePlay", "failure", error}, calls.get(1));
    }
}
