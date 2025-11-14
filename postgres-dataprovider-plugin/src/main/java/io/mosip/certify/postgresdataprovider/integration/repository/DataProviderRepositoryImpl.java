package io.mosip.certify.postgresdataprovider.integration.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class DataProviderRepositoryImpl implements DataProviderRepository {

    private static final Logger log = LoggerFactory.getLogger(DataProviderRepositoryImpl.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${partner.db.url}")
    private String partnerDbUrl;

    @Value("${partner.db.username}")
    private String partnerDbUsername;

    @Value("${partner.db.password}")
    private String partnerDbPassword;

    @Value("${partner.db.tables}")
    private String partnerDbTables;

    @Override
    public Map<String, Object> fetchQueryResult(String id, String queryString) {
        Map<String, Object> resultMap = new HashMap<>();

        List<String> partnerTables = Arrays.asList(partnerDbTables.split(","));
        boolean fromPartnerDb = partnerTables.stream()
                .anyMatch(t -> queryString.toLowerCase().contains(t.trim().toLowerCase()));

        log.info("Executing data fetch for ID [{}] on {} DB",
                id, fromPartnerDb ? "Partner DB" : "Certify DB");

        if (fromPartnerDb) {

            try (Connection conn = DriverManager.getConnection(partnerDbUrl, partnerDbUsername, partnerDbPassword)) {
                String sql = queryString.replace(":id", "?");

                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, id);

                    try (ResultSet rs = pstmt.executeQuery()) {
                        ResultSetMetaData rsmd = rs.getMetaData();
                        if (rs.next()) {
                            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                                resultMap.put(rsmd.getColumnName(i).toLowerCase(), rs.getObject(i));
                            }
                            log.info("Fetched {} columns from Partner DB for ID [{}]", resultMap.size(), id);
                        } else {
                            log.error("No user data found in Partner DB for ID [{}]", id);
                            throw new RuntimeException("NO_USER_DATA_FOUND_FOR_ID");
                        }
                    }
                }
            } catch (SQLException e) {
                log.error("Database error fetching user data from Partner DB for ID [{}]: {}", id, e.getMessage());
                throw new RuntimeException("DATABASE_ERROR_FETCHING_USER_DATA", e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.error("Unexpected error fetching user data from Partner DB for ID [{}]: {}", id, e.getMessage());
                throw new RuntimeException("ERROR_FETCHING_DATA_FROM_PARTNER_DB", e);
            }
        } else {

            try {
                Query query = entityManager.createNativeQuery(queryString, Tuple.class);
                query.setParameter("id", id);
                List<Tuple> tuples = query.getResultList();

                if (!tuples.isEmpty()) {
                    Tuple tuple = tuples.get(0);
                    for (var element : tuple.getElements()) {
                        String alias = element.getAlias();
                        if (alias != null) {
                            resultMap.put(alias.toLowerCase(), tuple.get(element));
                        }
                    }
                    log.info("Fetched {} columns from Certify DB for ID [{}]", resultMap.size(), id);
                } else {
                    log.error("No data found in Certify DB for ID [{}]", id);
                    throw new RuntimeException("NO_USER_DATA_FOUND_FOR_ID");
                }
            } catch (RuntimeException e) {
                if ("NO_USER_DATA_FOUND_FOR_ID".equals(e.getMessage())) {
                    throw e;
                }
                log.error("Error fetching data from Certify DB for ID [{}]: {}", id, e.getMessage());
                throw new RuntimeException("ERROR_FETCHING_DATA_FROM_CERTIFY_DB", e);
            } catch (Exception e) {
                log.error("Unexpected error fetching data from Certify DB for ID [{}]: {}", id, e.getMessage());
                throw new RuntimeException("ERROR_FETCHING_DATA_FROM_CERTIFY_DB", e);
            }
        }

        return resultMap;
    }
}
