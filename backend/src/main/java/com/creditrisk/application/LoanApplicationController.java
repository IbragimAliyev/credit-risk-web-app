package com.creditrisk.application;

import com.creditrisk.application.LoanApplicationDtos.CreateRequest;
import com.creditrisk.application.LoanApplicationDtos.Response;
import com.creditrisk.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class LoanApplicationController {

    private final LoanApplicationService service;

    public LoanApplicationController(LoanApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody CreateRequest request) {
        return service.create(user.getId(), request);
    }

    @GetMapping
    public List<Response> list(@AuthenticationPrincipal UserPrincipal user) {
        return service.listForUser(user.getId());
    }

    @GetMapping("/{id}")
    public Response get(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        return service.getForUser(user.getId(), id);
    }
}
