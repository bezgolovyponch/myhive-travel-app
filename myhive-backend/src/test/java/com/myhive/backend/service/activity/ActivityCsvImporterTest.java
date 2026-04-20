package com.myhive.backend.service.activity;

import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}
