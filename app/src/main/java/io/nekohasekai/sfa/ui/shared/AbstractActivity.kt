package io.nekohasekai.sfa.ui.shared

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowCompat
import androidx.viewbinding.ViewBinding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.ktx.getAttrColor
import io.nekohasekai.sfa.ui.MainActivity
import io.nekohasekai.sfa.utils.MIUIUtils
import java.lang.reflect.ParameterizedType

abstract class AbstractActivity<Binding : ViewBinding> : AppCompatActivity() {

    private var _binding: Binding? = null
    internal val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DynamicColors.applyToActivityIfAvailable(this)

        // Set light navigation bar for Android 8.0
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
            val nightFlag = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightFlag != Configuration.UI_MODE_NIGHT_YES) {
                val insetsController = WindowCompat.getInsetsController(
                    window,
                    window.decorView
                )
                insetsController.isAppearanceLightNavigationBars = true
            }
        }

        _binding = createBindingInstance(layoutInflater).also {
            setContentView(it.root)
        }

        findViewById<MaterialToolbar>(R.id.toolbar)?.also {
            setSupportActionBar(it)
        }

        // MIUI overrides colorSurfaceContainer to colorSurface without below flags
        @Suppress("DEPRECATION") if (MIUIUtils.isMIUI) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }

        if (this !is MainActivity) {
            supportActionBar?.setHomeAsUpIndicator(AppCompatResources.getDrawable(
                this@AbstractActivity, R.drawable.ic_arrow_back_24
            )!!.apply {
                setTint(getAttrColor(com.google.android.material.R.attr.colorOnSurface))
            })
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createBindingInstance(
        inflater: LayoutInflater,
    ): Binding {
        val vbType = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0]
        val vbClass = vbType as Class<Binding>
        val method = vbClass.getMethod("inflate", LayoutInflater::class.java)
        return method.invoke(null, inflater) as Binding
    }

}