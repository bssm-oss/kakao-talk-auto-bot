package com.example.kakaotalkautobot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RoomTargetAdapter(
    private val rooms: MutableList<AppSettings.RoomTarget>,
    private val secondaryActionLabel: String,
    private val onRoomClick: (AppSettings.RoomTarget) -> Unit,
    private val onSecondaryActionClick: (AppSettings.RoomTarget) -> Unit,
    private val onDeleteClick: (AppSettings.RoomTarget) -> Unit,
    private val onToggleChanged: (AppSettings.RoomTarget, Boolean) -> Unit
) : RecyclerView.Adapter<RoomTargetAdapter.RoomTargetViewHolder>() {

    class RoomTargetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.bot_name)
        val metaText: TextView = itemView.findViewById(R.id.bot_meta)
        val enableSwitch: SwitchMaterial = itemView.findViewById(R.id.bot_switch)
        val secondaryButton: MaterialButton = itemView.findViewById(R.id.bot_secondary_action)
        val deleteButton: MaterialButton = itemView.findViewById(R.id.bot_delete)
        val root: View = itemView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomTargetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.bot_item, parent, false)
        return RoomTargetViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomTargetViewHolder, position: Int) {
        val room = rooms[position]
        holder.nameText.text = room.name
        holder.metaText.text = buildMetaText(holder.itemView.context, room)
        holder.secondaryButton.text = secondaryActionLabel

        holder.enableSwitch.setOnCheckedChangeListener(null)
        holder.enableSwitch.isChecked = room.isEnabled
        holder.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggleChanged(room, isChecked)
        }

        holder.root.setOnClickListener { onRoomClick(room) }
        holder.secondaryButton.setOnClickListener { onSecondaryActionClick(room) }
        holder.deleteButton.setOnClickListener { onDeleteClick(room) }
    }

    override fun getItemCount(): Int = rooms.size

    fun replaceItems(newItems: List<AppSettings.RoomTarget>) {
        rooms.clear()
        rooms.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun buildMetaText(context: android.content.Context, room: AppSettings.RoomTarget): String {
        val memoryText = BotManager.getConfigByRoomPattern(context, room.name)?.roomMemory
            ?: AppSettings.getRoomMemory(context, room.name)
        val memoryLabel = if (memoryText.isBlank()) {
            "메모 없음"
        } else {
            "메모 ${memoryText.length}자"
        }
        val statusLabel = if (room.isEnabled) "활성" else "비활성"
        return "$memoryLabel · $statusLabel"
    }
}
