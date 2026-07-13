package com.pronet.evaluator;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.pronet.evaluator.repository.*;
import java.io.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ExportService {
    private final EvaluationRepository evaluations;
    private final EmployeeRepository employees;

    byte[] xlsx(UUID id) {
        var e = evaluations.findById(id).orElseThrow();
        var emp = employees.findById(e.getEmployeeId()).orElseThrow();
        try (var wb = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var s = wb.createSheet("Evaluation");
            String[] head = {
                "Employee",
                "Period",
                "Level",
                "Formula",
                "Measured",
                "Threshold",
                "Status",
                "Coverage"
            };
            var h = s.createRow(0);
            for (int i = 0; i < head.length; i++) h.createCell(i).setCellValue(head[i]);
            int row = 1;
            for (var r : e.getResults()) {
                var x = s.createRow(row++);
                x.createCell(0).setCellValue(emp.getCanonicalEmail());
                x.createCell(1).setCellValue(e.getPeriod());
                x.createCell(2).setCellValue(e.getLevelCode());
                x.createCell(3).setCellValue(r.getFormula());
                x.createCell(4)
                        .setCellValue(
                                r.getMeasuredValue() == null
                                        ? ""
                                        : r.getMeasuredValue().toPlainString());
                x.createCell(5)
                        .setCellValue(
                                r.getThresholdValue() == null
                                        ? ""
                                        : r.getThresholdValue().toPlainString());
                x.createCell(6).setCellValue(r.getResultStatus().name());
                x.createCell(7).setCellValue(r.getCoverage());
            }
            for (int i = 0; i < head.length; i++) s.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    byte[] pdf(UUID id) {
        var e = evaluations.findById(id).orElseThrow();
        var emp = employees.findById(e.getEmployeeId()).orElseThrow();
        try (var out = new ByteArrayOutputStream()) {
            var doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(
                    new Paragraph(
                            "Engineering Career Evaluation",
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
            doc.add(new Paragraph(emp.getDisplayName() + " <" + emp.getCanonicalEmail() + ">"));
            doc.add(
                    new Paragraph(
                            e.getPeriod() + " · " + e.getLevelCode() + " · " + e.getStatus()));
            doc.add(new Paragraph(" "));
            var table = new PdfPTable(new float[] {4, 1, 1, 1});
            table.setWidthPercentage(100);
            for (String h : java.util.List.of("Formula", "Value", "Target", "Status"))
                table.addCell(h);
            for (var r : e.getResults()) {
                table.addCell(r.getFormula());
                table.addCell(Objects.toString(r.getMeasuredValue(), "—"));
                table.addCell(Objects.toString(r.getThresholdValue(), "—"));
                table.addCell(r.getResultStatus().name());
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
