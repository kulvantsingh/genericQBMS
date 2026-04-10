package com.questionbank.service;

import com.questionbank.dto.QuestionImportParseResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionImportParserServiceTest {

    private final QuestionImportParserService parserService = new QuestionImportParserService();

    @Test
    void parse_infersArrangeSequenceTypeAndParsesOrderedAnswers() {
        String text = String.join("\n",
            "Q1. Put the steps in the correct order.",
            "Instruction:",
            "Arrange the process correctly",
            "Subject:",
            "Mathematics",
            "Difficulty:",
            "Medium",
            "Points:",
            "4",
            "Items to arrange (given in shuffled order):",
            "1",
            "Multiply the highest powers of each prime factor.",
            "2",
            "Write both numbers as products of primes.",
            "3",
            "Choose the common prime factors.",
            "4",
            "Take the lowest powers among common factors.",
            "Correct Sequence (indices, 0-based): 1 → 2 → 3 → 0"
        );

        QuestionImportParseResponse response = parserService.parse(text);

        assertEquals(1, response.getQuestions().size());
        assertEquals("arrange_sequence", response.getQuestions().get(0).getType().getValue());
        assertEquals(4, response.getQuestions().get(0).getOptions().size());
        assertEquals("2", response.getQuestions().get(0).getAnswers().get(0).getValue());
        assertEquals("1", response.getQuestions().get(0).getAnswers().get(3).getValue());
        assertTrue(response.getQuestions().get(0).getWarnings().stream()
            .anyMatch(warning -> warning.contains("inferred as arrange_sequence")));
        assertTrue(response.getQuestions().get(0).getWarnings().stream()
            .anyMatch(warning -> warning.contains("1-based indexing")));
    }

    @Test
    void parse_arrangeSequenceKeepsReadingItemsAfterCorrectSequenceLine() {
        String text = String.join("\n",
            "Q1. Arrange the HCF steps.",
            "Type:",
            "Arrange sequence",
            "Subject:",
            "Mathematics",
            "Difficulty:",
            "Medium",
            "Points:",
            "4",
            "Items to arrange (given in shuffled order):",
            "1",
            "Write both numbers as products of prime factors.",
            "2",
            "Identify all unique prime factors from both numbers.",
            "Correct Sequence: 3 → 2 → 1 → 4",
            "3",
            "Take only the common prime factors.",
            "4",
            "Multiply the required factors to get the HCF."
        );

        QuestionImportParseResponse response = parserService.parse(text);

        assertEquals(1, response.getQuestions().size());
        assertEquals(4, response.getQuestions().get(0).getOptions().size());
        assertEquals("Write both numbers as products of prime factors.", response.getQuestions().get(0).getOptions().get(0).getText());
        assertEquals("Identify all unique prime factors from both numbers.", response.getQuestions().get(0).getOptions().get(1).getText());
        assertEquals("Take only the common prime factors.", response.getQuestions().get(0).getOptions().get(2).getText());
        assertEquals("Multiply the required factors to get the HCF.", response.getQuestions().get(0).getOptions().get(3).getText());
        assertEquals("3", response.getQuestions().get(0).getAnswers().get(0).getValue());
        assertEquals("4", response.getQuestions().get(0).getAnswers().get(3).getValue());
    }

    @Test
    void parse_parsesMultiCorrectAnswersFromHumanReadableFormat() {
        String text = String.join("\n",
            "Q2. Which of the following are prime numbers?",
            "Type:",
            "Multi-correct",
            "Subject:",
            "Mathematics",
            "Difficulty:",
            "Easy",
            "Points:",
            "2",
            "Options:",
            "A",
            "2",
            "B",
            "3",
            "C",
            "4",
            "D",
            "5",
            "Correct Answers: A, B, D (indices: 0, 1, 3)"
        );

        QuestionImportParseResponse response = parserService.parse(text);

        assertEquals(1, response.getQuestions().size());
        assertEquals("multi_correct", response.getQuestions().get(0).getType().getValue());
        assertEquals(3, response.getQuestions().get(0).getAnswers().size());
        assertEquals("0", response.getQuestions().get(0).getAnswers().get(0).getValue());
        assertEquals("1", response.getQuestions().get(0).getAnswers().get(1).getValue());
        assertEquals("3", response.getQuestions().get(0).getAnswers().get(2).getValue());
    }

    @Test
    void parse_parsesComprehensiveQuestionWithSubQuestions() {
        String text = String.join("\n",
            "Q6. Study the following and answer the sub-questions:",
            "A number that has exactly two distinct factors is called prime.",
            "Type:",
            "Comprehensive",
            "Instruction:",
            "Read the passage carefully",
            "Subject:",
            "Mathematics",
            "Difficulty:",
            "Medium",
            "Points:",
            "2",
            "Sub-Question 6a (MCQ):",
            "Which of the following is prime?",
            "Options:",
            "A",
            "4",
            "B",
            "5",
            "C",
            "6",
            "D",
            "8",
            "Correct Answer: B (index: 1)",
            "Explanation: 5 has exactly two factors.",
            "Sub-Question 6b (True/False):",
            "1 is a prime number.",
            "Options:",
            "A",
            "True",
            "B",
            "False",
            "Correct Answer: False",
            "Explanation: 1 has only one factor."
        );

        QuestionImportParseResponse response = parserService.parse(text);

        assertEquals(1, response.getQuestions().size());
        assertEquals("comprehensive", response.getQuestions().get(0).getType().getValue());
        assertEquals(2, response.getQuestions().get(0).getSubQuestions().size());
        assertEquals("mcq", response.getQuestions().get(0).getSubQuestions().get(0).getType().getValue());
        assertEquals("1", response.getQuestions().get(0).getSubQuestions().get(0).getAnswers().get(0).getValue());
        assertEquals("false", response.getQuestions().get(0).getSubQuestions().get(1).getAnswers().get(0).getValue());
        assertEquals(2, response.getQuestions().get(0).getPoints());
    }

    @Test
    void parse_parsesComprehensiveQuestionWithoutOptionsHeaderAndWithAnswerSyntaxVariant() {
        String text = String.join("\n",
            "Q6. Study the following and answer the sub-questions:",
            "A school has 48 students in Class A and 60 students in Class B. The school wants to divide each class into equal groups of the maximum possible size, with no students left over.",
            "Type:\tComprehensive",
            "Subject:\tMathematics",
            "Book:\tNumber Theory Basics, 2nd Edition, ISBN: 9780123456789",
            "ETG No.:\tETG-15",
            "Page:\t55",
            "Q. No.:\tQ6",
            "Difficulty:\tHard",
            "Points:\t5",
            "Tags:\thcf, lcm, word problem, application",
            "Sub-Question 6a (MCQ):",
            "What is the maximum group size?",
            "A\t6",
            "B\t12",
            "C\t24",
            "D\t60",
            "Answer: B - index 1",
            "Explanation: H.C.F. of 48 and 60: 48 = 2^4 x 3, 60 = 2^2 x 3 x 5. H.C.F. = 2^2 x 3 = 12.",
            "Sub-Question 6b (True/False):",
            "The minimum number of days for both classes to complete a full project cycle simultaneously is the L.C.M. of 48 and 60.",
            "A\tTrue",
            "B\tFalse",
            "Answer: True",
            "Explanation: L.C.M. of 48 and 60 = 240. This is the smallest number divisible by both 48 and 60, representing the earliest simultaneous completion."
        );

        QuestionImportParseResponse response = parserService.parse(text);

        assertEquals(1, response.getQuestions().size());
        assertTrue(response.getErrors().isEmpty());
        assertEquals("comprehensive", response.getQuestions().get(0).getType().getValue());
        assertEquals("Mathematics", response.getQuestions().get(0).getSubject().getName());
        assertEquals(4, response.getQuestions().get(0).getTags().size());
        assertEquals(2, response.getQuestions().get(0).getSubQuestions().size());
        assertEquals(4, response.getQuestions().get(0).getSubQuestions().get(0).getOptions().size());
        assertEquals("12", response.getQuestions().get(0).getSubQuestions().get(0).getOptions().get(1).getText());
        assertEquals("1", response.getQuestions().get(0).getSubQuestions().get(0).getAnswers().get(0).getValue());
        assertEquals("true_false", response.getQuestions().get(0).getSubQuestions().get(1).getType().getValue());
        assertEquals("true", response.getQuestions().get(0).getSubQuestions().get(1).getAnswers().get(0).getValue());
    }
}
