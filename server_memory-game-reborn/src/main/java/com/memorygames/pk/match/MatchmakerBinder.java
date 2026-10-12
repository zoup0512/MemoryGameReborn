package com.memorygames.pk.match;

import org.springframework.stereotype.Component;

/** Breaks the Matchmaker <-> MatchManager constructor cycle. */
@Component
public class MatchmakerBinder {

    MatchmakerBinder(MatchManager manager, Matchmaker matchmaker) {
        manager.bindMatchmaker(matchmaker);
    }
}
