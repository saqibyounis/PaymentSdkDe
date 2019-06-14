/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.objects;

public class SoftwareInfo {

    private String serialNumber, osType, osVersion, mpiType, mpiVersion;

    public SoftwareInfo(String serialNumber, String mpiType, String mpiVersion, String osType, String osVersion) {
        this.serialNumber = serialNumber;
        this.mpiType = mpiType;
        this.mpiVersion = mpiVersion;
        this.osType = osType;
        this.osVersion = osVersion;

    }
    /**
     * @return Device serial number in EMV compatible format 8 digits long
     */
    public String getSerialNumber() {
        return serialNumber;
    }
    /**
     * @return OS type, typically M000-OS or M000-TESTOS
     */
    public String getOsType() {
        return osType;
    }
    /**
     * @return The OS Version number
     */
    public String getOsVersion() {
        return osVersion;
    }
    /**
     * @return String describing the MPI installed. Typically M000-MPI or M000-TESTMPI
     */
    public String getMpiType() {
        return mpiType;
    }
    /**
     * @return String giving the MPI version number
     */
    public String getMpiVersion() {
        return mpiVersion;
    }
    /**
     * @return String giving then RPI version number
     */

    @Override
    public String toString() {
        return "SoftwareInfo{" +
                "serialNumber='" + serialNumber + '\'' +
                ", mpiType='" + mpiType + '\'' +
                ", mpiVersion='" + mpiVersion + '\'' +
                ", osType='" + osType + '\'' +
                ", osVersion='" + osVersion + '\'' +
                '}';
    }
}
