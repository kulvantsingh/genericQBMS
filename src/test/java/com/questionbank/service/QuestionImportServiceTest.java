package com.questionbank.service;

import com.questionbank.dto.QuestionImportParseResponse;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionImportServiceTest {

    private final QuestionImportService importService = new QuestionImportService(
        new QuestionImportTextExtractorService(),
        new QuestionImportParserService()
    );

    @Test
    void parseDocxFile_extractsAndParsesStructuredQuestion() throws Exception {
        byte[] content = buildDocx(
            "Q1. What is the H.C.F. of 48 and 180?",
            "Type:",
            "MCQ",
            "Instruction:",
            "Select one correct answer",
            "Subject:",
            "Mathematics",
            "Book:",
            "Number Theory Basics, 2nd Edition, ISBN: 9780123456789",
            "ETG No.:",
            "ETG-11",
            "Page:",
            "45",
            "Q. No.:",
            "Q1",
            "Difficulty:",
            "Easy",
            "Points:",
            "1",
            "Tags:",
            "hcf, number system, factors",
            "Options:",
            "A",
            "6",
            "B",
            "12",
            "C",
            "24",
            "D",
            "36",
            "Correct Answer: B (index: 1)",
            "Explanation: 48 = 2^4 x 3, 180 = 2^2 x 3^2 x 5. Common factors: 2^2 x 3 = 12"
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            content
        );

        QuestionImportParseResponse response = importService.parse(file);

        assertEquals(1, response.getQuestions().size());
        assertTrue(response.getErrors().isEmpty());
        assertEquals("mcq", response.getQuestions().get(0).getType().getValue());
        assertEquals("12", response.getQuestions().get(0).getOptions().get(1).getText());
        assertEquals("1", response.getQuestions().get(0).getAnswers().get(0).getValue());
        assertEquals("Number Theory Basics", response.getQuestions().get(0).getBook().getName());
        assertEquals("2nd Edition", response.getQuestions().get(0).getBook().getEdition());
        assertEquals("9780123456789", response.getQuestions().get(0).getBook().getIsbn());
    }

    @Test
    void parseTxtFile_extractsAndParsesStructuredQuestion() {
        String text = String.join("\n",
            "Q1. The Earth revolves around the Sun.",
            "Type:",
            "True/False",
            "Instruction:",
            "Choose true or false",
            "Subject:",
            "Science",
            "Difficulty:",
            "Easy",
            "Points:",
            "1",
            "Options:",
            "A",
            "True",
            "B",
            "False",
            "Correct Answer: True",
            "Explanation: This is a basic astronomy fact."
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.txt",
            "text/plain",
            text.getBytes(StandardCharsets.UTF_8)
        );

        QuestionImportParseResponse response = importService.parse(file);

        assertEquals(1, response.getQuestions().size());
        assertTrue(response.getErrors().isEmpty());
        assertEquals("true_false", response.getQuestions().get(0).getType().getValue());
        assertEquals("boolean", response.getQuestions().get(0).getAnswers().get(0).getType());
        assertEquals("true", response.getQuestions().get(0).getAnswers().get(0).getValue());
    }

    @Test
    void parseDocxFile_preservesImagesAsBase64Html() throws Exception {
        byte[] image = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0ioAAAAASUVORK5CYII=");

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XWPFParagraph questionParagraph = document.createParagraph();
            questionParagraph.createRun().setText("Q1. Identify the shape shown below.");

            XWPFParagraph imageParagraph = document.createParagraph();
            imageParagraph.createRun().addPicture(
                new ByteArrayInputStream(image),
                Document.PICTURE_TYPE_PNG,
                "shape.png",
                Units.pixelToEMU(1),
                Units.pixelToEMU(1)
            );

            XWPFParagraph typeParagraph = document.createParagraph();
            typeParagraph.createRun().setText("Type:");
            XWPFParagraph typeValueParagraph = document.createParagraph();
            typeValueParagraph.createRun().setText("MCQ");
            XWPFParagraph optionsParagraph = document.createParagraph();
            optionsParagraph.createRun().setText("Options:");
            XWPFParagraph optionA = document.createParagraph();
            optionA.createRun().setText("A");
            XWPFParagraph optionAText = document.createParagraph();
            optionAText.createRun().setText("Circle");
            XWPFParagraph optionB = document.createParagraph();
            optionB.createRun().setText("B");
            XWPFParagraph optionBText = document.createParagraph();
            optionBText.createRun().setText("Square");
            XWPFParagraph answerParagraph = document.createParagraph();
            answerParagraph.createRun().setText("Correct Answer: A (index: 0)");

            document.write(outputStream);

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "with-image.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                outputStream.toByteArray()
            );

            QuestionImportParseResponse response = importService.parse(file);

            assertEquals(1, response.getQuestions().size());
            assertTrue(response.getQuestions().get(0).getQuestion().contains("<img src=\"data:image/png;base64,"));
        }
    }

    @Test
    void parseDocxFile_convertsWordEquationToLatex() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Q1. Evaluate the following integral.");

            XWPFParagraph equationParagraph = document.createParagraph();
            equationParagraph.getCTP().set(CTP.Factory.parse("""
                <w:p xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                     xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math">
                  <m:oMath>
                    <m:nary>
                      <m:naryPr>
                        <m:chr m:val="∫"/>
                      </m:naryPr>
                      <m:sub>
                        <m:r><m:t>y</m:t></m:r>
                      </m:sub>
                      <m:sup>
                        <m:r><m:t>x</m:t></m:r>
                      </m:sup>
                      <m:e>
                        <m:r><m:t>x</m:t></m:r>
                        <m:r><m:t>d</m:t></m:r>
                        <m:r><m:t>x</m:t></m:r>
                      </m:e>
                    </m:nary>
                  </m:oMath>
                </w:p>
                """));

            document.createParagraph().createRun().setText("Type:");
            document.createParagraph().createRun().setText("MCQ");
            document.createParagraph().createRun().setText("Options:");
            document.createParagraph().createRun().setText("A");
            document.createParagraph().createRun().setText("1");
            document.createParagraph().createRun().setText("B");
            document.createParagraph().createRun().setText("2");
            document.createParagraph().createRun().setText("Correct Answer: A (index: 0)");

            document.write(outputStream);

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "with-equation.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                outputStream.toByteArray()
            );

            QuestionImportParseResponse response = importService.parse(file);

            assertEquals(1, response.getQuestions().size());
            assertTrue(response.getErrors().isEmpty());
            assertTrue(response.getQuestions().get(0).getQuestion().contains("<span class=\"math-inline\""));
            assertTrue(response.getQuestions().get(0).getQuestion().contains("data-latex=\"\\int_{y}^{x} xdx\""));
            assertTrue(response.getQuestions().get(0).getQuestion().contains(">\\(\\int_{y}^{x} xdx\\)</span>"));
        }
    }

    private byte[] buildDocx(String... lines) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (String line : lines) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.createRun().setText(line);
            }
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
