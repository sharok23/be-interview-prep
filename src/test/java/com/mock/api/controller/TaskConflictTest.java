package com.mock.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mock.api.model.Task;
import com.mock.api.repository.TaskRepository;

@SpringBootTest
@AutoConfigureMockMvc
class TaskConflictTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskRepository repository;

    @Test
    void concurrentModificationReturns409InTheSharedErrorFormat() throws Exception {
        given(repository.findById(any())).willThrow(new ObjectOptimisticLockingFailureException(Task.class, 1L));

        mvc.perform(put("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"t\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("The resource was changed by another request, retry"))
                .andExpect(jsonPath("$.path").value("/api/tasks/1"));
    }
}
