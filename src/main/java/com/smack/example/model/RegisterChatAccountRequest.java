package com.smack.example.model;

public class RegisterChatAccountRequest {

    private String key;

    private String host;

    private String chatId;

    private String password;

    public RegisterChatAccountRequest() {
    }

    public RegisterChatAccountRequest(String key, String host, String chatId, String password) {
        this.key = key;
        this.host = host;
        this.chatId = chatId;
        this.password = password;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
