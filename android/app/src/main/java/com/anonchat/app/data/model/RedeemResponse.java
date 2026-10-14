package com.anonchat.app.data.model;

import com.google.gson.annotations.SerializedName;

public class RedeemResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("plan_id")
    private String planId;

    @SerializedName("plan_name")
    private String planName;

    @SerializedName("duration_days")
    private int durationDays;

    @SerializedName("premium_expires_at")
    private long premiumExpiresAt;

    @SerializedName("days_remaining")
    private int daysRemaining;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getPlanId() { return planId; }
    public String getPlanName() { return planName; }
    public int getDurationDays() { return durationDays; }
    public long getPremiumExpiresAt() { return premiumExpiresAt; }
    public int getDaysRemaining() { return daysRemaining; }
}
