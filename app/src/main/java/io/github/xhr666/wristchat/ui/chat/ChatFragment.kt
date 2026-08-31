package io.github.xhr666.wristchat.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.xhr666.wristchat.FullscreenInputActivity
import io.github.xhr666.wristchat.R
import io.github.xhr666.wristchat.databinding.FragmentChatBinding
import io.github.xhr666.wristchat.ui.common.RoundInsets

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val vm: ChatViewModel by viewModels { ChatViewModelFactory(requireActivity().application) }
    private lateinit var adapter: ChatAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 圆屏安全区:顶部栏/输入栏按所处高度动态收窄居中(方屏自动退化为 0)
        binding.root.post {
            applySafeInset(binding.topBar)
            applySafeInset(binding.inputBar)
        }
        adapter = ChatAdapter(requireContext()) { url ->
            // 代码块复制(占位:仅提示)
            Toast.makeText(requireContext(), "已复制", Toast.LENGTH_SHORT).show()
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        // 输入:点击输入框 -> 全屏输入页
        binding.etInput.setOnClickListener {
            val intent = Intent(requireContext(), FullscreenInputActivity::class.java)
            intent.putExtra("draft", vm.draft.value ?: "")
            startActivityForResult(intent, REQ_INPUT)
        }

        binding.btnSend.setOnClickListener {
            vm.send(binding.etInput.text.toString())
        }

        binding.btnQuick.setOnClickListener { vm.toggleQuickPanel() }
        binding.btnNew.setOnClickListener { confirmNewSession() }
        binding.btnDetails.setOnClickListener { showDetails() }

        // 快捷输入列表
        binding.quickList.layoutManager = LinearLayoutManager(requireContext())
        binding.quickList.adapter = QuickInputAdapter(vm.quickInputs.value ?: emptyList()) { text ->
            vm.useQuickInput(text)
        }

        // 状态观察
        vm.session.observe(viewLifecycleOwner) { s ->
            adapter.submit(s.messages)
            binding.recycler.scrollToPosition((s.messages.size - 1).coerceAtLeast(0))
            binding.tvTitle.text = vm.title.value?.takeIf { it != "新会话" } ?: vm.settings.model
        }
        vm.title.observe(viewLifecycleOwner) { binding.tvTitle.text = it.takeIf { t -> t != "新会话" } ?: vm.settings.model }
        vm.draft.observe(viewLifecycleOwner) { binding.etInput.setText(it) }
        vm.sending.observe(viewLifecycleOwner) { sending ->
            binding.btnSend.isEnabled = !sending
            if (sending) adapter.setThinking(true) else adapter.setThinking(false)
        }
        vm.status.observe(viewLifecycleOwner) { msg ->
            msg?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        }
        vm.quickPanelVisible.observe(viewLifecycleOwner) { visible ->
            binding.quickPanel.visibility = if (visible) View.VISIBLE else View.GONE
            if (visible) {
                (binding.quickList.adapter as? QuickInputAdapter)?.update(vm.quickInputs.value ?: emptyList())
            }
        }
        vm.quickInputs.observe(viewLifecycleOwner) { list ->
            (binding.quickList.adapter as? QuickInputAdapter)?.update(list)
        }
        vm.details.observe(viewLifecycleOwner) { d ->
            if (d != null && binding.quickPanel.visibility != View.VISIBLE && vm.sending.value == false) {
                // 无操作
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从全屏输入页返回后,草稿由 ViewModel 持有
        vm.refreshQuickInputs()
    }

    private fun confirmNewSession() {
        AlertDialog.Builder(requireContext())
            .setTitle("新会话")
            .setMessage("开始新会话?当前对话保留在历史中")
            .setPositiveButton("确定") { _, _ -> vm.newSession() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDetails() {
        val d = vm.details.value ?: return
        val msg = buildString {
            append("消息数:${d.messageCount}\n")
            append("请求次数:${d.requests}\n")
            append("总 tokens:${d.totalTokens}\n")
            append("缓存命中率:${"%.1f".format(d.cacheHitRate * 100)}%\n")
            append("当前上下文:${d.contextTokens} / ${d.contextWindow}\n")
            append("消耗余额:¥${"%.4f".format(d.totalCost)}\n")
            append("已压缩消息:${d.compressed}")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("对话详情")
            .setMessage(msg)
            .setPositiveButton("关闭", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_INPUT) {
            val text = data?.getStringExtra("result") ?: return
            if (resultCode == FullscreenInputActivity.RESULT_SEND) {
                vm.send(text)
            } else {
                vm.setDraft(text) // 保留草稿
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter.release()
        _binding = null
    }

    /** 圆屏:按栏的垂直位置计算水平安全缩进,让内容不被圆边裁切 */
    private fun applySafeInset(bar: View) {
        val inset = RoundInsets.horizontalInsetPx(binding.root, bar.top.toFloat() + bar.height / 2f)
        val minInset = (6 * resources.displayMetrics.density).toInt()
        bar.setPadding(inset.coerceAtLeast(minInset), bar.paddingTop, inset.coerceAtLeast(minInset), bar.paddingBottom)
    }

    companion object {
        private const val REQ_INPUT = 1001
    }
}
