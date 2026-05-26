package com.haohancom.docimagestripper.service;

import java.util.Arrays;

public class ExtractedImage {
    private final String filename;
    private final String contentType;
    private final byte[] bytes;

    public ExtractedImage(String filename, String contentType, byte[] bytes) {
        this.filename = filename;
        this.contentType = contentType;
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
