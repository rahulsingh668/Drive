package com.rahul.drive;

import android.service.notification.NotificationListenerService;

/** Optional permission bridge for standard Android media sessions; reads no notifications. */
public final class MediaAccess extends NotificationListenerService {
    // Both service and activity use the application's main thread and default process.
    static Runnable onConnectionChanged;
    @Override public void onListenerConnected() {
        if (onConnectionChanged != null) onConnectionChanged.run();
    }
    @Override public void onListenerDisconnected() {
        if (onConnectionChanged != null) onConnectionChanged.run();
    }
}
