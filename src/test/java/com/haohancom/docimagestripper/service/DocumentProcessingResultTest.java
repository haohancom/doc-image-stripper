package com.haohancom.docimagestripper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class DocumentProcessingResultTest {

    @Test
    void copiesDocumentBytesAndImagesDefensively() {
        byte[] documentBytes = new byte[] {1, 2, 3};
        byte[] imageBytes = new byte[] {4, 5, 6};
        List<ExtractedImage> images = new ArrayList<ExtractedImage>();
        images.add(new ExtractedImage("image1.png", "image/png", imageBytes));

        DocumentProcessingResult result = new DocumentProcessingResult(documentBytes, images);

        documentBytes[0] = 9;
        imageBytes[0] = 9;
        images.clear();

        assertThat(result.getDocumentBytes()).containsExactly(1, 2, 3);
        assertThat(result.getExtractedImages()).hasSize(1);
        assertThat(result.getExtractedImages().get(0).getBytes()).containsExactly(4, 5, 6);
    }

    @Test
    void exposesImagesAsUnmodifiableList() {
        DocumentProcessingResult result = new DocumentProcessingResult(
                new byte[] {1},
                Collections.singletonList(new ExtractedImage("image1.png", "image/png", new byte[] {2})));

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                result.getExtractedImages().clear();
            }
        }).isInstanceOf(UnsupportedOperationException.class);
    }
}
