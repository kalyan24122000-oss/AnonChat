package com.anonchat.app.ui.base;

import android.os.Bundle;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.anonchat.app.data.api.ApiService;
import com.anonchat.app.utils.PreferenceHelper;

/**
 * Base activity enforcing FLAG_SECURE to block screen captures,
 * prevent screen recordings, and protect OS recent task switcher previews.
 */
public abstract class BaseSecureActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Enforce FLAG_SECURE before window content is created
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );
        super.onCreate(savedInstanceState);
    }

    protected PreferenceHelper getPrefHelper() {
        return ((AnonChatApp) getApplication()).getPreferenceHelper();
    }

    protected ApiService getApiService() {
        return ((AnonChatApp) getApplication()).getApiService();
    }

    /**
     * Display explicit security disclosure dialog explaining FLAG_SECURE protections
     * and clarifying that physical optical cameras cannot be prevented.
     */
    public void showSecurityDisclosureDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.security_disclosure_title)
            .setMessage(getString(R.string.security_disclosure_text) + "\n\n" + getString(R.string.e2ee_notice))
            .setPositiveButton("Understood", null)
            .show();
    }
}
