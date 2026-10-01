package com.vinland.app.data;

public class SoilLog {

    private String id;
    private String cropName;
    private double moisture;
    private double ph;
    private double nitrogen;
    private double phosphorus;
    private double potassium;
    private String overallStatus;
    private long timestamp;

    public SoilLog() {
    }

    public SoilLog(String id, String cropName, double moisture, double ph,
                   double nitrogen, double phosphorus, double potassium,
                   String overallStatus, long timestamp) {
        this.id = id;
        this.cropName = cropName;
        this.moisture = moisture;
        this.ph = ph;
        this.nitrogen = nitrogen;
        this.phosphorus = phosphorus;
        this.potassium = potassium;
        this.overallStatus = overallStatus;
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

    public double getMoisture() {
        return moisture;
    }

    public void setMoisture(double moisture) {
        this.moisture = moisture;
    }

    public double getPh() {
        return ph;
    }

    public void setPh(double ph) {
        this.ph = ph;
    }

    public double getNitrogen() {
        return nitrogen;
    }

    public void setNitrogen(double nitrogen) {
        this.nitrogen = nitrogen;
    }

    public double getPhosphorus() {
        return phosphorus;
    }

    public void setPhosphorus(double phosphorus) {
        this.phosphorus = phosphorus;
    }

    public double getPotassium() {
        return potassium;
    }

    public void setPotassium(double potassium) {
        this.potassium = potassium;
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
