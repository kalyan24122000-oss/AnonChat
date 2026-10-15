package com.anonchat.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceHelper {
    private final SharedPreferences prefs;

    public PreferenceHelper(Context context) {
        this.prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveAuth(String token, String userId, String displayName) {
        prefs.edit()
            .putString(Constants.PREF_TOKEN, token)
            .putString(Constants.PREF_USER_ID, userId)
            .putString(Constants.PREF_DISPLAY_NAME, displayName)
            .apply();
    }

    public String getToken() {
        return prefs.getString(Constants.PREF_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(Constants.PREF_USER_ID, null);
    }

    public String getDisplayName() {
        return prefs.getString(Constants.PREF_DISPLAY_NAME, null);
    }

    public void saveKeys(String publicKeyBase64, String privateKeyBase64) {
        prefs.edit()
            .putString(Constants.PREF_PUBLIC_KEY, publicKeyBase64)
            .putString(Constants.PREF_PRIVATE_KEY, privateKeyBase64)
            .apply();
    }

    public String getPublicKey() {
        return prefs.getString(Constants.PREF_PUBLIC_KEY, null);
    }

    public String getPrivateKey() {
        return prefs.getString(Constants.PREF_PRIVATE_KEY, null);
    }

    public String getBaseUrl() {
        String url = prefs.getString(Constants.PREF_BASE_URL, Constants.DEFAULT_BASE_URL);
        if (url == null || url.contains("10.0.2.2") || url.contains("localhost")) {
            return Constants.DEFAULT_BASE_URL;
        }
        return url;
    }

    public void setBaseUrl(String url) {
        prefs.edit().putString(Constants.PREF_BASE_URL, url).apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null && getUserId() != null;
    }

    public boolean isBiometricEnabled() {
        return prefs.getBoolean("pref_biometric_enabled", true);
    }

    public void setBiometricEnabled(boolean enabled) {
        prefs.edit().putBoolean("pref_biometric_enabled", enabled).apply();
    }

    public String getBadgeColor() {
        return prefs.getString("pref_badge_color", "#FF9800");
    }

    public void setBadgeColor(String color) {
        prefs.edit().putString("pref_badge_color", color).apply();
    }

    public String getUserRole() {
        return prefs.getString("pref_user_role", "user");
    }

    public void setUserRole(String role) {
        prefs.edit().putString("pref_user_role", role).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
