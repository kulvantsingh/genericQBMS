package com.questionbank.service;

import com.questionbank.dto.QuestionRequest;
import com.questionbank.dto.QuestionResponse;
import com.questionbank.dto.StatsResponse;
import com.questionbank.exception.QuestionNotFoundException;
import com.questionbank.exception.QuestionValidationException;
import com.questionbank.model.ComprehensiveSubQuestion;
import com.questionbank.model.Question;
import com.questionbank.model.QuestionType;
import com.questionbank.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionService {

    private final QuestionRepository questionRepository;

    // ── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public QuestionResponse create(QuestionRequest request) {
        validate(request);
        Question question = mapToEntity(request);
        Question saved = questionRepository.save(question);
        log.info("Created question id={} type={}", saved.getId(), saved.getType());
        return mapToResponse(saved);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public QuestionResponse getById(Long id) {
        return mapToResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> getAll(String type, String difficulty, String subject, String search) {
        QuestionType qType = (type != null && !type.isBlank()) ? QuestionType.fromValue(type) : null;
        String diff  = (difficulty != null && !difficulty.isBlank()) ? difficulty : null;
        String subj  = (subject   != null && !subject.isBlank())    ? subject    : null;
        String srch  = (search    != null && !search.isBlank())     ? search.trim().toLowerCase(Locale.ROOT) : null;

        return questionRepository
            .findAllWithFilters(qType, diff, subj)
            .stream()
            .filter(question -> srch == null
                || (question.getQuestion() != null
                && question.getQuestion().toLowerCase(Locale.ROOT).contains(srch))
                || (question.getInstruction() != null
                && question.getInstruction().toLowerCase(Locale.ROOT).contains(srch)))
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public QuestionResponse update(Long id, QuestionRequest request) {
        Question existing = findOrThrow(id);
        validate(request);

        existing.setType(request.getType());
        existing.setQuestion(request.getQuestion());
        existing.setInstruction(request.getInstruction());
        existing.setOptions(request.getOptions());
        existing.setCorrectAnswer(request.getCorrectAnswer());
        existing.setPairs(request.getPairs());
        existing.setSubQuestions(request.getSubQuestions());
        existing.setDifficulty(request.getDifficulty() != null ? request.getDifficulty() : "Medium");
        existing.setSubject(request.getSubject() != null ? request.getSubject() : "General Knowledge");
        existing.setPoints(resolvePoints(request));
        existing.setExplanation(request.getExplanation());
        existing.setTags(request.getTags());
        existing.setBookName(request.getBookName());
        existing.setBookEdition(request.getBookEdition());
        existing.setIsbn(request.getIsbn());
        existing.setEtgNumber(request.getEtgNumber());
        existing.setPageNumber(request.getPageNumber());
        existing.setQuestionNumber(request.getQuestionNumber());

        Question updated = questionRepository.save(existing);
        log.info("Updated question id={}", id);
        return mapToResponse(updated);
    }

    // ── Patch (partial update) ────────────────────────────────────────────────

    @Transactional
    public QuestionResponse patch(Long id, QuestionRequest request) {
        Question existing = findOrThrow(id);

        if (request.getQuestion()     != null) existing.setQuestion(request.getQuestion());
        if (request.getInstruction()  != null) existing.setInstruction(request.getInstruction());
        if (request.getOptions()      != null) existing.setOptions(request.getOptions());
        if (request.getCorrectAnswer()!= null) existing.setCorrectAnswer(request.getCorrectAnswer());
        if (request.getPairs()        != null) existing.setPairs(request.getPairs());
        if (request.getSubQuestions() != null) existing.setSubQuestions(request.getSubQuestions());
        if (request.getDifficulty()   != null) existing.setDifficulty(request.getDifficulty());
        if (request.getSubject()      != null) existing.setSubject(request.getSubject());
        if (request.getPoints()       != null) existing.setPoints(request.getPoints());
        if (request.getExplanation()  != null) existing.setExplanation(request.getExplanation());
        if (request.getTags()         != null) existing.setTags(request.getTags());
        if (request.getBookName()     != null) existing.setBookName(request.getBookName());
        if (request.getBookEdition()  != null) existing.setBookEdition(request.getBookEdition());
        if (request.getIsbn()         != null) existing.setIsbn(request.getIsbn());
        if (request.getEtgNumber()    != null) existing.setEtgNumber(request.getEtgNumber());
        if (request.getPageNumber()   != null) existing.setPageNumber(request.getPageNumber());
        if (request.getQuestionNumber()!= null) existing.setQuestionNumber(request.getQuestionNumber());

        if (existing.getType() == QuestionType.COMPREHENSIVE && request.getSubQuestions() != null) {
            existing.setPoints(existing.getSubQuestions().stream()
                .filter(Objects::nonNull)
                .map(ComprehensiveSubQuestion::getPoints)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum));
        }

        return mapToResponse(questionRepository.save(existing));
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long id) {
        findOrThrow(id);
        questionRepository.deleteById(id);
        log.info("Deleted question id={}", id);
    }

    // ── Stats ────────────────────────────────────────────────────────────────

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
            bySubject.put(row.get("subject").toString(), ((Number) row.get("count")).longValue()));

        return StatsResponse.builder()
            .total(total)
            .byType(byType)
            .byDifficulty(byDifficulty)
            .bySubject(bySubject)
            .build();
    }

    // ── Validation ───────────────────────────────────────────────────────────

    private void validate(QuestionRequest req) {
        switch (req.getType()) {
            case MCQ -> {
                if (req.getOptions() == null || req.getOptions().size() < 2)
                    throw new QuestionValidationException("MCQ requires at least 2 options");
                if (req.getCorrectAnswer() == null || !req.getCorrectAnswer().isInt())
                    throw new QuestionValidationException("MCQ correctAnswer must be an integer index");
                int idx = req.getCorrectAnswer().asInt();
                if (idx < 0 || idx >= req.getOptions().size())
                    throw new QuestionValidationException("MCQ correctAnswer index out of range");
            }
            case TRUE_FALSE -> {
                if (req.getCorrectAnswer() == null || !req.getCorrectAnswer().isBoolean())
                    throw new QuestionValidationException("True/False correctAnswer must be true or false");
            }
            case MULTI_CORRECT -> {
                if (req.getOptions() == null || req.getOptions().size() < 2)
                    throw new QuestionValidationException("Multi-correct requires at least 2 options");
                if (req.getCorrectAnswer() == null || !req.getCorrectAnswer().isArray() || req.getCorrectAnswer().isEmpty())
                    throw new QuestionValidationException("Multi-correct correctAnswer must be a non-empty array of indices");
            }
            case MATCH_PAIR -> {
                if (req.getPairs() == null || req.getPairs().size() < 2)
                    throw new QuestionValidationException("Match-pair requires at least 2 pairs");
                boolean invalid = req.getPairs().stream()
                    .anyMatch(p -> p.getLeft() == null || p.getLeft().isBlank()
                               || p.getRight() == null || p.getRight().isBlank());
                if (invalid)
                    throw new QuestionValidationException("All pairs must have non-empty left and right values");
            }
            case ARRANGE_SEQUENCE -> {
                if (req.getOptions() == null || req.getOptions().size() < 2) {
                    throw new QuestionValidationException("Arrange-sequence requires at least 2 items");
                }
                if (req.getCorrectAnswer() == null || !req.getCorrectAnswer().isArray() || req.getCorrectAnswer().size() < 2) {
                    throw new QuestionValidationException("Arrange-sequence correctAnswer must be an array with at least 2 items");
                }
                if (req.getCorrectAnswer().size() != req.getOptions().size()) {
                    throw new QuestionValidationException("Arrange-sequence options and correctAnswer must contain the same number of items");
                }
            }
            case COMPREHENSIVE -> validateComprehensive(req);
        }
    }

    private void validateComprehensive(QuestionRequest req) {
        if (req.getSubQuestions() == null || req.getSubQuestions().isEmpty()) {
            throw new QuestionValidationException("Comprehensive question requires at least 1 sub-question");
        }

        for (int i = 0; i < req.getSubQuestions().size(); i++) {
            ComprehensiveSubQuestion subQuestion = req.getSubQuestions().get(i);
            validateSubQuestion(subQuestion, i + 1);
        }
    }

    private void validateSubQuestion(ComprehensiveSubQuestion subQuestion, int index) {
        if (subQuestion == null) {
            throw new QuestionValidationException("Sub-question " + index + " is required");
        }
        if (subQuestion.getType() == null) {
            throw new QuestionValidationException("Sub-question " + index + " type is required");
        }
        if (subQuestion.getQuestion() == null || subQuestion.getQuestion().isBlank()) {
            throw new QuestionValidationException("Sub-question " + index + " text is required");
        }
        if (subQuestion.getPoints() == null || subQuestion.getPoints() < 1) {
            throw new QuestionValidationException("Sub-question " + index + " points must be at least 1");
        }

        switch (subQuestion.getType()) {
            case MCQ -> {
                if (subQuestion.getOptions() == null || subQuestion.getOptions().size() < 2) {
                    throw new QuestionValidationException("Sub-question " + index + " MCQ requires at least 2 options");
                }
                if (subQuestion.getCorrectAnswer() == null || !subQuestion.getCorrectAnswer().isInt()) {
                    throw new QuestionValidationException("Sub-question " + index + " MCQ correctAnswer must be an integer index");
                }
                int answerIndex = subQuestion.getCorrectAnswer().asInt();
                if (answerIndex < 0 || answerIndex >= subQuestion.getOptions().size()) {
                    throw new QuestionValidationException("Sub-question " + index + " MCQ correctAnswer index out of range");
                }
            }
            case TRUE_FALSE -> {
                if (subQuestion.getCorrectAnswer() == null || !subQuestion.getCorrectAnswer().isBoolean()) {
                    throw new QuestionValidationException("Sub-question " + index + " True/False correctAnswer must be true or false");
                }
            }
            case MULTI_CORRECT -> {
                if (subQuestion.getOptions() == null || subQuestion.getOptions().size() < 2) {
                    throw new QuestionValidationException("Sub-question " + index + " Multi-correct requires at least 2 options");
                }
                if (subQuestion.getCorrectAnswer() == null || !subQuestion.getCorrectAnswer().isArray() || subQuestion.getCorrectAnswer().isEmpty()) {
                    throw new QuestionValidationException("Sub-question " + index + " Multi-correct correctAnswer must be a non-empty array of indices");
                }
            }
            case MATCH_PAIR -> {
                if (subQuestion.getPairs() == null || subQuestion.getPairs().size() < 2) {
                    throw new QuestionValidationException("Sub-question " + index + " Match-pair requires at least 2 pairs");
                }
                boolean invalidPair = subQuestion.getPairs().stream()
                    .anyMatch(p -> p.getLeft() == null || p.getLeft().isBlank()
                        || p.getRight() == null || p.getRight().isBlank());
                if (invalidPair) {
                    throw new QuestionValidationException("Sub-question " + index + " all pairs must have non-empty left and right values");
                }
            }
            case ARRANGE_SEQUENCE -> {
                if (subQuestion.getOptions() == null || subQuestion.getOptions().size() < 2) {
                    throw new QuestionValidationException("Sub-question " + index + " Arrange-sequence requires at least 2 items");
                }
                if (subQuestion.getCorrectAnswer() == null || !subQuestion.getCorrectAnswer().isArray() || subQuestion.getCorrectAnswer().size() < 2) {
                    throw new QuestionValidationException("Sub-question " + index + " Arrange-sequence correctAnswer must be an array with at least 2 items");
                }
                if (subQuestion.getCorrectAnswer().size() != subQuestion.getOptions().size()) {
                    throw new QuestionValidationException("Sub-question " + index + " Arrange-sequence options and correctAnswer must contain the same number of items");
                }
            }
            case COMPREHENSIVE -> throw new QuestionValidationException("Sub-question " + index + " cannot itself be comprehensive");
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private Question mapToEntity(QuestionRequest req) {
        return Question.builder()
            .type(req.getType())
            .question(req.getQuestion())
            .instruction(req.getInstruction())
            .options(req.getOptions())
            .correctAnswer(req.getCorrectAnswer())
            .pairs(req.getPairs())
            .subQuestions(req.getSubQuestions())
            .difficulty(req.getDifficulty() != null ? req.getDifficulty() : "Medium")
            .subject(req.getSubject()       != null ? req.getSubject()     : "General Knowledge")
            .points(resolvePoints(req))
            .explanation(req.getExplanation())
            .tags(req.getTags())
            .bookName(req.getBookName())
            .bookEdition(req.getBookEdition())
            .isbn(req.getIsbn())
            .etgNumber(req.getEtgNumber())
            .pageNumber(req.getPageNumber())
            .questionNumber(req.getQuestionNumber())
            .build();
    }

    private Integer resolvePoints(QuestionRequest req) {
        if (req.getType() == QuestionType.COMPREHENSIVE) {
            return req.getSubQuestions().stream()
                .filter(Objects::nonNull)
                .map(ComprehensiveSubQuestion::getPoints)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
        }
        return req.getPoints() != null ? req.getPoints() : 1;
    }

    public QuestionResponse mapToResponse(Question q) {
        QuestionResponse res = new QuestionResponse();
        res.setId(q.getId());
        res.setType(q.getType());
        res.setQuestion(q.getQuestion());
        res.setInstruction(q.getInstruction());
        res.setOptions(q.getOptions());
        res.setCorrectAnswer(q.getCorrectAnswer());
        res.setPairs(q.getPairs());
        res.setSubQuestions(q.getSubQuestions());
        res.setDifficulty(q.getDifficulty());
        res.setSubject(q.getSubject());
        res.setPoints(q.getPoints());
        res.setExplanation(q.getExplanation());
        res.setTags(q.getTags());
        res.setBookName(q.getBookName());
        res.setBookEdition(q.getBookEdition());
        res.setIsbn(q.getIsbn());
        res.setEtgNumber(q.getEtgNumber());
        res.setPageNumber(q.getPageNumber());
        res.setQuestionNumber(q.getQuestionNumber());
        res.setCreatedAt(q.getCreatedAt());
        res.setUpdatedAt(q.getUpdatedAt());
        return res;
    }

    private Question findOrThrow(Long id) {
        return questionRepository.findById(id)
            .orElseThrow(() -> new QuestionNotFoundException(id));
    }
}
