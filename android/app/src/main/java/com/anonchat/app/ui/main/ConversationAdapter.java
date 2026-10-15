package com.anonchat.app.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anonchat.app.R;
import com.anonchat.app.data.model.Conversation;
import com.anonchat.app.utils.DateFormatter;

import java.util.ArrayList;
import java.util.List;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {
    private final List<Conversation> conversations = new ArrayList<>();
    private final OnConversationClickListener listener;

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
    }

    public ConversationAdapter(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void setConversations(List<Conversation> list) {
        conversations.clear();
        if (list != null) {
            conversations.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conv = conversations.get(position);
        holder.bind(conv, listener);
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final View viewAvatarRing;
        private final TextView tvAvatarInitial;
        private final android.widget.ImageView ivAdminBadge;
        private final TextView tvConvName;
        private final TextView tvConvTime;
        private final TextView tvConvLastMsg;
        private final TextView tvConvUnreadBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewAvatarRing = itemView.findViewById(R.id.view_avatar_ring);
            tvAvatarInitial = itemView.findViewById(R.id.tv_avatar_initial);
            ivAdminBadge = itemView.findViewById(R.id.iv_admin_badge);
            tvConvName = itemView.findViewById(R.id.tv_conv_name);
            tvConvTime = itemView.findViewById(R.id.tv_conv_time);
            tvConvLastMsg = itemView.findViewById(R.id.tv_conv_last_msg);
            tvConvUnreadBadge = itemView.findViewById(R.id.tv_conv_unread_badge);
        }

        public void bind(Conversation conv, OnConversationClickListener listener) {
            String recipientId = conv.getRecipient() != null ? conv.getRecipient().getId() : "Unknown";
            String displayName = conv.getRecipient() != null && conv.getRecipient().getDisplayName() != null
                ? conv.getRecipient().getDisplayName()
                : recipientId;

            tvConvName.setText(displayName);

            // Avatar initial
            String initial = !displayName.isEmpty() ? String.valueOf(displayName.charAt(0)).toUpperCase() : "U";
            tvAvatarInitial.setText(initial);

            // Admin badge and outline ring
            boolean isAdmin = conv.getRecipient() != null && conv.getRecipient().isAdmin();
            if (isAdmin) {
                viewAvatarRing.setVisibility(View.VISIBLE);
                ivAdminBadge.setVisibility(View.VISIBLE);
                try {
                    String colorStr = conv.getRecipient().getBadgeColor();
                    int color = android.graphics.Color.parseColor(colorStr);
                    android.graphics.drawable.GradientDrawable ring = (android.graphics.drawable.GradientDrawable) viewAvatarRing.getBackground().mutate();
                    ring.setStroke((int) (2.5f * itemView.getResources().getDisplayMetrics().density), color);
                    ivAdminBadge.setColorFilter(color);
                } catch (Exception ignored) {}
            } else {
                viewAvatarRing.setVisibility(View.GONE);
                ivAdminBadge.setVisibility(View.GONE);
            }

            // Last message snippet & timestamp
            if (conv.getLastMessage() != null) {
                tvConvTime.setText(DateFormatter.formatConversationTime(conv.getLastMessage().getTimestamp()));
                tvConvLastMsg.setText("Encrypted message");
            } else {
                tvConvTime.setText(DateFormatter.formatConversationTime(conv.getUpdatedAt()));
                tvConvLastMsg.setText("Start secure conversation");
            }

            // Unread count
            if (conv.getUnreadCount() > 0) {
                tvConvUnreadBadge.setVisibility(View.VISIBLE);
                tvConvUnreadBadge.setText(String.valueOf(conv.getUnreadCount()));
            } else {
                tvConvUnreadBadge.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConversationClick(conv);
                }
            });
        }
    }
}
