package com.arbotics.databot.sciencejournal;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.ScrollView;

import org.json.JSONObject;
import org.msgpack.MessagePack;
import org.msgpack.type.Value;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_MATCH_LOST;

public class DatabotService extends Service {
    final private String TAG = this.getClass().getSimpleName();
    private IBinder mBinder = new DatabotBinder();

    private BluetoothManager mBluetoothManager;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;

    public boolean mButtonClicked = true;

    public HashMap<String, DatabotDevice> mDevices = new HashMap<>();
    public BluetoothDevice selectedDevice = null;

    MessagePack msgpack = new MessagePack();

    //this will hold names of sensors and their current vals
    private HashMap<String, List<Double>> mData = new HashMap<>();
    private HashMap<String, Integer> mDataIndex = new HashMap<String, Integer>();
    private List<Long> mTime = new LinkedList<>();

    //this will hold multiple packets at a time because we don't receive complete packets to parse
    private ByteArrayOutputStream multipacketbuf = new ByteArrayOutputStream();

    private Handler myHandler = new android.os.Handler();

    private Thread thread = null;
    private boolean isRunning = false;
    private boolean shouldDisconnect = false;
    private boolean shouldScan = true;

    private int mConnectionState = STATE_DISCONNECTED;
    private List<BluetoothGattService> mServices;

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    public final String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";


    public DatabotService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        if (thread == null) {
            thread = new Thread(new DatabotThread(), "DatabotThread");
            thread.start();
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy() , service stopped...");
        isRunning = false;
        disconnect();
        mConnectionState = STATE_DISCONNECTED;
        if (mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(scb);
        }
        bleClose();
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        Log.i(TAG, "db service has been unbound");
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        return Service.START_STICKY;
    }

    public void setSelectedDevice(String nameAndUuid) {
        if(!(this.selectedDevice == mDevices.get(nameAndUuid).mDevice)){
            disconnect();
        }
        this.selectedDevice = mDevices.get(nameAndUuid).mDevice;
    }

    public List<DatabotDevice> getDevices(){
        List<DatabotDevice> devices= new ArrayList<>();
        for(String key: mDevices.keySet()){
            devices.add(mDevices.get(key));
        }
        return devices;
    }

    public dataAndTime getDataAndTime(String key) {
        Integer index = mDataIndex.get(key);
        Double data = null;
        Long time = null;
        try {
            data = mData.get(key).get(index);
            time = mTime.get(index);
        } catch (Exception e) {
            //Log.e(TAG, "getDataAndTime: ",e );
            return null;
        }
        if(index < 999){
            index++;
        }
        mDataIndex.put(key, index);
        return new dataAndTime(data, time);
    }

    public int getConnectionState() {
        return mConnectionState;
    }

    public Set<String> getSensors() {
        return mData.keySet();
    }

    public class DatabotBinder extends Binder {
        DatabotService getService() {
            return DatabotService.this;
        }
    }

    public void sendBleMessage(String tag, String message) {
        //Log.d("sender", "Broadcasting message");
        Intent intent = new Intent("ble-packet-data");
        // You can also include some extra data.
        try {
            intent.putExtra(tag, message);
        }catch (Exception e){
            Log.e(TAG, "sendBleMessage: ", e);
            return;
        }
        LocalBroadcastManager.getInstance(DatabotService.this).sendBroadcast(intent);
    }

    public void sendNewDeviceMessage(String[] messages) {
        Intent intent = new Intent("ble-device-data");
        intent.putExtra("device", messages);
        LocalBroadcastManager.getInstance(DatabotService.this).sendBroadcast(intent);
    }

    public void sendUpdateDeviceMessage(String[] messages) {
        Intent intent = new Intent("ble-device-data");
        intent.putExtra("device", messages);
        LocalBroadcastManager.getInstance(DatabotService.this).sendBroadcast(intent);
    }

    public void sendUnSetDeviceMessage() {
        Intent intent = new Intent("ble-device-data");
        intent.putExtra("unset", "unset");
        LocalBroadcastManager.getInstance(DatabotService.this).sendBroadcast(intent);
    }

    public void sendScanDeviceMessage(String message) {
        Intent intent = new Intent("ble-device-data");
        intent.putExtra("scan", message);
        LocalBroadcastManager.getInstance(DatabotService.this).sendBroadcast(intent);
    }

    public void sendDeviceClearMessage(){
        Intent intent = new Intent("ble-device-data");
        intent.putExtra("clear", "true");
        LocalBroadcastManager.getInstance(DatabotService.this).sendBroadcast(intent);
    }

    private void getBluetoothDialogue(){
        if(mButtonClicked == true) {
            Intent btIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            btIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(btIntent);
            mButtonClicked = false;
        }
    }

    public boolean enableBluetooth() {
        return mBluetoothAdapter.enable();
    }

    public boolean toggleBluetooth() {
        if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE){
            stopScan();
        }
        if(mConnectionState != STATE_DISCONNECTED){
            disconnect();
        }
        if (!mBluetoothAdapter.isEnabled()){
            getBluetoothDialogue();
        } else {
            mBluetoothAdapter.disable();
        }

        setScan();

        // No need to change bluetooth state
        return true;
    }



    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                //sendBleMessage("state"("disconnected from gatt server");
                Log.i(TAG, "Disconnected from GATT server.");
                mServices = null;
                disconnect();
                bleClose();
            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                mConnectionState = STATE_CONNECTING;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mServices = getSupportedGattServices();
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            final byte[] data = characteristic.getValue();
            try {
                multipacketbuf.write(data);
            } catch (Exception e) {
                Log.e(TAG, "onCharacteristicChanged: ", e);
            }
            String bleDataString = new String(data);
            sendBleMessage("packet",bleDataString);

            byte parsablebyte[] = multipacketbuf.toByteArray();
            byte delimiter[] = "msgstart".getBytes();
            List<byte[]> packets = split(parsablebyte, delimiter);
            /*List<byte[]> lasttwopackets = null;
            lasttwopackets.add(packets.get(packets.size()-2));
            lasttwopackets.add(packets.get(packets.size()-1));*/

            for (byte[] packet : packets) {
                Value dynamic = null;
                try {
                    dynamic = msgpack.read(packet);
                } catch (Exception e) {
                    //Log.i(TAG, "onCharacteristicChanged: msgpackread exception"+e.toString());
                    continue;
                }
                String msgunpacked = dynamic.toString();
                JSONObject json = null;
                try {
                    json = new JSONObject(msgunpacked);
                } catch (Exception e) {
                    Log.e(TAG, "onCharacteristicChanged: json exception" + e);
                }
                if (json != null) {
                    Iterator<String> it = json.keys();
                    while (it.hasNext()) {
                        String key = it.next();
                        try {
                            if (key.equals("time")) {
                                mTime.add(Long.parseLong(json.getString(key)));
                                if(mTime.size() > 1000){
                                    mTime.remove(0);
                                }
                                continue;
                            }

                            if (mData.get(key) == null) {
                                mData.put(key, new LinkedList<Double>());
                                mDataIndex.put(key, 0);
                            }

                            List<Double> list = mData.get(key);
                            list.add(Double.parseDouble(json.getString(key)));
                            if(list.size() > 1000) {
                                list.remove(0);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "exception caught hash json parse", e);
                        }

                    }
                    multipacketbuf.reset();
                }

            }

        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.i(TAG, "onMtuChanged: new mtu size: " + mtu + " status:" + status);
        }

    };
    ScanCallback scb = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i(TAG, "onScanResult");
            //sendBleMessage("state"("scan results found");
            switch (callbackType) {
                case CALLBACK_TYPE_ALL_MATCHES: {
                    //Log.i(TAG, "CALLBACK_TYPE_ALL_MATCHES");
                    //Log.i(TAG,  result.toString());
                    ScanRecord rec = result.getScanRecord();
                    String name = rec.getDeviceName();
                    BluetoothDevice device = result.getDevice();
                    String address = device.getAddress();
                    String rssi = Integer.toString(result.getRssi());
                    if (name != null && name.startsWith("HMSoft")) {
                        Log.i(TAG, "Got A Databot");
                        //.put() returns null when new key
                        String[] message = {name, address, rssi};
                        if(mDevices.put(name+address, new DatabotDevice(name, address, rssi, device)) == null){
                            sendNewDeviceMessage(message);
                        }else {
                            sendUpdateDeviceMessage(message);
                        }

                    } else {
                        //Log.i(TAG, String.format("device name: %s", name));
                    }

                    break;
                }
                case CALLBACK_TYPE_FIRST_MATCH: {
                    Log.i(TAG, "CALLBACK_TYPE_FIRST_MATCH");
                    break;
                }
                case CALLBACK_TYPE_MATCH_LOST: {
                    Log.i(TAG, "CALLBACK_TYPE_MATCH_LOST");
                    break;
                }
                default: {
                    Log.i(TAG, String.format("unknown CALLBACK_TYPE %d", callbackType));
                    break;
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            //sendBleMessage("state"("scan failed");
            switch (errorCode) {
                case SCAN_FAILED_ALREADY_STARTED: {
                    Log.i(TAG, "SCAN_FAILED_ALREADY_STARTED");
                    break;
                }
                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED: {
                    Log.i(TAG, "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED");
                    break;
                }
                case SCAN_FAILED_FEATURE_UNSUPPORTED: {
                    Log.i(TAG, "SCAN_FAILED_FEATURE_UNSUPPORTED");
                    break;
                }
                case SCAN_FAILED_INTERNAL_ERROR: {
                    Log.i(TAG, "SCAN_FAILED_INTERNAL_ERROR");
                    break;
                }
                default: {
                    Log.i(TAG, String.format("unknown SCAN_FAILED code %d", errorCode));
                    break;
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            //sendBleMessage("state"("multiple scan results");
            //super.onBatchScanResults(results);
            Log.i(TAG, "got scan results");
            for (ScanResult result : results) {
                Log.i(TAG, result.toString());
            }
        }
    };

    /**
     * Initializes a reference to the local Bluetooth adapter.adb
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        mBluetoothManager = null;
        mBluetoothAdapter = null;
        mBluetoothLeScanner = null;

        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        //we dont want to reconnect
        // Previously connected device.  Try to reconnect.
            /*if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                    && mBluetoothGatt != null) {
                Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
                if (mBluetoothGatt.connect()) {
                    mConnectionState = STATE_CONNECTING;
                    return true;
                } else {
                    return false;
                }
            }*/

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        if(mBluetoothGatt==null) {
            mConnectionState = STATE_CONNECTING;
            mBluetoothGatt = device.connectGatt(DatabotService.this, false, mGattCallback);
        }
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void setDisconnect(){
        shouldDisconnect = true;
    }

    public void setScan(){
        shouldScan = true;
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void bleClose() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public Runnable scanTimeOut = new Runnable() {
        public void run() {
            shouldScan = false;
            stopScan();
        }
    };

    public void startScan() {
        try {
            mBluetoothLeScanner.startScan(scb);
        }catch (Exception e){
            Log.e(TAG, "startScan: ", e);
            return;
        }
        myHandler.removeCallbacks(scanTimeOut);
        myHandler.postDelayed(scanTimeOut, 45000);
        sendScanDeviceMessage("start");
    }

    public void stopScan() {
        myHandler.removeCallbacks(scanTimeOut);
        if(mBluetoothLeScanner!=null) {
            mBluetoothLeScanner.stopScan(scb);
        }
        sendScanDeviceMessage("stop");
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    class DatabotThread implements Runnable {
        final private String TAG = this.getClass().getSimpleName();
        private Integer timeoutCount = 0;

        public void run() {

            sendBleMessage("state","thread running");
            ble_fsm fsm = new ble_fsm();
            while (isRunning) {
                fsm.run();
                sleep(100);
            }
            return;

        }

        void sleep(int millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Log.i(TAG, "bad sleep");
            }
        }

        private class ble_fsm {

            private Long connectionTime = 0L;
            private Long currTime = 0L;

            private bleState currState = bleState.INITBLE;

            public void run() {

                switch (currState) {
                    case INITBLE:
                        mDevices.clear();
                        sendBleMessage("state", "Initializing Bluetooth");
                        sendDeviceClearMessage();
                        // Use this check to determine whether BLE is supported on the device.  Then you can
                        // selectively disable BLE-related features.
                        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                            currState = bleState.NOBLE;
                            break;
                        }
                        if (mBluetoothLeScanner != null) {
                            stopScan();
                        }
                        currState = bleState.INITADAPTER;
                        break;

                    case INITADAPTER:
                        sendBleMessage("state", "Initializing Adapter");
                        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
                        // BluetoothAdapter through BluetoothManager.
                        initialize();
                        if (mBluetoothAdapter == null) {
                            currState = bleState.NOADAPTER;
                            break;
                        }
                        if (!mBluetoothAdapter.isEnabled()) {
                            sendBleMessage("state", "please enable bluetooth");
                            currState = bleState.NOBLE;
                            break;
                        }
                        currState = bleState.INITSCANNER;
                        break;

                    case INITSCANNER:
                        sendBleMessage("state", "Initializing Scanner");
                        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
                        if (mBluetoothLeScanner == null) {
                            currState = bleState.NOSCANNER;
                            break;
                        }
                        currState = bleState.SCAN;
                        break;

                    case SCAN:
                        sendBleMessage("state", "Scan");
                        if (shouldScan) {
                            startScan();
                            currState = bleState.SCANNING;
                        } else {
                            currState = bleState.IDLE;
                            break;
                        }
                        break;

                    case SCANNING:
                        sendBleMessage("state", "Scanning");
                        if (!shouldScan) {
                            currState = bleState.IDLE;
                            break;
                        }
                        if (!mBluetoothAdapter.isEnabled()) {
                            sendBleMessage("state", "please enable bluetooth");
                            currState = bleState.NOBLE;
                            break;
                        }
                        if (selectedDevice == null) {
                            break;
                        }
                        currState = bleState.SELECTED;
                        break;

                    case SELECTED:
                        if (selectedDevice == null) {
                            disconnect();
                            currState = bleState.DISCONNECTED;
                            break;
                        }
                        connectionTime = System.currentTimeMillis();
                        currState = bleState.CONNECT;
                        break;

                    case CONNECT:
                        sendBleMessage("state", "Connect");
                        String address = "";
                        if (selectedDevice != null) {
                            address = selectedDevice.getAddress();
                        } else {
                            currState = bleState.DISCONNECTED;
                            break;
                        }
                        Log.i(TAG, String.format("connecting to address %s", address));
                        if (!connect(address)) {
                            Log.i(TAG, "couldn't connect");
                            break;
                        }
                        try {
                            stopScan();
                        } catch (Exception e) {
                            Log.e(TAG, "run: ", e);
                            currState = bleState.ERROR;
                        }
                        currState = bleState.CONNECTING;
                        break;

                    case CONNECTING:
                        currTime = System.currentTimeMillis();
                        sendBleMessage("state", "Connecting");
                        /*if(shouldDisconnect){
                            shouldDisconnect = false;
                            disconnect();
                            currState = CONN
                        }*/
                        if (!mBluetoothAdapter.isEnabled()) {
                            sendBleMessage("state", "please enable bluetooth");
                            currState = bleState.NOBLE;
                            break;
                        }
                        //if (mConnectionState == STATE_CONNECTING) {
                        if (currTime - connectionTime > 5000) {
                            disconnect();
                            currState = bleState.CONNECTIONTIMEOUT;
                            break;
                        }
                        //}
                        if (mConnectionState == STATE_DISCONNECTED) {
                            currState = bleState.DISCONNECTED;
                            break;
                        }
                        if (mConnectionState == STATE_CONNECTED) {
                            currState = bleState.CONNECTED;
                            break;
                        }
                        break;

                    case CONNECTED:
                        sendBleMessage("state","Connected");
                        try {
                            stopScan();
                            mBluetoothGatt.requestMtu(120);
                            mBluetoothGatt.discoverServices();
                        }catch(Exception e){
                            Log.e(TAG, "run: CONNECTED",e );
                            currState = bleState.DISCONNECTED;
                            break;
                        }
                        currState = bleState.DISCOVERING;
                        break;

                    case DISCOVERING:
                        sendBleMessage("state","Discovering services");
                        if (!mBluetoothAdapter.isEnabled()) {
                            sendBleMessage("state","please enable bluetooth");
                            currState = bleState.NOBLE;
                            break;
                        }
                        if (mConnectionState == STATE_DISCONNECTED || mBluetoothGatt == null) {
                            currState = bleState.DISCONNECTED;
                            break;
                        }
                        if (mServices == null) {
                            break;
                        }
                        currState = bleState.ENABLENOTIFCATION;
                        break;

                    case ENABLENOTIFCATION:
                        sendBleMessage("state","Enabling notifications");
                        for (BluetoothGattService service : mServices) {
                            Log.i(TAG, String.format("service uuid: %s", service.getUuid().toString()));
                            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                                String charUuid = characteristic.getUuid().toString();
                                Log.i(TAG, String.format("characteristic uuid: %s", charUuid));
                                final String DATABOT_CUSTOM_SERVICE_UUID_PREFIX = "0000ffe1";
                                if (charUuid.startsWith(DATABOT_CUSTOM_SERVICE_UUID_PREFIX)) {
                                    Log.i(TAG, "Identified Databot Custom Service");
                                    int charaProp = characteristic.getProperties();
                                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                        Log.i(TAG, "enabling Notifications");


                                        setCharacteristicNotification(characteristic, true);
                                        sleep(1000);
                                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                        try{
                                            mBluetoothGatt.writeDescriptor(descriptor);
                                        }catch (Exception e){
                                            Log.e(TAG, "run: ", e );
                                            currState = bleState.DISCONNECTED;
                                            break;
                                        }


                                        currState = bleState.LISTENING;
                                        break;
                                    }
                                }
                            }
                        }
                        break;

                    case LISTENING:
                        sendBleMessage("state","listening for new packets");
                        if (!mBluetoothAdapter.isEnabled()) {
                            sendBleMessage("state","please enable bluetooth");
                            currState = bleState.NOBLE;
                            break;
                        }
                        if (mBluetoothGatt == null){
                            currState = bleState.DISCONNECTED;
                        }
                        if (mConnectionState == STATE_DISCONNECTED) {
                            currState = bleState.DISCONNECTED;
                            break;
                        }
                        break;

                    case DISCONNECTED:
                        sendBleMessage("state","Disconnected from GATT server");
                        Log.i(TAG, "Disconnected");
                        mData.clear();
                        mDataIndex.clear();
                        disconnect();
                        currState = bleState.INITADAPTER;
                        break;

                    case ERROR:
                        sendBleMessage("state","encountered an exception");
                        currState = bleState.INITBLE;
                        break;

                    case NOBLE:
                        sendDeviceClearMessage();
                        sendBleMessage("state","bluetooth disabled");
                        if(mBluetoothAdapter.isEnabled()){
                            currState = bleState.INITBLE;
                            break;
                        }
                        Log.i(TAG, "no BLE");
                        mData.clear();
                        mDataIndex.clear();
                        getBluetoothDialogue();
                        //enableBluetooth();
                        break;

                    case NOADAPTER:
                        sendBleMessage("state","no adapter available");
                        Log.i(TAG, "no Adapter");
                        break;

                    case NOSCANNER:
                        sendBleMessage("state","no scanner available");
                        Log.i(TAG, "no Scanner");
                        currState = bleState.DISCONNECTED;
                        break;

                    case CONNECTIONTIMEOUT:
                        sendBleMessage("state", "connection timed out");
                        Log.i(TAG, "connection timeout");
                        //selectedDevice = null;
                        timeoutCount++;
                        if(timeoutCount == 3){
                            selectedDevice = null;
                            disconnect();
                            timeoutCount = 0;
                            sendUnSetDeviceMessage();
                            sendDeviceClearMessage();
                            shouldScan = true;
                        }
                        currState = bleState.SELECTED;
                        break;

                    case IDLE:
                        sendBleMessage("state", "idle, click scan to find new databots");
                        if(shouldScan){
                            startScan();
                            currState = bleState.SCANNING;
                        }
                        if (!mBluetoothAdapter.isEnabled()) {
                            sendBleMessage("state","please enable bluetooth");
                            currState = bleState.NOBLE;
                            break;
                        }
                        if (selectedDevice == null) {
                            break;
                        }
                        currState = bleState.SELECTED;
                        break;
                }

            }

        }

    }

    private enum bleState{
        INITBLE,
        INITADAPTER,
        INITSCANNER,
        SCAN,
        SCANNING,
        SELECTED,
        CONNECT,
        CONNECTING,
        CONNECTED,
        DISCOVERING,
        ENABLENOTIFCATION,
        LISTENING,
        DISCONNECTED,
        ERROR,
        NOBLE,
        NOADAPTER,
        NOSCANNER,
        CONNECTIONTIMEOUT,
        IDLE,
    }

    private List<byte[]> split(byte[] array, byte[] delimiter) {
        List<byte[]> byteArrays = new LinkedList<byte[]>();
        if (delimiter.length == 0) {
            return byteArrays;
        }
        int begin = 0;

        outer:
        for (int i = 0; i < array.length - delimiter.length + 1; i++) {
            for (int j = 0; j < delimiter.length; j++) {
                if (array[i + j] != delimiter[j]) {
                    continue outer;
                }
            }

            // If delimiter is at the beginning then there will not be any data.
            if (begin != i) {
                byteArrays.add(Arrays.copyOfRange(array, begin, i));
            }
            begin = i + delimiter.length;
        }

        // delimiter at the very end with no data following?
        if (begin != array.length) {
            byteArrays.add(Arrays.copyOfRange(array, begin, array.length));
        }

        return byteArrays;
    }

}