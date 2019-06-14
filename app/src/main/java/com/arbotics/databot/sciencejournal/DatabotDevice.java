package com.arbotics.databot.sciencejournal;

import android.bluetooth.BluetoothDevice;

public class DatabotDevice {
    public final String name;
    public final String UUID;
    public final String rssi;
    public final BluetoothDevice mDevice;

    public DatabotDevice(String name, String UUID, String rssi,BluetoothDevice device){
        this.name = name;
        this.UUID = UUID;
        this.rssi = rssi;
        this.mDevice = device;
    }

}
