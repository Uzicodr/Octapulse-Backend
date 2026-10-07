package com.octapulse.backend.web;

import com.octapulse.backend.service.NotificationJobs;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/** Machine-to-machine endpoints for the agent, guarded by the INTERNAL_TOKEN shared secret. */
@Hidden
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final NotificationJobs jobs;
    private final String token;

    public InternalController(NotificationJobs jobs, @Value("${internal.token:}") String token) {
        this.jobs = jobs;
        this.token = token;
    }

    /** Run the notification jobs now. The agent calls this after saving live results or news. */
    @PostMapping("/notify")
    public Map<String, Integer> notifyNow(@RequestHeader(value = "X-Internal-Token", required = false) String given) {
        if (token.isBlank() || given == null
                || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Map<String, Integer> sent = new LinkedHashMap<>(jobs.settleAndNotify());
        sent.putAll(jobs.remind());
        return sent;
    }
}
