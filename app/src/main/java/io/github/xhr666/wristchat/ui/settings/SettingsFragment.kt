package io.github.xhr666.wristchat.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.xhr666.wristchat.CategoryActivity
import io.github.xhr666.wristchat.databinding.FragmentSettingsBinding
import io.github.xhr666.wristchat.ui.common.RoundInsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置主页:分类菜单(两级导航)。
 * 点击分类 -> CategoryActivity;顶部横幅提示会话上限;进页自动静默检查更新。
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val vm: SettingsViewModel by viewModels { SettingsViewModelFactory(requireActivity().application) }
    private lateinit var adapter: SettingsAdapter
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.post {
            val inset = RoundInsets.horizontalInsetPx(binding.root, binding.topBar.top.toFloat() + binding.topBar.height / 2f)
            val minInset = (10 * resources.displayMetrics.density).toInt()
            binding.topBar.setPadding(inset.coerceAtLeast(minInset), binding.topBar.paddingTop, inset.coerceAtLeast(minInset), binding.topBar.paddingBottom)
        }
        adapter = SettingsAdapter(emptyList())
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.tvTitle.text = "设置"

        vm.sessionWarn.observe(viewLifecycleOwner) { warn ->
            binding.tvSessionWarn.visibility = if (warn) View.VISIBLE else View.GONE
        }

        adapter.submit(buildMenu())
        autoCheckUpdate()
    }

    private fun buildMenu(): List<SettingRow> {
        val s = vm.settings
        return listOf(
            menu("服务", "服务商 / API Key / 地址", CategoryActivity.CAT_SERVICE),
            menu("模型与对话", s.model, CategoryActivity.CAT_MODEL),
            menu("快捷输入", "${s.getQuickInputs().size} 条", CategoryActivity.CAT_QUICK),
            menu("技能与记忆", "Skills 导入 / 记忆", CategoryActivity.CAT_SKILLS_MEMORY),
            menu("会话与导入", "${vm.sessionStore.list().size} 个会话", CategoryActivity.CAT_SESSIONS),
            menu("存储与缓存", vm.cacheInfo.value ?: "", CategoryActivity.CAT_STORAGE),
            menu("更新与同步", "检查更新 / 手机同步", CategoryActivity.CAT_UPDATE),
            menu("安全与外观", "密码 / 主题", CategoryActivity.CAT_SECURITY),
            menu("关于", "版本 / 开源", CategoryActivity.CAT_ABOUT),
        )
    }

    private fun menu(title: String, value: String, category: String): SettingRow =
        SettingRow(SettingRow.VALUE, title, value, onClick = {
            startActivity(Intent(requireContext(), CategoryActivity::class.java).putExtra(CategoryActivity.EXTRA_CATEGORY, category))
        })

    private fun autoCheckUpdate() {
        val s = vm.settings
        val now = System.currentTimeMillis()
        if (now - s.lastUpdateCheck < s.updateCooldownMin * 60_000L) return
        val repo = io.github.xhr666.wristchat.data.UpdateRepository(s)
        scope.launch {
            val result = repo.check()
            s.lastUpdateCheck = System.currentTimeMillis()
            withContext(Dispatchers.Main) {
                if (result is io.github.xhr666.wristchat.data.UpdateResult.Found) {
                    Toast.makeText(requireContext(), "发现新版本 ${result.info.tagName},请到 设置→更新与同步 下载", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.submit(buildMenu())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
