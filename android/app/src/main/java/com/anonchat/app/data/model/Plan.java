package com.anonchat.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Plan {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("duration_days")
    private int durationDays;

    @SerializedName("description")
    private String description;

    @SerializedName("features")
    private List<String> features;

    public String getId() { return id; }
    public String getName() { return name; }
    public int getDurationDays() { return durationDays; }
    public String getDescription() { return description; }
    public List<String> getFeatures() { return features; }
}
