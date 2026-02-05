package com.example.chatbotchichi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Left
        val layoutLeft: LinearLayout = itemView.findViewById(R.id.layout_left)
        val nameLeft: TextView = itemView.findViewById(R.id.text_name_left)
        val msgLeft: TextView = itemView.findViewById(R.id.text_msg_left)
        val timeLeft: TextView = itemView.findViewById(R.id.text_time_left)

        // Right
        val layoutRight: LinearLayout = itemView.findViewById(R.id.layout_right)
        val msgRight: TextView = itemView.findViewById(R.id.text_msg_right)
        val timeRight: TextView = itemView.findViewById(R.id.text_time_right)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]

        if (msg.isMe) {
            holder.layoutLeft.visibility = View.GONE
            holder.layoutRight.visibility = View.VISIBLE
            holder.msgRight.text = msg.message
            holder.timeRight.text = msg.time
        } else {
            holder.layoutLeft.visibility = View.VISIBLE
            holder.layoutRight.visibility = View.GONE
            holder.nameLeft.text = msg.name
            holder.msgLeft.text = msg.message
            holder.timeLeft.text = msg.time
        }
    }

    override fun getItemCount() = messages.size
}
