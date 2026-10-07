package com.project.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.api.enums.TaskStatus;
import com.project.api.model.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Override
    @EntityGraph(attributePaths = "owner")
    List<Task> findAll(Sort sort);

    @Override
    @EntityGraph(attributePaths = "owner")
    Optional<Task> findById(Long id);

    @EntityGraph(attributePaths = "owner")
    List<Task> findByStatus(TaskStatus status, Sort sort);

    @EntityGraph(attributePaths = "owner")
    List<Task> findByOwnerUsername(String username, Sort sort);

    @EntityGraph(attributePaths = "owner")
    List<Task> findByOwnerUsernameAndStatus(String username, TaskStatus status, Sort sort);

    @EntityGraph(attributePaths = "owner")
    Optional<Task> findByIdAndOwnerUsername(Long id, String username);
}
