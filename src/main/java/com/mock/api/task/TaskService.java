package com.mock.api.task;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mock.api.common.exception.NotFoundException;

@Service
public class TaskService {

    private static final Sort BY_ID = Sort.by("id");

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TaskResponse create(TaskRequest request) {
        TaskStatus status = request.status() == null ? TaskStatus.TODO : request.status();
        Task task = new Task(request.title().trim(), request.description(), status, request.dueDate());
        return TaskResponse.from(repository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(TaskStatus status) {
        List<Task> tasks = status == null ? repository.findAll(BY_ID) : repository.findByStatus(status, BY_ID);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        return TaskResponse.from(find(id));
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = find(id);
        TaskStatus status = request.status() == null ? task.getStatus() : request.status();
        task.update(request.title().trim(), request.description(), status, request.dueDate());
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private Task find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Task", id));
    }
}
