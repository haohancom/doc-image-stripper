# Word Document Support Design

## Goal

Doc Image Stripper should accept PDF and Microsoft Word uploads from the same web page, replace embedded images with numbered text placeholders, and return a ZIP archive containing the processed document plus extracted image files.

## Supported Inputs

PDF support remains unchanged for `.pdf` files.

Word file recognition covers Microsoft Word upload extensions listed by Microsoft Forms: `.doc`, `.dot`, `.wbk`, `.docx`, `.docm`, `.dotx`, `.dotm`, and `.docb`.

Full Word image replacement is implemented for the modern Open XML Word formats:

- `.docx`
- `.docm`
- `.dotx`
- `.dotm`

Legacy or unsupported Word formats are recognized but not rewritten in this phase:

- `.doc`
- `.dot`
- `.wbk`
- `.docb`

Word temporary lock files whose names start with `~$` are rejected before processing.

## Output Contract

For processed PDF uploads, the current ZIP behavior stays the same:

- ZIP filename: `<original-name>-replaced.zip`
- Processed document entry: `<original-name>-replaced.pdf`
- Extracted image entries: `image1.png`, `image2.png`, and so on

For processed modern Word uploads:

- ZIP filename: `<original-name>-replaced.zip`
- Processed document entry keeps the input extension, for example `<original-name>-replaced.docx`
- Extracted image entries are named `image1.<ext>`, `image2.<ext>`, and so on when the original embedded image type is known
- Placeholder text in the document uses the same numbering source as the extracted image filenames

Custom `placeholderPrefix` and `placeholderSuffix` continue to wrap the placeholder label for both PDF and Word documents.

## Architecture

The controller becomes a document upload controller in behavior while keeping the existing endpoint compatible. It validates the upload, classifies the file type by original filename extension, dispatches to the correct processor, and builds the ZIP response.

Existing PDF processing remains in `PdfImagePlaceholderService`.

A new Word service handles modern Word files using Apache POI XWPF. It returns a common result shape containing:

- Processed document bytes
- Processed document filename
- Extracted image assets

The ZIP-building code works from this common result shape so PDF and Word responses share one HTTP output path.

## Word Processing

The Word service opens modern Open XML Word files with XWPF. It walks paragraphs and tables in the document body, headers, and footers. When it finds image-bearing runs, it extracts the image bytes, assigns the next `imageN` number, removes the drawing or picture node from the run, and inserts the placeholder text in the same run location.

The service keeps the original document package format when saving. Macro-enabled and template extensions are recognized by extension and returned using the same extension. The processor does not execute macros.

## Unsupported Word Formats

Legacy Word formats are recognized as Word uploads, but return `422 Unprocessable Entity` with a clear message asking the user to save the file as `.docx` and upload it again. This avoids silently corrupting older binary Word files.

## Frontend

The upload page changes visible copy from PDF-only wording to PDF / Word wording. The file input accepts `.pdf,.doc,.dot,.wbk,.docx,.docm,.dotx,.dotm,.docb`, and drag/drop selection chooses the first supported PDF or Word file instead of only the first PDF.

## Error Handling

Empty uploads still return `400`.

Unsupported extensions return `400` with a message that only PDF and Word files are supported.

Word temporary lock files return `400`.

Malformed PDF or modern Word processing failures return `422`.

Recognized legacy Word formats return `422` with the save-as-`.docx` guidance.

## Testing

Service tests cover modern Word image replacement, image extraction, placeholder numbering, custom placeholder wrappers, tables, headers, and footers where practical.

Controller tests cover:

- Modern Word uploads returning a ZIP with a processed Word entry and image entries
- All recognized Word extensions passing classification
- Legacy Word extensions returning the expected `422` guidance
- Temporary lock files returning `400`
- Existing PDF behavior staying unchanged

Static page tests cover updated accept filters, copy, drag/drop supported-file selection, and the unchanged upload flow.

## References

- Microsoft Forms lists Word upload file types as `.doc`, `.dot`, `.wbk`, `.docx`, `.docm`, `.dotx`, `.dotm`, and `.docb`.
- Microsoft Open XML documentation identifies `.docx`, `.docm`, `.dotx`, and `.dotm` as default XML Word extensions.
- Apache POI documents XWPF for modern `.docx` Word files and notes that HWPF for legacy `.doc` files has more limited support.
