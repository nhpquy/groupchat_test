package com.smack.example.model;

public class RoomProperties {

    private String roomId;

    private String roomName;

    private String passcode;

    private String nickName;

    public RoomProperties() {
    }

    public RoomProperties(String roomId, String passcode, String nickName) {
        this.roomId = roomId;
        this.passcode = passcode;
        this.nickName = nickName;
        this.roomName = nickName + "/Demo group";
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getPasscode() {
        return passcode;
    }

    public void setPasscode(String passcode) {
        this.passcode = passcode;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }
}
