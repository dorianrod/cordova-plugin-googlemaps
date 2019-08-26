

  var utils = require('cordova/utils'),
  common = require('./Common'),
  Overlay = require('./Overlay'),
  LatLng = require('./LatLng'),
  MapTypeId = require('./MapTypeId'),
  event = require('./event'),
  VisibleRegion = require('./VisibleRegion'),
  Marker = require('./Marker'),
  Polyline = require('./Polyline');

var ObjectQueue = function() {
  this.queue = {};
}
ObjectQueue.prototype.addObject = function(type, obj) {
  var list = this.queue[type] || [];
  list.push(obj);
  this.queue[type] = list;
}
ObjectQueue.prototype.removeObject = function(type, id) {
  var i = this.getObjIndex(type, {
    __pgmId: id 
  });

  var data = {
    __pgmId: id,
    _removed: true
  }

  var list = this.queue[type] || [];
  if(i != null) {
    list[i] = data;
  } else {
    list.push(data);
    this.queue[type] = list;
  }
}
ObjectQueue.prototype.getObjIndex = function(type, obj) {
  let list = this.queue[type] || [];
  for(var i = 0; i < list.length; i++) {
    if(list[i].__pgmId === obj.__pgmId && obj.__pgmId) {
      return i;
    }
  }
  return null;
}
ObjectQueue.prototype.updateObject = function(type, obj) {
  var i = this.getObjIndex(type, obj);
  if(i != null) {
    let list = this.queue[type] || [];
    if(!list[i]._removed) {
      list[i] = Object.assign(list[i], obj);
    }
  } else {
    this.addObject(type, obj);
  }
}
ObjectQueue.prototype.clear = function(type, obj) {
  this.queue = {};
}
ObjectQueue.prototype.get = function() {
  let queue = Object.assign({}, this.queue);
  this.clear();
  return queue;
}


/**
 * Google Maps model.
 */
var exec;
var Map = function(__pgmId, _exec, opts) {
  var self = this;
  exec = _exec;
  Overlay.call(self, self, {}, 'Map', _exec, {
    __pgmId: __pgmId
  });
  delete self.map;

  this.objectsQueue = new ObjectQueue();

  self.set('myLocation', false);
  self.set('myLocationButton', false);

  self.MARKERS = {};
  self.OVERLAYS = {};

  var infoWindowLayer = document.createElement('div');
  infoWindowLayer.style.position = 'absolute';
  infoWindowLayer.style.left = 0;
  infoWindowLayer.style.top = 0;
  infoWindowLayer.style.width = 0;
  infoWindowLayer.style.height = 0;
  infoWindowLayer.style.overflow = 'visible';
  infoWindowLayer.style['z-index'] = 1;

  Object.defineProperty(self, '_layers', {
    value: {
      info: infoWindowLayer
    },
    enumerable: false,
    writable: false
  });

  self.on(event.MAP_CLICK, function() {
    self.set('active_marker', undefined);
  });

  self.on('active_marker_changed', function(prevMarker, newMarker) {
    var newMarkerId = newMarker ? newMarker.getId() : null;
    if (prevMarker) {
      prevMarker.hideInfoWindow.call(prevMarker);
    }
    self.exec.call(self, null, null, self.__pgmId, 'setActiveMarkerId', [newMarkerId]);
  });

  let debounceV = (opts ? opts.debounce : 0) || 200;

  self.batchQueue = common.debounce(self.batchQueue, debounceV, false, self);
};

utils.extend(Map, Overlay);

/**
 * @desc Recalculate the position of HTML elements
 */
Map.prototype.refreshLayout = function() {
  // Webkit redraw mandatory
  // http://stackoverflow.com/a/3485654/697856
  document.body.style.display = 'none';
  document.body.offsetHeight;
  document.body.style.display = '';

  this.exec.call(this, null, null, this.__pgmId, 'resizeMap', []);
};

Map.prototype.getMap = function(meta, div, options) {
  var self = this,
    args = [meta];
  options = options || {};

  self.set('clickable', options.clickable === false ? false : true);
  self.set('visible', options.visible === false ? false : true);

  if (options.controls) {
    this.set('myLocation', options.controls.myLocation === true);
    this.set('myLocationButton', options.controls.myLocationButton === true);
  }

  if (options.preferences && options.preferences.gestureBounds) {
    if (utils.isArray(options.preferences.gestureBounds) ||
        options.preferences.gestureBounds.type === 'LatLngBounds') {
      options.preferences.gestureBounds = common.convertToPositionArray(options.preferences.gestureBounds);
    }
  }

  if (!common.isDom(div)) {
    self.set('visible', false);
    options = div;
    options = options || {};
    if (options.camera) {
      if (options.camera.latLng) {
        options.camera.target = options.camera.latLng;
        delete options.camera.latLng;
      }
      this.set('camera', options.camera);
      if (options.camera.target) {
        this.set('camera_target', options.camera.target);
      }
      if (options.camera.bearing) {
        this.set('camera_bearing', options.camera.bearing);
      }
      if (options.camera.zoom) {
        this.set('camera_zoom', options.camera.zoom);
      }
      if (options.camera.tilt) {
        this.set('camera_tilt', options.camera.tilt);
      }
    }
    args.push(options);
  } else {

    var positionCSS = common.getStyle(div, 'position');
    if (!positionCSS || positionCSS === 'static') {
      // important for HtmlInfoWindow
      div.style.position = 'relative';
    }
    options = options || {};
    if (options.camera) {
      if (options.camera.latLng) {
        options.camera.target = options.camera.latLng;
        delete options.camera.latLng;
      }
      this.set('camera', options.camera);
      if (options.camera.target) {
        this.set('camera_target', options.camera.target);
      }
      if (options.camera.bearing) {
        this.set('camera_bearing', options.camera.bearing);
      }
      if (options.camera.zoom) {
        this.set('camera_zoom', options.camera.zoom);
      }
      if (options.camera.tilt) {
        this.set('camera_tilt', options.camera.tilt);
      }
    }
    if (utils.isArray(options.styles)) {
      options.styles = JSON.stringify(options.styles);
    }
    args.push(options);

    div.style.overflow = 'hidden';
    self.set('div', div);

    if (div.offsetWidth < 100 || div.offsetHeight < 100) {
      // If the map Div is too small, wait a little.
      var callee = arguments.callee;
      setTimeout(function() {
        callee.call(self, meta, div, options);
      }, 250 + Math.random() * 100);
      return;
    }

    // Gets the map div size.
    // The plugin needs to consider the viewport zoom ratio
    // for the case window.innerHTML > body.offsetWidth.
    var elemId = common.getPluginDomId(div);
    args.push(elemId);

  }

  exec.call({
    _isReady: true
  }, function() {

    //------------------------------------------------------------------------
    // Clear background colors of map div parents after the map is created
    //------------------------------------------------------------------------
    var div = self.get('div');
    if (common.isDom(div)) {

      // Insert the infoWindow layer
      if (self._layers.info.parentNode) {
        try {
          self._layers.info.parentNode.removeChild(self._layers.info.parentNode);
        } catch (e) {
          // ignore
        }
      }
      var positionCSS;
      for (var i = 0; i < div.children.length; i++) {
        positionCSS = common.getStyle(div.children[i], 'position');
        if (positionCSS === 'static') {
          div.children[i].style.position = 'relative';
        }
      }
      div.insertBefore(self._layers.info, div.firstChild);


      while (div.parentNode) {
        div.style.backgroundColor = 'rgba(0,0,0,0) !important';

        // Add _gmaps_cdv_ class
        common.attachTransparentClass(div);

        div = div.parentNode;
      }
    }
    cordova.fireDocumentEvent('plugin_touch', {
      force: true
    });

    //------------------------------------------------------------------------
    // In order to work map.getVisibleRegion() correctly, wait a little.
    //------------------------------------------------------------------------
    var waitCnt = 0;
    var waitCameraSync = function() {
      if (!self.getVisibleRegion() && (waitCnt++ < 10)) {
        setTimeout(function() {
          common.nextTick(waitCameraSync);
        }, 100);
        return;
      }


      self._privateInitialize();
      delete self._privateInitialize;
      self.refreshLayout();
      self.trigger(event.MAP_READY, self);
    };
    setTimeout(function() {
      common.nextTick(waitCameraSync);
    }, 100);
  }, self.errorHandler, 'CordovaGoogleMaps', 'getMap', args, {
    sync: true
  });
};

Map.prototype.setOptions = function(options) {
  options = options || {};

  if (options.controls) {
    var myLocation = this.get('myLocation');
    if ('myLocation' in options.controls) {
      myLocation = options.controls.myLocation === true;
    }
    var myLocationButton = this.get('myLocationButton');
    if ('myLocationButton' in options.controls) {
      myLocationButton = options.controls.myLocationButton === true;
    }
    this.set('myLocation', myLocation);
    this.set('myLocationButton', myLocationButton);
    if (myLocation === true || myLocation === false) {
      options.controls.myLocation = myLocation;
    }
    if (myLocationButton === true || myLocationButton === false) {
      options.controls.myLocationButton = myLocationButton;
    }
  }
  if (options.camera) {
    if (options.camera.latLng) {
      options.camera.target = options.camera.latLng;
      delete options.camera.latLng;
    }
    this.set('camera', options.camera);
    if (options.camera.target) {
      this.set('camera_target', options.camera.target);
    }
    if (options.camera.bearing) {
      this.set('camera_bearing', options.camera.bearing);
    }
    if (options.camera.zoom) {
      this.set('camera_zoom', options.camera.zoom);
    }
    if (options.camera.tilt) {
      this.set('camera_tilt', options.camera.tilt);
    }
  }

  if (options.preferences && options.preferences.gestureBounds) {
    if (utils.isArray(options.preferences.gestureBounds) ||
        options.preferences.gestureBounds.type === 'LatLngBounds') {
      options.preferences.gestureBounds = common.convertToPositionArray(options.preferences.gestureBounds);
    }
  }

  if (utils.isArray(options.styles)) {
    options.styles = JSON.stringify(options.styles);
  }
  this.exec.call(this, null, this.errorHandler, this.__pgmId, 'setOptions', [options]);
  return this;
};

Map.prototype.getMyLocation = function(params, success_callback, error_callback) {
  return window.plugin.google.maps.LocationService.getMyLocation.call(this, params, success_callback, error_callback);
};


Map.prototype.setCameraTarget = function(latLng) {
  this.set('camera_target', latLng);
  this.exec.call(this, null, this.errorHandler, this.__pgmId, 'setCameraTarget', [latLng.lat, latLng.lng]);
  return this;
};

Map.prototype.setCameraZoom = function(zoom) {
  this.set('camera_zoom', zoom);
  this.exec.call(this, null, this.errorHandler, this.__pgmId, 'setCameraZoom', [zoom], {
    sync: true
  });
  return this;
};
Map.prototype.panBy = function(x, y) {
  x = parseInt(x, 10);
  y = parseInt(y, 10);
  this.exec.call(this, null, this.errorHandler, this.__pgmId, 'panBy', [x, y], {
    sync: true
  });
  return this;
};

/**
 * Clears all markup that has been added to the map,
 * including markers, polylines and ground overlays.
 */
Map.prototype.clear = function(callback) {
  var self = this;
  if (self._isRemoved) {
    // Simply ignore because this map is already removed.
    return Promise.resolve();
  }

  // Close the active infoWindow
  var active_marker = self.get('active_marker');
  if (active_marker) {
    active_marker.trigger(event.INFO_CLOSE);
  }

  var clearObj = function(obj) {
    var ids = Object.keys(obj);
    var id, instance;
    for (var i = 0; i < ids.length; i++) {
      id = ids[i];
      instance = obj[id];
      if (instance) {
        if (typeof instance.remove === 'function') {
          instance.remove();
        }
        instance.off();
        delete obj[id];
      }
    }
    obj = {};
  };

  clearObj(self.OVERLAYS);
  clearObj(self.MARKERS);
  self.trigger('map_clear');

  var resolver = function(resolve, reject) {
    self.exec.call(self,
      resolve.bind(self),
      reject.bind(self),
      self.__pgmId, 'clear', [], {
        sync: true
      });
  };

  if (typeof callback === 'function') {
    resolver(callback, self.errorHandler);
  } else {
    return new Promise(resolver);
  }

};

/**
 * @desc Change the map type
 * @param {String} mapTypeId   Specifies the one of the follow strings:
 *                               MAP_TYPE_HYBRID
 *                               MAP_TYPE_SATELLITE
 *                               MAP_TYPE_TERRAIN
 *                               MAP_TYPE_NORMAL
 *                               MAP_TYPE_NONE
 */
Map.prototype.setMapTypeId = function(mapTypeId) {
  if (mapTypeId !== MapTypeId[mapTypeId.replace('MAP_TYPE_', '')]) {
    return this.errorHandler('Invalid MapTypeId was specified.');
  }
  this.set('mapTypeId', mapTypeId);
  this.exec.call(this, null, this.errorHandler, this.__pgmId, 'setMapTypeId', [mapTypeId]);
  return this;
};

/**
 * @desc Change the map view angle
 * @param {Number} tilt  The angle
 */
Map.prototype.setCameraTilt = function(tilt) {
  this.set('camera_tilt', tilt);
  this.exec.call(this, null, this.errorHandler, this.__pgmId, 'setCameraTilt', [tilt], {
    sync: true
  });
  return this;
};

/**
 * @desc Change the map view bearing
 * @param {Number} bearing  The bearing
 */
Map.prototype.setCameraBearing = function(bearing) {
  this.set('camera_bearing', bearing);
  this.exec.call(this, null, this.errorHandler, this.__pgmId, 'setCameraBearing', [bearing], {
    sync: true
  });
  return this;
};

Map.prototype.moveCameraZoomIn = function(callback) {
  var self = this;
  var cameraPosition = self.get('camera');
  cameraPosition.zoom++;
  cameraPosition.zoom = cameraPosition.zoom < 0 ? 0 : cameraPosition.zoom;

  return self.moveCamera(cameraPosition, callback);

};
Map.prototype.moveCameraZoomOut = function(callback) {
  var self = this;
  var cameraPosition = self.get('camera');
  cameraPosition.zoom--;
  cameraPosition.zoom = cameraPosition.zoom < 0 ? 0 : cameraPosition.zoom;

  return self.moveCamera(cameraPosition, callback);
};
Map.prototype.animateCameraZoomIn = function(callback) {
  var self = this;
  var cameraPosition = self.get('camera');
  cameraPosition.zoom++;
  cameraPosition.zoom = cameraPosition.zoom < 0 ? 0 : cameraPosition.zoom;
  cameraPosition.duration = 500;
  return self.animateCamera(cameraPosition, callback);
};
Map.prototype.animateCameraZoomOut = function(callback) {
  var self = this;
  var cameraPosition = self.get('camera');
  cameraPosition.zoom--;
  cameraPosition.zoom = cameraPosition.zoom < 0 ? 0 : cameraPosition.zoom;
  cameraPosition.duration = 500;
  return self.animateCamera(cameraPosition, callback);
};
/**
 * Move the map camera with animation
 * @name animateCamera
 * @param {CameraPosition} cameraPosition - New camera position
 * @param {Function} [callback] - This callback is involved when the animation is completed.
 * @return {Promise} if you omit `callback`.
 */
Map.prototype.animateCamera = function(cameraPosition, callback) {
  var self = this,
    error;

  var target = cameraPosition.target;
  if (!target && 'position' in cameraPosition) {
    target = cameraPosition.position;
  }
  if (!target) {
    error = new Error('No target field is specified.');
    if (typeof callback === 'function') {
      throw error;
    } else {
      return Promise.reject(error);
    }
  }
  // if (!('padding' in cameraPosition)) {
  //   cameraPosition.padding = 10;
  // }

  if (utils.isArray(target) || target.type === 'LatLngBounds') {
    target = common.convertToPositionArray(target);
    if (target.length === 0) {
      // skip if no point is specified
      error = new Error('No point is specified.');
      if (typeof callback === 'function') {
        throw error;
      } else {
        return Promise.reject(error);
      }
    }
  }
  cameraPosition.target = target;

  var resolver = function(resolve, reject) {

    self.exec.call(self,
      resolve.bind(self),
      reject.bind(self),
      self.__pgmId, 'animateCamera', [cameraPosition], {
        sync: true
      });
  };

  if (typeof callback === 'function') {
    resolver(callback, self.errorHandler);
  } else {
    return new Promise(resolver);
  }
};
/**
 * Move the map camera without animation
 *
 * @name moveCamera
 * @param {CameraPosition} - cameraPosition New camera position
 * @param {Function} [callback] - This callback is involved when the animation is completed.
 * @return {Promise} if you omit `callback`.
 */
Map.prototype.moveCamera = function(cameraPosition, callback) {
  var self = this;
  var target = cameraPosition.target;
  if (!target && 'position' in cameraPosition) {
    target = cameraPosition.position;
  }
  if (!target) {
    return Promise.reject('No target field is specified.');
  }

  // if (!('padding' in cameraPosition)) {
  //   cameraPosition.padding = 10;
  // }
  if (utils.isArray(target) || target.type === 'LatLngBounds') {
    target = common.convertToPositionArray(target);
    if (target.length === 0) {
      // skip if no point is specified
      if (typeof callback === 'function') {
        callback.call(self);
        return;
      } else {
        return Promise.reject('No point is specified.');
      }
    }
  }
  cameraPosition.target = target;

  var resolver = function(resolve, reject) {

    self.exec.call(self,
      resolve.bind(self),
      reject.bind(self),
      self.__pgmId, 'moveCamera', [cameraPosition], {
        sync: true
      });
  };

  if (typeof callback === 'function') {
    resolver(callback, self.errorHandler);
  } else {
    return new Promise(resolver);
  }
};

Map.prototype.setMyLocationButtonEnabled = function(enabled) {
  var self = this;
  enabled = common.parseBoolean(enabled);
  this.set('myLocationButton', enabled);
  self.exec.call(self, null, this.errorHandler, this.__pgmId, 'setMyLocationEnabled', [{
    myLocationButton: enabled,
    myLocation: self.get('myLocation') === true
  }], {
    sync: true
  });
  return this;
};

Map.prototype.setMyLocationEnabled = function(enabled) {
  var self = this;
  enabled = common.parseBoolean(enabled);
  this.set('myLocation', enabled);
  self.exec.call(self, null, this.errorHandler, this.__pgmId, 'setMyLocationEnabled', [{
    myLocationButton: self.get('myLocationButton') === true,
    myLocation: enabled
  }], {
    sync: true
  });
  return this;
};

Map.prototype.setIndoorEnabled = function(enabled) {
  enabled = common.parseBoolean(enabled);
  this.exec.call(this, null, this.errorHandler, this.__pgmId, 'setIndoorEnabled', [enabled]);
  return this;
};
Map.prototype.setTrafficEnabled = function(enabled) {
  enabled = common.parseBoolean(enabled);
  this.exec.call(this, null, this.errorHandler, this.__pgmId, 'setTrafficEnabled', [enabled]);
  return this;
};
Map.prototype.setCompassEnabled = function(enabled) {
  var self = this;
  enabled = common.parseBoolean(enabled);
  self.exec.call(self, null, self.errorHandler, this.__pgmId, 'setCompassEnabled', [enabled]);
  return this;
};
Map.prototype.getFocusedBuilding = function(callback) {
  var self = this;
  var resolver = function(resolve, reject) {
    self.exec.call(self,
      resolve.bind(self),
      reject.bind(self),
      self.__pgmId, 'getFocusedBuilding', []);
  };

  if (typeof callback === 'function') {
    resolver(callback, self.errorHandler);
  } else {
    return new Promise(resolver);
  }
};
Map.prototype.getVisible = function() {
  return this.get('visible');
};
Map.prototype.setVisible = function(isVisible) {
  cordova.fireDocumentEvent('plugin_touch');
  var self = this;
  isVisible = common.parseBoolean(isVisible);
  self.set('visible', isVisible);
  self.exec.call(self, null, self.errorHandler, this.__pgmId, 'setVisible', [isVisible]);
  return this;
};

Map.prototype.setClickable = function(isClickable) {
  cordova.fireDocumentEvent('plugin_touch');
  var self = this;
  isClickable = common.parseBoolean(isClickable);
  self.set('clickable', isClickable);
  self.exec.call(self, null, self.errorHandler, this.__pgmId, 'setClickable', [isClickable]);
  return this;
};
Map.prototype.getClickable = function() {
  return this.get('clickable');
};


/**
 * Sets the preference for whether all gestures should be enabled or disabled.
 */
Map.prototype.setAllGesturesEnabled = function(enabled) {
  var self = this;
  enabled = common.parseBoolean(enabled);
  self.exec.call(self, null, self.errorHandler, this.__pgmId, 'setAllGesturesEnabled', [enabled]);
  return this;
};

/**
 * Return the current position of the camera
 * @return {CameraPosition}
 */
Map.prototype.getCameraPosition = function() {
  return this.get('camera');
};

/**
 * Remove the map completely.
 */
Map.prototype.remove = function(callback) {
  var self = this;
  if (self._isRemoved) {
    return;
  }
  Object.defineProperty(self, '_isRemoved', {
    value: true,
    writable: false
  });

  self.trigger('remove');

  // Close the active infoWindow
  var active_marker = self.get('active_marker');
  if (active_marker) {
    active_marker.trigger(event.INFO_CLOSE);
  }

  var clearObj = function(obj) {
    var ids = Object.keys(obj);
    var id, instance;
    for (var i = 0; i < ids.length; i++) {
      id = ids[i];
      instance = obj[id];
      if (instance) {
        if (typeof instance.remove === 'function') {
          instance.remove();
        }
        instance.off();
        delete obj[id];
      }
    }
    obj = {};
  };

  clearObj(self.OVERLAYS);
  clearObj(self.MARKERS);


  var resolver = function(resolve, reject) {
    self.exec.call(self,
      resolve.bind(self),
      reject.bind(self),
      'CordovaGoogleMaps', 'removeMap', [self.__pgmId],
      {
        sync: true,
        remove: true
      });
  };

  if (typeof callback === 'function') {
    resolver(callback, self.errorHandler);
  } else {
    return new Promise(resolver);
  }
};


Map.prototype.toDataURL = function(params, callback) {
  var args = [params || {}, callback];
  if (typeof args[0] === 'function') {
    args.unshift({});
  }

  params = args[0];
  callback = args[1];

  params.uncompress = params.uncompress === true;
  var self = this;

  var resolver = function(resolve, reject) {
    self.exec.call(self,
      resolve.bind(self),
      reject.bind(self),
      self.__pgmId, 'toDataURL', [params]);
  };

  if (typeof callback === 'function') {
    resolver(callback, self.errorHandler);
  } else {
    return new Promise(resolver);
  }
};

/**
 * Show the map into the specified div.
 */
Map.prototype.getDiv = function() {
  return this.get('div');
};

/**
 * Show the map into the specified div.
 */
Map.prototype.setDiv = function(div) {
  var self = this,
    args = [];

  if (!common.isDom(div)) {
    div = self.get('div');
    if (common.isDom(div)) {
      div.removeAttribute('__pluginMapId');
    }
    self.set('div', null);
  } else {
    div.setAttribute('__pluginMapId', self.__pgmId);

    // Insert the infoWindow layer
    if (self._layers.info.parentNode) {
      try {
        self._layers.info.parentNode.removeChild(self._layers.info.parentNode);
      } catch(e) {
        //ignore
      }
    }
    var positionCSS;
    for (var i = 0; i < div.children.length; i++) {
      positionCSS = common.getStyle(div.children[i], 'position');
      if (positionCSS === 'static') {
        div.children[i].style.position = 'relative';
      }
    }
    div.insertBefore(self._layers.info, div.firstChild);

    // Webkit redraw mandatory
    // http://stackoverflow.com/a/3485654/697856
    div.style.display = 'none';
    div.offsetHeight;
    div.style.display = '';

    self.set('div', div);

    if (cordova.platform === 'browser') {
      return;
    }


    positionCSS = common.getStyle(div, 'position');
    if (!positionCSS || positionCSS === 'static') {
      div.style.position = 'relative';
    }
    var elemId = common.getPluginDomId(div);
    args.push(elemId);
    while (div.parentNode) {
      div.style.backgroundColor = 'rgba(0,0,0,0)';

      // Add _gmaps_cdv_ class
      common.attachTransparentClass(div);

      div = div.parentNode;
    }
  }
  self.exec.call(self, function() {
    cordova.fireDocumentEvent('plugin_touch', {
      force: true,
      action: 'setDiv'
    });
    self.refreshLayout();
  }, self.errorHandler, self.__pgmId, 'setDiv', args, {
    sync: true
  });
  return self;
};

/**
 * Return the visible region of the map.
 */
Map.prototype.getVisibleRegion = function(callback) {
  var self = this;
  var cameraPosition = self.get('camera');
  if (!cameraPosition || !cameraPosition.southwest || !cameraPosition.northeast) {
    return null;
  }

  var latLngBounds = new VisibleRegion(
    cameraPosition.southwest,
    cameraPosition.northeast,
    cameraPosition.farLeft,
    cameraPosition.farRight,
    cameraPosition.nearLeft,
    cameraPosition.nearRight
  );

  if (typeof callback === 'function') {
    console.log('[deprecated] getVisibleRegion() is changed. Please check out the https://goo.gl/yHstHQ');
    callback.call(self, latLngBounds);
  }
  return latLngBounds;
};

/**
 * Maps an Earth coordinate to a point coordinate in the map's view.
 */
Map.prototype.fromLatLngToPoint = function(latLng, callback) {
  var self = this;

  if ('lat' in latLng && 'lng' in latLng) {

    var resolver = function(resolve, reject) {
      self.exec.call(self,
        resolve.bind(self),
        reject.bind(self),
        self.__pgmId, 'fromLatLngToPoint', [latLng.lat, latLng.lng]);
    };

    if (typeof callback === 'function') {
      resolver(callback, self.errorHandler);
    } else {
      return new Promise(resolver);
    }
  } else {
    var rejector = function(resolve, reject) {
      reject('The latLng is invalid');
    };

    if (typeof callback === 'function') {
      rejector(callback, self.errorHandler);
    } else {
      return new Promise(rejector);
    }
  }

};
/**
 * Maps a point coordinate in the map's view to an Earth coordinate.
 */
Map.prototype.fromPointToLatLng = function(pixel, callback) {
  var self = this;
  if (typeof pixel === 'object' && 'x' in pixel && 'y' in pixel) {
    pixel = [pixel.x, pixel.y];
  }
  if (pixel.length == 2 && utils.isArray(pixel)) {

    var resolver = function(resolve, reject) {
      self.exec.call(self,
        function(result) {
          var latLng = new LatLng(result[0] || 0, result[1] || 0);
          resolve.call(self, latLng);
        },
        reject.bind(self),
        self.__pgmId, 'fromPointToLatLng', [pixel[0], pixel[1]]);
    };

    if (typeof callback === 'function') {
      resolver(callback, self.errorHandler);
    } else {
      return new Promise(resolver);
    }
  } else {
    var rejector = function(resolve, reject) {
      reject('The pixel[] argument is invalid');
    };

    if (typeof callback === 'function') {
      rejector(callback, self.errorHandler);
    } else {
      return new Promise(rejector);
    }
  }

};

Map.prototype.setPadding = function(p1, p2, p3, p4) {
  if (arguments.length === 0 || arguments.length > 4) {
    return this;
  }
  var padding = {};
  padding.top = parseInt(p1, 10);
  switch (arguments.length) {
  case 4:
    // top right bottom left
    padding.right = parseInt(p2, 10);
    padding.bottom = parseInt(p3, 10);
    padding.left = parseInt(p4, 10);
    break;

  case 3:
    // top right&left bottom
    padding.right = parseInt(p2, 10);
    padding.left = padding.right;
    padding.bottom = parseInt(p3, 10);
    break;

  case 2:
    // top & bottom right&left
    padding.bottom = parseInt(p1, 10);
    padding.right = parseInt(p2, 10);
    padding.left = padding.right;
    break;

  case 1:
    // top & bottom right & left
    padding.bottom = padding.top;
    padding.right = padding.top;
    padding.left = padding.top;
    break;
  }
  this.exec.call(this, null, self.errorHandler, this.__pgmId, 'setPadding', [padding]);
  return this;
};



Map.prototype.addObject = function(type, options) {
  this.objectsQueue.addObject(type, options);
  this.batchQueue();
};
Map.prototype.updateObject = function(type, obj) {
  this.objectsQueue.updateObject(type, obj);
  this.batchQueue();
};
Map.prototype.removeObject = function(type, obj) {
  this.objectsQueue.removeObject(type, obj);
  this.batchQueue();
};



Map.prototype.addPolyline = function(markerOptions) {
  this.addObject("polylines", markerOptions);
};

function preparePolyline(map, polylineOptions) {
  var self = map;
  var polyline;
  var id = polylineOptions.__pgmId;

  if(!id) {
    polylineOptions.points = polylineOptions.points || [];
    var _orgs = polylineOptions.points;
    
    polylineOptions.points = common.convertToPositionArray(polylineOptions.points);
    polylineOptions.strokeColor = common.HTMLColor2RGBA(polylineOptions.strokeColor || '#FF000080', 0.75);
    polylineOptions.strokeWidth = 'strokeWidth' in polylineOptions ? polylineOptions.strokeWidth : 10;
    polylineOptions.visible = common.defaultTrueOption(polylineOptions.visible);
    polylineOptions.clickable = polylineOptions.clickable === true;
    polylineOptions.zIndex = polylineOptions.zIndex || 0;
    polylineOptions.geodesic = polylineOptions.geodesic === true;

  //  var opts = JSON.parse(JSON.stringify(polylineOptions));
    polylineOptions.points = _orgs;
    polyline = new Polyline(self, polylineOptions, exec);
    var polylineId = polyline.getId();
    self.OVERLAYS[polylineId] = polyline;

    polyline.one(polylineId + '_remove', function() {
      polyline.off();
      delete self.OVERLAYS[polylineId];
      polyline = undefined;
    });

  } else {
    var polyline = self.OVERLAYS[id];
    if(polyline) {
      if(polylineOptions.strokeColor) {
        polylineOptions.strokeColor = common.HTMLColor2RGBA(polylineOptions.strokeColor || '#FF000080', 0.75);
      }
    }
  }
  return polyline;
}

function afterPolylineAdded(polyline, result, polylineOptions) {
  if(polyline) polyline._privateInitialize(polylineOptions);
  //delete polyline._privateInitialize;
}






Map.prototype.batchQueue = function(callback) {
  this.batch(this.objectsQueue.get(), function() {
    if(callback) callback.apply(this, arguments);
    console.log("batchqueue", arguments);
  });
};

Map.prototype.batch = function(groups, callback) {
  var map = this;

  var markersOptions     = groups.markers || [];
  var polylinesOptions   = groups.polylines || [];

  var markersOptionsById = {};
  var polylinesOptionsById = {};

  markersOptions.forEach(function(markerOpt) {
    var marker = prepareMarker(map, markerOpt);
    if(marker) {
      markerOpt.hashCode = marker.hashCode;
      markersOptionsById[marker.getId()] = markerOpt;
    }
  });

  polylinesOptions.forEach(function(polylineOpt) {
    var polyline = preparePolyline(map, polylineOpt);
    if(polyline) {
      polylineOpt.hashCode = polyline.hashCode;
      polylinesOptionsById[polyline.getId()] = polylineOpt;
    }
  });

  this.exec.call(map, function(result) {
    let resMarkers = result.markers || [];
    let resPolylines = result.polylines || [];

    for(var k in resMarkers) {
      var resMarker = resMarkers[k] || {};
      var id        = resMarker.__pgmId;
      var marker    = map.MARKERS[id];
      if(marker) {
        afterMarkerAdded(marker, resMarker, markersOptionsById[id]);
      }
    }

    for(var k in resPolylines) {
      var resPoly   = resPolylines[k] || {};
      var id        = resPoly.__pgmId;
      var polyline  = map.OVERLAYS[id];
      if(polyline) {
        afterPolylineAdded(polyline, resPoly, polylinesOptionsById[id]);
      }
    }

    if (typeof callback === 'function') {
      callback.call(self, result);
    }

  }, this.errorHandler, this.__pgmId, 'batch', [groups]);
  return this;
}





function prepareMarker(map, markerOptions) {
  var self = map;
  var marker;
  
  var id = markerOptions.__pgmId;

  if(!id) {
    //------------------------------------
    // Generate a makrer instance at once.
    //------------------------------------
    markerOptions = common.markerOptionsFilter(markerOptions);
    markerOptions.icon = markerOptions.icon || {
      url: 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEAeAB4AAD/2wBDAAIBAQIBAQICAgICAgICAwUDAwMDAwYEBAMFBwYHBwcGBwcICQsJCAgKCAcHCg0KCgsMDAwMBwkODw0MDgsMDAz/2wBDAQICAgMDAwYDAwYMCAcIDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAz/wAARCAACAAIDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFAEBAAAAAAAAAAAAAAAAAAAAAP/EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAMAwEAAhEDEQA/AL+AA//Z'
    };
    if (typeof markerOptions.icon === 'string' || Array.isArray(markerOptions.icon)) {
      markerOptions.icon = {
        url: markerOptions.icon
      };
    }

    marker = new Marker(self, markerOptions, exec);
    var markerId = marker.getId();

    self.MARKERS[markerId] = marker;
    self.OVERLAYS[markerId] = marker;
    marker.one(markerId + '_remove', function() {
      delete self.MARKERS[markerId];
      delete self.OVERLAYS[markerId];
      marker.destroy();
      marker = undefined;
    });
  } else {
    var marker = self.MARKERS[id];
    if(marker) {
      if (typeof markerOptions.icon === 'string' || Array.isArray(markerOptions.icon)) {
        markerOptions.icon = {
          url: markerOptions.icon
        };
      }
    }
  }

  return marker;
}
function afterMarkerAdded(marker, result, markerOptions) {
  if(marker) {
    if(markerOptions.icon) {
      markerOptions.icon.size         = markerOptions.icon.size || {};
      markerOptions.icon.size.width   = markerOptions.icon.size.width || result.width;
      markerOptions.icon.size.height  = markerOptions.icon.size.height || result.height;
      markerOptions.icon.anchor       = markerOptions.icon.anchor || [markerOptions.icon.size.width / 2, markerOptions.icon.size.height]
    };

    marker._privateInitialize(markerOptions);
    //delete marker._privateInitialize;
  }
}

Map.prototype.addMarker = function(markerOptions) {
  this.addObject("markers", markerOptions);
};


/*****************************************************************************
 * Callbacks from the native side
 *****************************************************************************/

Map.prototype._onSyncInfoWndPosition = function(eventName, points) {
  this.set('infoPosition', points);
};

Map.prototype._onMapEvent = function(eventName) {
  if (!this._isReady) {
    return;
  }
  var args = [eventName];
  for (var i = 1; i < arguments.length; i++) {
    args.push(arguments[i]);
  }
  this.trigger.apply(this, args);
};

Map.prototype._onMarkerEvent = function(eventName, markerId, position) {
  var self = this;
  var marker = self.MARKERS[markerId] || null;

  if (marker) {
    marker.set('position', position);
    if (eventName === event.INFO_OPEN) {
      marker.set('isInfoWindowVisible', true);
    }
    if (eventName === event.INFO_CLOSE) {
      marker.set('isInfoWindowVisible', false);
    }
    marker.trigger(eventName, position, marker);
  }
};

Map.prototype._onOverlayEvent = function(eventName, overlayId) {
  var self = this;
  var overlay = self.OVERLAYS[overlayId] || null;

  if (overlay) {
    var args = [eventName];
    for (var i = 2; i < arguments.length; i++) {
      args.push(arguments[i]);
    }
    args.push(overlay); // for ionic
    overlay.trigger.apply(overlay, args);
  }
};

Map.prototype.getCameraTarget = function() {
  return this.get('camera_target');
};

Map.prototype.getCameraZoom = function() {
  return this.get('camera_zoom');
};
Map.prototype.getCameraTilt = function() {
  return this.get('camera_tilt');
};
Map.prototype.getCameraBearing = function() {
  return this.get('camera_bearing');
};

Map.prototype._onCameraEvent = function(eventName, cameraPosition) {
  this.set('camera', cameraPosition);
  this.set('camera_target', cameraPosition.target);
  this.set('camera_zoom', cameraPosition.zoom);
  this.set('camera_bearing', cameraPosition.bearing);
  this.set('camera_tilt', cameraPosition.viewAngle || cameraPosition.tilt);
  this.set('camera_northeast', cameraPosition.northeast);
  this.set('camera_southwest', cameraPosition.southwest);
  this.set('camera_nearLeft', cameraPosition.nearLeft);
  this.set('camera_nearRight', cameraPosition.nearRight);
  this.set('camera_farLeft', cameraPosition.farLeft);
  this.set('camera_farRight', cameraPosition.farRight);
  if (this._isReady) {
    this.trigger(eventName, cameraPosition, this);
  }
};

module.exports = Map;


