package com.anonchat.app.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anonchat.app.AnonChatApp;
import com.anonchat.app.R;
import com.anonchat.app.data.model.Plan;
import com.anonchat.app.data.model.RedeemResponse;
import com.anonchat.app.ui.base.BaseSecureActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LicenseActivationActivity extends BaseSecureActivity {
    private TextInputEditText etLicenseKey;
    private MaterialButton btnRedeemLicense;
    private ProgressBar progressLicense;
    private RecyclerView rvPlans;
    private PlansAdapter plansAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license_activation);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_license);
        toolbar.setNavigationOnClickListener(v -> finish());

        etLicenseKey = findViewById(R.id.et_license_key);
        btnRedeemLicense = findViewById(R.id.btn_redeem_license);
        progressLicense = findViewById(R.id.progress_license);
        rvPlans = findViewById(R.id.rv_plans);

        rvPlans.setLayoutManager(new LinearLayoutManager(this));
        plansAdapter = new PlansAdapter();
        rvPlans.setAdapter(plansAdapter);

        btnRedeemLicense.setOnClickListener(v -> redeemLicenseKey());

        loadPlans();
    }

    private void loadPlans() {
        getApiService().getPlans().enqueue(new Callback<Map<String, List<Plan>>>() {
            @Override
            public void onResponse(Call<Map<String, List<Plan>>> call, Response<Map<String, List<Plan>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Plan> plans = response.body().get("plans");
                    plansAdapter.setPlans(plans);
                }
            }

            @Override
            public void onFailure(Call<Map<String, List<Plan>>> call, Throwable t) {}
        });
    }

    private void redeemLicenseKey() {
        String key = etLicenseKey.getText() != null ? etLicenseKey.getText().toString().trim() : "";
        if (key.isEmpty()) {
            etLicenseKey.setError("License key is required");
            return;
        }

        setLoading(true);

        Map<String, String> body = new HashMap<>();
        body.put("key", key);

        getApiService().redeemLicense(body).enqueue(new Callback<RedeemResponse>() {
            @Override
            public void onResponse(Call<RedeemResponse> call, Response<RedeemResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    RedeemResponse res = response.body();
                    etLicenseKey.setText("");

                    new AlertDialog.Builder(LicenseActivationActivity.this)
                        .setTitle("License Activated")
                        .setMessage("Tier: " + res.getPlanName() + "\nDuration Added: " + res.getDurationDays() + " Days\n\nTotal Days Remaining: " + res.getDaysRemaining())
                        .setPositiveButton("OK", (dialog, which) -> finish())
                        .show();
                } else {
                    String msg = "Invalid or already used license key";
                    if (response.code() == 429) {
                        msg = "Too many activation attempts. Please try again in 10 minutes.";
                    }
                    Toast.makeText(LicenseActivationActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<RedeemResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LicenseActivationActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressLicense.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRedeemLicense.setEnabled(!loading);
    }

    private static class PlansAdapter extends RecyclerView.Adapter<PlansAdapter.ViewHolder> {
        private final List<Plan> plans = new ArrayList<>();

        public void setPlans(List<Plan> list) {
            plans.clear();
            if (list != null) plans.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plan_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Plan p = plans.get(position);
            holder.tvName.setText(p.getName());
            holder.tvDuration.setText(p.getDurationDays() + " Days");
            holder.tvDesc.setText(p.getDescription());

            if (p.getFeatures() != null && !p.getFeatures().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String f : p.getFeatures()) {
                    sb.append("• ").append(f).append("\n");
                }
                holder.tvFeatures.setText(sb.toString().trim());
                holder.tvFeatures.setVisibility(View.VISIBLE);
            } else {
                holder.tvFeatures.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return plans.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvDuration;
            final TextView tvDesc;
            final TextView tvFeatures;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_plan_name);
                tvDuration = itemView.findViewById(R.id.tv_plan_duration);
                tvDesc = itemView.findViewById(R.id.tv_plan_description);
                tvFeatures = itemView.findViewById(R.id.tv_plan_features);
            }
        }
    }
}
