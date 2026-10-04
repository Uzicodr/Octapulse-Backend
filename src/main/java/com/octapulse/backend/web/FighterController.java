package com.octapulse.backend.web;

import com.octapulse.backend.domain.Fighter;
import com.octapulse.backend.dto.FighterDto;
import com.octapulse.backend.dto.PageDto;
import com.octapulse.backend.repository.FighterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/fighters")
public class FighterController {

    private final FighterRepository fighterRepository;

    public FighterController(FighterRepository fighterRepository) {
        this.fighterRepository = fighterRepository;
    }

    @GetMapping
    public FighterDto.ListResponse list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        PageRequest pageable = PageRequest.of(Math.max(page - 1, 0), limit);
        Page<Fighter> result = (q == null || q.isBlank())
                ? fighterRepository.findAllByOrderByNameAsc(pageable)
                : fighterRepository.findByNameContainingIgnoreCaseOrderByNameAsc(q, pageable);

        List<FighterDto> data = result.getContent().stream().map(FighterDto::from).collect(Collectors.toList());
        PageDto meta = new PageDto(page, limit, result.getTotalElements(), result.getTotalPages());
        return new FighterDto.ListResponse(data, meta);
    }

    @GetMapping("/{slug}")
    public FighterDto get(@PathVariable String slug) {
        Fighter fighter = fighterRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fighter not found"));
        return FighterDto.from(fighter);
    }
}
