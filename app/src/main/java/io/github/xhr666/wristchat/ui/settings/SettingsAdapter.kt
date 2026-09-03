package io.github.xhr666.wristchat.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.xhr666.wristchat.R

data class SettingRow(
    val type: Int,               // SECTION / ACTION / TOGGLE / VALUE
    val title: String,
    val value: String = "",
    val checked: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val onToggle: ((Boolean) -> Unit)? = null,
) {
    companion object {
        const val SECTION = 0
        const val ACTION = 1
        const val TOGGLE = 2
        const val VALUE = 3
    }
}

class SettingsAdapter(
    private var rows: List<SettingRow>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun submit(list: List<SettingRow>) {
        rows = list
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = rows[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            SettingRow.SECTION -> SectionHolder(inflater.inflate(R.layout.item_setting_section, parent, false))
            SettingRow.TOGGLE -> RowHolder(inflater.inflate(R.layout.item_setting, parent, false), true)
            else -> RowHolder(inflater.inflate(R.layout.item_setting, parent, false), false)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = rows[position]
        when (holder) {
            is SectionHolder -> holder.title.text = row.title
            is RowHolder -> {
                holder.title.text = row.title
                holder.value.text = row.value
                if (row.type == SettingRow.TOGGLE) {
                    holder.switch.visibility = View.VISIBLE
                    holder.value.visibility = View.GONE
                    // 先摘监听再设值,避免 setChecked 触发回调造成重入(修复:开密码弹窗点取消又弹关闭确认)
                    holder.switch.setOnCheckedChangeListener(null)
                    holder.switch.isChecked = row.checked
                    holder.switch.setOnCheckedChangeListener { _, checked -> row.onToggle?.invoke(checked) }
                    holder.itemView.setOnClickListener { holder.switch.isChecked = !holder.switch.isChecked }
                } else {
                    holder.switch.visibility = View.GONE
                    holder.value.visibility = View.VISIBLE
                    if (row.onClick == null) {
                        holder.itemView.setOnClickListener(null)
                    } else {
                        holder.itemView.setOnClickListener { row.onClick?.invoke() }
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = rows.size

    class SectionHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvSection)
    }

    class RowHolder(view: View, private val hasSwitch: Boolean) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val value: TextView = view.findViewById(R.id.tvValue)
        val switch: Switch = view.findViewById(R.id.switchValue)
    }
}
