package com.recon.ingestion.parser;

import com.recon.common.dto.ParseErrorRecord;
import com.recon.common.dto.ParseResult;
import com.recon.common.dto.ReconStagingDto;
import com.recon.common.enums.DrCrIndicator;
import com.recon.common.enums.SourceSystem;
import com.recon.common.exception.ControlTotalMismatchException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class FlatFileParserService {

    private static final Logger log = LoggerFactory.getLogger(FlatFileParserService.class);

    public ParseResult parse(@NonNull Path filePath, @NonNull SourceSystem sourceSystem) {
        String name = filePath.getFileName().toString().toLowerCase();
        return switch (name.substring(name.lastIndexOf('.') + 1)) {
            case "dat" -> parsePipeDelimited(filePath, sourceSystem);
            case "csv" -> parseCsv(filePath, sourceSystem);
            default -> parseFixedWidth(filePath, sourceSystem);
        };
    }

    private ParseResult parsePipeDelimited(Path filePath, SourceSystem sourceSystem) {
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8)
                    .stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
            if (lines.size() < 3) {
                return new ParseResult(List.of(), List.of(new ParseErrorRecord("", "Missing HDR/DTL/TRL records")), 0, 0, BigDecimal.ZERO);
            }
            String header = lines.get(0);
            String trailer = lines.get(lines.size() - 1);
            List<String> dtlLines = lines.subList(1, lines.size() - 1);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                CompletableFuture<ParsedLines> dtlTask = CompletableFuture.supplyAsync(
                        () -> parseDtlLines(dtlLines, sourceSystem),
                        executor
                );
                ParsedLines parsed = dtlTask.join();
                validateControlTotals(header, trailer, parsed.controlTotal());
                return new ParseResult(parsed.records(), parsed.errors(), dtlLines.size(), parsed.records().size(), parsed.controlTotal());
            }
        } catch (ControlTotalMismatchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse pipe-delimited file", ex);
        }
    }

    private ParseResult parseCsv(Path filePath, SourceSystem sourceSystem) {
        List<ReconStagingDto> success = new ArrayList<>();
        List<ParseErrorRecord> errors = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        try (CSVParser parser = CSVParser.parse(filePath, StandardCharsets.UTF_8,
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            parser.forEach(rec -> {
                try {
                    ReconStagingDto dto = new ReconStagingDto(
                            rec.get("fileId"),
                            sourceSystem,
                            rec.get("recordId"),
                            LocalDate.parse(rec.get("reportDate")),
                            rec.get("entityId"),
                            rec.get("rconCode"),
                            new BigDecimal(rec.get("balance")),
                            DrCrIndicator.valueOf(rec.get("drCrInd")),
                            rec.get("currency"),
                            rec.isMapped("sourceRef") ? rec.get("sourceRef") : null,
                            rec.isMapped("comments") ? rec.get("comments") : null
                    );
                    success.add(dto);
                } catch (Exception ex) {
                    errors.add(new ParseErrorRecord(rec.toString(), ex.getMessage()));
                }
            });
            total = success.stream().map(ReconStagingDto::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse CSV file", ex);
        }

        return new ParseResult(success, errors, success.size() + errors.size(), success.size(), total);
    }

    private ParseResult parseFixedWidth(Path filePath, SourceSystem sourceSystem) {
        Charset charset = filePath.getFileName().toString().toLowerCase().endsWith(".txt")
                ? Charset.forName("Cp037") : StandardCharsets.UTF_8;

        List<ReconStagingDto> success = new ArrayList<>();
        List<ParseErrorRecord> errors = new ArrayList<>();

        try {
            for (String line : Files.readAllLines(filePath, charset)) {
                try {
                    if (line.length() < 66) {
                        errors.add(new ParseErrorRecord(line, "Fixed-width row too short"));
                        continue;
                    }
                    success.add(new ReconStagingDto(
                            slice(line, 0, 12).trim(),
                            sourceSystem,
                            slice(line, 12, 24).trim(),
                            LocalDate.parse(slice(line, 24, 32).trim(), DateTimeFormatter.BASIC_ISO_DATE),
                            slice(line, 32, 36).trim(),
                            slice(line, 36, 44).trim(),
                            new BigDecimal(slice(line, 44, 58).trim()),
                            DrCrIndicator.valueOf(slice(line, 58, 60).trim()),
                            slice(line, 60, 63).trim(),
                            null,
                            slice(line, 63, 66).trim()
                    ));
                } catch (Exception ex) {
                    errors.add(new ParseErrorRecord(line, ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse fixed-width file", ex);
        }

        BigDecimal total = success.stream().map(ReconStagingDto::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ParseResult(success, errors, success.size() + errors.size(), success.size(), total);
    }

    private ParsedLines parseDtlLines(List<String> lines, SourceSystem sourceSystem) {
        List<ReconStagingDto> success = new ArrayList<>();
        List<ParseErrorRecord> errors = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (String line : lines) {
            try {
                String[] parts = line.split("\\|", -1);
                if (parts.length < 14 || !"DTL".equals(parts[0])) {
                    errors.add(new ParseErrorRecord(line, "Malformed detail record"));
                    continue;
                }
                ReconStagingDto dto = new ReconStagingDto(
                        parts[1],
                        sourceSystem,
                        parts[1],
                        LocalDate.parse(parts[2], DateTimeFormatter.BASIC_ISO_DATE),
                        parts[3],
                        parts[7],
                        new BigDecimal(parts[8]),
                        DrCrIndicator.valueOf(parts[9]),
                        parts[10],
                        parts[12],
                        parts[13]
                );
                success.add(dto);
                total = total.add(dto.balance());
            } catch (Exception ex) {
                errors.add(new ParseErrorRecord(line, ex.getMessage()));
                log.warn("Unable to parse DTL row", ex);
            }
        }
        return new ParsedLines(success, errors, total);
    }

    private void validateControlTotals(String header, String trailer, BigDecimal computed) {
        String[] h = header.split("\\|");
        String[] t = trailer.split("\\|");
        if (h.length < 7 || t.length < 3) {
            throw new ControlTotalMismatchException("HDR/TRL structure is invalid");
        }
        BigDecimal headerAmount = new BigDecimal(h[6]);
        BigDecimal trailerAmount = new BigDecimal(t[2]);
        if (headerAmount.compareTo(computed) != 0 || trailerAmount.compareTo(computed) != 0) {
            throw new ControlTotalMismatchException("Control total mismatch. computed=" + computed);
        }
    }

    private String slice(String source, int begin, int end) {
        return source.substring(begin, Math.min(end, source.length()));
    }

    private record ParsedLines(List<ReconStagingDto> records, List<ParseErrorRecord> errors, BigDecimal controlTotal) {
    }
}

