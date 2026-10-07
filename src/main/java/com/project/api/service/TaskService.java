package com.project.api.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.api.contract.TaskRequest;
import com.project.api.contract.TaskResponse;
import com.project.api.enums.TaskStatus;
import com.project.api.exception.NotFoundException;
import com.project.api.model.CurrentUser;
import com.project.api.model.Task;
import com.project.api.repository.TaskRepository;

@Service
public class TaskService {

    private static final Sort BY_ID = Sort.by("id");

    private final TaskRepository repository;
    private final UserService userService;

    public TaskService(TaskRepository repository, UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    @Transactional
    public TaskResponse create(TaskRequest request, CurrentUser user) {
        TaskStatus status = request.status() == null ? TaskStatus.TODO : request.status();
        Task task = new Task(userService.require(user.username()), request.title(), request.description(), status,
                request.dueDate());
        return TaskResponse.from(repository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(TaskStatus status, CurrentUser user) {
        List<Task> tasks;
        if (user.admin()) {
            tasks = status == null ? repository.findAll(BY_ID) : repository.findByStatus(status, BY_ID);
        } else {
            tasks = status == null
                    ? repository.findByOwnerUsername(user.username(), BY_ID)
                    : repository.findByOwnerUsernameAndStatus(user.username(), status, BY_ID);
        }
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id, CurrentUser user) {
        return TaskResponse.from(find(id, user));
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request, CurrentUser user) {
        Task task = find(id, user);
        TaskStatus status = request.status() == null ? task.getStatus() : request.status();
        task.update(request.title(), request.description(), status, request.dueDate());
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(Long id, CurrentUser user) {
        repository.delete(find(id, user));
    }

    private Task find(Long id, CurrentUser user) {
        var task = user.admin() ? repository.findById(id) : repository.findByIdAndOwnerUsername(id, user.username());
        return task.orElseThrow(() -> new NotFoundException("Task", id));
    }
}
