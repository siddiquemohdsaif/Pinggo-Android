package com.w3n.pinggo.modals;

public class CallLog {
    private final String contactName;
    private final String calledTime;
    private final String fullCalledDateTime;
    private final String duration;
    private final boolean videoCall;

    public CallLog(String contactName, String calledTime, String fullCalledDateTime, String duration, boolean videoCall) {
        this.contactName = contactName;
        this.calledTime = calledTime;
        this.fullCalledDateTime = fullCalledDateTime;
        this.duration = duration;
        this.videoCall = videoCall;
    }

    public String getContactName() {
        return contactName;
    }

    public String getCalledTime() {
        return calledTime;
    }

    public String getFullCalledDateTime() {
        return fullCalledDateTime;
    }

    public String getDuration() {
        return duration;
    }

    public boolean isVideoCall() {
        return videoCall;
    }
}
