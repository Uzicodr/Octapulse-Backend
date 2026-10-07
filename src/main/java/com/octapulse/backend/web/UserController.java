package com.octapulse.backend.web;

import com.octapulse.backend.dto.PagedResponse;
import com.octapulse.backend.dto.PickDto.PickResponse;
import com.octapulse.backend.dto.UserDto;
import com.octapulse.backend.repository.UserRepository;
import com.octapulse.backend.service.PickService;
import com.octapulse.backend.service.SocialService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Public profiles. Reads work anonymously; following needs a signed-in user. */
@RestController
@RequestMapping("/users")
public class UserController {

    private final SocialService socialService;
    private final PickService pickService;
    private final UserRepository userRepository;

    public UserController(SocialService socialService, PickService pickService, UserRepository userRepository) {
        this.socialService = socialService;
        this.pickService = pickService;
        this.userRepository = userRepository;
    }

    @GetMapping("/{userId}")
    public UserDto.Profile get(@PathVariable UUID userId, HttpServletRequest request) {
        return socialService.profile(userId, CurrentUser.optional(request));
    }

    @GetMapping("/by-username/{username}")
    public UserDto.Profile byUsername(@PathVariable String username, HttpServletRequest request) {
        UUID userId = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();
        return socialService.profile(userId, CurrentUser.optional(request));
    }

    /** Only picks on locked fights, so open picks can't be copied. */
    @GetMapping("/{userId}/picks")
    public List<PickResponse> picks(@PathVariable UUID userId) {
        socialService.findUser(userId);
        return pickService.listVisibleForUser(userId).stream().map(PickResponse::from).toList();
    }

    @GetMapping("/{userId}/followers")
    public PagedResponse<UserDto.Summary> followers(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return socialService.followers(userId, page, CurrentUser.clampLimit(limit));
    }

    @GetMapping("/{userId}/following")
    public PagedResponse<UserDto.Summary> following(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return socialService.following(userId, page, CurrentUser.clampLimit(limit));
    }

    @PostMapping("/{userId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@PathVariable UUID userId, HttpServletRequest request) {
        socialService.follow(CurrentUser.require(request), userId);
    }

    @DeleteMapping("/{userId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@PathVariable UUID userId, HttpServletRequest request) {
        socialService.unfollow(CurrentUser.require(request), userId);
    }
}
