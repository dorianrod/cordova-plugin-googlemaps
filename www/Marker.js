
var utils = require('cordova/utils'),
  LatLng = require('./LatLng'),
  common = require('./Common'),
  event = require('./event'),
  Overlay = require('./Overlay');

var Marker = function(map, markerOptions, _exec, extras) {
  extras = extras || {};
  Overlay.call(this, map, markerOptions, extras.className || 'Marker', _exec, extras);

  var self = this;

  if (markerOptions && markerOptions.position) {
    markerOptions.position.lat = parseFloat(markerOptions.position.lat);
    markerOptions.position.lng = parseFloat(markerOptions.position.lng);
    self.set('position', markerOptions.position);
  }
};

utils.extend(Marker, Overlay);

Marker.prototype.isDraggable = function() {
  return this.get('draggable');
};
Marker.prototype.getTitle = function() {
  return this.get('title');
};
Marker.prototype.getSnippet = function() {
  return this.get('snippet');
};
Marker.prototype.getRotation = function() {
  return this.get('rotation');
};
Marker.prototype.isVisible = function() {
  return this.get('visible') === true;
};
Marker.prototype.getOpacity = function() {
  return this.get('opacity');
};
Marker.prototype.getPosition = function() {
  var position = this.get('position');
  if (!(position instanceof LatLng)) {
    return new LatLng(position.lat, position.lng);
  }
  return position;
};

Marker.prototype.setOptions = function(options) {
  if(this._isRemoved) return;
  
  var opts = common.markerOptionsFilter(Object.assign({
    '__pgmId': this.getId()
  }, options));

  this.map.updateObject("markers", opts);
}

Marker.prototype.getOptions = function() {
  var self = this;
  return {
    '__pgmId': self.getId(),
    'position': self.getPosition(),
    'disableAutoPan': self.get('disableAutoPan'),
    'opacity': self.get('opacity'),
    'icon': self.get('icon'),
    'zIndex': self.get('zIndex'),
    'anchor': self.get('anchor'),
    'infoWindowAnchor': self.get('infoWindowAnchor'),
    'draggable': self.get('draggable'),
    'title': self.getTitle(),
    'snippet': self.getSnippet(),
    'visible': self.get('visible'),
    'rotation': self.getRotation()
  };
};

Marker.prototype.remove = function() {
  var self = this;
  if (self._isRemoved) {
    return;
  }
  self.trigger(event.INFO_CLOSE); // close open infowindow, otherwise it will stay
  this.map.removeObject("markers", this.getId());
};

module.exports = Marker;
