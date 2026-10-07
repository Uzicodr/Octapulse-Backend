package com.octapulse.backend.web;

import com.octapulse.backend.domain.Fighter;
import com.octapulse.backend.dto.NewsDto;
import com.octapulse.backend.dto.PagedResponse;
import com.octapulse.backend.repository.FighterRepository;
import com.octapulse.backend.service.NewsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/news")
public class NewsController {

    private final NewsService newsService;
    private final FighterRepository fighterRepository;

    public NewsController(NewsService newsService, FighterRepository fighterRepository) {
        this.newsService = newsService;
        this.fighterRepository = fighterRepository;
    }

    /** Latest headlines, newest first. fighter is a fighter slug; kind is one of {@link NewsService#KINDS}. */
    @GetMapping
    public PagedResponse<NewsDto> list(
            @RequestParam(required = false) String fighter,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        if (kind != null && !NewsService.KINDS.contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind must be one of " + NewsService.KINDS);
        }
        UUID fighterId = fighter == null ? null : fighterRepository.findBySlug(fighter)
                .map(Fighter::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fighter not found"));
        return newsService.list(kind, fighterId, Math.max(page, 1), CurrentUser.clampLimit(limit));
    }
}
