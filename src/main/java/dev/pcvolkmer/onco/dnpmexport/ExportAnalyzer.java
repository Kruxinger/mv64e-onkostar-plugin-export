package dev.pcvolkmer.onco.dnpmexport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.itc.onkostar.api.Disease;
import de.itc.onkostar.api.IOnkostarApi;
import de.itc.onkostar.api.Procedure;
import de.itc.onkostar.api.analysis.AnalyseTriggerEvent;
import de.itc.onkostar.api.analysis.AnalyzerRequirement;
import de.itc.onkostar.api.analysis.IProcedureAnalyzer;
import de.itc.onkostar.api.analysis.OnkostarPluginType;
import dev.pcvolkmer.mv64e.mtb.Mtb;
import dev.pcvolkmer.mv64e.mtb.Patient;
import dev.pcvolkmer.onco.datamapper.mapper.MtbDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Analyzer to start export if DNPM Klinik/Anamnese or DNPM Therapieplan form gets locked
 *
 * @author Paul-Christian Volkmer
 * @since 0.1
 */
@Component
public class ExportAnalyzer implements IProcedureAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(ExportAnalyzer.class);

    private final IOnkostarApi onkostarApi;
    private final MtbDataMapper mtbDataMapper;
    private final RestTemplate restTemplate;

    public ExportAnalyzer(
            final IOnkostarApi onkostarApi,
            final MtbDataMapper mtbDataMapper,
            final RestTemplate restTemplate
    ) {
        this.onkostarApi = onkostarApi;
        this.mtbDataMapper = mtbDataMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public OnkostarPluginType getType() {
        return OnkostarPluginType.ANALYZER;
    }

    @Override
    public String getVersion() {
        return "0.1.0";
    }

    @Override
    public String getName() {
        return "DNPM-Export-Plugin";
    }

    @Override
    public String getDescription() {
        return "OS Export-Plugin für das DNPM-Datenmodell 2.1";
    }

    @Override
    public boolean isSynchronous() {
        return false;
    }

    @Override
    public AnalyzerRequirement getRequirement() {
        return AnalyzerRequirement.PROCEDURE;
    }

    @Override
    public boolean isRelevantForDeletedProcedure() {
        return false;
    }

    @Override
    public boolean isRelevantForAnalyzer(final Procedure procedure, final Disease disease) {
        return null != procedure
                && ("DNPM Klinik/Anamnese".equals(procedure.getFormName()) || "DNPM Therapieplan".equals(procedure.getFormName()));
    }

    @Override
    public void analyze(final Procedure procedure, final Disease disease) {
        logger.info("Starting export for procedure {}", procedure.getId());

        try {
            String caseId;
            switch (procedure.getFormName()) {
                case "DNPM Klinik/Anamnese":
                    caseId = procedure.getValue("FallnummerMV").getString();
                    break;
                case "DNPM Therapieplan":
                    var kpaProcedure = onkostarApi.getProcedure(
                            procedure.getValue("ref_dnpm_klinikanamnese").getInt()
                    );
                    caseId = kpaProcedure.getValue("FallnummerMV").getString();
                    break;
                default:
                    logger.info("Cannot handle procedure form {}", procedure.getFormName());
                    return;
            }
            Mtb mtb = mtbDataMapper.getByCaseId(caseId);


            mtb.getPatient().setId(mtb.getPatient().getId()+"###"+caseId);

            Mtb filtered = new Mtb();
            Patient pat = new Patient();
            pat.setBirthDate(mtb.getPatient().getBirthDate());
            pat.setGender(mtb.getPatient().getGender());
            pat.getGender().setCode(mtb.getPatient().getGender().getCode());
            pat.setHealthInsurance(mtb.getPatient().getHealthInsurance());
            pat.setId(mtb.getPatient().getId());
            filtered.setPatient(pat);
            filtered.setEpisodesOfCare(mtb.getEpisodesOfCare());
            filtered.setDiagnoses(mtb.getDiagnoses());
            filtered.setMetadata(mtb.getMetadata());

            sendMtbFileRequest(filtered);


        } catch (Exception e) {
            logger.error("Could not export mtb data for procedure {}", procedure.getId(), e);
        }

    }
    @Override
    public Set<AnalyseTriggerEvent> getTriggerEvents(){
        return Set.of(
                AnalyseTriggerEvent.LOCK,
                AnalyseTriggerEvent.EDIT_LOCK,
                AnalyseTriggerEvent.REORG
        );
    }

    public void sendMtbFileRequest(Mtb mtb) {
        var exportUrl = onkostarApi.getGlobalSetting("dnpmexport_url");
        var restTemplate = new RestTemplate();
        var objectMapper = new ObjectMapper();

        // UTF-8 für String-Konverter erzwingen
        restTemplate.getMessageConverters().removeIf(c -> c instanceof StringHttpMessageConverter);
        restTemplate.getMessageConverters().add(new StringHttpMessageConverter(StandardCharsets.UTF_8));

        try {
            // 1️⃣ Objekt in JSON-String serialisieren
            String jsonPayload = objectMapper.writeValueAsString(mtb);
            logger.debug("JSON to send: {}", jsonPayload);

            // 2️⃣ HttpEntity mit String-Body erstellen
            var headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));

            var uri = URI.create(exportUrl);
            if (uri.getUserInfo() != null) {
                headers.set(HttpHeaders.AUTHORIZATION, "Basic " +
                        Base64.getEncoder().encodeToString(uri.getUserInfo().getBytes(StandardCharsets.UTF_8)));
            }

            var entityReq = new HttpEntity<>(jsonPayload, headers);

            // 3️⃣ POST mit UTF-8-encoded String senden
            var r = restTemplate.postForEntity(uri, entityReq, String.class);
            if (!r.getStatusCode().is2xxSuccessful()) {
                logger.warn("Error sending to remote system: {}", r.getBody());
                throw new RuntimeException("Kann Daten nicht an das externe System senden");
            }

        } catch (Exception e) {
            logger.error("Cannot send data to remote system", e);
            throw new RuntimeException("Kann Daten nicht an das externe System senden");
        }
    }


}
