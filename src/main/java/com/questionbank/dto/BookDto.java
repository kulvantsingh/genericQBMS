package com.questionbank.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookDto {

    private Long id;

    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String edition;

    @Size(max = 50)
    private String isbn;
}
