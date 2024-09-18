package com.example.kuljeetsingh.loneworker;

import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Created by kuljeetsingh on 19/6/17.
 */

public interface InterfaceSensor {


    boolean isConnected();

    void setConnected(boolean state);

    boolean isPublishing();

    boolean isUpdated();

    int getLatestValue();

    void updateValue(BluetoothGattCharacteristic characteristic);

}
