package pk.albertsjacmenovs.distanceapp;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

import xdroid.toaster.Toaster;

import static pk.albertsjacmenovs.distanceapp.Constants.BLE_UUID_CHARACTERISTIC;
import static pk.albertsjacmenovs.distanceapp.Constants.BLE_UUID_SERVICE;
import static pk.albertsjacmenovs.distanceapp.Constants.CONFIG_DESCRIPTOR;
import static pk.albertsjacmenovs.distanceapp.Constants.SCAN_PERIOD;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    /*
     * Field Declarations
     */

    // Main UI components
    private Button connectBLE;
    private TextView distanceText;

    // Dialog Box for showing connection progress
    private ProgressDialog mProcessDialog = null;

    // For executing progress timeout when device is not found
    private Handler handler = new Handler();

    // For storing the device address
    private SharedPreferences prefs;

    final String PREFS_FILE = "MyPrefsFile";
    final String PREF_BLE_DEVICE = "my_ble_device";

    final String DOESNT_EXIST_BLE_DEVICE = "";

    private boolean isConnected = false;

    private ArrayList<BluetoothDevice> bondedDevices = new ArrayList<>();

    private BluetoothLeScanner bluetoothLeScanner;

    // Callback for registering new scanned devices
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            for(BluetoothDevice bluetoothDevice:bondedDevices){
                if(bluetoothDevice.getAddress().equals(result.getDevice().getAddress())){
                    return;
                }
            }
            bondedDevices.add(result.getDevice());
            // We connect when our device is found
            if(result.getDevice().getAddress().equals("E1:A8:27:61:61:27")){
                mProcessDialog.dismiss();
                bluetoothLeScanner.stopScan(leScanCallback);
                prefs.edit().putString(PREF_BLE_DEVICE,result.getDevice().getAddress()).apply();
                Connect(result.getDevice().getAddress());
            }
        }
    };

    private BluetoothGatt bluetoothGatt;

    // For handling different states of the connection
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true;
                    bluetoothGatt = gatt;
                    bluetoothGatt.discoverServices();
                    showMessage("Connection to bluetooth device successful!");
                    connectBLE.setText("Connected");
                    connectBLE.setEnabled(false);
                }else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected = false;
                    showMessage("Disconnected from bluetooth device");
                    connectBLE.setEnabled(true);
                    connectBLE.setText("Connect");
                    gatt.close();
                }
            }else{
                isConnected = false;
                showMessage("Error "+status+" encountered");
                connectBLE.setEnabled(true);
                connectBLE.setText("Connect");
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService bluetoothGattService = bluetoothGatt.getService(BLE_UUID_SERVICE);
            BluetoothGattCharacteristic bluetoothGattCharacteristic = bluetoothGattService.getCharacteristic(BLE_UUID_CHARACTERISTIC);

            if(bluetoothGattCharacteristic == null){
//                showMessage("Device characteristic unavailable!");
                return;
            }

            if (!bluetoothGatt.setCharacteristicNotification(bluetoothGattCharacteristic, true)) {
                showMessage("Failed to subscribe to sensor data!");
                return;
            }
            BluetoothGattDescriptor desc = bluetoothGattCharacteristic.getDescriptor(CONFIG_DESCRIPTOR);
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(desc);

            showMessage("Subscribed to sensor!");
        }

        //When the BLE Characteristic is changed
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if(characteristic.getUuid().equals(BLE_UUID_CHARACTERISTIC)){
                byte[] bytes = characteristic.getValue();

                //reformat the string appropriately. I pass my BLE distance characteristic as a string.

                String str1 = new String(bytes, StandardCharsets.UTF_8);
                str1 = str1.replace("\n"," ");
                str1 = str1.replace("\r"," ");

                String[] str2 = str1.split(" ");

                String str3 = "";

                for(String s:str2){
                    if(s.contains(".")){
                        str3 = s.substring(0, s.indexOf(".") + 2);
                    }
                }
                String finalStr = str3;
                runOnUiThread(() -> distanceText.setText(finalStr));

            }
        }
    };

    //onCreate

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);

        initializeViews();

    }

    //onResume

    @Override
    protected void onResume() {
        super.onResume();
        if(!checkPermissions()){
            requestPermissions();
        }
    }

    //onPause

    @Override
    protected void onPause() {
        super.onPause();
        if(mProcessDialog != null){
            mProcessDialog.dismiss();
        }
    }

    //onDestroy (quit)

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Disconnect();
    }

   //When the connect button is pressed, we connect to the device if the required address exists
    
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.bt_connection) {
            if(!Objects.equals(prefs.getString(PREF_BLE_DEVICE, DOESNT_EXIST_BLE_DEVICE), "")){
                Connect(prefs.getString(PREF_BLE_DEVICE,DOESNT_EXIST_BLE_DEVICE));
            }else{
                scanLeDevice();
            }
        }
    }


    //Requesting for Location permissions


    @TargetApi(Build.VERSION_CODES.M)
    public boolean checkPermissions() {
        // Permission check
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        else {
            return true;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void requestPermissions() {
        // Permission check
        requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION},
                Constants.PERMISSION_REQUEST_COARSE_LOCATION);
    }



    private void initializeViews(){

        connectBLE = findViewById(R.id.bt_connection);  //the button
        distanceText = findViewById(R.id.distance_txt); //the distance text

        connectBLE.setOnClickListener(this);

    }

    private void scanLeDevice() {
        bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        if(!checkPermissions()){
            showMessage("Enable all permissions first!");
            requestPermissions();
            return;
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showMessage("Device doesn't support BLE!");
            return;
        }
        if(bluetoothLeScanner == null){
            showMessage("Enable Bluetooth first!");
            return;
        }
        if(!bondedDevices.isEmpty()){
            bondedDevices.clear();
        }
        mProcessDialog = ProgressDialog.show(this, null,
                getString(R.string.scanning_nearby_devices), true);
        mProcessDialog.setCancelable(false);
        // Stops scanning after a pre-defined scan period.
        handler.postDelayed(() -> {
            mProcessDialog.dismiss();
            if(bondedDevices.isEmpty()){
                showMessage("No Devices Available!");
            }
            bluetoothLeScanner.stopScan(leScanCallback);
        }, SCAN_PERIOD);

        bluetoothLeScanner.startScan(leScanCallback);
    }

    public void Connect(String macAddress){

        Disconnect();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(!checkPermissions()){
            showMessage("Enable all permissions first!");
            requestPermissions();
            return;
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            showMessage("Device doesn't support BLE!");
            return;
        }
        if(bluetoothAdapter == null){
            showMessage("Enable Bluetooth first!");
            return;
        }

        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress);

        if(bluetoothDevice == null){
            showMessage("Device not found!");
            return;
        }
        bluetoothGatt = bluetoothDevice.connectGatt(getApplicationContext(),false,gattCallback,BluetoothDevice.TRANSPORT_LE);
    }

    private void Disconnect () {
        if ( bluetoothGatt != null ) {
            bluetoothGatt.close();
        }
        bluetoothGatt = null;
    }

    private void showMessage(String message){
        runOnUiThread(() -> {
            Toaster.toast(message);
        });
    }
}