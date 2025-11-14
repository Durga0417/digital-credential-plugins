package io.mosip.certify.postgresdataprovider.integration;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.postgresdataprovider.integration.repository.DataProviderRepository;
import io.mosip.certify.postgresdataprovider.integration.service.PostgresDataProviderPlugin;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class PostgresDataProviderPluginTest {

    @Mock
    DataProviderRepository dataProviderRepository;

    @InjectMocks
    PostgresDataProviderPlugin postgresDataProviderPlugin = new PostgresDataProviderPlugin();

    @Before
    public void setup() {
        LinkedHashMap<String, String> scopeQueryMapping = new LinkedHashMap<>();
        scopeQueryMapping.put("test_vc_ldp", "test_query");
        scopeQueryMapping.put("partner_scope", "select * from registration_receipt_data where id=:id");

        ReflectionTestUtils.setField(postgresDataProviderPlugin, "scopeQueryMapping", scopeQueryMapping);

        Map<String, Object> certData = Map.of(
                "id", "1234567",
                "name", "John Doe",
                "email", "john@test.com",
                "landArea", 100.24
        );

        Map<String, Object> partnerData = Map.of(
                "id", "999999",
                "statement", "SAMPLE_STATEMENT",
                "status", "ACTIVE"
        );


        Mockito.when(dataProviderRepository.fetchQueryResult("1234567", "test_query"))
                .thenReturn(certData);


        Mockito.when(dataProviderRepository.fetchQueryResult("999999", "select * from registration_receipt_data where id=:id"))
                .thenReturn(partnerData);


        Mockito.when(dataProviderRepository.fetchQueryResult("EMPTY", "test_query"))
                .thenReturn(Collections.emptyMap());


        Mockito.when(dataProviderRepository.fetchQueryResult("EXCEPTION", "test_query"))
                .thenThrow(new RuntimeException("ERROR_FETCHING_DATA_FROM_CERTIFY_DB"));
    }
    @Test
    public void fetchJsonDataWithValidIndividualId_thenPass() throws Exception {
        JSONObject jsonObject = postgresDataProviderPlugin.fetchData(
                Map.of("sub", "1234567", "scope", "test_vc_ldp")
        );

        Assert.assertNotNull(jsonObject);
        Assert.assertEquals("John Doe", jsonObject.get("name"));
        Assert.assertEquals("john@test.com", jsonObject.get("email"));
        Assert.assertEquals(100.24, jsonObject.get("landArea"));
    }

  @Test
    public void fetchJsonDataFromPartnerDb_thenPass() throws Exception {
        JSONObject jsonObject = postgresDataProviderPlugin.fetchData(
                Map.of("sub", "999999", "scope", "partner_scope")
        );

        Assert.assertNotNull(jsonObject);
        Assert.assertEquals("SAMPLE_STATEMENT", jsonObject.get("statement"));
        Assert.assertEquals("ACTIVE", jsonObject.get("status"));
    }

   @Test
    public void fetchJsonDataWithEmptyResult_thenFail() {
        try {
            postgresDataProviderPlugin.fetchData(
                    Map.of("sub", "EMPTY", "scope", "test_vc_ldp")
            );
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("NO_USER_DATA_FOUND_FOR_ID", e.getMessage());
        }
    }

    @Test
    public void fetchJsonDataWhenRepositoryThrowsException_thenFail() {
        try {
            postgresDataProviderPlugin.fetchData(
                    Map.of("sub", "EXCEPTION", "scope", "test_vc_ldp")
            );
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("ERROR_FETCHING_DATA_RECORD_FROM_TABLE", e.getMessage());
        }
    }

    @Test
    public void fetchJsonDataWithInvalidScope_thenFail() {
        try {
            postgresDataProviderPlugin.fetchData(
                    Map.of("sub", "1234567", "scope", "invalid_scope")
            );
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("MISSING_REQUIRED_PARAMETERS", e.getMessage());
        }
    }

   @Test
    public void fetchJsonDataWithMissingIndividualId_thenFail() {
        try {
            postgresDataProviderPlugin.fetchData(
                    Map.of("scope", "test_vc_ldp")
            );
        } catch (DataProviderExchangeException e) {
            Assert.assertEquals("MISSING_REQUIRED_PARAMETERS", e.getMessage());
        }
    }

}
