package com.anonchat.app.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anonchat.app.R;

import java.util.ArrayList;
import java.util.List;

public class CallAdapter extends RecyclerView.Adapter<CallAdapter.ViewHolder> {

    public static class CallItem {
        public final String id;
        public final String contactName;
        public final String timeFormatted;
        public final int callType; // 1 = incoming, 2 = outgoing, 3 = missed

        public CallItem(String id, String contactName, String timeFormatted, int callType) {
            this.id = id;
            this.contactName = contactName;
            this.timeFormatted = timeFormatted;
            this.callType = callType;
        }
    }

    public interface OnCallClickListener {
        void onCallClick(CallItem item);
    }

    private final List<CallItem> items = new ArrayList<>();
    private final OnCallClickListener listener;

    public CallAdapter(OnCallClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<CallItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_call_log, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CallItem item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAvatar;
        final TextView tvCallerName;
        final TextView tvCallTime;
        final ImageView ivCallType;
        final ImageView btnActionCall;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tv_call_avatar);
            tvCallerName = itemView.findViewById(R.id.tv_caller_name);
            tvCallTime = itemView.findViewById(R.id.tv_call_time);
            ivCallType = itemView.findViewById(R.id.iv_call_type);
            btnActionCall = itemView.findViewById(R.id.btn_action_call);
        }

        void bind(CallItem item, OnCallClickListener listener) {
            tvCallerName.setText(item.contactName);
            tvCallTime.setText(item.timeFormatted);

            String initial = !item.contactName.isEmpty() ? String.valueOf(item.contactName.charAt(0)).toUpperCase() : "U";
            tvAvatar.setText(initial);

            if (item.callType == 3) {
                ivCallType.setImageResource(R.drawable.ic_call_missed);
            } else if (item.callType == 2) {
                ivCallType.setImageResource(R.drawable.ic_call_outgoing);
            } else {
                ivCallType.setImageResource(R.drawable.ic_call_incoming);
            }

            View.OnClickListener clickAction = v -> {
                if (listener != null) listener.onCallClick(item);
            };
            itemView.setOnClickListener(clickAction);
            btnActionCall.setOnClickListener(clickAction);
        }
    }
}
