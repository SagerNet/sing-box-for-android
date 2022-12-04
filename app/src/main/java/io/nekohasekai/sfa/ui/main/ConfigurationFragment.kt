package io.nekohasekai.sfa.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.blacksquircle.ui.language.json.JsonLanguage
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.databinding.FragmentConfigurationBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigurationFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        binding.editor.language = JsonLanguage()
        lifecycleScope.launch(Dispatchers.IO) {
            val configurationContent = Settings.configurationContent
            withContext(Dispatchers.Main) {
                binding.editor.setTextContent(configurationContent)
                binding.editor.addTextChangedListener {
                    val newContent = it.toString()
                    lifecycleScope.launch(Dispatchers.IO) {
                        Settings.configurationContent = newContent
                    }
                }
            }
        }
        return binding.root
    }


}