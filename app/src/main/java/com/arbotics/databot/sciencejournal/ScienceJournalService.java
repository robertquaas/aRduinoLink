package com.arbotics.databot.sciencejournal;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.ArrayMap;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedDevice;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.AdvertisedSensor;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.IDeviceConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConnector;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorObserver;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ScalarSensorService;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorAppearanceResources;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorBehavior;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.Versions;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScienceJournalService extends Service {
    public boolean isRunning = false;
    final private String TAG = this.getClass().getSimpleName();
    public static final String DEVICE_ID = "databot";
    public static final String SENSOR_PREF_NAME = "databot sensors";
    private static final boolean ONLY_ALLOW_SCIENCE_JOURNAL = true;
    DatabotService mDatabotService;
    boolean mServiceBound = false;
    boolean isDbServiceBound = false;
    Intent dbIntent = null;
    private long androidStartTime = 0;
    private long arduinoStartTime = 0;
    //start above threshold so it pops up first use
    private long mtimeSincePopUp = System.currentTimeMillis() - 30000;

    private Map<String, DatabotSensor> mSensors = new ArrayMap<>();

    private Integer numDbListeners = 0;

    @Override
    public void onCreate() {
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReciever,
                new IntentFilter("scienceJournalService"));
        establishDataBotConnection();
        isRunning = true;
        super.onCreate();
    }

    private BroadcastReceiver mMessageReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTimeSinceDialog();
        }
    };

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReciever);
        disconnectDataBotConnection();
        isRunning = false;
    }

    private ISensorDiscoverer.Stub mDiscoverer = null;

    @Nullable
    @Override
    public ISensorDiscoverer.Stub onBind(Intent intent) {
        return getDiscoverer();
    }

    public ISensorDiscoverer.Stub getDiscoverer() {
        if (mDiscoverer == null) {
            mDiscoverer = createDiscoverer();
        }
        return mDiscoverer;
    }

    private void getDatabotSensors(){
        mSensors.clear();
        Set<String> keys = mDatabotService.getSensors();

        Iterator<String> it = keys.iterator();
        while (it.hasNext()) {
            String name = it.next();
            if(name.equals("time") || name.equals("n")) {
                continue;
            }
            String units = "undefined units";
            String description = "no description";
            Integer resource_id = android.R.drawable.ic_media_ff;
            if(name.equals("a.x")) {
                units = "g-force";
                description = "databot's acceleration along its x-axis";
                resource_id = R.drawable.sensor_accel;
            }
            if(name.equals("a.y")) {
                units = "g-force";
                description = "databot's acceleration along its y-axis";
                resource_id = R.drawable.sensor_accel;
            }
            if(name.equals("a.z")) {
                units = "g-force";
                description = "databot's acceleration along its z-axis";
                resource_id = R.drawable.sensor_accel;
            }
            if(name.equals("g.x")){
                units = "degrees per second";
                description = "how fast the databot is spinning along its x axis";
                resource_id = R.drawable.sensor_gyro;
            }
            if(name.equals("g.y")){
                units = "degrees per second";
                description = "how fast the databot is spinning along its y axis\"";
                resource_id = R.drawable.sensor_gyro;
            }
            if(name.equals("g.z")){
                units = "degrees per second";
                description = "how fast the databot is spinning along its z axis\"";
                resource_id = R.drawable.sensor_gyro;
            }
            if(name.equals("m.x")){
                units = "microteslas (μT)";
                description = "strength of the magnetic field relative to databot's x axis";
                resource_id = R.drawable.sensor_magnet;
            }
            if(name.equals("m.y")){
                units = "microteslas (μT)";
                description = "strength of the magnetic field relative to databot's y axis";
                resource_id = R.drawable.sensor_magnet;
            }
            if(name.equals("m.z")){
                units = "microteslas (μT)";
                description = "strength of the magnetic field relative to databot's z axis";
                resource_id = R.drawable.sensor_magnet;
            }
            if(name.equals("altitude")) {
                units = "meters";
                description = "altitude in meters above sea level";
                resource_id = R.drawable.sensor_airpressure;
            }
            if(name.equals("UV A")){
                units = "ultraviolet A in μW/cm2";
                description = "power of 320-400 nanometer radiation in micro watts per square centimeter";
                resource_id = R.drawable.sensor_uvlight;
            }
            if(name.equals("UV B")){
                units = "ultraviolet B in μW/cm2";
                description = "power of 280-320 nanometer radiation in micro watts per square centimeter";
                resource_id = R.drawable.sensor_uvlight;
            }
            if(name.equals("UV Index")){
                units = "ultraviolet index";
                description = "0-10, Higher values represent a greater risk of sunburn";
                resource_id = R.drawable.sensor_uvlight;
            }
            if(name.equals("lumens")){
                units = "Lux";
                description = "measure of perceived brightness in lumens per square meter";
                resource_id = R.drawable.sensor_lux;
            }
            if(name.equals("mic amp")){
                units = "Volts";
                description = "peak to peak amplitude measured from the microphone in volts";
                resource_id = R.drawable.sensor_mic;
            }
            if(name.equals("mic dB")){
                units = "decibels";
                description = "sound intensity in decibels";
                resource_id = R.drawable.sensor_mic;
            }
            if(name.equals("mic freq")){
                units = "Hz";
                description = "Strongest frequency in hz";
                resource_id = R.drawable.sensor_mic;
            }
            if(name.equals("mic waveform")){
                units = "Volts";
                description = "time varying signal in volts";
                resource_id = R.drawable.sensor_mic;
            }
            if(name.equals("pressure")){
                units = "Pascals";
                description = "air pressure in Pascals";
                resource_id = R.drawable.sensor_airpressure;
            }
            if(name.equals("temperature")){
                units = "Celsius";
                description = "temperature in Celsius";
                resource_id = R.drawable.sensor_temp;
            }
            if(name.equals("humidity")){
                units = "percent";
                description = "humidity as percentage of maximum possible humidity given the same air temperature";
                resource_id = R.drawable.sensor_humidity;
            }
            if(name.equals("relative humidity")){
                units = "percent";
                description = "humidity as percentage of maximum possible humidity given the same air temperature";
                resource_id = R.drawable.sensor_humidity;
            }
            if(name.equals("absolute humidity")){
                units = "grams per cubic meter";
                description = "humidity in grams per cubic meter of air";
                resource_id = R.drawable.sensor_humidity;
            }
            if(name.equals("CO2")){
                units = "parts per million";
                description = "CO2 content of the air in parts per million";
                resource_id = R.drawable.sensor_co2;
            }
            if(name.equals("TVOC")){
                units = "parts per billion";
                description = "total volatile organics compounds of the air in parts per billion";
                resource_id = R.drawable.sensor_tvoc;
            }

            DatabotSensor sensor = new DatabotSensor(name + "-address",
                    name, units, description, resource_id);

            mSensors.put(sensor.getAddress(), sensor );
        }
    }

    private ISensorDiscoverer.Stub createDiscoverer(){
        return new ISensorDiscoverer.Stub() {
            @Override
            public String getName() throws RemoteException {
                return "Databot Sensors";
            }

            @Override
            public void scanDevices(IDeviceConsumer iDeviceConsumer) throws RemoteException {
                /*long currTime = System.currentTimeMillis();
                if( currTime - mtimeSincePopUp  > 10000) {
                    updateTimeSinceDialog();
                    databotDialog();
                }*/

                if(MainActivity.isRunning) {
                    iDeviceConsumer.onDeviceFound(DEVICE_ID, SENSOR_PREF_NAME, null);
                    iDeviceConsumer.onScanDone();
                }
            }

            @Override
            public void scanSensors(String s, ISensorConsumer iSensorConsumer) throws RemoteException {

                /*PendingIntent intent = DatabotSelect.getPendingIntent(ScienceJournalService.this);
                if(mDatabotService.getConnectionState() != mDatabotService.STATE_CONNECTED) {
                    try {
                        intent.send();
                    } catch (Exception e) {
                        Log.e(TAG, "getName: ", e);
                    }
                }*/
                getDatabotSensors();

                for(DatabotSensor sensor: mSensors.values()) {
                    iSensorConsumer.onSensorFound(sensor.getAddress(), sensor.getName(),
                            sensor.getBehavior(), sensor.getAppearance());
                }
            }

            @Override
            public ISensorConnector getConnector() throws RemoteException {
                return new ISensorConnector.Stub() {
                    @Override
                    public void startObserving(String sensorAddress,
                                               ISensorObserver iSensorObserver,
                                               ISensorStatusListener iSensorStatusListener,
                                               String s1) throws RemoteException {
                        iSensorStatusListener.onSensorConnecting();
                        getDatabotSensors();
                        DatabotSensor sensor = mSensors.get(sensorAddress);
                        if(sensor == null){
                            //for now:
                            Log.i(TAG, "startObserving: sensor called is null," +
                                    " probably old list from science journal restart");
                            iSensorStatusListener.onSensorError("this sensor is not found");
                            return;
                        }
                        sensor.startObserving(iSensorObserver, iSensorStatusListener);

                    }

                    @Override
                    public void stopObserving(String sensorAddress) throws RemoteException {
                        DatabotSensor sensor = mSensors.get(sensorAddress);
                        sensor.stopObserving();
                    }

                };
            }
        };
    }


    public void updateTimeSinceDialog(){
        mtimeSincePopUp = System.currentTimeMillis();
    }

    private void databotDialog(){
        PendingIntent intent = DatabotSelect.getPendingIntent(ScienceJournalService.this);
        try {
            intent.send();
        } catch (Exception e) {
            Log.e(TAG, "scanDevices: ", e);
        }
    }

    private void disconnectDataBotConnection() {
        unbindService(mdbServiceConnection);
        isDbServiceBound = false;
    }

    private void establishDataBotConnection() {
        dbIntent = new Intent(this, DatabotService.class);
        bindService(dbIntent, mdbServiceConnection, Context.BIND_AUTO_CREATE );
        isDbServiceBound = true;
    }
    void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.i(TAG, "bad sleep");
        }
    }

    //This allows this service to talk to the DatabotService
    private ServiceConnection mdbServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DatabotService.DatabotBinder myBinder = (DatabotService.DatabotBinder) service;
            mDatabotService = myBinder.getService();
            mServiceBound = true;
        }
    };

    public SJThread createSJThread(DataConsumer c, String name, String address){
        return new SJThread(c, name, address);
    }

    //Services do not implicitly create a worker thread. To avoid hanging up the main
    //thread with this polling loop, a worker thread is created.
    class SJThread implements Runnable {
        final private String TAG = this.getClass().getSimpleName();
        final private DataConsumer mDataConsumer;
        private String myName;
        private String myAddress;
        DatabotSensor sensor;

        public SJThread(final DataConsumer c, String name, String address) {
            mDataConsumer = c;
            myName = name;
            myAddress = address;
            sensor = mSensors.get(myAddress);
        }

        public void run() {
            Log.i(TAG, "thread running");
            while (mServiceBound && sensor.isReceiving() && isRunning ) {
                dataAndTime data = mDatabotService.getDataAndTime(myName);
                if(data != null) {
                    if (androidStartTime == 0) {
                        androidStartTime = System.currentTimeMillis();
                        arduinoStartTime = data.time;
                    }
                    //google science journal really likes it to be current time...
                    try{
                        mDataConsumer.onNewData(System.currentTimeMillis()/*androidStartTime + (data.time - arduinoStartTime)*/, data.data);
                    }catch(Exception e){
                        Log.i(TAG, "run: onNewDataexception:" + e.toString());
                    }
                    data = mDatabotService.getDataAndTime(myName);
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Log.i(TAG, "bad sleep");
                    }
                }
            }
            Log.i(TAG, "exiting thread for" + myName);
            return;
        }
    }

    public  interface DataConsumer {
        /**
         * @return true iff there is anyone still interested in this data
         */
        boolean isReceiving();

        /**
         * @param timestamp a timestamp (if none provided by an external device, use
         * {@link System#currentTimeMillis()}.
         * @param value the sensor's current value
         */
        void onNewData(long timestamp, double value);
    }

    class DatabotSensor {
        final private String TAG = this.getClass().getSimpleName();

        private SensorAppearanceResources appearance;
        private SensorBehavior behavior;
        private final String mAddress;
        private String mName;

        private ISensorStatusListener mListener = null;

        public DatabotSensor(String sensorAddress, String name, String units, String description, Integer resource_id ) {
            mAddress = sensorAddress;
            mName = name;
            appearance = new SensorAppearanceResources();
            behavior = new SensorBehavior();
            behavior.loggingId = name;
            appearance.iconId = resource_id;
            appearance.units = units;
            appearance.shortDescription = description;
            Log.i(TAG,"constructor");
        }

        protected SensorAppearanceResources getAppearance() {
            return appearance;
        }

        protected SensorBehavior getBehavior() {
            return behavior;
        }

        protected boolean connect() throws Exception {
            Log.i(TAG, "connect to sensors");
            establishDataBotConnection();
            return true;
        }

        final void startObserving(final ISensorObserver observer,
                                  final ISensorStatusListener listener) throws RemoteException {
            listener.onSensorConnecting();
            try {
                if (!connect()) {
                    listener.onSensorError("Could not connect");
                    return;
                }
            } catch (Exception e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "Connection error", e);
                }
                listener.onSensorError(e.getMessage());
                return;
            }
            listener.onSensorConnected();
            mListener = listener;

            streamData(new DataConsumer() {
                @Override
                public boolean isReceiving() {
                    return mListener != null;
                }

                @Override
                public void onNewData(long timestamp, double value) {
                    try {
                        try {
                            observer.onNewData(timestamp, value);
                        } catch (DeadObjectException e) {
                            reportError(e);
                            stopObserving();
                        }
                    } catch (RemoteException e) {
                        reportError(e);
                    }
                }
            });
        }

        final void stopObserving() throws RemoteException {
            disconnect();
            if (mListener != null) {
                mListener.onSensorDisconnected();
                mListener = null;
            }
        }

        final boolean isReceiving(){
            return mListener != null;
        }

        protected void streamData(final DataConsumer c) {
            Log.i(TAG, "streamData: being called");

            try{
                //c.onNewData(System.currentTimeMillis(), 3.1415);
            }catch(Exception e){
                Log.i(TAG, "caught exception:" + e.toString());
            }
            Thread thread = new Thread( new SJThread(c, mName, mAddress), "SJThread");
            thread.start();
        }

        protected void disconnect() {
            //Log.i(TAG, "disconnect");
            //disconnectDataBotConnection();
        }

        String getAddress() {
            return mAddress;
        }

        public String getName() {
            return mName;
        }

        private void reportError(RemoteException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "error sending data", e);
            }
        }

    }

}