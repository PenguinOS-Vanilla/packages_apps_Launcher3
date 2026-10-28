/*
 * SPDX-FileCopyrightText: The PenguinOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.quickstep

import android.app.ActivityOptions
import android.content.pm.LauncherApps
import android.view.Display
import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.ActivityContext
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus

/**
 * Opens an app from its home screen or app drawer menu in a desktop window, through the same
 * shell launch the taskbar uses in desktop mode.
 */
class LaunchInDesktopShortcut(target: ActivityContext, itemInfo: ItemInfo, originalView: View) :
    SystemShortcut<ActivityContext>(
        R.drawable.ic_desktop,
        R.string.recent_task_option_desktop,
        target,
        itemInfo,
        originalView,
    ) {

    override fun onClick(view: View) {
        val context = mTarget.asContext()
        val component = mItemInfo.targetComponent ?: return
        val pendingIntent =
            context
                .getSystemService(LauncherApps::class.java)
                ?.getMainActivityLaunchIntent(component, null, mItemInfo.user) ?: return
        AbstractFloatingView.closeAllOpenViews(mTarget)
        SystemUiProxy.INSTANCE[context].startLaunchIntentTransition(
            pendingIntent,
            ActivityOptions.makeBasic().toBundle(),
            view.display?.displayId ?: Display.DEFAULT_DISPLAY,
        )
    }

    companion object {
        /** Only offered where the launcher's display can host desktops, for resizeable apps. */
        @JvmField
        val FACTORY =
            SystemShortcut.Factory<ActivityContext> { target, itemInfo, originalView ->
                val context = target.asContext()
                when {
                    !DesktopModeStatus.isDesktopModeSupportedOnDisplay(
                        context,
                        originalView.display ?: context.display,
                    ) -> null
                    itemInfo.itemType != ITEM_TYPE_APPLICATION -> null
                    !Utilities.isResizeableActivity(context, itemInfo.targetComponent) -> null
                    else -> LaunchInDesktopShortcut(target, itemInfo, originalView)
                }
            }
    }
}
