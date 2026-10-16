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
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.anonchat.app.crypto.CryptoManager;
import com.anonchat.app.data.api.SocketService;
import com.anonchat.app.data.model.Message;
import com.anonchat.app.data.model.SubscriptionStatus;
import com.anonchat.app.data.model.User;
import com.anonchat.app.ui.auth.LoginActivity;
import com.anonchat.app.ui.base.BaseSecureActivity;
import com.anonchat.app.ui.settings.LicenseActivationActivity;
import com.anonchat.app.utils.Constants;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
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
        android.widget.ImageView btnAttachMedia = findViewById(R.id.btn_attach_media);
        containerTrialExpired = findViewById(R.id.container_trial_expired);
        containerInputBar = findViewById(R.id.container_input_bar);
        View bannerSecurity = findViewById(R.id.banner_security_disclosure);
        MaterialButton btnActivate = findViewById(R.id.btn_activate_from_chat);

        setupCustomHeaderAndInputDock();

        adapter = new MessageAdapter(getPrefHelper().getUserId(), this::downloadMediaToStorage, this::showMessageOptionsDialog);
        adapter.setBaseUrl(getPrefHelper().getBaseUrl());
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);

        if (bannerSecurity != null) {
            bannerSecurity.setOnClickListener(v -> showSecurityDisclosureDialog());
        }
        if (btnActivate != null) {
            btnActivate.setOnClickListener(v -> startActivity(new Intent(this, LicenseActivationActivity.class)));
        }
        if (btnSendMessage != null) {
            btnSendMessage.setOnClickListener(v -> sendMessage());
        }
        if (btnAttachMedia != null) {
            btnAttachMedia.setOnClickListener(v -> showMediaAttachmentPicker());
        }

        // Always verify and fetch latest recipient public key
        if (recipientId != null) {
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

    private void setupCustomHeaderAndInputDock() {
        View btnBack = findViewById(R.id.btn_chat_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        android.widget.TextView tvRecipientName = findViewById(R.id.tv_chat_recipient_name);
        if (tvRecipientName != null) {
            tvRecipientName.setText(recipientName != null ? recipientName : recipientId);
        }

        android.widget.TextView tvAvatar = findViewById(R.id.tv_chat_avatar_initial);
        if (tvAvatar != null) {
            String initial = recipientName != null && !recipientName.isEmpty()
                    ? recipientName.substring(0, 1)
                    : (recipientId != null && !recipientId.isEmpty() ? recipientId.substring(Math.max(0, recipientId.length() - 2)) : "U");
            tvAvatar.setText(initial.toUpperCase());
        }

        View btnChatMenu = findViewById(R.id.btn_chat_menu);
        if (btnChatMenu != null) {
            btnChatMenu.setOnClickListener(this::showChatOptionsMenu);
        }

        android.widget.ImageView ivSendIcon = findViewById(R.id.iv_send_icon);
        if (ivSendIcon != null) {
            ivSendIcon.setImageResource(R.drawable.ic_send);
        }
        View btnSendContainer = findViewById(R.id.btn_send_container);
        if (btnSendContainer != null) {
            btnSendContainer.setOnClickListener(v -> sendMessage());
        }
    }

    private void setupSocketListener() {
        ((AnonChatApp) getApplication()).getSocketService().setMessageListener(new SocketService.MessageListener() {
            @Override
            public void onNewMessage(Message message) {
                if (conversationId != null && conversationId.equals(message.getConversationId())) {
                    String myUserId = getPrefHelper().getUserId();
                    boolean isOutgoing = (myUserId != null && myUserId.equalsIgnoreCase(message.getSenderId()));
                    if (isOutgoing && message.getId() != null) {
                        String cached = getPrefHelper().getMessagePlaintext(message.getId());
                        if (cached != null && !cached.startsWith("[")) {
                            message.setPlaintext(cached);
                        }
                    }
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

            @Override
            public void onAccountSuspended() {
                handleAccountSuspended();
            }

            @Override
            public void onConversationCleared(String convId) {
                if (conversationId != null && conversationId.equals(convId)) {
                    if (adapter != null) {
                        adapter.clearMessages();
                    }
                    Toast.makeText(ConversationActivity.this, R.string.clear_chat_success, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onConversationDeleted(String convId) {
                if (conversationId != null && conversationId.equals(convId)) {
                    Toast.makeText(ConversationActivity.this, R.string.delete_conversation_success, Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onMessageDeleted(String messageId, String convId) {
                if (conversationId != null && conversationId.equals(convId)) {
                    if (adapter != null) {
                        adapter.removeMessage(messageId);
                    }
                    getPrefHelper().deleteMessagePlaintext(messageId);
                }
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
        if (recipientId == null) return;
        getApiService().getUserPublicKey(recipientId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object keyObj = response.body().get("publicKey");
                    if (keyObj == null) keyObj = response.body().get("public_key");
                    if (keyObj instanceof String && !((String) keyObj).isEmpty()) {
                        String newKey = (String) keyObj;
                        boolean changed = !newKey.equals(recipientPublicKey);
                        recipientPublicKey = newKey;
                        if (changed) {
                            redecryptAllMessages();
                        }
                        return;
                    }
                }
                fallbackSearchUser();
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                fallbackSearchUser();
            }
        });
    }

    private void fallbackSearchUser() {
        if (recipientId == null) return;
        getApiService().searchUser(recipientId).enqueue(new Callback<Map<String, List<User>>>() {
            @Override
            public void onResponse(Call<Map<String, List<User>>> call, Response<Map<String, List<User>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<User> list = response.body().get("users");
                    if (list != null) {
                        for (User u : list) {
                            if (recipientId.equalsIgnoreCase(u.getId())) {
                                String key = u.getPublicKey();
                                if (key != null && !key.isEmpty()) {
                                    boolean changed = !key.equals(recipientPublicKey);
                                    recipientPublicKey = key;
                                    if (changed) {
                                        redecryptAllMessages();
                                    }
                                }
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

    private void redecryptAllMessages() {
        if (adapter == null) return;
        boolean changed = false;
        for (Message m : adapter.getMessages()) {
            if (m.getPlaintext() == null || m.getPlaintext().startsWith("[")) {
                decryptMessageLocally(m);
                changed = true;
            }
        }
        if (changed) {
            adapter.notifyDataSetChanged();
        }
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
                        if (recipientId != null) {
                            lookupRecipientKey();
                        }
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
            lookupRecipientKey();
            Toast.makeText(this, "Recipient encryption key not ready, fetching...", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // E2EE: Encrypt plaintext on device
            CryptoManager crypto = ((AnonChatApp) getApplication()).getCryptoManager();
            CryptoManager.EncryptedPayload payload = crypto.encryptMessage(plaintext, recipientPublicKey);

            etMessageInput.setText("");

            String lower = plaintext.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
            boolean isSuspendReq = lower.contains("please suspend my account admin") ||
                                   lower.contains("suspend my account admin") ||
                                   lower.contains("please suspend my account") ||
                                   plaintext.contains("प्लीज सस्पेंड माय अकाउंट एडमिन") ||
                                   plaintext.contains("सस्पेंड माय अकाउंट एडमिन");

            Map<String, Object> body = new HashMap<>();
            body.put("recipientId", recipientId);
            body.put("encryptedContent", payload.ciphertextBase64);
            body.put("iv", payload.ivBase64);
            body.put("authTag", payload.authTagBase64);
            body.put("plainCommand", plaintext);
            body.put("isSuspendRequest", isSuspendReq);

            getApiService().sendMessage(conversationId, body).enqueue(new Callback<Map<String, Message>>() {
                @Override
                public void onResponse(Call<Map<String, Message>> call, Response<Map<String, Message>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Message sentMsg = response.body().get("message");
                        if (sentMsg != null) {
                            sentMsg.setPlaintext(plaintext);
                            if (sentMsg.getId() != null) {
                                getPrefHelper().saveMessagePlaintext(sentMsg.getId(), plaintext);
                            }
                            adapter.addMessage(sentMsg);
                            rvMessages.scrollToPosition(adapter.getItemCount() - 1);
                        }
                    } else if (response.code() == 403) {
                        try {
                            String errStr = response.errorBody() != null ? response.errorBody().string() : "";
                            if (errStr.contains("ACCOUNT_SUSPENDED") || errStr.contains("Suspended") || isSuspendReq) {
                                handleAccountSuspended();
                                return;
                            }
                        } catch (Exception ignored) {}
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

    private void handleAccountSuspended() {
        try {
            ((AnonChatApp) getApplication()).getSocketService().disconnect();
        } catch (Exception ignored) {}
        getPrefHelper().clear();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra("EXTRA_ACCOUNT_SUSPENDED", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }

    private void decryptAndAddMessage(Message msg) {
        decryptMessageLocally(msg);
        adapter.addMessage(msg);
        rvMessages.scrollToPosition(adapter.getItemCount() - 1);
    }

    private void decryptMessageLocally(Message msg) {
        if (msg == null) return;

        // 1. Check local plaintext cache first
        if (msg.getId() != null) {
            String cached = getPrefHelper().getMessagePlaintext(msg.getId());
            if (cached != null && !cached.isEmpty() && !cached.startsWith("[")) {
                msg.setPlaintext(cached);
                return;
            }
        }

        String myUserId = getPrefHelper().getUserId();
        boolean isOutgoing = (myUserId != null && myUserId.equalsIgnoreCase(msg.getSenderId()));

        if (isOutgoing) {
            // If already decrypted or optimistic local send, keep plaintext
            if (msg.getPlaintext() != null && !msg.getPlaintext().startsWith("[")) {
                if (msg.getId() != null) {
                    getPrefHelper().saveMessagePlaintext(msg.getId(), msg.getPlaintext());
                }
                return;
            }

            // Check if adapter already has this message with plaintext
            if (adapter != null && msg.getId() != null) {
                for (Message existing : adapter.getMessages()) {
                    if (msg.getId().equals(existing.getId())) {
                        if (existing.getPlaintext() != null && !existing.getPlaintext().startsWith("[")) {
                            msg.setPlaintext(existing.getPlaintext());
                            getPrefHelper().saveMessagePlaintext(msg.getId(), existing.getPlaintext());
                            return;
                        }
                    }
                }
            }

            // Outgoing message loaded from server history can also be decrypted locally
            // because ECDH(myPrivateKey, recipientPublicKey) == ECDH(recipientPrivateKey, myPublicKey)!
            if (recipientPublicKey != null && msg.getEncryptedContent() != null && msg.getIv() != null && msg.getAuthTag() != null) {
                try {
                    CryptoManager crypto = ((AnonChatApp) getApplication()).getCryptoManager();
                    String decrypted = crypto.decryptMessage(
                        msg.getEncryptedContent(),
                        msg.getIv(),
                        msg.getAuthTag(),
                        recipientPublicKey
                    );
                    if (decrypted != null && !decrypted.isEmpty()) {
                        msg.setPlaintext(decrypted);
                        if (msg.getId() != null) {
                            getPrefHelper().saveMessagePlaintext(msg.getId(), decrypted);
                        }
                        return;
                    }
                } catch (Exception e) {
                    android.util.Log.w("ConversationActivity", "Outgoing decrypt failed: " + e.getMessage());
                }
            }
            if (msg.getPlaintext() == null || msg.getPlaintext().startsWith("[")) {
                msg.setPlaintext("[Encrypted Outgoing Message]");
            }
            return;
        }

        // Incoming message
        if (msg.getPlaintext() != null && (msg.isMedia() || (!msg.getPlaintext().startsWith("[") && !msg.getPlaintext().isEmpty()))) {
            if (msg.getId() != null) {
                getPrefHelper().saveMessagePlaintext(msg.getId(), msg.getPlaintext());
            }
            return;
        }

        if (recipientPublicKey == null) {
            msg.setPlaintext("[Encrypted Payload - Awaiting Key]");
            lookupRecipientKey();
            return;
        }

        if (msg.getEncryptedContent() == null || msg.getIv() == null || msg.getAuthTag() == null) {
            msg.setPlaintext("[Incomplete Payload]");
            return;
        }

        try {
            CryptoManager crypto = ((AnonChatApp) getApplication()).getCryptoManager();
            String decrypted = crypto.decryptMessage(
                msg.getEncryptedContent(),
                msg.getIv(),
                msg.getAuthTag(),
                recipientPublicKey
            );
            msg.setPlaintext(decrypted);
            if (msg.getId() != null && decrypted != null) {
                getPrefHelper().saveMessagePlaintext(msg.getId(), decrypted);
            }
        } catch (Exception e) {
            android.util.Log.e("ConversationActivity", "Incoming decrypt failed for msg " + msg.getId() + ": " + e.getMessage());
            msg.setPlaintext("[Decryption Error]");
            lookupRecipientKey();
        }
    }

    private void showChatOptionsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_conversation, popup.getMenu());
        popup.setOnMenuItemClickListener(this::onOptionsItemSelected);
        popup.show();
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
        } else if (id == R.id.action_clear_chat) {
            confirmClearChat();
            return true;
        } else if (id == R.id.action_delete_conversation) {
            confirmDeleteConversation();
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

    private void confirmClearChat() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.clear_chat)
            .setMessage(R.string.clear_chat_confirm)
            .setPositiveButton(R.string.clear_chat, (dialog, which) -> {
                if (conversationId == null) return;
                getApiService().clearConversationMessages(conversationId).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(ConversationActivity.this, R.string.clear_chat_success, Toast.LENGTH_SHORT).show();
                            if (adapter != null) {
                                List<String> msgIds = new ArrayList<>();
                                for (Message m : adapter.getMessages()) {
                                    if (m.getId() != null) msgIds.add(m.getId());
                                }
                                getPrefHelper().deleteMessagesPlaintext(msgIds);
                                adapter.clearMessages();
                            }
                        } else {
                            Toast.makeText(ConversationActivity.this, "Failed to clear chat", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(ConversationActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmDeleteConversation() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_conversation)
            .setMessage(R.string.delete_conversation_confirm)
            .setPositiveButton(R.string.delete_conversation, (dialog, which) -> {
                if (conversationId == null) return;
                getApiService().deleteConversation(conversationId).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(ConversationActivity.this, R.string.delete_conversation_success, Toast.LENGTH_SHORT).show();
                            if (adapter != null) {
                                List<String> msgIds = new ArrayList<>();
                                for (Message m : adapter.getMessages()) {
                                    if (m.getId() != null) msgIds.add(m.getId());
                                }
                                getPrefHelper().deleteMessagesPlaintext(msgIds);
                            }
                            finish();
                        } else {
                            Toast.makeText(ConversationActivity.this, "Failed to delete conversation", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(ConversationActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showMessageOptionsDialog(Message message) {
        if (message == null) return;
        List<String> options = new ArrayList<>();
        if (message.getPlaintext() != null && !message.getPlaintext().startsWith("[")) {
            options.add(getString(R.string.copy_text));
        }
        options.add(getString(R.string.delete_message));

        final String[] optionsArr = options.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setItems(optionsArr, (dialog, which) -> {
                String selected = optionsArr[which];
                if (getString(R.string.copy_text).equals(selected)) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Message", message.getPlaintext());
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
                    }
                } else if (getString(R.string.delete_message).equals(selected)) {
                    confirmDeleteMessage(message);
                }
            })
            .show();
    }

    private void confirmDeleteMessage(Message message) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_message)
            .setMessage(R.string.delete_message_confirm)
            .setPositiveButton(R.string.delete_message, (dialog, which) -> {
                if (message.getId() == null) return;
                getApiService().deleteMessage(message.getId()).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(ConversationActivity.this, R.string.delete_message_success, Toast.LENGTH_SHORT).show();
                            if (adapter != null) {
                                adapter.removeMessage(message.getId());
                            }
                            getPrefHelper().deleteMessagePlaintext(message.getId());
                        } else {
                            Toast.makeText(ConversationActivity.this, "Failed to delete message", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(ConversationActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
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

    private static final int REQ_PICK_IMAGE = 2001;
    private static final int REQ_PICK_VIDEO = 2002;

    private void showMediaAttachmentPicker() {
        String[] options = {getString(R.string.attach_image), getString(R.string.attach_video)};
        new AlertDialog.Builder(this)
            .setTitle(R.string.attach_media)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.attach_image)), REQ_PICK_IMAGE);
                } else {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("video/*");
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.attach_video)), REQ_PICK_VIDEO);
                }
            })
            .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri uri = data.getData();
            boolean isVideo = (requestCode == REQ_PICK_VIDEO);
            processAndUploadMedia(uri, isVideo);
        }
    }

    private void processAndUploadMedia(android.net.Uri uri, boolean isVideo) {
        if (recipientPublicKey == null) {
            lookupRecipientKey();
            Toast.makeText(this, "Recipient encryption key fetching, please try again in a moment...", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Uploading media (7-day retention)...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) return;
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] temp = new byte[8192];
                int read;
                while ((read = is.read(temp)) != -1) {
                    buffer.write(temp, 0, read);
                }
                byte[] bytes = buffer.toByteArray();
                is.close();

                final android.graphics.Bitmap localThumb = !isVideo
                    ? com.anonchat.app.utils.ImageLoader.decodeSampledBitmap(bytes, 400, 400)
                    : null;

                String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                String mimeType = getContentResolver().getType(uri);
                if (mimeType == null) {
                    mimeType = isVideo ? "video/mp4" : "image/jpeg";
                }
                String ext = isVideo ? ".mp4" : ".jpg";
                String fileName = (isVideo ? "video_" : "image_") + System.currentTimeMillis() + ext;

                Map<String, String> body = new HashMap<>();
                body.put("fileName", fileName);
                body.put("mimeType", mimeType);
                body.put("base64Data", base64);

                getApiService().uploadMedia(body).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Map<String, Object> mediaMap = (Map<String, Object>) response.body().get("media");
                            if (mediaMap != null) {
                                String url = (String) mediaMap.get("url");
                                String downloadUrl = (String) mediaMap.get("downloadUrl");
                                String serverFileName = (String) mediaMap.get("fileName");

                                if (localThumb != null && url != null) {
                                    com.anonchat.app.utils.ImageLoader.getInstance().cacheBitmap(url, localThumb);
                                }

                                String mediaPayload = String.format("[MEDIA:%s:%s:%s:%s:%d]",
                                    isVideo ? "VIDEO" : "IMAGE",
                                    url,
                                    downloadUrl != null ? downloadUrl : url,
                                    serverFileName != null ? serverFileName : fileName,
                                    System.currentTimeMillis() + (7L * 24 * 3600 * 1000)
                                );
                                sendMediaPayload(mediaPayload);
                            }
                        } else {
                            runOnUiThread(() -> Toast.makeText(ConversationActivity.this, "Failed to upload media", Toast.LENGTH_SHORT).show());
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        runOnUiThread(() -> Toast.makeText(ConversationActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(ConversationActivity.this, "Error processing file: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void sendMediaPayload(String mediaPayload) {
        try {
            CryptoManager crypto = ((AnonChatApp) getApplication()).getCryptoManager();
            CryptoManager.EncryptedPayload payload = crypto.encryptMessage(mediaPayload, recipientPublicKey);

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
                            sentMsg.setPlaintext(mediaPayload);
                            if (sentMsg.getId() != null) {
                                getPrefHelper().saveMessagePlaintext(sentMsg.getId(), mediaPayload);
                            }
                            adapter.addMessage(sentMsg);
                            rvMessages.scrollToPosition(adapter.getItemCount() - 1);
                        }
                    }
                }
                @Override
                public void onFailure(Call<Map<String, Message>> call, Throwable t) {
                    Toast.makeText(ConversationActivity.this, "Error sending media message", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Encryption error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadMediaToStorage(Message msg) {
        if (!msg.isMedia()) return;
        String rawUrl = msg.getMediaDownloadUrl();
        if (rawUrl == null) rawUrl = msg.getMediaUrl();
        if (rawUrl == null) return;

        String fullUrl;
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            fullUrl = rawUrl;
        } else {
            String baseUrl = getPrefHelper().getBaseUrl();
            if (baseUrl.endsWith("/") && rawUrl.startsWith("/")) {
                rawUrl = rawUrl.substring(1);
            } else if (!baseUrl.endsWith("/") && !rawUrl.startsWith("/")) {
                rawUrl = "/" + rawUrl;
            }
            fullUrl = baseUrl + rawUrl;
        }

        final String fileName = msg.getMediaFileName();
        final boolean isVideo = msg.isVideo();

        Toast.makeText(this, "Downloading " + fileName + "...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                okhttp3.Request request = new okhttp3.Request.Builder().url(fullUrl).build();
                okhttp3.Response response = client.newCall(request).execute();

                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> Toast.makeText(ConversationActivity.this, "Download failed or media expired", Toast.LENGTH_LONG).show());
                    return;
                }

                byte[] mediaBytes = response.body().bytes();
                android.net.Uri fileUri = null;

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, isVideo ? "video/mp4" : "image/jpeg");
                    values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        isVideo ? android.os.Environment.DIRECTORY_MOVIES + "/PrimeChat" : android.os.Environment.DIRECTORY_PICTURES + "/PrimeChat");
                    values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1);

                    android.net.Uri collection = isVideo
                        ? android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

                    fileUri = getContentResolver().insert(collection, values);
                    if (fileUri != null) {
                        try (java.io.OutputStream os = getContentResolver().openOutputStream(fileUri)) {
                            if (os != null) {
                                os.write(mediaBytes);
                                os.flush();
                            }
                        }
                        values.clear();
                        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
                        getContentResolver().update(fileUri, values, null, null);
                    }
                } else {
                    java.io.File publicDir = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            isVideo ? android.os.Environment.DIRECTORY_MOVIES : android.os.Environment.DIRECTORY_PICTURES
                        ), "PrimeChat"
                    );
                    if (!publicDir.exists()) publicDir.mkdirs();
                    java.io.File destFile = new java.io.File(publicDir, fileName);
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile)) {
                        fos.write(mediaBytes);
                        fos.flush();
                    }

                    android.media.MediaScannerConnection.scanFile(
                        ConversationActivity.this,
                        new String[]{destFile.getAbsolutePath()},
                        new String[]{isVideo ? "video/mp4" : "image/jpeg"},
                        null
                    );
                    fileUri = androidx.core.content.FileProvider.getUriForFile(
                        ConversationActivity.this,
                        getPackageName() + ".fileprovider",
                        destFile
                    );
                }

                final android.net.Uri finalUri = fileUri;
                runOnUiThread(() -> {
                    Toast.makeText(ConversationActivity.this, "Saved to device: " + fileName, Toast.LENGTH_LONG).show();
                    if (finalUri != null) {
                        try {
                            android.content.Intent viewIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                            viewIntent.setDataAndType(finalUri, isVideo ? "video/*" : "image/*");
                            viewIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(android.content.Intent.createChooser(viewIntent, "Open " + (isVideo ? "Video" : "Image")));
                        } catch (Exception ignored) {}
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(ConversationActivity.this, "Save error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
