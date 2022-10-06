package sushi.hardcore.droidfs.explorers

import android.content.Context
import android.content.Intent
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.IntentUtils

class ExplorerRouter(private val context: Context, private val intent: Intent) {
    var pickMode = intent.action == "pick"
    var dropMode = (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) && intent.extras != null

    fun getExplorerIntent(encryptedVolume: EncryptedVolume, volumeShortName: String): Intent {
        var explorerIntent: Intent? = null
        if (dropMode) { //import via android share menu
            explorerIntent = Intent(context, ExplorerActivityDrop::class.java)
            IntentUtils.forwardIntent(intent, explorerIntent)
        } else if (pickMode) {
            explorerIntent = Intent(context, ExplorerActivityPick::class.java)
            explorerIntent.putExtra("destinationVolume", IntentUtils.getParcelableExtra<EncryptedVolume>(intent, "volume")!!)
            explorerIntent.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
        }
        if (explorerIntent == null) {
            explorerIntent = Intent(context, ExplorerActivity::class.java) //default opening
        }
        explorerIntent.putExtra("volume", encryptedVolume)
        explorerIntent.putExtra("volume_name", volumeShortName)
        return explorerIntent
    }
}