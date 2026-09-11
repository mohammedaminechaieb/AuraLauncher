package com.auralauncher.app.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Same technique HaptiKit and StatusBar+ use — there's no other way to
 * know "does this app currently have a notification" without a
 * NotificationListenerService. Powers the small dot badge drawn on top
 * of an icon (home grid, dock, and drawer) when its package has an
 * active notification. Requires the user to grant Notification Access
 * (deep-linked from Settings, same pattern as those other apps).
 */
class AuraNotificationListenerService : NotificationListenerService() {

    companion object {
        private val _activePackages = MutableStateFlow<Set<String>>(emptySet())
        val activePackages: StateFlow<Set<String>> = _activePackages.asStateFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _activePackages.value = activeNotifications?.map { it.packageName }?.toSet() ?: emptySet()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        _activePackages.value = _activePackages.value + sbn.packageName
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        _activePackages.value = activeNotifications?.map { it.packageName }?.toSet() ?: emptySet()
    }
}
