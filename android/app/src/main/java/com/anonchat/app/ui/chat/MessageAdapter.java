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

    public interface OnMediaClickListener {
        void onDownloadMedia(Message message);
    }

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    private final List<Message> messages = new ArrayList<>();
    private final String currentUserId;
    private final OnMediaClickListener mediaClickListener;

    public MessageAdapter(String currentUserId) {
        this(currentUserId, null);
    }

    public MessageAdapter(String currentUserId, OnMediaClickListener mediaClickListener) {
        this.currentUserId = currentUserId;
        this.mediaClickListener = mediaClickListener;
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
            ((SentViewHolder) holder).bind(msg, mediaClickListener);
        } else if (holder instanceof ReceivedViewHolder) {
            ((ReceivedViewHolder) holder).bind(msg, mediaClickListener);
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
        private final View containerMedia;
        private final ImageView ivMediaThumbnail;
        private final ImageView ivVideoPlayBadge;
        private final TextView tvMediaName;
        private final TextView tvMediaExpiryBadge;
        private final View btnMediaDownload;

        public SentViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_message_text);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            ivStatus = itemView.findViewById(R.id.iv_delivery_status);
            containerMedia = itemView.findViewById(R.id.container_media);
            ivMediaThumbnail = itemView.findViewById(R.id.iv_media_thumbnail);
            ivVideoPlayBadge = itemView.findViewById(R.id.iv_video_play_badge);
            tvMediaName = itemView.findViewById(R.id.tv_media_name);
            tvMediaExpiryBadge = itemView.findViewById(R.id.tv_media_expiry_badge);
            btnMediaDownload = itemView.findViewById(R.id.btn_media_download);
        }

        public void bind(Message msg, OnMediaClickListener listener) {
            tvTime.setText(DateFormatter.formatMessageTime(msg.getCreatedAt()));

            if (msg.isMedia()) {
                tvText.setVisibility(View.GONE);
                containerMedia.setVisibility(View.VISIBLE);
                tvMediaName.setText(msg.getMediaFileName());
                tvMediaExpiryBadge.setText("Auto-deletes in 7 days");

                if (msg.isVideo()) {
                    ivVideoPlayBadge.setVisibility(View.VISIBLE);
                    ivMediaThumbnail.setImageResource(R.drawable.ic_video);
                } else {
                    ivVideoPlayBadge.setVisibility(View.GONE);
                    ivMediaThumbnail.setImageResource(R.drawable.ic_image);
                }

                View.OnClickListener clickAction = v -> {
                    if (listener != null) listener.onDownloadMedia(msg);
                };
                containerMedia.setOnClickListener(clickAction);
                if (btnMediaDownload != null) btnMediaDownload.setOnClickListener(clickAction);
            } else {
                tvText.setVisibility(View.VISIBLE);
                containerMedia.setVisibility(View.GONE);
                tvText.setText(msg.getPlaintext() != null ? msg.getPlaintext() : "[Decrypted Message]");
            }

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
        private final View containerMedia;
        private final ImageView ivMediaThumbnail;
        private final ImageView ivVideoPlayBadge;
        private final TextView tvMediaName;
        private final TextView tvMediaExpiryBadge;
        private final View btnMediaDownload;

        public ReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tv_message_text);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            containerMedia = itemView.findViewById(R.id.container_media);
            ivMediaThumbnail = itemView.findViewById(R.id.iv_media_thumbnail);
            ivVideoPlayBadge = itemView.findViewById(R.id.iv_video_play_badge);
            tvMediaName = itemView.findViewById(R.id.tv_media_name);
            tvMediaExpiryBadge = itemView.findViewById(R.id.tv_media_expiry_badge);
            btnMediaDownload = itemView.findViewById(R.id.btn_media_download);
        }

        public void bind(Message msg, OnMediaClickListener listener) {
            tvTime.setText(DateFormatter.formatMessageTime(msg.getCreatedAt()));

            if (msg.isMedia()) {
                tvText.setVisibility(View.GONE);
                containerMedia.setVisibility(View.VISIBLE);
                tvMediaName.setText(msg.getMediaFileName());
                tvMediaExpiryBadge.setText("Auto-deletes in 7 days");

                if (msg.isVideo()) {
                    ivVideoPlayBadge.setVisibility(View.VISIBLE);
                    ivMediaThumbnail.setImageResource(R.drawable.ic_video);
                } else {
                    ivVideoPlayBadge.setVisibility(View.GONE);
                    ivMediaThumbnail.setImageResource(R.drawable.ic_image);
                }

                View.OnClickListener clickAction = v -> {
                    if (listener != null) listener.onDownloadMedia(msg);
                };
                containerMedia.setOnClickListener(clickAction);
                if (btnMediaDownload != null) btnMediaDownload.setOnClickListener(clickAction);
            } else {
                tvText.setVisibility(View.VISIBLE);
                containerMedia.setVisibility(View.GONE);
                tvText.setText(msg.getPlaintext() != null ? msg.getPlaintext() : "[Decrypted Message]");
            }
        }
    }
}
