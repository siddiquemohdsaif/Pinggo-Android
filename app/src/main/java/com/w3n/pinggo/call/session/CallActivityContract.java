package com.w3n.pinggo.call.session;

/** Stable intent contract shared by every call entry point and session engine. */
public final class CallActivityContract {
  public static final String EXTRA_PHONE_NUMBER = "com.w3n.pinggo.EXTRA_CALL_PHONE_NUMBER";
  public static final String EXTRA_PROFILE_PATH = "com.w3n.pinggo.EXTRA_CALL_PROFILE_PATH";
  public static final String EXTRA_CALL_ID = "com.w3n.pinggo.EXTRA_CALL_ID";
  public static final String EXTRA_CALLER_ID = "com.w3n.pinggo.EXTRA_CALLER_ID";
  public static final String EXTRA_SDP_OFFER = "com.w3n.pinggo.EXTRA_SDP_OFFER";
  public static final String EXTRA_CALL_CHAT_ID = "com.w3n.pinggo.EXTRA_CALL_CHAT_ID";
  public static final String EXTRA_AUTO_ACCEPT = "com.w3n.pinggo.EXTRA_AUTO_ACCEPT";
  public static final String EXTRA_CALL_ENGINE = "com.w3n.pinggo.EXTRA_CALL_ENGINE";
  public static final String EXTRA_MEDIA_TYPE = "com.w3n.pinggo.EXTRA_LIVEKIT_MEDIA_TYPE";
  public static final String EXTRA_INCOMING = "com.w3n.pinggo.EXTRA_LIVEKIT_INCOMING";
  public static final String EXTRA_CONFERENCE_CALL =
      "com.w3n.pinggo.EXTRA_LIVEKIT_CONFERENCE_CALL";
  public static final String EXTRA_PARTICIPANT_IDS =
      "com.w3n.pinggo.EXTRA_LIVEKIT_PARTICIPANT_IDS";
  public static final String EXTRA_VIDEO = "com.w3n.pinggo.EXTRA_UNIFIED_CALL_VIDEO";
  private CallActivityContract() { }
}
