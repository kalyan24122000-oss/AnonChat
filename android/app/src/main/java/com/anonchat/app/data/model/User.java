package com.anonchat.app.data.model;

import com.google.gson.annotations.SerializedName;

public class User {
    @SerializedName("id")
    private String id;

    @SerializedName(value = "display_name", alternate = {"displayName"})
    private String displayName;

    @SerializedName("role")
    private String role;

    @SerializedName("status")
    private String status;

    @SerializedName(value = "public_key", alternate = {"publicKey"})
    private String publicKey;

    @SerializedName(value = "trial_started_at", alternate = {"trialStartedAt"})
    private long trialStartedAt;

    @SerializedName(value = "trial_expires_at", alternate = {"trialExpiresAt"})
    private long trialExpiresAt;

    @SerializedName(value = "subscription_status", alternate = {"subscriptionStatus"})
    private String subscriptionStatus;

    @SerializedName(value = "premium_expires_at", alternate = {"premiumExpiresAt"})
    private Long premiumExpiresAt;

    @SerializedName(value = "created_at", alternate = {"createdAt"})
    private long createdAt;

    @SerializedName(value = "badge_color", alternate = {"badgeColor"})
    private String badgeColor;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role) || (id != null && id.startsWith("ADM-"));
    }

    public String getBadgeColor() {
        return badgeColor != null && !badgeColor.isEmpty() ? badgeColor : "#FF9800";
    }

    public void setBadgeColor(String badgeColor) { this.badgeColor = badgeColor; }

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
