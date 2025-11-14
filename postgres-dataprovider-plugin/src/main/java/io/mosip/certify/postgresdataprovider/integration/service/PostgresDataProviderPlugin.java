package io.mosip.certify.postgresdataprovider.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.api.spi.DataProviderPlugin;
import io.mosip.certify.postgresdataprovider.integration.repository.DataProviderRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;

@ConditionalOnProperty(value = "mosip.certify.integration.data-provider-plugin", havingValue = "PostgresDataProviderPlugin")
@Component
@Slf4j
public class PostgresDataProviderPlugin implements DataProviderPlugin {

    @Autowired
    private DataProviderRepository dataProviderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("#{${mosip.certify.data-provider-plugin.postgres.scope-query-mapping}}")
    private LinkedHashMap<String, String> scopeQueryMapping;

    public PostgresDataProviderPlugin() {
        log.info("=== POSTGRES DATA PROVIDER PLUGIN CONSTRUCTOR CALLED ===");
    }

    @PostConstruct
    public void init() {
        log.info("=== POSTGRES DATA PROVIDER PLUGIN INITIALIZED ===");
        log.info("Scope query mapping: {}", scopeQueryMapping);
    }

    @Override
    public JSONObject fetchData(Map<String, Object> identityDetails) throws DataProviderExchangeException {
        log.info("=== PLUGIN fetchData METHOD CALLED ===");

        try {
            String individualId = (String) identityDetails.get("sub");
            String scope = (String) identityDetails.get("scope");
            String queryString = scopeQueryMapping.get(scope);

            log.info("Fetching data for individualId: {}, scope: {}", individualId, scope);

            if (individualId != null && queryString != null) {
                Map<String, Object> dataRecord = dataProviderRepository.fetchQueryResult(individualId, queryString);


                if (dataRecord == null || dataRecord.isEmpty()) {
                    log.error("Empty data received for individualId: {}, scope: {}", individualId, scope);
                    throw new DataProviderExchangeException("NO_USER_DATA_FOUND_FOR_ID");
                }

                log.info("Successfully fetched {} fields for individualId: {}", dataRecord.size(), individualId);
                log.debug("Fields retrieved: {}", dataRecord.keySet());

                return new JSONObject(dataRecord);
            } else {
                log.error("Missing required parameters");
                throw new DataProviderExchangeException("MISSING_REQUIRED_PARAMETERS");
            }
        } catch (DataProviderExchangeException e) {
            log.error("Data Provider Exception: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error fetching data: {}", e.getMessage());
            throw new DataProviderExchangeException("ERROR_FETCHING_DATA_RECORD_FROM_TABLE");
        }
    }
}
