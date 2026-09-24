package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.dto.LeaderboardEntryResponse;
import com.cybersixseven.platformapi.entity.UserAccount;
import com.cybersixseven.platformapi.repository.UserAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardService {

    public static final String SCORES_KEY = "leaderboard:scores";
    public static final String APPLIED_PREFIX = "leaderboard:applied:";

    private static final String APPLY_SCRIPT =
            """
            if redis.call('SET', KEYS[1], '1', 'NX') then
              redis.call('ZINCRBY', KEYS[2], ARGV[1], ARGV[2])
              return 1
            end
            return 0
            """;

    private final StringRedisTemplate redis;
    private final UserAccountRepository userAccountRepository;
    private final DefaultRedisScript<Long> applyScript;

    public LeaderboardService(StringRedisTemplate redis, UserAccountRepository userAccountRepository) {
        this.redis = redis;
        this.userAccountRepository = userAccountRepository;
        this.applyScript = new DefaultRedisScript<>();
        this.applyScript.setScriptText(APPLY_SCRIPT);
        this.applyScript.setResultType(Long.class);
    }

    public boolean apply(UUID submissionId, UUID studentId, int awardedPoints) {
        if (submissionId == null || studentId == null || awardedPoints < 0) {
            return false;
        }
        Long applied = redis.execute(
                applyScript,
                List.of(APPLIED_PREFIX + submissionId, SCORES_KEY),
                Integer.toString(awardedPoints),
                studentId.toString());
        return applied != null && applied == 1L;
    }

    public Page<LeaderboardEntryResponse> page(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long start = (long) safePage * safeSize;
        long end = start + safeSize - 1;
        Long total = redis.opsForZSet().zCard(SCORES_KEY);
        long totalCount = total == null ? 0L : total;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(SCORES_KEY, start, end);
        if (tuples == null || tuples.isEmpty()) {
            return new PageImpl<>(List.of(), PageRequest.of(safePage, safeSize), totalCount);
        }
        List<UUID> userIds = tuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(value -> value != null)
                .map(UUID::fromString)
                .toList();
        Map<UUID, String> nicknames = userAccountRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getNickname));
        List<LeaderboardEntryResponse> rows = new ArrayList<>();
        long rank = start + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() == null) {
                continue;
            }
            UUID userId = UUID.fromString(tuple.getValue());
            double score = tuple.getScore() == null ? 0d : tuple.getScore();
            rows.add(new LeaderboardEntryResponse(userId, nicknames.getOrDefault(userId, ""), score, rank));
            rank++;
        }
        return new PageImpl<>(rows, PageRequest.of(safePage, safeSize), totalCount);
    }
}
