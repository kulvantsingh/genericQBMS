# gqbms — Normalized Entity Classes

## Files generated (all in com/questionbank/model/)

| File | Replaces |
|---|---|
| Subject.java | `questions.subject` VARCHAR column |
| Book.java | `questions.book_name / book_edition / isbn` columns |
| Tag.java | `questions.tags` plain-text CSV column |
| Question.java | Original Question.java (JSONB fields removed) |
| QuestionOption.java | `questions.options` JSONB |
| QuestionAnswer.java | `questions.correct_answer` JSONB |
| QuestionMatchPair.java | `questions.pairs` JSONB |
| SubQuestion.java | `questions.sub_questions` JSONB top-level object |
| SubQuestionOption.java | sub_questions[].options JSONB |
| SubQuestionAnswer.java | sub_questions[].correctAnswer JSONB |
| SubQuestionMatchPair.java | sub_questions[].pairs JSONB |

## What to do next

### 1. Drop the old model files
Remove from src/main/java/com/questionbank/model/:
- ComprehensiveSubQuestion.java  (replaced by SubQuestion + SubQuestionOption/Answer/MatchPair)
- MatchPair.java                 (replaced by QuestionMatchPair / SubQuestionMatchPair)
  
Keep:
- QuestionType.java
- QuestionTypeConverter.java

### 2. Copy new files into your project
Copy all files from this folder into:
  D:\genericQBMS\src\main\java\com\questionbank\model\

### 3. Add Flyway to pom.xml
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
  </dependency>

### 4. Set DDL auto to validate
In application.properties:
  spring.jpa.hibernate.ddl-auto=validate

### 5. Update QuestionService, DTOs, and Repository
- QuestionRequest/QuestionResponse need to reference the new structure
  (ask Claude to generate updated DTOs and service)
- QuestionRepository queries that filter by subject now need
  q.subject.name instead of q.subject

### Answer type convention (QuestionAnswer.answerType)
| Question type     | answerType | answerValue example  | answerOrder |
|---|---|---|---|
| MCQ               | "index"    | "2"                  | null        |
| TRUE_FALSE        | "boolean"  | "true"               | null        |
| MULTI_CORRECT     | "index"    | "0", "3"             | 0, 1        |
| ARRANGE_SEQUENCE  | "index"    | "2", "0", "1"        | 0, 1, 2     |
| MATCH_PAIR        | — (uses QuestionMatchPair table instead) | | |
