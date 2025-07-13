package dev.pcvolkmer.onco.dnpmexport;

import de.itc.onkostar.api.Disease;
import de.itc.onkostar.api.IOnkostarApi;
import de.itc.onkostar.api.Procedure;
import de.itc.onkostar.api.analysis.AnalyzerRequirement;
import de.itc.onkostar.api.analysis.IProcedureAnalyzer;
import de.itc.onkostar.api.analysis.OnkostarPluginType;
import dev.pcvolkmer.onco.datamapper.mapper.MtbDataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

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

    public ExportAnalyzer(final IOnkostarApi onkostarApi, final DataSource dataSource) {
        this.onkostarApi = onkostarApi;
        // Reuse default Onkostar DataSource for MtbDataMapper
        this.mtbDataMapper = MtbDataMapper.create(dataSource);
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
            String caseId = "";
            switch (procedure.getFormName()) {
                case "DNPM Klinik/Anamnese":
                    caseId = procedure.getValue("FallnummrMV").getString();
                    break;
                case "DNPM Therapieplan":
                    var kpaProcedure = onkostarApi.getProcedure(
                            procedure.getValue("ref_dnpm_klinikanamnese").getInt()
                    );
                    caseId = kpaProcedure.getValue("FallnummrMV").getString();
                    break;
                default:
                    logger.info("Cannot handle procedure form {}", procedure.getFormName());
                    return;
            }
            var mtb = mtbDataMapper.getByCaseId(caseId);

            // TODO: Send MTB data to remote application
        } catch (Exception e) {
            logger.error("Could export mtb data for procedure {}", procedure.getId(), e);
        }

    }

}
