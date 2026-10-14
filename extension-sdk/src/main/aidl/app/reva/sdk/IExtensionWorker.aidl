package app.reva.sdk;
import app.reva.sdk.IExtensionHost;
import app.reva.sdk.IExtensionResult;
oneway interface IExtensionWorker {
    void execute(String taskId, String actionId, String input, IExtensionHost host, IExtensionResult result);
    void cancel(String taskId);
}
