# PDF ZIP Images Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Return a ZIP archive containing the processed PDF plus extracted PNG images named `image1.png`, `image2.png`, and so on.

**Architecture:** `PdfImagePlaceholderService` returns a result object with PDF bytes and image assets. `PdfController` builds the ZIP and returns `application/zip`. The static page keeps the same upload flow but updates download fallback and user-facing copy to say ZIP/archive.

**Tech Stack:** Java 8, Spring Boot 2.7, PDFBox 2.0.30, JUnit 5, AssertJ, MockMvc, `java.util.zip`.

---

### Task 1: Service Result And Image Export

**Files:**
- Modify: `src/main/java/com/haohancom/docimagestripper/service/PdfImagePlaceholderService.java`
- Modify: `src/test/java/com/haohancom/docimagestripper/service/PdfImagePlaceholderServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Add tests that call `service.replaceImages(input)` and assert the returned result has processed PDF bytes plus PNG image entries named `image1.png`, then `image2.png` for multiple images.

- [ ] **Step 2: Run service tests to verify failure**

Run: `mvn -Dtest=PdfImagePlaceholderServiceTest test`

Expected: compilation or assertion failure because `replaceImages` still returns `byte[]`.

- [ ] **Step 3: Implement minimal service result**

Add nested result and image classes, collect PNG bytes when images are encountered, and return the result object. Keep placeholder numbering and image naming driven by the same counter.

- [ ] **Step 4: Run service tests to verify pass**

Run: `mvn -Dtest=PdfImagePlaceholderServiceTest test`

Expected: all service tests pass.

### Task 2: ZIP Controller Response

**Files:**
- Modify: `src/main/java/com/haohancom/docimagestripper/web/PdfController.java`
- Modify: `src/test/java/com/haohancom/docimagestripper/web/PdfControllerTest.java`

- [ ] **Step 1: Write failing controller test**

Update the MockMvc test to expect `application/zip`, `sample-replaced.zip`, and ZIP entries for `sample-replaced.pdf` and `image1.png`.

- [ ] **Step 2: Run controller tests to verify failure**

Run: `mvn -Dtest=PdfControllerTest test`

Expected: failure because the controller still returns `application/pdf`.

- [ ] **Step 3: Implement ZIP response**

Build ZIP bytes with `ZipOutputStream`, add the processed PDF entry first, then all image entries. Change content type and filename helper to return `.zip`.

- [ ] **Step 4: Run controller tests to verify pass**

Run: `mvn -Dtest=PdfControllerTest test`

Expected: all controller tests pass.

### Task 3: Frontend Copy And Full Verification

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `README.md`

- [ ] **Step 1: Update static page wording**

Change fallback download name to `<original>-replaced.zip`, update completion message to mention the downloaded archive, and update hint copy to mention extracted images.

- [ ] **Step 2: Update README**

Document that the app now returns a ZIP containing the processed PDF and extracted PNG images.

- [ ] **Step 3: Run full test suite**

Run: `mvn test`

Expected: all tests pass.
