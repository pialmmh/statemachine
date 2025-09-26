package com.telcobright.statewalk.examples;

import java.time.LocalDateTime;

/**
 * Device information entity - marked as singleton to be shared across the object graph
 */
public class DeviceInfo {

    private String deviceId;
    private String deviceType; // MOBILE, LANDLINE, VOIP, SOFTPHONE
    private String manufacturer;
    private String model;
    private String osVersion;
    private String appVersion;
    private String imei;
    private String imsi;
    private String networkOperator;
    private String networkType; // 3G, 4G, 5G, WIFI
    private LocalDateTime lastSeen;
    private String location;

    public DeviceInfo() {
        this.deviceId = java.util.UUID.randomUUID().toString();
        this.lastSeen = LocalDateTime.now();
        this.deviceType = "MOBILE";
        this.networkType = "4G";
    }

    public DeviceInfo(String deviceType, String model) {
        this();
        this.deviceType = deviceType;
        this.model = model;
    }

    // Helper methods
    public boolean isMobile() {
        return "MOBILE".equals(deviceType);
    }

    public boolean isVoIP() {
        return "VOIP".equals(deviceType);
    }

    public void updateLastSeen() {
        this.lastSeen = LocalDateTime.now();
    }

    // Getters and setters
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public String getImsi() {
        return imsi;
    }

    public void setImsi(String imsi) {
        this.imsi = imsi;
    }

    public String getNetworkOperator() {
        return networkOperator;
    }

    public void setNetworkOperator(String networkOperator) {
        this.networkOperator = networkOperator;
    }

    public String getNetworkType() {
        return networkType;
    }

    public void setNetworkType(String networkType) {
        this.networkType = networkType;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return String.format("DeviceInfo[%s, %s %s, %s]",
            deviceType, manufacturer, model, networkType);
    }
}