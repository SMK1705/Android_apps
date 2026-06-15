package com.rajasudhan.taskmind.data.source

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService
import com.rajasudhan.taskmind.ui.capture.QuickCaptureActivity

/** Quick Settings tile → opens the lock-free quick-add capture dialog. */
class QuickTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // PendingIntent overload (API 34+); minSdk is 35.
        startActivityAndCollapse(pendingIntent)
    }
}
