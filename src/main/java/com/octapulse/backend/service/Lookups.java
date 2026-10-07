package com.octapulse.backend.service;

import com.octapulse.backend.domain.Fight;
import com.octapulse.backend.domain.Fighter;
import com.octapulse.backend.domain.User;
import com.octapulse.backend.dto.FightDto;
import com.octapulse.backend.dto.UserDto;
import com.octapulse.backend.repository.FighterRepository;
import com.octapulse.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Batch loads for building responses without N+1 queries. */
@Service
public class Lookups {

    private final FighterRepository fighterRepository;
    private final UserRepository userRepository;

    public Lookups(FighterRepository fighterRepository, UserRepository userRepository) {
        this.fighterRepository = fighterRepository;
        this.userRepository = userRepository;
    }

    public Map<UUID, Fighter> fighters(Collection<UUID> ids) {
        Set<UUID> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return fighterRepository.findAllById(wanted).stream()
                .collect(Collectors.toMap(Fighter::getId, Function.identity()));
    }

    public Map<UUID, Fighter> fightersFor(Collection<Fight> fights) {
        Set<UUID> ids = new HashSet<>();
        for (Fight f : fights) {
            ids.add(f.getRedFighterId());
            ids.add(f.getBlueFighterId());
        }
        return fighters(ids);
    }

    /** Fights need their event loaded (JOIN FETCH) since lock state reads the event start. */
    public List<FightDto> fightDtos(List<Fight> fights) {
        Map<UUID, Fighter> fighters = fightersFor(fights);
        return fights.stream().map(f -> FightDto.from(f, fighters)).toList();
    }

    public Map<UUID, UserDto.Summary> users(Collection<UUID> ids) {
        Set<UUID> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(wanted).stream()
                .collect(Collectors.toMap(User::getId, UserDto.Summary::from));
    }
}
