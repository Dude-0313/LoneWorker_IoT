package com.example.kuljeetsingh.loneworker;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.UnsupportedEncodingException;

/**
 * Created by kuljeetsingh on 26/7/17.
 */

public class LwMQTTService implements MqttCallback{
    private static final String TAG = LwMQTTService.class.getSimpleName();

    private final static String MQTT_BROKER_HOST = "tcp://192.168.1.101:1883";

    public final static String ACTION_MQTT_CONNECTION_SUCCESS = "com.example.kuljeetsingh.ble.ACTION_MQTT_CONNECTION_SUCCESS";
    public final static String ACTION_MQTT_CONNECTION_FAILED = "com.example.kuljeetsingh.ble.ACTION_MQTT_CONNECTION_FAILED";
    public final static String ACTION_MQTT_SUBSCRIBE_SUCCESS = "com.example.kuljeetsingh.ble.ACTION_MQTT_SUBSCRIBE_SUCCESS";
    public final static String ACTION_MQTT_SUBSCRIBE_FAILED = "com.example.kuljeetsingh.ble.ACTION_MQTT_SUBSCRIBE_FAILED";
    public final static String ACTION_MQTT_UNSUBSCRIBE_SUCCESS = "com.example.kuljeetsingh.ble.ACTION_MQTT_UNSUBSCRIBE_SUCCESS";
    public final static String ACTION_MQTT_UNSUBSCRIBE_FAILED = "com.example.kuljeetsingh.ble.ACTION_MQTT_UNSUBSCRIBE_FAILED";
    public final static String ACTION_MQTT_DISCONNECT_SUCCESS = "com.example.kuljeetsingh.ble.ACTION_MQTT_DISCONNECT_SUCCESS";
    public final static String ACTION_MQTT_DISCONNECT_FAILED = "com.example.kuljeetsingh.ble.ACTION_MQTT_DISCONNECT_FAILED";
    public final static String ACTION_MQTT_TOKEN_ARRIVED = "com.example.kuljeetsingh.ble.ACTION_MQTT_TOKEN_ARRIVED";
    public final static String ACTION_MQTT_TOKEN_DELIVERED = "com.example.kuljeetsingh.ble.ACTION_MQTT_TOKEN_DELIVERED";
    public final static String EXTRA_TOKEN_STRING = "com.example.kuljeetsingh.ble.EXTRA_TOKEN_STRING";
    public final static String EXTRA_PAYLOAD_STRING = "com.example.kuljeetsingh.ble.EXTRA_PAYLOAD_STRING";

    public final static String TOKEN_TIMESTAMP = "loneworker/timestamp";
    public final static String TOKEN_HEARTRATE = "loneworker/heartrate";
    public final static String TOKEN_LOCATION_LAT = "loneworker/location/lat";
    public final static String TOKEN_LOCATION_LONG = "loneworker/location/long";
    public final static String TOKEN_MOVEMENT = "loneworker/movement";
    public final static String TOKEN_ALERT = "loneworker/alert";

    private MqttAndroidClient client;
    private String clientId;
    private Context mContext;

    public void openConnection(Context context,String username, String password){
        clientId = MqttClient.generateClientId();
        mContext = context;
//        client =
//                new MqttAndroidClient(mContext, "tcp://broker.hivemq.com:1883",
 //                       clientId);
        client =
                new MqttAndroidClient(mContext, MQTT_BROKER_HOST,
                        clientId);

        try {
            IMqttToken token = client.connect();
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We are connected
                    Log.d(TAG, "onSuccess");
                    Intent intent = new Intent();
                    intent.setAction(ACTION_MQTT_CONNECTION_SUCCESS);
                    mContext.sendBroadcast(intent);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems
                    Log.d(TAG, "onFailure");
                    Intent intent = new Intent();
                    intent.setAction(ACTION_MQTT_CONNECTION_FAILED);
                    mContext.sendBroadcast(intent);
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
        client.setCallback(this);
    }

    public boolean publishTopic(String topic, String payload){
        boolean status=true;
        byte[] encodedPayload = new byte[0];
        try {
            encodedPayload = payload.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(encodedPayload);
            client.publish(topic, message);
        } catch (UnsupportedEncodingException | MqttException e) {
            status=false;
            Log.e(TAG,"Error publishing topic : "+topic);
            e.printStackTrace();
        }
        return status;
    }

    public void subscribeTopic(String topic){
        int qos = 1;
        try {
            IMqttToken subToken = client.subscribe(topic, qos);
            subToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // The message was published
                    Intent intent = new Intent();
                    intent.setAction(ACTION_MQTT_SUBSCRIBE_SUCCESS);
                    mContext.sendBroadcast(intent);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    // The subscription could not be performed, maybe the user was not
                    // authorized to subscribe on the specified topic e.g. using wildcards
                    Intent intent = new Intent();
                    intent.setAction(ACTION_MQTT_SUBSCRIBE_FAILED);
                    mContext.sendBroadcast(intent);

                }
            });
        } catch (MqttException e) {
            Log.e(TAG,"Cannot subscribe to : "+topic);
            e.printStackTrace();
        }
    }


    public void unsbscribeTopic(String topic){
        try {
            IMqttToken unsubToken = client.unsubscribe(topic);
            unsubToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // The subscription could successfully be removed from the client
                    Intent intent = new Intent();
                    intent.setAction(ACTION_MQTT_UNSUBSCRIBE_SUCCESS);
                    mContext.sendBroadcast(intent);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    // some error occurred, this is very unlikely as even if the client
                    // did not had a subscription to the topic the unsubscribe action
                    // will be successfully
                    Intent intent = new Intent();
                    intent.setAction(ACTION_MQTT_UNSUBSCRIBE_FAILED);
                    mContext.sendBroadcast(intent);
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void closeConnection(){
        try {
            IMqttToken disconToken = client.disconnect();
            disconToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Intent intent = new Intent();
                    intent.setAction(ACTION_MQTT_DISCONNECT_SUCCESS);
                    mContext.sendBroadcast(intent);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    // something went wrong, but probably we are disconnected anyway
                    Intent intent = new Intent();
                    intent.setAction(ACTION_MQTT_DISCONNECT_FAILED);
                    mContext.sendBroadcast(intent);
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void connectionLost(java.lang.Throwable cause){
        Log.e(TAG,"MQTT Connection Lost !!");
        Intent intent = new Intent();
        intent.setAction(ACTION_MQTT_CONNECTION_FAILED);
        mContext.sendBroadcast(intent);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token){
        Intent intent = new Intent();
        intent.setAction(ACTION_MQTT_TOKEN_DELIVERED);
        intent.putExtra(EXTRA_TOKEN_STRING,token.toString());
        mContext.sendBroadcast(intent);
    }

    @Override
    public void messageArrived(java.lang.String topic, MqttMessage message){
        Intent intent = new Intent();
        Log.e(TAG,"ALERT RECEIVED !!!"+message.toString());
        intent.setAction(ACTION_MQTT_TOKEN_ARRIVED);
        intent.putExtra(EXTRA_TOKEN_STRING,topic);
        intent.putExtra(EXTRA_PAYLOAD_STRING,message.toString());
        mContext.sendBroadcast(intent);
    }

    public static IntentFilter getIntentFilter(IntentFilter intentFilter) {
//        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_MQTT_CONNECTION_SUCCESS);
        intentFilter.addAction(ACTION_MQTT_CONNECTION_FAILED);
        intentFilter.addAction(ACTION_MQTT_DISCONNECT_SUCCESS);
        intentFilter.addAction(ACTION_MQTT_DISCONNECT_FAILED);
        intentFilter.addAction(ACTION_MQTT_SUBSCRIBE_SUCCESS);
        intentFilter.addAction(ACTION_MQTT_SUBSCRIBE_FAILED);
        intentFilter.addAction(ACTION_MQTT_UNSUBSCRIBE_SUCCESS);
        intentFilter.addAction(ACTION_MQTT_UNSUBSCRIBE_FAILED);
        intentFilter.addAction(ACTION_MQTT_TOKEN_ARRIVED);
        intentFilter.addAction(ACTION_MQTT_TOKEN_DELIVERED);
        return intentFilter;
    }


}
