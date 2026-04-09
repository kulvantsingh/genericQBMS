package com.questionbank.repository;

import com.questionbank.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("""
        SELECT b
        FROM Book b
        WHERE LOWER(b.name) = LOWER(:name)
          AND LOWER(COALESCE(b.edition, '')) = LOWER(COALESCE(:edition, ''))
          AND LOWER(COALESCE(b.isbn, '')) = LOWER(COALESCE(:isbn, ''))
        """)
    Optional<Book> findExisting(
        @Param("name") String name,
        @Param("edition") String edition,
        @Param("isbn") String isbn
    );
}
