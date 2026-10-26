package cl.uss.ca.directory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline DRY RUN of the academic matrix. Does not write MySQL or PURE.
 * Prints aggregate counts only; never prints names, emails or RUT.
 */
public final class DirectoryMatrixDryRun {
    private DirectoryMatrixDryRun() {
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args.length == 0 ? "docs/Matriz academica filtrada.xlsx" : args[0]);
        if (!file.isAbsolute()) {
            Path fromBackend = Path.of("..").resolve(file).normalize();
            if (Files.exists(fromBackend)) {
                file = fromBackend;
            }
        }
        if (!Files.exists(file)) {
            throw new IllegalStateException("Matrix file not found: " + file.toAbsolutePath());
        }
        byte[] pepper = "dry-run-directory-pepper".getBytes(StandardCharsets.UTF_8);
        byte[] aes = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        DirectoryRutCrypto crypto = new DirectoryRutCrypto(pepper, aes);
        DirectoryExcelParser parser = new DirectoryExcelParser();
        DirectoryRowMapper mapper = new DirectoryRowMapper(crypto);
        DirectoryImportPlanner planner = new DirectoryImportPlanner();
        var parsed = parser.parse(Files.readAllBytes(file));
        List<DirectoryIssue> issues = new ArrayList<>();
        List<DirectoryIncomingRow> incoming = mapper.map(parsed.rows(), issues);
        DirectoryImportPlan plan = planner.plan(incoming, new DirectoryCatalog(), issues);
        Map<String, Integer> byCode = new LinkedHashMap<>();
        for (DirectoryIssue issue : plan.issues) {
            if ("UNMAPPED_HEADER".equals(issue.code)) {
                continue;
            }
            byCode.merge(issue.code, 1, Integer::sum);
        }
        System.out.println("DIRECTORY_DRY_RUN_START");
        System.out.println("fileExists=true");
        System.out.println("sheet=" + parsed.sheetName());
        System.out.println("rowCount=" + parsed.rows().size());
        System.out.println("created=" + plan.created);
        System.out.println("updated=" + plan.updated);
        System.out.println("unchanged=" + plan.unchanged);
        System.out.println("reactivated=" + plan.reactivated);
        System.out.println("rejected=" + plan.rejected);
        System.out.println("wouldInactivate=" + plan.wouldInactivate);
        System.out.println("censusReliable=" + plan.censusReliable);
        System.out.println("issueCount=" + byCode.values().stream().mapToInt(Integer::intValue).sum());
        System.out.println("unmappedHeaders=" + parsed.unmappedHeaders().size());
        for (Map.Entry<String, Integer> entry : byCode.entrySet()) {
            System.out.println("issue." + entry.getKey() + "=" + entry.getValue());
        }
        System.out.println("DIRECTORY_DRY_RUN_END");
    }
}
