// (c) 2014-2016 Don Coleman
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.megster.cordova.ble.central;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Build;

import android.provider.Settings;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.*;



public class BLECentralPlugin extends CordovaPlugin {
    // actions
    private static final String SCAN = "scan";
    private static final String START_SCAN = "startScan";
    private static final String STOP_SCAN = "stopScan";
    private static final String START_SCAN_WITH_OPTIONS = "startScanWithOptions";

    private static final String CONNECT = "connect";
    private static final String DISCONNECT = "disconnect";
    private static final String QUEUE_CLEANUP = "queueCleanup";
    private static final String REQUEST_MTU = "requestMtu";
    private static final String REQUEST_CONNECTION_PRIORITY = "requestConnectionPriority";
    private final String CONNECTION_PRIORITY_HIGH = "high";
    private final String CONNECTION_PRIORITY_LOW = "low";
    private final String CONNECTION_PRIORITY_BALANCED = "balanced";

    private static final String READ = "read";
    private static final String WRITE = "write";
    private static final String READ_RSSI = "readRSSI";

    private static final String START_NOTIFICATION = "startNotification"; // register for characteristic notification
    private static final String STOP_NOTIFICATION = "stopNotification"; // remove characteristic notification

    private static final String IS_ENABLED = "isEnabled";
    private static final String IS_LOCATION_ENABLED = "isLocationEnabled";
    private static final String IS_CONNECTED = "isConnected";

    private static final String SETTINGS = "showBluetoothSettings";
    private static final String ENABLE = "enable";

    private static final String START_STATE_NOTIFICATIONS = "startStateNotifications";
    private static final String STOP_STATE_NOTIFICATIONS = "stopStateNotifications";


    // PERIPHERAL_PART
    private static final String INITIALIZE_PERIPHERAL = "initializePeripheral";
    private static final String ADD_SERVICE = "addService";
    private static final String REMOVE_ALL_SERVICE = "removeAllServices";
    private static final String NOTIFY = "notify";

    //NEW COMMAND
    private static final String START_STATE_PERMISSIONS = "startStatePermissions";
    private static final String REQUEST_PERMISSIONS = "requestPermissions";
    private static final String REGISTER_PERIPHERAL_STATUS = "registerPeripheralStatus";


    private BluetoothGattServer gattServer;
    private CallbackContext initPeripheralCallback;
    private CallbackContext registerPeripheralStatusCallback;
    private final UUID clientConfigurationDescriptorUuid = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
    // END OF PERIPHERAL_PART

    // callbacks
    CallbackContext discoverCallback;
    private CallbackContext enableBluetoothCallback;

    private static final String TAG = "Solaari_BLE_PLUGIN";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;

    BluetoothAdapter bluetoothAdapter;
    BluetoothLeScanner bluetoothLeScanner;

    // key is the MAC Address
    Map<String, Peripheral> peripherals = new LinkedHashMap<String, Peripheral>();

    // scan options
    boolean reportDuplicates = false;
    private CallbackContext permissionCallback;

    // Bluetooth state notification
    CallbackContext stateCallback;
    BroadcastReceiver stateReceiver;
    Map<Integer, String> bluetoothStates = new Hashtable<Integer, String>() {{
        put(BluetoothAdapter.STATE_OFF, "off");
        put(BluetoothAdapter.STATE_TURNING_OFF, "turningOff");
        put(BluetoothAdapter.STATE_ON, "on");
        put(BluetoothAdapter.STATE_TURNING_ON, "turningOn");
    }};

    public void onDestroy() {
        removeStateListener();
    }

    public void onReset() {
        removeStateListener();
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {

        if (bluetoothAdapter == null) {
            Activity activity = cordova.getActivity();
            boolean hardwareSupportsBLE = activity.getApplicationContext()
                    .getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
            if (!hardwareSupportsBLE) {
                callbackContext.error("This hardware does not support Bluetooth Low Energy.");
                return false;
            }
            BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        boolean validAction = true;
        //CENTRAL
        switch (action) {
            case SCAN: {
                UUID[] serviceUUIDs = parseServiceUUIDList(args.getJSONArray(0));
                int scanSeconds = args.getInt(1);
                resetScanOptions();
                findLowEnergyDevices(callbackContext, serviceUUIDs, scanSeconds);
                break;
            }
            case START_SCAN: {
                UUID[] serviceUUIDs = parseServiceUUIDList(args.getJSONArray(0));
                resetScanOptions();
                findLowEnergyDevices(callbackContext, serviceUUIDs, -1);
                break;
            }
            case STOP_SCAN:
                bluetoothLeScanner.stopScan(leScanCallback);
                callbackContext.success();
                break;
            case CONNECT: {
                String macAddress = args.getString(0);
                connect(callbackContext, macAddress);
                break;
            }
            case DISCONNECT: {
                String macAddress = args.getString(0);
                disconnect(callbackContext, macAddress);
                break;
            }
            case QUEUE_CLEANUP: {
                String macAddress = args.getString(0);
                queueCleanup(callbackContext, macAddress);
                break;
            }
            case REQUEST_MTU: {
                String macAddress = args.getString(0);
                int mtuValue = args.getInt(1);
                requestMtu(callbackContext, macAddress, mtuValue);
                break;
            }
            case REQUEST_CONNECTION_PRIORITY: {
                String macAddress = args.getString(0);
                String priority = args.getString(1);
                requestConnectionPriority(callbackContext, macAddress, priority);
                break;
            }
            case READ: {
                String macAddress = args.getString(0);
                UUID serviceUUID = uuidFromString(args.getString(1));
                UUID characteristicUUID = uuidFromString(args.getString(2));
                read(callbackContext, macAddress, serviceUUID, characteristicUUID);
                break;
            }
            case READ_RSSI: {
                String macAddress = args.getString(0);
                readRSSI(callbackContext, macAddress);
                break;
            }
            case WRITE: {
                String macAddress = args.getString(0);
                UUID serviceUUID = uuidFromString(args.getString(1));
                UUID characteristicUUID = uuidFromString(args.getString(2));
                byte[] data = args.getArrayBuffer(3);
                int type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                write(callbackContext, macAddress, serviceUUID, characteristicUUID, data, type);
                break;
            }
            case START_NOTIFICATION: {
                String macAddress = args.getString(0);
                UUID serviceUUID = uuidFromString(args.getString(1));
                UUID characteristicUUID = uuidFromString(args.getString(2));
                registerNotifyCallback(callbackContext, macAddress, serviceUUID, characteristicUUID);
                break;
            }
            case STOP_NOTIFICATION: {
                String macAddress = args.getString(0);
                UUID serviceUUID = uuidFromString(args.getString(1));
                UUID characteristicUUID = uuidFromString(args.getString(2));
                removeNotifyCallback(callbackContext, macAddress, serviceUUID, characteristicUUID);
                break;
            }
            case IS_ENABLED:
                if (bluetoothAdapter.isEnabled()) {
                    callbackContext.success();
                } else {
                    callbackContext.error("Bluetooth is disabled.");
                }
                break;
            case IS_LOCATION_ENABLED:
                if (locationServicesEnabled()) {
                    callbackContext.success();
                } else {
                    callbackContext.error("Location services disabled.");
                }
                break;
            case IS_CONNECTED: {
                String macAddress = args.getString(0);

                if (peripherals.containsKey(macAddress) && peripherals.get(macAddress).isConnected()) {
                    callbackContext.success();
                } else {
                    callbackContext.error("Not connected.");
                }
                break;
            }
            case SETTINGS: {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                cordova.getActivity().startActivity(intent);
                callbackContext.success();
                break;
            }
            case ENABLE: {
                enableBluetoothCallback = callbackContext;
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                cordova.startActivityForResult(this, intent, REQUEST_ENABLE_BLUETOOTH);
                break;
            }
            case START_STATE_NOTIFICATIONS:
                if (this.stateCallback != null) {
                    callbackContext.error("State callback already registered.");
                } else {
                    this.stateCallback = callbackContext;
                    addStateListener();
                    sendBluetoothStateChange(bluetoothAdapter.getState());
                }
                break;
            case STOP_STATE_NOTIFICATIONS:
                if (this.stateCallback != null) {
                    // Clear callback in JavaScript without actually calling it
                    PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                    result.setKeepCallback(false);
                    this.stateCallback.sendPluginResult(result);
                    this.stateCallback = null;
                }
                removeStateListener();
                callbackContext.success();
                break;
            case START_SCAN_WITH_OPTIONS: {
                UUID[] serviceUUIDs = parseServiceUUIDList(args.getJSONArray(0));
                JSONObject options = args.getJSONObject(1);

                resetScanOptions();
                this.reportDuplicates = options.optBoolean("reportDuplicates", false);
                ScanSettings.Builder scanSettings = new ScanSettings.Builder();

                switch (options.optString("scanMode", "")) {
                    case "":
                        break;
                    case "lowPower":
                        scanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
                        break;
                    case "balanced":
                        scanSettings.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
                        break;
                    case "lowLatency":
                        scanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
                        break;
                    case "opportunistic":
                        scanSettings.setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC);
                        break;
                    default:
                        callbackContext.error("scanMode must be one of: lowPower | balanced | lowLatency");
                        validAction = false;
                        break;
                }

                switch (options.optString("callbackType", "")) {
                    case "":
                        break;
                    case "all":
                        scanSettings.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
                        break;
                    case "first":
                        scanSettings.setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH);
                        break;
                    case "lost":
                        scanSettings.setCallbackType(ScanSettings.CALLBACK_TYPE_MATCH_LOST);
                        break;
                    default:
                        callbackContext.error("callbackType must be one of: all | first | lost");
                        validAction = false;
                        break;
                }

                switch (options.optString("matchMode", "")) {
                    case "":
                        break;
                    case "aggressive":
                        scanSettings.setCallbackType(ScanSettings.MATCH_MODE_AGGRESSIVE);
                        break;
                    case "sticky":
                        scanSettings.setCallbackType(ScanSettings.MATCH_MODE_STICKY);
                        break;
                    default:
                        callbackContext.error("matchMode must be one of: aggressive | sticky");
                        validAction = false;
                        break;
                }

                switch (options.optString("numOfMatches", "")) {
                    case "":
                        break;
                    case "one":
                        scanSettings.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT);
                        break;
                    case "few":
                        scanSettings.setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT);
                        break;
                    case "max":
                        scanSettings.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT);
                        break;
                    default:
                        callbackContext.error("numOfMatches must be one of: one | few | max");
                        validAction = false;
                        break;
                }

                switch (options.optString("phy", "")) {
                    case "":
                        break;
                    case "1m":
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            scanSettings.setPhy(BluetoothDevice.PHY_LE_1M);
                        }
                        break;
                    case "coded":
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            scanSettings.setPhy(BluetoothDevice.PHY_LE_CODED);
                        }
                        break;
                    case "all":
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            scanSettings.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED);
                        }
                        break;
                    default:
                        callbackContext.error("phy must be one of: 1m | coded | all");
                        validAction = false;
                        break;
                }

                if (validAction) {
                    String LEGACY = "legacy";
                    if (!options.isNull(LEGACY))
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            scanSettings.setLegacy(options.getBoolean(LEGACY));
                        }

                    long reportDelay = options.optLong("reportDelay", -1);
                    if (reportDelay >= 0L)
                        scanSettings.setReportDelay(reportDelay);

                    findLowEnergyDevices(callbackContext, serviceUUIDs, -1, scanSettings.build());
                }
                break;
            }
            // PERIPHERAL
            case INITIALIZE_PERIPHERAL:
                initializePeripheralAction(callbackContext);
                break;
            case ADD_SERVICE: {
                UUID uuid = uuidFromString(args.getString(0));
                BluetoothGattService service = new BluetoothGattService(uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);
                JSONArray characteristicsIn = args.getJSONArray(1);
                for (int i = 0; i < characteristicsIn.length(); i++) {
                    JSONObject characteristicIn = null;
                    try {
                        characteristicIn = characteristicsIn.getJSONObject(i);
                    } catch (JSONException ex) {
                        continue;
                    }
                    UUID characteristicUuid = uuidFromString(characteristicIn.optString("uuid", null));
                    boolean includeClientConfiguration = false;
                    JSONObject propertiesIn = characteristicIn.optJSONObject("properties");
                    int properties = 0;
                    if (propertiesIn != null) {
                        if (propertiesIn.optString("broadcast", null) != null) {
                            properties |= BluetoothGattCharacteristic.PROPERTY_BROADCAST;
                        }
                        if (propertiesIn.optString("extendedProps", null) != null) {
                            properties |= BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS;
                        }
                        if (propertiesIn.optString("indicate", null) != null) {
                            properties |= BluetoothGattCharacteristic.PROPERTY_INDICATE;
                            includeClientConfiguration = true;
                        }
                        if (propertiesIn.optString("notify", null) != null) {
                            properties |= BluetoothGattCharacteristic.PROPERTY_NOTIFY;
                            includeClientConfiguration = true;
                        }
                        if (propertiesIn.optString("read", null) != null) {
                            properties |= BluetoothGattCharacteristic.PROPERTY_READ;
                        }
                        if (propertiesIn.optString("signedWrite", null) != null) {
                            properties |= BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE;
                        }
                        if (propertiesIn.optString("write", null) != null) {
                            properties |= BluetoothGattCharacteristic.PROPERTY_WRITE;
                        }
                        if (propertiesIn.optString("writeNoResponse", null) != null) {
                            properties |= BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
                        }
                        if (propertiesIn.optString("notifyEncryptionRequired", null) != null) {
                            properties |= 0x100;
                        }
                        if (propertiesIn.optString("indicateEncryptionRequired", null) != null) {
                            properties |= 0x200;
                        }
                    }
                    JSONObject permissionsIn = characteristicIn.optJSONObject("permissions");
                    int permissions = 0;
                    if (permissionsIn != null) {
                        if (permissionsIn.optString("read", null) != null) {
                            permissions |= BluetoothGattCharacteristic.PERMISSION_READ;
                        }
                        if (permissionsIn.optString("readEncrypted", null) != null) {
                            permissions |= BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED;
                        }
                        if (permissionsIn.optString("readEncryptedMITM", null) != null) {
                            permissions |= BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM;
                        }
                        if (permissionsIn.optString("write", null) != null) {
                            permissions |= BluetoothGattCharacteristic.PERMISSION_WRITE;
                        }
                        if (permissionsIn.optString("writeEncrypted", null) != null) {
                            permissions |= BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED;
                        }
                        if (permissionsIn.optString("writeEncryptedMITM", null) != null) {
                            permissions |= BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM;
                        }
                        if (permissionsIn.optString("writeSigned", null) != null) {
                            permissions |= BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED;
                        }
                        if (permissionsIn.optString("writeSignedMITM", null) != null) {
                            permissions |= BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM;
                        }
                    }
                    BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(characteristicUuid, properties, permissions);

                    if (includeClientConfiguration) {
                        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(clientConfigurationDescriptorUuid, BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
                        characteristic.addDescriptor(descriptor);
                    }
                    JSONArray descriptorsIn = characteristicIn.optJSONArray("descriptors");
                    if (descriptorsIn != null) {
                        for (int j = 0; j < descriptorsIn.length(); j++) {
                            JSONObject descriptorIn = null;
                            try {
                                descriptorIn = descriptorsIn.getJSONObject(j);
                            } catch (JSONException ex) {
                                continue;
                            }
                            UUID descriptorUuid = uuidFromString(descriptorIn.optString("uuid", null));
                            permissionsIn = descriptorIn.optJSONObject("permissions");
                            permissions = 0;
                            if (permissionsIn != null) {
                                if (permissionsIn.optString("read", null) != null) {
                                    permissions |= BluetoothGattDescriptor.PERMISSION_READ;
                                }
                                if (permissionsIn.optString("readEncrypted", null) != null) {
                                    permissions |= BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED;
                                }
                                if (permissionsIn.optString("readEncryptedMITM", null) != null) {
                                    permissions |= BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM;
                                }
                                if (permissionsIn.optString("write", null) != null) {
                                    permissions |= BluetoothGattDescriptor.PERMISSION_WRITE;
                                }
                                if (permissionsIn.optString("writeEncrypted", null) != null) {
                                    permissions |= BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED;
                                }
                                if (permissionsIn.optString("writeEncryptedMITM", null) != null) {
                                    permissions |= BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM;
                                }
                                if (permissionsIn.optString("writeSigned", null) != null) {
                                    permissions |= BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED;
                                }
                                if (permissionsIn.optString("writeSignedMITM", null) != null) {
                                    permissions |= BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED_MITM;
                                }
                            }
                            BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(descriptorUuid, permissions);
                            characteristic.addDescriptor(descriptor);
                        }
                    }
                    service.addCharacteristic(characteristic);
                }
                addServiceAction(service, callbackContext);
                break;
            }
            case REMOVE_ALL_SERVICE:
                removeAllServicesAction(callbackContext);
                break;
            case NOTIFY: {
                UUID serviceUuid = uuidFromString(args.getString(0));
                BluetoothGattService service = gattServer.getService(serviceUuid);
                if (serviceUuid == null) {
                    validAction = false;
                }
                UUID characteristicUuid = uuidFromString(args.getString(1));
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
                if (characteristicUuid == null) {
                    validAction = false;
                }
                byte[] value = args.getArrayBuffer(2);
                if (value == null) {
                    validAction = false;
                }
                String address = args.getString(3);
                if (address == null) {
                    validAction = false;
                }
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                notifyAction(service, characteristic, value, device, callbackContext);
                break;
            }
            //NEW COMMAND
            case START_STATE_PERMISSIONS: {
                if (this.permissionCallback != null) {
                    callbackContext.error("State callback already registered.");
                } else {
                    this.permissionCallback = callbackContext;
                }
                break;
            }
            case REQUEST_PERMISSIONS: {
                requestBLEPermissions(0);
                break;
            }
            case REGISTER_PERIPHERAL_STATUS: {
                this.registerPeripheralStatusCallback = callbackContext;
                break;
            }
            default:
                validAction = false;
                break;
        }

        return validAction;
    }

    private UUID[] parseServiceUUIDList(JSONArray jsonArray) throws JSONException {
        List<UUID> serviceUUIDs = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            String uuidString = jsonArray.getString(i);
            serviceUUIDs.add(uuidFromString(uuidString));
        }

        return serviceUUIDs.toArray(new UUID[jsonArray.length()]);
    }

    private void onBluetoothStateChange(Intent intent) {
        final String action = intent.getAction();

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            sendBluetoothStateChange(state);
        }
    }

    private void sendBluetoothStateChange(int state) {
        if (this.stateCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, this.bluetoothStates.get(state));
            result.setKeepCallback(true);
            this.stateCallback.sendPluginResult(result);
        }
    }

    private void addStateListener() {
        if (this.stateReceiver == null) {
            this.stateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    onBluetoothStateChange(intent);
                }
            };
        }

        try {
            IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            webView.getContext().registerReceiver(this.stateReceiver, intentFilter);
        } catch (Exception ignored) {
        }
    }

    private void removeStateListener() {
        if (this.stateReceiver != null) {
            try {
                webView.getContext().unregisterReceiver(this.stateReceiver);
            } catch (Exception ignored) {
            }
        }
        this.stateCallback = null;
        this.stateReceiver = null;
    }

    private void connect(CallbackContext callbackContext, String macAddress) {

        if (!peripherals.containsKey(macAddress) && BLECentralPlugin.this.bluetoothAdapter.checkBluetoothAddress(macAddress)) {
            BluetoothDevice device = BLECentralPlugin.this.bluetoothAdapter.getRemoteDevice(macAddress);
            Peripheral peripheral = new Peripheral(device);
            peripherals.put(macAddress, peripheral);
        }

        Peripheral peripheral = peripherals.get(macAddress);
        if (peripheral != null) {
            peripheral.connect(callbackContext, cordova.getActivity(), false);
        } else {
            callbackContext.error("Peripheral " + macAddress + " not found.");
        }

    }

    private void disconnect(CallbackContext callbackContext, String macAddress) {

        Peripheral peripheral = peripherals.get(macAddress);
        if (peripheral != null) {
            peripheral.disconnect();
            callbackContext.success();
        } else {
            String message = "Peripheral " + macAddress + " not found.";
            callbackContext.error(message);
        }

    }

    private void queueCleanup(CallbackContext callbackContext, String macAddress) {
        Peripheral peripheral = peripherals.get(macAddress);
        if (peripheral != null) {
            peripheral.queueCleanup();
        }
        callbackContext.success();
    }

    private void requestMtu(CallbackContext callbackContext, String macAddress, int mtuValue) {
        Peripheral peripheral = peripherals.get(macAddress);
        if (peripheral != null) {
            peripheral.requestMtu(callbackContext, mtuValue);
        } else {
            String message = "Peripheral " + macAddress + " not found.";
            callbackContext.error(message);
        }
    }

    private void requestConnectionPriority(CallbackContext callbackContext, String macAddress, String priority) {
        Peripheral peripheral = peripherals.get(macAddress);

        if (peripheral == null) {
            callbackContext.error("Peripheral " + macAddress + " not found.");
            return;
        }

        if (!peripheral.isConnected()) {
            callbackContext.error("Peripheral " + macAddress + " is not connected.");
            return;
        }

        int androidPriority = BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
        if (priority.equals(CONNECTION_PRIORITY_LOW)) {
            androidPriority = BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER;
        } else if (priority.equals(CONNECTION_PRIORITY_BALANCED)) {
            androidPriority = BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
        } else if (priority.equals(CONNECTION_PRIORITY_HIGH)) {
            androidPriority = BluetoothGatt.CONNECTION_PRIORITY_HIGH;
        }
        peripheral.requestConnectionPriority(androidPriority);
        callbackContext.success();
    }

    private void read(CallbackContext callbackContext, String macAddress, UUID serviceUUID, UUID characteristicUUID) {

        Peripheral peripheral = peripherals.get(macAddress);

        if (peripheral == null) {
            callbackContext.error("Peripheral " + macAddress + " not found.");
            return;
        }

        if (!peripheral.isConnected()) {
            callbackContext.error("Peripheral " + macAddress + " is not connected.");
            return;
        }

        peripheral.queueRead(callbackContext, serviceUUID, characteristicUUID);

    }

    private void readRSSI(CallbackContext callbackContext, String macAddress) {

        Peripheral peripheral = peripherals.get(macAddress);

        if (peripheral == null) {
            callbackContext.error("Peripheral " + macAddress + " not found.");
            return;
        }

        if (!peripheral.isConnected()) {
            callbackContext.error("Peripheral " + macAddress + " is not connected.");
            return;
        }
        peripheral.queueReadRSSI(callbackContext);
    }

    private void write(CallbackContext callbackContext, String macAddress, UUID serviceUUID, UUID characteristicUUID,
                       byte[] data, int writeType) {

        Peripheral peripheral = peripherals.get(macAddress);

        if (peripheral == null) {
            callbackContext.error("Peripheral " + macAddress + " not found.");
            return;
        }

        if (!peripheral.isConnected()) {
            callbackContext.error("Peripheral " + macAddress + " is not connected.");
            return;
        }

        peripheral.queueWrite(callbackContext, serviceUUID, characteristicUUID, data, writeType);
    }

    private void registerNotifyCallback(CallbackContext callbackContext, String macAddress, UUID serviceUUID, UUID characteristicUUID) {

        Peripheral peripheral = peripherals.get(macAddress);
        if (peripheral != null) {

            if (!peripheral.isConnected()) {
                callbackContext.error("Peripheral " + macAddress + " is not connected.");
                return;
            }

            peripheral.queueRegisterNotifyCallback(callbackContext, serviceUUID, characteristicUUID);

        } else {

            callbackContext.error("Peripheral " + macAddress + " not found");

        }

    }

    private void removeNotifyCallback(CallbackContext callbackContext, String macAddress, UUID serviceUUID, UUID characteristicUUID) {

        Peripheral peripheral = peripherals.get(macAddress);
        if (peripheral != null) {

            if (!peripheral.isConnected()) {
                callbackContext.error("Peripheral " + macAddress + " is not connected.");
                return;
            }

            peripheral.queueRemoveNotifyCallback(callbackContext, serviceUUID, characteristicUUID);

        } else {

            callbackContext.error("Peripheral " + macAddress + " not found");

        }

    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            String address = device.getAddress();
            boolean alreadyReported = peripherals.containsKey(address) && !peripherals.get(address).isUnscanned();

            if (!alreadyReported) {

                Peripheral peripheral = new Peripheral(device, result.getRssi(), result.getScanRecord().getBytes());
                peripherals.put(device.getAddress(), peripheral);

                if (discoverCallback != null) {
                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, peripheral.asJSONObject());
                    pluginResult.setKeepCallback(true);
                    discoverCallback.sendPluginResult(pluginResult);
                }

            } else {
                Peripheral peripheral = peripherals.get(address);
                if (peripheral != null) {
                    peripheral.update(result.getRssi(), result.getScanRecord().getBytes());
                    if (reportDuplicates && discoverCallback != null) {
                        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, peripheral.asJSONObject());
                        pluginResult.setKeepCallback(true);
                        discoverCallback.sendPluginResult(pluginResult);
                    }
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };


    private void findLowEnergyDevices(CallbackContext callbackContext, UUID[] serviceUUIDs, int scanSeconds) {
        findLowEnergyDevices(callbackContext, serviceUUIDs, scanSeconds, new ScanSettings.Builder().build());
    }

    @SuppressLint("MissingPermission")
    private void findLowEnergyDevices(CallbackContext callbackContext, UUID[] serviceUUIDs, int scanSeconds, ScanSettings scanSettings) {

        if (!hasPermissions()) {
            callbackContext.error("PERMISSIONS_DENIED");
        }

        // return error if already scanning
        if (bluetoothAdapter.isDiscovering()) {
            callbackContext.error("Tried to start scan while already running.");
            return;
        }

        // clear non-connected cached peripherals
        for (Iterator<Map.Entry<String, Peripheral>> iterator = peripherals.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Peripheral> entry = iterator.next();
            Peripheral device = entry.getValue();
            boolean connecting = device.isConnecting();
            if (connecting) {
                LOG.d(TAG, "Not removing connecting device: " + device.getDevice().getAddress());
            }
            if (!entry.getValue().isConnected() && !connecting) {
                iterator.remove();
            }
        }

        discoverCallback = callbackContext;
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        List<ScanFilter> filters = new ArrayList<ScanFilter>();
        if (serviceUUIDs != null && serviceUUIDs.length > 0) {
            for (UUID uuid : serviceUUIDs) {
                ScanFilter filter = new ScanFilter.Builder().setServiceUuid(
                        new ParcelUuid(uuid)).build();
                filters.add(filter);
            }
        }
        bluetoothLeScanner.startScan(filters, scanSettings, leScanCallback);

        if (scanSeconds > 0) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, scanSeconds * 1000);
        }

        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private boolean locationServicesEnabled() {
        int locationMode = 0;
        try {
            locationMode = Settings.Secure.getInt(cordova.getActivity().getContentResolver(), Settings.Secure.LOCATION_MODE);
        } catch (Settings.SettingNotFoundException e) {
        }
        return (locationMode > 0);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {

            if (resultCode == Activity.RESULT_OK) {
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback.success();
                }
            } else {
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback.error("User did not enable Bluetooth");
                }
            }

            enableBluetoothCallback = null;
        }
    }

    /* @Override */
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        //Android 12 (API 31) and higher
        // Users MUST accept BLUETOOTH_SCAN and BLUETOOTH_CONNECT
        // Android 10 (API 29) up to Android 11 (API 30)
        // Users MUST accept ACCESS_FINE_LOCATION
        // Android 9 (API 28) and lower
        // Users MUST accept ACCESS_COARSE_LOCATION
        if (!this.hasPermissions()) {
            return;
        }

        switch (requestCode) {
            case 0:
            this.hasPermissions();
            break;
        }
    }

    private UUID uuidFromString(String uuid) {
        return UUIDHelper.uuidFromString(uuid);
    }

    /**
     * Reset the BLE scanning options
     */
    private void resetScanOptions() {
        this.reportDuplicates = false;
    }

    private void initializePeripheralAction(CallbackContext callbackContext) {

        initPeripheralCallback = callbackContext;

        if (!hasPermissions()) {
            initPeripheralCallback.error("PERMISSIONS_DENIED");
            return;
        }

        initGattServer();

        JSONObject returnObj = new JSONObject();
        addProperty(returnObj, "status", "enabled");
    
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
        pluginResult.setKeepCallback(true);
        registerPeripheralStatusCallback.sendPluginResult(pluginResult);
    }

    private void addServiceAction(BluetoothGattService service, CallbackContext callbackContext) {

        if (!hasPermissions()) {
            callbackContext.error("PERMISSIONS_DENIED");
        }

        @SuppressLint("MissingPermission") boolean result = gattServer.addService(service);
        if (result) {
          callbackContext.success();
        } else {
          callbackContext.error("Failed to add service");
        }
    }

    @SuppressLint("MissingPermission")
    private void removeAllServicesAction(CallbackContext callbackContext) {

        if (!hasPermissions()) {
            callbackContext.error("PERMISSIONS_DENIED");
        }

        gattServer.clearServices();
        callbackContext.success();
    }

    private void notifyAction(BluetoothGattService service, BluetoothGattCharacteristic characteristic, byte[] value, BluetoothDevice device, CallbackContext callbackContext) {

        if (!hasPermissions()) {
            callbackContext.error("PERMISSIONS_DENIED");
        }

        if (service == null) {
          callbackContext.error("Service not found");
        }

        if (characteristic == null) {
          callbackContext.error("Characteristic not found");
        }

        boolean setResult = characteristic.setValue(value);
        if (!setResult) {
          callbackContext.error("Failed to set value");
        }

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(clientConfigurationDescriptorUuid);
        byte[] descriptorValue = descriptor.getValue();

        boolean isIndicate = false;
        if (Arrays.equals(descriptorValue, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
          isIndicate = true;
        }

        //Wait for onNotificationSent event
        @SuppressLint("MissingPermission") boolean result = gattServer.notifyCharacteristicChanged(device, characteristic, isIndicate);
        if (result) {
            callbackContext.success();
        } else {
            callbackContext.error("Failed to notify characteristic");
        }
    }

    private BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
          if (registerPeripheralStatusCallback == null) {
            return;
          }

          JSONObject returnObj = new JSONObject();

          addDevice(returnObj, device);
          addCharacteristic(returnObj, characteristic);

          addProperty(returnObj, "status", "readRequested");
          addProperty(returnObj, "requestId", requestId);
          addProperty(returnObj, "offset", offset);

          PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
          pluginResult.setKeepCallback(true);
          registerPeripheralStatusCallback.sendPluginResult(pluginResult);
        }

        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
          if (registerPeripheralStatusCallback == null) {
            return;
          }

          JSONObject returnObj = new JSONObject();

          addDevice(returnObj, device);
          addCharacteristic(returnObj, characteristic);

          addProperty(returnObj, "status", "writeRequested");
          addProperty(returnObj, "requestId", requestId);
          addProperty(returnObj, "offset", offset);
          addProperty(returnObj, "value", value);

          addProperty(returnObj, "preparedWrite", preparedWrite);
          addProperty(returnObj, "responseNeeded", responseNeeded);

          PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
          pluginResult.setKeepCallback(true);
          registerPeripheralStatusCallback.sendPluginResult(pluginResult);
        }

        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
          if (registerPeripheralStatusCallback == null) {
            return;
          }

          JSONObject returnObj = new JSONObject();

          addDevice(returnObj, device);

          if (newState == BluetoothGatt.STATE_CONNECTED) {
            addProperty(returnObj, "status", "connected");
          } else {
            addProperty(returnObj, "status", "disconnected");
          }

          PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
          pluginResult.setKeepCallback(true);
          registerPeripheralStatusCallback.sendPluginResult(pluginResult);
        }

        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
          if (registerPeripheralStatusCallback == null) {
            return;
          }

          JSONObject returnObj = new JSONObject();

          addDevice(returnObj, device);
          addDescriptor(returnObj, descriptor);

          addProperty(returnObj, "status", "readRequested");
          addProperty(returnObj, "requestId", requestId);
          addProperty(returnObj, "offset", offset);

          PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
          pluginResult.setKeepCallback(true);
          registerPeripheralStatusCallback.sendPluginResult(pluginResult);
        }

        @SuppressLint("MissingPermission")
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
          if (registerPeripheralStatusCallback == null) {
            return;
          }

          if (descriptor.getUuid().equals(clientConfigurationDescriptorUuid)) {
            JSONObject returnObj = new JSONObject();

            addDevice(returnObj, device);
            addCharacteristic(returnObj, descriptor.getCharacteristic());

            if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
              addProperty(returnObj, "status", "unsubscribed");
            } else {
              addProperty(returnObj, "status", "subscribed");
            }

            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
            pluginResult.setKeepCallback(true);
            registerPeripheralStatusCallback.sendPluginResult(pluginResult);

            gattServer.sendResponse(device, requestId, 0, offset, value);

            return;
          }

          JSONObject returnObj = new JSONObject();

          addDevice(returnObj, device);
          addDescriptor(returnObj, descriptor);

          addProperty(returnObj, "status", "writeRequested");
          addProperty(returnObj, "requestId", requestId);
          addProperty(returnObj, "offset", offset);
          addProperty(returnObj, "value", value);

          addProperty(returnObj, "preparedWrite", preparedWrite);
          addProperty(returnObj, "responseNeeded", responseNeeded);

          PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
          pluginResult.setKeepCallback(true);
          registerPeripheralStatusCallback.sendPluginResult(pluginResult);
        }

        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
          //Log.d("BLE", "execute write");
        }

        public void onMtuChanged(BluetoothDevice device, int mtu) {
          if (registerPeripheralStatusCallback == null) {
            return;
          }

          JSONObject returnObj = new JSONObject();

          addDevice(returnObj, device);
          addProperty(returnObj, "status", "mtuChanged");
          addProperty(returnObj, "mtu", mtu);

          PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
          pluginResult.setKeepCallback(true);
          registerPeripheralStatusCallback.sendPluginResult(pluginResult);
        }

        public void onNotificationSent(BluetoothDevice device, int status) {
          if (registerPeripheralStatusCallback == null) {
            return;
          }

          JSONObject returnObj = new JSONObject();

          addDevice(returnObj, device);

          if (status == BluetoothGatt.GATT_SUCCESS) {
            addProperty(returnObj, "status", "notificationSent");
          } else {
            addProperty(returnObj, "error", "notificationSent");
            addProperty(returnObj, "message", "Unable to send notification");
          }

          PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
          pluginResult.setKeepCallback(true);
          registerPeripheralStatusCallback.sendPluginResult(pluginResult);
        }
      };

    private void addProperty(JSONObject obj, String key, Object value) {
        //Believe exception only occurs when adding duplicate keys, so just ignore it
        try {
            if (value == null) {
                obj.put(key, JSONObject.NULL);
            } else {
                obj.put(key, value);
            }
        } catch (JSONException e) {
        }
    }

    @SuppressLint("MissingPermission")
    private void addDevice(JSONObject returnObj, BluetoothDevice device) {
        addProperty(returnObj, "address", device.getAddress());
        addProperty(returnObj, "name", device.getName());
    }

    private void addService(JSONObject returnObj, BluetoothGattService service) {
        addProperty(returnObj, "service", service.getUuid());
    }

    private void addCharacteristic(JSONObject returnObj, BluetoothGattCharacteristic characteristic) {
        addService(returnObj, characteristic.getService());
        addProperty(returnObj, "characteristic", characteristic.getUuid());
    }

    private void addDescriptor(JSONObject returnObj, BluetoothGattDescriptor descriptor) {
        addCharacteristic(returnObj, descriptor.getCharacteristic());
        addProperty(returnObj, "descriptor", descriptor.getUuid());
    }

    @SuppressLint("MissingPermission")
    private void initGattServer() {
        if (gattServer == null) {
            try {
                Activity activity = cordova.getActivity();
                BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
                gattServer = bluetoothManager.openGattServer(activity.getApplicationContext(), bluetoothGattServerCallback);
                this.initPeripheralCallback.success();
            } catch (Exception e) {
                this.initPeripheralCallback.error(e.getMessage());
            }
        }
    }

    private static final String[] BLE_PERMISSIONS = new String[]{
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
    };

    private static final String[] ANDROID_12_BLE_PERMISSIONS = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
    };

    public void requestBLEPermissions(int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PermissionHelper.requestPermissions(this, requestCode, ANDROID_12_BLE_PERMISSIONS);
        else
            PermissionHelper.requestPermissions(this, requestCode, BLE_PERMISSIONS);
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            if (!PermissionHelper.hasPermission(this, Manifest.permission.BLUETOOTH_SCAN) ||
                !PermissionHelper.hasPermission(this, Manifest.permission.BLUETOOTH_CONNECT)) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, "PERMISSION_DENIED");
                result.setKeepCallback(true);
                this.permissionCallback.sendPluginResult(result);
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= 29) {
            if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, "PERMISSION_DENIED");
                result.setKeepCallback(true);
                this.permissionCallback.sendPluginResult(result);
                return false;
            }
        } else if (Build.VERSION.SDK_INT <= 28) {
            if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, "PERMISSION_DENIED");
                result.setKeepCallback(true);
                this.permissionCallback.sendPluginResult(result);
                return false;
            }
        }
            PluginResult result = new PluginResult(PluginResult.Status.OK, "PERMISSION_GRANTED");
            result.setKeepCallback(true);
            this.permissionCallback.sendPluginResult(result);
            return true;
    }

}
