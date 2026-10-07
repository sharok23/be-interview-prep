package com.project.api.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.api.contract.UserResponse;
import com.project.api.exception.UnauthorizedException;
import com.project.api.model.User;
import com.project.api.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserResponse profile(String username) {
        return UserResponse.from(require(username));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return users.findAll(Sort.by("id")).stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public User require(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists"));
    }
}
