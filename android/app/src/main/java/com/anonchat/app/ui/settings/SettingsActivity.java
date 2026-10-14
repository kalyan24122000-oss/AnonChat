package com.anonchat.app.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.anonchat.app.data.model.SubscriptionStatus;
import com.anonchat.app.ui.auth.LoginActivity;
import com.anonchat.app.ui.base.BaseSecureActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends BaseSecureActivity {
    private TextView tvUserId;
    private TextView tvDisplayName;
    private TextView tvSubStatus;
    private TextView tvSubDetail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_settings);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvUserId = findViewById(R.id.tv_settings_user_id);
        tvDisplayName = findViewById(R.id.tv_settings_display_name);
        tvSubStatus = findViewById(R.id.tv_settings_sub_status);
        tvSubDetail = findViewById(R.id.tv_settings_sub_detail);

        MaterialButton btnCopyId = findViewById(R.id.btn_copy_id);
        MaterialButton btnOpenLicense = findViewById(R.id.btn_open_license);

        TextView btnSecurityInfo = findViewById(R.id.tv_btn_security_info);
        TextView btnBlockedUsers = findViewById(R.id.tv_btn_blocked_users);
        TextView btnLogout = findViewById(R.id.tv_btn_logout);
        TextView btnDeleteAccount = findViewById(R.id.tv_btn_delete_account);

        String myId = getPrefHelper().getUserId();
        String myName = getPrefHelper().getDisplayName();
        tvUserId.setText(myId != null ? myId : "Unknown");
        tvDisplayName.setText(myName != null && !myName.isEmpty() ? myName : "Anonymous");

        btnCopyId.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("AnonChat ID", myId);
            cm.setPrimaryClip(clip);
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        });

        btnOpenLicense.setOnClickListener(v -> {
            startActivity(new Intent(this, LicenseActivationActivity.class));
        });

        btnSecurityInfo.setOnClickListener(v -> showSecurityDisclosureDialog());

        btnBlockedUsers.setOnClickListener(v -> {
            startActivity(new Intent(this, BlockedUsersActivity.class));
        });

        btnLogout.setOnClickListener(v -> confirmLogout());
        btnDeleteAccount.setOnClickListener(v -> confirmDeleteAccount());

        loadSubscriptionDetails();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSubscriptionDetails();
    }

    private void loadSubscriptionDetails() {
        getApiService().getSubscriptionStatus().enqueue(new Callback<SubscriptionStatus>() {
            @Override
            public void onResponse(Call<SubscriptionStatus> call, Response<SubscriptionStatus> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SubscriptionStatus status = response.body();
                    tvSubStatus.setText(status.getNotice());
                    if (!status.canChat()) {
                        tvSubStatus.setTextColor(getResources().getColor(R.color.danger));
                        tvSubDetail.setText(R.string.trial_expired_notice);
                    } else if (status.isTrial()) {
                        tvSubStatus.setTextColor(getResources().getColor(R.color.badge_trial));
                        tvSubDetail.setText("Free trial active. Contact owner for full license keys.");
                    } else {
                        tvSubStatus.setTextColor(getResources().getColor(R.color.primary));
                        tvSubDetail.setText("Verified sovereign communication tier.");
                    }
                }
            }

            @Override
            public void onFailure(Call<SubscriptionStatus> call, Throwable t) {}
        });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.logout_action)
            .setMessage("Are you sure you want to sign out? Ensure you have your User ID and Recovery Code stored.")
            .setPositiveButton("Sign Out", (dialog, which) -> {
                ((AnonChatApp) getApplication()).getSocketService().disconnect();
                getPrefHelper().clear();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmDeleteAccount() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_account_action)
            .setMessage(R.string.delete_account_confirm)
            .setPositiveButton("Delete Permanently", (dialog, which) -> {
                getApiService().deleteAccount().enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        Toast.makeText(SettingsActivity.this, "Account deleted", Toast.LENGTH_SHORT).show();
                        ((AnonChatApp) getApplication()).getSocketService().disconnect();
                        getPrefHelper().clear();
                        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(SettingsActivity.this, "Failed to delete account: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
