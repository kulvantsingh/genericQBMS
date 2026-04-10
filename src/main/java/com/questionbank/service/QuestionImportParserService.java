package com.questionbank.service;

import com.questionbank.dto.BookDto;
import com.questionbank.dto.QuestionAnswerDto;
import com.questionbank.dto.QuestionImportIssueDto;
import com.questionbank.dto.QuestionImportParseResponse;
import com.questionbank.dto.QuestionImportQuestionDto;
import com.questionbank.dto.QuestionMatchPairDto;
import com.questionbank.dto.QuestionOptionDto;
import com.questionbank.dto.SubjectDto;
import com.questionbank.dto.SubQuestionDto;
import com.questionbank.dto.TagDto;
import com.questionbank.model.QuestionType;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuestionImportParserService {

    private static final Pattern QUESTION_START_PATTERN =
        Pattern.compile("^Q(\\d+)\\.\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUB_QUESTION_START_PATTERN =
        Pattern.compile("^Sub-Question\\s+(.+?)\\s*\\((MCQ|True/False|Multi-correct|Arrange sequence|Match pair)\\):\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OPTION_INLINE_PATTERN =
        Pattern.compile("^([A-Z])(?:[\\).:-])?\\s+(.+)$");
    private static final Pattern OPTION_LABEL_ONLY_PATTERN =
        Pattern.compile("^([A-Z])(?:[\\).:-])?$");
    private static final Pattern NUMBERED_INLINE_PATTERN =
        Pattern.compile("^(\\d+)(?:[\\).:-])?\\s+(.+)$");
    private static final Pattern NUMBER_ONLY_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern DIRECT_PAIR_PATTERN =
        Pattern.compile("^(.+?)\\s*(?:->|=>|→)\\s*(.+)$");
    private static final Pattern INDEX_PATTERN =
        Pattern.compile("(?i)(?:index(?:es)?|indices)\\s*(?::|=|[-–—])?\\s*([\\d,\\s]+)");
    private static final Pattern ISBN_PATTERN =
        Pattern.compile("(?i)^ISBN\\s*:\\s*(.+)$");
    private static final Pattern IMAGE_MARKER_PATTERN =
        Pattern.compile("\\[\\[IMG:(data:image/[^\\]]+)]]");
    private static final Pattern MATH_MARKER_PATTERN =
        Pattern.compile("\\[\\[MATH:(inline|display):([A-Za-z0-9+/=]+)]]");

    private static final String FIELD_TYPE = "Type";
    private static final String FIELD_INSTRUCTION = "Instruction";
    private static final String FIELD_SUBJECT = "Subject";
    private static final String FIELD_BOOK = "Book";
    private static final String FIELD_ETG = "ETG No.";
    private static final String FIELD_PAGE = "Page";
    private static final String FIELD_QUESTION_NUMBER = "Q. No.";
    private static final String FIELD_DIFFICULTY = "Difficulty";
    private static final String FIELD_POINTS = "Points";
    private static final String FIELD_TAGS = "Tags";

    private static final List<String> METADATA_ORDER = List.of(
        FIELD_TYPE,
        FIELD_INSTRUCTION,
        FIELD_SUBJECT,
        FIELD_BOOK,
        FIELD_ETG,
        FIELD_PAGE,
        FIELD_QUESTION_NUMBER,
        FIELD_DIFFICULTY,
        FIELD_POINTS,
        FIELD_TAGS
    );

    public QuestionImportParseResponse parse(String extractedText) {
        List<String> normalizedLines = normalizeLines(extractedText);
        List<List<String>> blocks = splitIntoQuestionBlocks(normalizedLines);

        List<QuestionImportQuestionDto> questions = new ArrayList<>();
        List<QuestionImportIssueDto> errors = new ArrayList<>();

        int sourceIndex = 0;
        for (List<String> block : blocks) {
            sourceIndex++;
            try {
                questions.add(parseQuestionBlock(block, sourceIndex));
            } catch (RuntimeException ex) {
                errors.add(QuestionImportIssueDto.builder()
                    .sourceIndex(sourceIndex)
                    .field("parse")
                    .message(ex.getMessage())
                    .build());
            }
        }

        return QuestionImportParseResponse.builder()
            .questions(questions)
            .errors(errors)
            .build();
    }

    private QuestionImportQuestionDto parseQuestionBlock(List<String> block, int sourceIndex) {
        if (block.isEmpty()) {
            throw new IllegalArgumentException("Question block was empty");
        }

        Matcher startMatcher = QUESTION_START_PATTERN.matcher(block.get(0));
        if (!startMatcher.matches()) {
            throw new IllegalArgumentException("Question block does not start with a valid question marker");
        }

        QuestionImportQuestionDto question = new QuestionImportQuestionDto();
        question.setSourceIndex(sourceIndex);

        List<String> questionLines = new ArrayList<>();
        String firstLineText = trimToNull(startMatcher.group(2));
        if (firstLineText != null) {
            questionLines.add(firstLineText);
        }

        int index = 1;
        while (index < block.size() && metadataField(block.get(index)) == null) {
            questionLines.add(block.get(index));
            index++;
        }

        question.setQuestion(toHtml(questionLines));

        MetadataResult metadataResult = parseMetadata(block, index);
        Map<String, String> metadata = metadataResult.values();
        question.setType(resolveType(metadata, block, question.getWarnings()));
        question.setInstruction(toHtml(metadata.get(FIELD_INSTRUCTION)));
        question.setDifficulty(normalizeDifficulty(metadata.get(FIELD_DIFFICULTY)));
        question.setPoints(parsePoints(metadata.get(FIELD_POINTS), question.getWarnings()));
        question.setSubject(parseSubject(metadata.get(FIELD_SUBJECT)));
        question.setBook(parseBook(metadata.get(FIELD_BOOK)));
        question.setTags(parseTags(metadata.get(FIELD_TAGS)));
        question.setEtgNumber(trimToNull(metadata.get(FIELD_ETG)));
        question.setPageNumber(trimToNull(metadata.get(FIELD_PAGE)));
        question.setQuestionNumber(trimToNull(metadata.get(FIELD_QUESTION_NUMBER)));
        question.setOptions(new ArrayList<>());
        question.setAnswers(new ArrayList<>());
        question.setPairs(new ArrayList<>());
        question.setSubQuestions(new ArrayList<>());

        List<String> content = slice(block, metadataResult.nextIndex(), block.size());
        switch (question.getType()) {
            case MCQ, MULTI_CORRECT, TRUE_FALSE -> parseChoiceQuestion(question, content);
            case ARRANGE_SEQUENCE -> parseArrangeSequenceQuestion(question, content);
            case MATCH_PAIR -> parseMatchPairQuestion(question, content);
            case COMPREHENSIVE -> parseComprehensiveQuestion(question, content);
        }

        if (trimToNull(stripHtml(question.getQuestion())) == null) {
            throw new IllegalArgumentException("Question text could not be parsed");
        }

        return question;
    }

    private MetadataResult parseMetadata(List<String> lines, int startIndex) {
        Map<String, String> values = new LinkedHashMap<>();
        int index = startIndex;

        while (index < lines.size()) {
            String line = lines.get(index);
            String field = metadataField(line);
            if (field == null) {
                break;
            }

            index++;
            List<String> valueLines = new ArrayList<>();
            String sameLineValue = trimToNull(line.substring(line.indexOf(':') + 1));
            if (sameLineValue != null) {
                valueLines.add(sameLineValue);
            }

            while (index < lines.size()) {
                String candidate = lines.get(index);
                if (metadataField(candidate) != null || isSectionBoundary(candidate)) {
                    break;
                }
                valueLines.add(candidate);
                index++;
            }

            values.put(field, joinLines(valueLines));
        }

        return new MetadataResult(values, index);
    }

    private void parseChoiceQuestion(QuestionImportQuestionDto question, List<String> content) {
        int optionsIndex = findIndex(content, this::isOptionsLine);
        int answerIndex = findIndex(content, this::isCorrectAnswerLine);
        int explanationIndex = findIndex(content, this::isExplanationLine);
        boolean implicitOptions = optionsIndex < 0;
        if (implicitOptions) {
            optionsIndex = findImplicitOptionIndex(content, minPositive(answerIndex, explanationIndex, content.size()));
        }
        int firstSectionIndex = minPositive(optionsIndex, answerIndex, explanationIndex, content.size());

        appendQuestionLines(question, slice(content, 0, firstSectionIndex));

        List<ParsedOption> options = new ArrayList<>();
        if (optionsIndex >= 0) {
            int optionsEnd = minPositive(answerIndex, explanationIndex, content.size());
            int optionsStart = implicitOptions ? optionsIndex : optionsIndex + 1;
            options = parseLabeledOptions(slice(content, optionsStart, optionsEnd), question.getWarnings());
            question.setOptions(mapOptions(options));
        }

        if (question.getType() == QuestionType.TRUE_FALSE && question.getOptions().isEmpty()) {
            question.setOptions(defaultTrueFalseOptions());
            options = defaultTrueFalseParsedOptions();
        }

        if (answerIndex >= 0) {
            String line = content.get(answerIndex);
            switch (question.getType()) {
                case MCQ -> question.setAnswers(parseMcqAnswer(line, options, question.getWarnings()));
                case MULTI_CORRECT -> question.setAnswers(parseMultiCorrectAnswers(line, options, question.getWarnings()));
                case TRUE_FALSE -> question.setAnswers(parseTrueFalseAnswer(line, options, question.getWarnings()));
                default -> {
                }
            }
        } else {
            question.getWarnings().add("Answer line was not found");
        }

        if (explanationIndex >= 0) {
            question.setExplanation(parseExplanation(content, explanationIndex));
        }
    }

    private void parseArrangeSequenceQuestion(QuestionImportQuestionDto question, List<String> content) {
        int itemsIndex = findIndex(content, this::isArrangeItemsLine);
        int answerIndex = findIndex(content, this::isCorrectSequenceLine);
        int explanationIndex = findIndex(content, this::isExplanationLine);
        int firstSectionIndex = minPositive(itemsIndex, answerIndex, explanationIndex, content.size());

        appendQuestionLines(question, slice(content, 0, firstSectionIndex));

        if (itemsIndex >= 0) {
            int itemsEnd = minPositive(explanationIndex, content.size());
            List<String> items = parseNumberedItems(slice(content, itemsIndex + 1, itemsEnd), question.getWarnings());
            question.setOptions(mapTextOptions(items));
        } else {
            question.getWarnings().add("Items to arrange section was not found");
        }

        if (answerIndex >= 0) {
            question.setAnswers(parseSequenceAnswers(content.get(answerIndex), question.getWarnings()));
        } else {
            question.getWarnings().add("Correct sequence line was not found");
        }

        if (explanationIndex >= 0) {
            question.setExplanation(parseExplanation(content, explanationIndex));
        }
    }

    private void parseMatchPairQuestion(QuestionImportQuestionDto question, List<String> content) {
        int pairsIndex = findIndex(content, this::isPairsLine);
        int matchesIndex = findIndex(content, this::isCorrectMatchesLine);
        int explanationIndex = findIndex(content, this::isExplanationLine);
        int firstSectionIndex = minPositive(pairsIndex, matchesIndex, explanationIndex, content.size());

        appendQuestionLines(question, slice(content, 0, firstSectionIndex));

        List<QuestionMatchPairDto> pairs = new ArrayList<>();
        if (matchesIndex >= 0) {
            pairs = parseCorrectMatches(content.get(matchesIndex), question.getWarnings());
        } else if (pairsIndex >= 0) {
            int pairsEnd = minPositive(explanationIndex, content.size());
            pairs = parsePairsBlock(slice(content, pairsIndex + 1, pairsEnd), question.getWarnings());
        } else {
            question.getWarnings().add("Pairs section was not found");
        }
        question.setPairs(pairs);

        if (explanationIndex >= 0) {
            question.setExplanation(parseExplanation(content, explanationIndex));
        }
    }

    private void parseComprehensiveQuestion(QuestionImportQuestionDto question, List<String> content) {
        int firstSubQuestionIndex = findIndex(content, this::isSubQuestionStartLine);
        if (firstSubQuestionIndex < 0) {
            throw new IllegalArgumentException("Comprehensive question does not contain any sub-questions");
        }

        appendQuestionLines(question, slice(content, 0, firstSubQuestionIndex));

        List<List<String>> blocks = splitIntoSubQuestionBlocks(slice(content, firstSubQuestionIndex, content.size()));
        List<SubQuestionDto> subQuestions = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            subQuestions.add(parseSubQuestionBlock(blocks.get(i), i + 1, question.getWarnings()));
        }
        question.setSubQuestions(subQuestions);
        question.setPoints(subQuestions.stream()
            .map(SubQuestionDto::getPoints)
            .filter(java.util.Objects::nonNull)
            .reduce(0, Integer::sum));
    }

    private SubQuestionDto parseSubQuestionBlock(List<String> block, int order, List<String> parentWarnings) {
        Matcher matcher = SUB_QUESTION_START_PATTERN.matcher(block.get(0));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid sub-question marker: " + block.get(0));
        }

        SubQuestionDto subQuestion = new SubQuestionDto();
        subQuestion.setOrder(order);
        subQuestion.setType(mapTypeLabel(matcher.group(2)));
        subQuestion.setPoints(1);
        subQuestion.setOptions(new ArrayList<>());
        subQuestion.setAnswers(new ArrayList<>());
        subQuestion.setPairs(new ArrayList<>());

        List<String> content = slice(block, 1, block.size());
        switch (subQuestion.getType()) {
            case MCQ, MULTI_CORRECT, TRUE_FALSE -> parseSubChoiceQuestion(subQuestion, content, parentWarnings, order);
            case ARRANGE_SEQUENCE -> parseSubArrangeQuestion(subQuestion, content, parentWarnings, order);
            case MATCH_PAIR -> parseSubMatchPairQuestion(subQuestion, content, parentWarnings, order);
            case COMPREHENSIVE -> throw new IllegalArgumentException("Sub-questions cannot be comprehensive");
        }

        if (trimToNull(stripHtml(subQuestion.getQuestion())) == null) {
            throw new IllegalArgumentException("Sub-question " + order + " text could not be parsed");
        }

        return subQuestion;
    }

    private void parseSubChoiceQuestion(
        SubQuestionDto subQuestion,
        List<String> content,
        List<String> warnings,
        int order
    ) {
        int optionsIndex = findIndex(content, this::isOptionsLine);
        int answerIndex = findIndex(content, this::isCorrectAnswerLine);
        int explanationIndex = findIndex(content, this::isExplanationLine);
        boolean implicitOptions = optionsIndex < 0;
        if (implicitOptions) {
            optionsIndex = findImplicitOptionIndex(content, minPositive(answerIndex, explanationIndex, content.size()));
        }
        int firstSectionIndex = minPositive(optionsIndex, answerIndex, explanationIndex, content.size());

        subQuestion.setQuestion(toHtml(slice(content, 0, firstSectionIndex)));

        List<ParsedOption> options = new ArrayList<>();
        if (optionsIndex >= 0) {
            int optionsEnd = minPositive(answerIndex, explanationIndex, content.size());
            int optionsStart = implicitOptions ? optionsIndex : optionsIndex + 1;
            options = parseLabeledOptions(slice(content, optionsStart, optionsEnd), warnings);
            subQuestion.setOptions(mapOptions(options));
        }

        if (subQuestion.getType() == QuestionType.TRUE_FALSE && subQuestion.getOptions().isEmpty()) {
            subQuestion.setOptions(defaultTrueFalseOptions());
            options = defaultTrueFalseParsedOptions();
        }

        if (answerIndex >= 0) {
            String line = content.get(answerIndex);
            switch (subQuestion.getType()) {
                case MCQ -> subQuestion.setAnswers(parseMcqAnswer(line, options, warnings));
                case MULTI_CORRECT -> subQuestion.setAnswers(parseMultiCorrectAnswers(line, options, warnings));
                case TRUE_FALSE -> subQuestion.setAnswers(parseTrueFalseAnswer(line, options, warnings));
                default -> {
                }
            }
        } else {
            warnings.add("Sub-question " + order + " answer line was not found");
        }

        if (explanationIndex >= 0) {
            subQuestion.setExplanation(parseExplanation(content, explanationIndex));
        }
    }

    private void parseSubArrangeQuestion(
        SubQuestionDto subQuestion,
        List<String> content,
        List<String> warnings,
        int order
    ) {
        int itemsIndex = findIndex(content, this::isArrangeItemsLine);
        int answerIndex = findIndex(content, this::isCorrectSequenceLine);
        int explanationIndex = findIndex(content, this::isExplanationLine);
        int firstSectionIndex = minPositive(itemsIndex, answerIndex, explanationIndex, content.size());

        subQuestion.setQuestion(toHtml(slice(content, 0, firstSectionIndex)));

        if (itemsIndex >= 0) {
            int itemsEnd = minPositive(explanationIndex, content.size());
            List<String> items = parseNumberedItems(slice(content, itemsIndex + 1, itemsEnd), warnings);
            subQuestion.setOptions(mapTextOptions(items));
        } else {
            warnings.add("Sub-question " + order + " items to arrange section was not found");
        }

        if (answerIndex >= 0) {
            subQuestion.setAnswers(parseSequenceAnswers(content.get(answerIndex), warnings));
        } else {
            warnings.add("Sub-question " + order + " correct sequence line was not found");
        }

        if (explanationIndex >= 0) {
            subQuestion.setExplanation(parseExplanation(content, explanationIndex));
        }
    }

    private void parseSubMatchPairQuestion(
        SubQuestionDto subQuestion,
        List<String> content,
        List<String> warnings,
        int order
    ) {
        int pairsIndex = findIndex(content, this::isPairsLine);
        int matchesIndex = findIndex(content, this::isCorrectMatchesLine);
        int explanationIndex = findIndex(content, this::isExplanationLine);
        int firstSectionIndex = minPositive(pairsIndex, matchesIndex, explanationIndex, content.size());

        subQuestion.setQuestion(toHtml(slice(content, 0, firstSectionIndex)));

        if (matchesIndex >= 0) {
            subQuestion.setPairs(parseCorrectMatches(content.get(matchesIndex), warnings));
        } else if (pairsIndex >= 0) {
            int pairsEnd = minPositive(explanationIndex, content.size());
            subQuestion.setPairs(parsePairsBlock(slice(content, pairsIndex + 1, pairsEnd), warnings));
        } else {
            warnings.add("Sub-question " + order + " pairs section was not found");
        }

        if (explanationIndex >= 0) {
            subQuestion.setExplanation(parseExplanation(content, explanationIndex));
        }
    }

    private List<ParsedOption> parseLabeledOptions(List<String> lines, List<String> warnings) {
        List<ParsedOption> options = new ArrayList<>();
        ParsedOption current = null;

        for (String rawLine : lines) {
            String line = trimToNull(rawLine);
            if (line == null) {
                continue;
            }
            if (isSectionBoundary(line)) {
                break;
            }

            Matcher inlineMatcher = OPTION_INLINE_PATTERN.matcher(line);
            if (inlineMatcher.matches()) {
                current = new ParsedOption(inlineMatcher.group(1));
                current.append(inlineMatcher.group(2));
                options.add(current);
                continue;
            }

            Matcher labelOnlyMatcher = OPTION_LABEL_ONLY_PATTERN.matcher(line);
            if (labelOnlyMatcher.matches()) {
                current = new ParsedOption(labelOnlyMatcher.group(1));
                options.add(current);
                continue;
            }

            if (current == null) {
                warnings.add("Encountered option text before any option label: " + line);
                continue;
            }

            current.append(line);
        }

        options.removeIf(option -> trimToNull(option.text()) == null);
        return options;
    }

    private List<String> parseNumberedItems(List<String> lines, List<String> warnings) {
        List<ParsedTextBlock> items = new ArrayList<>();
        ParsedTextBlock current = null;

        for (String rawLine : lines) {
            String line = trimToNull(rawLine);
            if (line == null) {
                continue;
            }
            if (isCorrectSequenceLine(line)) {
                continue;
            }
            if (isExplanationLine(line) || isSubQuestionStartLine(line) || QUESTION_START_PATTERN.matcher(line).matches()) {
                break;
            }

            Matcher inlineMatcher = NUMBERED_INLINE_PATTERN.matcher(line);
            if (inlineMatcher.matches()) {
                current = new ParsedTextBlock();
                current.append(inlineMatcher.group(2));
                items.add(current);
                continue;
            }

            if (NUMBER_ONLY_PATTERN.matcher(line).matches()) {
                current = new ParsedTextBlock();
                items.add(current);
                continue;
            }

            if (current == null) {
                warnings.add("Encountered arrange-sequence item text before any item number: " + line);
                continue;
            }

            current.append(line);
        }

        List<String> values = new ArrayList<>();
        for (ParsedTextBlock item : items) {
            String text = item.text();
            if (trimToNull(text) != null) {
                values.add(text);
            }
        }
        return values;
    }

    private List<QuestionMatchPairDto> parseCorrectMatches(String line, List<String> warnings) {
        String value = extractValueAfterColon(line);
        if (value == null) {
            warnings.add("Correct matches line was empty");
            return new ArrayList<>();
        }

        List<QuestionMatchPairDto> pairs = new ArrayList<>();
        String[] entries = value.split("\\s*,\\s*");
        for (int i = 0; i < entries.length; i++) {
            Matcher matcher = DIRECT_PAIR_PATTERN.matcher(entries[i]);
            if (!matcher.matches()) {
                warnings.add("Could not parse match pair: " + entries[i]);
                continue;
            }

            QuestionMatchPairDto pair = new QuestionMatchPairDto();
            pair.setOrder(i + 1);
            pair.setLeft(trimToNull(matcher.group(1)));
            pair.setRight(trimToNull(matcher.group(2)));
            pairs.add(pair);
        }
        return pairs;
    }

    private List<QuestionMatchPairDto> parsePairsBlock(List<String> lines, List<String> warnings) {
        List<QuestionMatchPairDto> directPairs = new ArrayList<>();
        for (String rawLine : lines) {
            String line = trimToNull(rawLine);
            if (line == null || line.toLowerCase(Locale.ROOT).startsWith("column ")) {
                continue;
            }

            Matcher directMatcher = DIRECT_PAIR_PATTERN.matcher(line);
            if (directMatcher.matches()) {
                QuestionMatchPairDto pair = new QuestionMatchPairDto();
                pair.setOrder(directPairs.size() + 1);
                pair.setLeft(trimToNull(directMatcher.group(1)));
                pair.setRight(trimToNull(directMatcher.group(2)));
                directPairs.add(pair);
            }
        }

        if (!directPairs.isEmpty()) {
            return directPairs;
        }

        List<String> cleanedValues = new ArrayList<>();
        for (String rawLine : lines) {
            String line = trimToNull(rawLine);
            if (line != null && !line.toLowerCase(Locale.ROOT).startsWith("column ")) {
                cleanedValues.add(line);
            }
        }

        List<QuestionMatchPairDto> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < cleanedValues.size(); i += 2) {
            QuestionMatchPairDto pair = new QuestionMatchPairDto();
            pair.setOrder((i / 2) + 1);
            pair.setLeft(cleanedValues.get(i));
            pair.setRight(cleanedValues.get(i + 1));
            pairs.add(pair);
        }

        if (cleanedValues.size() % 2 != 0) {
            warnings.add("Pairs block had an unmatched value: " + cleanedValues.get(cleanedValues.size() - 1));
        }

        return pairs;
    }

    private List<QuestionAnswerDto> parseMcqAnswer(String line, List<ParsedOption> options, List<String> warnings) {
        Integer index = resolveSingleIndex(line, options);
        if (index == null) {
            warnings.add("Could not resolve MCQ correct answer");
            return new ArrayList<>();
        }

        QuestionAnswerDto answer = new QuestionAnswerDto();
        answer.setOrder(1);
        answer.setType("index");
        answer.setValue(String.valueOf(index));
        return new ArrayList<>(List.of(answer));
    }

    private List<QuestionAnswerDto> parseMultiCorrectAnswers(
        String line,
        List<ParsedOption> options,
        List<String> warnings
    ) {
        List<Integer> indices = resolveMultipleIndexes(line, options);
        if (indices.isEmpty()) {
            warnings.add("Could not resolve multi-correct answers");
            return new ArrayList<>();
        }

        List<QuestionAnswerDto> answers = new ArrayList<>();
        for (int i = 0; i < indices.size(); i++) {
            QuestionAnswerDto answer = new QuestionAnswerDto();
            answer.setOrder(i + 1);
            answer.setType("index");
            answer.setValue(String.valueOf(indices.get(i)));
            answers.add(answer);
        }
        return answers;
    }

    private List<QuestionAnswerDto> parseTrueFalseAnswer(
        String line,
        List<ParsedOption> options,
        List<String> warnings
    ) {
        String value = normalizeBooleanValue(extractValueAfterColon(line), options);
        if (value == null) {
            warnings.add("Could not resolve true/false answer");
            return new ArrayList<>();
        }

        QuestionAnswerDto answer = new QuestionAnswerDto();
        answer.setOrder(1);
        answer.setType("boolean");
        answer.setValue(value);
        return new ArrayList<>(List.of(answer));
    }

    private List<QuestionAnswerDto> parseSequenceAnswers(String line, List<String> warnings) {
        List<Integer> indices = extractExplicitIndexes(line);
        if (indices.isEmpty()) {
            warnings.add("Could not parse arrange-sequence answer indices");
            return new ArrayList<>();
        }

        if (shouldConvertArrangeSequenceToOneBased(line, indices)) {
            indices = indices.stream().map(index -> index + 1).toList();
            warnings.add("Arrange-sequence answers were normalized from 0-based to 1-based indexing");
        }

        List<QuestionAnswerDto> answers = new ArrayList<>();
        for (int i = 0; i < indices.size(); i++) {
            QuestionAnswerDto answer = new QuestionAnswerDto();
            answer.setOrder(i + 1);
            answer.setType("index");
            answer.setValue(String.valueOf(indices.get(i)));
            answers.add(answer);
        }
        return answers;
    }

    private boolean shouldConvertArrangeSequenceToOneBased(String line, List<Integer> indices) {
        String normalizedLine = line == null ? "" : line.toLowerCase(Locale.ROOT);
        if (normalizedLine.contains("0-based") || normalizedLine.contains("zero-based")) {
            return true;
        }
        return indices.stream().anyMatch(index -> index == 0);
    }

    private List<QuestionOptionDto> mapOptions(List<ParsedOption> options) {
        List<QuestionOptionDto> dtos = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            QuestionOptionDto dto = new QuestionOptionDto();
            dto.setOrder(i + 1);
            dto.setText(options.get(i).text());
            dtos.add(dto);
        }
        return dtos;
    }

    private List<QuestionOptionDto> mapTextOptions(List<String> values) {
        List<QuestionOptionDto> dtos = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            QuestionOptionDto dto = new QuestionOptionDto();
            dto.setOrder(i + 1);
            dto.setText(values.get(i));
            dtos.add(dto);
        }
        return dtos;
    }

    private List<QuestionOptionDto> defaultTrueFalseOptions() {
        List<QuestionOptionDto> options = new ArrayList<>();

        QuestionOptionDto trueOption = new QuestionOptionDto();
        trueOption.setOrder(1);
        trueOption.setText("True");
        options.add(trueOption);

        QuestionOptionDto falseOption = new QuestionOptionDto();
        falseOption.setOrder(2);
        falseOption.setText("False");
        options.add(falseOption);

        return options;
    }

    private List<ParsedOption> defaultTrueFalseParsedOptions() {
        List<ParsedOption> options = new ArrayList<>();

        ParsedOption trueOption = new ParsedOption("A");
        trueOption.append("True");
        options.add(trueOption);

        ParsedOption falseOption = new ParsedOption("B");
        falseOption.append("False");
        options.add(falseOption);

        return options;
    }

    private Integer resolveSingleIndex(String line, List<ParsedOption> options) {
        List<Integer> indices = resolveMultipleIndexes(line, options);
        return indices.isEmpty() ? null : indices.get(0);
    }

    private List<Integer> resolveMultipleIndexes(String line, List<ParsedOption> options) {
        List<Integer> explicitIndexes = extractExplicitIndexes(line);
        if (!explicitIndexes.isEmpty()) {
            return explicitIndexes;
        }

        String rawValue = extractValueWithoutParentheses(line);
        if (rawValue == null) {
            return new ArrayList<>();
        }

        List<Integer> indices = new ArrayList<>();
        String[] tokens = rawValue.split("\\s*(?:,|/|;| and )\\s*");
        for (String token : tokens) {
            Integer index = resolveIndexToken(token, options);
            if (index != null && !indices.contains(index)) {
                indices.add(index);
            }
        }
        return indices;
    }

    private Integer resolveIndexToken(String token, List<ParsedOption> options) {
        String normalized = trimToNull(token);
        if (normalized == null) {
            return null;
        }

        if (normalized.matches("\\d+")) {
            return Integer.parseInt(normalized);
        }

        for (int i = 0; i < options.size(); i++) {
            ParsedOption option = options.get(i);
            if (option.label().equalsIgnoreCase(normalized) || option.text().equalsIgnoreCase(normalized)) {
                return i;
            }
        }

        return null;
    }

    private String normalizeBooleanValue(String rawValue, List<ParsedOption> options) {
        String cleaned = trimToNull(stripTrailingParentheses(rawValue));
        if (cleaned == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(cleaned) || "false".equalsIgnoreCase(cleaned)) {
            return cleaned.toLowerCase(Locale.ROOT);
        }

        Integer index = resolveIndexToken(cleaned, options);
        if (index == null || index < 0 || index >= options.size()) {
            return null;
        }

        String optionText = options.get(index).text();
        if ("true".equalsIgnoreCase(optionText) || "false".equalsIgnoreCase(optionText)) {
            return optionText.toLowerCase(Locale.ROOT);
        }

        return null;
    }

    private List<Integer> extractExplicitIndexes(String line) {
        String value = extractValueAfterColon(line);
        if (value == null) {
            Matcher matcher = INDEX_PATTERN.matcher(line);
            value = matcher.find() ? matcher.group(1) : null;
        }
        if (value == null) {
            return new ArrayList<>();
        }

        List<Integer> indices = new ArrayList<>();
        Matcher numberMatcher = Pattern.compile("\\d+").matcher(value);
        while (numberMatcher.find()) {
            indices.add(Integer.parseInt(numberMatcher.group()));
        }
        return indices;
    }

    private String parseExplanation(List<String> lines, int explanationIndex) {
        List<String> explanationLines = new ArrayList<>();
        String firstLine = extractValueAfterColon(lines.get(explanationIndex));
        if (firstLine != null) {
            explanationLines.add(firstLine);
        }
        for (int i = explanationIndex + 1; i < lines.size(); i++) {
            if (isSubQuestionStartLine(lines.get(i))) {
                break;
            }
            explanationLines.add(lines.get(i));
        }
        return toHtml(explanationLines);
    }

    private QuestionType resolveType(Map<String, String> metadata, List<String> block, List<String> warnings) {
        String explicitType = trimToNull(metadata.get(FIELD_TYPE));
        if (explicitType != null) {
            return mapTypeLabel(explicitType);
        }

        for (String line : block) {
            if (isSubQuestionStartLine(line)) {
                warnings.add("Question type was inferred as comprehensive because sub-questions were detected");
                return QuestionType.COMPREHENSIVE;
            }
        }
        for (String line : block) {
            if (isCorrectSequenceLine(line)) {
                warnings.add("Question type was inferred as arrange_sequence from the answer section");
                return QuestionType.ARRANGE_SEQUENCE;
            }
            if (isCorrectMatchesLine(line) || isPairsLine(line)) {
                warnings.add("Question type was inferred as match_pair from the pair section");
                return QuestionType.MATCH_PAIR;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("correct answers:")) {
                warnings.add("Question type was inferred as multi_correct from the answer section");
                return QuestionType.MULTI_CORRECT;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("correct answer:")) {
                String answerValue = extractValueWithoutParentheses(line);
                if ("true".equalsIgnoreCase(answerValue) || "false".equalsIgnoreCase(answerValue)) {
                    warnings.add("Question type was inferred as true_false from the answer section");
                    return QuestionType.TRUE_FALSE;
                }
            }
        }

        warnings.add("Question type defaulted to mcq");
        return QuestionType.MCQ;
    }

    private QuestionType mapTypeLabel(String rawValue) {
        String normalized = trimToNull(rawValue);
        if (normalized == null) {
            return QuestionType.MCQ;
        }

        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "mcq" -> QuestionType.MCQ;
            case "multi-correct", "multi correct" -> QuestionType.MULTI_CORRECT;
            case "true/false", "true false" -> QuestionType.TRUE_FALSE;
            case "arrange sequence", "arrange-sequence" -> QuestionType.ARRANGE_SEQUENCE;
            case "match pair", "match-pair" -> QuestionType.MATCH_PAIR;
            case "comprehensive" -> QuestionType.COMPREHENSIVE;
            default -> QuestionType.fromValue(normalized);
        };
    }

    private SubjectDto parseSubject(String value) {
        String name = trimToNull(value);
        if (name == null) {
            return null;
        }

        SubjectDto subject = new SubjectDto();
        subject.setName(name);
        return subject;
    }

    private BookDto parseBook(String value) {
        String cleaned = trimToNull(value);
        if (cleaned == null) {
            return null;
        }

        String[] parts = Arrays.stream(cleaned.split("\\s*,\\s*"))
            .map(this::trimToNull)
            .filter(java.util.Objects::nonNull)
            .toArray(String[]::new);

        BookDto book = new BookDto();
        List<String> nameParts = new ArrayList<>();
        for (String part : parts) {
            Matcher isbnMatcher = ISBN_PATTERN.matcher(part);
            if (isbnMatcher.matches()) {
                book.setIsbn(trimToNull(isbnMatcher.group(1)));
            } else if (part.toLowerCase(Locale.ROOT).contains("edition")) {
                book.setEdition(part);
            } else {
                nameParts.add(part);
            }
        }
        if (!nameParts.isEmpty()) {
            book.setName(String.join(", ", nameParts));
        } else {
            book.setName(cleaned);
        }
        return book;
    }

    private List<TagDto> parseTags(String value) {
        String cleaned = trimToNull(value);
        if (cleaned == null) {
            return new ArrayList<>();
        }

        Set<String> uniqueNames = new LinkedHashSet<>();
        for (String part : cleaned.split("\\s*,\\s*")) {
            String tagName = trimToNull(part);
            if (tagName != null) {
                uniqueNames.add(tagName.toLowerCase(Locale.ROOT));
            }
        }

        List<TagDto> tags = new ArrayList<>();
        for (String uniqueName : uniqueNames) {
            TagDto tag = new TagDto();
            tag.setName(uniqueName);
            tags.add(tag);
        }
        return tags;
    }

    private Integer parsePoints(String value, List<String> warnings) {
        String cleaned = trimToNull(value);
        if (cleaned == null) {
            return 1;
        }
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException ex) {
            warnings.add("Points value was invalid and defaulted to 1");
            return 1;
        }
    }

    private String normalizeDifficulty(String value) {
        String cleaned = trimToNull(value);
        if (cleaned == null) {
            return "Medium";
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "easy" -> "Easy";
            case "medium" -> "Medium";
            case "hard" -> "Hard";
            default -> cleaned;
        };
    }

    private List<List<String>> splitIntoQuestionBlocks(List<String> lines) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = new ArrayList<>();

        for (String line : lines) {
            boolean isQuestionStart = QUESTION_START_PATTERN.matcher(line).matches();
            if (isQuestionStart && !current.isEmpty()) {
                blocks.add(current);
                current = new ArrayList<>();
            }

            if (!current.isEmpty() || isQuestionStart) {
                current.add(line);
            }
        }

        if (!current.isEmpty()) {
            blocks.add(current);
        }

        return blocks;
    }

    private List<List<String>> splitIntoSubQuestionBlocks(List<String> lines) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = new ArrayList<>();

        for (String line : lines) {
            boolean isSubQuestionStart = isSubQuestionStartLine(line);
            if (isSubQuestionStart && !current.isEmpty()) {
                blocks.add(current);
                current = new ArrayList<>();
            }

            if (!current.isEmpty() || isSubQuestionStart) {
                current.add(line);
            }
        }

        if (!current.isEmpty()) {
            blocks.add(current);
        }

        return blocks;
    }

    private List<String> normalizeLines(String text) {
        if (text == null) {
            return List.of();
        }

        String normalized = text
            .replace('\uFEFF', ' ')
            .replace('\u00A0', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n');

        List<String> lines = new ArrayList<>();
        for (String rawLine : normalized.split("\n")) {
            String line = rawLine.replace('\t', ' ').replaceAll("\\s+", " ").trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private void appendQuestionLines(QuestionImportQuestionDto question, List<String> extraLines) {
        String extraHtml = toHtml(extraLines);
        if (extraHtml == null) {
            return;
        }
        if (question.getQuestion() == null) {
            question.setQuestion(extraHtml);
        } else {
            question.setQuestion(question.getQuestion() + extraHtml);
        }
    }

    private String toHtml(String value) {
        return toHtml(value == null ? List.of() : Arrays.asList(value.split("\\R", -1)));
    }

    private String toHtml(List<String> lines) {
        List<String> paragraphs = new ArrayList<>();
        List<String> currentParagraph = new ArrayList<>();

        for (String rawLine : lines) {
            String line = trimToNull(rawLine);
            if (line == null) {
                if (!currentParagraph.isEmpty()) {
                    paragraphs.add(String.join("<br/>", currentParagraph));
                    currentParagraph.clear();
                }
                continue;
            }
            currentParagraph.add(renderRichText(line));
        }

        if (!currentParagraph.isEmpty()) {
            paragraphs.add(String.join("<br/>", currentParagraph));
        }

        if (paragraphs.isEmpty()) {
            return null;
        }

        StringBuilder html = new StringBuilder();
        for (String paragraph : paragraphs) {
            html.append("<p>").append(paragraph).append("</p>");
        }
        return html.toString();
    }

    private String metadataField(String line) {
        String cleaned = trimToNull(line);
        if (cleaned == null) {
            return null;
        }

        for (String field : METADATA_ORDER) {
            if (cleaned.equalsIgnoreCase(field + ":")
                || cleaned.toLowerCase(Locale.ROOT).startsWith(field.toLowerCase(Locale.ROOT) + ":")) {
                return field;
            }
        }
        return null;
    }

    private boolean isSectionBoundary(String line) {
        return isOptionsLine(line)
            || isCorrectAnswerLine(line)
            || isExplanationLine(line)
            || isArrangeItemsLine(line)
            || isCorrectSequenceLine(line)
            || isPairsLine(line)
            || isCorrectMatchesLine(line)
            || isSubQuestionStartLine(line)
            || QUESTION_START_PATTERN.matcher(line).matches();
    }

    private boolean isOptionsLine(String line) {
        return line != null && line.equalsIgnoreCase("Options:");
    }

    private boolean isCorrectAnswerLine(String line) {
        if (line == null) {
            return false;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.startsWith("correct answer:")
            || lower.startsWith("correct answers:")
            || lower.startsWith("answer:")
            || lower.startsWith("answers:");
    }

    private boolean isExplanationLine(String line) {
        return line != null && line.toLowerCase(Locale.ROOT).startsWith("explanation:");
    }

    private boolean isArrangeItemsLine(String line) {
        return line != null && line.toLowerCase(Locale.ROOT).startsWith("items to arrange");
    }

    private boolean isCorrectSequenceLine(String line) {
        return line != null && line.toLowerCase(Locale.ROOT).startsWith("correct sequence");
    }

    private boolean isPairsLine(String line) {
        return line != null && line.equalsIgnoreCase("Pairs:");
    }

    private boolean isCorrectMatchesLine(String line) {
        return line != null && line.toLowerCase(Locale.ROOT).startsWith("correct matches:");
    }

    private boolean isSubQuestionStartLine(String line) {
        return line != null && SUB_QUESTION_START_PATTERN.matcher(line).matches();
    }

    private int findIndex(List<String> lines, java.util.function.Predicate<String> predicate) {
        for (int i = 0; i < lines.size(); i++) {
            if (predicate.test(lines.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private int findImplicitOptionIndex(List<String> lines, int upperBoundExclusive) {
        int upperBound = upperBoundExclusive < 0 ? lines.size() : Math.min(lines.size(), upperBoundExclusive);
        for (int i = 0; i < upperBound; i++) {
            String line = lines.get(i);
            if (line == null) {
                continue;
            }
            if (OPTION_INLINE_PATTERN.matcher(line).matches() || OPTION_LABEL_ONLY_PATTERN.matcher(line).matches()) {
                return i;
            }
        }
        return -1;
    }

    private int minPositive(int... values) {
        int min = Integer.MAX_VALUE;
        for (int value : values) {
            if (value >= 0 && value < min) {
                min = value;
            }
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    private List<String> slice(List<String> lines, int fromIndex, int toIndex) {
        if (fromIndex >= toIndex || fromIndex >= lines.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(lines.subList(Math.max(0, fromIndex), Math.min(lines.size(), toIndex)));
    }

    private String extractValueAfterColon(String line) {
        if (line == null) {
            return null;
        }
        int index = line.indexOf(':');
        return index >= 0 ? trimToNull(line.substring(index + 1)) : null;
    }

    private String extractValueWithoutParentheses(String line) {
        return stripTrailingParentheses(extractValueAfterColon(line));
    }

    private String stripTrailingParentheses(String value) {
        String cleaned = trimToNull(value);
        if (cleaned == null) {
            return null;
        }
        return cleaned.replaceFirst("\\s*\\([^)]*\\)\\s*$", "").trim();
    }

    private String joinLines(List<String> lines) {
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            String value = trimToNull(line);
            if (value != null) {
                cleaned.add(value);
            }
        }
        return cleaned.isEmpty() ? null : String.join("\n", cleaned);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        return value
            .replaceAll("<img[^>]*>", " image ")
            .replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .trim();
    }

    private String renderRichText(String line) {
        StringBuilder html = new StringBuilder();
        int lastIndex = 0;

        while (true) {
            int nextIndex = nextMarkerIndex(line, lastIndex);
            if (nextIndex < 0) {
                break;
            }

            html.append(HtmlUtils.htmlEscape(line.substring(lastIndex, nextIndex)));

            Matcher imageMatcher = IMAGE_MARKER_PATTERN.matcher(line);
            imageMatcher.region(nextIndex, line.length());
            if (imageMatcher.lookingAt()) {
                String dataUri = imageMatcher.group(1);
                if (dataUri.startsWith("data:image/")) {
                    html.append("<img src=\"")
                        .append(HtmlUtils.htmlEscape(dataUri))
                        .append("\" alt=\"Imported image\" />");
                } else {
                    html.append(HtmlUtils.htmlEscape(imageMatcher.group()));
                }
                lastIndex = imageMatcher.end();
                continue;
            }

            Matcher mathMatcher = MATH_MARKER_PATTERN.matcher(line);
            mathMatcher.region(nextIndex, line.length());
            if (mathMatcher.lookingAt()) {
                String latex = decodeMathLatex(mathMatcher.group(2));
                String escapedLatex = HtmlUtils.htmlEscape(latex);
                html.append("<span class=\"math-inline\" data-latex=\"")
                    .append(escapedLatex)
                    .append("\">\\(")
                    .append(escapedLatex)
                    .append("\\)</span>");
                lastIndex = mathMatcher.end();
                continue;
            }

            html.append(HtmlUtils.htmlEscape(String.valueOf(line.charAt(nextIndex))));
            lastIndex = nextIndex + 1;
        }
        html.append(HtmlUtils.htmlEscape(line.substring(lastIndex)));
        return html.toString();
    }

    private int nextMarkerIndex(String line, int fromIndex) {
        int imageIndex = line.indexOf("[[IMG:", fromIndex);
        int mathIndex = line.indexOf("[[MATH:", fromIndex);
        if (imageIndex < 0) {
            return mathIndex;
        }
        if (mathIndex < 0) {
            return imageIndex;
        }
        return Math.min(imageIndex, mathIndex);
    }

    private String decodeMathLatex(String encoded) {
        try {
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return encoded;
        }
    }

    private record MetadataResult(Map<String, String> values, int nextIndex) {
    }

    private static final class ParsedOption {
        private final String label;
        private final List<String> parts = new ArrayList<>();

        private ParsedOption(String label) {
            this.label = label;
        }

        private void append(String value) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }

        private String label() {
            return label;
        }

        private String text() {
            return String.join(" ", parts).trim();
        }
    }

    private static final class ParsedTextBlock {
        private final List<String> parts = new ArrayList<>();

        private void append(String value) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }

        private String text() {
            return String.join(" ", parts).trim();
        }
    }
}
