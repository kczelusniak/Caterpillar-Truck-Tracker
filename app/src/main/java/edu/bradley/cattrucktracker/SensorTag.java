/*
 * Copyright 2016 Nathan Clark, Krzysztof Czelusniak, Michael Holwey, Dakota Leonard
 */

package edu.bradley.cattrucktracker;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

class SensorTag {
    private final double GYROSCOPE_RANGE = 250;
    private final double ACCELEROMETER_RANGE = 8;
    private final TruckState truckState;
    private final BackEnd backEnd;

    SensorTag(Context context, TruckState truckState, BackEnd backEnd) {
        BluetoothManager bluetoothManager
                = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice("B0:B4:48:C0:4C:85");
        bluetoothDevice.connectGatt(context, true, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                gatt.discoverServices();
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                BluetoothGattService movementService = getMovementService(gatt);
                UUID periodUuid = getPeriodUuid();
                BluetoothGattCharacteristic periodCharacteristic
                        = movementService.getCharacteristic(periodUuid);
                periodCharacteristic.setValue(new byte[]{0x0A});
                gatt.writeCharacteristic(periodCharacteristic);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt,
                                              BluetoothGattCharacteristic characteristic,
                                              int status) {
                UUID characteristicWrittenTo = characteristic.getUuid();
                UUID periodUuid = getPeriodUuid();
                if (characteristicWrittenTo.equals(periodUuid)) {
                    BluetoothGattService movementService = getMovementService(gatt);
                    UUID dataUuid = UUID.fromString("f000aa81-0451-4000-b000-000000000000");
                    BluetoothGattCharacteristic dataCharacteristic
                            = movementService.getCharacteristic(dataUuid);
                    gatt.setCharacteristicNotification(dataCharacteristic, true);
                    UUID notificationUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
                    BluetoothGattDescriptor notificationDescriptor
                            = dataCharacteristic.getDescriptor(notificationUuid);
                    notificationDescriptor
                            .setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(notificationDescriptor);
                }
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                          int status) {
                BluetoothGattService movementService = getMovementService(gatt);
                UUID configurationUuid = UUID.fromString("f000aa82-0451-4000-b000-000000000000");
                BluetoothGattCharacteristic configurationCharacteristic
                        = movementService.getCharacteristic(configurationUuid);
                configurationCharacteristic.setValue(new byte[]{0x39, 0x02});
                gatt.writeCharacteristic(configurationCharacteristic);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic) {
                byte[] movementData = characteristic.getValue();

                byte gyroscopeZFirstByte = movementData[4];
                byte gyroscopeZSecondByte = movementData[5];
                double gyroscopeZ = convertRawDatum
                        (gyroscopeZFirstByte, gyroscopeZSecondByte, GYROSCOPE_RANGE);

                if (gyroscopeZ >= 14.8410) {
                    SensorTag.this.truckState.setTruckBedUp(true);
                }
                else if (gyroscopeZ <= -14.8410) {
                    SensorTag.this.truckState.setTruckBedUp(false);
                }

                byte accelerometerXFirstByte = movementData[6];
                byte accelerometerXSecondByte = movementData[7];
                double accelerometerX = convertRawDatum
                        (accelerometerXFirstByte, accelerometerXSecondByte, ACCELEROMETER_RANGE);

                byte accelerometerYFirstByte = movementData[8];
                byte accelerometerYSecondByte = movementData[9];
                double accelerometerY = convertRawDatum
                        (accelerometerYFirstByte, accelerometerYSecondByte, ACCELEROMETER_RANGE);

                byte accelerometerZFirstByte = movementData[10];
                byte accelerometerZSecondByte = movementData[11];
                double accelerometerZ = convertRawDatum
                        (accelerometerZFirstByte, accelerometerZSecondByte, ACCELEROMETER_RANGE);

                double accelerometerXMagnitude = Math.abs(accelerometerX);
                double accelerometerYMagnitude = Math.abs(accelerometerY);
                double accelerometerZMagnitude = Math.abs(accelerometerZ);

                if (accelerometerXMagnitude >= 0.2175 || accelerometerYMagnitude >= 0.2175
                        || accelerometerZMagnitude >= 0.2175) {
                    SensorTag.this.truckState.setTruckBedVibrating(true);
                }
                else {
                    SensorTag.this.truckState.setTruckBedVibrating(false);
                }

                SensorTag.this.truckState.update();
            }

            private BluetoothGattService getMovementService(BluetoothGatt bluetoothGatt) {
                UUID serviceUuid = UUID.fromString("f000aa80-0451-4000-b000-000000000000");
                return bluetoothGatt.getService(serviceUuid);
            }

            private UUID getPeriodUuid() {
                return UUID.fromString("f000aa83-0451-4000-b000-000000000000");
            }

            private double wordToDouble(byte firstByte, byte secondByte) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
                        .put(firstByte).put(secondByte);
                return (double) byteBuffer.getShort(0);
            }

            private double convertRawDatum(byte firstRawByte, byte secondRawByte, double range) {
                double rawDatum = wordToDouble(firstRawByte, secondRawByte);

                int signedShortValueCount = Short.MAX_VALUE + 1;
                double rawDatumValueCount = signedShortValueCount / range;

                return rawDatum / rawDatumValueCount;
            }
        });

        this.truckState = truckState;
        this.backEnd = backEnd;
    }
}
