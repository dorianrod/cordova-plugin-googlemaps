 var utils = require('cordova/utils'),
    Overlay = require('./Overlay');
  
  var Polyline = function (map, polylineOptions, _exec) {
    Overlay.call(this, map, polylineOptions, 'Polyline', _exec, {
      'ignores': ['points', 'path']
    });
  };
  
  utils.extend(Polyline, Overlay);
  
  Polyline.prototype.getPoints = function () {
    return this.get("points");
  };
  Polyline.prototype.getPath = function () {
    return this.get("path");
  };
  Polyline.prototype.getStrokeColor = function () {
    return this.get('strokeColor');
  };
  Polyline.prototype.getStrokeWidth = function () {
    return this.get('strokeWidth');
  };
  Polyline.prototype.getVisible = function () {
    return this.get('visible');
  };
  Polyline.prototype.getClickable = function () {
    return this.get('clickable');
  };
  Polyline.prototype.getGeodesic = function () {
    return this.get('geodesic');
  };
  Polyline.prototype.getZIndex = function () {
    return this.get('zIndex');
  };
  Polyline.prototype.getOptions = function() {
    var self = this;
    return {
      '__pgmId': self.getId(),
      'visible': self.getVisible(),
      'zIndex': self.getZIndex(),
      'clickable': self.getClickable(),
      'strokeWidth': self.getStrokeWidth(),
      'strokeColor': self.getStrokeColor(),
      'geodesic': self.getGeodesic(),
      'points': self.getPoints(),
      'path': self.getPath()
    };
  };
  Polyline.prototype.setOptions = function(options) {
    if(this._isRemoved) return;
    
    this.map.updateObject("polylines", Object.assign({
      '__pgmId': this.getId()
    }, options));
  }
  
  Polyline.prototype.remove = function (callback) {
    var self = this;
    if (self._isRemoved) {
      if (typeof callback === 'function') {
        callback();
        return;
      } else {
        return Promise.resolve();
      }
    }
    Object.defineProperty(self, '_isRemoved', {
      value: true,
      writable: false
    });
  
    if (self.points) {
      self.points.empty();
    }  
    
    self.trigger(self.__pgmId + '_remove');
    self.destroy();
  
    this.map.removeObject("polylines", self.__pgmId);
  
    if (typeof callback === 'function') {
      callback();
    } else {
      return Promise.resolve();
    }
  
    return result;
  };
  
  module.exports = Polyline;
  