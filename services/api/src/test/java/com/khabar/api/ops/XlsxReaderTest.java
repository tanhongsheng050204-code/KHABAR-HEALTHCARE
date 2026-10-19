package com.khabar.api.ops;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The small .xlsx reader behind the patient import. */
class XlsxReaderTest {

    static byte[] xlsx(String shared, String sheet) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write(shared.getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(sheet.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    static final String NS = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"";

    @Test
    void readsSharedStringsInlineStringsAndNumbersByColumn() throws IOException {
        String shared = "<sst " + NS + "><si><t>name</t></si><si><t>phone</t></si><si><t>Rahim bin Ali</t></si></sst>";
        String sheet = "<worksheet " + NS + "><sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>"
                + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"C2\"><v>60127000002</v></c></row>"
                + "<row r=\"3\"><c r=\"A3\" t=\"inlineStr\"><is><t>Tan Ah Kow</t></is></c><c r=\"B3\"><v>1.2345E9</v></c></row>"
                + "</sheetData></worksheet>";
        List<List<String>> rows = XlsxReader.read(xlsx(shared, sheet));
        assertThat(rows.get(0)).containsExactly("name", "phone");
        assertThat(rows.get(1)).containsExactly("Rahim bin Ali", "", "60127000002");
        assertThat(rows.get(2)).containsExactly("Tan Ah Kow", "1234500000");
    }

    @Test
    void aFileWithADoctypeIsRefused() throws IOException {
        String evil = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><sst " + NS + "><si><t>&e;</t></si></sst>";
        assertThatThrownBy(() -> XlsxReader.read(xlsx(evil, "<worksheet " + NS + "><sheetData/></worksheet>"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> XlsxReader.read("not a zip".getBytes(StandardCharsets.UTF_8))).isInstanceOf(IOException.class);
    }
}
