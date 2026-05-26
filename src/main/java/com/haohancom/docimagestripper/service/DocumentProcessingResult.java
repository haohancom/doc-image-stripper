package com.haohancom.docimagestripper.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DocumentProcessingResult {
    private final byte[] documentBytes;
    private final List<ExtractedImage> extractedImages;

    public DocumentProcessingResult(byte[] documentBytes, List<ExtractedImage> extractedImages) {
        this.documentBytes = Arrays.copyOf(documentBytes, documentBytes.length);
        this.extractedImages = Collections.unmodifiableList(new ArrayList<ExtractedImage>(extractedImages));
    }

    public byte[] getDocumentBytes() {
        return Arrays.copyOf(documentBytes, documentBytes.length);
    }

    public List<ExtractedImage> getExtractedImages() {
        return extractedImages;
    }
}
