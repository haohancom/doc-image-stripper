package com.haohancom.docimagestripper.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.haohancom.docimagestripper.service.PdfImagePlaceholderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PdfController.class)
class PdfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PdfImagePlaceholderService service;

    @Test
    void uploadsPdfAndReturnsProcessedZipDownload() throws Exception {
        byte[] processedPdf = "%PDF-1.4\n%EOF".getBytes(StandardCharsets.US_ASCII);
        byte[] image = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
        given(service.replaceImages(any(byte[].class), any(String.class), any(String.class)))
                .willReturn(new PdfImagePlaceholderService.Result(
                processedPdf,
                java.util.Collections.singletonList(
                        new PdfImagePlaceholderService.ExtractedImage("image1.png", "image/png", image))));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "input pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/pdf/replace-images").file(file))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("sample-replaced.zip")))
                .andExpect(content().contentType("application/zip"))
                .andExpect(result -> {
                    Map<String, byte[]> entries = unzip(result.getResponse().getContentAsByteArray());
                    org.assertj.core.api.Assertions.assertThat(entries).containsOnlyKeys(
                            "sample-replaced.pdf", "image1.png");
                    org.assertj.core.api.Assertions.assertThat(entries.get("sample-replaced.pdf"))
                            .isEqualTo(processedPdf);
                    org.assertj.core.api.Assertions.assertThat(entries.get("image1.png")).isEqualTo(image);
                });
        verify(service).replaceImages(any(byte[].class), eq(""), eq(""));
    }

    @Test
    void passesCustomPlaceholderDelimitersToPdfService() throws Exception {
        byte[] processedPdf = "%PDF-1.4\n%EOF".getBytes(StandardCharsets.US_ASCII);
        given(service.replaceImages(any(byte[].class), any(String.class), any(String.class)))
                .willReturn(new PdfImagePlaceholderService.Result(
                        processedPdf,
                        java.util.Collections.emptyList()));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "input pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/pdf/replace-images")
                        .file(file)
                        .param("placeholderPrefix", "-")
                        .param("placeholderSuffix", "!"))
                .andExpect(status().isOk());

        verify(service).replaceImages(any(byte[].class), eq("-"), eq("!"));
    }

    @Test
    void allowsUploadsFromDoubleClickedStaticPage() throws Exception {
        byte[] processedPdf = "%PDF-1.4\n%EOF".getBytes(StandardCharsets.US_ASCII);
        given(service.replaceImages(any(byte[].class), any(String.class), any(String.class)))
                .willReturn(new PdfImagePlaceholderService.Result(
                processedPdf,
                java.util.Collections.emptyList()));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "input pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/pdf/replace-images")
                        .file(file)
                        .header(HttpHeaders.ORIGIN, "null"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
    }

    private Map<String, byte[]> unzip(byte[] zipBytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                entries.put(entry.getName(), org.springframework.util.StreamUtils.copyToByteArray(zip));
                entry = zip.getNextEntry();
            }
        }
        return entries;
    }
}
