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

import java.util.ArrayList;
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

    private FloatingActionButton fabNewChat;

    private static boolean sBiometricAuthenticated = false;

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
        fabNewChat = findViewById(R.id.fab_new_chat);

        setupChatsList();
        setupNeumorphicControls();

        btnBannerActivate.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, LicenseActivationActivity.class));
        });

        // Initialize real-time socket connection
        ((AnonChatApp) getApplication()).getSocketService().connect();
        setupSocketListener();

        // Biometric Security Prompt
        checkBiometricAuth();
    }

    private List<Conversation> allConversations = new ArrayList<>();

    private void setupNeumorphicControls() {
        // Profile initial
        TextView tvHeaderAvatar = findViewById(R.id.tv_header_avatar_initial);
        String myId = prefHelper.getUserId();
        if (tvHeaderAvatar != null && myId != null && !myId.isEmpty()) {
            tvHeaderAvatar.setText(myId.substring(Math.max(0, myId.length() - 2)).toUpperCase());
        }

        View btnUserProfile = findViewById(R.id.btn_user_profile);
        if (btnUserProfile != null) {
            btnUserProfile.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        }

        View btnHeaderAdd = findViewById(R.id.btn_header_add_chat);
        if (btnHeaderAdd != null) {
            btnHeaderAdd.setOnClickListener(v -> startActivity(new Intent(this, com.anonchat.app.ui.main.SearchUserActivity.class)));
        }

        // Search bar filter
        android.widget.EditText etSearch = findViewById(R.id.et_search_chats);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterConversations(s != null ? s.toString() : "");
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // Bottom Dock tabs
        View tabChats = findViewById(R.id.tab_chats);
        if (tabChats != null) {
            tabChats.setOnClickListener(v -> {
                if (rvConversations != null) rvConversations.smoothScrollToPosition(0);
                loadConversations();
            });
        }

        View tabCamera = findViewById(R.id.tab_camera);
        if (tabCamera != null) {
            tabCamera.setOnClickListener(v -> {
                Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
                pickIntent.setType("image/* video/*");
                startActivity(Intent.createChooser(pickIntent, "Select Media to Share"));
            });
        }

        View tabScan = findViewById(R.id.tab_scan);
        if (tabScan != null) {
            tabScan.setOnClickListener(v -> startActivity(new Intent(this, com.anonchat.app.ui.main.SearchUserActivity.class)));
        }

        View tabSettings = findViewById(R.id.tab_settings);
        if (tabSettings != null) {
            tabSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        }
    }

    private void filterConversations(String query) {
        if (allConversations == null || adapter == null) return;
        if (query.trim().isEmpty()) {
            adapter.setConversations(allConversations);
            llEmptyState.setVisibility(allConversations.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
        List<Conversation> filtered = new ArrayList<>();
        for (Conversation c : allConversations) {
            String name = c.getRecipient() != null ? c.getRecipient().getDisplayName() : "";
            String id = c.getRecipient() != null ? c.getRecipient().getId() : "";
            if ((name != null && name.toLowerCase().contains(query.toLowerCase())) ||
                (id != null && id.toLowerCase().contains(query.toLowerCase()))) {
                filtered.add(c);
            }
        }
        adapter.setConversations(filtered);
        llEmptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void checkBiometricAuth() {
        if (!sBiometricAuthenticated && prefHelper.isBiometricEnabled() && com.anonchat.app.utils.BiometricHelper.isBiometricAvailable(this)) {
            com.anonchat.app.utils.BiometricHelper.showBiometricPrompt(this, new com.anonchat.app.utils.BiometricHelper.AuthListener() {
                @Override
                public void onAuthenticated() {
                    sBiometricAuthenticated = true;
                    loadSubscriptionStatus();
                    loadConversations();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(MainActivity.this, "Authentication required: " + error, Toast.LENGTH_LONG).show();
                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.biometric_title)
                        .setMessage("Biometric authentication is required to access AnonChat communications.")
                        .setPositiveButton("Retry", (dialog, which) -> checkBiometricAuth())
                        .setNegativeButton("Exit", (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
                }
            });
        } else {
            sBiometricAuthenticated = true;
            loadSubscriptionStatus();
            loadConversations();
        }
    }

    private void setupChatsList() {
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

            @Override
            public void onAccountSuspended() {
                handleAccountSuspended();
            }
        });
    }

    private void handleAccountSuspended() {
        try {
            ((AnonChatApp) getApplication()).getSocketService().disconnect();
        } catch (Exception ignored) {}
        prefHelper.clear();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("EXTRA_ACCOUNT_SUSPENDED", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
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
                    allConversations = list != null ? list : new ArrayList<>();
                    adapter.setConversations(allConversations);
                    llEmptyState.setVisibility(allConversations.isEmpty() ? View.VISIBLE : View.GONE);
                } else if (response.code() == 403) {
                    try {
                        String errStr = response.errorBody() != null ? response.errorBody().string() : "";
                        if (errStr.contains("ACCOUNT_SUSPENDED") || errStr.contains("Suspended")) {
                            handleAccountSuspended();
                        }
                    } catch (Exception ignored) {}
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
