package com.example.app.user.infrastructure.in.web;

import com.example.app.user.application.port.in.ManageUsersUseCase;
import com.example.app.user.application.port.in.NewUser;
import com.example.app.user.application.port.in.UserChanges;
import com.example.app.user.domain.UserId;
import com.example.app.shared.application.Page;
import com.example.app.shared.infrastructure.in.web.Principals;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final ManageUsersUseCase users;

    public UsersController(ManageUsersUseCase users) {
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USERS_READ')")
    public Page<UserResponse> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return users.list(page, size).map(UserResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USERS_READ')")
    public UserResponse get(@PathVariable long id) {
        return UserResponse.from(users.get(new UserId(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USERS_WRITE')")
    public ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest r) {
        UserResponse created = UserResponse.from(
                users.create(new NewUser(r.email(), r.fullName(), r.role(), r.password())));
        return ResponseEntity.created(
                ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USERS_WRITE')")
    public UserResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                               @RequestBody UpdateUserRequest r) {
        return UserResponse.from(users.update(new UserId(Principals.userId(jwt)), new UserId(id),
                new UserChanges(r.fullName(), r.role(), r.active())));
    }
}
