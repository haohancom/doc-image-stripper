package com.haohancom.docimagestripper.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class StaticPageTest {

    @Test
    void uploadPageHasRobustDragAndDropHandling() throws Exception {
        String html = readIndexHtml();

        assertThat(html).contains("document.addEventListener('dragover', preventWindowFileDrop)");
        assertThat(html).contains("document.addEventListener('drop', preventWindowFileDrop)");
        assertThat(html).contains("event.dataTransfer.dropEffect = 'copy'");
        assertThat(html).contains("dropZone.classList.toggle('is-processing', busy)");
        assertThat(html).contains("处理完成，已下载");
        assertThat(html).contains("window.location.protocol === 'file:' ? 'http://localhost:8080' : ''");
        assertThat(html).contains("fetch(API_BASE + '/api/pdf/replace-images'");
    }

    private String readIndexHtml() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/static/index.html")) {
            assertThat(input).isNotNull();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
