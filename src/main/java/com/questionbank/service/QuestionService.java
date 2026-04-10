package com.questionbank.service;

import com.questionbank.dto.BookDto;
import com.questionbank.dto.QuestionAnswerDto;
import com.questionbank.dto.QuestionMatchPairDto;
import com.questionbank.dto.QuestionOptionDto;
import com.questionbank.dto.QuestionRequest;
import com.questionbank.dto.QuestionResponse;
import com.questionbank.dto.StatsResponse;
import com.questionbank.dto.SubjectDto;
import com.questionbank.dto.SubQuestionDto;
import com.questionbank.exception.QuestionNotFoundException;
import com.questionbank.exception.QuestionValidationException;
import com.questionbank.model.Book;
import com.questionbank.model.Question;
import com.questionbank.model.QuestionAnswer;
import com.questionbank.model.QuestionMatchPair;
import com.questionbank.model.QuestionOption;
import com.questionbank.model.QuestionType;
import com.questionbank.model.SubQuestion;
import com.questionbank.model.SubQuestionAnswer;
import com.questionbank.model.SubQuestionMatchPair;
import com.questionbank.model.SubQuestionOption;
import com.questionbank.model.Subject;
import com.questionbank.repository.BookRepository;
import com.questionbank.repository.QuestionRepository;
import com.questionbank.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionService {

    private static final String DEFAULT_DIFFICULTY = "Medium";
    private static final String DEFAULT_SUBJECT = "General Knowledge";

    private final QuestionRepository questionRepository;
    private final SubjectRepository subjectRepository;
    private final BookRepository bookRepository;

    @Transactional
    public QuestionResponse create(QuestionRequest request) {
        Question question = new Question();
        applyRequest(question, request, true);
        Question saved = questionRepository.save(question);
        log.info("Created question id={} type={}", saved.getId(), saved.getType());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public QuestionResponse getById(Long id) {
        return mapToResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> getAll(String type, String difficulty, String subject, String search) {
        QuestionType qType = hasText(type) ? QuestionType.fromValue(type) : null;
        String diff = hasText(difficulty) ? difficulty : null;
        String subj = hasText(subject) ? subject.trim().toLowerCase(Locale.ROOT) : null;
        String srch = hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;

        return questionRepository.findAllWithFilters(qType, diff, subj).stream()
            .filter(question -> srch == null
                || containsIgnoreCase(question.getQuestionText(), srch)
                || containsIgnoreCase(question.getInstruction(), srch))
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public QuestionResponse update(Long id, QuestionRequest request) {
        Question existing = findOrThrow(id);
        applyRequest(existing, request, true);
        Question updated = questionRepository.save(existing);
        log.info("Updated question id={}", id);
        return mapToResponse(updated);
    }

    @Transactional
    public QuestionResponse patch(Long id, QuestionRequest request) {
        Question existing = findOrThrow(id);
        applyRequest(existing, request, false);
        Question patched = questionRepository.save(existing);
        log.info("Patched question id={}", id);
        return mapToResponse(patched);
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id);
        questionRepository.deleteById(id);
        log.info("Deleted question id={}", id);
    }

    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        long total = questionRepository.count();

        Map<String, Long> byType = new HashMap<>();
        questionRepository.countByType().forEach(row ->
            byType.put(row.get("type").toString(), ((Number) row.get("count")).longValue()));

        Map<String, Long> byDifficulty = new HashMap<>();
        questionRepository.countByDifficulty().forEach(row ->
            byDifficulty.put(row.get("difficulty").toString(), ((Number) row.get("count")).longValue()));

        Map<String, Long> bySubject = new HashMap<>();
        questionRepository.countBySubject().forEach(row ->
            bySubject.put(Objects.toString(row.get("subject"), "Unassigned"), ((Number) row.get("count")).longValue()));

        return StatsResponse.builder()
            .total(total)
            .byType(byType)
            .byDifficulty(byDifficulty)
            .bySubject(bySubject)
            .build();
    }

    private void applyRequest(Question question, QuestionRequest request, boolean fullReplace) {
        if (fullReplace || request.getType() != null) {
            question.setType(request.getType());
        }
        if (fullReplace || request.getQuestion() != null) {
            question.setQuestionText(request.getQuestion());
        }
        if (fullReplace || request.getInstruction() != null) {
            question.setInstruction(request.getInstruction());
        }
        if (fullReplace || request.getDifficulty() != null) {
            question.setDifficulty(defaultIfBlank(request.getDifficulty(), DEFAULT_DIFFICULTY));
        }
        if (fullReplace || request.getSubject() != null) {
            question.setSubject(resolveSubject(request.getSubject(), fullReplace));
        }
        if (fullReplace || request.getExplanation() != null) {
            question.setExplanation(request.getExplanation());
        }
        if (fullReplace || request.getBook() != null) {
            question.setBook(resolveBook(request.getBook()));
        }
        if (fullReplace || request.getEtgNumber() != null) {
            question.setEtgNumber(trimToNull(request.getEtgNumber()));
        }
        if (fullReplace || request.getPageNumber() != null) {
            question.setPageNumber(trimToNull(request.getPageNumber()));
        }
        if (fullReplace || request.getQuestionNumber() != null) {
            question.setQuestionNumber(trimToNull(request.getQuestionNumber()));
        }
        if (fullReplace || request.getPoints() != null) {
            question.setPoints(request.getPoints());
        }
        if (fullReplace || request.getOptions() != null) {
            replaceOptions(question, request.getOptions());
        }
        if (fullReplace || request.getAnswers() != null) {
            replaceAnswers(question, request.getAnswers());
        }
        if (fullReplace || request.getPairs() != null) {
            replacePairs(question, request.getPairs());
        }
        if (fullReplace || request.getSubQuestions() != null) {
            replaceSubQuestions(question, request.getSubQuestions());
        }

        pruneUnsupportedChildren(question);
        resolvePoints(question);
        validateQuestion(question);
    }

    private void replaceOptions(Question question, List<QuestionOptionDto> optionDtos) {
        question.getOptions().clear();
        if (optionDtos == null) {
            return;
        }

        for (int i = 0; i < optionDtos.size(); i++) {
            QuestionOptionDto dto = optionDtos.get(i);
            if (dto == null) {
                continue;
            }
            QuestionOption option = QuestionOption.builder()
                .optionOrder(dto.getOrder() != null ? dto.getOrder() : i)
                .optionText(dto.getText())
                .build();
            question.addOption(option);
        }
    }

    private void replaceAnswers(Question question, List<QuestionAnswerDto> answerDtos) {
        question.getAnswers().clear();
        if (answerDtos == null) {
            return;
        }

        for (int i = 0; i < answerDtos.size(); i++) {
            QuestionAnswerDto dto = answerDtos.get(i);
            if (dto == null) {
                continue;
            }
            QuestionAnswer answer = QuestionAnswer.builder()
                .answerType(normalizeAnswerType(dto.getType()))
                .answerValue(trimToNull(dto.getValue()))
                .answerOrder(dto.getOrder() != null ? dto.getOrder() : defaultAnswerOrder(question.getType(), i))
                .build();
            question.addAnswer(answer);
        }
    }

    private void replacePairs(Question question, List<QuestionMatchPairDto> pairDtos) {
        question.getMatchPairs().clear();
        if (pairDtos == null) {
            return;
        }

        for (int i = 0; i < pairDtos.size(); i++) {
            QuestionMatchPairDto dto = pairDtos.get(i);
            if (dto == null) {
                continue;
            }
            QuestionMatchPair pair = QuestionMatchPair.builder()
                .pairOrder(dto.getOrder() != null ? dto.getOrder() : i)
                .leftSide(dto.getLeft())
                .rightSide(dto.getRight())
                .build();
            question.addMatchPair(pair);
        }
    }

    private void replaceSubQuestions(Question question, List<SubQuestionDto> subQuestionDtos) {
        question.getSubQuestions().clear();
        if (subQuestionDtos == null) {
            return;
        }

        for (int i = 0; i < subQuestionDtos.size(); i++) {
            SubQuestionDto dto = subQuestionDtos.get(i);
            if (dto == null) {
                continue;
            }

            SubQuestion subQuestion = SubQuestion.builder()
                .displayOrder(dto.getOrder() != null ? dto.getOrder() : i)
                .type(dto.getType())
                .questionText(dto.getQuestion())
                .points(dto.getPoints() != null ? dto.getPoints() : 1)
                .explanation(dto.getExplanation())
                .build();

            replaceSubQuestionOptions(subQuestion, dto.getOptions());
            replaceSubQuestionAnswers(subQuestion, dto.getAnswers());
            replaceSubQuestionPairs(subQuestion, dto.getPairs());
            pruneUnsupportedChildren(subQuestion);
            validateSubQuestion(subQuestion, "Sub-question " + (i + 1));

            question.addSubQuestion(subQuestion);
        }
    }

    private void replaceSubQuestionOptions(SubQuestion subQuestion, List<QuestionOptionDto> optionDtos) {
        subQuestion.getOptions().clear();
        if (optionDtos == null) {
            return;
        }

        for (int i = 0; i < optionDtos.size(); i++) {
            QuestionOptionDto dto = optionDtos.get(i);
            if (dto == null) {
                continue;
            }
            SubQuestionOption option = SubQuestionOption.builder()
                .optionOrder(dto.getOrder() != null ? dto.getOrder() : i)
                .optionText(dto.getText())
                .subQuestion(subQuestion)
                .build();
            subQuestion.getOptions().add(option);
        }
    }

    private void replaceSubQuestionAnswers(SubQuestion subQuestion, List<QuestionAnswerDto> answerDtos) {
        subQuestion.getAnswers().clear();
        if (answerDtos == null) {
            return;
        }

        for (int i = 0; i < answerDtos.size(); i++) {
            QuestionAnswerDto dto = answerDtos.get(i);
            if (dto == null) {
                continue;
            }
            SubQuestionAnswer answer = SubQuestionAnswer.builder()
                .answerType(normalizeAnswerType(dto.getType()))
                .answerValue(trimToNull(dto.getValue()))
                .answerOrder(dto.getOrder() != null ? dto.getOrder() : defaultAnswerOrder(subQuestion.getType(), i))
                .subQuestion(subQuestion)
                .build();
            subQuestion.getAnswers().add(answer);
        }
    }

    private void replaceSubQuestionPairs(SubQuestion subQuestion, List<QuestionMatchPairDto> pairDtos) {
        subQuestion.getMatchPairs().clear();
        if (pairDtos == null) {
            return;
        }

        for (int i = 0; i < pairDtos.size(); i++) {
            QuestionMatchPairDto dto = pairDtos.get(i);
            if (dto == null) {
                continue;
            }
            SubQuestionMatchPair pair = SubQuestionMatchPair.builder()
                .pairOrder(dto.getOrder() != null ? dto.getOrder() : i)
                .leftSide(dto.getLeft())
                .rightSide(dto.getRight())
                .subQuestion(subQuestion)
                .build();
            subQuestion.getMatchPairs().add(pair);
        }
    }

    private void pruneUnsupportedChildren(Question question) {
        if (question.getType() == null) {
            return;
        }

        switch (question.getType()) {
            case MCQ, MULTI_CORRECT, ARRANGE_SEQUENCE -> {
                question.getMatchPairs().clear();
                question.getSubQuestions().clear();
            }
            case TRUE_FALSE -> {
                question.getOptions().clear();
                question.getMatchPairs().clear();
                question.getSubQuestions().clear();
            }
            case MATCH_PAIR -> {
                question.getOptions().clear();
                question.getAnswers().clear();
                question.getSubQuestions().clear();
            }
            case COMPREHENSIVE -> {
                question.getOptions().clear();
                question.getAnswers().clear();
                question.getMatchPairs().clear();
            }
        }
    }

    private void pruneUnsupportedChildren(SubQuestion subQuestion) {
        if (subQuestion.getType() == null) {
            return;
        }

        switch (subQuestion.getType()) {
            case MCQ, MULTI_CORRECT, ARRANGE_SEQUENCE -> subQuestion.getMatchPairs().clear();
            case TRUE_FALSE -> {
                subQuestion.getOptions().clear();
                subQuestion.getMatchPairs().clear();
            }
            case MATCH_PAIR -> {
                subQuestion.getOptions().clear();
                subQuestion.getAnswers().clear();
            }
            case COMPREHENSIVE -> throw new QuestionValidationException("Sub-questions cannot be comprehensive");
        }
    }

    private void resolvePoints(Question question) {
        if (question.getType() == QuestionType.COMPREHENSIVE) {
            int total = question.getSubQuestions().stream()
                .map(SubQuestion::getPoints)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
            question.setPoints(total);
        } else if (question.getPoints() == null) {
            question.setPoints(1);
        }
    }

    private void validateQuestion(Question question) {
        if (question.getType() == null) {
            throw new QuestionValidationException("Question type is required");
        }
        if (!hasText(question.getQuestionText())) {
            throw new QuestionValidationException("Question text is required");
        }
        if (!hasText(question.getDifficulty())) {
            throw new QuestionValidationException("Difficulty is required");
        }
        if (!isValidDifficulty(question.getDifficulty())) {
            throw new QuestionValidationException("Difficulty must be Easy, Medium, or Hard");
        }
        if (question.getPoints() == null || question.getPoints() < 1) {
            throw new QuestionValidationException("Points must be at least 1");
        }

        switch (question.getType()) {
            case MCQ -> {
                requireOptionCount(question.getOptions(), "MCQ", 2);
                requireSingleIndexAnswer(question.getAnswers(), question.getOptions().size(), "MCQ");
            }
            case TRUE_FALSE -> requireSingleBooleanAnswer(question.getAnswers(), "True/False");
            case MULTI_CORRECT -> {
                requireOptionCount(question.getOptions(), "Multi-correct", 2);
                requireIndexedAnswerList(question.getAnswers(), question.getOptions().size(), "Multi-correct", false);
            }
            case MATCH_PAIR -> requireMatchPairs(question.getMatchPairs(), "Match-pair");
            case ARRANGE_SEQUENCE -> {
                requireOptionCount(question.getOptions(), "Arrange-sequence", 2);
                requireOneBasedIndexedAnswerList(question.getAnswers(), question.getOptions().size(), "Arrange-sequence", true);
                if (question.getAnswers().size() != question.getOptions().size()) {
                    throw new QuestionValidationException("Arrange-sequence options and answers must contain the same number of items");
                }
            }
            case COMPREHENSIVE -> {
                if (question.getSubQuestions() == null || question.getSubQuestions().isEmpty()) {
                    throw new QuestionValidationException("Comprehensive question requires at least 1 sub-question");
                }
                for (int i = 0; i < question.getSubQuestions().size(); i++) {
                    validateSubQuestion(question.getSubQuestions().get(i), "Sub-question " + (i + 1));
                }
            }
        }
    }

    private void validateSubQuestion(SubQuestion subQuestion, String label) {
        if (subQuestion == null) {
            throw new QuestionValidationException(label + " is required");
        }
        if (subQuestion.getType() == null) {
            throw new QuestionValidationException(label + " type is required");
        }
        if (!hasText(subQuestion.getQuestionText())) {
            throw new QuestionValidationException(label + " text is required");
        }
        if (subQuestion.getPoints() == null || subQuestion.getPoints() < 1) {
            throw new QuestionValidationException(label + " points must be at least 1");
        }

        switch (subQuestion.getType()) {
            case MCQ -> {
                requireOptionCount(subQuestion.getOptions(), label + " MCQ", 2);
                requireSingleIndexAnswer(subQuestion.getAnswers(), subQuestion.getOptions().size(), label + " MCQ");
            }
            case TRUE_FALSE -> requireSingleBooleanAnswer(subQuestion.getAnswers(), label + " True/False");
            case MULTI_CORRECT -> {
                requireOptionCount(subQuestion.getOptions(), label + " Multi-correct", 2);
                requireIndexedAnswerList(subQuestion.getAnswers(), subQuestion.getOptions().size(), label + " Multi-correct", false);
            }
            case MATCH_PAIR -> requireMatchPairs(subQuestion.getMatchPairs(), label + " Match-pair");
            case ARRANGE_SEQUENCE -> {
                requireOptionCount(subQuestion.getOptions(), label + " Arrange-sequence", 2);
                requireOneBasedIndexedAnswerList(subQuestion.getAnswers(), subQuestion.getOptions().size(), label + " Arrange-sequence", true);
                if (subQuestion.getAnswers().size() != subQuestion.getOptions().size()) {
                    throw new QuestionValidationException(label + " Arrange-sequence options and answers must contain the same number of items");
                }
            }
            case COMPREHENSIVE -> throw new QuestionValidationException(label + " cannot itself be comprehensive");
        }
    }

    private void requireOptionCount(List<?> options, String label, int minCount) {
        if (options == null || options.size() < minCount) {
            throw new QuestionValidationException(label + " requires at least " + minCount + " options");
        }
    }

    private void requireSingleIndexAnswer(List<?> answers, int optionCount, String label) {
        if (answers == null || answers.size() != 1) {
            throw new QuestionValidationException(label + " requires exactly 1 answer");
        }
        Object answer = answers.get(0);
        requireAnswerType(extractAnswerType(answer), "index", label + " answer type must be index");
        validateIndexValue(extractAnswerValue(answer), optionCount, label + " answer index out of range");
    }

    private void requireSingleBooleanAnswer(List<?> answers, String label) {
        if (answers == null || answers.size() != 1) {
            throw new QuestionValidationException(label + " requires exactly 1 answer");
        }
        Object answer = answers.get(0);
        requireAnswerType(extractAnswerType(answer), "boolean", label + " answer type must be boolean");
        validateBooleanValue(extractAnswerValue(answer), label + " answer value must be true or false");
    }

    private void requireIndexedAnswerList(List<?> answers, int optionCount, String label, boolean ordered) {
        validateIndexedAnswerList(answers, optionCount, label, ordered, false);
    }

    private void requireOneBasedIndexedAnswerList(List<?> answers, int optionCount, String label, boolean ordered) {
        validateIndexedAnswerList(answers, optionCount, label, ordered, true);
    }

    private void validateIndexedAnswerList(List<?> answers, int optionCount, String label, boolean ordered, boolean oneBased) {
        if (answers == null || answers.isEmpty()) {
            throw new QuestionValidationException(label + " requires at least 1 answer");
        }
        List<?> sortedAnswers = answers;
        if (ordered) {
            sortedAnswers = answers.stream()
                .sorted(Comparator.comparingInt(this::extractAnswerOrder))
                .collect(Collectors.toList());
        }
        for (Object answer : sortedAnswers) {
            requireAnswerType(extractAnswerType(answer), "index", label + " answer type must be index");
            validateIndexValue(extractAnswerValue(answer), optionCount, label + " answer index out of range", oneBased);
        }
    }

    private void requireMatchPairs(List<?> pairs, String label) {
        if (pairs == null || pairs.size() < 2) {
            throw new QuestionValidationException(label + " requires at least 2 pairs");
        }
        boolean invalid = pairs.stream().anyMatch(pair -> {
            if (pair instanceof QuestionMatchPair questionPair) {
                return !hasText(questionPair.getLeftSide()) || !hasText(questionPair.getRightSide());
            }
            SubQuestionMatchPair subQuestionPair = (SubQuestionMatchPair) pair;
            return !hasText(subQuestionPair.getLeftSide()) || !hasText(subQuestionPair.getRightSide());
        });
        if (invalid) {
            throw new QuestionValidationException(label + " requires non-empty left and right values for every pair");
        }
    }

    private Subject resolveSubject(SubjectDto dto, boolean defaultIfMissing) {
        SubjectDto effectiveDto = dto;
        if (effectiveDto == null && defaultIfMissing) {
            effectiveDto = new SubjectDto();
            effectiveDto.setName(DEFAULT_SUBJECT);
        }
        if (effectiveDto == null) {
            return null;
        }
        if (effectiveDto.getId() != null) {
            Long subjectId = effectiveDto.getId();
            return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new QuestionValidationException("Subject not found: " + subjectId));
        }

        String name = trimToNull(effectiveDto.getName());
        if (name == null) {
            if (defaultIfMissing) {
                name = DEFAULT_SUBJECT;
            } else {
                throw new QuestionValidationException("Subject name is required");
            }
        }

        String subjectName = name;
        return subjectRepository.findByNameIgnoreCase(subjectName)
            .orElseGet(() -> subjectRepository.save(Subject.builder().name(subjectName).build()));
    }

    private Book resolveBook(BookDto dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getId() != null) {
            return bookRepository.findById(dto.getId())
                .orElseThrow(() -> new QuestionValidationException("Book not found: " + dto.getId()));
        }

        String name = trimToNull(dto.getName());
        String edition = trimToNull(dto.getEdition());
        String isbn = trimToNull(dto.getIsbn());
        if (name == null && edition == null && isbn == null) {
            return null;
        }
        if (name == null) {
            throw new QuestionValidationException("Book name is required when book details are provided");
        }

        return bookRepository.findExisting(name, edition, isbn)
            .orElseGet(() -> bookRepository.save(Book.builder()
                .name(name)
                .edition(edition)
                .isbn(isbn)
                .build()));
    }

    public QuestionResponse mapToResponse(Question question) {
        QuestionResponse response = new QuestionResponse();
        response.setId(question.getId());
        response.setType(question.getType());
        response.setQuestion(question.getQuestionText());
        response.setInstruction(question.getInstruction());
        response.setOptions(question.getOptions().stream().map(option -> {
            QuestionOptionDto dto = new QuestionOptionDto();
            dto.setOrder(option.getOptionOrder());
            dto.setText(option.getOptionText());
            return dto;
        }).collect(Collectors.toList()));
        response.setAnswers(question.getAnswers().stream().map(answer -> {
            QuestionAnswerDto dto = new QuestionAnswerDto();
            dto.setOrder(answer.getAnswerOrder());
            dto.setType(answer.getAnswerType());
            dto.setValue(answer.getAnswerValue());
            return dto;
        }).collect(Collectors.toList()));
        response.setPairs(question.getMatchPairs().stream().map(pair -> {
            QuestionMatchPairDto dto = new QuestionMatchPairDto();
            dto.setOrder(pair.getPairOrder());
            dto.setLeft(pair.getLeftSide());
            dto.setRight(pair.getRightSide());
            return dto;
        }).collect(Collectors.toList()));
        response.setSubQuestions(question.getSubQuestions().stream().map(this::mapSubQuestion).collect(Collectors.toList()));
        response.setDifficulty(question.getDifficulty());
        response.setSubject(mapSubject(question.getSubject()));
        response.setPoints(question.getPoints());
        response.setExplanation(question.getExplanation());
        response.setBook(mapBook(question.getBook()));
        response.setEtgNumber(question.getEtgNumber());
        response.setPageNumber(question.getPageNumber());
        response.setQuestionNumber(question.getQuestionNumber());
        response.setCreatedAt(question.getCreatedAt());
        response.setUpdatedAt(question.getUpdatedAt());
        return response;
    }

    private SubQuestionDto mapSubQuestion(SubQuestion subQuestion) {
        SubQuestionDto dto = new SubQuestionDto();
        dto.setId(subQuestion.getId());
        dto.setOrder(subQuestion.getDisplayOrder());
        dto.setType(subQuestion.getType());
        dto.setQuestion(subQuestion.getQuestionText());
        dto.setPoints(subQuestion.getPoints());
        dto.setExplanation(subQuestion.getExplanation());
        dto.setOptions(subQuestion.getOptions().stream().map(option -> {
            QuestionOptionDto optionDto = new QuestionOptionDto();
            optionDto.setOrder(option.getOptionOrder());
            optionDto.setText(option.getOptionText());
            return optionDto;
        }).collect(Collectors.toList()));
        dto.setAnswers(subQuestion.getAnswers().stream().map(answer -> {
            QuestionAnswerDto answerDto = new QuestionAnswerDto();
            answerDto.setOrder(answer.getAnswerOrder());
            answerDto.setType(answer.getAnswerType());
            answerDto.setValue(answer.getAnswerValue());
            return answerDto;
        }).collect(Collectors.toList()));
        dto.setPairs(subQuestion.getMatchPairs().stream().map(pair -> {
            QuestionMatchPairDto pairDto = new QuestionMatchPairDto();
            pairDto.setOrder(pair.getPairOrder());
            pairDto.setLeft(pair.getLeftSide());
            pairDto.setRight(pair.getRightSide());
            return pairDto;
        }).collect(Collectors.toList()));
        return dto;
    }

    private SubjectDto mapSubject(Subject subject) {
        if (subject == null) {
            return null;
        }
        SubjectDto dto = new SubjectDto();
        dto.setId(subject.getId());
        dto.setName(subject.getName());
        return dto;
    }

    private BookDto mapBook(Book book) {
        if (book == null) {
            return null;
        }
        BookDto dto = new BookDto();
        dto.setId(book.getId());
        dto.setName(book.getName());
        dto.setEdition(book.getEdition());
        dto.setIsbn(book.getIsbn());
        return dto;
    }

    private Question findOrThrow(Long id) {
        return questionRepository.findById(id)
            .orElseThrow(() -> new QuestionNotFoundException(id));
    }

    private String extractAnswerType(Object answer) {
        if (answer instanceof QuestionAnswer questionAnswer) {
            return questionAnswer.getAnswerType();
        }
        return ((SubQuestionAnswer) answer).getAnswerType();
    }

    private String extractAnswerValue(Object answer) {
        if (answer instanceof QuestionAnswer questionAnswer) {
            return questionAnswer.getAnswerValue();
        }
        return ((SubQuestionAnswer) answer).getAnswerValue();
    }

    private int extractAnswerOrder(Object answer) {
        Integer order;
        if (answer instanceof QuestionAnswer questionAnswer) {
            order = questionAnswer.getAnswerOrder();
        } else {
            order = ((SubQuestionAnswer) answer).getAnswerOrder();
        }
        return order == null ? Integer.MAX_VALUE : order;
    }

    private void requireAnswerType(String actualType, String expectedType, String message) {
        if (!expectedType.equals(normalizeAnswerType(actualType))) {
            throw new QuestionValidationException(message);
        }
    }

    private void validateIndexValue(String value, int optionCount, String message) {
        validateIndexValue(value, optionCount, message, false);
    }

    private void validateIndexValue(String value, int optionCount, String message, boolean oneBased) {
        try {
            int index = Integer.parseInt(value);
            if (oneBased ? index < 1 || index > optionCount : index < 0 || index >= optionCount) {
                throw new QuestionValidationException(message);
            }
        } catch (NumberFormatException ex) {
            throw new QuestionValidationException(message);
        }
    }

    private void validateBooleanValue(String value, String message) {
        String normalized = trimToNull(value);
        if (!"true".equalsIgnoreCase(normalized) && !"false".equalsIgnoreCase(normalized)) {
            throw new QuestionValidationException(message);
        }
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : defaultValue;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String normalizeAnswerType(String answerType) {
        return answerType == null ? null : answerType.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isValidDifficulty(String difficulty) {
        return "Easy".equals(difficulty) || "Medium".equals(difficulty) || "Hard".equals(difficulty);
    }

    private Integer defaultAnswerOrder(QuestionType type, int index) {
        return switch (type) {
            case MULTI_CORRECT, ARRANGE_SEQUENCE -> index;
            default -> null;
        };
    }
}
