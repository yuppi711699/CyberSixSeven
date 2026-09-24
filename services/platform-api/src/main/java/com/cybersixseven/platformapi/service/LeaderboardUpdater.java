package com.cybersixseven.platformapi.service;

import com.cybersixseven.platformapi.event.SubmissionScoredEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LeaderboardUpdater {

    private final LeaderboardService leaderboardService;

    public LeaderboardUpdater(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionScored(SubmissionScoredEvent event) {
        leaderboardService.apply(event.submissionId(), event.studentId(), event.awardedPoints());
    }
}
