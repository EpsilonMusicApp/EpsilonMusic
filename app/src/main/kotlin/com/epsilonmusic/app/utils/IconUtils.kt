

package com.epsilonmusic.app.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconUtils {
    /** Launcher uses the current default icon (no legacy variant active). */
    const val LEGACY_OFF = -1
    /** The original legacy icon design (MainActivityLegacy). */
    const val LEGACY_CLASSIC = 0
    /** Additional legacy icon designs (MainActivityLegacy1..3). */
    const val LEGACY_VARIANT_1 = 1
    const val LEGACY_VARIANT_2 = 2
    const val LEGACY_VARIANT_3 = 3

    private val legacyAliases = mapOf(
        LEGACY_CLASSIC to "com.epsilonmusic.app.MainActivityLegacy",
        LEGACY_VARIANT_1 to "com.epsilonmusic.app.MainActivityLegacy1",
        LEGACY_VARIANT_2 to "com.epsilonmusic.app.MainActivityLegacy2",
        LEGACY_VARIANT_3 to "com.epsilonmusic.app.MainActivityLegacy3",
    )

    /**
     * Enable exactly one launcher entry point:
     * - [legacyVariant] >= 0 enables the matching legacy icon alias and disables
     *   the default/static ones.
     * - [legacyVariant] == [LEGACY_OFF] restores the default launcher alias
     *   ([isDynamic] selects between the dynamic and static default aliases).
     *
     * Component enabled states persist in PackageManager, so this only needs to
     * be called when the selection changes (and once at startup to reconcile).
     */
    fun setIcon(context: Context, isDynamic: Boolean, legacyVariant: Int) {
        val pm = context.packageManager
        val dynamic = ComponentName(context, "com.epsilonmusic.app.MainActivityAlias")
        val static = ComponentName(context, "com.epsilonmusic.app.MainActivityStatic")

        pm.setComponentEnabledSetting(
            dynamic,
            if (isDynamic && legacyVariant == LEGACY_OFF) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            static,
            if (!isDynamic && legacyVariant == LEGACY_OFF) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        for ((variant, alias) in legacyAliases) {
            pm.setComponentEnabledSetting(
                ComponentName(context, alias),
                if (legacyVariant == variant) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    /** Backwards-compatible boolean overload: legacy on/off with the classic design. */
    @Deprecated("Use setIcon(context, isDynamic, legacyVariant) instead", ReplaceWith(
        "setIcon(context, isDynamic, if (isLegacy) LEGACY_CLASSIC else LEGACY_OFF)"
    ))
    fun setIcon(context: Context, isDynamic: Boolean, isLegacy: Boolean) =
        setIcon(context, isDynamic, if (isLegacy) LEGACY_CLASSIC else LEGACY_OFF)
}
