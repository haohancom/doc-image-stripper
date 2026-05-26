# Word Document Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Accept PDF and recognized Microsoft Word uploads, fully replace images in modern Word Open XML files, and return the processed document plus extracted images in one ZIP archive.

**Architecture:** Keep the existing PDF processor intact but move controller output assembly onto a common document result model. Add a filename-extension classifier so the controller can dispatch PDF uploads to `PdfImagePlaceholderService`, modern Word uploads to a new XWPF-based `WordImagePlaceholderService`, and recognized legacy Word uploads to a clear `422` response. Update the static page to advertise and select PDF / Word files.

**Tech Stack:** Java 8, Spring Boot 2.7.18, PDFBox 2.0.30, Apache POI `poi-ooxml` 5.2.5, JUnit 5, AssertJ, MockMvc, `java.util.zip`.

---

## File Structure

- Create `src/main/java/com/haohancom/docimagestripper/service/ExtractedImage.java`
  - Common immutable image asset model used by PDF and Word processors.
- Create `src/main/java/com/haohancom/docimagestripper/service/DocumentProcessingResult.java`
  - Common immutable processed document result with processed bytes and extracted images.
- Modify `src/main/java/com/haohancom/docimagestripper/service/PdfImagePlaceholderService.java`
  - Return the common result type and common image model.
- Create `src/main/java/com/haohancom/docimagestripper/service/WordImagePlaceholderService.java`
  - Process modern Open XML Word files with Apache POI XWPF.
- Create `src/main/java/com/haohancom/docimagestripper/web/DocumentUploadType.java`
  - Classify uploads by filename extension.
- Modify `src/main/java/com/haohancom/docimagestripper/web/PdfController.java`
  - Keep the existing endpoint but dispatch PDF / Word based on `DocumentUploadType`.
- Modify `src/main/resources/static/index.html`
  - Accept and describe PDF / Word uploads.
- Modify `README.md`
  - Document Word support and recognized legacy Word behavior.
- Modify `pom.xml`
  - Add Apache POI dependency.
- Modify existing tests and add:
  - `src/test/java/com/haohancom/docimagestripper/service/DocumentProcessingResultTest.java`
  - `src/test/java/com/haohancom/docimagestripper/service/WordImagePlaceholderServiceTest.java`
  - `src/test/java/com/haohancom/docimagestripper/web/DocumentUploadTypeTest.java`

---

### Task 1: Common Result Model

**Files:**
- Create: `src/main/java/com/haohancom/docimagestripper/service/ExtractedImage.java`
- Create: `src/main/java/com/haohancom/docimagestripper/service/DocumentProcessingResult.java`
- Create: `src/test/java/com/haohancom/docimagestripper/service/DocumentProcessingResultTest.java`

- [ ] **Step 1: Write the failing immutability tests**

Create `src/test/java/com/haohancom/docimagestripper/service/DocumentProcessingResultTest.java`:

```java
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
```

- [ ] **Step 2: Run the new test to verify RED**

Run:

```bash
mvn -Dtest=DocumentProcessingResultTest test
```

Expected: compilation failure because `DocumentProcessingResult` and `ExtractedImage` do not exist.

- [ ] **Step 3: Implement the minimal common model**

Create `src/main/java/com/haohancom/docimagestripper/service/ExtractedImage.java`:

```java
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
```

Create `src/main/java/com/haohancom/docimagestripper/service/DocumentProcessingResult.java`:

```java
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
```

- [ ] **Step 4: Run the model test to verify GREEN**

Run:

```bash
mvn -Dtest=DocumentProcessingResultTest test
```

Expected: test passes.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/haohancom/docimagestripper/service/ExtractedImage.java \
  src/main/java/com/haohancom/docimagestripper/service/DocumentProcessingResult.java \
  src/test/java/com/haohancom/docimagestripper/service/DocumentProcessingResultTest.java
git commit -m "feat: add document processing result model"
```

---

### Task 2: Upload Type Classification

**Files:**
- Create: `src/main/java/com/haohancom/docimagestripper/web/DocumentUploadType.java`
- Create: `src/test/java/com/haohancom/docimagestripper/web/DocumentUploadTypeTest.java`

- [ ] **Step 1: Write the failing classifier tests**

Create `src/test/java/com/haohancom/docimagestripper/web/DocumentUploadTypeTest.java`:

```java
package com.haohancom.docimagestripper.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DocumentUploadTypeTest {

    @Test
    void classifiesPdfAndModernWordExtensionsCaseInsensitively() {
        assertThat(DocumentUploadType.fromFilename("sample.PDF")).isEqualTo(DocumentUploadType.PDF);
        assertThat(DocumentUploadType.fromFilename("sample.docx")).isEqualTo(DocumentUploadType.MODERN_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.docm")).isEqualTo(DocumentUploadType.MODERN_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.dotx")).isEqualTo(DocumentUploadType.MODERN_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.dotm")).isEqualTo(DocumentUploadType.MODERN_WORD);
    }

    @Test
    void recognizesLegacyWordExtensionsWithoutMarkingThemModern() {
        assertThat(DocumentUploadType.fromFilename("sample.doc")).isEqualTo(DocumentUploadType.LEGACY_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.dot")).isEqualTo(DocumentUploadType.LEGACY_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.wbk")).isEqualTo(DocumentUploadType.LEGACY_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.docb")).isEqualTo(DocumentUploadType.LEGACY_WORD);
    }

    @Test
    void rejectsMissingUnsupportedAndTemporaryFilenames() {
        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                DocumentUploadType.fromFilename(null);
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Please upload a PDF or Word file.");

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                DocumentUploadType.fromFilename("sample.txt");
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only PDF and Word files are supported.");

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                DocumentUploadType.fromFilename("~$sample.docx");
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Word temporary lock files cannot be processed.");
    }
}
```

- [ ] **Step 2: Run the classifier test to verify RED**

Run:

```bash
mvn -Dtest=DocumentUploadTypeTest test
```

Expected: compilation failure because `DocumentUploadType` does not exist.

- [ ] **Step 3: Implement the classifier**

Create `src/main/java/com/haohancom/docimagestripper/web/DocumentUploadType.java`:

```java
package com.haohancom.docimagestripper.web;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

enum DocumentUploadType {
    PDF,
    MODERN_WORD,
    LEGACY_WORD;

    private static final Set<String> MODERN_WORD_EXTENSIONS = new HashSet<String>(
            Arrays.asList(".docx", ".docm", ".dotx", ".dotm"));
    private static final Set<String> LEGACY_WORD_EXTENSIONS = new HashSet<String>(
            Arrays.asList(".doc", ".dot", ".wbk", ".docb"));

    static DocumentUploadType fromFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Please upload a PDF or Word file.");
        }
        String trimmed = filename.trim();
        String simpleName = simpleName(trimmed);
        if (simpleName.startsWith("~$")) {
            throw new IllegalArgumentException("Word temporary lock files cannot be processed.");
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return PDF;
        }
        if (hasExtension(lower, MODERN_WORD_EXTENSIONS)) {
            return MODERN_WORD;
        }
        if (hasExtension(lower, LEGACY_WORD_EXTENSIONS)) {
            return LEGACY_WORD;
        }
        throw new IllegalArgumentException("Only PDF and Word files are supported.");
    }

    private static boolean hasExtension(String filename, Set<String> extensions) {
        for (String extension : extensions) {
            if (filename.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static String simpleName(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return slash >= 0 ? filename.substring(slash + 1) : filename;
    }
}
```

- [ ] **Step 4: Run the classifier test to verify GREEN**

Run:

```bash
mvn -Dtest=DocumentUploadTypeTest test
```

Expected: test passes.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/haohancom/docimagestripper/web/DocumentUploadType.java \
  src/test/java/com/haohancom/docimagestripper/web/DocumentUploadTypeTest.java
git commit -m "feat: classify document upload types"
```

---

### Task 3: Migrate PDF Service To Common Result

**Files:**
- Modify: `src/main/java/com/haohancom/docimagestripper/service/PdfImagePlaceholderService.java`
- Modify: `src/test/java/com/haohancom/docimagestripper/service/PdfImagePlaceholderServiceTest.java`
- Modify: `src/main/java/com/haohancom/docimagestripper/web/PdfController.java`
- Modify: `src/test/java/com/haohancom/docimagestripper/web/PdfControllerTest.java`

- [ ] **Step 1: Update PDF service tests to expect common result**

In `PdfImagePlaceholderServiceTest`, change references from:

```java
PdfImagePlaceholderService.Result result = service.replaceImages(input);
result.getPdfBytes()
PdfImagePlaceholderService.ExtractedImage::getFilename
```

to:

```java
DocumentProcessingResult result = service.replaceImages(input);
result.getDocumentBytes()
ExtractedImage::getFilename
```

Keep every existing assertion the same otherwise.

- [ ] **Step 2: Run PDF service tests to verify RED**

Run:

```bash
mvn -Dtest=PdfImagePlaceholderServiceTest test
```

Expected: compilation failure because `PdfImagePlaceholderService.replaceImages` still returns its nested `Result`.

- [ ] **Step 3: Update PDF service return type**

In `PdfImagePlaceholderService.java`, replace the nested `Result` and nested `ExtractedImage` usage with the common classes:

```java
public DocumentProcessingResult replaceImages(byte[] input) throws IOException {
    return replaceImages(input, "", "");
}

public DocumentProcessingResult replaceImages(byte[] input, String placeholderPrefix, String placeholderSuffix)
        throws IOException {
    ...
    return new DocumentProcessingResult(output.toByteArray(), extractedImages);
}
```

Remove the nested `Result` and nested `ExtractedImage` classes from the bottom of the file. Keep `PagePlaceholders`, `Rectangle`, `Point`, and `ImagePlaceholder`.

- [ ] **Step 4: Run PDF service tests to verify GREEN**

Run:

```bash
mvn -Dtest=PdfImagePlaceholderServiceTest test
```

Expected: all PDF service tests pass.

- [ ] **Step 5: Update controller test mocks to common result**

In `PdfControllerTest`, replace mock return values like:

```java
new PdfImagePlaceholderService.Result(processedPdf,
        java.util.Collections.singletonList(
                new PdfImagePlaceholderService.ExtractedImage("image1.png", "image/png", image)))
```

with:

```java
new DocumentProcessingResult(processedPdf,
        java.util.Collections.singletonList(
                new ExtractedImage("image1.png", "image/png", image)))
```

Add imports for `DocumentProcessingResult` and `ExtractedImage`.

- [ ] **Step 6: Run controller tests to verify RED**

Run:

```bash
mvn -Dtest=PdfControllerTest test
```

Expected: compilation failure because `PdfController` still references `PdfImagePlaceholderService.Result`.

- [ ] **Step 7: Update controller PDF path to common result**

In `PdfController.java`, replace `PdfImagePlaceholderService.Result` references with `DocumentProcessingResult`, replace `result.getPdfBytes()` with `result.getDocumentBytes()`, and replace imports for nested `ExtractedImage` with the common `ExtractedImage`.

- [ ] **Step 8: Run controller tests to verify GREEN**

Run:

```bash
mvn -Dtest=PdfControllerTest test
```

Expected: controller tests pass.

- [ ] **Step 9: Commit**

Run:

```bash
git add src/main/java/com/haohancom/docimagestripper/service/PdfImagePlaceholderService.java \
  src/test/java/com/haohancom/docimagestripper/service/PdfImagePlaceholderServiceTest.java \
  src/main/java/com/haohancom/docimagestripper/web/PdfController.java \
  src/test/java/com/haohancom/docimagestripper/web/PdfControllerTest.java
git commit -m "refactor: share document processing result"
```

---

### Task 4: Word Service For Modern Open XML Files

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/haohancom/docimagestripper/service/WordImagePlaceholderService.java`
- Create: `src/test/java/com/haohancom/docimagestripper/service/WordImagePlaceholderServiceTest.java`

- [ ] **Step 1: Add Apache POI dependency**

In `pom.xml`, add:

```xml
<poi.version>5.2.5</poi.version>
```

under `<properties>`, and add:

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>${poi.version}</version>
</dependency>
```

under `<dependencies>`.

- [ ] **Step 2: Write the first failing Word service test**

Create `src/test/java/com/haohancom/docimagestripper/service/WordImagePlaceholderServiceTest.java`:

```java
package com.haohancom.docimagestripper.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class WordImagePlaceholderServiceTest {

    private final WordImagePlaceholderService service = new WordImagePlaceholderService();

    @Test
    void replacesDocumentBodyImageWithPlaceholderAndExtractsImage() throws Exception {
        byte[] input = createDocxWithBodyImage();

        DocumentProcessingResult result = service.replaceImages(input, "", "");

        assertThat(result.getExtractedImages()).hasSize(1);
        assertThat(result.getExtractedImages().get(0).getFilename()).isEqualTo("image1.png");
        assertThat(result.getExtractedImages().get(0).getContentType()).isEqualTo("image/png");
        assertThat(result.getExtractedImages().get(0).getBytes()).startsWith(
                (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result.getDocumentBytes()))) {
            assertThat(document.getParagraphArray(0).getText()).contains("Before image");
            assertThat(document.getParagraphArray(1).getText()).contains("image1");
            assertThat(document.getAllPictures()).isEmpty();
        }
    }

    @Test
    void wrapsWordPlaceholdersWithCustomPrefixAndSuffix() throws Exception {
        byte[] input = createDocxWithBodyImage();

        DocumentProcessingResult result = service.replaceImages(input, "-", "!");

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result.getDocumentBytes()))) {
            assertThat(document.getParagraphArray(1).getText()).contains("-image1!");
        }
    }

    private byte[] createDocxWithBodyImage() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph textParagraph = document.createParagraph();
            textParagraph.createRun().setText("Before image");

            XWPFParagraph imageParagraph = document.createParagraph();
            XWPFRun run = imageParagraph.createRun();
            run.addPicture(new ByteArrayInputStream(pngBytes(Color.RED)), Document.PICTURE_TYPE_PNG,
                    "sample.png", Units.toEMU(80), Units.toEMU(50));

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] pngBytes(Color color) throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
```

- [ ] **Step 3: Run the Word service test to verify RED**

Run:

```bash
mvn -Dtest=WordImagePlaceholderServiceTest test
```

Expected: compilation failure because `WordImagePlaceholderService` does not exist.

- [ ] **Step 4: Implement minimal body paragraph image replacement**

Create `src/main/java/com/haohancom/docimagestripper/service/WordImagePlaceholderService.java`:

```java
package com.haohancom.docimagestripper.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.springframework.stereotype.Service;

@Service
public class WordImagePlaceholderService {

    public DocumentProcessingResult replaceImages(byte[] input, String placeholderPrefix, String placeholderSuffix)
            throws IOException {
        if (input == null || input.length == 0) {
            throw new IllegalArgumentException("Word file must not be empty.");
        }

        List<ExtractedImage> extractedImages = new ArrayList<ExtractedImage>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(input))) {
            int nextImageNumber = replaceParagraphImages(document.getParagraphs(), extractedImages, 1,
                    placeholderPart(placeholderPrefix), placeholderPart(placeholderSuffix));

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            return new DocumentProcessingResult(output.toByteArray(), extractedImages);
        }
    }

    private int replaceParagraphImages(List<XWPFParagraph> paragraphs, List<ExtractedImage> extractedImages,
            int nextImageNumber, String prefix, String suffix) {
        for (XWPFParagraph paragraph : paragraphs) {
            for (XWPFRun run : paragraph.getRuns()) {
                List<XWPFPicture> pictures = run.getEmbeddedPictures();
                if (pictures.isEmpty()) {
                    continue;
                }

                StringBuilder placeholders = new StringBuilder();
                for (XWPFPicture picture : pictures) {
                    XWPFPictureData data = picture.getPictureData();
                    if (data == null) {
                        continue;
                    }
                    String extension = imageExtension(data);
                    extractedImages.add(new ExtractedImage("image" + nextImageNumber + "." + extension,
                            contentType(extension), data.getData()));
                    if (placeholders.length() > 0) {
                        placeholders.append(" ");
                    }
                    placeholders.append(prefix).append("image").append(nextImageNumber).append(suffix);
                    nextImageNumber++;
                }

                removePictureXml(run);
                if (placeholders.length() > 0) {
                    run.setText(placeholders.toString());
                }
            }
        }
        return nextImageNumber;
    }

    private String placeholderPart(String value) {
        return value == null ? "" : value;
    }

    private void removePictureXml(XWPFRun run) {
        CTR ctr = run.getCTR();
        for (int i = ctr.sizeOfDrawingArray() - 1; i >= 0; i--) {
            ctr.removeDrawing(i);
        }
        for (int i = ctr.sizeOfPictArray() - 1; i >= 0; i--) {
            ctr.removePict(i);
        }
    }

    private String imageExtension(XWPFPictureData data) {
        String extension = data.suggestFileExtension();
        return extension == null || extension.trim().isEmpty() ? "bin" : extension;
    }

    private String contentType(String extension) {
        if ("png".equalsIgnoreCase(extension)) {
            return "image/png";
        }
        if ("jpg".equalsIgnoreCase(extension) || "jpeg".equalsIgnoreCase(extension)) {
            return "image/jpeg";
        }
        if ("gif".equalsIgnoreCase(extension)) {
            return "image/gif";
        }
        if ("bmp".equalsIgnoreCase(extension)) {
            return "image/bmp";
        }
        if ("tif".equalsIgnoreCase(extension) || "tiff".equalsIgnoreCase(extension)) {
            return "image/tiff";
        }
        return "application/octet-stream";
    }
}
```

- [ ] **Step 5: Run the Word service test to verify GREEN**

Run:

```bash
mvn -Dtest=WordImagePlaceholderServiceTest test
```

Expected: the first two Word service tests pass.

- [ ] **Step 6: Add failing tests for tables, headers, and footers**

Append to `WordImagePlaceholderServiceTest`:

```java
@Test
void replacesImagesInTablesHeadersAndFooters() throws Exception {
    byte[] input = createDocxWithTableHeaderAndFooterImages();

    DocumentProcessingResult result = service.replaceImages(input, "", "");

    assertThat(result.getExtractedImages()).extracting(ExtractedImage::getFilename)
            .containsExactly("image1.png", "image2.png", "image3.png");

    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result.getDocumentBytes()))) {
        assertThat(document.getTables().get(0).getRow(0).getCell(0).getText()).contains("image1");
        assertThat(document.getHeaderArray(0).getText()).contains("image2");
        assertThat(document.getFooterArray(0).getText()).contains("image3");
        assertThat(document.getAllPictures()).isEmpty();
    }
}

private byte[] createDocxWithTableHeaderAndFooterImages() throws Exception {
    try (XWPFDocument document = new XWPFDocument()) {
        XWPFTable table = document.createTable(1, 1);
        addImageRun(table.getRow(0).getCell(0).getParagraphArray(0).createRun(), Color.BLUE);

        org.apache.poi.xwpf.usermodel.XWPFHeader header = document.createHeader(
                org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        addImageRun(header.createParagraph().createRun(), Color.GREEN);

        org.apache.poi.xwpf.usermodel.XWPFFooter footer = document.createFooter(
                org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
        addImageRun(footer.createParagraph().createRun(), Color.YELLOW);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.write(output);
        return output.toByteArray();
    }
}

private void addImageRun(XWPFRun run, Color color) throws Exception {
    run.addPicture(new ByteArrayInputStream(pngBytes(color)), Document.PICTURE_TYPE_PNG,
            "sample.png", Units.toEMU(80), Units.toEMU(50));
}
```

- [ ] **Step 7: Run the expanded Word service test to verify RED**

Run:

```bash
mvn -Dtest=WordImagePlaceholderServiceTest test
```

Expected: `replacesImagesInTablesHeadersAndFooters` fails because only body paragraphs are walked.

- [ ] **Step 8: Implement table, header, and footer traversal**

In `WordImagePlaceholderService`, add imports:

```java
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
```

Replace the direct body paragraph call with:

```java
int nextImageNumber = replaceBodyElements(document.getBodyElements(), extractedImages, 1,
        placeholderPart(placeholderPrefix), placeholderPart(placeholderSuffix));
for (XWPFHeader header : document.getHeaderList()) {
    nextImageNumber = replaceBodyElements(header.getBodyElements(), extractedImages, nextImageNumber,
            placeholderPart(placeholderPrefix), placeholderPart(placeholderSuffix));
}
for (XWPFFooter footer : document.getFooterList()) {
    nextImageNumber = replaceBodyElements(footer.getBodyElements(), extractedImages, nextImageNumber,
            placeholderPart(placeholderPrefix), placeholderPart(placeholderSuffix));
}
```

Add helper methods:

```java
private int replaceBodyElements(List<IBodyElement> bodyElements, List<ExtractedImage> extractedImages,
        int nextImageNumber, String prefix, String suffix) {
    for (IBodyElement bodyElement : bodyElements) {
        if (bodyElement instanceof XWPFParagraph) {
            nextImageNumber = replaceParagraphImages(
                    java.util.Collections.singletonList((XWPFParagraph) bodyElement),
                    extractedImages, nextImageNumber, prefix, suffix);
        } else if (bodyElement instanceof XWPFTable) {
            nextImageNumber = replaceTableImages((XWPFTable) bodyElement, extractedImages,
                    nextImageNumber, prefix, suffix);
        }
    }
    return nextImageNumber;
}

private int replaceTableImages(XWPFTable table, List<ExtractedImage> extractedImages,
        int nextImageNumber, String prefix, String suffix) {
    for (XWPFTableRow row : table.getRows()) {
        for (XWPFTableCell cell : row.getTableCells()) {
            nextImageNumber = replaceBodyElements(cell.getBodyElements(), extractedImages,
                    nextImageNumber, prefix, suffix);
        }
    }
    return nextImageNumber;
}
```

- [ ] **Step 9: Run the Word service tests to verify GREEN**

Run:

```bash
mvn -Dtest=WordImagePlaceholderServiceTest test
```

Expected: all Word service tests pass.

- [ ] **Step 10: Commit**

Run:

```bash
git add pom.xml \
  src/main/java/com/haohancom/docimagestripper/service/WordImagePlaceholderService.java \
  src/test/java/com/haohancom/docimagestripper/service/WordImagePlaceholderServiceTest.java
git commit -m "feat: replace images in modern word documents"
```

---

### Task 5: Controller Dispatch And ZIP Naming

**Files:**
- Modify: `src/main/java/com/haohancom/docimagestripper/web/PdfController.java`
- Modify: `src/test/java/com/haohancom/docimagestripper/web/PdfControllerTest.java`

- [ ] **Step 1: Update controller tests for Word dispatch and legacy handling**

In `PdfControllerTest`, add a mock bean:

```java
@MockBean
private WordImagePlaceholderService wordService;
```

Add imports:

```java
import com.haohancom.docimagestripper.service.DocumentProcessingResult;
import com.haohancom.docimagestripper.service.ExtractedImage;
import com.haohancom.docimagestripper.service.WordImagePlaceholderService;
```

Add tests:

```java
@Test
void uploadsModernWordAndReturnsProcessedZipDownload() throws Exception {
    byte[] processedDocx = new byte[] {'P', 'K', 3, 4};
    byte[] image = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
    given(wordService.replaceImages(any(byte[].class), any(String.class), any(String.class)))
            .willReturn(new DocumentProcessingResult(
                    processedDocx,
                    java.util.Collections.singletonList(new ExtractedImage("image1.png", "image/png", image))));
    MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "input docx".getBytes(StandardCharsets.UTF_8));

    mockMvc.perform(multipart("/api/pdf/replace-images").file(file))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("sample-replaced.zip")))
            .andExpect(content().contentType("application/zip"))
            .andExpect(result -> {
                Map<String, byte[]> entries = unzip(result.getResponse().getContentAsByteArray());
                org.assertj.core.api.Assertions.assertThat(entries).containsOnlyKeys(
                        "sample-replaced.docx", "image1.png");
                org.assertj.core.api.Assertions.assertThat(entries.get("sample-replaced.docx"))
                        .isEqualTo(processedDocx);
                org.assertj.core.api.Assertions.assertThat(entries.get("image1.png")).isEqualTo(image);
            });
    verify(wordService).replaceImages(any(byte[].class), eq(""), eq(""));
}

@Test
void rejectsRecognizedLegacyWordWithSaveAsDocxGuidance() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.doc",
            "application/msword",
            "input doc".getBytes(StandardCharsets.UTF_8));

    mockMvc.perform(multipart("/api/pdf/replace-images").file(file))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().string(containsString("Please save this Word file as .docx and upload it again.")));
}

@Test
void rejectsWordTemporaryLockFiles() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
            "file",
            "~$sample.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "temp".getBytes(StandardCharsets.UTF_8));

    mockMvc.perform(multipart("/api/pdf/replace-images").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Word temporary lock files cannot be processed."));
}

@Test
void rejectsUnsupportedDocuments() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "text".getBytes(StandardCharsets.UTF_8));

    mockMvc.perform(multipart("/api/pdf/replace-images").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Only PDF and Word files are supported."));
}
```

- [ ] **Step 2: Run controller tests to verify RED**

Run:

```bash
mvn -Dtest=PdfControllerTest test
```

Expected: compilation failure because the controller constructor does not accept `WordImagePlaceholderService`, or assertions fail because validation still only accepts PDF.

- [ ] **Step 3: Implement controller dispatch**

In `PdfController.java`, add a `WordImagePlaceholderService` constructor dependency and use `DocumentUploadType.fromFilename(file.getOriginalFilename())`.

Replace `validatePdf(file)` with:

```java
private DocumentUploadType validateDocument(MultipartFile file) {
    if (file == null || file.isEmpty()) {
        throw new IllegalArgumentException("Please upload a PDF or Word file.");
    }
    return DocumentUploadType.fromFilename(file.getOriginalFilename());
}
```

In `replaceImages`, dispatch:

```java
DocumentUploadType uploadType = validateDocument(file);
DocumentProcessingResult result;
if (uploadType == DocumentUploadType.PDF) {
    result = service.replaceImages(file.getBytes(), placeholderPart(placeholderPrefix),
            placeholderPart(placeholderSuffix));
} else if (uploadType == DocumentUploadType.MODERN_WORD) {
    result = wordService.replaceImages(file.getBytes(), placeholderPart(placeholderPrefix),
            placeholderPart(placeholderSuffix));
} else {
    throw new UnsupportedWordFormatException("Please save this Word file as .docx and upload it again.");
}
String documentFileName = documentOutputFileName(file.getOriginalFilename());
byte[] zipBytes = toZip(result, documentFileName);
```

Add a nested exception and handler:

```java
@ExceptionHandler(UnsupportedWordFormatException.class)
public ResponseEntity<String> unsupportedWordFormat(UnsupportedWordFormatException exception) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .contentType(MediaType.TEXT_PLAIN)
            .body(exception.getMessage());
}

private static class UnsupportedWordFormatException extends RuntimeException {
    UnsupportedWordFormatException(String message) {
        super(message);
    }
}
```

Replace PDF-only filename helper with extension-preserving helpers:

```java
private String documentOutputFileName(String originalFilename) {
    String filename = normalizedFilename(originalFilename);
    int extensionIndex = filename.lastIndexOf('.');
    if (extensionIndex > 0) {
        return filename.substring(0, extensionIndex) + "-replaced" + filename.substring(extensionIndex);
    }
    return filename + "-replaced";
}

private String zipOutputFileName(String originalFilename) {
    String filename = normalizedFilename(originalFilename);
    int extensionIndex = filename.lastIndexOf('.');
    if (extensionIndex > 0) {
        return filename.substring(0, extensionIndex) + "-replaced.zip";
    }
    return filename + "-replaced.zip";
}

private String normalizedFilename(String originalFilename) {
    return originalFilename == null || originalFilename.trim().isEmpty()
            ? "converted"
            : originalFilename.trim();
}
```

- [ ] **Step 4: Run controller tests to verify GREEN**

Run:

```bash
mvn -Dtest=PdfControllerTest test
```

Expected: all controller tests pass.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/haohancom/docimagestripper/web/PdfController.java \
  src/test/java/com/haohancom/docimagestripper/web/PdfControllerTest.java
git commit -m "feat: dispatch uploads by document type"
```

---

### Task 6: Frontend And Documentation

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/test/java/com/haohancom/docimagestripper/web/StaticPageTest.java`
- Modify: `README.md`

- [ ] **Step 1: Update static page test expectations**

In `StaticPageTest`, update assertions to expect:

```java
assertThat(html).contains("PDF / Word 上传");
assertThat(html).contains("PDF / Word");
assertThat(html).contains("accept=\".pdf,.doc,.dot,.wbk,.docx,.docm,.dotx,.dotm,.docb\"");
assertThat(html).contains("const SUPPORTED_EXTENSIONS = ['.pdf', '.doc', '.dot', '.wbk', '.docx', '.docm', '.dotx', '.dotm', '.docb']");
assertThat(html).contains("firstSupportedFile");
assertThat(html).contains("fetch(API_BASE + '/api/pdf/replace-images'");
```

Remove PDF-only assertions such as `选择 PDF` or `firstPdfOrFirstFile`.

- [ ] **Step 2: Run static page test to verify RED**

Run:

```bash
mvn -Dtest=StaticPageTest test
```

Expected: assertion failure because the page is still PDF-only.

- [ ] **Step 3: Update static page copy and file filtering**

In `src/main/resources/static/index.html`, change:

```html
<section class="uploader" aria-label="PDF 上传">
...
<div class="file-mark">PDF</div>
<h1>把 PDF 丢进来</h1>
<p class="hint">图片会变成 image1、image2，并随 PDF 一起打包下载。</p>
...
<button class="primary" type="button" id="pickButton">选择 PDF</button>
...
<input id="fileInput" type="file" accept="application/pdf,.pdf">
```

to:

```html
<section class="uploader" aria-label="PDF / Word 上传">
...
<div class="file-mark">DOC</div>
<h1>把 PDF / Word 丢进来</h1>
<p class="hint">图片会变成 image1、image2，并随处理后的文档一起打包下载。</p>
...
<button class="primary" type="button" id="pickButton">选择文档</button>
...
<input id="fileInput" type="file" accept=".pdf,.doc,.dot,.wbk,.docx,.docm,.dotx,.dotm,.docb">
```

Add the supported extension list:

```javascript
const SUPPORTED_EXTENSIONS = ['.pdf', '.doc', '.dot', '.wbk', '.docx', '.docm', '.dotx', '.dotm', '.docb'];
```

Replace the upload extension guard with:

```javascript
const isSupportedFile = (file) => file && SUPPORTED_EXTENSIONS
        .some((extension) => file.name.toLowerCase().endsWith(extension));

if (!isSupportedFile(file)) {
    setMessage('只支持 PDF / Word 文件', true);
    return;
}
```

Replace `firstPdfOrFirstFile` with:

```javascript
const firstSupportedFile = (files) => Array.from(files || [])
        .find(isSupportedFile) || (files && files[0]);
```

Update the drop handler:

```javascript
upload(firstSupportedFile(event.dataTransfer.files));
```

Update download fallback:

```javascript
const extensionIndex = fallbackName.lastIndexOf('.');
return (extensionIndex > 0 ? fallbackName.substring(0, extensionIndex) : fallbackName) + '-replaced.zip';
```

- [ ] **Step 4: Run static page test to verify GREEN**

Run:

```bash
mvn -Dtest=StaticPageTest test
```

Expected: static page test passes.

- [ ] **Step 5: Update README**

Change current status bullets to say:

```markdown
- PDF upload and processing is implemented.
- Modern Word formats `.docx`, `.docm`, `.dotx`, and `.dotm` are processed with the same image placeholder flow.
- Legacy Word formats `.doc`, `.dot`, `.wbk`, and `.docb` are recognized and return guidance to save as `.docx`.
- Processed downloads are ZIP archives containing the replaced document and extracted images named `image1.png`, `image2.png`, and so on.
```

- [ ] **Step 6: Commit**

Run:

```bash
git add src/main/resources/static/index.html \
  src/test/java/com/haohancom/docimagestripper/web/StaticPageTest.java \
  README.md
git commit -m "docs: describe pdf and word uploads"
```

---

### Task 7: Full Verification

**Files:**
- Verify all changed files.

- [ ] **Step 1: Run full tests**

Run:

```bash
mvn test
```

Expected: all tests pass.

- [ ] **Step 2: Inspect status**

Run:

```bash
git status --short
```

Expected: no uncommitted files.

- [ ] **Step 3: Manual smoke check the UI if frontend changed**

Run:

```bash
./start.sh
```

Expected: the server starts on `http://localhost:8080/`.

Open the page and verify:

- The upload UI says PDF / Word.
- The file picker accepts PDF and Word extensions.
- Uploading a generated `.docx` from the tests returns a ZIP containing `<name>-replaced.docx` and `image1.png`.

Stop the server after the smoke check.
