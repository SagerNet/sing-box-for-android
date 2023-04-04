package io.nekohasekai.sfa.ui.shared

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.elevation.SurfaceColors
import io.nekohasekai.sfa.R

abstract class AbstractActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DynamicColors.applyToActivityIfAvailable(this)

        val color = SurfaceColors.SURFACE_2.getColor(this)
        window.statusBarColor = color
        window.navigationBarColor = color

        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back_24)
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

}