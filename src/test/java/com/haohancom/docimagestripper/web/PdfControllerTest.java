package com.haohancom.docimagestripper.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

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
    void uploadsPdfAndReturnsProcessedPdfDownload() throws Exception {
        byte[] processedPdf = "%PDF-1.4\n%EOF".getBytes(StandardCharsets.US_ASCII);
        given(service.replaceImages(any(byte[].class))).willReturn(processedPdf);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "input pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/pdf/replace-images").file(file))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("sample-replaced.pdf")))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(processedPdf));
    }

    @Test
    void allowsUploadsFromDoubleClickedStaticPage() throws Exception {
        byte[] processedPdf = "%PDF-1.4\n%EOF".getBytes(StandardCharsets.US_ASCII);
        given(service.replaceImages(any(byte[].class))).willReturn(processedPdf);
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
}
