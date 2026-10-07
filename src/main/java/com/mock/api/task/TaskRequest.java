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

    // Trim before validation runs so surrounding spaces don't count towards the length limits.
    public TaskRequest {
        title = title == null ? null : title.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }

    static final int TITLE_MAX = 100;
    static final int DESCRIPTION_MAX = 1000;
}
