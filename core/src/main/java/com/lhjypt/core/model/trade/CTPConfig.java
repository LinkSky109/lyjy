package com.lhjypt.core.model.trade;

import java.util.Objects;

/**
 * CTP柜台连接配置
 */
public class CTPConfig {

    /** 经纪商代码 */
    private String brokerId;

    /** 用户ID */
    private String userId;

    /** 密码 */
    private String password;

    /** 行情前置地址（如 tcp://180.168.146.187:10131） */
    private String mdFrontAddress;

    /** 交易前置地址（如 tcp://180.168.146.187:10130） */
    private String tdFrontAddress;

    /** 认证码 */
    private String authCode;

    /** 产品名称 */
    private String appId;

    /** 用户产品信息 */
    private String userProductInfo;

    /** 是否使用UDP */
    private boolean udpEnabled = false;

    /** 重连间隔（秒） */
    private int reconnectInterval = 5;

    /** 心跳超时（秒） */
    private int heartbeatTimeout = 15;

    /** 交易模式：MOCK（模拟）/LIVE（真实CTP） */
    private String mode = "MOCK";

    public String getBrokerId() {
        return brokerId;
    }

    public String getUserId() {
        return userId;
    }

    public String getPassword() {
        return password;
    }

    public String getMdFrontAddress() {
        return mdFrontAddress;
    }

    public String getTdFrontAddress() {
        return tdFrontAddress;
    }

    public String getAuthCode() {
        return authCode;
    }

    public String getAppId() {
        return appId;
    }

    public String getUserProductInfo() {
        return userProductInfo;
    }

    public String getMode() {
        return mode;
    }

    public boolean isUdpEnabled() {
        return udpEnabled;
    }

    public int getReconnectInterval() {
        return reconnectInterval;
    }

    public int getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    public void setBrokerId(String brokerId) {
        this.brokerId = brokerId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setMdFrontAddress(String mdFrontAddress) {
        this.mdFrontAddress = mdFrontAddress;
    }

    public void setTdFrontAddress(String tdFrontAddress) {
        this.tdFrontAddress = tdFrontAddress;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public void setUserProductInfo(String userProductInfo) {
        this.userProductInfo = userProductInfo;
    }

    @Override
    public String toString() {
        return "CTPConfig{brokerId=" + brokerId + ", userId=" + userId
                + ", credentials=<redacted>"
                + ", mdFrontAddress=" + mdFrontAddress
                + ", tdFrontAddress=" + tdFrontAddress
                + ", appId=" + appId
                + ", userProductInfo=" + userProductInfo + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CTPConfig that = (CTPConfig) o;
        return Objects.equals(brokerId, that.brokerId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(password, that.password)
                && Objects.equals(mdFrontAddress, that.mdFrontAddress)
                && Objects.equals(tdFrontAddress, that.tdFrontAddress)
                && Objects.equals(authCode, that.authCode)
                && Objects.equals(appId, that.appId)
                && Objects.equals(userProductInfo, that.userProductInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(brokerId, userId, password, mdFrontAddress, tdFrontAddress, authCode, appId, userProductInfo);
    }
}
