var exec = require('cordova/exec');

/* eslint-disable */

var stringToArrayBuffer = function(str) {
    var ret = new Uint8Array(str.length);
    for (var i = 0; i < str.length; i++) {
        ret[i] = str.charCodeAt(i);
    }
    return ret.buffer;
  };
  
  var base64ToArrayBuffer = function(b64) {
    return stringToArrayBuffer(atob(b64));
  };
  
  function massageMessageNativeToJs(message) {
    if (message.CDVType == 'ArrayBuffer') {
        message = base64ToArrayBuffer(message.data);
    }
    return message;
  }
  
  // Cordova 3.6 doesn't unwrap ArrayBuffers in nested data structures
  // https://github.com/apache/cordova-js/blob/94291706945c42fd47fa632ed30f5eb811080e95/src/ios/exec.js#L107-L122
  function convertToNativeJS(object) {
    Object.keys(object).forEach(function (key) {
        var value = object[key];
        object[key] = massageMessageNativeToJs(value);
        if (typeof(value) === 'object') {
            convertToNativeJS(value);
        }
    });
  }
  
  var bluetoothleName = "SolaariBLE";
  var SolaariBLE = {
  
    // SLAVE
    scan: function (services, seconds, success, failure) {
      var successWrapper = function(peripheral) {
          convertToNativeJS(peripheral);
          success(peripheral);
      };
      exec(successWrapper, failure, bluetoothleName, 'scan', [services, seconds]);
    },
  
    startScan: function (services, success, failure) {
        var successWrapper = function(peripheral) {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        exec(successWrapper, failure, bluetoothleName, 'startScan', [services]);
    },
  
    stopScan: function (success, failure) {
        exec(success, failure, bluetoothleName, 'stopScan', []);
    },
  
    startScanWithOptions: function(services, options, success, failure) {
        var successWrapper = function(peripheral) {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        options = options || {};
        exec(successWrapper, failure, bluetoothleName, 'startScanWithOptions', [services, options]);
    },
  
    // iOS only
    connectedPeripheralsWithServices: function(services, success, failure) {
        exec(success, failure, bluetoothleName, 'connectedPeripheralsWithServices', [services]);
    },
  
    // iOS only
    peripheralsWithIdentifiers: function(identifiers, success, failure) {
        exec(success, failure, bluetoothleName, 'peripheralsWithIdentifiers', [identifiers]);
    },
  
    // Android only
    bondedDevices: function(success, failure) {
        exec(success, failure, bluetoothleName, 'bondedDevices', []);
    },
  
    // this will probably be removed
    list: function (success, failure) {
        exec(success, failure, bluetoothleName, 'list', []);
    },
  
    connect: function (device_id, success, failure) {
        // wrap success so nested array buffers in advertising info are handled correctly
        var successWrapper = function(peripheral) {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        exec(successWrapper, failure, bluetoothleName, 'connect', [device_id]);    
    },
  
    autoConnect: function (deviceId, connectCallback, disconnectCallback) {
        var disconnectCallbackWrapper;
        autoconnected[deviceId] = true;
  
        // wrap connectCallback so nested array buffers in advertising info are handled correctly
        var connectCallbackWrapper = function(peripheral) {
            convertToNativeJS(peripheral);
            connectCallback(peripheral);
        };
  
        // iOS needs to reconnect on disconnect, unless ble.disconnect was called. 
        if (cordova.platformId === 'ios') {
            disconnectCallbackWrapper = function(peripheral) {
                // let the app know the peripheral disconnected
                disconnectCallback(peripheral);
  
                // reconnect if we have a peripheral.id and the user didn't call disconnect
                if (peripheral.id && autoconnected[peripheral.id]) {
                    exec(connectCallbackWrapper, disconnectCallbackWrapper, bluetoothleName, 'autoConnect', [deviceId]);
                }
            };    
        } else {  // no wrapper for Android
            disconnectCallbackWrapper = disconnectCallback; 
        }
  
        exec(connectCallbackWrapper, disconnectCallbackWrapper, bluetoothleName, 'autoConnect', [deviceId]);
    },
  
    disconnect: function (device_id, success, failure) {
        try {
            delete autoconnected[device_id];
        } catch(e) {
            // ignore error
        }
        exec(success, failure, bluetoothleName, 'disconnect', [device_id]);
    },
  
    queueCleanup: function (device_id,  success, failure) {
        exec(success, failure, bluetoothleName, 'queueCleanup', [device_id]);
    },
  
    setPin: function (pin, success, failure) {
        exec(success, failure, bluetoothleName, 'setPin', [pin]);
    },
  
    requestMtu: function (device_id, mtu,  success, failure) {
        exec(success, failure, bluetoothleName, 'requestMtu', [device_id, mtu]);
    },
  
    requestConnectionPriority: function (device_id, connectionPriority, success, failure) {
        exec(success, failure, bluetoothleName, 'requestConnectionPriority', [device_id, connectionPriority])
    },
  
    refreshDeviceCache: function(deviceId, timeoutMillis, success, failure) {
        var successWrapper = function(peripheral) {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        exec(successWrapper, failure, bluetoothleName, 'refreshDeviceCache', [deviceId, timeoutMillis]);
    },
  
    // characteristic value comes back as ArrayBuffer in the success callback
    read: function (device_id, service_uuid, characteristic_uuid, success, failure) {
        exec(success, failure, bluetoothleName, 'read', [device_id, service_uuid, characteristic_uuid]);
    },
  
    // RSSI value comes back as an integer
    readRSSI: function(device_id, success, failure) {
        exec(success, failure, bluetoothleName, 'readRSSI', [device_id]);
    },
  
    // value must be an ArrayBuffer
    write: function (device_id, service_uuid, characteristic_uuid, value, success, failure) {
        exec(success, failure, bluetoothleName, 'write', [device_id, service_uuid, characteristic_uuid, value]);
    },
  
    // value must be an ArrayBuffer
    writeWithoutResponse: function (device_id, service_uuid, characteristic_uuid, value, success, failure) {
        exec(success, failure, bluetoothleName, 'writeWithoutResponse', [device_id, service_uuid, characteristic_uuid, value]);
    },
  
    // value must be an ArrayBuffer
    writeCommand: function (device_id, service_uuid, characteristic_uuid, value, success, failure) {
        console.log("WARNING: writeCommand is deprecated, use writeWithoutResponse");
        exec(success, failure, bluetoothleName, 'writeWithoutResponse', [device_id, service_uuid, characteristic_uuid, value]);
    },
  
    // success callback is called on notification
    notify: function (device_id, service_uuid, characteristic_uuid, success, failure) {
        console.log("WARNING: notify is deprecated, use startNotification");
        exec(success, failure, bluetoothleName, 'startNotification', [device_id, service_uuid, characteristic_uuid]);
    },
  
    // success callback is called on notification
    startNotification: function (device_id, service_uuid, characteristic_uuid, success, failure) {
        exec(success, failure, bluetoothleName, 'startNotification', [device_id, service_uuid, characteristic_uuid]);
    },
  
    // success callback is called when the descriptor 0x2902 is written
    stopNotification: function (device_id, service_uuid, characteristic_uuid, success, failure) {
        exec(success, failure, bluetoothleName, 'stopNotification', [device_id, service_uuid, characteristic_uuid]);
    },
  
    isConnected: function (device_id, success, failure) {
        exec(success, failure, bluetoothleName, 'isConnected', [device_id]);
    },
  
    isEnabled: function (success, failure) {
        exec(success, failure, bluetoothleName, 'isEnabled', []);
    },
  
    // Android only
    isLocationEnabled: function (success, failure) {
        exec(success, failure, bluetoothleName, 'isLocationEnabled', []);
    },
  
    enable: function (success, failure) {
        exec(success, failure, bluetoothleName, "enable", []);
    },
  
    showBluetoothSettings: function (success, failure) {
        exec(success, failure, bluetoothleName, "showBluetoothSettings", []);
    },
  
    startStateNotifications: function (success, failure) {
        exec(success, failure, bluetoothleName, "startStateNotifications", []);
    },
  
    stopStateNotifications: function (success, failure) {
        exec(success, failure, bluetoothleName, "stopStateNotifications", []);
    },
  
    // MASTER
    initializePeripheral: function(success, failure) {
      exec(success, failure, bluetoothleName, "initializePeripheral", []);
    },
    addService: function(serviceUUID, characteristics, success, failure) {
      exec(success, failure, bluetoothleName, "addService", [serviceUUID, characteristics]);
    },
    removeService: function(serviceUUID, success, failure) {
      exec(success, failure, bluetoothleName, "removeService", [serviceUUID]);
    },
    removeAllServices: function(success, failure) {
      exec(success, failure, bluetoothleName, "removeAllServices", []);
    },
    /* respond: function(successCallback, errorCallback, params) {
      exec(successCallback, errorCallback, bluetoothleName, "respond", [params]);
    }, */
    notify: function(serviceUUID, characteristicUUID, value, deviceAddress, success, failure) {
      exec(success, failure, bluetoothleName, "notify", [serviceUUID, characteristicUUID, value, deviceAddress]);
    },
  }
  module.exports = SolaariBLE;
