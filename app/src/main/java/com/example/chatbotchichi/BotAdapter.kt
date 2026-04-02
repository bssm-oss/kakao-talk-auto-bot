package com.example.kakaotalkautobot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.RecyclerView

class BotAdapter(
    private val botList: List<BotInfo>,
    private val onItemClick: (BotInfo) -> Unit,
    private val onDeleteClick: (BotInfo) -> Unit,
    private val onToggleChanged: (BotInfo, Boolean) -> Unit
) : RecyclerView.Adapter<BotAdapter.BotViewHolder>() {

    class BotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.bot_name)
        val enableSwitch: Switch = itemView.findViewById(R.id.bot_switch)
        val deleteButton: MaterialButton = itemView.findViewById(R.id.bot_delete)
        val root: View = itemView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.bot_item, parent, false)
        return BotViewHolder(view)
    }

    override fun onBindViewHolder(holder: BotViewHolder, position: Int) {
        val bot = botList[position]
        holder.nameText.text = bot.name
        holder.enableSwitch.setOnCheckedChangeListener(null)
        holder.enableSwitch.isChecked = bot.isEnabled
        holder.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            bot.isEnabled = isChecked
            // 상태 저장 필요 (BotManager 통해)
            BotManager.setBotEnabled(holder.itemView.context, bot.name, isChecked)
            onToggleChanged(bot, isChecked)
        }
        
        holder.root.setOnClickListener {
            onItemClick(bot)
        }
        holder.deleteButton.setOnClickListener {
            onDeleteClick(bot)
        }
    }

    override fun getItemCount() = botList.size
}
