package plugin.google.maps;

import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.Cap;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Dot;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class PolylineResult {
  List<LatLng> points;
  LatLngBounds bounds;
}

public class PluginPolyline extends MyPlugin implements MapElementInterface, MyPluginInterface   {

    protected final HashMap<String, ArrayList<String>> markerKeys = new HashMap<>();

  /**
   * Create polyline
   * @param args
   * @param callbackContext
   * @throws JSONException
   */
  public void create(final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    //
  }



  public void remove(final String id, final PluginAsyncInterface callbackContext)  {
    final Polyline polyline = this.getPolyline(id);
    if (polyline == null) {
      callbackContext.onPostExecute(null);
      return;
    }


    pluginMap.objects.remove(id);
    pluginMap.objects.remove(id.replace("polyline_", "polyline_property_"));
    pluginMap.objects.remove(id.replace("polyline_", "polyline_bounds_"));


      final CountDownLatch waiter = new CountDownLatch(2);

      cordova.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
              try {
                  polyline.remove();
              } finally {
                  waiter.countDown();
              }
          }
      });

      removePolylineMarkers(id, new PluginAsyncInterface() {
          @Override
          public void onPostExecute(Object object) {
              waiter.countDown();
          }

          @Override
          public void onError(String errorMsg) {
              waiter.countDown();
          }
      });

      try {
          waiter.await();
      } catch (InterruptedException e) {
          e.printStackTrace();
      }

      callbackContext.onPostExecute(null);
  }

  void removePolylineMarkers(String id, PluginAsyncInterface callback) {
      ArrayList<String> ids = markerKeys.get(id);

      if (ids != null && ids.size() > 0) {
          PluginMarker pluginMarker = (PluginMarker) pluginMap.getPluginInstance("Marker");

          //Delete previous markers
          if (ids != null) {
              final CountDownLatch waiter = new CountDownLatch(ids.size());
              for (int i = 0; i < ids.size(); i++) {
                  pluginMarker.remove((String) ids.get(i), new PluginAsyncInterface() {

                      @Override
                      public void onPostExecute(Object object) {
                          waiter.countDown();
                      }

                      @Override
                      public void onError(String errorMsg) {
                          waiter.countDown();
                      }
                  });
              }
              try {
                  waiter.await();
                  markerKeys.remove(id);
              } catch (InterruptedException e) {
                  e.printStackTrace();
              }
          }
      }

      callback.onPostExecute(null);
  }

  void getPolyline(final JSONObject opts, final PluginAsyncInterface callbackContext) {
    try {
      final PolylineResult result = new PolylineResult();
      final List<LatLng> list = new ArrayList<>();

      List<LatLng> listPoints = null;

      if (opts.has("points")) {
        JSONArray points = opts.getJSONArray("points");
        listPoints = PluginUtil.JSONArray2LatLngList(points);
      }
      if (opts.has("path")) {
        String path = opts.getString("path");
        listPoints = PolyUtil.decode(path);
      }

      if(listPoints != null) {
        List<LatLng> simplified = PolyUtil.simplify(listPoints, 100); //100m
        int i = 0;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        for (i = 0; i < simplified.size(); i++) {
          list.add(simplified.get(i));
          builder.include(simplified.get(i));
        }
        LatLngBounds bounds = builder.build();
        result.bounds = bounds;
        result.points = list;
      }

      callbackContext.onPostExecute(result);
    }
    catch (Exception e) {
      e.printStackTrace();
      callbackContext.onError(e.toString());
    }
  }

  protected void cachePolylineBounds(String hashCode, LatLngBounds bounds) {
    String boundsId = "polyline_bounds_" + hashCode;
    pluginMap.objects.put(boundsId, bounds);

  }

  public void updateItem(final String id, JSONObject opts, final PluginAsyncInterface callbackContext) {
    final Polyline polyline = this.getPolyline(id);
    if(polyline != null) {
        synchronized(polyline) {
            boolean hasPoints = opts.has("points") || opts.has("path");
            final List<PolylineResult> polyResult = new ArrayList<>();

            if (hasPoints) {

                final CountDownLatch waiter = new CountDownLatch(1);
                getPolyline(opts, new PluginAsyncInterface() {

                    @Override
                    public void onPostExecute(Object object) {
                        PolylineResult polylineResult = (PolylineResult) object;
                        polyResult.add(polylineResult);
                        waiter.countDown();
                    }

                    @Override
                    public void onError(String errorMsg) {
                        waiter.countDown();
                    }
                });

                try {
                    waiter.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            final CountDownLatch waiter = new CountDownLatch(1);

            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    try {
                        Iterator<String> it = opts.keys();
                        String hashCode = id.replace("polyline_", "");

                        while (it.hasNext()) {
                            String key = it.next();

                            switch (key) {
                                case "pattern":
                                    PolylineOptions polyOptions = new PolylineOptions();
                                    getPattern(opts, polyOptions);
                                    polyline.setPattern(polyOptions.getPattern());
                                    break;
                                case "strokeColor":
                                    int color = PluginUtil.parsePluginColor(opts.getJSONArray(key));
                                    polyline.setColor(color);
                                    break;
                                case "strokeWidth":
                                    float width = (float) (opts.getDouble(key) * density);
                                    polyline.setWidth(width);
                                    break;
                                case "zIndex":
                                    float zIndex = (float) opts.getDouble(key);
                                    polyline.setZIndex(zIndex);
                                    break;
                                case "geodesic":
                                    boolean geodesic = opts.getBoolean(key);
                                    polyline.setGeodesic(geodesic);
                                    break;
                                case "visible":
                                    boolean visible = opts.getBoolean(key);
                                    polyline.setVisible(visible);
                                    break;
                                case "clickable":
                                    boolean clickable = opts.getBoolean(key);
                                    polyline.setClickable(clickable);

                                    String propertyId = "polyline_property_" + hashCode;
                                    JSONObject properties = pluginMap.objects.containsKey(propertyId) ? (JSONObject) pluginMap.objects.get(propertyId) : null;
                                    properties.put("isClickable", clickable);
                                    pluginMap.objects.put(propertyId, properties);

                                    break;
                            }
                        }

                        if (polyResult.size() > 0) {
                            PolylineResult result = polyResult.get(0);
                            polyline.setPoints(result.points);
                            cachePolylineBounds(hashCode, result.bounds);
                        }

                    } catch (Exception e) {

                        e.printStackTrace();
                    } finally {
                        waiter.countDown();
                    }
                }
            });

            try {
                waiter.await();
            }catch(Exception e) {
                e.printStackTrace();
            }

            updatePolylineIcons(opts, new PluginAsyncInterface() {

                @Override
                public void onPostExecute(Object object) {
                    callbackContext.onPostExecute(prepareResponse(id));
                }

                @Override
                public void onError(String errorMsg) {
                    callbackContext.onError(errorMsg);
                }
            });

        }
    } else {
      callbackContext.onError("No polyline anymore");
    }
  }



    public void updatePolylineIcons(JSONObject opts, PluginAsyncInterface callback) {
        try {
            PluginMarker pluginMarker = (PluginMarker) pluginMap.getPluginInstance("Marker");

            String id = opts.getString("__pgmId");
            final JSONArray icons = opts.has("icons") ? opts.getJSONArray("icons") : null;



            final CountDownLatch waiter = new CountDownLatch((icons != null ? 1 + icons.length() : 0));

            JSONArray strokeColor = opts.has("strokeColor") ? opts.getJSONArray("strokeColor") : null;
            Float alpha = strokeColor != null && strokeColor.length() >= 3 ? (float) strokeColor.getDouble(3) / 255 : null;

            ArrayList<String> ids = markerKeys.get(id);

            if(alpha != null && ids != null && ids.size() > 0) {
                synchronized(ids) {
                    for(int i = 0; i < ids.size(); i++) {
                        String idmarker = ids.get(i);
                        Marker marker = pluginMarker.getMarker(idmarker);
                        if(marker != null) {
                            synchronized(marker) {
                                cordova.getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        marker.setAlpha(alpha);
                                    }
                                });
                            }
                        }
                    }
                }
            }

            //Create new markers
            if (icons != null) {

                ArrayList<String> markersIds = new ArrayList();

                removePolylineMarkers(id, new PluginAsyncInterface() {
                    @Override
                    public void onPostExecute(Object object) {
                        waiter.countDown();
                    }

                    @Override
                    public void onError(String errorMsg) {
                        waiter.countDown();
                    }
                });

                for (int i = 0; i < icons.length(); i++) {
                    try {
                        String hashCode = id + "-" + i;

                        JSONObject marker = icons.getJSONObject(i);
                        if(alpha != null) {
                            marker.put("opacity", alpha);
                        }

                        marker.put("__pgmId", "marker_" + hashCode);
                        markersIds.add(marker.getString("__pgmId"));

                        pluginMarker.createItem(hashCode, marker, new PluginAsyncInterface() {

                            @Override
                            public void onPostExecute(Object object) {
                                waiter.countDown();
                            }

                            @Override
                            public void onError(String errorMsg) {
                                waiter.countDown();
                            }
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        waiter.countDown();
                    }
                }

                markerKeys.put(id, markersIds);
            }

            waiter.await();


            if(callback != null) callback.onPostExecute(null);
        } catch(Exception e) {
            e.printStackTrace();
            if(callback != null) callback.onError(e.toString());
        }
    }

    public void getPattern(JSONObject opts, PolylineOptions polyOptions) {
        try {
            if(opts.has("pattern")) {
                String pattern = opts.getString("pattern");
                List<PatternItem> patternItem = null;
                if("dash".equals(pattern)) {
                    patternItem = Arrays.asList(new Dash(20), new Gap(20));
                } else {
                    float strokeWidth = opts.has("strokeWidth") ? (float) (opts.getDouble("strokeWidth")) : 1;
                    patternItem = Arrays.asList(new Dot(), new Gap(5 + strokeWidth));
                }
                if(patternItem != null) {
                    polyOptions.pattern(patternItem);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
  public void createItem(final String hashCode, JSONObject opts, final PluginAsyncInterface callbackContext) {

    self = this;


    getPolyline(opts, new PluginAsyncInterface(){

      @Override
      public void onPostExecute(Object object) {

        try {

          final PolylineOptions polylineOptions = new PolylineOptions();
          final JSONObject properties = new JSONObject();
          final PolylineResult polylineResult = (PolylineResult) object;
          if(polylineResult == null) {
              callbackContext.onError("no polyline");
              return;
          }

          int color;

          if (opts.has("strokeColor")) {
            color = PluginUtil.parsePluginColor(opts.getJSONArray("strokeColor"));
            polylineOptions.color(color);
          }
          if (opts.has("strokeWidth")) {
            polylineOptions.width((float) (opts.getDouble("strokeWidth") * density));
          }
          if (opts.has("visible")) {
            polylineOptions.visible(opts.getBoolean("visible"));
          }
          if (opts.has("geodesic")) {
            polylineOptions.geodesic(opts.getBoolean("geodesic"));
          }
          if (opts.has("zIndex")) {
            polylineOptions.zIndex(opts.getInt("zIndex"));
          }
          if (opts.has("clickable")) {
            properties.put("isClickable", opts.getBoolean("clickable"));
          } else {
            properties.put("isClickable", true);
          }
          properties.put("isVisible", polylineOptions.isVisible());

            getPattern(opts, polylineOptions);

            final CountDownLatch waiter = new CountDownLatch(1);
            updatePolylineIcons(opts, new PluginAsyncInterface(){
                @Override
                public void onPostExecute(Object object) {
                    waiter.countDown();
                }

                @Override
                public void onError(String errorMsg) {
                    waiter.countDown();
                }
            });

            try {
                waiter.await();
            } catch(InterruptedException e) {
                e.printStackTrace();
            }

          // Since this plugin provide own click detection,
          // disable default clickable feature.
          polylineOptions.clickable(false);

          polylineOptions.addAll(polylineResult.points);

          cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
              Polyline polyline = map.addPolyline(polylineOptions);
              polyline.setTag(hashCode);

              String id = "polyline_" + hashCode;
              pluginMap.objects.put(id, polyline);

              cachePolylineBounds(hashCode, polylineResult.bounds);

              String propertyId = "polyline_property_" + hashCode;
              pluginMap.objects.put(propertyId, properties);
              callbackContext.onPostExecute(prepareResponse(id));

            }
          });

        } catch(Exception e) {
          e.printStackTrace();
          callbackContext.onError(e.toString());
        }
      }

      @Override
      public void onError(String errorMsg) {
        callbackContext.onError(errorMsg);
      }
    });
  }

    private JSONObject prepareResponse(String polylineId) {
        final JSONObject result = new JSONObject();

        try {
            result.put("__pgmId", polylineId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

  private JSONObject prepareResponse(Polyline polyline) {
    final String polylineId = "polyline_" + (String) polyline.getTag(); //"polyline_" + polyline.hashCode();
    return prepareResponse(polylineId);
  }
}


