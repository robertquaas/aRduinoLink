package com.arbotics.databot.sciencejournal;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class MainActivity extends AppCompatActivity {

    public static boolean isRunning = false;

    private TextView bleDataText = null;
    private TextView bleStateText = null;
    private Intent dbIntent = null;
    boolean isDbServiceBound = false;
    boolean mServiceBound = false;
    private DatabotService mDatabotService;
    private Integer mButtonIdIndex = 0;

    final private String TAG = this.getClass().getSimpleName();
    private ScrollView scrollViewDevices = null;
    private LinearLayout scrollViewLayout = null;

    Button scanButton;
    Button bleButton;

    private HashMap<String, DatabotSelectButton> buttons = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isRunning = true;
        setContentView(R.layout.activity_main);
        Context context = MainActivity.this;

        establishDataBotConnection();

        scrollViewDevices = findViewById(R.id.scrollviewdevices);

        scrollViewLayout = new LinearLayout(this);
        scrollViewLayout.setOrientation(LinearLayout.VERTICAL);
        scrollViewDevices.addView(scrollViewLayout);

        final int PERMISSION_REQUEST_COARSE_LOCATION = 456;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }

        bleStateText = (TextView) findViewById(R.id.ble_state_display);
        bleDataText = (TextView) findViewById(R.id.ble_packet_display);

        LocalBroadcastManager.getInstance(this).registerReceiver(mBleMessageReciever,
                new IntentFilter("ble-packet-data"));

        LocalBroadcastManager.getInstance(this).registerReceiver(mDeviceMessageReciever,
                new IntentFilter("ble-device-data"));

        scanButton = findViewById(R.id.scanbutton);
        bleButton = findViewById(R.id.blebutton);

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Button clicked = (Button) v;
                //setScanButton();
                unSetButtons();
                scrollViewLayout.removeAllViews();
                buttons.clear();
                mDatabotService.setScan();
                mDatabotService.selectedDevice = null;
                mDatabotService.disconnect();
                mDatabotService.mButtonClicked = true;

            }
        });

        bleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button clicked = (Button) v;
                /*for ( Integer key : buttons.keySet()){
                    scrollViewLayout.removeView(buttons.get(key));
                }
                buttons.clear();*/
                mDatabotService.disconnect();
                mDatabotService.selectedDevice = null;
                mDatabotService.mButtonClicked = true;
                mDatabotService.toggleBluetooth();
            }
        });

    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "main activity destroyed");
        // Unregister since the activity is about to be closed.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBleMessageReciever);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mDeviceMessageReciever);
        disconnectDataBotConnection();
        isRunning = false;
        super.onDestroy();
    }

    private void disconnectDataBotConnection() {
        unbindService(mdbServiceConnection);

        isDbServiceBound = false;
    }

    private void establishDataBotConnection() {
        dbIntent = new Intent(this, DatabotService.class);
        bindService(dbIntent, mdbServiceConnection, Context.BIND_AUTO_CREATE);
        isDbServiceBound = true;
    }

    private BroadcastReceiver mBleMessageReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("state");
            if(message != null){
                bleStateText.setText(message);
            }
            message = intent.getStringExtra("packet");
            if(message != null){
                bleDataText.setText(message);
            }
            //Log.d("receiver", "Got message: " + message);
        }
    };

    private BroadcastReceiver mDeviceMessageReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("clear");
            if(message!=null){
                scrollViewLayout.removeAllViews();
                buttons.clear();
            }
            String[] messages = intent.getStringArrayExtra("device");
            //Log.d("receiver", "Got message: " + message);
            if( messages != null ) {
                DatabotSelectButton button = new DatabotSelectButton(MainActivity.this, messages[0], messages[1], messages[2], mButtonIdIndex);
                if (buttons.get(button.UUID) == null) {
                    buttons.put(button.UUID, button);
                    scrollViewLayout.addView(button);
                    reorder(scrollViewLayout);
                }else{
                    buttons.get(button.UUID).rssi = button.rssi;
                    buttons.get(button.UUID).updateText();
                    reorder(scrollViewLayout);
                }
                mButtonIdIndex++;
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        DatabotSelectButton clicked = (DatabotSelectButton) v;
                        DatabotSelectButton deviceButton = buttons.get(clicked.UUID);
                        mDatabotService.disconnect();
                        unSetButtons();
                        deviceButton.setActive();
                        mDatabotService.setSelectedDevice(deviceButton.name + deviceButton.UUID);
                    }
                });
            }
            message = intent.getStringExtra("unset");
            if( message != null ){
                unSetButtons();
            }

            message = intent.getStringExtra("scan");
            if( message != null ){
                if(message.equals("start")){
                    setScanButton();
                }
                if(message.equals("stop")){
                    unSetScanButton();
                }
            }
        }
    };

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

            List<DatabotDevice> devices = mDatabotService.getDevices();
            if(devices != null) {
                for (DatabotDevice device : devices) {
                    DatabotSelectButton button = new DatabotSelectButton(MainActivity.this, device.name, device.UUID, device.rssi, mButtonIdIndex);
                    if (buttons.put(button.UUID, button) == null) {
                        scrollViewLayout.addView(button);
                    }
                    mButtonIdIndex++;
                    button.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            DatabotSelectButton clicked = (DatabotSelectButton) v;
                            DatabotSelectButton deviceButton = buttons.get(clicked.UUID);
                            mDatabotService.setSelectedDevice(deviceButton.name + deviceButton.UUID);
                        }
                    });
                }
            }
        }
    };

    private void reorder(LinearLayout view){
        List<DatabotSelectButton> devices = new LinkedList<>();

        for (String key: buttons.keySet()){
            devices.add(buttons.get(key));
        }

        boolean hasSwapped = true;
        while(hasSwapped) {
            hasSwapped = false;
            for (Integer i = 0; i < devices.size() - 1; i++) {
                if (Integer.parseInt(devices.get(i).rssi) < Integer.parseInt(devices.get(i + 1).rssi)) {
                    Collections.swap(devices, i, i + 1);
                    hasSwapped = true;
                }
            }
        }

        view.removeAllViews();
        for(DatabotSelectButton sorted: devices){
            view.addView(sorted);
        }

    }

    private void unSetButtons(){
        for (String key: buttons.keySet()){
            buttons.get(key).unSetActive();
        }
    }

    public void setScanButton(){
        scanButton.setBackgroundColor(getResources().getColor(R.color.databotGreen));
        scanButton.setText("Scanning...");
    }

    public void unSetScanButton(){
        scanButton.setBackgroundColor(getResources().getColor(R.color.databotDarkBlue));
        scanButton.setText("Scan");
    }

}