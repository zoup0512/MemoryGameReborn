package com.memorygames.pk.party;

import com.memorygames.pk.protocol.ProtocolCodec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Independent append-only history: intentionally no User/Settlement/ELO/coin writes. */
@Service
public class PartyHistory {
    private final JdbcTemplate jdbc;
    public PartyHistory(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void save(PartyProtocol.State state) {
        Integer exists = jdbc.queryForObject("select count(*) from pk_party_match where match_id = ?", Integer.class, state.matchId);
        if (exists != null && exists > 0) return;
        jdbc.update("insert into pk_party_match(match_id, settled_at, status, round_count, participant_count, snapshot_json) values (?,?,?,?,?,?)",
                state.matchId, state.serverTime, state.status, state.roundCount, state.participants.size(),
                ProtocolCodec.encode("party_state", state));
        for (PartyProtocol.Participant p : state.participants) {
            jdbc.update("insert into pk_party_participant(match_id, player_id, nickname, avatar_value, final_rank, points2, first_places, withdrawn) values (?,?,?,?,?,?,?,?)",
                    state.matchId, p.playerId, p.nickname, p.avatarValue, p.rank, p.points2, p.firstPlaces, p.withdrawn);
        }
    }

    @Transactional(readOnly = true)
    public List<PartyProtocol.HistoryEntry> recent(long userId) {
        return jdbc.query("select m.match_id,m.settled_at,m.status,m.round_count,m.participant_count,p.final_rank,p.points2,p.first_places "
                        + "from pk_party_match m join pk_party_participant p on p.match_id=m.match_id where p.player_id=? "
                        + "order by m.settled_at desc, m.match_id desc limit 20",
                (rs, n) -> new PartyProtocol.HistoryEntry(rs.getString(1), rs.getLong(2), rs.getString(3),
                        rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8)), userId);
    }
}
