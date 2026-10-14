package com.anonchat.app.data.model;

import com.google.gson.annotations.SerializedName;

public class User {
    @SerializedName("id")
    private String id;

    @SerializedName("display_name")
    private String displayName;

    @SerializedName("role")
    private String role;

    @SerializedName("status")
    private String status;

    @SerializedName("public_key")
    private String publicKey;

    @SerializedName("trial_started_at")
    private long trialStartedAt;

    @SerializedName("trial_expires_at")
    private long trialExpiresAt;

    @SerializedName("subscription_status")
    private String subscriptionStatus;

    @SerializedName("premium_expires_at")
    private Long premiumExpiresAt;

    @SerializedName("created_at")
    private long createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public long getTrialStartedAt() { return trialStartedAt; }
    public void setTrialStartedAt(long trialStartedAt) { this.trialStartedAt = trialStartedAt; }

    public long getTrialExpiresAt() { return trialExpiresAt; }
    public void setTrialExpiresAt(long trialExpiresAt) { this.trialExpiresAt = trialExpiresAt; }

    public String getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }

    public Long getPremiumExpiresAt() { return premiumExpiresAt; }
    public void setPremiumExpiresAt(Long premiumExpiresAt) { this.premiumExpiresAt = premiumExpiresAt; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
