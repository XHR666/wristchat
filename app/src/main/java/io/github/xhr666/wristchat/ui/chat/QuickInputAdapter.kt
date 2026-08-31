package io.github.xhr666.wristchat.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.xhr666.wristchat.R

class QuickInputAdapter(
    private var items: List<String>,
    private val onClick: (String) -> Unit,
) : RecyclerView.Adapter<QuickInputAdapter.Holder>() {

    fun update(list: List<String>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_quick, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val text = items[position]
        holder.text.text = text
        holder.itemView.setOnClickListener { onClick(text) }
    }

    override fun getItemCount(): Int = items.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvQuick)
    }
}
