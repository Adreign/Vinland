package com.vinland.app.data;

public class NotificationItem {
    private String id;
    private String title;
    private String message;
    private String category;
    private long timestamp;

    public NotificationItem() {
    }

    public NotificationItem(String id, String title, String message, String category, long timestamp) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.category = category;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getResolvedCategory() {
        if (category != null && !category.isEmpty()) {
            return category.toLowerCase();
        }
        String text = ((title != null ? title : "") + " " + (message != null ? message : "")).toLowerCase();
        if (text.contains("rain") || text.contains("thunderstorm") || text.contains("drizzle") || text.contains("shower") || text.contains("☔")) {
            return "rain";
        }
        if (text.contains("heat") || text.contains("temperature") || text.contains("hot") || text.contains("33°c") || text.contains("34°c") || text.contains("35°c") || text.contains("🌡️")) {
            return "heat";
        }
        if (text.contains("humidity") || text.contains("humid") || text.contains("fungal") || text.contains("damp") || text.contains("🌿")) {
            return "humidity";
        }
        return "general";
    }
}
