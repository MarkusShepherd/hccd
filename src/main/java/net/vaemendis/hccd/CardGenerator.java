package net.vaemendis.hccd;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CardGenerator {

    private static final String GENERATED_SUFFIX = "-GENERATED";

    private static String recordToString(CSVRecord record, Template template, FalseValue falseValue) {
        if (falseValue == null || !falseValue.isEnabled())
            return template.execute(record.toMap());

        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, String> e : record.toMap().entrySet())
            result.put(
                    e.getKey(),
                    e.getValue().equals(falseValue.value) ? false : e.getValue());
        return template.execute(result);
    }

    private static List<String> recordToStrings(
            CSVRecord record,
            Template template,
            FalseValue falseValue,
            CopiesValue copiesValue
    ) {
        String copiesStr = copiesValue != null && copiesValue.isEnabled() && record.isMapped(copiesValue.value)
                ? record.get(copiesValue.value)
                : null;
        int copies = NumberUtils.toInt(copiesStr, 1);
        return Collections.nCopies(copies, recordToString(record, template, falseValue));
    }

    private static String[] recordsToStrings(
            List<CSVRecord> records,
            Template template,
            FalseValue falseValue,
            CopiesValue copiesValue
    ) {
        return records.stream()
                .flatMap(record -> recordToStrings(record, template, falseValue, copiesValue).stream())
                .toArray(String[]::new);
    }

    public static void generateCards(WatchedFiles projectFiles, UserConfiguration config) throws IOException {
        Hccd.log("Generating card sheet file...");
        if (!projectFiles.getCsvFile().isFile()) {
            Hccd.log("CSV file does not exist. Generation aborted.");
            return;
        }

        String root = FilenameUtils.getBaseName(projectFiles.getHtmlFile().getName());
        File target = new File(projectFiles.getParentDir(), root + GENERATED_SUFFIX + ".html");

        String html = FileUtils.readFileToString(projectFiles.getHtmlFile());
        Document doc = Jsoup.parse(html);
        String card = doc.select(".card").first().outerHtml();
        Template template = Mustache.compiler().escapeHTML(false).defaultValue("[NOT FOUND]").compile(card);
        List<CSVRecord> records = getData(projectFiles.getCsvFile(), config);

        StringBuilder sb = new StringBuilder();
        writeHeader(sb, projectFiles.getCssFile().getName());

        int rows = config.getGridRowNumber();
        int cols = config.getGridColNumber();
        int page = rows * cols;

        final String[] allCards = recordsToStrings(records, template, config.getFalseValue(), config.getCopiesValue());

        // filter cards
        List<Integer> filter = config.getCardFilter();
        final String[] cards = filter.isEmpty()
                ? allCards
                : filter.stream()
                .map(i -> 1 <= i && i <= allCards.length ? allCards[i - 1] : null)
                .filter(Objects::nonNull)
                .toArray(String[]::new);

        for (int i = 0; i < cards.length; i++) {
            if (i % page == 0)
                sb.append("<table class=\"page\">");
            if (i % cols == 0)
                sb.append("<tr>");
            sb.append("<td>");
            sb.append(cards[i]);
            sb.append("</td>");
            if ((i + 1) % cols == 0 || i + 1 == cards.length)
                sb.append("</tr>");
            if ((i + 1) % page == 0 || i + 1 == cards.length)
                sb.append("</table>");
        }

        writeFooter(sb);

        FileUtils.writeStringToFile(target, sb.toString());
        Hccd.log("Card sheet file written to " + target.getPath());
    }

    private static void writeHeader(StringBuilder sb, String cssFilePath) {
        sb.append("<!doctype html>" +
                "<html>" +
                "<head>" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"></meta>" +
                "<link rel=\"stylesheet\" type=\"text/css\" href=\"");
        sb.append(cssFilePath);
        sb.append("\"><style>" +
                "body {\n" +
                "    margin: 10mm;\n" +
                "}\n" +
                "\n" +
                "table.page {\n" +
                "    border: 0mm;\n" +
                "    page-break-after: always;    \n" +
                "    border-spacing: 0;\n" +
                "    border-collapse: collapse;\n" +
                "    display:block;        \n" +
                "    clear: both;\n" +
                "}\n" +
                "\n" +
                "table.page td {\n" +
                "    padding: 0;\n" +
                "}\n" +
                "</style>\n</head><body>");
    }

    private static void writeFooter(StringBuilder sb) {
        sb.append("</body></html>");
    }

    private static List<CSVRecord> getData(File csvFile, UserConfiguration config) throws IOException {
        List<CSVRecord> recordList = new ArrayList<>();
        Iterable<CSVRecord> records;
        if (config.useExcelFormat()) {
            try (Reader reader = new InputStreamReader(new BOMInputStream(new FileInputStream(csvFile)), StandardCharsets.UTF_8)) {
                records = CSVFormat.EXCEL.withDelimiter(config.getDelimiter()).withFirstRecordAsHeader().parse(reader);
                for (CSVRecord record : records) {
                    recordList.add(record);
                }
            }
        } else {
            try (Reader in = new FileReader(csvFile)) {
                records = CSVFormat.RFC4180.withDelimiter(config.getDelimiter()).withFirstRecordAsHeader().parse(in);
                for (CSVRecord record : records) {
                    recordList.add(record);
                }
            }
        }
        return recordList;
    }

}
