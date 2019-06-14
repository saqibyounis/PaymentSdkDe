/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.examples.connectors;

import android.support.annotation.Nullable;

import com.intel.bluetooth.BluetoothConsts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.concurrent.SynchronousQueue;

import javax.bluetooth.DataElement;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;

class ServiceRecordQueueInfo {
    @Nullable
    final ServiceRecord mServiceRecord;

    final RemoteDevice mRemoteDevice;

    final boolean mRecordAvailable;

    final String mErrorResponseCode;


    ServiceRecordQueueInfo(RemoteDevice remoteDevice, String errorResponseCode) {
        mRecordAvailable = false;
        mErrorResponseCode = errorResponseCode;
        mRemoteDevice = remoteDevice;
        mServiceRecord = null;
    }

    ServiceRecordQueueInfo(RemoteDevice remoteDevice, ServiceRecord serviceRecord) {
        mRecordAvailable = true;
        mServiceRecord = serviceRecord;
        mRemoteDevice = remoteDevice;
        mErrorResponseCode = "no error";
    }
}

class MyDiscoveryListener implements DiscoveryListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyDiscoveryListener.class);

    private final SynchronousQueue<ServiceRecordQueueInfo> mQueue;
    private final RemoteDevice mRemoteDevice;

    MyDiscoveryListener(
            SynchronousQueue<ServiceRecordQueueInfo> queue,
            RemoteDevice device
    ) {
        mQueue = queue;
        mRemoteDevice = device;
    }


    @Override
    public void deviceDiscovered(@Nullable RemoteDevice btDevice, @Nullable DeviceClass arg1) {
        throw new AssertionError("Unexpected");
    }

    @Override
    public void inquiryCompleted(int arg0) {
        throw new AssertionError("Unexpected");
    }

    @Override
    public void serviceSearchCompleted(int transId, int respCode) {
        LOGGER.trace("serviceSearchCompleted({}, {})", transId, respCode);

        String msg;
        switch (respCode) {
            case SERVICE_SEARCH_COMPLETED:
                return;
            case SERVICE_SEARCH_TERMINATED:
                msg = "serviceSearchCompleted: SERVICE_SEARCH_TERMINATED";
                break;
            case SERVICE_SEARCH_ERROR:
                msg = "serviceSearchCompleted: SERVICE_SEARCH_ERROR";
                break;
            case SERVICE_SEARCH_NO_RECORDS:
                msg = "serviceSearchCompleted: SERVICE_SEARCH_NO_RECORDS";
                break;
            case SERVICE_SEARCH_DEVICE_NOT_REACHABLE:
                msg = "serviceSearchCompleted: SERVICE_SEARCH_DEVICE_NOT_REACHABLE";
                break;
            default:
                msg = "???";
                break;
        }

        ServiceRecordQueueInfo info = new ServiceRecordQueueInfo(mRemoteDevice, msg);
        addInfoToQueue(info);
    }

    @Override
    public void servicesDiscovered(int transID, @Nullable ServiceRecord[] serviceRecords) {
        LOGGER.trace("servicesDiscovered({}, serviceRecords)", transID);

        if (serviceRecords == null) {
            throw new AssertionError("null serviceRecords?");
        }
        LOGGER.debug("{} serviceRecord(s)", serviceRecords.length);
        for (ServiceRecord record : serviceRecords) {
            LOGGER.debug("\n{}", serviceRecordToString(record));
        }

        ServiceRecordQueueInfo info = new ServiceRecordQueueInfo(mRemoteDevice, serviceRecords[0]);
        addInfoToQueue(info);
    }

    private void addInfoToQueue(ServiceRecordQueueInfo info) {
        int retries = 4;
        while (retries > 0) {
            try {
                mQueue.put(info);
                LOGGER.debug("info added to queue ok");
                return;
            } catch (InterruptedException e) {
                //noinspection ThrowableInFormattedMessage
                LOGGER.info("Interrupted putting bad record into queue? {}", info, e);
            }
            retries -= 1;
        }
        LOGGER.warn("Interrupted many times whilst putting bad record into queue!! {}", info);
    }

    @SuppressWarnings("unchecked")
    private static String serviceRecordToString(ServiceRecord record) {
        StringBuilder builder = new StringBuilder(2048);
        builder.append("ServiceRecord: {\n");

        RemoteDevice hostDevice = record.getHostDevice();
        String friendlyName;
        try {
            friendlyName = hostDevice.getFriendlyName(false);
        } catch (IOException ignore) {
            friendlyName = "??? EXCEPTION ???";
        }
        String bluetoothAddress = hostDevice.getBluetoothAddress();
        builder.append(String.format("    HostDevice: %s, %s\n", friendlyName, bluetoothAddress));

        String url = record.getConnectionURL(ServiceRecord.AUTHENTICATE_ENCRYPT, false);
        builder.append("    connectionUrl: ").append(url).append('\n');

        builder.append("    Attributes:\n");
        int[] attributeIDs = record.getAttributeIDs();
        for (int id : attributeIDs) {
            DataElement attribute = record.getAttributeValue(id);
            if (attribute == null) {
                builder.append("attribute == null?!\n");
                continue;
            }
            String indent1 = "        ";
            String indent2 = "            ";
            String indent3 = "                ";

            builder.append(indent1).append(id).append(" = ");

            switch (id) {
                case BluetoothConsts.ServiceRecordHandle:
                    long aLong = attribute.getLong();
                    builder.append("ServiceRecordHandle: ").append(aLong).append('\n');
                    break;
                case BluetoothConsts.ServiceClassIDList:
                    builder.append("ServiceClassIDList:\n");
                    Enumeration<DataElement> datseq =
                            (Enumeration<DataElement>) attribute.getValue();
                    ArrayList<DataElement> list = Collections.list(datseq);
                    try {
                        for (DataElement uuid : list) {
                            String str = uuidToString((UUID) uuid.getValue());
                            builder.append(indent2).append("* ").append(str).append('\n');
                        }
                    } catch (Throwable ex) {
                        LOGGER.info("uuid problem", ex);
                    }
                    break;
                case BluetoothConsts.ServiceRecordState:
                    builder.append("ServiceRecordState: ").append(attribute.getLong()).append('\n');
                    break;
                case BluetoothConsts.ServiceID:
                    builder.append("ServiceID: ").append(attribute.getValue()).append('\n');
                    break;
                case BluetoothConsts.ProtocolDescriptorList:
                    builder.append("ProtocolDescriptorList\n");

                    // ProtocolDescriptorList is a DATSEQ of DATSEQ of objects
                    Enumeration<DataElement> datseq2 =
                            (Enumeration<DataElement>) attribute.getValue();
                    ArrayList<DataElement> list2 = Collections.list(datseq2);

                    for (DataElement dataElement : list2) {
                        Enumeration<DataElement> datseq3 =
                                (Enumeration<DataElement>) dataElement.getValue();

                        ArrayList<DataElement> vectorlist = Collections.list(datseq3);
                        builder.append(indent2).append("DATSEQ {\n");

                        Iterator<DataElement> iterator = vectorlist.iterator();

                        DataElement uuid = iterator.next();
                        if (uuid.getDataType() != DataElement.UUID) {
                            throw new AssertionError();
                        }
                        builder.append(indent3).append(
                                uuidToString((UUID) uuid.getValue())).append('\n');
                        while (iterator.hasNext()) {
                            builder.append(indent3).append(iterator.next()).append('\n');
                        }
                        builder.append(indent2).append("}\n");
                    }
                    break;
                default:
                    builder.append(" ??? value = ");

                    switch (attribute.getDataType()) {
                        case DataElement.NULL:
                            builder.append("NULL\n");
                            break;
                        case DataElement.U_INT_1:
                        case DataElement.U_INT_2:
                        case DataElement.U_INT_4:
                        case DataElement.U_INT_8:
                        case DataElement.U_INT_16:
                        case DataElement.INT_1:
                        case DataElement.INT_2:
                        case DataElement.INT_4:
                        case DataElement.INT_8:
                        case DataElement.INT_16:
                            builder.append(attribute.getLong()).append('\n');
                            break;
                        case DataElement.BOOL:
                            builder.append(attribute.getBoolean()).append('\n');
                            break;
                        case DataElement.URL:
                        case DataElement.UUID:
                        case DataElement.STRING:
                        case DataElement.DATSEQ:
                        case DataElement.DATALT:
                            builder.append(attribute.getValue()).append('\n');
                            break;
                    }
            }
        }

        builder.append('}');
        return builder.toString();
    }

    private static String uuidToString(UUID uuid) {
        String s = "UUID " + uuid.toString();
        if (BluetoothConsts.L2CAP_PROTOCOL_UUID.equals(uuid)) {
            return s + " (L2CAP)";
        } else if (BluetoothConsts.RFCOMM_PROTOCOL_UUID.equals(uuid)) {
            return s + " (RFCOMM)";
        } else if (BluetoothConsts.SERIAL_PORT_UUID.equals(uuid)) {
            return s + " (SERIAL PORT)";
        } else if (BluetoothConsts.OBEX_PROTOCOL_UUID.equals(uuid)) {
            return s + " (OBEX)";
        } else if (BluetoothConsts.OBEXFileTransferServiceClass_UUID.equals(uuid)) {
            return s + " (OBEX File Transfer)";
        } else {
            return s;
        }
    }
}
