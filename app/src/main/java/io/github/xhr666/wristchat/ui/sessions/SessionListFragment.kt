package io.github.xhr666.wristchat.ui.sessions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.xhr666.wristchat.MainActivity
import io.github.xhr666.wristchat.R
import io.github.xhr666.wristchat.data.Session
import io.github.xhr666.wristchat.databinding.FragmentSessionsBinding
import io.github.xhr666.wristchat.ui.chat.ChatViewModel
import io.github.xhr666.wristchat.ui.chat.ChatViewModelFactory
import io.github.xhr666.wristchat.ui.common.WristDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 负一屏:会话历史(含 AI 标题),点击切换到聊天页;长按删除 */
class SessionListFragment : Fragment() {

    private var _binding: FragmentSessionsBinding? = null
    private val binding get() = _binding!!
    private val vm: ChatViewModel by activityViewModels { ChatViewModelFactory(requireActivity().application) }
    private lateinit var adapter: SessionsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSessionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SessionsAdapter(
            onClick = { s -> vm.setCurrentSession(s.id); switchToChat() },
            onLongClick = { s -> confirmDelete(s) },
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.timeCapsule.bind(binding.recycler)
        binding.indicatorWrap.bindChild()

        binding.btnNew.setOnClickListener {
            vm.newSession()
            switchToChat()
        }

        vm.sessions.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.tvTitle.text = if (list.isEmpty()) "会话" else "会话(${list.size})"
        }
        binding.root.post { vm.refreshSessions() }
    }

    private fun confirmDelete(s: Session) {
        WristDialog.build(requireContext())
            .setTitle("删除会话")
            .setMessage("删除「${s.title.take(14)}」?不可恢复")
            .setPositive("删除") { vm.deleteSession(s.id) }
            .setNegative("取消", null)
            .show()
    }

    private fun switchToChat() {
        (activity as? MainActivity)?.showChatPage()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshSessions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class SessionsAdapter(
        private val onClick: (Session) -> Unit,
        private val onLongClick: (Session) -> Unit,
    ) : RecyclerView.Adapter<SessionsAdapter.Holder>() {

        private var list: List<Session> = emptyList()
        private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        fun submit(s: List<Session>) {
            list = s
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val s = list[position]
            holder.title.text = s.title.ifBlank { "新会话" }
            val cost = if (s.totalCost > 0) " · ¥%.3f".format(s.totalCost) else ""
            holder.sub.text = "${s.messages.size} 条 · ${timeFmt.format(Date(s.updatedAt))}$cost"
            holder.itemView.setOnClickListener { onClick(s) }
            holder.itemView.setOnLongClickListener { onLongClick(s); true }
        }

        override fun getItemCount(): Int = list.size

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvTitle)
            val sub: TextView = view.findViewById(R.id.tvSub)
        }
    }
}
