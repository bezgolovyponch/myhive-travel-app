package com.myhive.backend.service.activity;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.dto.ActivityImportApplyRequest;
import com.myhive.backend.dto.ActivityImportPreviewDTO;
import com.myhive.backend.dto.ActivityImportResultDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.CsvImportException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.DestinationRepository;
import com.myhive.backend.service.ImageUploadService;
import com.myhive.backend.service.RemoteImageFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rows with an empty {@code id} create new activities. Update behaviour is covered by
 * {@link ActivityCsvImporterTest}; this class covers the create branch only.
 */
@ExtendWith(MockitoExtension.class)
class ActivityCsvImporterCreateTest {

    private static final String HOSTED_BASE = "https://img.test";

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private DestinationRepository destinationRepository;
    @Mock
    private ImageUploadService imageUploadService;
    @Mock
    private RemoteImageFetcher remoteImageFetcher;

    private ActivityCsvImporter importer;
    private Destination bali;

    @BeforeEach
    void setUp() {
        importer = newImporter(Optional.of(imageUploadService));
        bali = TestDataFactory.destination();
        bali.setSlug("bali");
    }

    private ActivityCsvImporter newImporter(Optional<ImageUploadService> uploadService) {
        ActivityImportWriter writer = new ActivityImportWriter(
                activityRepository, categoryRepository, destinationRepository);
        return new ActivityCsvImporter(activityRepository, categoryRepository, destinationRepository,
                writer, uploadService, remoteImageFetcher);
    }

    private String header() {
        return "id,slug,destination_slug,name,description,price,duration,category_slugs,image_url,includes\n";
    }

    private String newRow(String slug, String destSlug, String name, String price,
                          String categorySlugs, String imageUrl) {
        return "," + slug + "," + destSlug + ",\"" + name + "\",\"Desc\",\"" + price + "\",60,"
                + categorySlugs + "," + imageUrl + ",\"Guide\"\n";
    }

    private void stubBaliExists() {
        when(destinationRepository.findBySlug("bali")).thenReturn(Optional.of(bali));
    }

    private ActivityImportPreviewDTO previewOf(String csv) {
        return importer.preview(csv.getBytes());
    }

    @Test
    void preview_blankId_isReportedAsCreateNotError() {
        stubBaliExists();
        String expectedName = "Sunrise hike";
        String csv = header() + newRow("", "bali", expectedName, "45.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.rowsToCreate()).isEqualTo(1);
        assertThat(preview.rowsToUpdate()).isZero();
        assertThat(preview.token()).isNotNull();
        ActivityImportPreviewDTO.RowCreate create = preview.creates().get(0);
        assertThat(create.csvRowNumber()).isEqualTo(2);
        assertThat(create.name()).isEqualTo(expectedName);
        assertThat(create.destinationSlug()).isEqualTo("bali");
        assertThat(create.slug()).isNull();
        assertThat(create.price()).isEqualByComparingTo("45.00");
    }

    @Test
    void preview_blankDestinationOnNewRow_producesDestinationRequiredError() {
        String csv = header() + newRow("", "", "Hike", "45.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .containsExactly(ImportErrorCode.DESTINATION_REQUIRED);
    }

    @Test
    void preview_unknownDestinationOnNewRow_producesUnknownDestinationError() {
        when(destinationRepository.findBySlug("atlantis")).thenReturn(Optional.empty());
        String csv = header() + newRow("", "atlantis", "Hike", "45.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code, ActivityImportPreviewDTO.RowError::field)
                .containsExactly(tuple(
                        ImportErrorCode.UNKNOWN_DESTINATION, "destination_slug"));
    }

    @Test
    void preview_newRowStillRequiresNameAndPrice() {
        stubBaliExists();
        String csv = header() + newRow("", "bali", "", "", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .containsExactlyInAnyOrder(ImportErrorCode.NAME_REQUIRED, ImportErrorCode.PRICE_REQUIRED);
    }

    @Test
    void preview_customSlugOnNewRow_isShownInPreview() {
        stubBaliExists();
        when(activityRepository.existsBySlug("sunrise-hike")).thenReturn(false);
        String csv = header() + newRow("sunrise-hike", "bali", "Sunrise hike", "45.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.creates().get(0).slug()).isEqualTo("sunrise-hike");
        assertThat(preview.warnings()).isEmpty();
    }

    @Test
    void preview_customSlugAlreadyInDb_producesSlugTakenWarning() {
        stubBaliExists();
        when(activityRepository.existsBySlug("sunrise-hike")).thenReturn(true);
        String csv = header() + newRow("sunrise-hike", "bali", "Sunrise hike", "45.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.warnings())
                .extracting(ActivityImportPreviewDTO.RowWarning::code)
                .containsExactly(ImportErrorCode.SLUG_TAKEN);
    }

    @Test
    void preview_sameNameTwiceInFileForOneDestination_producesDuplicateNameWarning() {
        stubBaliExists();
        String csv = header()
                + newRow("", "bali", "Sunrise hike", "45.00", "", "")
                + newRow("", "bali", "sunrise HIKE", "50.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.rowsToCreate()).isEqualTo(2);
        assertThat(preview.warnings())
                .extracting(ActivityImportPreviewDTO.RowWarning::code, ActivityImportPreviewDTO.RowWarning::csvRowNumber)
                .containsExactly(tuple(ImportErrorCode.DUPLICATE_NAME, 3));
    }

    @Test
    void preview_nameAlreadyExistsInDestination_isAnErrorNotAWarning() {
        // An exported file whose id column got blanked by a spreadsheet must not re-create the catalog.
        stubBaliExists();
        when(activityRepository.existsByDestinationSlugAndNameIgnoreCase("bali", "Sunrise hike"))
                .thenReturn(true);
        String csv = header() + newRow("", "bali", "Sunrise hike", "45.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code, ActivityImportPreviewDTO.RowError::field)
                .containsExactly(tuple(ImportErrorCode.NAME_EXISTS, "name"));
    }

    @Test
    void preview_customSlugIsNormalizedBeforeCollisionCheckAndDisplay() {
        stubBaliExists();
        when(activityRepository.existsBySlug("sunrise-hike")).thenReturn(true);
        String csv = header() + newRow("Sunrise Hike", "bali", "Morning walk", "45.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.creates().get(0).slug()).isEqualTo("sunrise-hike");
        assertThat(preview.warnings())
                .extracting(ActivityImportPreviewDTO.RowWarning::code)
                .containsExactly(ImportErrorCode.SLUG_TAKEN);
    }

    @Test
    void preview_twoNewRowsWhoseCustomSlugsNormalizeToTheSame_warnOnce() {
        stubBaliExists();
        String csv = header()
                + newRow("My-Hike", "bali", "First", "45.00", "", "")
                + newRow("my hike", "bali", "Second", "45.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.warnings())
                .extracting(ActivityImportPreviewDTO.RowWarning::code, ActivityImportPreviewDTO.RowWarning::csvRowNumber)
                .containsExactly(tuple(ImportErrorCode.SLUG_TAKEN, 3));
    }

    @Test
    void preview_unslugifiableName_producesInvalidSlugErrorOnName() {
        stubBaliExists();
        String csv = header() + newRow("", "bali", "🔥🌍", "45.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code, ActivityImportPreviewDTO.RowError::field)
                .containsExactly(tuple(ImportErrorCode.INVALID_SLUG, "name"));
    }

    @Test
    void preview_unslugifiableCustomSlug_producesInvalidSlugErrorOnSlug() {
        stubBaliExists();
        String csv = header() + newRow("!!!", "bali", "Hike", "45.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code, ActivityImportPreviewDTO.RowError::field)
                .containsExactly(tuple(ImportErrorCode.INVALID_SLUG, "slug"));
    }

    @Test
    void preview_customSlugLongerThanColumn_producesFieldTooLongError() {
        stubBaliExists();
        String csv = header() + newRow("a".repeat(301), "bali", "Hike", "45.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code, ActivityImportPreviewDTO.RowError::field)
                .containsExactly(tuple(ImportErrorCode.FIELD_TOO_LONG, "slug"));
    }

    @Test
    void preview_sameDestinationOnManyRows_looksItUpOnce() {
        stubBaliExists();
        String csv = header()
                + newRow("", "bali", "One", "1.00", "", "")
                + newRow("", "bali", "Two", "1.00", "", "")
                + newRow("", "bali", "Three", "1.00", "", "");

        previewOf(csv);

        verify(destinationRepository, times(1)).findBySlug("bali");
    }

    @Test
    void preview_unknownCategoryOnNewRow_isAnError() {
        stubBaliExists();
        when(categoryRepository.findBySlug("nope")).thenReturn(Optional.empty());
        String csv = header() + newRow("", "bali", "Hike", "45.00", "nope", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .containsExactly(ImportErrorCode.UNKNOWN_CATEGORY);
    }

    @Test
    void preview_malformedImageUrlOnNewRow_producesInvalidUrlError() {
        stubBaliExists();
        String csv = header() + newRow("", "bali", "Hike", "45.00", "", "ftp://example.com/x.jpg");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code, ActivityImportPreviewDTO.RowError::field)
                .containsExactly(tuple(ImportErrorCode.INVALID_URL, "image_url"));
    }

    @Test
    void preview_remoteImageWhenUploadsNotConfigured_producesUploadUnavailableError() {
        ActivityCsvImporter noUploads = newImporter(Optional.empty());
        stubBaliExists();
        String csv = header() + newRow("", "bali", "Hike", "45.00", "", "https://example.com/x.jpg");

        ActivityImportPreviewDTO preview = noUploads.preview(csv.getBytes());

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .containsExactly(ImportErrorCode.IMAGE_UPLOAD_UNAVAILABLE);
    }

    @Test
    void preview_anyImageUrlWhenUploadsNotConfigured_isRejected() {
        // Without an ImageUploadService there is no way to recognise "our" bucket host, so
        // every non-empty image_url on a new row counts as remote: this documents that choice.
        ActivityCsvImporter noUploads = newImporter(Optional.empty());
        stubBaliExists();
        String csv = header() + newRow("", "bali", "Hike", "45.00", "", HOSTED_BASE + "/a.jpg");

        ActivityImportPreviewDTO preview = noUploads.preview(csv.getBytes());

        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .containsExactly(ImportErrorCode.IMAGE_UPLOAD_UNAVAILABLE);
    }

    @Test
    void preview_tooManyRemoteImages_producesFileLevelError() {
        stubBaliExists();
        StringBuilder csv = new StringBuilder(header());
        for (int i = 0; i <= ActivityCsvImporter.MAX_REMOTE_IMAGES; i++) {
            csv.append(newRow("", "bali", "Hike " + i, "45.00", "", "https://example.com/" + i + ".jpg"));
        }

        ActivityImportPreviewDTO preview = previewOf(csv.toString());

        assertThat(preview.token()).isNull();
        assertThat(preview.errors())
                .extracting(ActivityImportPreviewDTO.RowError::code)
                .contains(ImportErrorCode.TOO_MANY_IMAGES);
    }

    @Test
    void preview_hostedImageUrl_doesNotCountAsRemote() {
        stubBaliExists();
        when(imageUploadService.isHostedUrl(HOSTED_BASE + "/a.jpg")).thenReturn(true);
        StringBuilder csv = new StringBuilder(header());
        for (int i = 0; i <= ActivityCsvImporter.MAX_REMOTE_IMAGES; i++) {
            csv.append(newRow("", "bali", "Hike " + i, "45.00", "", HOSTED_BASE + "/a.jpg"));
        }

        ActivityImportPreviewDTO preview = previewOf(csv.toString());

        assertThat(preview.errors()).isEmpty();
    }

    @Test
    void preview_mixedFile_reportsUpdatesAndCreatesSeparately() {
        stubBaliExists();
        Activity existing = TestDataFactory.activity(bali);
        when(activityRepository.findAllById(List.of(existing.getId()))).thenReturn(List.of(existing));
        String csv = header()
                + existing.getId() + "," + existing.getSlug() + ",bali,\"Renamed\",\"" + existing.getDescription()
                + "\",\"" + existing.getPrice().toPlainString() + "\"," + existing.getDuration() + ",,"
                + existing.getImageUrl() + ",\"" + existing.getIncludes() + "\"\n"
                + newRow("", "bali", "Brand new", "10.00", "", "");

        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThat(preview.errors()).isEmpty();
        assertThat(preview.totalRows()).isEqualTo(2);
        assertThat(preview.rowsToUpdate()).isEqualTo(1);
        assertThat(preview.rowsToCreate()).isEqualTo(1);
        assertThat(preview.changes()).hasSize(1);
        assertThat(preview.creates()).hasSize(1);
    }

    @Test
    void apply_newRow_savesActivityWithDestinationCategoriesAndDefaults() {
        stubBaliExists();
        Category beach = TestDataFactory.category("Beach");
        beach.setSlug("beach");
        when(categoryRepository.findBySlug("beach")).thenReturn(Optional.of(beach));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String expectedName = "Sunrise hike";
        BigDecimal expectedPrice = new BigDecimal("45.00");
        String csv = header() + newRow("", "bali", expectedName, "45.00", "beach", "");
        ActivityImportPreviewDTO preview = previewOf(csv);

        ActivityImportResultDTO result = importer.apply(new ActivityImportApplyRequest(preview.token()));

        assertThat(result.rowsCreated()).isEqualTo(1);
        assertThat(result.rowsUpdated()).isZero();
        Activity saved = capturedSave();
        assertThat(saved.getName()).isEqualTo(expectedName);
        assertThat(saved.getDestination()).isSameAs(bali);
        assertThat(saved.getPrice()).isEqualByComparingTo(expectedPrice);
        assertThat(saved.getDuration()).isEqualTo(60);
        assertThat(saved.getDescription()).isEqualTo("Desc");
        assertThat(saved.getIncludes()).isEqualTo("Guide");
        assertThat(saved.getCategories()).containsExactly(beach);
        assertThat(saved.getSlug()).isEqualTo("sunrise-hike");
        assertThat(saved.getImageUrl()).isNull();
        assertThat(saved.getMinPrice()).isNull();
        assertThat(saved.getFeaturedWeight()).isZero();
        assertThat(saved.isFeatured()).isFalse();
        assertThat(saved.isSeoIndexable()).isFalse();
    }

    @Test
    void apply_newRowWithCustomSlug_usesIt() {
        stubBaliExists();
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String csv = header() + newRow("my-hike", "bali", "Sunrise hike", "45.00", "", "");
        ActivityImportPreviewDTO preview = previewOf(csv);

        importer.apply(new ActivityImportApplyRequest(preview.token()));

        assertThat(capturedSave().getSlug()).isEqualTo("my-hike");
    }

    @Test
    void apply_newRowWithOptionalColumns_setsMinPriceAndFeaturedWeight() {
        stubBaliExists();
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String csv = "id,slug,destination_slug,name,description,price,duration,category_slugs,image_url,includes,"
                + "min_price,featured_weight\n"
                + ",,bali,\"Hike\",\"\",\"45.00\",,,,\"\",120.00,3\n";
        ActivityImportPreviewDTO preview = previewOf(csv);

        importer.apply(new ActivityImportApplyRequest(preview.token()));

        Activity saved = capturedSave();
        assertThat(saved.getMinPrice()).isEqualByComparingTo("120.00");
        assertThat(saved.getFeaturedWeight()).isEqualTo(3);
    }

    @Test
    void apply_remoteImage_isFetchedUploadedAndStoredAsHostedUrl() throws IOException {
        stubBaliExists();
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String remoteUrl = "https://example.com/photo.jpg";
        String expectedHostedUrl = HOSTED_BASE + "/abc.jpg";
        byte[] bytes = {1, 2, 3};
        when(remoteImageFetcher.fetch(remoteUrl))
                .thenReturn(new RemoteImageFetcher.FetchedImage(bytes, "image/jpeg", "photo.jpg"));
        when(imageUploadService.uploadImage(bytes, "image/jpeg", "photo.jpg")).thenReturn(expectedHostedUrl);
        String csv = header() + newRow("", "bali", "Hike", "45.00", "", remoteUrl);
        ActivityImportPreviewDTO preview = previewOf(csv);

        importer.apply(new ActivityImportApplyRequest(preview.token()));

        assertThat(capturedSave().getImageUrl()).isEqualTo(expectedHostedUrl);
    }

    @Test
    void apply_hostedImageUrl_isStoredWithoutFetching() {
        stubBaliExists();
        String hostedUrl = HOSTED_BASE + "/existing.jpg";
        when(imageUploadService.isHostedUrl(hostedUrl)).thenReturn(true);
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String csv = header() + newRow("", "bali", "Hike", "45.00", "", hostedUrl);
        ActivityImportPreviewDTO preview = previewOf(csv);

        importer.apply(new ActivityImportApplyRequest(preview.token()));

        assertThat(capturedSave().getImageUrl()).isEqualTo(hostedUrl);
        verify(remoteImageFetcher, never()).fetch(anyString());
    }

    @Test
    void apply_imageFetchFails_throwsImageFetchFailedAndWritesNothing() {
        stubBaliExists();
        String remoteUrl = "https://example.com/gone.jpg";
        when(remoteImageFetcher.fetch(remoteUrl))
                .thenThrow(new RemoteImageFetcher.FetchException("HTTP 404"));
        String csv = header()
                + newRow("", "bali", "First", "45.00", "", "")
                + newRow("", "bali", "Second", "45.00", "", remoteUrl);
        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThatThrownBy(() -> importer.apply(new ActivityImportApplyRequest(preview.token())))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getCode())
                        .isEqualTo(CsvImportException.Code.IMAGE_FETCH_FAILED))
                .hasMessageContaining("row 3")
                .hasMessageContaining("HTTP 404");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void apply_imageFetchFails_tokenStaysValidSoApplyCanBeRetried() throws IOException {
        stubBaliExists();
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String remoteUrl = "https://example.com/flaky.jpg";
        String expectedHostedUrl = HOSTED_BASE + "/ok.jpg";
        RemoteImageFetcher.FetchedImage fetched = new RemoteImageFetcher.FetchedImage(new byte[]{1}, "image/jpeg", "flaky.jpg");
        when(remoteImageFetcher.fetch(remoteUrl))
                .thenThrow(new RemoteImageFetcher.FetchException("download timed out after 10s"))
                .thenReturn(fetched);
        when(imageUploadService.uploadImage(fetched.bytes(), "image/jpeg", "flaky.jpg")).thenReturn(expectedHostedUrl);
        String csv = header() + newRow("", "bali", "Hike", "45.00", "", remoteUrl);
        ActivityImportPreviewDTO preview = previewOf(csv);
        ActivityImportApplyRequest request = new ActivityImportApplyRequest(preview.token());

        assertThatThrownBy(() -> importer.apply(request)).isInstanceOf(CsvImportException.class);
        ActivityImportResultDTO result = importer.apply(request);

        assertThat(result.rowsCreated()).isEqualTo(1);
        assertThat(capturedSave().getImageUrl()).isEqualTo(expectedHostedUrl);
        assertThatThrownBy(() -> importer.apply(request))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getCode())
                        .isEqualTo(CsvImportException.Code.TOKEN_NOT_FOUND));
    }

    @Test
    void apply_uploadPipelineRejectsImage_isReportedAsImageFetchFailedWithRow() throws IOException {
        stubBaliExists();
        String remoteUrl = "https://example.com/huge.jpg";
        when(remoteImageFetcher.fetch(remoteUrl))
                .thenReturn(new RemoteImageFetcher.FetchedImage(new byte[]{1}, "image/jpeg", "huge.jpg"));
        when(imageUploadService.uploadImage(any(byte[].class), anyString(), anyString()))
                .thenThrow(new BadRequestException("Image is too large to process"));
        String csv = header() + newRow("", "bali", "Hike", "45.00", "", remoteUrl);
        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThatThrownBy(() -> importer.apply(new ActivityImportApplyRequest(preview.token())))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getCode())
                        .isEqualTo(CsvImportException.Code.IMAGE_FETCH_FAILED))
                .hasMessageContaining("row 2")
                .hasMessageContaining("too large to process");
        verify(activityRepository, never()).save(any());
    }

    @Test
    void apply_rehostingOverBudget_failsBeforeAnyWrite() throws IOException {
        stubBaliExists();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-14T10:00:00Z"));
        importer.setClockForTest(clock);
        String first = "https://example.com/1.jpg";
        String second = "https://example.com/2.jpg";
        RemoteImageFetcher.FetchedImage fetched = new RemoteImageFetcher.FetchedImage(new byte[]{1}, "image/jpeg", "1.jpg");
        when(remoteImageFetcher.fetch(first)).thenAnswer(inv -> {
            clock.advance(ActivityCsvImporter.REHOST_BUDGET.plusSeconds(1));
            return fetched;
        });
        when(imageUploadService.uploadImage(fetched.bytes(), "image/jpeg", "1.jpg")).thenReturn(HOSTED_BASE + "/1.jpg");
        String csv = header()
                + newRow("", "bali", "One", "45.00", "", first)
                + newRow("", "bali", "Two", "45.00", "", second);
        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThatThrownBy(() -> importer.apply(new ActivityImportApplyRequest(preview.token())))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getCode())
                        .isEqualTo(CsvImportException.Code.IMAGE_FETCH_FAILED))
                .hasMessageContaining("row 3")
                .hasMessageContaining("budget");
        verify(remoteImageFetcher, never()).fetch(second);
        verify(activityRepository, never()).save(any());
    }

    /** Test clock that only moves when told to. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void apply_destinationDeletedBetweenPreviewAndApply_throwsStateChanged() {
        when(destinationRepository.findBySlug("bali"))
                .thenReturn(Optional.of(bali))
                .thenReturn(Optional.empty());
        String csv = header() + newRow("", "bali", "Hike", "45.00", "", "");
        ActivityImportPreviewDTO preview = previewOf(csv);

        assertThatThrownBy(() -> importer.apply(new ActivityImportApplyRequest(preview.token())))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getCode())
                        .isEqualTo(CsvImportException.Code.STATE_CHANGED));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void apply_mixedFile_updatesThenCreates() {
        stubBaliExists();
        Activity existing = TestDataFactory.activity(bali);
        when(activityRepository.findAllById(List.of(existing.getId()))).thenReturn(List.of(existing));
        when(activityRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String csv = header()
                + existing.getId() + "," + existing.getSlug() + ",bali,\"Renamed\",\"" + existing.getDescription()
                + "\",\"" + existing.getPrice().toPlainString() + "\"," + existing.getDuration() + ",,"
                + existing.getImageUrl() + ",\"" + existing.getIncludes() + "\"\n"
                + newRow("", "bali", "Brand new", "10.00", "", "");
        ActivityImportPreviewDTO preview = previewOf(csv);

        ActivityImportResultDTO result = importer.apply(new ActivityImportApplyRequest(preview.token()));

        assertThat(result.rowsUpdated()).isEqualTo(1);
        assertThat(result.rowsCreated()).isEqualTo(1);
        assertThat(existing.getName()).isEqualTo("Renamed");
        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0)).isSameAs(existing);
        assertThat(captor.getAllValues().get(1).getName()).isEqualTo("Brand new");
    }

    private Activity capturedSave() {
        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        return captor.getValue();
    }
}
