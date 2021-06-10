// This #include statement was automatically added by the Particle IDE.
#include <HC-SR04.h>

// This #include statement was automatically added by the Particle IDE.
#include <DiagnosticsHelperRK.h>
#include <cmath>

// trigger / echo pins
const int triggerPin = A0;
const int echoPin = D0;
double distance;
HC_SR04 rangefinder = HC_SR04(triggerPin, echoPin);



const unsigned long UPDATE_INTERVAL = 500;
unsigned long lastUpdate = 0;

const BleUuid serviceUuid("10959b34-62b6-4e38-a055-265911433117"); 

BleCharacteristic distanceCharacteristic("distance", BleCharacteristicProperty::NOTIFY, BleUuid("fdcf4a3f-3fed-4ed2-84e6-04bbb9ae04d4"), serviceUuid);


void configureBLE()
{
BLE.addCharacteristic(distanceCharacteristic);


BleAdvertisingData advData;

// Advertise our private service only
advData.appendServiceUUID(serviceUuid);

// Continuously advertise when not connected
BLE.advertise(&advData);
}


void setup()
{
    configureBLE();
    Particle.variable("distanceF", distance);
    Serial.begin(9600);
    rangefinder.init();
   
}


void loop()
{
    

    unsigned long currentMillis = millis();
    
    if (currentMillis - lastUpdate >= UPDATE_INTERVAL)
    {
        lastUpdate = millis();
        if (BLE.connected())
        {
            unsigned long start = micros();
            float inch = rangefinder.distInch();
            distance = (double)inch;
            if(distance != -1){
                distance *= 2.54; //convert inches to centimeters
            }
            else{
                distance = -1;
            }
            
            distance = std::round(distance * 100.0) / 100.0;    //convert to 2 decimal places
            unsigned long calcTime = micros() - start;
            Serial.printf("Range finding duration: %lu | Distance in inches: %.2f\n", calcTime, inch);
            distanceCharacteristic.setValue(std::to_string(distance));  //set distance characteristic to distance

        }

    }
}
