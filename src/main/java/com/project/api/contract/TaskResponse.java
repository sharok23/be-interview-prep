package com.project.api.contract;

import java.time.Instant;
import java.time.LocalDate;

import com.project.api.enums.TaskStatus;
import com.project.api.model.Task;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        Instant createdAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getDueDate(), task.getCreatedAt());
    }
}
