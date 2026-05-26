# PDF ZIP Images Design

## Goal

When a user uploads a PDF, return one ZIP archive containing the processed PDF and every extracted image.

## Output Contract

The ZIP archive is downloaded as `<original-name>-replaced.zip`.

The archive root contains:

- `<original-name>-replaced.pdf`
- `image1.png`
- `image2.png`
- More images using the same sequence.

Image numbers must match the placeholders drawn into the processed PDF. If the processed PDF contains `[image1]`, the ZIP must contain `image1.png`.

## Architecture

`PdfImagePlaceholderService` will return a result object instead of only PDF bytes. The result contains the processed PDF and extracted PNG images in appearance order.

The controller remains responsible for HTTP concerns. It validates the upload, calls the service, builds a ZIP in memory, and responds with `application/zip`.

The static page continues to upload to the same endpoint and trigger the browser download. Its copy changes from PDF-specific wording to ZIP/archive wording.

## Image Extraction

Normal image XObjects are exported as PNG using PDFBox image APIs. Inline images are also exported as PNG. The implementation keeps the numbering source of truth in the same collector that creates placeholders, so PDF labels and ZIP filenames cannot drift.

Nested images inside Form XObjects are handled in the same appearance order already used for placeholder numbering.

## Error Handling

Existing validation and PDF processing error responses stay unchanged. Empty or non-PDF uploads still return `400`; malformed PDFs still return `422`.

## Testing

Service tests cover that:

- The processed PDF still has placeholders and no image draws.
- The result includes `image1.png`.
- Multiple images are exported in order.

Controller tests cover that:

- The endpoint responds with a ZIP attachment.
- The ZIP contains the processed PDF and image entries.

Static page tests continue to verify the page loads.
