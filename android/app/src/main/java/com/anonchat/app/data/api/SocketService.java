package com.anonchat.app.data.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.anonchat.app.data.model.Message;
import com.anonchat.app.utils.PreferenceHelper;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;

public class SocketService {
    private static final String TAG = "SocketService";
    private static SocketService instance;
    private Socket socket;
    private final PreferenceHelper preferenceHelper;
    private final Gson gson;
    private final Handler mainHandler;

    public interface MessageListener {
        void onNewMessage(Message message);
        void onMessageStatusUpdate(String messageId, String status);
        void onSubscriptionExpired();
        void onAccountSuspended();
    }

    private MessageListener messageListener;

    private SocketService(PreferenceHelper preferenceHelper) {
        this.preferenceHelper = preferenceHelper;
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized SocketService getInstance(PreferenceHelper preferenceHelper) {
        if (instance == null) {
            instance = new SocketService(preferenceHelper);
        }
        return instance;
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public synchronized void connect() {
        if (socket != null && socket.connected()) return;

        try {
            String token = preferenceHelper.getToken();
            if (token == null) return;

            String baseUrl = preferenceHelper.getBaseUrl();
            URI uri = URI.create(baseUrl);

            IO.Options options = IO.Options.builder()
                .setAuth(Collections.singletonMap("token", token))
                .setTransports(new String[]{"websocket", "polling"})
                .build();

            socket = IO.socket(uri, options);

            socket.on(Socket.EVENT_CONNECT, args -> {
                Log.d(TAG, "Connected to AnonChat Real-Time Gateway");
            });

            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                Log.w(TAG, "Socket connection error: " + (args.length > 0 ? args[0] : ""));
            });

            // Incoming direct message
            socket.on("new_message", args -> {
                if (args.length > 0) {
                    try {
                        String json = args[0].toString();
                        Message message = gson.fromJson(json, Message.class);
                        mainHandler.post(() -> {
                            if (messageListener != null) {
                                messageListener.onNewMessage(message);
                            }
                        });
                        // Send delivery confirmation
                        emitDelivered(message.getId(), message.getConversationId(), message.getSenderId());
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing incoming message", e);
                    }
                }
            });

            // Conversation active room message
            socket.on("conversation_message", args -> {
                if (args.length > 0) {
                    try {
                        String json = args[0].toString();
                        Message message = gson.fromJson(json, Message.class);
                        mainHandler.post(() -> {
                            if (messageListener != null) {
                                messageListener.onNewMessage(message);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing conversation message", e);
                    }
                }
            });

            // Delivery / Read status receipt
            socket.on("message_status_update", args -> {
                if (args.length > 0) {
                    try {
                        JSONObject obj = new JSONObject(args[0].toString());
                        String msgId = obj.optString("messageId");
                        String status = obj.optString("status");
                        mainHandler.post(() -> {
                            if (messageListener != null) {
                                messageListener.onMessageStatusUpdate(msgId, status);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling status receipt", e);
                    }
                }
            });

            // Subscription expiry push
            socket.on("subscription_expired", args -> {
                mainHandler.post(() -> {
                    if (messageListener != null) {
                        messageListener.onSubscriptionExpired();
                    }
                });
            });

            // Account suspension push
            socket.on("account_suspended", args -> {
                mainHandler.post(() -> {
                    if (messageListener != null) {
                        messageListener.onAccountSuspended();
                    }
                });
            });

            socket.connect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Socket.IO", e);
        }
    }

    public void joinConversation(String conversationId) {
        if (socket != null && socket.connected()) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("conversationId", conversationId);
                socket.emit("join_conversation", obj);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void leaveConversation(String conversationId) {
        if (socket != null && socket.connected()) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("conversationId", conversationId);
                socket.emit("leave_conversation", obj);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void emitDelivered(String messageId, String conversationId, String senderId) {
        if (socket != null && socket.connected()) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("messageId", messageId);
                obj.put("conversationId", conversationId);
                obj.put("senderId", senderId);
                socket.emit("message_delivered", obj);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void emitRead(String conversationId, String senderId) {
        if (socket != null && socket.connected()) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("conversationId", conversationId);
                obj.put("senderId", senderId);
                socket.emit("message_read", obj);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public synchronized void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket = null;
        }
    }
}
