package pk.albertsjacmenovs.distanceapp;

import java.util.UUID;

public class Constants {

    public static final UUID BLE_UUID_SERVICE = UUID.fromString("10959b34-62b6-4e38-a055-265911433117");

    public static final UUID BLE_UUID_CHARACTERISTIC = UUID.fromString("fdcf4a3f-3fed-4ed2-84e6-04bbb9ae04d4");

    public static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //gatt service thing

    public static final long SCAN_PERIOD = 10000;

    public static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

}
