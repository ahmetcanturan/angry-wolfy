package com.buddywolfy.angrywolfy.web.view;

import jakarta.validation.constraints.Size;

public class HeaderEntryForm {

    @Size(max = 255)
    private String key;

    @Size(max = 2000)
    private String value;

    public HeaderEntryForm() {
    }

    public HeaderEntryForm(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
