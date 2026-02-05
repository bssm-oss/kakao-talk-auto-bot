package com.example.chatbotchichi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BotAdapter(
    private val botList: List<BotInfo>,
    private val onItemClick: (BotInfo) -> Unit
) : RecyclerView.Adapter<BotAdapter.BotViewHolder>() {

    class BotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.bot_name)
        val enableSwitch: Switch = itemView.findViewById(R.id.bot_switch)
        val root: View = itemView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.bot_item, parent, false)
        return BotViewHolder(view)
    }

    override fun onBindViewHolder(holder: BotViewHolder, position: Int) {
        val bot = botList[position]
        holder.nameText.text = bot.name
        holder.enableSwitch.isChecked = bot.isEnabled

        holder.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            bot.isEnabled = isChecked
            // 상태 저장 필요 (BotManager 통해)
            BotManager.setBotEnabled(holder.itemView.context, bot.name, isChecked)
        }
        
        holder.root.setOnClickListener {
            onItemClick(bot)
        }
    }

    override fun getItemCount() = botList.size
}
