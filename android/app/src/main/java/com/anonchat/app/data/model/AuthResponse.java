package com.anonchat.app.data.model;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName("user")
    private User user;

    @SerializedName("token")
    private String token;

    @SerializedName("recoveryCode")
    private String recoveryCode;

    public User getUser() { return user; }
    public String getToken() { return token; }
    public String getRecoveryCode() { return recoveryCode; }
}
