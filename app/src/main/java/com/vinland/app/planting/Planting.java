package com.vinland.app.planting;

/**
 * One crop the gardener started planting.
 * Stored in Firestore at users/{uid}/plantings/{id}.
 */
public class Planting {

    public static final String ACTIVE = "active";
    public static final String HARVESTED = "harvested";
    public static final String STOPPED = "stopped";

    private String id;
    private String cropName;
    private String emoji;
    private int daysToHarvest;
    /** Planting day as "yyyy-MM-dd" (Philippine date). */
    private String startDate;
    private String status = ACTIVE;
    private long createdAt;
    private long endedAt;

    public Planting() {
    }

    public Planting(String cropName, String emoji, int daysToHarvest, String startDate) {
        this.cropName = cropName;
        this.emoji = emoji;
        this.daysToHarvest = daysToHarvest;
        this.startDate = startDate;
        this.status = ACTIVE;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCropName() { return cropName; }
    public void setCropName(String cropName) { this.cropName = cropName; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public int getDaysToHarvest() { return daysToHarvest; }
    public void setDaysToHarvest(int daysToHarvest) { this.daysToHarvest = daysToHarvest; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long endedAt) { this.endedAt = endedAt; }
}
