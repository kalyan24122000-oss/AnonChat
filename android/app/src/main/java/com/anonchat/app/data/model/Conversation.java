package com.anonchat.app.data.model;

import com.google.gson.annotations.SerializedName;

public class Conversation {
    @SerializedName("id")
    private String id;

    @SerializedName("recipient")
    private Recipient recipient;

    @SerializedName("lastMessage")
    private LastMessage lastMessage;

    @SerializedName("unreadCount")
    private int unreadCount;

    @SerializedName("updatedAt")
    private long updatedAt;

    public static class Recipient {
        @SerializedName("id")
        private String id;

        @SerializedName("displayName")
        private String displayName;

        @SerializedName("publicKey")
        private String publicKey;

        @SerializedName("status")
        private String status;

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getPublicKey() { return publicKey; }
        public String getStatus() { return status; }
    }

    public static class LastMessage {
        @SerializedName("id")
        private String id;

        @SerializedName("senderId")
        private String senderId;

        @SerializedName("status")
        private String status;

        @SerializedName("timestamp")
        private long timestamp;

        public String getId() { return id; }
        public String getSenderId() { return senderId; }
        public String getStatus() { return status; }
        public long getTimestamp() { return timestamp; }
    }

    public String getId() { return id; }
    public Recipient getRecipient() { return recipient; }
    public LastMessage getLastMessage() { return lastMessage; }
    public int getUnreadCount() { return unreadCount; }
    public long getUpdatedAt() { return updatedAt; }
}
