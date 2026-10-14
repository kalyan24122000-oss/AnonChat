package com.anonchat.app.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RecoveryActivity extends AppCompatActivity {
    private TextInputEditText etUserId;
    private TextInputEditText etRecoveryCode;
    private TextInputEditText etNewPassword;
    private MaterialButton btnRecover;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recovery);

        etUserId = findViewById(R.id.et_user_id);
        etRecoveryCode = findViewById(R.id.et_recovery_code);
        etNewPassword = findViewById(R.id.et_new_password);
        btnRecover = findViewById(R.id.btn_recover);
        progressBar = findViewById(R.id.progress_bar);
        TextView tvBackToLogin = findViewById(R.id.tv_back_to_login);

        btnRecover.setOnClickListener(v -> performRecovery());
        tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void performRecovery() {
        String userId = etUserId.getText() != null ? etUserId.getText().toString().trim() : "";
        String recoveryCode = etRecoveryCode.getText() != null ? etRecoveryCode.getText().toString().trim() : "";
        String newPassword = etNewPassword.getText() != null ? etNewPassword.getText().toString().trim() : "";

        if (userId.isEmpty()) {
            etUserId.setError("User ID is required");
            return;
        }

        if (recoveryCode.isEmpty()) {
            etRecoveryCode.setError("Recovery Code is required");
            return;
        }

        if (newPassword.length() < 8) {
            etNewPassword.setError("New password must be at least 8 characters");
            return;
        }

        setLoading(true);

        Map<String, Object> body = new HashMap<>();
        body.put("id", userId);
        body.put("recoveryCode", recoveryCode);
        body.put("newPassword", newPassword);

        ((AnonChatApp) getApplication()).getApiService().recoverAccount(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    String newCode = (String) response.body().get("newRecoveryCode");

                    new AlertDialog.Builder(RecoveryActivity.this)
                        .setTitle("Account Restored")
                        .setMessage("Your password has been successfully updated!\n\nYour NEW Recovery Code is:\n" + newCode + "\n\nPlease write this down safely. Your old recovery code is now invalidated.")
                        .setPositiveButton("Sign In With New Password", (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
                } else {
                    Toast.makeText(RecoveryActivity.this, "Recovery failed: Invalid ID or Recovery Code", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                setLoading(false);
                Toast.makeText(RecoveryActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRecover.setEnabled(!loading);
    }
}
