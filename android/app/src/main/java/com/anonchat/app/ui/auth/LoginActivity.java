package com.anonchat.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.anonchat.app.data.api.ApiClient;
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

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText etUserId;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private ProgressBar progressBar;
    private PreferenceHelper prefHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefHelper = ((AnonChatApp) getApplication()).getPreferenceHelper();

        // Check if already authenticated
        if (prefHelper.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        etUserId = findViewById(R.id.et_user_id);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        progressBar = findViewById(R.id.progress_bar);

        TextView tvRegister = findViewById(R.id.tv_go_to_register);
        TextView tvRecovery = findViewById(R.id.tv_go_to_recovery);
        btnLogin.setOnClickListener(v -> performLogin());

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        tvRecovery.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RecoveryActivity.class));
        });

        if (getIntent().getBooleanExtra("EXTRA_ACCOUNT_SUSPENDED", false)) {
            showAccountSuspendedDialog();
        }
    }

    private void showAccountSuspendedDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.account_suspended_title)
            .setMessage(R.string.account_suspended_message)
            .setPositiveButton("OK", null)
            .setCancelable(false)
            .show();
    }

    private void performLogin() {
        String userId = etUserId.getText() != null ? etUserId.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (userId.isEmpty()) {
            etUserId.setError("User ID is required");
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            return;
        }

        setLoading(true);

        Map<String, Object> body = new HashMap<>();
        body.put("id", userId);
        body.put("password", password);

        ((AnonChatApp) getApplication()).getApiService().login(body).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse auth = response.body();
                    prefHelper.saveAuth(auth.getToken(), auth.getUser().getId(), auth.getUser().getDisplayName());
                    if (auth.getUser() != null) {
                        if (auth.getUser().getRole() != null) {
                            prefHelper.setUserRole(auth.getUser().getRole());
                        }
                        if (auth.getUser().getBadgeColor() != null) {
                            prefHelper.setBadgeColor(auth.getUser().getBadgeColor());
                        }
                    }

                    // Publish public key to server if not set
                    publishPublicKeyIfNeeded();

                    // Start socket connection
                    ((AnonChatApp) getApplication()).getSocketService().connect();

                    Toast.makeText(LoginActivity.this, "Welcome back", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else if (response.code() == 403) {
                    etPassword.setText("");
                    showAccountSuspendedDialog();
                } else {
                    Toast.makeText(LoginActivity.this, "Invalid credentials", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Connection failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void publishPublicKeyIfNeeded() {
        try {
            String userId = prefHelper.getUserId();
            String pubKey = ((AnonChatApp) getApplication()).getCryptoManager().getOrCreateIdentityPublicKey(userId);
            Map<String, String> body = new HashMap<>();
            body.put("publicKey", pubKey);
            ((AnonChatApp) getApplication()).getApiService().updatePublicKey(body).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
            });
        } catch (Exception e) {
            // Safe ignore
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }
}
