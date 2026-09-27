package com.vinland.app;

public class HarvestLog {

    private String id;
    private String cropName;
    private double quantityKg;
    private String notes;
    private long timestamp;

    public HarvestLog() {
    }

    public HarvestLog(String id, String cropName, double quantityKg, String notes, long timestamp) {
        this.id = id;
        this.cropName = cropName;
        this.quantityKg = quantityKg;
        this.notes = notes;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCropName() {
        return cropName;
    }

    public void setCropName(String cropName) {
        this.cropName = cropName;
    }

    public double getQuantityKg() {
        return quantityKg;
    }

    public void setQuantityKg(double quantityKg) {
        this.quantityKg = quantityKg;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
