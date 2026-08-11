package com.dctm.workbench.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConnectionProfile {

    private String id;
    private String name;
    private Product product = Product.DOCUMENTUM;
    private Protocol protocol = Protocol.MOCK_DFC;
    private String repository = "mock";
    private String username = "dmadmin";
    private String secretId;
    private String restBaseUrl;
    private String cgiRoot;
    private String otdsUrl;
    private String otdsClientId;
    private String dfcLibDir;
    private String dfcPropertiesPath;
    private String reportedVersion = "24.2";
    private AuthMode authMode = AuthMode.PASSWORD;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getRestBaseUrl() {
        return restBaseUrl;
    }

    public void setRestBaseUrl(String restBaseUrl) {
        this.restBaseUrl = restBaseUrl;
    }

    public String getCgiRoot() {
        return cgiRoot;
    }

    public void setCgiRoot(String cgiRoot) {
        this.cgiRoot = cgiRoot;
    }

    public String getOtdsUrl() {
        return otdsUrl;
    }

    public void setOtdsUrl(String otdsUrl) {
        this.otdsUrl = otdsUrl;
    }

    public String getOtdsClientId() {
        return otdsClientId;
    }

    public void setOtdsClientId(String otdsClientId) {
        this.otdsClientId = otdsClientId;
    }

    public String getDfcLibDir() {
        return dfcLibDir;
    }

    public void setDfcLibDir(String dfcLibDir) {
        this.dfcLibDir = dfcLibDir;
    }

    public String getDfcPropertiesPath() {
        return dfcPropertiesPath;
    }

    public void setDfcPropertiesPath(String dfcPropertiesPath) {
        this.dfcPropertiesPath = dfcPropertiesPath;
    }

    public String getReportedVersion() {
        return reportedVersion;
    }

    public void setReportedVersion(String reportedVersion) {
        this.reportedVersion = reportedVersion;
    }

    public AuthMode getAuthMode() {
        return authMode;
    }

    public void setAuthMode(AuthMode authMode) {
        this.authMode = authMode;
    }
}
