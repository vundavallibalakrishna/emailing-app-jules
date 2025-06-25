package com.wisestep.emailing.domain;

import java.util.Objects;

public class EmailAttachment {

    private String filename;
    private String contentType;
    private byte[] data;

    public EmailAttachment(String filename, String contentType, byte[] data) {
        this.filename = filename;
        this.contentType = contentType;
        this.data = data;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmailAttachment that = (EmailAttachment) o;
        return Objects.equals(filename, that.filename) &&
                Objects.equals(contentType, that.contentType) &&
                java.util.Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(filename, contentType);
        result = 31 * result + java.util.Arrays.hashCode(data);
        return result;
    }
}
