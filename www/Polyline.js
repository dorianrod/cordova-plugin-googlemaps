var utils = require('cordova/utils'),
  common = require('./Common'),
  Overlay = require('./Overlay');

var Polyline = function (map, polylineOptions, _exec) {
  Overlay.call(this, map, polylineOptions, 'Polyline', _exec, {
    'ignores': ['points', 'path']
  });
};

utils.extend(Polyline, Overlay);

Polyline.prototype.getIcons = function () {
  return this.get("icons");
};
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
    'icons': self.getIcons(),
    'clickable': self.getClickable(),
    'strokeWidth': self.getStrokeWidth(),
    'strokeColor': self.getStrokeColor(),
    'geodesic': self.getGeodesic()
  };
};
Polyline.prototype.setOptions = function(options) {
  if(this._isRemoved) return;

  var opts = common.polylineOptionsFilter(Object.assign({
    '__pgmId': this.getId()
  }, options));
  
  this.map.updateObject("polylines", opts);
}

Polyline.prototype.remove = function () {
  var self = this;
  if (self._isRemoved) {
    return;
  }
  if (self.points) {
    self.points.empty();
  }  
  this.map.removeObject("polylines", self.__pgmId);
};

module.exports = Polyline;