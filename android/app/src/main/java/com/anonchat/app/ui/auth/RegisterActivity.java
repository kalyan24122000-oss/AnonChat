package com.anonchat.app.ui.auth;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.anonchat.app.data.model.AuthResponse;
import com.anonchat.app.ui.main.MainActivity;
import com.anonchat.app.utils.PreferenceHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {
    private TextInputEditText etDisplayName;
    private TextInputEditText etPassword;
    private MaterialButton btnRegister;
    private ProgressBar progressBar;
    private PreferenceHelper prefHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        prefHelper = ((AnonChatApp) getApplication()).getPreferenceHelper();

        etDisplayName = findViewById(R.id.et_display_name);
        etPassword = findViewById(R.id.et_password);
        btnRegister = findViewById(R.id.btn_register);
        progressBar = findViewById(R.id.progress_bar);
        TextView tvGoToLogin = findViewById(R.id.tv_go_to_login);

        btnRegister.setOnClickListener(v -> performRegistration());
        tvGoToLogin.setOnClickListener(v -> finish());
    }

    private void performRegistration() {
        String displayName = etDisplayName.getText() != null ? etDisplayName.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (password.length() < 8) {
            etPassword.setError("Password must be at least 8 characters");
            return;
        }

        setLoading(true);

        String publicKey = null;
        try {
            publicKey = ((AnonChatApp) getApplication()).getCryptoManager().getOrCreateIdentityPublicKey();
        } catch (Exception e) {
            // Key generation fallback
        }

        Map<String, Object> body = new HashMap<>();
        if (!displayName.isEmpty()) {
            body.put("displayName", displayName);
        }
        body.put("password", password);
        if (publicKey != null) {
            body.put("publicKey", publicKey);
        }

        ((AnonChatApp) getApplication()).getApiService().register(body).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse auth = response.body();
                    String registeredUserId = auth.getUser().getId();
                    prefHelper.saveAuth(auth.getToken(), registeredUserId, auth.getUser().getDisplayName());
                    String pub = prefHelper.getPublicKey();
                    String priv = prefHelper.getPrivateKey();
                    if (pub != null && priv != null) {
                        prefHelper.saveKeys(registeredUserId, pub, priv);
                    }
                    if (auth.getUser() != null) {
                        if (auth.getUser().getRole() != null) {
                            prefHelper.setUserRole(auth.getUser().getRole());
                        }
                        if (auth.getUser().getBadgeColor() != null) {
                            prefHelper.setBadgeColor(auth.getUser().getBadgeColor());
                        }
                    }

                    // Start real-time socket
                    ((AnonChatApp) getApplication()).getSocketService().connect();

                    // Show Critical Recovery Credentials Modal Dialog
                    showCredentialsModal(auth.getUser().getId(), auth.getRecoveryCode());
                } else {
                    Toast.makeText(RegisterActivity.this, "Registration failed. Please try again.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showCredentialsModal(String userId, String recoveryCode) {
        String msg = "Your anonymous account has been generated!\n\n" +
            "ASSIGNED USER ID:\n" + userId + "\n\n" +
            "RECOVERY CODE:\n" + recoveryCode + "\n\n" +
            "⚠️ CRITICAL: Copy and save your Recovery Code now. If you forget your password, your account cannot be recovered without this code.";

        new AlertDialog.Builder(this)
            .setTitle("Account Generated Successfully")
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton("Copy & Continue to AnonChat", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("AnonChat Credentials", "User ID: " + userId + "\nRecovery Code: " + recoveryCode);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(RegisterActivity.this, "Credentials copied to clipboard!", Toast.LENGTH_SHORT).show();

                startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                finishAffinity();
            })
            .show();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
    }
}
