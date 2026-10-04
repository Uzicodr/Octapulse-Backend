package com.octapulse.backend.web;

import com.octapulse.backend.dto.RankingDto;
import com.octapulse.backend.repository.RankingRepository;
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

    public RankingController(RankingRepository rankingRepository) {
        this.rankingRepository = rankingRepository;
    }

    @GetMapping
    public List<RankingDto> list(@RequestParam(required = false) String division) {
        var rows = (division == null || division.isBlank())
                ? rankingRepository.findAllByOrderByDivisionAscRankAsc()
                : rankingRepository.findByDivisionOrderByRankAsc(division);
        return rows.stream().map(RankingDto::from).collect(Collectors.toList());
    }
}
