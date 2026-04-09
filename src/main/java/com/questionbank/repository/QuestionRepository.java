package com.questionbank.repository;

import com.questionbank.model.Question;
import com.questionbank.model.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    // ── Filtered queries ────────────────────────────────────────────────────

    @Query("""
        SELECT q FROM Question q
        LEFT JOIN q.subject subject
        WHERE (:type     IS NULL OR q.type       = :type)
          AND (:difficulty IS NULL OR q.difficulty = :difficulty)
          AND (:subject   IS NULL OR LOWER(subject.name) = :subject)
        ORDER BY q.createdAt DESC
        """)
    List<Question> findAllWithFilters(
        @Param("type")       QuestionType type,
        @Param("difficulty") String difficulty,
        @Param("subject")    String subject
    );

    // ── Stats queries ───────────────────────────────────────────────────────

    @Query("SELECT q.type AS type, COUNT(q) AS count FROM Question q GROUP BY q.type")
    List<Map<String, Object>> countByType();

    @Query("SELECT q.difficulty AS difficulty, COUNT(q) AS count FROM Question q GROUP BY q.difficulty")
    List<Map<String, Object>> countByDifficulty();

    @Query("""
        SELECT COALESCE(subject.name, 'Unassigned') AS subject, COUNT(q) AS count
        FROM Question q
        LEFT JOIN q.subject subject
        GROUP BY COALESCE(subject.name, 'Unassigned')
        ORDER BY count DESC
        """)
    List<Map<String, Object>> countBySubject();

    // ── By type ─────────────────────────────────────────────────────────────

    List<Question> findByTypeOrderByCreatedAtDesc(QuestionType type);

    List<Question> findByDifficultyOrderByCreatedAtDesc(String difficulty);

    List<Question> findBySubject_NameIgnoreCaseOrderByCreatedAtDesc(String subject);
}
