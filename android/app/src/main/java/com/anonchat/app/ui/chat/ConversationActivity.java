package com.anonchat.app.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.anonchat.app.crypto.CryptoManager;
import com.anonchat.app.data.api.SocketService;
import com.anonchat.app.data.model.Message;
import com.anonchat.app.data.model.SubscriptionStatus;
import com.anonchat.app.data.model.User;
import com.anonchat.app.ui.base.BaseSecureActivity;
import com.anonchat.app.ui.settings.LicenseActivationActivity;
import com.anonchat.app.utils.Constants;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ConversationActivity extends BaseSecureActivity {
    private String conversationId;
    private String recipientId;
    private String recipientName;
    private String recipientPublicKey;

    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private EditText etMessageInput;
    private FloatingActionButton btnSendMessage;
    private LinearLayout containerTrialExpired;
    private LinearLayout containerInputBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        conversationId = getIntent().getStringExtra(Constants.EXTRA_CONVERSATION_ID);
        recipientId = getIntent().getStringExtra(Constants.EXTRA_RECIPIENT_ID);
        recipientName = getIntent().getStringExtra(Constants.EXTRA_RECIPIENT_NAME);
        recipientPublicKey = getIntent().getStringExtra(Constants.EXTRA_RECIPIENT_PUBLIC_KEY);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_chat);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(recipientName != null ? recipientName : recipientId);
            getSupportActionBar().setSubtitle(recipientId);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvMessages = findViewById(R.id.rv_messages);
        etMessageInput = findViewById(R.id.et_message_input);
        btnSendMessage = findViewById(R.id.btn_send_message);
        containerTrialExpired = findViewById(R.id.container_trial_expired);
        containerInputBar = findViewById(R.id.container_input_bar);
        LinearLayout bannerSecurity = findViewById(R.id.banner_security_disclosure);
        MaterialButton btnActivate = findViewById(R.id.btn_activate_from_chat);

        adapter = new MessageAdapter(getPrefHelper().getUserId());
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);

        bannerSecurity.setOnClickListener(v -> showSecurityDisclosureDialog());
        btnActivate.setOnClickListener(v -> startActivity(new Intent(this, LicenseActivationActivity.class)));
        btnSendMessage.setOnClickListener(v -> sendMessage());

        // Check if recipient public key is already available or needs lookup
        if (recipientPublicKey == null && recipientId != null) {
            lookupRecipientKey();
        }

        checkSubscriptionStatus();
        loadMessageHistory();

        // Join conversation room for real-time messages
        SocketService socketService = ((AnonChatApp) getApplication()).getSocketService();
        socketService.joinConversation(conversationId);
        socketService.emitRead(conversationId, recipientId);

        setupSocketListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SocketService socketService = ((AnonChatApp) getApplication()).getSocketService();
        socketService.leaveConversation(conversationId);
    }

    private void setupSocketListener() {
        ((AnonChatApp) getApplication()).getSocketService().setMessageListener(new SocketService.MessageListener() {
            @Override
            public void onNewMessage(Message message) {
                if (conversationId != null && conversationId.equals(message.getConversationId())) {
                    decryptAndAddMessage(message);
                    ((AnonChatApp) getApplication()).getSocketService().emitRead(conversationId, recipientId);
                }
            }

            @Override
            public void onMessageStatusUpdate(String messageId, String status) {
                if (messageId != null) {
                    adapter.updateMessageStatus(messageId, status);
                } else if ("read".equals(status)) {
                    adapter.markAllRead();
                }
            }

            @Override
            public void onSubscriptionExpired() {
                containerTrialExpired.setVisibility(View.VISIBLE);
                containerInputBar.setVisibility(View.GONE);
            }
        });
    }

    private void checkSubscriptionStatus() {
        getApiService().getSubscriptionStatus().enqueue(new Callback<SubscriptionStatus>() {
            @Override
            public void onResponse(Call<SubscriptionStatus> call, Response<SubscriptionStatus> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SubscriptionStatus status = response.body();
                    if (!status.canChat()) {
                        containerTrialExpired.setVisibility(View.VISIBLE);
                        containerInputBar.setVisibility(View.GONE);
                    } else {
                        containerTrialExpired.setVisibility(View.GONE);
                        containerInputBar.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onFailure(Call<SubscriptionStatus> call, Throwable t) {
                // Default to allowing input if network check fails
            }
        });
    }

    private void lookupRecipientKey() {
        getApiService().searchUser(recipientId).enqueue(new Callback<Map<String, List<User>>>() {
            @Override
            public void onResponse(Call<Map<String, List<User>>> call, Response<Map<String, List<User>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<User> list = response.body().get("users");
                    if (list != null) {
                        for (User u : list) {
                            if (recipientId.equals(u.getId())) {
                                recipientPublicKey = u.getPublicKey();
                                break;
                            }
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, List<User>>> call, Throwable t) {}
        });
    }

    private void loadMessageHistory() {
        getApiService().getMessages(conversationId, 50).enqueue(new Callback<Map<String, List<Message>>>() {
            @Override
            public void onResponse(Call<Map<String, List<Message>>> call, Response<Map<String, List<Message>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Message> list = response.body().get("messages");
                    if (list != null) {
                        for (Message m : list) {
                            decryptMessageLocally(m);
                        }
                        adapter.setMessages(list);
                        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, List<Message>>> call, Throwable t) {
                Toast.makeText(ConversationActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendMessage() {
        String plaintext = etMessageInput.getText().toString().trim();
        if (plaintext.isEmpty()) return;

        if (recipientPublicKey == null) {
            Toast.makeText(this, "Recipient public encryption key not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // E2EE: Encrypt plaintext on device
            CryptoManager crypto = ((AnonChatApp) getApplication()).getCryptoManager();
            CryptoManager.EncryptedPayload payload = crypto.encryptMessage(plaintext, recipientPublicKey);

            etMessageInput.setText("");

            Map<String, Object> body = new HashMap<>();
            body.put("recipientId", recipientId);
            body.put("encryptedContent", payload.ciphertextBase64);
            body.put("iv", payload.ivBase64);
            body.put("authTag", payload.authTagBase64);

            getApiService().sendMessage(conversationId, body).enqueue(new Callback<Map<String, Message>>() {
                @Override
                public void onResponse(Call<Map<String, Message>> call, Response<Map<String, Message>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Message sentMsg = response.body().get("message");
                        if (sentMsg != null) {
                            sentMsg.setPlaintext(plaintext);
                            adapter.addMessage(sentMsg);
                            rvMessages.scrollToPosition(adapter.getItemCount() - 1);
                        }
                    } else if (response.code() == 403) {
                        Toast.makeText(ConversationActivity.this, R.string.trial_expired_notice, Toast.LENGTH_LONG).show();
                        containerTrialExpired.setVisibility(View.VISIBLE);
                        containerInputBar.setVisibility(View.GONE);
                    } else {
                        Toast.makeText(ConversationActivity.this, "Failed to send message", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Message>> call, Throwable t) {
                    Toast.makeText(ConversationActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "Encryption error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void decryptAndAddMessage(Message msg) {
        decryptMessageLocally(msg);
        adapter.addMessage(msg);
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    private void decryptMessageLocally(Message msg) {
        String myUserId = getPrefHelper().getUserId();
        if (myUserId.equals(msg.getSenderId())) {
            // Outgoing message already known or local preview
            if (msg.getPlaintext() == null) {
                msg.setPlaintext("🔒 [Encrypted Outgoing Message]");
            }
            return;
        }

        try {
            if (recipientPublicKey != null) {
                CryptoManager crypto = ((AnonChatApp) getApplication()).getCryptoManager();
                String decrypted = crypto.decryptMessage(
                    msg.getEncryptedContent(),
                    msg.getIv(),
                    msg.getAuthTag(),
                    recipientPublicKey
                );
                msg.setPlaintext(decrypted);
            } else {
                msg.setPlaintext("🔒 [Encrypted Payload - Awaiting Key]");
            }
        } catch (Exception e) {
            msg.setPlaintext("🔒 [Decryption Error]");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_conversation, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_security_info) {
            showSecurityDisclosureDialog();
            return true;
        } else if (id == R.id.action_block_user) {
            confirmBlockUser();
            return true;
        } else if (id == R.id.action_report_user) {
            confirmReportUser();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmBlockUser() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.block_user)
            .setMessage("Block communications from " + recipientId + "? They will no longer be able to message you.")
            .setPositiveButton("Block", (dialog, which) -> {
                Map<String, String> body = new HashMap<>();
                body.put("blockedId", recipientId);
                getApiService().blockUser(body).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        Toast.makeText(ConversationActivity.this, "User blocked", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(ConversationActivity.this, "Error blocking user", Toast.LENGTH_SHORT).show();
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmReportUser() {
        final EditText etReason = new EditText(this);
        etReason.setHint("Reason for report (e.g. harassment, abuse)");

        new AlertDialog.Builder(this)
            .setTitle(R.string.report_user)
            .setView(etReason)
            .setPositiveButton("Submit Report", (dialog, which) -> {
                String reason = etReason.getText().toString().trim();
                Map<String, String> body = new HashMap<>();
                body.put("reportedId", recipientId);
                body.put("reason", reason);
                getApiService().reportUser(body).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        Toast.makeText(ConversationActivity.this, "Report submitted securely to administrator", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(ConversationActivity.this, "Failed to submit report", Toast.LENGTH_SHORT).show();
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
