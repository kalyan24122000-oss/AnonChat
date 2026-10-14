package com.anonchat.app.data.model;

import com.google.gson.annotations.SerializedName;

public class Message {
    @SerializedName("id")
    private String id;

    @SerializedName("conversation_id")
    private String conversationId;

    @SerializedName("sender_id")
    private String senderId;

    @SerializedName("recipient_id")
    private String recipientId;

    @SerializedName("encrypted_content")
    private String encryptedContent;

    @SerializedName("iv")
    private String iv;

    @SerializedName("auth_tag")
    private String authTag;

    @SerializedName("sender_ephemeral_key")
    private String senderEphemeralKey;

    @SerializedName("status")
    private String status;

    @SerializedName("delivered_at")
    private Long deliveredAt;

    @SerializedName("read_at")
    private Long readAt;

    @SerializedName("created_at")
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
}
