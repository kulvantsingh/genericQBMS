package com.questionbank.service;

import com.questionbank.exception.UnsupportedImportFileException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;

@Service
@Slf4j
public class QuestionImportTextExtractorService {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final String IMAGE_MARKER_PREFIX = "[[IMG:";
    private static final String IMAGE_MARKER_SUFFIX = "]]";
    private static final String MATH_MARKER_PREFIX = "[[MATH:";
    private static final Map<String, String> SYMBOL_TO_LATEX = createSymbolToLatexMap();

    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UnsupportedImportFileException("A non-empty file is required for import");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new UnsupportedImportFileException("Uploaded file name is missing");
        }

        String extension = extensionOf(fileName);
        try {
            byte[] content = file.getBytes();
            return switch (extension) {
                case "docx" -> extractDocx(content);
                case "doc" -> extractDoc(content);
                case "txt" -> extractTxt(content);
                default -> throw new UnsupportedImportFileException(
                    "Unsupported file type: ." + extension + ". Supported formats are .doc, .docx, and .txt");
            };
        } catch (IOException ex) {
            throw new UnsupportedImportFileException("Failed to read uploaded file");
        }
    }

    String extractDocx(byte[] content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            List<String> lines = new ArrayList<>();
            appendBodyElements(lines, document.getBodyElements());
            return String.join("\n", lines).trim();
        }
    }

    String extractDoc(byte[] content) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(content));
             WordExtractor extractor = new WordExtractor(document)) {
            return normalizeExtractedText(extractor.getText());
        }
    }

    String extractTxt(byte[] content) {
        try {
            return normalizeExtractedText(decodeStrict(content, StandardCharsets.UTF_8));
        } catch (CharacterCodingException utf8Ex) {
            log.info("UTF-8 decoding failed for text import, falling back to Windows-1252");
            return normalizeExtractedText(new String(content, WINDOWS_1252));
        }
    }

    private void appendBodyElements(List<String> lines, List<IBodyElement> bodyElements) {
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph) {
                appendParagraph(lines, paragraph);
            } else if (element instanceof XWPFTable table) {
                appendTable(lines, table);
            }
        }
    }

    private void appendParagraph(List<String> lines, XWPFParagraph paragraph) {
        String text = normalizeExtractedText(renderParagraph(paragraph));
        if (!text.isBlank()) {
            lines.add(text);
        } else {
            lines.add("");
        }
    }

    private void appendTable(List<String> lines, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                appendBodyElements(lines, cell.getBodyElements());
            }
        }
    }

    private String renderParagraph(XWPFParagraph paragraph) {
        String paragraphXml = paragraph.getCTP().xmlText();
        if (paragraphXml.contains("oMath")) {
            String xmlRendered = renderParagraphFromXml(paragraphXml);
            if (!xmlRendered.isBlank()) {
                return xmlRendered;
            }
        }

        List<String> parts = new ArrayList<>();
        List<XWPFRun> runs = paragraph.getRuns();
        int runIndex = 0;

        NodeList children = paragraph.getCTP().getDomNode().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String childName = localName(child);
            if ("r".equals(childName)) {
                if (runIndex < runs.size()) {
                    appendRun(parts, runs.get(runIndex));
                }
                runIndex++;
                continue;
            }

            if ("oMath".equals(childName) || "oMathPara".equals(childName)) {
                String latex = convertMathNodeToLatex(child);
                if (!latex.isBlank()) {
                    parts.add(wrapLatexForStorage(latex, "oMathPara".equals(childName)));
                }
            }
        }

        while (runIndex < runs.size()) {
            appendRun(parts, runs.get(runIndex));
            runIndex++;
        }

        if (parts.isEmpty()) {
            return paragraph.getText();
        }
        return String.join(" ", parts).trim();
    }

    private String renderParagraphFromXml(String paragraphXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Node root = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(paragraphXml)))
                .getDocumentElement();

            List<String> parts = new ArrayList<>();
            for (Node child : elementChildren(root)) {
                String childName = localName(child);
                if ("r".equals(childName)) {
                    String runText = extractWordRunText(child);
                    if (!runText.isBlank()) {
                        parts.add(runText);
                    }
                } else if ("oMath".equals(childName) || "oMathPara".equals(childName)) {
                    String latex = convertMathNodeToLatex(child);
                    if (!latex.isBlank()) {
                        parts.add(wrapLatexForStorage(latex, "oMathPara".equals(childName)));
                    }
                }
            }
            return String.join(" ", parts).trim();
        } catch (Exception ex) {
            log.debug("Could not parse paragraph XML for equation extraction", ex);
            return "";
        }
    }

    private String extractWordRunText(Node runNode) {
        StringBuilder text = new StringBuilder();
        collectWordText(runNode, text);
        return normalizeExtractedText(text.toString());
    }

    private void collectWordText(Node node, StringBuilder text) {
        for (Node child : elementChildren(node)) {
            String childName = localName(child);
            if ("t".equals(childName)) {
                text.append(child.getTextContent());
            } else {
                collectWordText(child, text);
            }
        }
    }

    private void appendRun(List<String> parts, XWPFRun run) {
        String runText = normalizeExtractedText(run.text());
        if (!runText.isBlank()) {
            parts.add(runText);
        }
        for (XWPFPicture picture : run.getEmbeddedPictures()) {
            XWPFPictureData pictureData = picture.getPictureData();
            if (pictureData != null && pictureData.getData() != null && pictureData.getData().length > 0) {
                parts.add(imageMarkerFor(pictureData));
            }
        }
    }

    private String wrapLatexForStorage(String latex, boolean displayMode) {
        String encodedLatex = Base64.getEncoder().encodeToString(latex.getBytes(StandardCharsets.UTF_8));
        return MATH_MARKER_PREFIX + (displayMode ? "display" : "inline") + ":" + encodedLatex + "]]";
    }

    private String convertMathNodeToLatex(Node node) {
        if (node == null) {
            return "";
        }
        String localName = localName(node);
        if ("oMathPara".equals(localName)) {
            List<String> parts = new ArrayList<>();
            for (Node child : elementChildren(node)) {
                String latex = convertMathNodeToLatex(child);
                if (!latex.isBlank()) {
                    parts.add(latex);
                }
            }
            return String.join(" ", parts).trim();
        }
        return convertMathChildren(node);
    }

    private String convertMathChildren(Node node) {
        StringBuilder latex = new StringBuilder();
        for (Node child : elementChildren(node)) {
            latex.append(convertMathElement(child));
        }
        return normalizeLatexSpacing(latex.toString());
    }

    private String convertMathElement(Node node) {
        if (node == null) {
            return "";
        }

        String localName = localName(node);

        return switch (localName) {
            case "r" -> convertMathRun(node);
            case "t" -> escapeLatexText(node.getTextContent());
            case "sSub" -> renderScript(node, "e", "sub", null);
            case "sSup" -> renderScript(node, "e", null, "sup");
            case "sSubSup" -> renderScript(node, "e", "sub", "sup");
            case "f" -> "\\frac{" + convertChild(node, "num") + "}{" + convertChild(node, "den") + "}";
            case "rad" -> renderRadical(node);
            case "nary" -> renderNary(node);
            case "d" -> renderDelimited(node);
            case "acc" -> renderAccent(node);
            case "bar" -> "\\overline{" + convertChild(node, "e") + "}";
            case "func" -> convertChild(node, "fName") + " " + convertChild(node, "e");
            case "limLow" -> renderScript(node, "e", "lim", null);
            case "limUpp" -> renderScript(node, "e", null, "lim");
            case "e", "num", "den", "sub", "sup", "deg", "fName", "lim" -> convertMathChildren(node);
            case "oMath", "oMathPara" -> convertMathNodeToLatex(node);
            default -> convertMathChildren(node);
        };
    }

    private String convertMathRun(Node node) {
        StringBuilder text = new StringBuilder();
        for (Node child : elementChildren(node)) {
            if ("t".equals(child.getLocalName())) {
                text.append(escapeLatexText(child.getTextContent()));
            }
        }
        return text.toString();
    }

    private String renderScript(Node node, String baseName, String subName, String supName) {
        String base = groupLatex(convertChild(node, baseName));
        StringBuilder latex = new StringBuilder(base);
        if (subName != null) {
            latex.append("_{").append(convertChild(node, subName)).append("}");
        }
        if (supName != null) {
            latex.append("^{").append(convertChild(node, supName)).append("}");
        }
        return latex.toString();
    }

    private String renderRadical(Node node) {
        String degree = convertChild(node, "deg");
        String expression = convertChild(node, "e");
        if (degree.isBlank()) {
            return "\\sqrt{" + expression + "}";
        }
        return "\\sqrt[" + degree + "]{" + expression + "}";
    }

    private String renderNary(Node node) {
        Node naryPr = firstChild(node, "naryPr");
        String operator = "\\sum";
        if (naryPr != null) {
            Node chr = firstChild(naryPr, "chr");
            String value = attributeValue(chr, "val");
            if (value != null && !value.isBlank()) {
                operator = mapOperatorToLatex(value);
            }
        }
        String lower = convertChild(node, "sub");
        String upper = convertChild(node, "sup");
        String expression = convertChild(node, "e");

        StringBuilder latex = new StringBuilder(operator);
        if (!lower.isBlank()) {
            latex.append("_{").append(lower).append("}");
        }
        if (!upper.isBlank()) {
            latex.append("^{").append(upper).append("}");
        }
        if (!expression.isBlank()) {
            latex.append(" ").append(expression);
        }
        return latex.toString();
    }

    private String renderDelimited(Node node) {
        Node dPr = firstChild(node, "dPr");
        String begin = "(";
        String end = ")";
        if (dPr != null) {
            String beginValue = attributeValue(firstChild(dPr, "begChr"), "val");
            String endValue = attributeValue(firstChild(dPr, "endChr"), "val");
            if (beginValue != null && !beginValue.isBlank()) {
                begin = beginValue;
            }
            if (endValue != null && !endValue.isBlank()) {
                end = endValue;
            }
        }

        List<String> expressions = new ArrayList<>();
        for (Node child : childrenByLocalName(node, "e")) {
            expressions.add(convertMathChildren(child));
        }
        return "\\left" + escapeDelimiter(begin) + " " + String.join(" ", expressions) + " \\right" + escapeDelimiter(end);
    }

    private String renderAccent(Node node) {
        Node accPr = firstChild(node, "accPr");
        String command = "\\hat";
        if (accPr != null) {
            String accentValue = attributeValue(firstChild(accPr, "chr"), "val");
            if ("¯".equals(accentValue)) {
                command = "\\overline";
            } else if ("→".equals(accentValue)) {
                command = "\\vec";
            } else if ("˙".equals(accentValue)) {
                command = "\\dot";
            }
        }
        return command + "{" + convertChild(node, "e") + "}";
    }

    private String convertChild(Node node, String localName) {
        Node child = firstChild(node, localName);
        return child == null ? "" : convertMathChildren(child);
    }

    private Node firstChild(Node node, String localName) {
        for (Node child : elementChildren(node)) {
            if (localName.equals(localName(child))) {
                return child;
            }
        }
        return null;
    }

    private List<Node> childrenByLocalName(Node node, String localName) {
        List<Node> matches = new ArrayList<>();
        for (Node child : elementChildren(node)) {
            if (localName.equals(localName(child))) {
                matches.add(child);
            }
        }
        return matches;
    }

    private List<Node> elementChildren(Node node) {
        List<Node> children = new ArrayList<>();
        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                children.add(child);
            }
        }
        return children;
    }

    private String attributeValue(Node node, String localName) {
        if (node == null || node.getAttributes() == null) {
            return null;
        }
        for (int i = 0; i < node.getAttributes().getLength(); i++) {
            Node attribute = node.getAttributes().item(i);
            if (localName.equals(localName(attribute))) {
                return attribute.getNodeValue();
            }
        }
        return null;
    }

    private String localName(Node node) {
        if (node == null) {
            return "";
        }
        if (node.getLocalName() != null) {
            return node.getLocalName();
        }
        String nodeName = node.getNodeName();
        int colonIndex = nodeName.indexOf(':');
        return colonIndex >= 0 ? nodeName.substring(colonIndex + 1) : nodeName;
    }

    private String groupLatex(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("\\") || trimmed.length() == 1) {
            return trimmed;
        }
        return "{" + trimmed + "}";
    }

    private String escapeDelimiter(String value) {
        return switch (value) {
            case "{" -> "\\{";
            case "}" -> "\\}";
            case "[" -> "[";
            case "]" -> "]";
            default -> value;
        };
    }

    private String mapOperatorToLatex(String value) {
        return switch (value) {
            case "∫" -> "\\int";
            case "∑" -> "\\sum";
            case "∏" -> "\\prod";
            case "⋂" -> "\\bigcap";
            case "⋃" -> "\\bigcup";
            default -> escapeLatexText(value);
        };
    }

    private String normalizeLatexSpacing(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String escapeLatexText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder latex = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            String symbol = String.valueOf(value.charAt(i));
            String mapped = SYMBOL_TO_LATEX.get(symbol);
            if (mapped != null) {
                latex.append(mapped);
                continue;
            }
            switch (symbol) {
                case "\\" -> latex.append("\\textbackslash{}");
                case "{", "}", "_", "^", "%", "#", "&", "$" -> latex.append("\\").append(symbol);
                default -> latex.append(symbol);
            }
        }
        return latex.toString();
    }

    private static Map<String, String> createSymbolToLatexMap() {
        Map<String, String> symbols = new HashMap<>();
        symbols.put("∫", "\\int ");
        symbols.put("∑", "\\sum ");
        symbols.put("∏", "\\prod ");
        symbols.put("√", "\\sqrt ");
        symbols.put("∞", "\\infty ");
        symbols.put("≤", "\\le ");
        symbols.put("≥", "\\ge ");
        symbols.put("≠", "\\neq ");
        symbols.put("≈", "\\approx ");
        symbols.put("±", "\\pm ");
        symbols.put("×", "\\times ");
        symbols.put("·", "\\cdot ");
        symbols.put("→", "\\to ");
        symbols.put("←", "\\leftarrow ");
        symbols.put("∂", "\\partial ");
        symbols.put("∇", "\\nabla ");
        symbols.put("α", "\\alpha ");
        symbols.put("β", "\\beta ");
        symbols.put("γ", "\\gamma ");
        symbols.put("δ", "\\delta ");
        symbols.put("ε", "\\epsilon ");
        symbols.put("θ", "\\theta ");
        symbols.put("λ", "\\lambda ");
        symbols.put("μ", "\\mu ");
        symbols.put("π", "\\pi ");
        symbols.put("ρ", "\\rho ");
        symbols.put("σ", "\\sigma ");
        symbols.put("τ", "\\tau ");
        symbols.put("φ", "\\phi ");
        symbols.put("ω", "\\omega ");
        symbols.put("Γ", "\\Gamma ");
        symbols.put("Δ", "\\Delta ");
        symbols.put("Θ", "\\Theta ");
        symbols.put("Λ", "\\Lambda ");
        symbols.put("Π", "\\Pi ");
        symbols.put("Σ", "\\Sigma ");
        symbols.put("Φ", "\\Phi ");
        symbols.put("Ω", "\\Omega ");
        return symbols;
    }

    private String imageMarkerFor(XWPFPictureData pictureData) {
        String mimeType = mimeTypeFor(pictureData.suggestFileExtension());
        String base64 = Base64.getEncoder().encodeToString(pictureData.getData());
        return IMAGE_MARKER_PREFIX + "data:" + mimeType + ";base64," + base64 + IMAGE_MARKER_SUFFIX;
    }

    private String mimeTypeFor(String extension) {
        if (extension == null) {
            return "image/png";
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            case "svg" -> "image/svg+xml";
            case "emf" -> "image/emf";
            case "wmf" -> "image/wmf";
            default -> "image/png";
        };
    }

    private String decodeStrict(byte[] content, java.nio.charset.Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer buffer = decoder.decode(ByteBuffer.wrap(content));
        return buffer.toString();
    }

    private String normalizeExtractedText(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace('\uFEFF', ' ')
            .replace('\u00A0', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim();
    }

    private String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            throw new UnsupportedImportFileException(
                "Unsupported file type. Supported formats are .doc, .docx, and .txt");
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
