package plugin.google.maps;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.util.Log;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.CordovaWebView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class AsyncLoadImage extends AsyncTask<Void, Void, AsyncLoadImage.AsyncLoadImageResult> {
  private AsyncLoadImageInterface callback;
  private float density = Resources.getSystem().getDisplayMetrics().density;
  private AsyncLoadImageOptions mOptions;

  // Get max available VM memory, exceeding this amount will throw an
  // OutOfMemory exception.
  static int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
  // Use 1/8th of the available memory for this memory cache.
  public static BitmapCache mIconCache = new BitmapCache(maxMemory / 5);

  private final String TAG = "AsyncLoadImage";
  private CordovaWebView webView;
  private CordovaInterface cordova;

  public static class AsyncLoadImageOptions {
    String url;
    int width;
    int height;
    boolean noCaching;
  }

  public static class AsyncLoadImageResult {
    Bitmap image;
    boolean cacheHit;
    String cacheKey;
  }

  public AsyncLoadImage(CordovaInterface cordova, CordovaWebView webView, AsyncLoadImageOptions options, AsyncLoadImageInterface callback) {
    this.callback = callback;
    this.mOptions = options;
    this.webView = webView;
    this.cordova = cordova;
  }

  public static String getCacheKey(String url, int width, int height) {
    if (url == null) {
      return null;
    }
    try {
      return getCacheKey(new URL(url), width, height);
    } catch (MalformedURLException e) {
      return url.hashCode() + "/" + width + "x" + height;
    }
  }
  public static String getCacheKey(URL url, int width, int height) {
    return url.hashCode() + "/" + width + "x" + height;
  }

  public static void addBitmapToMemoryCache(String key, Bitmap image) {
    if (getBitmapFromMemCache(key) == null) {
      mIconCache.put(key, image.copy(image.getConfig(), true));
    }
  }

  public static void removeBitmapFromMemCache(String key) {
    Bitmap image = mIconCache.remove(key);
    if (image == null || image.isRecycled()) {
      return;
    }
    image.recycle();
  }

  public static Bitmap getBitmapFromMemCache(String key) {
    Bitmap image = mIconCache.get(key);
    if (image == null || image.isRecycled()) {
      return null;
    }

    return image.copy(image.getConfig(), true);
  }

  @Override
  protected void onCancelled(AsyncLoadImageResult result) {
    super.onCancelled(result);
    if (result == null) {
      return;
    }

    if (!result.image.isRecycled()) {
      result.image.recycle();
    }
    result.image = null;
  }

  protected AsyncLoadImageResult doInBackground(Void... params) {

    Log.d("doInBackground getmage","");

    int mWidth = mOptions.width;
    int mHeight = mOptions.height;
    String iconUrl = mOptions.url;
    String orgIconUrl = iconUrl;
    Bitmap image = null;

    if (iconUrl == null) {
      return null;
    }

    String cacheKey = null;
    cacheKey = getCacheKey(orgIconUrl, mWidth, mHeight);

    image = getBitmapFromMemCache(cacheKey);
    if (image != null) {
      AsyncLoadImageResult result = new AsyncLoadImageResult();
      result.image = image;
      result.cacheHit = true;
      result.cacheKey = cacheKey;
      return result;
    }

    if (iconUrl.indexOf("data:image/") == 0 && iconUrl.contains(";base64,")) {
      String[] tmp = iconUrl.split(",");
      image = PluginUtil.getBitmapFromBase64encodedImage(tmp[1]);
    }

    if (mWidth > 0 && mHeight > 0) {
      mWidth = Math.round(mWidth * density);
      mHeight = Math.round(mHeight * density);
      image = PluginUtil.resizeBitmap(image, mWidth, mHeight);
    } else {
      image = PluginUtil.scaleBitmapForDevice(image);
    }

    AsyncLoadImageResult result = new AsyncLoadImageResult();
    result.image = image;
    result.cacheHit = true;

    if (!mOptions.noCaching) {
      result.cacheKey = cacheKey;
      addBitmapToMemoryCache(cacheKey, image);
    }

    return result;
  }


  @Override
  protected void onPostExecute(AsyncLoadImageResult result) {
    this.callback.onPostExecute(result);
  }

}
