import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Scanner;

/** Creates a disposable pre-account database; refuses non-fixture databases. */
class LegacyAccountFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[1].startsWith("jdbc:h2:file:")
                || !args[1].contains("account-migration-fixture")) {
            throw new IllegalArgumentException("Expected seed|check, disposable fixture JDBC URL, migration directory");
        }
        Class.forName("org.h2.Driver");
        try (Connection connection = DriverManager.getConnection(args[1], "sa", "")) {
            if (args[0].equals("seed")) {
                try (Statement query = connection.createStatement()) {
                    ResultSet tables = query.executeQuery("select count(*) from information_schema.tables where table_schema='PUBLIC'");
                    tables.next();
                    if (tables.getInt(1) != 0) {
                        throw new IllegalStateException("Fixture database is not empty; refusing overwrite");
                    }
                }
                for (String file : new String[]{"V1__baseline.sql", "V2__phase2_hardening.sql", "V3__phase3_recovery.sql"}) {
                    String sql = Files.readString(Path.of(args[2], file)).replaceAll("(?m)--.*$", "");
                    try (Statement statement = connection.createStatement()) {
                        for (String part : sql.split(";")) {
                            if (!part.isBlank()) statement.execute(part);
                        }
                    }
                }
                Scanner input = new Scanner(System.in);
                String token = input.nextLine();
                String uuid = input.nextLine();
                if (token.length() < 32 || uuid.length() < 16) throw new IllegalArgumentException("Invalid fixture secrets");
                long now = System.currentTimeMillis();
                try (PreparedStatement statement = connection.prepareStatement("insert into pk_user (id,uuid,token,nickname,elo,wins,losses,draws,balance,created_at,updated_at,suspicion,restricted_matches,banned_until,deleted,practice_streak) values (91001,?,?, 'LegacyFixture',1450,7,3,1,321,?,?,2,0,0,false,4)")) {
                    statement.setString(1, uuid);
                    statement.setString(2, token);
                    statement.setLong(3, now);
                    statement.setLong(4, now);
                    statement.executeUpdate();
                }
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("insert into pk_match_record (match_id,mode,player_a,nickname_a,result_a,score_a,player_b,nickname_b,score_b,elo_delta_a,created_at,settled_at,bot_opponent,voided) values ('legacy-fixture-match','ranked',91001,'LegacyFixture','win',123,-1,'Bot',100,12,1,2,true,false)");
                    statement.executeUpdate("insert into pk_coin_ledger (user_id,match_id,amount,reason,created_at) values (91001,null,321,'WELCOME',1)");
                }
                System.out.println("Legacy V1-V3 fixture seeded");
            } else if (args[0].equals("check")) {
                try (Statement statement = connection.createStatement()) {
                    ResultSet user = statement.executeQuery("select balance,elo,wins,losses,draws,suspicion,practice_streak,legacy_auth_disabled from pk_user where id=91001");
                    if (!user.next() || user.getInt(1) != 321 || user.getInt(2) != 1450
                            || user.getInt(3) != 7 || user.getInt(4) != 3 || user.getInt(5) != 1
                            || user.getInt(6) != 2 || user.getInt(7) != 4 || !user.getBoolean(8)) {
                        throw new AssertionError("Legacy identity, asset, statistics or migration gate changed");
                    }
                    ResultSet records = statement.executeQuery("select count(*) from pk_match_record where player_a=91001 and match_id='legacy-fixture-match'");
                    records.next();
                    if (records.getInt(1) != 1) throw new AssertionError("Match history lost");
                    ResultSet ledger = statement.executeQuery("select count(*),sum(amount) from pk_coin_ledger where user_id=91001");
                    ledger.next();
                    if (ledger.getInt(1) != 1 || ledger.getInt(2) != 321) throw new AssertionError("Ledger changed or welcome grant duplicated");
                }
                System.out.println("Legacy migration preserved userId, balance, ELO, statistics, history and ledger");
            } else {
                throw new IllegalArgumentException("Unknown mode");
            }
        }
    }
}
