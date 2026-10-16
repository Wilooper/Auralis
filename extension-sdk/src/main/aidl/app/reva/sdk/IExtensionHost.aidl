package app.reva.sdk;
import app.reva.sdk.IExtensionResult;
oneway interface IExtensionHost {
    void request(String taskId, String requestId, String operation, String payload, IExtensionResult result);
}
