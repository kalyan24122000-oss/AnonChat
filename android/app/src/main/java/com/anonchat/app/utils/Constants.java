package com.anonchat.app.utils;

public class Constants {
    // 10.0.2.2 points to localhost host machine from standard Android Emulator.
    // Can also be overridden in app settings for local Wi-Fi or production domain.
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:4000/";
    
    public static final String PREF_NAME = "anonchat_secure_prefs";
    public static final String PREF_TOKEN = "jwt_token";
    public static final String PREF_USER_ID = "user_id";
    public static final String PREF_DISPLAY_NAME = "display_name";
    public static final String PREF_PUBLIC_KEY = "public_key";
    public static final String PREF_PRIVATE_KEY = "private_key";
    public static final String PREF_BASE_URL = "server_base_url";

    public static final String EXTRA_CONVERSATION_ID = "extra_conversation_id";
    public static final String EXTRA_RECIPIENT_ID = "extra_recipient_id";
    public static final String EXTRA_RECIPIENT_NAME = "extra_recipient_name";
    public static final String EXTRA_RECIPIENT_PUBLIC_KEY = "extra_recipient_public_key";

    public static final String SUBSCRIPTION_STATUS_ACTIVE = "active";
    public static final String SUBSCRIPTION_STATUS_TRIAL = "trial";
    public static final String SUBSCRIPTION_STATUS_EXPIRED = "expired";
}
