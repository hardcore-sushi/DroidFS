package sushi.hardcore.droidfs

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar

open class BaseActivity: AppCompatActivity() {
    /**
     * ID of the view to which insets must be applied. If no insets must be applied, no view must have this ID.
     */
    protected open val contentAreaId = R.id.content_area
    protected lateinit var sharedPrefs: SharedPreferences
    protected var applyCustomTheme: Boolean = true
    lateinit var theme: Theme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        theme = Theme.fromSharedPrefs(sharedPrefs)
        if (applyCustomTheme) {
            setTheme(theme.toResourceId())
        }
        if (applyCustomTheme && theme.color == "dynamic") {
            enableEdgeToEdge()
        } else {
            enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        }
        if (!sharedPrefs.getBoolean("usf_screenshot", false)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        applyInsets()
        findViewById<MaterialToolbar>(R.id.toolbar)?.let {
            applyHorizonalDisplayCutoutInsets(it)
        }
    }

    protected fun applyHorizonalDisplayCutoutInsets(view: View) {
        val padLeft = view.paddingLeft
        val padRight = view.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(view) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(
                insets.left + padLeft,
                view.paddingTop,
                insets.right + padRight,
                view.paddingBottom
            )
            windowInsets
        }
    }

    protected fun applyInsets() {
        findViewById<View>(contentAreaId)?.let {
            applyHorizonalDisplayCutoutInsets(it)
            ViewCompat.setOnApplyWindowInsetsListener(
                (findViewById<ViewGroup>(android.R.id.content))
            ) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
                view.updatePadding(
                    left = insets.left,
                    right = insets.right,
                    bottom = if (imeInsets.bottom > 0) imeInsets.bottom else insets.bottom
                )
                windowInsets
            }
        }
    }
}