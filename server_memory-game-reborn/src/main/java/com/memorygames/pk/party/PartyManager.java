package com.memorygames.pk.party;

import com.memorygames.pk.admin.AdminState;
import com.memorygames.pk.game.GameCatalog;
import com.memorygames.pk.match.PlayerEndpoint;
import com.memorygames.pk.match.PlayerLifecycle;
import com.memorygames.pk.protocol.ProtocolCodec;
import com.memorygames.pk.user.UserAccount;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Persistent lobby + authoritative party state machine. One monitor serializes admission,
 * membership, clocks and snapshots; no DB or socket IO runs under it (endpoints enqueue).
 * A bounded shared clock scan replaces per-round/member delayed callbacks, so there is
 * no timer closure that can act on a successor match, reconnect or lifecycle claim.
 */
@Component
public class PartyManager {
    private static final Logger log = LoggerFactory.getLogger(PartyManager.class);
    private static final long SCORE_BROADCAST_MS = 250;
    public record Timing(long loadingMs, long countdownMs, long intermissionMs,
                         long disconnectMs, long hostTransferMs, long idleMs, long graceMs) { }
    private static class Member {
        final long id;
        final String nickname, avatar;
        PlayerEndpoint endpoint;
        boolean ready, connected = true;
        long disconnectedAt;
        Member(UserAccount u, PlayerEndpoint e) { id=u.id; nickname=u.nickname; avatar=u.avatarValue; endpoint=e; }
    }
    private static class Seat {
        final Member member;
        boolean withdrawn, loaded, eligible, done;
        int score, firstPlaces, rank, lastLevel;
        long points2;
        long lastLevelAt, nextAllowedAt;
        Seat(Member m) { member=m; }
    }
    private static class Lobby {
        final String id = UUID.randomUUID().toString();
        final String code;
        final LinkedHashMap<Long,Member> members = new LinkedHashMap<>();
        final LinkedHashMap<Long,Seat> seats = new LinkedHashMap<>();
        final List<PartyProtocol.RoundResult> results = new ArrayList<>();
        List<GameCatalog.ProtocolRound> rounds = List.of();
        long hostId, version, lastActivity, startAt, deadlineAt, phaseDeadlineAt;
        int roundCount, roundIndex = -1;
        boolean scoreDirty;
        long scoreBroadcastAt;
        String phase = "LOBBY", matchId, status, reason;
        Lobby(String code, int rounds, long now) { this.code=code; roundCount=rounds; lastActivity=now; }
    }
    private record Window(long start, int count) { }
    private final LinkedHashMap<String,Lobby> lobbies = new LinkedHashMap<>();
    private final Map<String,Lobby> codes = new HashMap<>();
    private final Map<Long,Lobby> byUser = new HashMap<>();
    private final Map<String,Window> joins = new HashMap<>();
    private final LinkedHashMap<String,PartyProtocol.State> pending = new LinkedHashMap<>();
    private final PlayerLifecycle lifecycle;
    private final GameCatalog catalog;
    private final PartyHistory history;
    private final AdminState admin;
    private final Clock clock;
    private final Timing timing;
    private final SecureRandom random = new SecureRandom();
    private final ScheduledThreadPoolExecutor scheduler;
    private final ScheduledThreadPoolExecutor writer;
    private boolean stopping;

    @Autowired
    public PartyManager(PlayerLifecycle lifecycle, GameCatalog catalog, PartyHistory history,
                        AdminState admin, Environment env) {
        this(lifecycle,catalog,history,admin,Clock.systemUTC(),new Timing(
                value(env,"loading-ms",15000), value(env,"countdown-ms",3000),
                value(env,"intermission-ms",6000), value(env,"disconnect-ms",90000),
                value(env,"host-transfer-ms",30000), value(env,"idle-ms",1800000),
                value(env,"score-grace-ms",300)), true);
    }
    private static long value(Environment e, String name, long fallback) {
        return Math.max(1, e.getProperty("pk.party."+name, Long.class, fallback));
    }
    PartyManager(PlayerLifecycle lifecycle, GameCatalog catalog, PartyHistory history,
                 AdminState admin, Clock clock, Timing timing, boolean schedule) {
        this.lifecycle=lifecycle; this.catalog=catalog; this.history=history;
        this.admin=admin; this.clock=clock; this.timing=timing;
        scheduler=pool("pk-party-clock"); writer=pool("pk-party-history");
        if (schedule) {
            scheduler.scheduleWithFixedDelay(() -> {
                try { tick(); } catch (RuntimeException e) { log.error("party clock failed",e); }
            },100,100,TimeUnit.MILLISECONDS);
            writer.scheduleWithFixedDelay(this::flushHistory,100,500,TimeUnit.MILLISECONDS);
        }
    }
    private static ScheduledThreadPoolExecutor pool(String name) {
        ScheduledThreadPoolExecutor p=new ScheduledThreadPoolExecutor(1,r->{ Thread t=new Thread(r,name); t.setDaemon(true); return t; });
        p.setRemoveOnCancelPolicy(true); return p;
    }

    /** Handles every party command on the existing authenticated connection. */
    public synchronized void command(UserAccount user, PlayerEndpoint endpoint, String ip,
                                     String type, Object raw, String rid) {
        try {
            if (stopping) throw fail(503,"server shutting down");
            require(raw instanceof Map<?,?>,400,"object payload required");
            Map<?,?> fields=(Map<?,?>)raw;
            for(String name:List.of("roundCount","roundIndex","level","playerId")) {
                Object value=fields.get(name);
                require(value==null || value instanceof Integer || value instanceof Long,400,"integer field required: "+name);
            }
            for(String name:List.of("ready","success")) {
                Object value=fields.get(name); require(value==null || value instanceof Boolean,400,"boolean field required: "+name);
            }
            for(String name:List.of("lobbyId","matchId","code")) {
                Object value=fields.get(name); require(value==null || value instanceof String && ((String)value).length()<=64,400,"invalid identity field");
            }
            PartyProtocol.Command c;
            try { c=ProtocolCodec.convert(raw,PartyProtocol.Command.class); }
            catch (IllegalArgumentException e) { throw fail(400,"bad party command"); }
            if (c==null) throw fail(400,"bad party command");
            if (type.equals("party_history")) {
                // Gateway calls history separately, keeping database latency out of this monitor.
                throw fail(400,"use history gateway");
            }
            if (type.equals("party_create") || type.equals("party_join") || type.equals("party_start")) {
                if (admin.isMaintenance()) throw fail(503,"server in maintenance");
                if (pending.size()>=1000) throw fail(503,"party history backlog");
            }
            if (type.equals("party_sync")) {
                Lobby l=byUser.get(user.id);
                if (l==null) left(endpoint,null,"not_in_lobby",rid);
                else send(endpoint,"party_state",snapshot(l),rid);
                return;
            }
            if (type.equals("party_leave")) {
                require(c.lobbyId!=null,400,"lobbyId required");
                Lobby l=byUser.get(user.id);
                if (l==null || !l.id.equals(c.lobbyId)) { left(endpoint,c.lobbyId,"not_in_lobby",rid); return; }
                if (c.matchId!=null && !c.matchId.equals(l.matchId)) throw fail(409,"stale match");
                remove(l,user.id,"left",rid);
                return;
            }
            if (type.equals("party_create")) {
                require(!byUser.containsKey(user.id),409,"already in lobby");
                int rounds=c.roundCount==null?5:c.roundCount;
                require(rounds==3 || rounds==5,400,"roundCount must be 3 or 5");
                require(lobbies.size()<10000,503,"party capacity reached");
                Lobby l=new Lobby(newCode(),rounds,clock.millis());
                require(lifecycle.claimOwned(user.id,PlayerLifecycle.PARTY_LOBBY,l.id),409,"player busy");
                Member m=new Member(user,endpoint); l.members.put(user.id,m); l.hostId=user.id;
                lobbies.put(l.id,l); codes.put(l.code,l); byUser.put(user.id,l);
                changed(l); send(endpoint,"party_state",snapshot(l),rid); return;
            }
            if (type.equals("party_join")) {
                boolean userOk=acquire("u:"+user.id,10), ipOk=acquire("i:"+ip,40);
                require(userOk && ipOk,429,"too many join attempts");
                require(c.code!=null && c.code.matches("[0-9]{6}"),400,"six digit code required");
                Lobby l=codes.get(c.code);
                require(l!=null,404,"lobby not found");
                if (byUser.get(user.id)==l) { send(endpoint,"party_state",snapshot(l),rid); return; }
                require(l.phase.equals("LOBBY"),409,"match in progress");
                require(!byUser.containsKey(user.id) && lifecycle.claimOwned(user.id,PlayerLifecycle.PARTY_LOBBY,l.id),409,"player busy");
                l.members.put(user.id,new Member(user,endpoint)); byUser.put(user.id,l); clearReady(l);
                touch(l); changed(l); send(endpoint,"party_state",snapshot(l),rid); return;
            }
            Lobby l=byUser.get(user.id);
            require(l!=null && l.id.equals(c.lobbyId),409,"stale lobby");
            advancePhase(l,clock.millis());
            Member m=l.members.get(user.id);
            switch (type) {
                case "party_config" -> {
                    host(l,user.id); lobby(l); require(c.roundCount!=null && (c.roundCount==3 || c.roundCount==5),400,"bad roundCount");
                    if (l.roundCount!=c.roundCount) { l.roundCount=c.roundCount; clearReady(l); changed(l); }
                }
                case "party_ready" -> {
                    lobby(l); require(c.ready!=null,400,"ready required");
                    if (m.ready!=c.ready) { m.ready=c.ready; changed(l); }
                }
                case "party_transfer" -> {
                    host(l,user.id); lobby(l);
                    Member target=c.playerId==null?null:l.members.get(c.playerId);
                    require(target!=null && target.connected && target.id!=user.id,400,"invalid host target");
                    l.hostId=target.id; clearReady(l); changed(l);
                }
                case "party_kick" -> {
                    host(l,user.id); lobby(l);
                    require(c.playerId!=null && c.playerId.longValue()!=user.id.longValue() && l.members.containsKey(c.playerId),400,"invalid kick target");
                    remove(l,c.playerId,"kicked",null);
                }
                case "party_start" -> {
                    host(l,user.id);
                    // A lost start receipt is safe to retry, never begins a second match.
                    if (!l.phase.equals("LOBBY")) { send(endpoint,"party_state",snapshot(l),rid); return; }
                    require(l.members.size()>=2,409,"need at least 2 players");
                    require(l.members.values().stream().allMatch(x->x.connected && (x.id==l.hostId || x.ready)),409,"players not ready");
                    l.matchId=UUID.randomUUID().toString(); l.status="IN_PROGRESS"; l.reason=null;
                    l.seats.clear(); l.results.clear(); l.rounds=catalog.planRounds(random.nextLong(),l.roundCount);
                    for (Member member:l.members.values()) l.seats.put(member.id,new Seat(member));
                    clearReady(l); beginLoading(l,0);
                }
                case "party_round_ready" -> {
                    Seat s=round(l,c,user.id);
                    if (l.phase.equals("LOADING")) {
                        if (!s.loaded) { s.loaded=true; changed(l); }
                        if (active(l).stream().allMatch(x->x.loaded)) beginCountdown(l);
                    } else require(s.loaded && (l.phase.equals("COUNTDOWN") || l.phase.equals("PLAYING")),409,"loading closed");
                }
                case "party_level" -> {
                    level(l,c,user.id);
                    touch(l);
                    Seat seat=l.seats.get(user.id);
                    send(endpoint,"party_ack",new PartyProtocol.Ack(l.id,l.matchId,l.roundIndex,
                            user.id,l.version,seat.score),rid);
                    return;
                }
                case "party_complete" -> {
                    Seat s=round(l,c,user.id);
                    require(l.phase.equals("PLAYING") && clock.millis()>=l.startAt && clock.millis()<=l.deadlineAt+timing.graceMs,409,"round not playing");
                    require(s.eligible,409,"not loaded");
                    if (!s.done) { s.done=true; changed(l); }
                    maybeEndRound(l);
                }
                case "party_return" -> {
                    require(c.matchId!=null && c.matchId.equals(l.matchId),409,"stale match");
                    require(l.phase.equals("FINISHED") || l.phase.equals("LOBBY"),409,"match not finished");
                    if (l.phase.equals("FINISHED")) {
                        l.phase="LOBBY"; l.roundIndex=-1; l.startAt=0; l.deadlineAt=0; l.phaseDeadlineAt=0;
                        clearReady(l); changed(l);
                    }
                }
                default -> throw fail(405,"unknown party command");
            }
            touch(l); send(endpoint,"party_state",snapshot(l),rid);
        } catch (PartyFailure e) {
            send(endpoint,"error",error(e.code,e.getMessage()),rid);
        }
    }

    private void level(Lobby l,PartyProtocol.Command c,long id) {
        Seat s=round(l,c,id); long now=clock.millis();
        require(l.phase.equals("PLAYING") && now>=l.startAt && now<=l.deadlineAt+timing.graceMs,409,"round not playing");
        require(s.eligible && !s.done,409,"seat not playing");
        require(c.level!=null && c.success!=null,400,"level and success required");
        GameCatalog.GameSpec spec=catalog.byId(l.rounds.get(l.roundIndex).gameId);
        long floor=Math.max(1,catalog.minLevelIntervalOf(spec));
        // Grace is transport slack, not gameplay time. Clamp both progression
        // and cadence to the gameplay deadline; a late burst cannot buy extra levels.
        long effectiveNow=Math.min(now,l.deadlineAt);
        long elapsed=effectiveNow-l.startAt;
        require(c.level>s.lastLevel && c.level<=spec.maxLevel() && c.level<=elapsed/floor+3,
                409,"invalid level progression");
        require(effectiveNow>=s.nextAllowedAt && (s.lastLevel==0 || effectiveNow-s.lastLevelAt>=floor),409,"level too fast");
        // Compute before mutating cadence/progression. Neither per-level multiplication
        // nor cumulative int overflow may partially consume an otherwise valid report.
        long score=(long)s.score+(c.success ? (spec.flatScore ? spec.baseScore : spec.baseScore+2L*c.level) : 0);
        require(score>=0 && score<=Integer.MAX_VALUE,409,"score overflow");
        s.lastLevel=c.level; s.lastLevelAt=effectiveNow;
        s.nextAllowedAt=effectiveNow+(c.success?0:spec.failureDelayMs());
        s.score=(int)score;
        l.version++;
        if(!l.scoreDirty) { l.scoreDirty=true; l.scoreBroadcastAt=now+SCORE_BROADCAST_MS; }
    }
    private Seat round(Lobby l,PartyProtocol.Command c,long id) {
        require(c.matchId!=null && c.matchId.equals(l.matchId) && c.roundIndex!=null && c.roundIndex==l.roundIndex,409,"stale round");
        Seat s=l.seats.get(id); require(s!=null && !s.withdrawn,409,"not a participant"); return s;
    }
    private void beginLoading(Lobby l,int roundIndex) {
        if (active(l).size()<2) { finish(l,"INTERRUPTED","not_enough_players"); return; }
        l.roundIndex=roundIndex; l.phase="LOADING"; l.startAt=0; l.deadlineAt=0;
        l.phaseDeadlineAt=clock.millis()+timing.loadingMs;
        for (Seat s:l.seats.values()) { s.loaded=false; s.eligible=false; s.done=false; s.score=0; s.lastLevel=0; s.lastLevelAt=0; s.nextAllowedAt=0; }
        changed(l);
    }
    private void beginCountdown(Lobby l) {
        long loaded=active(l).stream().filter(s->s.loaded).count();
        if (loaded<2) { finish(l,"INTERRUPTED","loading_failed"); return; }
        for (Seat s:l.seats.values()) s.eligible=s.loaded && !s.withdrawn;
        l.phase="COUNTDOWN"; l.startAt=clock.millis()+timing.countdownMs;
        l.deadlineAt=l.startAt+l.rounds.get(l.roundIndex).durationMs;
        l.phaseDeadlineAt=l.startAt; changed(l);
    }
    private void maybeEndRound(Lobby l) {
        if (l.phase.equals("PLAYING") && l.seats.values().stream().filter(s->s.eligible && !s.withdrawn).allMatch(s->s.done)) endRound(l);
    }
    private void endRound(Lobby l) {
        PartyProtocol.RoundResult result=new PartyProtocol.RoundResult();
        result.roundIndex=l.roundIndex; result.gameId=l.rounds.get(l.roundIndex).gameId;
        List<Seat> sorted=l.seats.values().stream().filter(s->s.eligible && !s.withdrawn)
                .sorted(Comparator.comparingInt((Seat s)->s.score).reversed()).toList();
        Map<Long,PartyProtocol.Entry> entries=new HashMap<>();
        for (int i=0;i<sorted.size();) {
            int end=i+1; while(end<sorted.size() && sorted.get(end).score==sorted.get(i).score) end++;
            int rank=i+1;
            long points=sorted.get(i).score>0 ? 2L*(l.seats.size()-rank)-(end-i-1L) : 0;
            for(int j=i;j<end;j++) {
                Seat s=sorted.get(j); s.points2+=points;
                if (rank==1 && s.score>0) s.firstPlaces++;
                PartyProtocol.Entry e=new PartyProtocol.Entry(); e.playerId=s.member.id; e.score=s.score; e.rank=rank; e.points2=points; e.eligible=true; entries.put(e.playerId,e);
            }
            i=end;
        }
        for(Seat s:l.seats.values()) {
            PartyProtocol.Entry e=entries.get(s.member.id);
            if(e==null) { e=new PartyProtocol.Entry(); e.playerId=s.member.id; e.score=0; }
            result.entries.add(e);
        }
        l.results.add(result); updateRanks(l);
        if(l.roundIndex==l.roundCount-1) finish(l,"COMPLETED",null);
        else { l.phase="ROUND_RESULT"; l.phaseDeadlineAt=clock.millis()+timing.intermissionMs; changed(l); }
    }
    private void updateRanks(Lobby l) {
        List<Seat> sorted=l.seats.values().stream().sorted(Comparator.comparing((Seat s)->s.withdrawn)
                .thenComparing(Comparator.comparingLong((Seat s)->s.points2).reversed())
                .thenComparing(Comparator.comparingInt((Seat s)->s.firstPlaces).reversed())).toList();
        Seat previous=null; int rank=0;
        for(int i=0;i<sorted.size();i++) {
            Seat s=sorted.get(i);
            if(previous==null || previous.withdrawn!=s.withdrawn || previous.points2!=s.points2 || previous.firstPlaces!=s.firstPlaces) rank=i+1;
            s.rank=rank; previous=s;
        }
    }
    private void finish(Lobby l,String status,String reason) {
        if(l.phase.equals("FINISHED")) return;
        l.phase="FINISHED"; l.status=status; l.reason=reason; l.phaseDeadlineAt=0; touch(l);
        if(status.equals("COMPLETED")) updateRanks(l);
        else for(Seat s:l.seats.values()) { s.rank=0; if(l.results.size()<=l.roundIndex) s.score=0; }
        changed(l);
        pending.put(l.matchId,snapshot(l));
    }
    private List<Seat> active(Lobby l) { return l.seats.values().stream().filter(s->!s.withdrawn).toList(); }
    private boolean inMatch(Lobby l) { return !l.phase.equals("LOBBY") && !l.phase.equals("FINISHED"); }
    private void clearReady(Lobby l) { for(Member m:l.members.values()) m.ready=false; }
    private void touch(Lobby l) { l.lastActivity=clock.millis(); }
    private void lobby(Lobby l) { require(l.phase.equals("LOBBY"),409,"not in lobby phase"); }
    private void host(Lobby l,long id) { require(l.hostId==id,403,"host only"); }

    private void remove(Lobby l,long id,String reason,String rid) {
        Member m=l.members.remove(id); if(m==null) return;
        byUser.remove(id,l); lifecycle.releaseOwned(id,PlayerLifecycle.PARTY_LOBBY,l.id);
        left(m.endpoint,l.id,reason,rid);
        Seat s=l.seats.get(id);
        if(s!=null && inMatch(l)) {
            s.withdrawn=true; s.eligible=false; s.done=true;
            if(l.results.size()<=l.roundIndex) s.score=0;
            updateRanks(l);
        }
        if(l.hostId==id) transfer(l);
        if(l.phase.equals("LOBBY")) clearReady(l);
        touch(l);
        if(inMatch(l)) {
            if(active(l).size()<2) finish(l,"INTERRUPTED","not_enough_players");
            else if(l.phase.equals("LOADING") && active(l).stream().allMatch(x->x.loaded)) beginCountdown(l);
            else maybeEndRound(l);
        }
        if(l.members.isEmpty()) { lobbies.remove(l.id,l); codes.remove(l.code,l); }
        else changed(l);
    }
    private void transfer(Lobby l) {
        long next=l.members.values().stream().filter(m->m.connected).mapToLong(m->m.id).findFirst()
                .orElseGet(()->l.members.keySet().stream().mapToLong(Long::longValue).findFirst().orElse(0));
        if(next!=l.hostId) { l.hostId=next; if(l.phase.equals("LOBBY")) clearReady(l); }
    }
    public synchronized void reconnect(long id,PlayerEndpoint endpoint) {
        Lobby l=byUser.get(id); if(l==null) return;
        Member m=l.members.get(id); m.endpoint=endpoint; m.connected=true; m.disconnectedAt=0;
        changed(l);
    }
    public synchronized void disconnect(long id) {
        Lobby l=byUser.get(id); if(l==null) return;
        Member m=l.members.get(id); m.connected=false; m.ready=false; m.disconnectedAt=clock.millis(); changed(l);
    }
    public synchronized void tick() {
        if(stopping) return;
        long now=clock.millis();
        for(Lobby l:new ArrayList<>(lobbies.values())) {
            for(Member m:new ArrayList<>(l.members.values())) {
                if(!m.connected && now-m.disconnectedAt>=timing.disconnectMs) remove(l,m.id,"disconnect_timeout",null);
            }
            if(!lobbies.containsKey(l.id)) continue;
            Member host=l.members.get(l.hostId);
            if(l.phase.equals("LOBBY") && host!=null && !host.connected && now-host.disconnectedAt>=timing.hostTransferMs
                    && l.members.values().stream().anyMatch(m->m.connected)) { transfer(l); changed(l); }
            if(!inMatch(l) && now-l.lastActivity>=timing.idleMs) {
                for(long id:new ArrayList<>(l.members.keySet())) remove(l,id,"lobby_expired",null);
                continue;
            }
            advancePhase(l,now);
            if(l.scoreDirty && now>=l.scoreBroadcastAt) broadcast(l);
        }
        joins.entrySet().removeIf(e->now-e.getValue().start>=60000);
    }
    /** Commands must honor elapsed cutoffs even between the 100ms clock scans. */
    private void advancePhase(Lobby l,long now) {
        if(l.phase.equals("LOADING") && now>=l.phaseDeadlineAt) beginCountdown(l);
        if(l.phase.equals("COUNTDOWN") && now>=l.startAt) {
            l.phase="PLAYING"; l.phaseDeadlineAt=l.deadlineAt; changed(l);
        }
        if(l.phase.equals("PLAYING") && now>=l.deadlineAt+timing.graceMs) endRound(l);
        if(l.phase.equals("ROUND_RESULT") && now>=l.phaseDeadlineAt) beginLoading(l,l.roundIndex+1);
    }
    private boolean acquire(String key,int max) {
        long now=clock.millis(); Window w=joins.get(key);
        if(w==null || now-w.start>=60000) w=new Window(now,0);
        if(joins.size()>50000) return false;
        w=new Window(w.start,w.count+1); joins.put(key,w); return w.count<=max;
    }
    private String newCode() {
        for(int i=0;i<100;i++) { String code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000)); if(!codes.containsKey(code)) return code; }
        throw fail(503,"code capacity reached");
    }
    private void changed(Lobby l) {
        l.version++;
        broadcast(l);
    }
    private void broadcast(Lobby l) {
        l.scoreDirty=false;
        PartyProtocol.State state=snapshot(l);
        String frame=ProtocolCodec.encode("party_state",state);
        for(Member m:l.members.values()) if(m.connected) m.endpoint.send(frame);
    }
    private PartyProtocol.State snapshot(Lobby l) {
        PartyProtocol.State s=new PartyProtocol.State();
        s.lobbyId=l.id; s.code=l.code; s.hostId=l.hostId; s.roundCount=l.roundCount; s.stateVersion=l.version;
        s.serverTime=clock.millis(); s.phase=l.phase; s.matchId=l.matchId; s.roundIndex=l.roundIndex;
        s.startAt=l.startAt; s.deadlineAt=l.deadlineAt; s.phaseDeadlineAt=l.phaseDeadlineAt; s.status=l.status; s.reason=l.reason;
        for (GameCatalog.ProtocolRound round:l.rounds) {
            ProtocolCodec.RoundDesc d=new ProtocolCodec.RoundDesc(); d.gameId=round.gameId; d.durationMs=round.durationMs; d.seed=round.seed; s.rounds.add(d);
        }
        s.results=new ArrayList<>(l.results);
        for(Member m:l.members.values()) {
            PartyProtocol.Member v=new PartyProtocol.Member(); v.playerId=m.id; v.nickname=m.nickname; v.avatarValue=m.avatar; v.ready=m.ready; v.connected=m.connected; s.members.add(v);
        }
        for(Seat p:l.seats.values()) {
            PartyProtocol.Participant v=new PartyProtocol.Participant(); v.playerId=p.member.id; v.nickname=p.member.nickname; v.avatarValue=p.member.avatar;
            v.connected=!p.withdrawn && l.members.containsKey(v.playerId) && p.member.connected;
            v.withdrawn=p.withdrawn; v.roundEligible=p.eligible; v.score=p.score; v.points2=p.points2; v.firstPlaces=p.firstPlaces; v.rank=p.rank; s.participants.add(v);
        }
        return s;
    }
    public synchronized PartyProtocol.State stateFor(long id) { Lobby l=byUser.get(id); return l==null?null:snapshot(l); }
    public void history(long id,PlayerEndpoint endpoint,String rid) {
        // Capture pending BEFORE the database read. A writer completing between the
        // two reads cannot make the just-finished result temporarily disappear.
        List<PartyProtocol.HistoryEntry> memory=new ArrayList<>();
        synchronized(this) {
            for(PartyProtocol.State state:pending.values()) for(PartyProtocol.Participant p:state.participants) if(p.playerId==id) {
                memory.add(new PartyProtocol.HistoryEntry(state.matchId,state.serverTime,state.status,state.roundCount,
                        state.participants.size(),p.rank,p.points2,p.firstPlaces));
            }
        }
        try {
            Map<String,PartyProtocol.HistoryEntry> merged=new HashMap<>();
            for(PartyProtocol.HistoryEntry entry:history.recent(id)) merged.put(entry.matchId(),entry);
            for(PartyProtocol.HistoryEntry entry:memory) merged.put(entry.matchId(),entry);
            List<PartyProtocol.HistoryEntry> recent=merged.values().stream()
                    .sorted(Comparator.comparingLong(PartyProtocol.HistoryEntry::ts).reversed()
                            .thenComparing(PartyProtocol.HistoryEntry::matchId,Comparator.reverseOrder())).limit(20).toList();
            send(endpoint,"party_history",new PartyProtocol.History(recent),rid);
        } catch(RuntimeException e) {
            log.warn("party history read failed",e);
            send(endpoint,"error",error(503,"history unavailable"),rid);
        }
    }
    void flushHistory() {
        List<PartyProtocol.State> work;
        synchronized(this) { work=new ArrayList<>(pending.values()); }
        for(PartyProtocol.State state:work) {
            try { history.save(state); synchronized(this) { pending.remove(state.matchId,state); } }
            catch(RuntimeException e) { log.warn("party history persistence will retry match={}",state.matchId,e); }
        }
    }
    @PreDestroy
    public void shutdown() {
        synchronized(this) {
            if(stopping) return; stopping=true;
            for(Lobby l:new ArrayList<>(lobbies.values())) for(Member m:l.members.values()) {
                left(m.endpoint,l.id,"server_shutdown",null);
                lifecycle.releaseOwned(m.id,PlayerLifecycle.PARTY_LOBBY,l.id);
            }
            lobbies.clear(); codes.clear(); byUser.clear();
        }
        scheduler.shutdownNow(); writer.shutdown();
        try { writer.awaitTermination(5,TimeUnit.SECONDS); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        flushHistory();
        // In-flight memory-only matches are intentionally not written as formal history.
    }
    private static void send(PlayerEndpoint e,String type,Object data,String rid) { e.send(ProtocolCodec.encode(type,data,rid)); }
    private static void left(PlayerEndpoint e,String lobby,String reason,String rid) { send(e,"party_left",new PartyProtocol.Left(lobby,reason),rid); }
    private static ProtocolCodec.ErrorMsg error(int code,String message) { ProtocolCodec.ErrorMsg e=new ProtocolCodec.ErrorMsg(); e.code=code; e.message=message; return e; }
    private static class PartyFailure extends RuntimeException { final int code; PartyFailure(int code,String message) { super(message); this.code=code; } }
    private static PartyFailure fail(int code,String message) { return new PartyFailure(code,message); }
    private static void require(boolean ok,int code,String message) { if(!ok) throw fail(code,message); }
}
