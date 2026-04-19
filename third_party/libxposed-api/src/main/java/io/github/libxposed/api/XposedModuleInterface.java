package io.github.libxposed.api;

import android.app.AppComponentFactory;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Interface for module initialization.
 */
@SuppressWarnings("unused")
public interface XposedModuleInterface {
    /**
     * Wraps information about the process in which the module is loaded.
     */
    interface ModuleLoadedParam {
        /**
         * Gets information about whether the module is running in system server.
         *
         * @return {@code true} if the module is running in system server
         */
        boolean isSystemServer();

        /**
         * Gets the process name.
         *
         * @return The process name
         */
        @NonNull
        String getProcessName();
    }

    /**
     * Wraps information about system server. API 100 flavor.
     */
    interface SystemServerLoadedParam {
        /**
         * Gets the class loader of system server.
         *
         * @return The class loader
         */
        @NonNull
        ClassLoader getClassLoader();
    }

    /**
     * Wraps information about system server. API 101 flavor.
     */
    interface SystemServerStartingParam {
        @NonNull
        ClassLoader getClassLoader();
    }

    /**
     * Wraps information about a package whose classloader is ready. API 101.
     */
    interface PackageReadyParam extends PackageLoadedParam {
        @NonNull
        ClassLoader getClassLoader();

        @RequiresApi(Build.VERSION_CODES.P)
        @NonNull
        AppComponentFactory getAppComponentFactory();
    }

    /**
     * Wraps information about the package being loaded.
     */
    interface PackageLoadedParam {
        /**
         * Gets the package name of the package being loaded.
         *
         * @return The package name.
         */
        @NonNull
        String getPackageName();

        /**
         * Gets the {@link ApplicationInfo} of the package being loaded.
         *
         * @return The ApplicationInfo.
         */
        @NonNull
        ApplicationInfo getApplicationInfo();

        /**
         * Gets default class loader.
         *
         * @return the default class loader
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        @NonNull
        ClassLoader getDefaultClassLoader();

        /**
         * Gets the class loader of the package being loaded.
         *
         * @return The class loader.
         */
        @NonNull
        ClassLoader getClassLoader();

        /**
         * Gets information about whether is this package the first and main package of the app process.
         *
         * @return {@code true} if this is the first package.
         */
        boolean isFirstPackage();
    }

    /**
     * Gets notified when a package is loaded into the app process.<br/>
     * This callback could be invoked multiple times for the same process on each package.
     *
     * @param param Information about the package being loaded
     */
    default void onPackageLoaded(@NonNull PackageLoadedParam param) {
    }

    /**
     * Gets notified when the system server is loaded. API 100.
     *
     * @param param Information about system server
     */
    default void onSystemServerLoaded(@NonNull SystemServerLoadedParam param) {
    }

    /**
     * API 101: invoked once per process after the module instance is attached.
     */
    default void onModuleLoaded(@NonNull ModuleLoadedParam param) {
    }

    /**
     * API 101: invoked when a package's classloader is ready.
     */
    default void onPackageReady(@NonNull PackageReadyParam param) {
    }

    /**
     * API 101: replaces {@link #onSystemServerLoaded(SystemServerLoadedParam)}.
     */
    default void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
    }
}
