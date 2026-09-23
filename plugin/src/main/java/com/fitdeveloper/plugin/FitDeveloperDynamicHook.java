package com.fitdeveloper.plugin;

import com.intellij.ide.plugins.CannotUnloadPluginException;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Plugin load/unload safety net — the official mechanism for plugins that
 * hold GLOBAL state (the JetBrains docs' "Plugin Load/Unload Events" pattern).
 *
 * The coding lock patches the IDE-wide TypedAction pipeline. The platform
 * does not roll that back on hot-unload, so this listener guarantees a clean
 * teardown should a dynamic unload ever occur (it should not: the plugin
 * declares require-restart="true" — this is the second line of defense).
 *
 *  - {@link #checkUnloadPlugin}: vetoes the unload while a walk-to-unlock
 *    break is actually live, so a running break can never be torn down midway.
 *  - {@link #beforePluginUnload}: restores the original typing handler while
 *    the plugin's classes are still alive — the IDE keeps typing normally
 *    even if the plugin is unloaded without a restart.
 *
 * Registered in plugin.xml via <applicationListeners> on the
 * com.intellij.ide.plugins.DynamicPluginListener message-bus topic.
 * (Signatures verified against the installed IDEs: the listener methods take
 * IdeaPluginDescriptor + an isUpdate boolean, all default methods.)
 */
public final class FitDeveloperDynamicHook implements DynamicPluginListener {

    @Override
    public void checkUnloadPlugin(@NotNull IdeaPluginDescriptor descriptor) throws CannotUnloadPluginException {
        if (FitDeveloperSettings.isEnabled() && FitDeveloperEngine.breakActive()) {
            throw new CannotUnloadPluginException(
                    "FitDeveloper is holding a walk-to-unlock break — finish or abandon it first");
        }
    }

    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor descriptor, boolean isUpdate) {
        try {
            CodingLock.uninstall();
        } catch (Throwable t) {
            System.out.println("[FitDeveloper] unload cleanup failed: " + t);
        }
    }
}
