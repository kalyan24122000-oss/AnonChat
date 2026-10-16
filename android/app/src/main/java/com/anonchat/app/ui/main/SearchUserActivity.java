package com.anonchat.app.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.anonchat.app.data.model.User;
import com.anonchat.app.ui.chat.ConversationActivity;
import com.anonchat.app.utils.Constants;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchUserActivity extends AppCompatActivity {
    private EditText etSearchQuery;
    private ProgressBar searchProgress;
    private TextView tvSearchEmpty;
    private RecyclerView rvSearchResults;
    private SearchAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_user);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_search);
        toolbar.setNavigationOnClickListener(v -> finish());

        etSearchQuery = findViewById(R.id.et_search_query);
        searchProgress = findViewById(R.id.search_progress);
        tvSearchEmpty = findViewById(R.id.tv_search_empty);
        rvSearchResults = findViewById(R.id.rv_search_results);
        MaterialButton btnDoSearch = findViewById(R.id.btn_do_search);

        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchAdapter(this::startConversationWithUser);
        rvSearchResults.setAdapter(adapter);

        btnDoSearch.setOnClickListener(v -> performSearch());
    }

    private void performSearch() {
        String query = etSearchQuery.getText().toString().trim();
        if (query.length() < 3) {
            Toast.makeText(this, "Enter at least 3 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        searchProgress.setVisibility(View.VISIBLE);
        tvSearchEmpty.setVisibility(View.GONE);

        ((AnonChatApp) getApplication()).getApiService().searchUser(query).enqueue(new Callback<Map<String, List<User>>>() {
            @Override
            public void onResponse(Call<Map<String, List<User>>> call, Response<Map<String, List<User>>> response) {
                searchProgress.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    List<User> users = response.body().get("users");
                    adapter.setUsers(users);
                    if (users == null || users.isEmpty()) {
                        tvSearchEmpty.setVisibility(View.VISIBLE);
                        tvSearchEmpty.setText("No users found matching: " + query);
                    }
                } else {
                    tvSearchEmpty.setVisibility(View.VISIBLE);
                    tvSearchEmpty.setText("Search failed");
                }
            }

            @Override
            public void onFailure(Call<Map<String, List<User>>> call, Throwable t) {
                searchProgress.setVisibility(View.GONE);
                tvSearchEmpty.setVisibility(View.VISIBLE);
                tvSearchEmpty.setText("Network error: " + t.getMessage());
            }
        });
    }

    private void startConversationWithUser(User user) {
        if (user == null || user.getId() == null) return;
        searchProgress.setVisibility(View.VISIBLE);
        Map<String, String> body = new HashMap<>();
        body.put("recipientId", user.getId());

        ((AnonChatApp) getApplication()).getApiService().getOrCreateConversation(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                searchProgress.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    String convId = (String) response.body().get("id");
                    Intent intent = new Intent(SearchUserActivity.this, ConversationActivity.class);
                    intent.putExtra(Constants.EXTRA_CONVERSATION_ID, convId);
                    intent.putExtra(Constants.EXTRA_RECIPIENT_ID, user.getId());
                    intent.putExtra(Constants.EXTRA_RECIPIENT_NAME, user.getDisplayName());
                    intent.putExtra(Constants.EXTRA_RECIPIENT_PUBLIC_KEY, user.getPublicKey());
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(SearchUserActivity.this, "Failed to start conversation", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                searchProgress.setVisibility(View.GONE);
                Toast.makeText(SearchUserActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private final List<User> users = new ArrayList<>();
        private final OnUserClickListener listener;

        public interface OnUserClickListener {
            void onUserClick(User user);
        }

        public SearchAdapter(OnUserClickListener listener) {
            this.listener = listener;
        }

        public void setUsers(List<User> list) {
            users.clear();
            if (list != null) users.addAll(list);
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
            User u = users.get(position);
            holder.tvUserId.setText(u.getId());
            holder.tvUserName.setText(u.getDisplayName() != null && !u.getDisplayName().isEmpty() ? u.getDisplayName() : "Anonymous User");
            if (holder.tvAvatar != null) {
                String initial = u.getDisplayName() != null && !u.getDisplayName().isEmpty()
                        ? u.getDisplayName().substring(0, 1).toUpperCase()
                        : (u.getId() != null ? u.getId().substring(Math.max(0, u.getId().length() - 2)).toUpperCase() : "U");
                holder.tvAvatar.setText(initial);
            }
            holder.btnStartChat.setOnClickListener(v -> listener.onUserClick(u));
            holder.itemView.setOnClickListener(v -> listener.onUserClick(u));
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvAvatar;
            final TextView tvUserId;
            final TextView tvUserName;
            final MaterialButton btnStartChat;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvAvatar = itemView.findViewById(R.id.tv_search_avatar);
                tvUserId = itemView.findViewById(R.id.tv_search_user_id);
                tvUserName = itemView.findViewById(R.id.tv_search_user_name);
                btnStartChat = itemView.findViewById(R.id.btn_start_chat);
            }
        }
    }
}
