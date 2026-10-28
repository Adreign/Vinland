package com.vinland.app.chat;

public class ChatMessage {
    private String text;
    private boolean isUser;
    private long timestamp;

    public ChatMessage(String text, boolean isUser, long timestamp) {
        this.text = text;
        this.isUser = isUser;
        this.timestamp = timestamp;
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return isUser;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
