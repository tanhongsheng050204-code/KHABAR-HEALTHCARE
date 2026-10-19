package com.khabar.api.ops;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads the first worksheet of an Excel (.xlsx) file into rows of text, enough for a patient list: shared and inline
 * strings and plain numbers. No macros, formulas are read as their saved value. The file is a zip of XML; external
 * entities are refused and sizes are capped, so a crafted file cannot read the server's files or exhaust memory.
 */
final class XlsxReader {

    /** A 500-patient list is well under these; anything bigger is refused before or while it is read. */
    static final int MAX_PART = 2 * 1024 * 1024;
    static final int MAX_TOTAL = 4 * 1024 * 1024;
    static final int MAX_ROWS = 502;
    static final int MAX_COLUMNS = 64;
    static final int MAX_SHARED = 20_000;

    private XlsxReader() {
    }

    static List<List<String>> read(byte[] xlsx) throws IOException {
        Map<String, byte[]> parts = unzip(xlsx);
        List<String> shared = new ArrayList<>();
        if (parts.containsKey("xl/sharedStrings.xml")) {
            Document doc = parse(parts.get("xl/sharedStrings.xml"));
            NodeList items = doc.getElementsByTagNameNS("*", "si");
            if (items.getLength() > MAX_SHARED) {
                throw new IOException("The spreadsheet is too large.");
            }
            for (int i = 0; i < items.getLength(); i++) {
                shared.add(text((Element) items.item(i)));
            }
        }
        byte[] sheet = parts.get("xl/worksheets/sheet1.xml");
        if (sheet == null) {
            sheet = parts.entrySet().stream().filter(e -> e.getKey().startsWith("xl/worksheets/sheet")).map(Map.Entry::getValue).findFirst()
                    .orElseThrow(() -> new IOException("No worksheet in this file."));
        }
        Document doc = parse(sheet);
        NodeList rowNodes = doc.getElementsByTagNameNS("*", "row");
        List<List<String>> rows = new ArrayList<>();
        if (rowNodes.getLength() > MAX_ROWS) {
            throw new IOException("Import up to 500 patients at a time.");
        }
        for (int r = 0; r < rowNodes.getLength(); r++) {
            Element row = (Element) rowNodes.item(r);
            NodeList cells = row.getElementsByTagNameNS("*", "c");
            if (cells.getLength() > MAX_COLUMNS) {
                throw new IOException("A row has more than " + MAX_COLUMNS + " columns.");
            }
            TreeMap<Integer, String> byColumn = new TreeMap<>();
            for (int c = 0; c < cells.getLength(); c++) {
                Element cell = (Element) cells.item(c);
                int column = column(cell.getAttribute("r"), c);
                if (column < 0 || column >= MAX_COLUMNS) {
                    // Columns past the first 64 are never part of a patient list.
                    continue;
                }
                String type = cell.getAttribute("t");
                String value;
                if ("inlineStr".equals(type)) {
                    NodeList is = cell.getElementsByTagNameNS("*", "is");
                    value = is.getLength() == 0 ? "" : text((Element) is.item(0));
                } else {
                    NodeList v = cell.getElementsByTagNameNS("*", "v");
                    String raw = v.getLength() == 0 ? "" : v.item(0).getTextContent();
                    if ("s".equals(type)) {
                        int index = Integer.parseInt(raw.trim());
                        value = index >= 0 && index < shared.size() ? shared.get(index) : "";
                    } else {
                        value = number(raw);
                    }
                }
                byColumn.put(column, value);
            }
            List<String> out = new ArrayList<>();
            int last = byColumn.isEmpty() ? -1 : byColumn.lastKey();
            for (int i = 0; i <= last; i++) {
                out.add(byColumn.getOrDefault(i, ""));
            }
            rows.add(out);
        }
        return rows;
    }

    /** "12" not "12.0", and a phone number stored as 6.0127E10 keeps its digits. Huge exponents stay as written. */
    private static String number(String raw) {
        String t = raw.trim();
        if (t.length() > 32) {
            return t.substring(0, 32);
        }
        try {
            if (t.contains("E") || t.contains("e")) {
                java.math.BigDecimal value = new java.math.BigDecimal(t);
                if (value.precision() - value.scale() > 20 || value.scale() > 20) {
                    return t;
                }
                return value.toPlainString();
            }
            if (t.endsWith(".0")) {
                return t.substring(0, t.length() - 2);
            }
        } catch (NumberFormatException ignored) {
            // Not a number: keep the text.
        }
        return t;
    }

    private static int column(String ref, int fallback) {
        if (ref == null || ref.isEmpty()) {
            return fallback;
        }
        int col = 0;
        int letters = 0;
        for (char ch : ref.toCharArray()) {
            if (ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z') {
                if (++letters > 3) {
                    return -1;
                }
                col = col * 26 + (Character.toUpperCase(ch) - 'A' + 1);
            } else {
                break;
            }
        }
        return col == 0 ? fallback : col - 1;
    }

    private static String text(Element element) {
        NodeList ts = element.getElementsByTagNameNS("*", "t");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ts.getLength(); i++) {
            sb.append(ts.item(i).getTextContent());
        }
        return sb.toString();
    }

    private static Map<String, byte[]> unzip(byte[] xlsx) throws IOException {
        Map<String, byte[]> parts = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!(name.equals("xl/sharedStrings.xml") || name.startsWith("xl/worksheets/sheet"))) {
                    continue;
                }
                byte[] data = zip.readNBytes(MAX_PART + 1);
                if (data.length > MAX_PART || (total += data.length) > MAX_TOTAL) {
                    throw new IOException("The spreadsheet is too large.");
                }
                parts.put(name, data);
            }
        }
        if (parts.isEmpty()) {
            throw new IOException("This is not an Excel .xlsx file.");
        }
        return parts;
    }

    private static Document parse(byte[] xml) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IOException("The spreadsheet could not be read.");
        }
    }
}
