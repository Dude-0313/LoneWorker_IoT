package com.example.kuljeetsingh.loneworker;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.UUID;

/**
 * Created by kuljeetsingh on 19/6/17.
 */

public class LwBLEDevice implements Serializable {
    private static final String TAG = LwBLEDevice.class.getSimpleName();

    private static final String UNKNOWN_DEVICE = "Unknown Device";

    private BluetoothDevice bleDevice;


    @SerializedName("scan_record")
    private String mScanRecordHex;

    @SerializedName("device_name")
    private String mDeviceName;

    @SerializedName("device_address")
    private String mDeviceAddress;

    @SerializedName("rssi")
    private int rssi;

    @SerializedName("advertise_uuid")
    private String advertiseUUID;

 //   @SerializedName("mac_address")
//    private String macAddress;

    @SerializedName("sensor_name")
    private String sensorName;

    private int mDeviceState;
    private boolean mIsConnected = false;
    private boolean mIsPublishing = false;


    private static final UUID UUID_CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    protected  String mSensorName;

    LwBLEDevice(Context ctx, BluetoothDevice btdev, int devrssi){

        if(btdev==null ) {
            mDeviceName = UNKNOWN_DEVICE;
            mSensorName = "UNKNOWN";
        }
        else
        {
            bleDevice=btdev;
            mDeviceName = btdev.getName();
        }
        this.mDeviceAddress = btdev.getAddress();
        this.rssi=devrssi;
        this.mDeviceState= btdev.getBondState();
        this.mIsConnected = false;
        //this.mContext=ctx;
    }

    public String getDeviceName() { return mDeviceName;}
    public String getDeviceAddress() { return mDeviceAddress;}
    public int getDeviceState() {return mDeviceState;}
    public int getDeviceRSSI() { return rssi;}
    public BluetoothDevice getBaseDevice(){
        return bleDevice;
    }
    public String getSensorName(){ return mSensorName;}
    public void setConnected(boolean state){
        mIsConnected=state;
    }
    public boolean isConnected(){
        return mIsConnected;
    }
    public boolean isPublishing(){
        return mIsPublishing;
    }


}
