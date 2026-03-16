# Question Bank - Spring Boot REST API

## Tech Stack
- Java 17
- Spring Boot 3.2.4
- Spring Data JPA
- PostgreSQL (JSONB for flexible question data)
- Lombok
- Hypersistence Utils (JSONB mapping)

---

## Project Structure

```
src/main/java/com/questionbank/
├── QuestionBankApplication.java
├── config/
│   └── AppConfig.java              # CORS + Jackson config
├── controller/
│   └── QuestionController.java     # REST endpoints
├── dto/
│   ├── ApiResponse.java            # Generic response wrapper
│   ├── QuestionRequest.java        # Create/Update DTO
│   ├── QuestionResponse.java       # Response DTO
│   └── StatsResponse.java          # Stats DTO
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── QuestionNotFoundException.java
│   └── QuestionValidationException.java
├── model/
│   ├── MatchPair.java              # Embedded pair value object
│   ├── Question.java               # JPA Entity
│   └── QuestionType.java           # Enum: mcq, true_false, multi_correct, match_pair
├── repository/
│   └── QuestionRepository.java     # JPA + custom filter queries
└── service/
    └── QuestionService.java        # Business logic + validation
```

---

## Setup

### 1. Configure Database
Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/YOUR_DB
spring.datasource.username=YOUR_USER
spring.datasource.password=YOUR_PASSWORD
```

### 2. Run
```bash
mvn spring-boot:run
```
API starts at: `http://localhost:8080`

---

## API Endpoints

### Base URL: `/api/v1/questions`

| Method   | Endpoint               | Description              |
|----------|------------------------|--------------------------|
| `POST`   | `/`                    | Create a question        |
| `GET`    | `/`                    | List / filter questions  |
| `GET`    | `/{id}`                | Get question by ID       |
| `PUT`    | `/{id}`                | Full update              |
| `PATCH`  | `/{id}`                | Partial update           |
| `DELETE` | `/{id}`                | Delete question          |
| `GET`    | `/stats`               | Get DB statistics        |

### Query Parameters for `GET /`
| Param        | Example          | Description         |
|--------------|------------------|---------------------|
| `type`       | `mcq`            | Filter by type      |
| `difficulty` | `Hard`           | Filter by level     |
| `subject`    | `Mathematics`    | Filter by subject   |
| `search`     | `photosynthesis` | Full-text search    |

---

## Request / Response Examples

### POST — Create MCQ
```json
POST /api/v1/questions
{
  "type": "mcq",
  "question": "What is the capital of France?",
  "options": ["Berlin", "Madrid", "Paris", "Rome"],
  "correctAnswer": 2,
  "difficulty": "Easy",
  "subject": "Geography",
  "points": 1,
  "explanation": "Paris has been the capital of France since 987 AD."
}
```

### POST — Create True/False
```json
{
  "type": "true_false",
  "question": "The Earth is flat.",
  "correctAnswer": false,
  "difficulty": "Easy",
  "subject": "Science",
  "points": 1
}
```

### POST — Create Multi-Correct
```json
{
  "type": "multi_correct",
  "question": "Which of the following are prime numbers?",
  "options": ["2", "4", "7", "9", "11"],
  "correctAnswer": [0, 2, 4],
  "difficulty": "Medium",
  "subject": "Mathematics",
  "points": 2
}
```

### POST — Create Match the Pair
```json
{
  "type": "match_pair",
  "question": "Match the countries with their capitals.",
  "pairs": [
    { "left": "France",  "right": "Paris"  },
    { "left": "Germany", "right": "Berlin" },
    { "left": "Japan",   "right": "Tokyo"  }
  ],
  "difficulty": "Medium",
  "subject": "Geography",
  "points": 3
}
```

### GET — List with filters
```
GET /api/v1/questions?type=mcq&difficulty=Hard&subject=Mathematics
GET /api/v1/questions?search=capital
```

### GET Stats Response
```json
{
  "success": true,
  "data": {
    "total": 42,
    "byType": {
      "mcq": 20,
      "true_false": 10,
      "multi_correct": 8,
      "match_pair": 4
    },
    "byDifficulty": {
      "Easy": 15,
      "Medium": 20,
      "Hard": 7
    },
    "bySubject": {
      "Mathematics": 12,
      "Science": 10
    }
  }
}
```

### Standard API Response Wrapper
All endpoints return:
```json
{
  "success": true,
  "message": "Question created successfully",
  "data": { ... },
  "timestamp": "2026-03-13T10:00:00Z"
}
```

---

## correctAnswer Format by Type

| Type            | correctAnswer value       | Example          |
|-----------------|---------------------------|------------------|
| `mcq`           | Integer (option index)    | `2`              |
| `true_false`    | Boolean                   | `false`          |
| `multi_correct` | Array of integers         | `[0, 2, 4]`      |
| `match_pair`    | `null` (pairs hold answer)| `null`           |

---

## Build JAR
```bash
mvn clean package -DskipTests
java -jar target/question-bank-1.0.0.jar
```
