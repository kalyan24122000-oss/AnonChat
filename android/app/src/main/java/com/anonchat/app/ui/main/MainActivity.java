package com.anonchat.app.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.anonchat.app.data.api.SocketService;
import com.anonchat.app.data.model.Conversation;
import com.anonchat.app.data.model.Message;
import com.anonchat.app.data.model.SubscriptionStatus;
import com.anonchat.app.ui.auth.LoginActivity;
import com.anonchat.app.ui.chat.ConversationActivity;
import com.anonchat.app.ui.settings.LicenseActivationActivity;
import com.anonchat.app.ui.settings.SettingsActivity;
import com.anonchat.app.utils.Constants;
import com.anonchat.app.utils.PreferenceHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private PreferenceHelper prefHelper;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvConversations;
    private ConversationAdapter adapter;
    private LinearLayout llEmptyState;
    private LinearLayout bannerSubscription;
    private TextView tvSubscriptionStatus;
    private MaterialButton btnBannerActivate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefHelper = ((AnonChatApp) getApplication()).getPreferenceHelper();

        if (!prefHelper.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
            getSupportActionBar().setSubtitle(prefHelper.getUserId());
        }

        swipeRefresh = findViewById(R.id.swipe_refresh);
        rvConversations = findViewById(R.id.rv_conversations);
        llEmptyState = findViewById(R.id.ll_empty_state);
        bannerSubscription = findViewById(R.id.banner_subscription);
        tvSubscriptionStatus = findViewById(R.id.tv_subscription_status);
        btnBannerActivate = findViewById(R.id.btn_banner_activate);
        FloatingActionButton fabNewChat = findViewById(R.id.fab_new_chat);

        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConversationAdapter(conversation -> {
            Intent intent = new Intent(MainActivity.this, ConversationActivity.class);
            intent.putExtra(Constants.EXTRA_CONVERSATION_ID, conversation.getId());
            if (conversation.getRecipient() != null) {
                intent.putExtra(Constants.EXTRA_RECIPIENT_ID, conversation.getRecipient().getId());
                intent.putExtra(Constants.EXTRA_RECIPIENT_NAME, conversation.getRecipient().getDisplayName());
                intent.putExtra(Constants.EXTRA_RECIPIENT_PUBLIC_KEY, conversation.getRecipient().getPublicKey());
            }
            startActivity(intent);
        });
        rvConversations.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> {
            loadSubscriptionStatus();
            loadConversations();
        });

        fabNewChat.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SearchUserActivity.class));
        });

        btnBannerActivate.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, LicenseActivationActivity.class));
        });

        // Initialize real-time socket connection
        ((AnonChatApp) getApplication()).getSocketService().connect();
        setupSocketListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSubscriptionStatus();
        loadConversations();
    }

    private void setupSocketListener() {
        ((AnonChatApp) getApplication()).getSocketService().setMessageListener(new SocketService.MessageListener() {
            @Override
            public void onNewMessage(Message message) {
                // Refresh conversations on new message arrival
                loadConversations();
            }

            @Override
            public void onMessageStatusUpdate(String messageId, String status) {
                loadConversations();
            }

            @Override
            public void onSubscriptionExpired() {
                loadSubscriptionStatus();
            }
        });
    }

    private void loadSubscriptionStatus() {
        ((AnonChatApp) getApplication()).getApiService().getSubscriptionStatus().enqueue(new Callback<SubscriptionStatus>() {
            @Override
            public void onResponse(Call<SubscriptionStatus> call, Response<SubscriptionStatus> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SubscriptionStatus status = response.body();
                    updateSubscriptionBanner(status);
                }
            }

            @Override
            public void onFailure(Call<SubscriptionStatus> call, Throwable t) {
                // Keep default banner
            }
        });
    }

    private void updateSubscriptionBanner(SubscriptionStatus status) {
        if (!status.canChat()) {
            bannerSubscription.setBackgroundColor(getResources().getColor(R.color.badge_expired));
            tvSubscriptionStatus.setTextColor(getResources().getColor(R.color.danger));
            tvSubscriptionStatus.setText(R.string.trial_expired_notice);
            btnBannerActivate.setVisibility(View.VISIBLE);
        } else if (status.isTrial()) {
            bannerSubscription.setBackgroundColor(getResources().getColor(R.color.primary_light));
            tvSubscriptionStatus.setTextColor(getResources().getColor(R.color.text_primary));
            tvSubscriptionStatus.setText(getString(R.string.trial_remaining, status.getDaysRemaining()));
            btnBannerActivate.setVisibility(View.VISIBLE);
        } else if (status.isPremium()) {
            bannerSubscription.setBackgroundColor(getResources().getColor(R.color.primary_light));
            tvSubscriptionStatus.setTextColor(getResources().getColor(R.color.primary));
            tvSubscriptionStatus.setText(getString(R.string.premium_active, status.getDaysRemaining()));
            btnBannerActivate.setVisibility(View.GONE);
        } else {
            // Admin unlimited
            bannerSubscription.setVisibility(View.GONE);
        }
    }

    private void loadConversations() {
        swipeRefresh.setRefreshing(true);
        ((AnonChatApp) getApplication()).getApiService().getConversations().enqueue(new Callback<Map<String, List<Conversation>>>() {
            @Override
            public void onResponse(Call<Map<String, List<Conversation>>> call, Response<Map<String, List<Conversation>>> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<Conversation> list = response.body().get("conversations");
                    adapter.setConversations(list);
                    llEmptyState.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onFailure(Call<Map<String, List<Conversation>>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(MainActivity.this, "Failed to load chats: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            startActivity(new Intent(this, SearchUserActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
