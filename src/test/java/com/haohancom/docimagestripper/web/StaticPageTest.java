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
        assertThat(html).contains("PDF / Word 上传");
        assertThat(html).contains("PDF / Word");
        assertThat(html).contains("class=\"file-hero\"");
        assertThat(html).contains("class=\"file-card pdf\"");
        assertThat(html).contains("class=\"file-card doc\"");
        assertThat(html).contains("transform: rotate(-30deg)");
        assertThat(html).contains("transform: rotate(30deg)");
        assertThat(html).doesNotContain("class=\"file-mark\"");
        assertThat(html).contains("accept=\".pdf,.doc,.dot,.wbk,.docx,.docm,.dotx,.dotm,.docb\"");
        assertThat(html).contains("const SUPPORTED_EXTENSIONS = ['.pdf', '.doc', '.dot', '.wbk', '.docx', '.docm', '.dotx', '.dotm', '.docb']");
        assertThat(html).contains("firstSupportedFile");
        assertThat(html).contains("window.location.protocol === 'file:' ? 'http://localhost:8080' : ''");
        assertThat(html).contains("id=\"placeholderPrefix\"");
        assertThat(html).contains("id=\"placeholderSuffix\"");
        assertThat(html).contains("placeholder=\"例如 [\"");
        assertThat(html).contains("placeholder=\"例如 ]\"");
        assertThat(html).contains("class=\"placeholder-preview\"");
        assertThat(html).contains("class=\"preview-prefix\"");
        assertThat(html).contains("class=\"preview-image\">image</span>");
        assertThat(html).contains("class=\"preview-suffix\"");
        assertThat(html).contains("previewPrefix.textContent = placeholderPrefix.value");
        assertThat(html).contains("previewSuffix.textContent = placeholderSuffix.value");
        assertThat(html).contains("placeholderPrefix.addEventListener('input', updatePlaceholderPreview)");
        assertThat(html).contains("placeholderSuffix.addEventListener('input', updatePlaceholderPreview)");
        assertThat(html).contains("formData.append('placeholderPrefix'");
        assertThat(html).contains("formData.append('placeholderSuffix'");
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
