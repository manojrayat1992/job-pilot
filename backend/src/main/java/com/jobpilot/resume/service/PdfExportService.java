package com.jobpilot.resume.service;

import com.jobpilot.common.exception.BadRequestException;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Generates clean PDF resumes from parsed JSON data using OpenPDF.
 */
@Slf4j
@Service
public class PdfExportService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(33, 37, 41));
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 13, Font.BOLD, new Color(44, 62, 80));
    private static final Font SUBHEADING_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(52, 73, 94));
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(60, 60, 60));
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 9, Font.ITALIC, new Color(127, 140, 141));
    private static final Color ACCENT_COLOR = new Color(41, 128, 185);

    @SuppressWarnings("unchecked")
    public byte[] generatePdf(Map<String, Object> parsedJson) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 40, 40);
            PdfWriter.getInstance(document, baos);
            document.open();

            // ---- Header / Personal Info ----
            Map<String, Object> personalInfo = (Map<String, Object>) parsedJson.get("personalInfo");
            if (personalInfo != null) {
                String name = (String) personalInfo.getOrDefault("name", "");
                Paragraph namePara = new Paragraph(name, TITLE_FONT);
                namePara.setAlignment(Element.ALIGN_CENTER);
                document.add(namePara);

                StringBuilder contactLine = new StringBuilder();
                appendIfPresent(contactLine, personalInfo, "email");
                appendIfPresent(contactLine, personalInfo, "phone");
                appendIfPresent(contactLine, personalInfo, "location");
                appendIfPresent(contactLine, personalInfo, "linkedin");

                if (!contactLine.isEmpty()) {
                    Paragraph contactPara = new Paragraph(contactLine.toString().trim(), SMALL_FONT);
                    contactPara.setAlignment(Element.ALIGN_CENTER);
                    contactPara.setSpacingAfter(8);
                    document.add(contactPara);
                }

                addSeparator(document);
            }

            // ---- Summary ----
            String summary = (String) parsedJson.get("summary");
            if (summary != null && !summary.isBlank()) {
                addSectionHeading(document, "PROFESSIONAL SUMMARY");
                document.add(new Paragraph(summary, BODY_FONT));
                document.add(Chunk.NEWLINE);
            }

            // ---- Experience ----
            List<Map<String, Object>> experience = (List<Map<String, Object>>) parsedJson.get("experience");
            if (experience != null && !experience.isEmpty()) {
                addSectionHeading(document, "EXPERIENCE");
                for (Map<String, Object> exp : experience) {
                    String title = (String) exp.getOrDefault("title", "");
                    String company = (String) exp.getOrDefault("company", "");
                    String dates = "";
                    String start = (String) exp.get("startDate");
                    String end = (String) exp.get("endDate");
                    if ((start != null && !start.isBlank()) || (end != null && !end.isBlank())) {
                        dates = (start != null ? start : "") + " – " + (end != null && !end.isBlank() ? end : "Present");
                    } else if (exp.containsKey("dates") && exp.get("dates") != null) {
                        dates = (String) exp.get("dates");
                    } else if (exp.containsKey("duration") && exp.get("duration") != null) {
                        dates = (String) exp.get("duration");
                    } else {
                        dates = "Present";
                    }

                    Paragraph titlePara = new Paragraph(title + "  |  " + company, SUBHEADING_FONT);
                    document.add(titlePara);

                    Paragraph datesPara = new Paragraph(dates, SMALL_FONT);
                    datesPara.setSpacingAfter(4);
                    document.add(datesPara);

                    List<String> bullets = (List<String>) exp.get("bullets");
                    if (bullets != null) {
                        com.lowagie.text.List bulletList = new com.lowagie.text.List(com.lowagie.text.List.UNORDERED);
                        bulletList.setListSymbol("• ");
                        bulletList.setIndentationLeft(15);
                        for (String bullet : bullets) {
                            bulletList.add(new ListItem(bullet, BODY_FONT));
                        }
                        document.add(bulletList);
                    }
                    document.add(Chunk.NEWLINE);
                }
            }

            // ---- Education ----
            List<Map<String, Object>> education = (List<Map<String, Object>>) parsedJson.get("education");
            if (education != null && !education.isEmpty()) {
                addSectionHeading(document, "EDUCATION");
                for (Map<String, Object> edu : education) {
                    String degree = edu.getOrDefault("degree", "") + " in " + edu.getOrDefault("field", "");
                    String institution = (String) edu.getOrDefault("institution", "");
                    String dates = "";
                    String start = (String) edu.get("startDate");
                    String end = (String) edu.get("endDate");
                    if ((start != null && !start.isBlank()) || (end != null && !end.isBlank())) {
                        dates = (start != null ? start : "") + " – " + (end != null && !end.isBlank() ? end : "");
                    } else if (edu.containsKey("dates") && edu.get("dates") != null) {
                        dates = (String) edu.get("dates");
                    } else if (edu.containsKey("duration") && edu.get("duration") != null) {
                        dates = (String) edu.get("duration");
                    }

                    document.add(new Paragraph(degree.trim(), SUBHEADING_FONT));
                    Paragraph instPara = new Paragraph(institution + "  |  " + dates, SMALL_FONT);
                    instPara.setSpacingAfter(6);
                    document.add(instPara);
                }
                document.add(Chunk.NEWLINE);
            }

            // ---- Skills ----
            List<String> skills = (List<String>) parsedJson.get("skills");
            if (skills != null && !skills.isEmpty()) {
                addSectionHeading(document, "SKILLS");
                document.add(new Paragraph(String.join("  •  ", skills), BODY_FONT));
                document.add(Chunk.NEWLINE);
            }

            // ---- Projects ----
            List<Map<String, Object>> projects = (List<Map<String, Object>>) parsedJson.get("projects");
            if (projects != null && !projects.isEmpty()) {
                addSectionHeading(document, "PROJECTS");
                for (Map<String, Object> proj : projects) {
                    String projName = (String) proj.getOrDefault("name", "");
                    String projDesc = (String) proj.getOrDefault("description", "");
                    document.add(new Paragraph(projName, SUBHEADING_FONT));
                    document.add(new Paragraph(projDesc, BODY_FONT));
                    List<String> tech = (List<String>) proj.get("technologies");
                    if (tech != null && !tech.isEmpty()) {
                        Paragraph techPara = new Paragraph("Technologies: " + String.join(", ", tech), SMALL_FONT);
                        techPara.setSpacingAfter(6);
                        document.add(techPara);
                    }
                }
            }

            document.close();
            log.info("PDF generated: {} bytes", baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate PDF", e);
            throw new BadRequestException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private void addSectionHeading(Document document, String text) throws DocumentException {
        Paragraph heading = new Paragraph(text, HEADING_FONT);
        heading.setSpacingBefore(8);
        heading.setSpacingAfter(4);
        document.add(heading);
        addSeparator(document);
    }

    private void addSeparator(Document document) throws DocumentException {
        Paragraph line = new Paragraph();
        line.add(new Chunk("────────────────────────────────────────────────────────────────────────", 
                new Font(Font.HELVETICA, 6, Font.NORMAL, ACCENT_COLOR)));
        line.setSpacingAfter(6);
        document.add(line);
    }

    private void appendIfPresent(StringBuilder sb, Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof String s && !s.isBlank()) {
            if (!sb.isEmpty()) sb.append("  |  ");
            sb.append(s);
        }
    }
}
