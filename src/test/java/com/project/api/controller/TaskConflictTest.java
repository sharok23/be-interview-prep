package com.project.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.project.api.enums.Role;
import com.project.api.model.Task;
import com.project.api.repository.TaskRepository;
import com.project.api.support.TestUsers;

@SpringBootTest
class TaskConflictTest {

    @Autowired
    private TestUsers testUsers;

    private TestUsers.Account account;
    private MockMvc mvc;

    @BeforeEach
    void signIn() {
        account = testUsers.create(Role.USER);
        mvc = testUsers.mockMvcAs(account);
    }

    @MockitoBean
    private TaskRepository repository;

    @Test
    void concurrentModificationReturns409InTheSharedErrorFormat() throws Exception {
        given(repository.findByIdAndOwnerUsername(any(), any())).willThrow(new ObjectOptimisticLockingFailureException(Task.class, 1L));

        mvc.perform(put("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"t\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("The resource was changed by another request, retry"))
                .andExpect(jsonPath("$.path").value("/api/tasks/1"));
    }
}
