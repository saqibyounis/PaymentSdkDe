/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.objects;




public class P2PEStatus  {


    /*!
    * @brief This is true after the P2PE Initialise call
    */
    public boolean  isInitialised;

    /*!
     * @brief This is true when a valid online PIN key is installed
     */
    public boolean isPINReady;

    /*!
     * @brief This is true when a valid Account Data or SRED encryption key is installed
     */
    public boolean isSREDReady;

}
