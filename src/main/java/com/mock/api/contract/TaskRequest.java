package com.mock.api.contract;

import java.time.LocalDate;

import com.mock.api.enums.TaskStatus;
import com.mock.api.model.Task;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank(message = "title is required")
        @Size(max = Task.TITLE_MAX, message = "title must be at most {max} characters")
        String title,

        @Size(max = Task.DESCRIPTION_MAX, message = "description must be at most {max} characters")
        String description,

        TaskStatus status,

        @FutureOrPresent(message = "dueDate cannot be in the past")
        LocalDate dueDate) {

    public TaskRequest {
        title = title == null ? null : title.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
