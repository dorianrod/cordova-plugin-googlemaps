package plugin.google.maps;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PluginMarker extends MyPlugin implements MapElementInterface, MyPluginInterface  {

  private enum Animation {
    DROP,
    BOUNCE
  }

  class AnimationState {
    float progress;
    int way;
    public AnimationState(float progress, int way) {
      this.progress = progress;
      this.way = way;
    }
  }

  protected HashMap<Integer, AsyncTask> iconLoadingTasks = new HashMap<Integer, AsyncTask>();
  protected HashMap<String, Bitmap> icons = new HashMap<String, Bitmap>();
  protected final HashMap<String, Integer> iconCacheKeys = new HashMap<String, Integer>();
  protected final HashMap<String, AnimationState> animationProgress = new HashMap<String, AnimationState>();
  private static final Paint paint = new Paint();
  private final HashMap<String, Integer> semaphoreAsync = new HashMap<String, Integer>();
  private boolean _clearDone = false;


  protected interface ICreateMarkerCallback {
    void onSuccess(Marker marker);
    void onError(String message);
  }

  class IconMarker {
    public Bundle size;
    public double[] anchor;
    public BitmapDescriptor bitmap;
    public IconMarker() {
      /*
      Bundle size, BitmapDescriptor bitmap
      this.size = size;
      this.bitmap = bitmap;*/
    }
  }

  /**
   * Create a marker
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  @SuppressWarnings("unused")
  public void create(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    /*
    JSONObject marker = args.getJSONObject(1);
    JSONObject data = new JSONObject();
    JSONArray markers = new JSONArray();
    markers.put(marker);
    data.put("markers", markers);

    map.batchAdd(data, args);*/
  }


  protected void _create(final String markerId, final JSONObject opts, final ICreateMarkerCallback callback) throws JSONException {

  }

  protected void setIcon_(final Marker marker, final Bundle opts, final PluginAsyncInterface callback) {

  }


  @Override
  public void initialize(CordovaInterface cordova, final CordovaWebView webView) {
    super.initialize(cordova, webView);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    this.clear();
    cordova.getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Set<String> keySet = pluginMap.objects.keys;
        if (keySet.size() > 0) {
          String[] objectIdArray = keySet.toArray(new String[keySet.size()]);

          for (String objectId : objectIdArray) {
            if (pluginMap.objects.containsKey(objectId)) {
              if (objectId.startsWith("marker_") &&
                  !objectId.startsWith("marker_property_") &&
                  !objectId.startsWith("marker_imageSize_") &&
                  !objectId.startsWith("marker_icon_")) {
                Marker marker = (Marker) pluginMap.objects.remove(objectId);
                _removeMarker(marker);
                marker = null;
              } else {
                Object object = pluginMap.objects.remove(objectId);
                object = null;
              }
            }
          }
        }

        pluginMap.objects.clear();
      }
    });

  }

  @Override
  protected void clear() {
    synchronized (semaphoreAsync) {
      _clearDone = false;

      cordova.getThreadPool().submit(new Runnable() {
        @Override
        public void run() {
          //--------------------------------------
          // Cancel tasks
          //--------------------------------------
          AsyncTask task;
          if (iconLoadingTasks != null && iconLoadingTasks.size() > 0) {
            int i, ilen = iconLoadingTasks.size();
            for (i = 0; i < ilen; i++) {
              task = iconLoadingTasks.get(i);
              task.cancel(true);
            }
          }
        }
      });


      //--------------------------------------
      // Recycle bitmaps as much as possible
      //--------------------------------------
      if (iconCacheKeys != null) {
        if (iconCacheKeys.size() > 0) {
          String[] cacheKeys = iconCacheKeys.keySet().toArray(new String[iconCacheKeys.size()]);
          for (int i = 0; i < cacheKeys.length; i++) {
            AsyncLoadImage.removeBitmapFromMemCache(cacheKeys[i]);
            iconCacheKeys.remove(cacheKeys[i]);
          }
          cacheKeys = null;
        }
      }
      if (icons != null && icons.size() > 0) {
        String[] keys = icons.keySet().toArray(new String[icons.size()]);
        //Bitmap[] cachedBitmaps = icons.toArray(new Bitmap[icons.size()]);
        Bitmap image;
        for (int i = 0; i < keys.length; i++) {
          image = icons.remove(keys[i]);
          if (image != null && !image.isRecycled()) {
            image.recycle();
          }
          image = null;
        }
        icons.clear();
      }

      //--------------------------------------
      // clean up properties as much as possible
      //--------------------------------------
      cordova.getActivity().runOnUiThread(new Runnable() {
        @Override
        public void run() {
          Set<String> keySet = pluginMap.objects.keys;
          if (keySet.size() > 0) {
            String[] objectIdArray = keySet.toArray(new String[keySet.size()]);

            for (String objectId : objectIdArray) {
              if (pluginMap.objects.containsKey(objectId)) {
                if (objectId.startsWith("marker_") &&
                    !objectId.startsWith("marker_property_") &&
                    !objectId.startsWith("marker_imageSize") &&
                    !objectId.startsWith("marker_icon_")) {
                  Marker marker = (Marker) pluginMap.objects.remove(objectId);
                  marker.setTag(null);
                  marker.remove();
                  marker = null;
                } else {
                  Object object = pluginMap.objects.remove(objectId);
                  object = null;
                }
              }
            }

            synchronized (semaphoreAsync) {
              _clearDone = true;
              semaphoreAsync.notify();
            }
          }

        }
      });

      try {
        if (!_clearDone) {
          semaphoreAsync.wait(1000);
        }
      } catch (InterruptedException e) {
        // ignore
        //e.printStackTrace();
      }
    }
  }



  /**
   * Set visibility for the object
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  public void setVisible(JSONArray args, CallbackContext callbackContext) throws JSONException {
    boolean isVisible = args.getBoolean(1);
    String id = args.getString(0);

    Marker marker = this.getMarker(id);

    if (marker == null) {
      callbackContext.success();
      return;
    }
    String propertyId = "marker_property_" + id;
    JSONObject properties = null;
    if (self.pluginMap.objects.containsKey(propertyId)) {
      properties = (JSONObject)self.pluginMap.objects.get(propertyId);
    } else {
      properties = new JSONObject();
    }
    properties.put("isVisible", isVisible);
    self.pluginMap.objects.put(propertyId, properties);

    this.setBoolean("setVisible", id, isVisible, callbackContext);
  }
  /**
   * @param args
   * @param callbackContext
   * @throws JSONException
   */

  public void setDisableAutoPan(JSONArray args, CallbackContext callbackContext) throws JSONException {
    boolean disableAutoPan = args.getBoolean(1);
    String id = args.getString(0);
    Marker marker = this.getMarker(id);
    if (marker == null) {
      callbackContext.success();
      return;
    }
    String propertyId = "marker_property_" + id;
    JSONObject properties = null;
    if (self.pluginMap.objects.containsKey(propertyId)) {
      properties = (JSONObject)self.pluginMap.objects.get(propertyId);
    } else {
      properties = new JSONObject();
    }
    properties.put("disableAutoPan", disableAutoPan);
    self.pluginMap.objects.put(propertyId, properties);
    callbackContext.success();
  }



  public void remove(final String id, final PluginAsyncInterface callbackContext) {
    final Marker marker = this.getMarker(id);

    if (marker == null) {
      callbackContext.onPostExecute(null);
      return;
    }

   // Log.d("remove", id);

    setDisappearAnimation(marker, new PluginAsyncInterface(){

      @Override
      public void onPostExecute(Object object) {
        pluginMap.objects.remove(id);
        pluginMap.objects.remove(id.replace("marker_", "marker_property_"));
        pluginMap.objects.remove(id.replace("marker_", "marker_imageSize_"));

        cordova.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            //Log.d("remove after animation", id);
            _removeMarker(marker);
            callbackContext.onPostExecute(null);
          }
        });
      }

      @Override
      public void onError(String errorMsg) {
          Log.e("remove after error", errorMsg);
          _removeMarker(marker);
        callbackContext.onError(errorMsg);
      }
    });
  }

  protected void _removeMarker(Marker marker) {
    if (marker == null || marker.getTag() == null) {
      return;
    }
    //---------------------------------------------
    // Removes marker safely
    // (prevent the `un-managed object exception`)
    //---------------------------------------------
    String markerTag = ((String) marker.getTag()).replace("marker_", "");
    String iconCacheKey = "marker_icon_" + markerTag;
    marker.setTag(null);
    marker.remove();

    icons.remove(markerTag);
    //---------------------------------------------------------------------------------
    // If no marker uses the icon image used be specified this marker, release it
    //---------------------------------------------------------------------------------
    if (pluginMap.objects.containsKey(iconCacheKey)) {
      String cacheKey = (String) pluginMap.objects.remove(iconCacheKey);
      if (iconCacheKeys.containsKey(cacheKey)) {
        int count = iconCacheKeys.get(cacheKey);
        count--;
        iconCacheKeys.put(cacheKey, count);
      }
     // pluginMap.objects.remove(iconCacheKey);
    }
  }


  private void _setIconAnchor(final Marker marker, double anchorX, double anchorY, final int imageWidth, final int imageHeight) {
    // The `anchor` of the `icon` property
    anchorX = anchorX * density;
    anchorY = anchorY * density;
    final double fAnchorX = anchorX;
    final double fAnchorY = anchorY;
    marker.setAnchor((float)(fAnchorX / imageWidth), (float)(fAnchorY / imageHeight));
  }


  /*
  protected Bitmap drawLabel(Bitmap image, Bundle labelOptions) {
    String text = labelOptions.getString("text");
    if (text == null || text.length() == 0) {
      return image;
    }
    Bitmap newIcon = Bitmap.createBitmap(image);
    Canvas canvas = new Canvas(newIcon);
    image.recycle();
    image = null;

    int fontSize = 10;
    if (labelOptions.containsKey("fontSize")) {
      fontSize = labelOptions.getInt("fontSize");
    }
    paint.setTextSize(fontSize * density);

    int color = Color.BLACK;
    if (labelOptions.containsKey("color")) {
      color = labelOptions.getInt("color");
    }
    boolean bold = false;
    if (labelOptions.containsKey("bold")) {
      bold = labelOptions.getBoolean("bold");
    }
    paint.setFakeBoldText(bold);
    boolean italic = false;
    if (labelOptions.containsKey("italic")) {
      italic = labelOptions.getBoolean("italic");
    }
    if (italic && bold) {
      paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
    } else if(italic) {
      paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
    } else if(bold) {
      paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    } else {
      paint.setTypeface(Typeface.DEFAULT);
    }
    paint.setColor(color);


    Rect rect = new Rect();
    canvas.getClipBounds(rect);
    int cHeight = rect.height();
    int cWidth = rect.width();
    paint.setTextAlign(Paint.Align.LEFT);
    paint.getTextBounds(text, 0, text.length(), rect);
    float x = cWidth / 2f - rect.width() / 2f - rect.left;
    float y = cHeight / 2f + rect.height() / 2f - rect.bottom;
    canvas.drawText(text, x, y, paint);
    return newIcon;
  }*/

  protected void setIcon(final Marker marker, final Bundle iconProperty, final PluginAsyncInterface callback) {
    boolean noCaching = false;

    if (iconProperty.containsKey("noCache")) {
      noCaching = iconProperty.getBoolean("noCache");
    }
    if (iconProperty.containsKey("iconHue")) {
        float hue = iconProperty.getFloat("iconHue");
        marker.setIcon(BitmapDescriptorFactory.defaultMarker(hue));
        return;
    }

    String iconUrl = iconProperty.getString("url");
    if (iconUrl == null) {
      return;
    }

    int width = -1;
    int height = -1;
    if (iconProperty.containsKey("size")) {
      try {
        Bundle sizeInfo = (Bundle) iconProperty.get("size");
        width = (int) sizeInfo.getInt("width", width);
        height = (int) sizeInfo.getInt("height", height);
      } catch(Exception e) {
        e.printStackTrace();
      }
    }

    final AsyncLoadImage.AsyncLoadImageOptions options = new AsyncLoadImage.AsyncLoadImageOptions();
    options.url = iconUrl;
    options.width = width ;
    options.height = height;
    options.noCaching = noCaching;
    final int taskId = options.hashCode();

    final AsyncLoadImageInterface onComplete = new AsyncLoadImageInterface() {
      @Override
      public void onPostExecute(AsyncLoadImage.AsyncLoadImageResult result) {
        iconLoadingTasks.remove(taskId);

        if (result == null || result.image == null) {
          callback.onPostExecute(marker);
          return;
        }
        if (marker == null) {
          callback.onError("marker is removed");
          return;
        }
        if (result.image.isRecycled()) {
          //Maybe the task was canceled by map.clean()?
          callback.onError("Can not get image for marker. Maybe the task was canceled by map.clean()?");
          return;
        }

        synchronized (marker) {
          String markerTag = (marker.getTag() + "").replace("marker_", "");
          String markerIconTag = "marker_icon_" + markerTag;
          String markerImgSizeTag = "marker_imageSize_" + markerTag;
          String currentCacheKey = (String) pluginMap.objects.get(markerIconTag);

          if (result.cacheKey != null && !result.cacheKey.equals(currentCacheKey)) {
            synchronized (iconCacheKeys) {
              if (iconCacheKeys.containsKey(currentCacheKey)) {
                int count = iconCacheKeys.get(currentCacheKey);
                count--;
                iconCacheKeys.put(currentCacheKey, count);
              }
            }
          }

          /*
          if (icons.containsKey(markerIconTag)) {
            Bitmap icon = icons.remove(markerIconTag);
            if (icon != null && !icon.isRecycled()) {
              icon.recycle();
            }
            icon = null;
          }*/

          icons.put(markerTag, result.image);

          //-------------------------------------------------------
          // Counts up the markers that use the same icon image.
          //-------------------------------------------------------
          if (result.cacheHit) {
            if (marker == null || marker.getTag() == null) {
              callback.onPostExecute(marker);
              return;
            }
            String hitCountKey = markerIconTag;
            pluginMap.objects.put(hitCountKey, result.cacheKey);
            if (!iconCacheKeys.containsKey(result.cacheKey)) {
              iconCacheKeys.put(result.cacheKey, 1);
            } else {
              int count = iconCacheKeys.get(result.cacheKey);
              iconCacheKeys.put(result.cacheKey, count + 1);
            }
            //Log.d(TAG, "----> " + result.cacheKey + " = " + iconCacheKeys.get(result.cacheKey));
          }

          //------------------------
          // Draw label on icon
          //------------------------
          /*
          if (iconProperty.containsKey("label")) {
            result.image = drawLabel(result.image, iconProperty.getBundle("label"));
          }*/

          final BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(result.image);
          if (bitmapDescriptor == null || marker == null || marker.getTag() == null) {
            callback.onPostExecute(marker);
            return;
          }

          cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
              //------------------------
              // Sets image as icon
              //------------------------
              marker.setIcon(bitmapDescriptor);
              //bitmapDescriptor = null;

              //---------------------------------------------
              // Save the information for the anchor property
              //---------------------------------------------
              Bundle imageSize = new Bundle();
              imageSize.putInt("width", result.image.getWidth());
              imageSize.putInt("height", result.image.getHeight());

              self.pluginMap.objects.remove(markerImgSizeTag);
              self.pluginMap.objects.put(markerImgSizeTag, imageSize);

              //result.image.recycle();
              //result.image = null;


              // The `anchor` of the `icon` property
              try {
                if (iconProperty.containsKey("anchor")) {
                  double[] anchor = iconProperty.getDoubleArray("anchor");
                  if (anchor != null && anchor.length == 2) {
                    _setIconAnchor(marker, anchor[0], anchor[1], imageSize.getInt("width"), imageSize.getInt("height"));
                  }
                }
              } catch (Exception e) {
                e.printStackTrace();
              }

              callback.onPostExecute(marker);
            }
          });

        }
      }
    };

    AsyncLoadImage task = new AsyncLoadImage(cordova, webView, options, onComplete);
    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    iconLoadingTasks.put(taskId, task);
  }


  /*
  protected void getBitmapIcon(final Marker marker, final Bundle iconProperty, final PluginAsyncInterface callback) {
    IconMarker markerIcon = new IconMarker();
    boolean noCaching = false;

    if (iconProperty.containsKey("noCache")) {
      noCaching = iconProperty.getBoolean("noCache");
    }

    if (iconProperty.containsKey("iconHue")) {
      float hue = iconProperty.getFloat("iconHue");
      callback.onPostExecute(BitmapDescriptorFactory.defaultMarker(hue));
      return;
    }

    String iconUrl = iconProperty.getString("url");
    if (iconUrl == null) {
      return;
    }

    int width = -1;
    int height = -1;
    if (iconProperty.containsKey("size")) {
      try {
        Bundle sizeInfo = (Bundle) iconProperty.get("size");
        width = (int) sizeInfo.getDouble("width", width);
        height = (int) sizeInfo.getDouble("height", height);
      } catch(Exception e) {
        e.printStackTrace();
      }
    }

    final AsyncLoadImage.AsyncLoadImageOptions options = new AsyncLoadImage.AsyncLoadImageOptions();
    options.url = iconUrl;
    options.width = width;
    options.height = height;
    options.noCaching = noCaching;
    final int taskId = options.hashCode();

    final AsyncLoadImageInterface onComplete = new AsyncLoadImageInterface() {
      @Override
      public void onPostExecute(AsyncLoadImage.AsyncLoadImageResult result) {
        iconLoadingTasks.remove(taskId);

        if (result == null || result.image == null) {
          callback.onPostExecute(null);
          return;
        }
        if (marker == null) {
          callback.onError("marker is removed");
          return;
        }
        if (result.image.isRecycled()) {
          //Maybe the task was canceled by map.clean()?
          callback.onError("Can not get image for marker. Maybe the task was canceled by map.clean()?");
          return;
        }

        synchronized (marker) {
          String markerTag = marker.getTag() + "";
          String markerIconTag = "marker_icon_" + markerTag;

          String currentCacheKey = (String) pluginMap.objects.get(markerIconTag);
          if (result.cacheKey != null && result.cacheKey.equals(currentCacheKey)) {
            synchronized (iconCacheKeys) {
              if (iconCacheKeys.containsKey(currentCacheKey)) {
                int count = iconCacheKeys.get(currentCacheKey);
                count--;
                if (count < 1) {
                  AsyncLoadImage.removeBitmapFromMemCache(currentCacheKey);
                  iconCacheKeys.remove(currentCacheKey);
                } else {
                  iconCacheKeys.put(currentCacheKey, count);
                }
              }
            }
          }

          if (icons.containsKey(markerIconTag)) {
            Bitmap icon = icons.remove(markerIconTag);
            if (icon != null && !icon.isRecycled()) {
              icon.recycle();
            }
            icon = null;
          }
          icons.put(markerTag, result.image);

          //-------------------------------------------------------
          // Counts up the markers that use the same icon image.
          //-------------------------------------------------------
          if (result.cacheHit) {
            if (marker == null || marker.getTag() == null) {
              callback.onPostExecute(null);
              return;
            }
            String hitCountKey = markerIconTag;
            pluginMap.objects.put(hitCountKey, result.cacheKey);
            if (!iconCacheKeys.containsKey(result.cacheKey)) {
              iconCacheKeys.put(result.cacheKey, 1);
            } else {
              int count = iconCacheKeys.get(result.cacheKey);
              iconCacheKeys.put(result.cacheKey, count + 1);
            }
            //Log.d(TAG, "----> " + result.cacheKey + " = " + iconCacheKeys.get(result.cacheKey));
          }

          //------------------------
          // Draw label on icon
          //------------------------
          if (iconProperty.containsKey("label")) {
            result.image = drawLabel(result.image, iconProperty.getBundle("label"));
          }

          BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(result.image);
          if (bitmapDescriptor == null || marker == null || marker.getTag() == null) {
            callback.onPostExecute(null);
            return;
          }

          //---------------------------------------------
          // Save the information for the anchor property
          //---------------------------------------------
          Bundle imageSize = new Bundle();
          imageSize.putInt("width", result.image.getWidth());
          imageSize.putInt("height", result.image.getHeight());

          result.image.recycle();
          result.image = null;

          markerIcon.size = imageSize;

          // The `anchor` of the `icon` property
          try {
            if (iconProperty.containsKey("anchor")) {
              double[] anchor = iconProperty.getDoubleArray("anchor");
              if (anchor != null && anchor.length == 2) {
                markerIcon.anchor = anchor;
              }
            }
          } catch(Exception e) {
            e.printStackTrace();
          }

          callback.onPostExecute(markerIcon);
        }
      }
    };

    AsyncLoadImage task = new AsyncLoadImage(cordova, webView, options, onComplete);
    task.execute();
    iconLoadingTasks.put(taskId, task);
  }*/


  public void updateItem(final String id, JSONObject opts, final PluginAsyncInterface callbackContext) {
    final Marker marker = this.getMarker(id);
    if(marker != null) {
        synchronized(marker) {
            Iterator<String> it = opts.keys();
            String hashCode = id.replace("marker_", "");

            JSONObject result = new JSONObject();

            boolean hasIcon = opts.has("icon");

            final CountDownLatch waiterIcon = new CountDownLatch(hasIcon ? 1 : 0);

            if (hasIcon) {
                Bundle bundle = getBundleIcon(opts);
                this.setIcon(marker, bundle, new PluginAsyncInterface() {
                    @Override
                    public void onPostExecute(final Object object) {
                        //Marker marker = (Marker) object;
                        waiterIcon.countDown();
                    }

                    @Override
                    public void onError(String errorMsg) {
                      waiterIcon.countDown();
                    }

                });
            }

          try {
            waiterIcon.await();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }

          cordova.getActivity().runOnUiThread(new Runnable() {
              @Override
              public void run() {
                try {

                  while (it.hasNext()) {
                    String key = it.next();

                    switch (key) {
                      case "position":
                        JSONObject latlng = opts.getJSONObject(key);
                        final LatLng position = new LatLng(latlng.getDouble("lat"), latlng.getDouble("lng"));
                        marker.setPosition(position);
                        break;

                      case "title":
                        marker.setTitle(opts.getString(key));
                        break;

                      case "snippet":
                        marker.setSnippet(opts.getString(key));
                        break;

                      case "visible":
                        marker.setVisible(opts.getBoolean(key));
                        break;

                      case "draggable":
                        marker.setDraggable(opts.getBoolean(key));
                        break;

                      case "rotation":
                        marker.setRotation((float) opts.getDouble(key));
                        break;

                      case "flat":
                        marker.setFlat(opts.getBoolean(key));
                        break;

                      case "zIndex":
                        marker.setZIndex((float) opts.getDouble(key));
                        break;

                    }
                  }

                  try {
                    marker.setAlpha(opts.has("opacity") ? (float) opts.getDouble("opacity") : 1);
                  } catch (JSONException e) {
                    e.printStackTrace();
                  }

                  callbackContext.onPostExecute(prepareResponse(marker));
                } catch (Exception e) {
                  e.printStackTrace();
                  callbackContext.onError(e.toString());
                }
              }
            });
        }

    } else {
      callbackContext.onError("No marker anymore");
    }
  }

  protected Bundle getBundleIcon(JSONObject opts) {
    Bundle bundle = null;
    try {
      Object value = opts.get("icon");
      if (JSONObject.class.isInstance(value)) {
        JSONObject iconProperty = (JSONObject) value;
        if (iconProperty.has("url") && JSONArray.class.isInstance(iconProperty.get("url"))) {

          float[] hsv = new float[3];
          JSONArray arrayRGBA = iconProperty.getJSONArray("url");
          Color.RGBToHSV(arrayRGBA.getInt(0), arrayRGBA.getInt(1), arrayRGBA.getInt(2), hsv);
          bundle = new Bundle();
          bundle.putFloat("iconHue", hsv[0]);

        } else {
          if (iconProperty.has("label")) {
            JSONObject label = iconProperty.getJSONObject("label");
            if (label != null && label.get("color") instanceof JSONArray) {
              label.put("color", PluginUtil.parsePluginColor(label.getJSONArray("color")));
              iconProperty.put("label", label);
            }
          }
          bundle = PluginUtil.Json2Bundle(iconProperty);

          // The `anchor` of the `icon` property
          if (iconProperty.has("anchor")) {
            value = iconProperty.get("anchor");
            if (JSONArray.class.isInstance(value)) {
              JSONArray points = (JSONArray) value;
              double[] anchorPoints = new double[points.length()];
              for (int i = 0; i < points.length(); i++) {
                anchorPoints[i] = points.getDouble(i);
              }

              bundle.putDoubleArray("anchor", anchorPoints);
            } else if (value instanceof JSONObject && ((JSONObject) value).has("x") && ((JSONObject) value).has("y")) {
              double[] anchorPoints = new double[2];
              anchorPoints[0] = ((JSONObject) value).getDouble("x");
              anchorPoints[1] = ((JSONObject) value).getDouble("y");
              bundle.putDoubleArray("anchor", anchorPoints);
            }
          }

          // The `infoWindowAnchor` property for infowindow
          /*
          if (opts.has("infoWindowAnchor")) {
            value = opts.get("infoWindowAnchor");
            if (JSONArray.class.isInstance(value)) {
              JSONArray points = (JSONArray) value;
              double[] anchorPoints = new double[points.length()];
              for (int i = 0; i < points.length(); i++) {
                anchorPoints[i] = points.getDouble(i);
              }
              bundle.putDoubleArray("infoWindowAnchor", anchorPoints);
            } else if (value instanceof JSONObject && ((JSONObject) value).has("x") && ((JSONObject) value).has("y")) {
              double[] anchorPoints = new double[2];
              anchorPoints[0] = ((JSONObject) value).getDouble("x");
              anchorPoints[1] = ((JSONObject) value).getDouble("y");
              bundle.putDoubleArray("infoWindowAnchor", anchorPoints);
            }
          }*/
        }

      } else if (JSONArray.class.isInstance(value)) {
        float[] hsv = new float[3];
        JSONArray arrayRGBA = (JSONArray) value;
        Color.RGBToHSV(arrayRGBA.getInt(0), arrayRGBA.getInt(1), arrayRGBA.getInt(2), hsv);
        bundle = new Bundle();
        bundle.putFloat("iconHue", hsv[0]);
      } else {
        bundle = new Bundle();
        bundle.putString("url", (String) value);
      }

      /*
      if (opts.has("animation")) {
        bundle.putString("animation", opts.getString("animation"));
      }*/
    } catch(Exception e) {
      e.printStackTrace();
    }
    return bundle;
  }

  private void setAppearAnimation(final Marker marker, final PluginAsyncInterface callback ) {
    setScaleAnimation(marker, 1, callback);
  }
  private void setDisappearAnimation(final Marker marker, final PluginAsyncInterface callback ) {
    setScaleAnimation(marker, -1, callback);
  }

  private void setScaleAnimation(final Marker marker, int way, final PluginAsyncInterface callback) {
      final long startTime = SystemClock.uptimeMillis();
      final long duration = 200;

      cordova.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            final String markerId = ((String) marker.getTag());
            final String markerTag = markerId.replace("marker_", "");
            final String markerImgSizeTag = "marker_imageSize_" + markerTag;
            //final Bitmap bitmap;

            /*
            synchronized(icons) {
              final Bitmap origBitmap = icons.get(markerTag);
              if (origBitmap == null || origBitmap.isRecycled()) {
                callback.onError("Icon recycled");
                return;
              }
              synchronized(origBitmap) {
                  bitmap = Bitmap.createBitmap(origBitmap);
              }
            }*/

            class End {
              public void clean(boolean error, boolean finishAnimation) {
               // bitmap.recycle();
                if(finishAnimation) {
                  animationProgress.remove(markerId);
                }
                if(callback != null) {
                  if(error) {
                    callback.onError(null);
                  } else {
                    callback.onPostExecute(null);
                  }
                }
              }
            }
            final End end = new End();


            /*
            final Bundle imageSize = (Bundle) self.pluginMap.objects.get(markerImgSizeTag);
            if(imageSize == null) {
              end.clean(true, true);
            }*/

           // final int width = imageSize.getInt("width");
           // final int height = imageSize.getInt("height");

            final Handler handler = new Handler();
            final Interpolator interpolator = new LinearInterpolator();

            handler.post(new Runnable() {
              @Override
              public void run() {
                //  Log.d("Animation start", markerId);

                final Marker marker = getMarker(markerId);
                if (marker != null) {
                  synchronized(marker) {
                    synchronized (animationProgress) {
                      AnimationState curState = animationProgress.get(markerId);
                      if (curState != null && curState.way < 0 && way > 0) {
                        end.clean(true, false);
                        return;
                      }

                      long elapsed = SystemClock.uptimeMillis() - startTime;
                      float t = interpolator.getInterpolation((float) elapsed / duration);
                      if (t > 1) t = 1;

                      float progress = way > 0 ? t : 1 - t;

                      // int w = Math.max(10, (int) Math.round(progress * width));
                      // int h = Math.max(10, (int) Math.round(progress * height));
                      float alpha = Math.min(1, 1 * progress);

                      animationProgress.put(markerId, new AnimationState(progress, way));

                      marker.setAlpha(alpha);
                      /*
                      synchronized(bitmap) {
                        if (bitmap != null) {
                          Bitmap bitmapCopy = null;
                          Bitmap scaledIcon = null;
                          try {
                            bitmapCopy = Bitmap.createBitmap(bitmap);
                            scaledIcon = Bitmap.createScaledBitmap(bitmapCopy, w, h, false);
                            final BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(scaledIcon);
                            marker.setIcon(bitmapDescriptor);
                            marker.setAlpha(alpha);
                          } catch (Exception e) {
                            e.printStackTrace();
                          } finally {
                            if (scaledIcon != null) scaledIcon.recycle();
                            if (scaledIcon != null) bitmapCopy.recycle();
                          }
                        }
                      }*/

                      if (t < 1.0) {
                        handler.postDelayed(this, 16);
                      } else {
                        end.clean(false, true);
                      }
                    }
                  }
                } else {
                    end.clean(true, true);
                }
              }
            });
          }
      });
    }


    public void createItem(final String hashCode, final JSONObject opts, final PluginAsyncInterface callbackContext) {
      try {
        final String markerId = "marker_" + hashCode;
        final JSONObject properties = new JSONObject();
        final MarkerOptions markerOptions = new MarkerOptions();

        final boolean animation = opts.has("noAnimation") ? false : true;

        if (opts.has("position")) {
          JSONObject position = opts.getJSONObject("position");
          markerOptions.position(new LatLng(position.getDouble("lat"), position.getDouble("lng")));
        }
        if (opts.has("title")) {
          markerOptions.title(opts.getString("title"));
        }
        if (opts.has("snippet")) {
          markerOptions.snippet(opts.getString("snippet"));
        }
        if (opts.has("visible")) {
          markerOptions.visible(opts.getBoolean("visible"));
        }
        if (opts.has("draggable")) {
          markerOptions.draggable(opts.getBoolean("draggable"));
        }
        markerOptions.visible(false);
        if (opts.has("rotation")) {
          markerOptions.rotation((float) opts.getDouble("rotation"));
        }
        if (opts.has("flat")) {
          markerOptions.flat(opts.getBoolean("flat"));
        }
        if (opts.has("opacity")) {
          markerOptions.alpha(0);
        }
        if (opts.has("zIndex")) {
          markerOptions.zIndex((float) opts.getDouble("zIndex"));
        }

        if (opts.has("icon")) {
          JSONObject icon = opts.getJSONObject("icon");
          if(opts.has("anchor")) {
            JSONObject anchor = icon.getJSONObject("anchor");
            properties.put("anchor", anchor);
          }
        }
        if (opts.has("styles")) {
          properties.put("styles", opts.getJSONObject("styles"));
        }
        if (opts.has("disableAutoPan")) {
          properties.put("disableAutoPan", opts.getBoolean("disableAutoPan"));
        } else {
          properties.put("disableAutoPan", false);
        }
        if (opts.has("noCache")) {
          properties.put("noCache", opts.getBoolean("noCache"));
        } else {
          properties.put("noCache", false);
        }
        if (opts.has("useHtmlInfoWnd")) {
          properties.put("useHtmlInfoWnd", opts.getBoolean("useHtmlInfoWnd"));
        } else {
          properties.put("useHtmlInfoWnd", false);
        }

        cordova.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            final Marker marker = map.addMarker(markerOptions);

            marker.setTag("marker_" + hashCode);
            marker.hideInfoWindow();

            // Store the marker
            synchronized (pluginMap.objects) {
              pluginMap.objects.put(markerId, marker);
              pluginMap.objects.put("marker_property_" + hashCode, properties);
            }

            // Prepare the result
            final JSONObject result = new JSONObject();
            try {
              result.put("__pgmId", markerId);
            } catch (JSONException e) {
              e.printStackTrace();
            }

            // Load icon
            if (opts.has("icon")) {
              //------------------------------
              // Case: have the icon property
              //------------------------------
              Bundle bundle = getBundleIcon(opts);

              PluginMarker.this.setIcon(marker, bundle, new PluginAsyncInterface() {
                @Override
                public void onPostExecute(final Object object) {
                  Marker marker = (Marker) object;
                  marker.setVisible(true);

                  if(!animation) {
                      try {
                          marker.setAlpha(opts.has("opacity") ? (float) opts.getDouble("opacity") : 1);
                      } catch (JSONException e) {
                          e.printStackTrace();
                      }
                  } else {
                      setAppearAnimation(marker, null);
                  }

                  callbackContext.onPostExecute(prepareResponse(marker));
                }

                @Override
                public void onError(String errorMsg) {
                  callbackContext.onError(errorMsg);
                }

              });
            } else {
              callbackContext.onPostExecute(prepareResponse(marker));
            }
          }
        });
      } catch (JSONException e) {
        e.printStackTrace();
        callbackContext.onError(e.toString());
      }
  }

  private JSONObject prepareResponse(Marker marker) {
    final JSONObject result = new JSONObject();
    final String markerId = (String) marker.getTag();

    try {
      result.put("__pgmId", markerId);

      /*
      if (icons.containsKey(markerId)) {
        Bitmap icon = icons.get(markerId);
        result.put("width", icon.getWidth() / density);
        result.put("height", icon.getHeight() / density);

      } else {
        result.put("width", 24);
        result.put("height", 42);
      }*/
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return result;
  }
}
