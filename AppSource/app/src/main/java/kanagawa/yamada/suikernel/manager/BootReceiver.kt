package kanagawa.yamada.suikernel.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("SuiKernel", "Boot completed, applying SuiKernel settings...")
            SuiKernelIoctl.applyAllSettings(context)
        }
    }
}
