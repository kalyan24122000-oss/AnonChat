package com.anonchat.app.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.anonchat.app.R;
import com.anonchat.app.data.model.Message;
import com.anonchat.app.utils.DateFormatter;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    private final List<Message> messages = new ArrayList<>();
    private final String currentUserId;

    public MessageAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> list) {
        messages.clear();
        if (list != null) {
            messages.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void addMessage(Message message) {
        if (message == null) return;
        if (message.getId() != null) {
            for (int i = 0; i < messages.size(); i++) {
                if (message.getId().equals(messages.get(i).getId())) {
                    messages.set(i, message);
                    notifyItemChanged(i);
                    return;
                }
            }
        }
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateMessageStatus(String messageId, String status) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.getId() != null && m.getId().equals(messageId)) {
                m.setStatus(status);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void markAllRead() {
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (currentUserId.equals(m.getSenderId()) && !"read".equals(m.getStatus())) {
                m.setStatus("read");
                notifyItemChanged(i);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        Message msg = messages.get(position);
        if (currentUserId != null && currentUserId.equals(msg.getSenderId())) {
            return VIEW_TYPE_SENT;
        }
        return VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
            return new SentViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
            return new ReceivedViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);
        if (holder instanceof SentViewHolder) {
            ((SentViewHolder) holder).bind(msg);
        } else if (holder instanceof ReceivedViewHolder) {
            ((ReceivedViewHolder) holder).bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvText;
        private final TextView tvTime;
        private final ImageView ivStatus;

        public SentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_message_text);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            ivStatus = itemView.findViewById(R.id.iv_delivery_status);
        }

        public void bind(Message msg) {
            tvText.setText(msg.getPlaintext() != null ? msg.getPlaintext() : "[Decrypted Message]");
            tvTime.setText(DateFormatter.formatMessageTime(msg.getCreatedAt()));

            String status = msg.getStatus();
            if ("read".equalsIgnoreCase(status)) {
                ivStatus.setImageResource(R.drawable.ic_check_all);
                ivStatus.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.tick_read));
            } else if ("delivered".equalsIgnoreCase(status)) {
                ivStatus.setImageResource(R.drawable.ic_check_all);
                ivStatus.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.tick_delivered));
            } else {
                ivStatus.setImageResource(R.drawable.ic_check);
                ivStatus.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.tick_sent));
            }
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvText;
        private final TextView tvTime;

        public ReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_message_text);
            tvTime = itemView.findViewById(R.id.tv_message_time);
        }

        public void bind(Message msg) {
            tvText.setText(msg.getPlaintext() != null ? msg.getPlaintext() : "[Decrypted Message]");
            tvTime.setText(DateFormatter.formatMessageTime(msg.getCreatedAt()));
        }
    }
}
