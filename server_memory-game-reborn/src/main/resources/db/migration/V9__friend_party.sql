-- FRIEND_PARTY is deliberately separate from duel records, balances and ratings.
create table pk_party_match (
    match_id varchar(64) primary key,
    settled_at bigint not null,
    status varchar(32) not null,
    round_count integer not null,
    participant_count integer not null,
    snapshot_json text not null
);
create table pk_party_participant (
    match_id varchar(64) not null references pk_party_match(match_id),
    player_id bigint not null,
    nickname varchar(255) not null,
    avatar_value varchar(255),
    final_rank integer not null,
    points2 integer not null,
    first_places integer not null,
    withdrawn boolean not null,
    primary key (match_id, player_id)
);
create index ix_party_participant_player on pk_party_participant(player_id, match_id);
create index ix_party_match_settled on pk_party_match(settled_at);
