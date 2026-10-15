package com.anonchat.app.data.model;

import com.google.gson.annotations.SerializedName;

public class Message {
    @SerializedName("id")
    private String id;

    @SerializedName(value = "conversation_id", alternate = {"conversationId"})
    private String conversationId;

    @SerializedName(value = "sender_id", alternate = {"senderId"})
    private String senderId;

    @SerializedName(value = "recipient_id", alternate = {"recipientId"})
    private String recipientId;

    @SerializedName(value = "encrypted_content", alternate = {"encryptedContent"})
    private String encryptedContent;

    @SerializedName("iv")
    private String iv;

    @SerializedName(value = "auth_tag", alternate = {"authTag"})
    private String authTag;

    @SerializedName(value = "sender_ephemeral_key", alternate = {"senderEphemeralKey"})
    private String senderEphemeralKey;

    @SerializedName("status")
    private String status;

    @SerializedName(value = "delivered_at", alternate = {"deliveredAt"})
    private Long deliveredAt;

    @SerializedName(value = "read_at", alternate = {"readAt"})
    private Long readAt;

    @SerializedName(value = "created_at", alternate = {"createdAt", "timestamp"})
    private long createdAt;

    // Transient client-decrypted text for UI rendering
    private transient String plaintext;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getEncryptedContent() { return encryptedContent; }
    public void setEncryptedContent(String encryptedContent) { this.encryptedContent = encryptedContent; }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }

    public String getAuthTag() { return authTag; }
    public void setAuthTag(String authTag) { this.authTag = authTag; }

    public String getSenderEphemeralKey() { return senderEphemeralKey; }
    public void setSenderEphemeralKey(String senderEphemeralKey) { this.senderEphemeralKey = senderEphemeralKey; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getDeliveredAt() { return deliveredAt; }
    public Long getReadAt() { return readAt; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getPlaintext() { return plaintext; }
    public void setPlaintext(String plaintext) { this.plaintext = plaintext; }

    public boolean isMedia() {
        return plaintext != null && (plaintext.startsWith("[MEDIA:IMAGE:") || plaintext.startsWith("[MEDIA:VIDEO:"));
    }

    public boolean isImage() {
        return plaintext != null && plaintext.startsWith("[MEDIA:IMAGE:");
    }

    public boolean isVideo() {
        return plaintext != null && plaintext.startsWith("[MEDIA:VIDEO:");
    }

    public String getMediaUrl() {
        if (!isMedia()) return null;
        String[] parts = plaintext.substring(1, plaintext.length() - 1).split(":");
        return parts.length >= 3 ? parts[2] : null;
    }

    public String getMediaDownloadUrl() {
        if (!isMedia()) return null;
        String[] parts = plaintext.substring(1, plaintext.length() - 1).split(":");
        return parts.length >= 4 ? parts[3] : getMediaUrl();
    }

    public String getMediaFileName() {
        if (!isMedia()) return "Media";
        String[] parts = plaintext.substring(1, plaintext.length() - 1).split(":");
        return parts.length >= 5 ? parts[4] : "Media File";
    }

    public long getMediaExpiresAt() {
        if (!isMedia()) return 0;
        String[] parts = plaintext.substring(1, plaintext.length() - 1).split(":");
        if (parts.length >= 6) {
            try {
                return Long.parseLong(parts[5]);
            } catch (Exception ignored) {}
        }
        return createdAt + (7L * 24 * 3600 * 1000);
    }
}
