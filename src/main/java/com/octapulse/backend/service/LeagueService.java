package com.octapulse.backend.service;

import com.octapulse.backend.domain.League;
import com.octapulse.backend.domain.LeagueMember;
import com.octapulse.backend.dto.SocialDto.LeagueDetail;
import com.octapulse.backend.dto.SocialDto.LeagueResponse;
import com.octapulse.backend.repository.LeagueMemberRepository;
import com.octapulse.backend.repository.LeagueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LeagueService {

    public static final int MAX_MEMBERS = 200;
    public static final int MAX_LEAGUES_PER_USER = 20;

    // No 0/O or 1/I so codes survive being read aloud or typed from a screenshot.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final LeagueRepository leagueRepository;
    private final LeagueMemberRepository memberRepository;
    private final Lookups lookups;
    private final SecureRandom random = new SecureRandom();

    public LeagueService(LeagueRepository leagueRepository, LeagueMemberRepository memberRepository, Lookups lookups) {
        this.leagueRepository = leagueRepository;
        this.memberRepository = memberRepository;
        this.lookups = lookups;
    }

    @Transactional
    public LeagueResponse create(UUID userId, String name) {
        requireRoomForAnotherLeague(userId);
        League league = new League();
        league.setName(name.strip());
        league.setOwnerId(userId);
        league.setInviteCode(newCode());
        league.setCreatedAt(Instant.now());
        league = leagueRepository.save(league);
        addMember(league.getId(), userId);
        return LeagueResponse.from(league, 1);
    }

    @Transactional
    public LeagueResponse join(UUID userId, String inviteCode) {
        League league = leagueRepository.findByInviteCode(inviteCode.strip().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid invite code"));
        if (!memberRepository.existsByLeagueIdAndUserId(league.getId(), userId)) {
            requireRoomForAnotherLeague(userId);
            if (memberRepository.countByLeagueId(league.getId()) >= MAX_MEMBERS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "League is full");
            }
            addMember(league.getId(), userId);
        }
        return LeagueResponse.from(league, memberRepository.countByLeagueId(league.getId()));
    }

    public List<LeagueResponse> forUser(UUID userId) {
        return leagueRepository.findForMember(userId).stream()
                .map(l -> LeagueResponse.from(l, memberRepository.countByLeagueId(l.getId())))
                .toList();
    }

    public LeagueDetail detail(UUID userId, UUID leagueId) {
        League league = requireMember(userId, leagueId);
        List<LeagueMember> members = memberRepository.findByLeagueIdOrderByJoinedAtAsc(leagueId);
        var users = lookups.users(members.stream().map(LeagueMember::getUserId).toList());
        return new LeagueDetail(LeagueResponse.from(league, members.size()),
                members.stream().map(m -> users.get(m.getUserId())).toList());
    }

    /** When the owner leaves, the longest-standing member takes over; the last one out deletes it. */
    @Transactional
    public void leave(UUID userId, UUID leagueId) {
        League league = requireMember(userId, leagueId);
        memberRepository.deleteById(new LeagueMember.LeagueMemberId(leagueId, userId));
        memberRepository.flush();
        List<LeagueMember> remaining = memberRepository.findByLeagueIdOrderByJoinedAtAsc(leagueId);
        if (remaining.isEmpty()) {
            leagueRepository.delete(league);
        } else if (league.getOwnerId().equals(userId)) {
            league.setOwnerId(remaining.get(0).getUserId());
            leagueRepository.save(league);
        }
    }

    @Transactional
    public void delete(UUID userId, UUID leagueId) {
        League league = requireOwner(userId, leagueId);
        leagueRepository.delete(league);
    }

    @Transactional
    public LeagueResponse regenerateCode(UUID userId, UUID leagueId) {
        League league = requireOwner(userId, leagueId);
        league.setInviteCode(newCode());
        league = leagueRepository.save(league);
        return LeagueResponse.from(league, memberRepository.countByLeagueId(leagueId));
    }

    @Transactional
    public void removeMember(UUID userId, UUID leagueId, UUID memberId) {
        requireOwner(userId, leagueId);
        if (memberId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use leave to remove yourself");
        }
        memberRepository.deleteById(new LeagueMember.LeagueMemberId(leagueId, memberId));
    }

    private League requireMember(UUID userId, UUID leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found"));
        if (!memberRepository.existsByLeagueIdAndUserId(leagueId, userId)) {
            // Same answer as a missing league, so ids can't be probed.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found");
        }
        return league;
    }

    private League requireOwner(UUID userId, UUID leagueId) {
        League league = requireMember(userId, leagueId);
        if (!league.getOwnerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the league owner can do this");
        }
        return league;
    }

    private void requireRoomForAnotherLeague(UUID userId) {
        if (memberRepository.countByUserId(userId) >= MAX_LEAGUES_PER_USER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You can be in at most " + MAX_LEAGUES_PER_USER + " leagues");
        }
    }

    private void addMember(UUID leagueId, UUID userId) {
        LeagueMember member = new LeagueMember();
        member.setLeagueId(leagueId);
        member.setUserId(userId);
        member.setJoinedAt(Instant.now());
        memberRepository.save(member);
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            if (!leagueRepository.existsByInviteCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("could not generate a unique invite code");
    }
}
