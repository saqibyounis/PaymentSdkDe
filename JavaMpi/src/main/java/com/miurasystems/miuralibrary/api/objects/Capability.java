/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.objects;

public class Capability {

    private String name;
    private String value;
    private boolean hasValue;

    public Capability(String name) {
        this.name = name;
        this.hasValue = false;
    }

    public Capability(String name, String value) {
        this.name = name;
        this.value = value;
        this.hasValue = true;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public boolean isHasValue() {
        return hasValue;
    }
}
