package com.myhive.backend.service.activity;

import com.myhive.backend.TestDataFactory;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Destination;
import com.myhive.backend.repository.ActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityCsvExporterTest {

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private ActivityCsvExporter exporter;

    private Destination destination;
    private Category categoryBeach;
    private Category categoryFamily;

    @BeforeEach
    void setUp() {
        destination = TestDataFactory.destination();
        categoryBeach = TestDataFactory.category("Beach");
        categoryFamily = TestDataFactory.category("Family");
    }

    @Test
    void export_writesBomAndHeader() {
        when(activityRepository.findAll()).thenReturn(List.of());

        String csv = exporter.exportAll();

        assertThat(csv).startsWith("\uFEFF");
        String firstLine = csv.substring(1).split("\r?\n", 2)[0];
        assertThat(firstLine).isEqualTo(
                "id,slug,destination_slug,name,description,price,duration,category_slugs,image_url,includes");
    }

    @Test
    void export_writesActivityRowWithCategoriesJoined() {
        Activity activity = TestDataFactory.activity(destination, categoryBeach, categoryFamily);
        when(activityRepository.findAll()).thenReturn(List.of(activity));

        String csv = exporter.exportAll();

        assertThat(csv).contains(activity.getId().toString());
        assertThat(csv).contains("beach;family");
    }

    @Test
    void export_escapesQuotesAndNewlinesInDescription() {
        Activity activity = TestDataFactory.activity(destination);
        activity.setDescription("Line one\nLine \"two\"");
        when(activityRepository.findAll()).thenReturn(List.of(activity));

        String csv = exporter.exportAll();

        assertThat(csv).contains("\"Line one\nLine \"\"two\"\"\"");
    }

    @Test
    void export_prefixesFormulaInjectionWithSingleQuote() {
        Activity activity = TestDataFactory.activity(destination);
        activity.setName("=CMD(\"calc\")");
        activity.setIncludes("-1+1");
        activity.setDescription("@lookup");
        when(activityRepository.findAll()).thenReturn(List.of(activity));

        String csv = exporter.exportAll();

        assertThat(csv).contains("\"'=CMD(\"\"calc\"\")\"");
        assertThat(csv).contains("'-1+1");
        assertThat(csv).contains("'@lookup");
    }

    @Test
    void export_blankPriceOrDurationWrittenAsEmpty() {
        Activity activity = TestDataFactory.activity(destination);
        activity.setPrice(new BigDecimal("12.50"));
        activity.setDuration(null);
        when(activityRepository.findAll()).thenReturn(List.of(activity));

        String csv = exporter.exportAll();

        assertThat(csv).contains("12.50");
        // duration column must be empty (two consecutive commas around it)
        String dataLine = csv.split("\r?\n")[1];
        String[] columns = dataLine.split(",", -1);
        assertThat(columns[6]).isEmpty();
    }
}
