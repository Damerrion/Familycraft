package com.familycraft.model;

/** Thrown when a family action is not allowed. {@link #getKey()} is a message key in config.yml. */
public class FamilyException extends Exception {

    private final String key;

    public FamilyException(String key) {
        super(key);
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
