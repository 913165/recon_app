package com.recon.storage.bulk;

import com.recon.common.dto.ReconStagingDto;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostgresBulkLoader {

    private final DataSource dataSource;

    public int bulkInsertStaging(@NonNull List<ReconStagingDto> records) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            CopyManager copyManager = conn.unwrap(PGConnection.class).getCopyAPI();

            StringBuilder sb = new StringBuilder();
            for (ReconStagingDto r : records) {
                sb.append(toCsvLine(r)).append("\n");
            }

            String sql = """
                    COPY recon_staging (file_id, source_system, record_id, report_date,
                    entity_id, rcon_code, balance, dr_cr_ind, currency, source_ref, comments)
                    FROM STDIN WITH CSV
                    """;

            try (InputStream is = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8))) {
                return (int) copyManager.copyIn(sql, is);
            }
        }
    }

    private String toCsvLine(ReconStagingDto r) {
        return String.join(",",
                safe(r.fileId()),
                safe(r.sourceSystem().name()),
                safe(r.recordId()),
                safe(r.reportDate().toString()),
                safe(r.entityId()),
                safe(r.rconCode()),
                safe(r.balance().toPlainString()),
                safe(r.drCrInd().name()),
                safe(r.currency()),
                safe(r.sourceRef()),
                safe(r.comments())
        );
    }

    private String safe(String input) {
        if (input == null) {
            return "";
        }
        return '"' + input.replace("\"", "\"\"") + '"';
    }

    public BigDecimal summarizeAmount(@NonNull List<ReconStagingDto> records) {
        return records.stream().map(ReconStagingDto::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

