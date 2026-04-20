package com.myhive.backend.service.activity;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.ActivityImportApplyRequest;
import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.dto.ActivityImportResultDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.CsvImportException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityCsvImporterTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ActivityCsvImporter importer;

    private String header() {
        return "id,slug,destination_slug,name,description,price,duration,category_slugs,image_url,includes\n";
    }

    @Test
    void preview_emptyBytes_returnsEmptyFileError() {
        ActivityImportPreviewDTO preview = importer.preview(new byte[0]);

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .containsExactly(ImportErrorCode.EMPTY_FILE);
    }

    @Test
    void preview_fileTooLarge_returnsFileTooLargeError() {
        byte[] big = new byte[(5 * 1024 * 1024) + 1];

        ActivityImportPreviewDTO preview = importer.preview(big);

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .containsExactly(ImportErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void preview_missingRequiredColumn_returnsMissingColumnsError() {
        String csv = "id,name,price\n" + "x,y,1.00\n";

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.MISSING_COLUMNS);
    }

    @Test
    void preview_unknownColumn_producesWarningOnly() {
        String csv = header().replace("\n", ",extra_col\n");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.warnings())
                .extracting(ActivityImportPreviewDTO.RowWarning::code)
                .contains(ImportErrorCode.UNKNOWN_COLUMNS);
        assertThat(preview.errors()).isEmpty();
    }

    @Test
    void preview_bomStripped_headerStillRecognized() {
        byte[] withBom = ("\uFEFF" + header()).getBytes(StandardCharsets.UTF_8);

        ActivityImportPreviewDTO preview = importer.preview(withBom);

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.totalRows()).isZero();
    }

    @Test
    void preview_rowsOverLimit_returnsTooManyRowsError() {
        StringBuilder csv = new StringBuilder(header());
        UUID id = UUID.randomUUID();
        for (int i = 0; i <= 10_000; i++) {
            csv.append(id).append(",,,,,1.0,,,,\n");
        }

        ActivityImportPreviewDTO preview = importer.preview(csv.toString().getBytes());

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.TOO_MANY_ROWS);
    }

    private String row(String id, String slug, String destSlug, String name, String desc,
                       String price, String duration, String categorySlugs,
                       String imageUrl, String includes) {
        return id + "," + slug + "," + destSlug + ",\"" + name + "\",\"" + desc + "\",\""
                + price + "\"," + duration + "," + categorySlugs + ","
                + imageUrl + ",\"" + includes + "\"\n";
    }

    @Test
    void preview_rowWithBlankId_producesMissingIdError() {
        String csv = header() + row("", "s", "d", "n", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.MISSING_ID);
    }

    @Test
    void preview_rowWithBadUuid_producesInvalidUuidError() {
        String csv = header() + row("not-a-uuid", "s", "d", "n", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.INVALID_UUID);
    }

    @Test
    void preview_duplicateIds_producesDuplicateIdError() {
        UUID id = UUID.randomUUID();
        String csv = header()
                + row(id.toString(), "", "", "A", "", "1.00", "", "", "", "")
                + row(id.toString(), "", "", "B", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.DUPLICATE_ID);
    }

    @Test
    void preview_blankName_producesNameRequiredError() {
        UUID id = UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.NAME_REQUIRED);
    }

    @Test
    void preview_priceWithComma_producesInvalidDecimalError() {
        UUID id = UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "N", "", "1,50", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.INVALID_DECIMAL);
    }

    @Test
    void preview_negativePrice_producesInvalidDecimalError() {
        UUID id = UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "N", "", "-1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.INVALID_DECIMAL);
    }

    @Test
    void preview_blankPrice_producesPriceRequiredError() {
        UUID id = UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "N", "", "", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.PRICE_REQUIRED);
    }

    @Test
    void preview_nonIntDuration_producesInvalidIntegerError() {
        UUID id = UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "N", "", "1.00", "abc", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.INVALID_INTEGER);
    }

    @Test
    void preview_unknownCategorySlug_producesUnknownCategoryError() {
        when(categoryRepository.findBySlug("ghost")).thenReturn(Optional.empty());
        UUID id = UUID.randomUUID();
        String csv = header() + row(id.toString(), "", "", "N", "", "1.00", "", "ghost", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.UNKNOWN_CATEGORY);
    }

    @Test
    void preview_nameTooLong_producesFieldTooLongError() {
        UUID id = UUID.randomUUID();
        String longName = "X".repeat(256);
        String csv = header() + row(id.toString(), "", "", longName, "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.FIELD_TOO_LONG);
    }

    @Test
    void preview_unknownId_producesRowNotFoundError() {
        UUID missingId = UUID.randomUUID();
        when(activityRepository.findAllById(List.of(missingId))).thenReturn(List.of());
        String csv = header() + row(missingId.toString(), "", "", "N", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.ROW_NOT_FOUND);
    }

    @Test
    void preview_allFieldsUnchanged_producesNoDiffButIncrementsUnchanged() {
        Destination dest = TestDataFactory.destination();
        Activity existing = TestDataFactory.activity(dest);
        existing.setPrice(new BigDecimal("99.99"));
        when(activityRepository.findAllById(List.of(existing.getId())))
                .thenReturn(List.of(existing));

        String csv = header() + row(
                existing.getId().toString(),
                existing.getSlug(),
                dest.getSlug(),
                existing.getName(),
                existing.getDescription(),
                "99.99",
                existing.getDuration().toString(),
                "",
                existing.getImageUrl(),
                existing.getIncludes());

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.rowsUnchanged()).isEqualTo(1);
        assertThat(preview.rowsToUpdate()).isZero();
        assertThat(preview.changes()).isEmpty();
        assertThat(preview.token()).isNotNull();
    }

    @Test
    void preview_nameChanged_producesSingleFieldDiff() {
        Destination dest = TestDataFactory.destination();
        Activity existing = TestDataFactory.activity(dest);
        when(activityRepository.findAllById(List.of(existing.getId())))
                .thenReturn(List.of(existing));

        String csv = header() + row(
                existing.getId().toString(), existing.getSlug(), dest.getSlug(),
                "New Name", existing.getDescription(),
                existing.getPrice().toPlainString(),
                existing.getDuration().toString(),
                "", existing.getImageUrl(), existing.getIncludes());

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.rowsToUpdate()).isEqualTo(1);
        assertThat(preview.changes()).hasSize(1);
        ActivityImportPreviewDTO.RowDiff diff = preview.changes().getFirst();
        assertThat(diff.fieldChanges()).containsKey("name");
        assertThat(diff.fieldChanges().get("name").newValue()).isEqualTo("New Name");
        assertThat(preview.token()).isNotNull();
    }

    @Test
    void preview_readOnlyFieldChanged_producesWarningNotError() {
        Destination dest = TestDataFactory.destination();
        Activity existing = TestDataFactory.activity(dest);
        when(activityRepository.findAllById(List.of(existing.getId())))
                .thenReturn(List.of(existing));

        String csv = header() + row(
                existing.getId().toString(),
                "different-slug", // read-only, changed
                dest.getSlug(),
                existing.getName(), existing.getDescription(),
                existing.getPrice().toPlainString(),
                existing.getDuration().toString(),
                "", existing.getImageUrl(), existing.getIncludes());

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.warnings())
                .extracting(ActivityImportPreviewDTO.RowWarning::code)
                .contains(ImportErrorCode.READ_ONLY_FIELD_CHANGED);
        assertThat(preview.token()).isNotNull();
    }

    @Test
    void preview_errorsPresent_tokenIsNull() {
        String csv = header() + row("", "", "", "N", "", "1.00", "", "", "", "");

        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        assertThat(preview.token()).isNull();
    }

    @Test
    void apply_unknownToken_throwsTokenNotFound() {
        ActivityImportApplyRequest req = new ActivityImportApplyRequest(UUID.randomUUID().toString());

        assertThatThrownBy(() -> importer.apply(req))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getCode())
                        .isEqualTo(CsvImportException.Code.TOKEN_NOT_FOUND));
    }

    @Test
    void apply_validToken_persistsChangesAndInvalidatesToken() {
        Destination dest = TestDataFactory.destination();
        Activity existing = TestDataFactory.activity(dest);
        when(activityRepository.findAllById(List.of(existing.getId()))).thenReturn(List.of(existing));
        when(activityRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String csv = header() + row(
                existing.getId().toString(), existing.getSlug(), dest.getSlug(),
                "Renamed", existing.getDescription(),
                existing.getPrice().toPlainString(),
                existing.getDuration().toString(),
                "", existing.getImageUrl(), existing.getIncludes());
        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());
        assertThat(preview.token()).isNotNull();

        ActivityImportResultDTO result = importer.apply(new ActivityImportApplyRequest(preview.token()));

        assertThat(result.rowsUpdated()).isEqualTo(1);
        assertThat(existing.getName()).isEqualTo("Renamed");

        // second apply with the same token is rejected
        assertThatThrownBy(() ->
                importer.apply(new ActivityImportApplyRequest(preview.token())))
                .isInstanceOf(CsvImportException.class);
    }

    @Test
    void apply_activityDeletedBetweenPreviewAndApply_throwsStateChanged() {
        Destination dest = TestDataFactory.destination();
        Activity existing = TestDataFactory.activity(dest);
        when(activityRepository.findAllById(List.of(existing.getId()))).thenReturn(List.of(existing));

        String csv = header() + row(
                existing.getId().toString(), existing.getSlug(), dest.getSlug(),
                "Renamed", existing.getDescription(),
                existing.getPrice().toPlainString(),
                existing.getDuration().toString(),
                "", existing.getImageUrl(), existing.getIncludes());
        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        // simulate deletion
        when(activityRepository.findById(existing.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                importer.apply(new ActivityImportApplyRequest(preview.token())))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getCode())
                        .isEqualTo(CsvImportException.Code.STATE_CHANGED));
    }

    @Test
    void apply_expiredToken_throwsTokenExpired() {
        Destination dest = TestDataFactory.destination();
        Activity existing = TestDataFactory.activity(dest);
        when(activityRepository.findAllById(List.of(existing.getId()))).thenReturn(List.of(existing));

        String csv = header() + row(
                existing.getId().toString(), existing.getSlug(), dest.getSlug(),
                "Renamed", existing.getDescription(),
                existing.getPrice().toPlainString(),
                existing.getDuration().toString(),
                "", existing.getImageUrl(), existing.getIncludes());
        ActivityImportPreviewDTO preview = importer.preview(csv.getBytes());

        // backdate via package-private helper
        importer.expireTokenForTest(UUID.fromString(preview.token()));

        assertThatThrownBy(() ->
                importer.apply(new ActivityImportApplyRequest(preview.token())))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getCode())
                        .isEqualTo(CsvImportException.Code.TOKEN_EXPIRED));
    }
}
