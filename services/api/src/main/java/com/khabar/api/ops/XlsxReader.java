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

    static final int MAX_PART = 8 * 1024 * 1024;
    static final int MAX_TOTAL = 32 * 1024 * 1024;

    private XlsxReader() {
    }

    static List<List<String>> read(byte[] xlsx) throws IOException {
        Map<String, byte[]> parts = unzip(xlsx);
        List<String> shared = new ArrayList<>();
        if (parts.containsKey("xl/sharedStrings.xml")) {
            Document doc = parse(parts.get("xl/sharedStrings.xml"));
            NodeList items = doc.getElementsByTagNameNS("*", "si");
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
        for (int r = 0; r < rowNodes.getLength(); r++) {
            Element row = (Element) rowNodes.item(r);
            NodeList cells = row.getElementsByTagNameNS("*", "c");
            TreeMap<Integer, String> byColumn = new TreeMap<>();
            for (int c = 0; c < cells.getLength(); c++) {
                Element cell = (Element) cells.item(c);
                int column = column(cell.getAttribute("r"), c);
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

    /** "12" not "12.0", and a phone number stored as 60123456789.0 keeps its digits. */
    private static String number(String raw) {
        String t = raw.trim();
        try {
            if (t.contains("E") || t.contains("e")) {
                return new java.math.BigDecimal(t).toPlainString();
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
        for (char ch : ref.toCharArray()) {
            if (Character.isLetter(ch)) {
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
