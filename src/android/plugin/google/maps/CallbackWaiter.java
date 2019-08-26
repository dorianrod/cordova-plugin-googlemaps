package plugin.google.maps;

public class CallbackWaiter {
    int count = 0;
    final int countMax;
    final PluginAsyncInterface callback;
    Boolean hasBeenCalled = false;

    public CallbackWaiter(int countMax, PluginAsyncInterface callback) {
        this.countMax = countMax;
        this.callback = callback;

        if(countMax == 0) {
            finish();
        }
    }

    void finish() {
        synchronized(hasBeenCalled) {
            if (hasBeenCalled) return;
            hasBeenCalled = true;
            callback.onPostExecute(null);
        }
    }

    void error(String errorMessage) {
        synchronized(hasBeenCalled) {
            if(hasBeenCalled ) return;
            hasBeenCalled = true;
            callback.onError(errorMessage);
        }
    }

    synchronized void callbackFinished() {
        count++;
        if(count >= countMax) {
            finish();
        }
    }
}
