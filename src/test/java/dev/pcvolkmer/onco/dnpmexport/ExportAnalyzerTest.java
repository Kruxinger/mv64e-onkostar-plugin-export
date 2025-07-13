package dev.pcvolkmer.onco.dnpmexport;

import de.itc.onkostar.api.IOnkostarApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

@ExtendWith(MockitoExtension.class)
class ExportAnalyzerTest {

    private IOnkostarApi onkostarApi;
    private DataSource dataSource;

    private ExportAnalyzer analyzer;

    @BeforeEach
    void setup(
            @Mock IOnkostarApi onkostarApi,
            @Mock DataSource dataSource
    ) {
        this.onkostarApi = onkostarApi;
        this.dataSource = dataSource;
        this.analyzer = new ExportAnalyzer(onkostarApi, dataSource);
    }

    @Test
    void testShouldTestSomeAnalyzerImplementation() {
        // Implement your first test
    }
}
