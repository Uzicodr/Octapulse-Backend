package com.octapulse.backend.web;

import com.octapulse.backend.dto.PickDto.PickRequest;
import com.octapulse.backend.dto.PickDto.PickResponse;
import com.octapulse.backend.security.AuthInterceptor;
import com.octapulse.backend.service.PickService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/picks")
public class PickController {

    private final PickService pickService;

    public PickController(PickService pickService) {
        this.pickService = pickService;
    }

    @PostMapping
    public PickResponse createOrUpdate(@Valid @RequestBody PickRequest req, HttpServletRequest request) {
        UUID userId = currentUserId(request);
        var pick = pickService.createOrUpdatePick(userId, req.fightId(), req.pickedFighterId());
        return PickResponse.from(pick);
    }

    @GetMapping("/me")
    public List<PickResponse> mine(HttpServletRequest request) {
        UUID userId = currentUserId(request);
        return pickService.listForUser(userId).stream().map(PickResponse::from).collect(Collectors.toList());
    }

    private UUID currentUserId(HttpServletRequest request) {
        return (UUID) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
    }
}
