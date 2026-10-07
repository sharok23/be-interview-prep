package com.mock.api.task;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank(message = "title is required")
        @Size(max = TITLE_MAX, message = "title must be at most {max} characters")
        String title,

        @Size(max = DESCRIPTION_MAX, message = "description must be at most {max} characters")
        String description,

        TaskStatus status,

        @FutureOrPresent(message = "dueDate cannot be in the past")
        LocalDate dueDate) {

    static final int TITLE_MAX = 100;
    static final int DESCRIPTION_MAX = 1000;
}
