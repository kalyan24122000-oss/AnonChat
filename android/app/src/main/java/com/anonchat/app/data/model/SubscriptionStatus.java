package com.anonchat.app.data.model;

import com.google.gson.annotations.SerializedName;

public class SubscriptionStatus {
    @SerializedName("status")
    private String status;

    @SerializedName("isTrial")
    private boolean isTrial;

    @SerializedName("isPremium")
    private boolean isPremium;

    @SerializedName("canChat")
    private boolean canChat;

    @SerializedName("trialExpiresAt")
    private long trialExpiresAt;

    @SerializedName("premiumExpiresAt")
    private Long premiumExpiresAt;

    @SerializedName("daysRemaining")
    private int daysRemaining;

    @SerializedName("notice")
    private String notice;

    public String getStatus() { return status; }
    public boolean isTrial() { return isTrial; }
    public boolean isPremium() { return isPremium; }
    public boolean canChat() { return canChat; }
    public long getTrialExpiresAt() { return trialExpiresAt; }
    public Long getPremiumExpiresAt() { return premiumExpiresAt; }
    public int getDaysRemaining() { return daysRemaining; }
    public String getNotice() { return notice; }
}
