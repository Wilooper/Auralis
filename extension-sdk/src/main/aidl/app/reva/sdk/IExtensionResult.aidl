package app.reva.sdk;
oneway interface IExtensionResult {
    void complete(String requestId, boolean success, String payload);
}
