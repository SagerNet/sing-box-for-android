package io.github.libxposed.api;

import androidx.annotation.NonNull;

/**
 * Super class which all Xposed module entry classes should extend.<br/>
 * Entry classes will be instantiated exactly once for each process.
 */
@SuppressWarnings("unused")
public abstract class XposedModule extends XposedInterfaceWrapper implements XposedModuleInterface {
    /**
     * No-arg constructor for API 101 contract: the framework instantiates the module via
     * {@code Class.getDeclaredConstructor()}, then calls {@link #attachFramework}.
     */
    public XposedModule() {
        super();
    }

    /**
     * Two-arg constructor for API 100 contract: the framework instantiates the module via
     * {@code (XposedInterface, ModuleLoadedParam)} and attaches the framework base inline.
     */
    public XposedModule(@NonNull XposedInterface base, @NonNull ModuleLoadedParam param) {
        super(base);
    }
}
