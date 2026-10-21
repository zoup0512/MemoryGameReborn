-- Room size has no fixed product limit; accumulated placement points use 64 bits.
alter table pk_party_participant alter column points2 type bigint;
