package dev.pcvolkmer.onco.dnpmexport;

import dev.pcvolkmer.onco.datamapper.mapper.MtbDataMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;

@Configuration
public class ExportAnalyzerConfig {

    @Bean
    public MtbDataMapper mtbDataMapper(final DataSource dataSource) {
        // Reuse default Onkostar DataSource for MtbDataMapper
        return MtbDataMapper.create(dataSource);
    }
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
