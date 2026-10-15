package com.anonchat.app.ui.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.anonchat.app.data.model.SubscriptionStatus;
import com.anonchat.app.ui.auth.LoginActivity;
import com.anonchat.app.ui.base.BaseSecureActivity;
import com.anonchat.app.utils.BiometricHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends BaseSecureActivity {
    private TextView tvUserId;
    private TextView tvDisplayName;
    private TextView tvSubStatus;
    private TextView tvSubDetail;
    private TextView tvSettingsRoleBadge;

    private View viewProfileAvatarRing;
    private TextView tvProfileAvatarInitial;
    private ImageView ivProfileAdminBadge;
    private LinearLayout layoutAdminBadgeCustomizer;
    private SwitchMaterial switchBiometric;

    private MaterialCardView[] colorCards;
    private final String[] hexColors = {
        "#FF9800", "#10B981", "#EF4444", "#3B82F6", "#8B5CF6", "#F59E0B"
    };

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
        tvSettingsRoleBadge = findViewById(R.id.tv_settings_role_badge);

        viewProfileAvatarRing = findViewById(R.id.view_profile_avatar_ring);
        tvProfileAvatarInitial = findViewById(R.id.tv_profile_avatar_initial);
        ivProfileAdminBadge = findViewById(R.id.iv_profile_admin_badge);
        layoutAdminBadgeCustomizer = findViewById(R.id.layout_admin_badge_customizer);
        switchBiometric = findViewById(R.id.switch_biometric);

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

        String initial = (myName != null && !myName.isEmpty()) ? String.valueOf(myName.charAt(0)).toUpperCase() : "A";
        tvProfileAvatarInitial.setText(initial);

        initColorPalette();
        updateAdminBadgeUi();

        // Biometric toggle
        switchBiometric.setChecked(getPrefHelper().isBiometricEnabled());
        switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!BiometricHelper.isBiometricAvailable(this)) {
                    switchBiometric.setChecked(false);
                    Toast.makeText(this, "Biometric authentication is not enrolled or supported on this device", Toast.LENGTH_LONG).show();
                    return;
                }
                getPrefHelper().setBiometricEnabled(true);
                Toast.makeText(this, "Biometric App Lock enabled", Toast.LENGTH_SHORT).show();
            } else {
                getPrefHelper().setBiometricEnabled(false);
                Toast.makeText(this, "Biometric App Lock disabled", Toast.LENGTH_SHORT).show();
            }
        });

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
        syncProfileDetails();
    }

    private void initColorPalette() {
        colorCards = new MaterialCardView[]{
            findViewById(R.id.card_color_orange),
            findViewById(R.id.card_color_emerald),
            findViewById(R.id.card_color_crimson),
            findViewById(R.id.card_color_blue),
            findViewById(R.id.card_color_purple),
            findViewById(R.id.card_color_gold)
        };

        for (int i = 0; i < colorCards.length; i++) {
            final int index = i;
            if (colorCards[i] != null) {
                colorCards[i].setOnClickListener(v -> selectColor(hexColors[index]));
            }
        }
    }

    private void updateAdminBadgeUi() {
        String role = getPrefHelper().getUserRole();
        String myId = getPrefHelper().getUserId();
        boolean isAdmin = "admin".equalsIgnoreCase(role) || (myId != null && myId.startsWith("ADM-"));

        if (isAdmin) {
            viewProfileAvatarRing.setVisibility(View.VISIBLE);
            ivProfileAdminBadge.setVisibility(View.VISIBLE);
            layoutAdminBadgeCustomizer.setVisibility(View.VISIBLE);
            tvSettingsRoleBadge.setText("Official Administrator");

            String activeColor = getPrefHelper().getBadgeColor();
            applyBadgeColor(activeColor);
            highlightColorCard(activeColor);
        } else {
            viewProfileAvatarRing.setVisibility(View.GONE);
            ivProfileAdminBadge.setVisibility(View.GONE);
            layoutAdminBadgeCustomizer.setVisibility(View.GONE);
            tvSettingsRoleBadge.setText("Anonymous Sovereign User");
        }
    }

    private void applyBadgeColor(String hexColor) {
        try {
            int color = Color.parseColor(hexColor);
            if (viewProfileAvatarRing.getBackground() != null) {
                GradientDrawable ring = (GradientDrawable) viewProfileAvatarRing.getBackground().mutate();
                ring.setStroke((int) (3.0f * getResources().getDisplayMetrics().density), color);
            }
            ivProfileAdminBadge.setColorFilter(color);
            tvSettingsRoleBadge.setTextColor(color);
        } catch (Exception ignored) {}
    }

    private void highlightColorCard(String activeColor) {
        if (colorCards == null) return;
        int strokeWidth = (int) (3.0f * getResources().getDisplayMetrics().density);
        int strokeColor = getResources().getColor(R.color.text_primary);

        for (int i = 0; i < hexColors.length; i++) {
            if (colorCards[i] != null) {
                if (hexColors[i].equalsIgnoreCase(activeColor)) {
                    colorCards[i].setStrokeWidth(strokeWidth);
                    colorCards[i].setStrokeColor(strokeColor);
                } else {
                    colorCards[i].setStrokeWidth(0);
                }
            }
        }
    }

    private void selectColor(String hex) {
        applyBadgeColor(hex);
        highlightColorCard(hex);
        getPrefHelper().setBadgeColor(hex);

        Map<String, Object> body = new HashMap<>();
        body.put("badgeColor", hex);
        getApiService().updateProfile(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(SettingsActivity.this, "Admin verified color updated", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(SettingsActivity.this, "Network sync delayed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void syncProfileDetails() {
        getApiService().getProfile().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> body = response.body();
                    Object userObj = body.get("user");
                    if (userObj instanceof Map) {
                        Map<?, ?> userMap = (Map<?, ?>) userObj;
                        Object role = userMap.get("role");
                        if (role != null) {
                            getPrefHelper().setUserRole(role.toString());
                        }
                        Object badgeColor = userMap.get("badge_color");
                        if (badgeColor != null) {
                            getPrefHelper().setBadgeColor(badgeColor.toString());
                        }
                        Object dName = userMap.get("display_name");
                        if (dName != null && !dName.toString().isEmpty()) {
                            tvDisplayName.setText(dName.toString());
                            tvProfileAvatarInitial.setText(String.valueOf(dName.toString().charAt(0)).toUpperCase());
                        }
                        updateAdminBadgeUi();
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSubscriptionDetails();
        syncProfileDetails();
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
