package com.anonchat.app.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BlockedUsersActivity extends AppCompatActivity {
    private ProgressBar progressBlocked;
    private TextView tvNoBlocked;
    private RecyclerView rvBlockedUsers;
    private BlockedAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_users);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_blocked);
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBlocked = findViewById(R.id.progress_blocked);
        tvNoBlocked = findViewById(R.id.tv_no_blocked);
        rvBlockedUsers = findViewById(R.id.rv_blocked_users);

        rvBlockedUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BlockedAdapter(this::unblockUser);
        rvBlockedUsers.setAdapter(adapter);

        loadBlockedUsers();
    }

    private void loadBlockedUsers() {
        progressBlocked.setVisibility(View.VISIBLE);
        tvNoBlocked.setVisibility(View.GONE);

        ((AnonChatApp) getApplication()).getApiService().getBlockedUsers().enqueue(new Callback<Map<String, List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<Map<String, List<Map<String, Object>>>> call, Response<Map<String, List<Map<String, Object>>>> response) {
                progressBlocked.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    List<Map<String, Object>> blocked = response.body().get("blocked");
                    adapter.setBlockedList(blocked);
                    if (blocked == null || blocked.isEmpty()) {
                        tvNoBlocked.setVisibility(View.VISIBLE);
                    }
                } else {
                    tvNoBlocked.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<Map<String, List<Map<String, Object>>>> call, Throwable t) {
                progressBlocked.setVisibility(View.GONE);
                tvNoBlocked.setVisibility(View.VISIBLE);
                Toast.makeText(BlockedUsersActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void unblockUser(String blockedId) {
        Map<String, String> body = new HashMap<>();
        body.put("blockedId", blockedId);

        ((AnonChatApp) getApplication()).getApiService().unblockUser(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(BlockedUsersActivity.this, "User unblocked", Toast.LENGTH_SHORT).show();
                    loadBlockedUsers();
                } else {
                    Toast.makeText(BlockedUsersActivity.this, "Failed to unblock user", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(BlockedUsersActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class BlockedAdapter extends RecyclerView.Adapter<BlockedAdapter.ViewHolder> {
        private final List<Map<String, Object>> blockedList = new ArrayList<>();
        private final OnUnblockClickListener listener;

        public interface OnUnblockClickListener {
            void onUnblock(String blockedId);
        }

        public BlockedAdapter(OnUnblockClickListener listener) {
            this.listener = listener;
        }

        public void setBlockedList(List<Map<String, Object>> list) {
            blockedList.clear();
            if (list != null) blockedList.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_search, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> item = blockedList.get(position);
            String id = (String) item.get("blocked_id");
            String name = (String) item.get("display_name");

            holder.tvUserId.setText(id);
            holder.tvUserName.setText(name != null ? name : "Anonymous");
            holder.btnUnblock.setText("Unblock");
            holder.btnUnblock.setOnClickListener(v -> listener.onUnblock(id));
        }

        @Override
        public int getItemCount() {
            return blockedList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvUserId;
            final TextView tvUserName;
            final MaterialButton btnUnblock;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvUserId = itemView.findViewById(R.id.tv_search_user_id);
                tvUserName = itemView.findViewById(R.id.tv_search_user_name);
                btnUnblock = itemView.findViewById(R.id.btn_start_chat);
            }
        }
    }
}
