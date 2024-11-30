package sushi.hardcore.droidfs.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.util.RingBuffer

/**
 * A [TextView] dropping first lines when appended too fast.
 *
 * The dropping rate depends on the size of the ring buffer (set by the
 * [R.styleable.RingBufferTextView_updateMaxLines] attribute) and the UI update
 * time. If the buffer becomes full before the UI finished to update, the first
 * (oldest) lines are dropped such as only the latest appended lines in the buffer
 * are going to be displayed.
 *
 * If the ring buffer never fills up completely, the content of the [TextView] can
 * grow indefinitely.
 */
class RingBufferTextView: AppCompatTextView {
    private var updateMaxLines = -1
    private var averageLineLength = -1

    /**
     * Lines ring buffer of capacity [updateMaxLines].
     *
     * Must never be used without acquiring the [bufferLock] mutex.
     */
    private val buffer by lazy {
        RingBuffer<String>(updateMaxLines)
    }
    private val bufferLock = Mutex()

    /**
     * Channel used to notify the worker coroutine that a new line has
     * been appended to the ring buffer. No data is sent through it.
     *
     * We use a buffered channel with a capacity of 1 to ensure that the worker
     * can be notified that at least one update occurred while allowing the
     * sender to never block.
     *
     * A greater capacity is not desired because the worker empties the buffer each time.
     */
    private val channel = Channel<Unit>(1)
    private val scope = CoroutineScope(Dispatchers.Default)

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) { init(context, attrs) }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr) { init(context, attrs) }
    private fun init(context: Context, attrs: AttributeSet) {
        with (context.obtainStyledAttributes(attrs, R.styleable.RingBufferTextView)) {
            updateMaxLines = getInt(R.styleable.RingBufferTextView_updateMaxLines, -1)
            averageLineLength = getInt(R.styleable.RingBufferTextView_averageLineLength, -1)
            recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope.launch {
            val text = StringBuilder(updateMaxLines*averageLineLength)
            while (isActive) {
                channel.receive()
                val size: Int
                bufferLock.withLock {
                    size = buffer.size
                    buffer.drain {
                        text.appendLine(it)
                    }
                }
                withContext(Dispatchers.Main) {
                    if (size >= updateMaxLines) {
                        // Buffer full. Lines could have been dropped so we replace the content.
                        setText(text.toString())
                    } else {
                        super.append(text.toString())
                    }
                }
                text.clear()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }

    /**
     * Append a line to the ring buffer and update the UI.
     *
     * If the buffer is full (when adding [R.styleable.RingBufferTextView_updateMaxLines]
     * lines before the UI had time to update), the oldest line is overwritten.
     */
    suspend fun append(line: String) {
        bufferLock.withLock {
            buffer.addLast(line)
        }
        channel.trySend(Unit)
    }
}