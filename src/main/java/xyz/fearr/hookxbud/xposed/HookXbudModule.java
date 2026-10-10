package xyz.fearr.hookxbud.xposed;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

import xyz.fearr.hookcommon.xposed.AggressiveAdRules;
import xyz.fearr.hookcommon.xposed.HookLog;
import xyz.fearr.hookcommon.xposed.TargetHookModule;

public final class HookXbudModule extends TargetHookModule {
    private static final String PACKAGE_NAME = "run.xbud.android";

    @Override
    protected String targetPackage() {
        return PACKAGE_NAME;
    }

    @Override
    protected String moduleTag() {
        return "HookXbud";
    }

    @Override
    protected String[] classLoaderProbeClasses() {
        return new String[]{
                "run.xbud.android.mvp.ui.other.SplashActivity",
                "run.xbud.android.mvp.ui.other.MainActivity"};
    }

    @Override
    protected void inspectTargetBeforeRules(ClassLoader loader) {
        String[] classes = {
                "run.xbud.android.mvp.ui.other.SplashActivity",
                "run.xbud.android.mvp.ui.sport.run.RunStartActivity",
                "run.xbud.android.mvp.ui.other.XBDFlutterActivity"};
        for (String name : classes) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                Method[] methods = type.getDeclaredMethods();
                Arrays.sort(methods, Comparator.comparing(Method::toString));
                for (int i = 0; i < Math.min(methods.length, 80); i++) {
                    HookLog.log(this, Log.INFO, moduleTag(), "Xbud method: " + methods[i]);
                }
                Field[] fields = type.getDeclaredFields();
                Arrays.sort(fields, Comparator.comparing(Field::getName));
                for (int i = 0; i < Math.min(fields.length, 40); i++) {
                    HookLog.log(this, Log.INFO, moduleTag(), "Xbud field: " + name + "." + fields[i].getName()
                            + " : " + fields[i].getType().getName());
                }
                HookLog.log(this, Log.INFO, moduleTag(), "Xbud inventory: " + name + ", methods=" + methods.length
                        + ", fields=" + fields.length + ", limits=80/40; values not collected");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                HookLog.log(this, Log.WARN, moduleTag(), "Xbud inventory unavailable: " + name, error);
            }
        }
    }

    @Override
    protected void installTargetRules(ClassLoader loader) {
        new XbudRules(this).install(loader);
        new AggressiveAdRules(this, PACKAGE_NAME).install(loader);
    }
}
