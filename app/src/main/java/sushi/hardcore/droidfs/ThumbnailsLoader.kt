package sushi.hardcore.droidfs

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import sushi.hardcore.droidfs.filesystems.EncryptedVolume

class ThumbnailsLoader(
    private val context: Context,
    private val encryptedVolume: EncryptedVolume,
    private val maxSize: Long,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    internal class ThumbnailData(val id: Int, val path: String, val imageView: ImageView, val onLoaded: (Drawable) -> Unit)
    internal class ThumbnailTask(var senderJob: Job?, var workerJob: Job?, var target: DrawableImageViewTarget?)

    private val concurrentTasks = Runtime.getRuntime().availableProcessors()/4
    private val channel = Channel<ThumbnailData>(concurrentTasks)
    private var taskId = 0
    private val tasks = HashMap<Int, ThumbnailTask>()

    private suspend fun loadThumbnail(data: ThumbnailData) {
        withContext(Dispatchers.IO) {
            encryptedVolume.loadWholeFile(data.path, maxSize = maxSize).first?.let {
                yield()
                withContext(Dispatchers.Main) {
                    tasks[data.id]?.let { task ->
                        val channel = Channel<Unit>(1)
                        task.target = Glide.with(context).load(it).skipMemoryCache(true).into(object : DrawableImageViewTarget(data.imageView) {
                            override fun onResourceReady(
                                resource: Drawable,
                                transition: Transition<in Drawable>?
                            ) {
                                super.onResourceReady(resource, transition)
                                data.onLoaded(resource)
                                channel.trySend(Unit)
                            }
                        })
                        channel.receive()
                        tasks.remove(data.id)
                    }
                }
            }
        }
    }

    fun initialize() {
        for (i in 0 until concurrentTasks) {
            lifecycleScope.launch {
                while (true) {
                    val data = channel.receive()
                    val workerJob = launch {
                        loadThumbnail(data)
                    }
                    tasks[data.id]?.workerJob = workerJob
                    workerJob.join()
                }
            }
        }
    }

    fun loadAsync(path: String, target: ImageView, onLoaded: (Drawable) -> Unit): Int {
        val id = taskId++
        tasks[id] = ThumbnailTask(null, null, null)
        val senderJob = lifecycleScope.launch {
            channel.send(ThumbnailData(id, path, target, onLoaded))
        }
        tasks[id]!!.senderJob = senderJob
        return id
    }

    fun cancel(id: Int) {
        tasks[id]?.let { task ->
            task.senderJob?.cancel()
            task.workerJob?.cancel()
            task.target?.let {
                Glide.with(context).clear(it)
            }
        }
        tasks.remove(id)
    }
}