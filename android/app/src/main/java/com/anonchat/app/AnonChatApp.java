package com.anonchat.app;

import android.app.Application;
import com.anonchat.app.crypto.CryptoManager;
import com.anonchat.app.data.api.ApiClient;
import com.anonchat.app.data.api.ApiService;
import com.anonchat.app.data.api.SocketService;
import com.anonchat.app.utils.PreferenceHelper;

public class AnonChatApp extends Application {
    private static AnonChatApp instance;
    private PreferenceHelper preferenceHelper;
    private CryptoManager cryptoManager;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        preferenceHelper = new PreferenceHelper(this);
        cryptoManager = new CryptoManager(preferenceHelper);

        // Ensure keypair exists on first run
        try {
            cryptoManager.getOrCreateIdentityPublicKey();
        } catch (Exception e) {
            // Log securely without leaking key material
        }
    }

    public static AnonChatApp getInstance() {
        return instance;
    }

    public PreferenceHelper getPreferenceHelper() {
        return preferenceHelper;
    }

    public CryptoManager getCryptoManager() {
        return cryptoManager;
    }

    public ApiService getApiService() {
        return ApiClient.getService(preferenceHelper);
    }

    public SocketService getSocketService() {
        return SocketService.getInstance(preferenceHelper);
    }
}
