package sushi.hardcore.droidfs

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.lang.ref.WeakReference
import java.util.*

class SensorOrientationListener(context: Context) {
    private val mSensorEventListener: SensorEventListener
    private val mSensorManager: SensorManager
    init {
        mSensorEventListener = NotifierSensorEventListener()
        mSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val mListeners = ArrayList<WeakReference<Listener?>>(3)
    private var orientation = 0

    private fun onResume() {
        mSensorManager.registerListener(
            mSensorEventListener,
            mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    private fun onPause() {
        mSensorManager.unregisterListener(mSensorEventListener)
    }

    private inner class NotifierSensorEventListener : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            var newOrientation: Int = orientation
            if (x < 5 && x > -5 && y > 5) newOrientation = 0
            else if (x < -5 && y < 5 && y > -5) newOrientation = 90
            else if (x < 5 && x > -5 && y < -5) newOrientation = 180
            else if (x > 5 && y < 5 && y > -5) newOrientation = 270

            if (orientation != newOrientation) {
                orientation = newOrientation
                notifyListeners()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    interface Listener {
        fun onOrientationChange(newOrientation: Int)
    }

    fun addListener(listener: Listener) {
        if (get(listener) == null) // prevent duplications
            mListeners.add(WeakReference(listener))
        if (mListeners.size == 1) {
            onResume() // this is the first client
        }
    }

    fun remove(listener: Listener) {
        val listenerWR = get(listener)
        remove(listenerWR)
    }

    private fun remove(listenerWR: WeakReference<Listener?>?) {
        if (listenerWR != null) mListeners.remove(listenerWR)
        if (mListeners.size == 0) {
            onPause()
        }
    }

    private operator fun get(listener: Listener): WeakReference<Listener?>? {
        for (existingListener in mListeners) if (existingListener.get() === listener) return existingListener
        return null
    }

    private fun notifyListeners() {
        val deadListeners = ArrayList<WeakReference<Listener?>>()
        for (wr in mListeners) {
            if (wr.get() == null)
                deadListeners.add(wr)
            else
                wr.get()!!.onOrientationChange(orientation)
        }

        // remove dead references
        for (wr in deadListeners) {
            mListeners.remove(wr)
        }
    }
}