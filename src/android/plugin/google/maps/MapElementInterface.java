package plugin.google.maps;

import org.json.JSONObject;

public interface MapElementInterface {
    public void remove(final String id, final PluginAsyncInterface callbackContext);
    public void createItem(final String hashCode, JSONObject opts, final PluginAsyncInterface callbackContext);
    public void updateItem(final String hashCode, JSONObject opts, final PluginAsyncInterface callbackContext);
}
