package com.w3n.pinggo.modals;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class UserData {
  private String _id;
  private String phoneNumber;
  private ProfileData profileData;
  private String encryptedCredential;
  private long createdAt;
  private long lastSeen;

  public UserData() {}

  public UserData(
      String id,
      String phoneNumber,
      ProfileData profileData,
      String encryptedCredential,
      long createdAt,
      long lastSeen) {
    this._id = id;
    this.phoneNumber = phoneNumber;
    this.profileData = profileData;
    this.encryptedCredential = encryptedCredential;
    this.createdAt = createdAt;
    this.lastSeen = lastSeen;
  }

  public String getId() {
    return _id;
  }

  public void setId(String id) {
    this._id = id;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public ProfileData getProfileData() {
    return profileData;
  }

  public void setProfileData(ProfileData profileData) {
    this.profileData = profileData;
  }

  public String getEncryptedCredential() {
    return encryptedCredential;
  }

  public void setEncryptedCredential(String encryptedCredential) {
    this.encryptedCredential = encryptedCredential;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  public void setLastSeen(long lastSeen) {
    this.lastSeen = lastSeen;
  }

  public String toJson() {
    return new Gson().toJson(this);
  }

  public static UserData fromJson(String json) {
    if (json == null || json.trim().isEmpty()) {
      return null;
    }

    try {
      return new Gson().fromJson(json, UserData.class);
    } catch (JsonSyntaxException e) {
      return null;
    }
  }

  public static class ProfileData {
    private String name;
    private String phoneNumber;
    private String email;
    private String profilePhotoUrl;
    private String localProfilePhotoPath;
    private String P_ID;

    public ProfileData() {}

    public ProfileData(String name, String phoneNumber, String email, String P_ID) {
      this.name = name;
      this.phoneNumber = phoneNumber;
      this.email = email;
      this.P_ID = P_ID;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getPhoneNumber() {
      return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
      this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getProfilePhotoUrl() {
      return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
      this.profilePhotoUrl = profilePhotoUrl;
    }

    public String getLocalProfilePhotoPath() {
      return localProfilePhotoPath;
    }

    public void setLocalProfilePhotoPath(String localProfilePhotoPath) {
      this.localProfilePhotoPath = localProfilePhotoPath;
    }

    public String getPId() {
      return P_ID;
    }

    public void setPId(String P_ID) {
      this.P_ID = P_ID;
    }
  }
}
