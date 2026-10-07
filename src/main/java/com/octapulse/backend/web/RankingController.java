package com.octapulse.backend.web;

import com.octapulse.backend.domain.Ranking;
import com.octapulse.backend.dto.RankingDto;
import com.octapulse.backend.repository.RankingRepository;
import com.octapulse.backend.service.Lookups;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rankings")
public class RankingController {

    private final RankingRepository rankingRepository;
    private final Lookups lookups;

    public RankingController(RankingRepository rankingRepository, Lookups lookups) {
        this.rankingRepository = rankingRepository;
        this.lookups = lookups;
    }

    @GetMapping
    public List<RankingDto> list(@RequestParam(required = false) String division) {
        var rows = (division == null || division.isBlank())
                ? rankingRepository.findAllByOrderByDivisionAscRankAsc()
                : rankingRepository.findByDivisionOrderByRankAsc(division);
        var fighters = lookups.fighters(rows.stream().map(Ranking::getFighterId).toList());
        return rows.stream().map(r -> RankingDto.from(r, fighters.get(r.getFighterId()))).collect(Collectors.toList());
    }
}
