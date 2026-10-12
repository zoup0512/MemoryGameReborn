#!/usr/bin/env python3
"""Fourth pass: activeandroid generics + remaining decompiler flow artifacts."""
import re
from pathlib import Path

JAVA = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main\java")

def patch(rel, subs, label, count=1):
    p = JAVA / rel
    t = p.read_text(encoding="utf-8")
    o = t
    for old, new in subs:
        if old not in t:
            print(f"  !! missing in {label}: {old[:70]!r}")
            continue
        t = t.replace(old, new, count)
    if t != o:
        p.write_text(t, encoding="utf-8")
        print(f"patched {label}")

# 1. From.from generic (restores T inference through execute()/executeSingle())
patch("com/activeandroid/query/From.java", [
    ("public From from(Class<? extends Model> table) {",
     "public <T extends Model> From from(Class<T> table) {"),
], "From.from generic")

# 2. Model.java: duplicate catches + loadFromCursor field2 artifacts
p = JAVA / "com/activeandroid/Model.java"
t = p.read_text(encoding="utf-8")
t = t.replace("""            } catch (Throwable e) {
                Log.e(e.getClass().getName(), e);
            } catch (Throwable e2) {
                Log.e(e2.getClass().getName(), e2);
            }""",
"""            } catch (Throwable e) {
                Log.e(e.getClass().getName(), e);
            }""", 1)
t = t.replace("""                } catch (Throwable e) {
                    Log.e(e.getClass().getName(), e);
                } catch (Throwable e2) {
                    Log.e(e2.getClass().getName(), e2);
                } catch (Throwable e22) {
                    Log.e(e22.getClass().getName(), e22);
                }""",
"""                } catch (Throwable e) {
                    Log.e(e.getClass().getName(), e);
                }""", 1)
t = t.replace("""            Field field2;
            String fieldName = this.mTableInfo.getColumnName(field2);
            Class<?> fieldType = field2.getType();
            int columnIndex = columnsOrdered.indexOf(fieldName);
            if (columnIndex >= 0) {
                field2.setAccessible(true);""",
"""            String fieldName = this.mTableInfo.getColumnName(field);
            Class<?> fieldType = field.getType();
            int columnIndex = columnsOrdered.indexOf(fieldName);
            if (columnIndex >= 0) {
                field.setAccessible(true);""", 1)
t = t.replace("field2.set(this, value);", "field.set(this, value);", 1)
p.write_text(t, encoding="utf-8")
print("patched Model")

# 3. DatabaseHelper reader type
patch("com/activeandroid/DatabaseHelper.java", [
    ("Closeable reader2 = new InputStreamReader(stream);",
     "Reader reader2 = new InputStreamReader(stream);"),
], "DatabaseHelper reader")

# 4. SQLiteUtils entity typing
patch("com/activeandroid/util/SQLiteUtils.java", [
    ("                    Model entity = Cache.getEntity(type, cursor.getLong(columnsOrdered.indexOf(idName)));",
     "                    T entity = (T) Cache.getEntity(type, cursor.getLong(columnsOrdered.indexOf(idName)));"),
], "SQLiteUtils entity")

# 5. ChallengeAdapter holder casts
patch("com/cube/memorygames/ChallengeAdapter.java", [
    ("""            holder = new ButtonBottomHolder(layoutInflater.inflate(R.layout.item_list_game_challenge_bottom_button, parent, false));
            holder.levelName.setTypeface(this.typefaceRegular);
            holder.nextLevel.setTypeface(this.typefaceRegular);
            holder.starsCount.setTypeface(this.typefaceRegular);""",
     """            holder = new ButtonBottomHolder(layoutInflater.inflate(R.layout.item_list_game_challenge_bottom_button, parent, false));
            ((ButtonBottomHolder) holder).levelName.setTypeface(this.typefaceRegular);
            ((ButtonBottomHolder) holder).nextLevel.setTypeface(this.typefaceRegular);
            ((ButtonBottomHolder) holder).starsCount.setTypeface(this.typefaceRegular);"""),
    ("""            holder = new ButtonTopHolder(layoutInflater.inflate(R.layout.item_list_game_challenge_top_button, parent, false));
            holder.levelName.setTypeface(this.typefaceRegular);
            holder.starsCount.setTypeface(this.typefaceRegular);""",
     """            holder = new ButtonTopHolder(layoutInflater.inflate(R.layout.item_list_game_challenge_top_button, parent, false));
            ((ButtonTopHolder) holder).levelName.setTypeface(this.typefaceRegular);
            ((ButtonTopHolder) holder).starsCount.setTypeface(this.typefaceRegular);"""),
    ("""            holder = new GameHolder(view);
            holder.name.setTypeface(this.typefaceLight);
            holder.categoryName.setTypeface(this.typefaceRegular, 1);""",
     """            holder = new GameHolder(view);
            ((GameHolder) holder).name.setTypeface(this.typefaceLight);
            ((GameHolder) holder).categoryName.setTypeface(this.typefaceRegular, 1);"""),
], "ChallengeAdapter casts")

# 6. LocalDataManager addWorkoutResult typing
patch("com/cube/memorygames/api/local/LocalDataManager.java", [
    ("        Object gameIds;\n        Gson gson = new Gson();",
     "        List<DbLevelInfo> gameIds;\n        Gson gson = new Gson();"),
    ("            gameIds = (List) gson.fromJson(localWorkoutGame.games, new TypeToken<List<DbLevelInfo>>() {",
     "            gameIds = (List<DbLevelInfo>) gson.fromJson(localWorkoutGame.games, new TypeToken<List<DbLevelInfo>>() {"),
    ("            gameIds = new ArrayList();\n            for (GameInfo gameInfo : gameInfoList) {\n                gameIds.add(new DbLevelInfo(gameInfo.getId(), 0));\n            }\n            localWorkoutGame = new LocalWorkoutGame();\n            localWorkoutGame.date = date;",
     "            gameIds = new ArrayList();\n            for (GameInfo gameInfo : gameInfoList) {\n                gameIds.add(new DbLevelInfo(gameInfo.getId(), 0));\n            }\n            localWorkoutGame = new LocalWorkoutGame();\n            localWorkoutGame.date = getCurrentDate();"),
], "addWorkoutResult")

# 7. no-arg constructors for ActiveAndroid models
for rel in ["com/cube/memorygames/api/local/model/LocalGameStats.java",
            "com/cube/memorygames/api/local/model/LocalCoinsTransaction.java",
            "com/cube/memorygames/api/local/model/LocalGameSession.java"]:
    p = JAVA / rel
    t = p.read_text(encoding="utf-8")
    m = re.search(r"(public (?:final )?class \w+ extends Model \{\n)", t)
    if m and re.search(r"public \w+\(\) \{\n", t):
        print("skip (has no-arg):", rel)
        continue
    if m:
        t = t.replace(m.group(1), m.group(1) + "    public " + Path(rel).stem + "() {\n    }\n\n", 1)
        p.write_text(t, encoding="utf-8")
        print("added no-arg ctor:", rel)

# 8. rewrite updateInformation wholesale
p = JAVA / "com/cube/memorygames/api/local/LocalDataManager.java"
t = p.read_text(encoding="utf-8")
start = t.index("    private void updateInformation(Context context) {")
end = t.index("    private static int getStars(int score, int goal, String gameId, int level) {")
new_method = """    private void updateInformation(Context context) {
        if (this.challengeJsonGames == null || this.totalChallengeScore == null || this.totalChallengeStars == null || this.totalChallengeRating == null) {
            try {
                InputStream is = context.getAssets().open("challenge.json");
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                String jsonGames = new String(buffer, "UTF-8");
                is = context.getAssets().open("challenge_levels.json");
                buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                String jsonLevels = new String(buffer, "UTF-8");

                List<ChallengeJsonGame> challengeJsonGames = (List<ChallengeJsonGame>) new Gson().fromJson(jsonGames, new TypeToken<List<ChallengeJsonGame>>(){}.getType());
                List<ChallengeJsonLevel> challengeJsonLevels = (List<ChallengeJsonLevel>) new Gson().fromJson(jsonLevels, new TypeToken<List<ChallengeJsonLevel>>(){}.getType());
                List<ChallengeDbGame> savedGames = new Select().from(ChallengeDbGame.class).execute();
                Map<Integer, ChallengeDbGame> map = new HashMap<Integer, ChallengeDbGame>();
                for (ChallengeDbGame dbGame : savedGames) {
                    map.put(Integer.valueOf(dbGame.levelNumber), dbGame);
                }
                int totalScore = 0;
                int totalStars = 0;
                double totalRating = 0.0d;
                for (int i = 0; i < challengeJsonGames.size(); i++) {
                    ChallengeJsonGame challengeJsonGame = (ChallengeJsonGame) challengeJsonGames.get(i);
                    challengeJsonGame.setLevelNumber(i + 1);
                    ChallengeDbGame dbGame = (ChallengeDbGame) map.get(Integer.valueOf(challengeJsonGame.getLevelNumber()));
                    if (dbGame != null) {
                        int stars = getStars(dbGame.score, dbGame.goal, dbGame.gameId, challengeJsonGame.getLevel());
                        challengeJsonGame.setStars(stars);
                        double rating = 1.0d + (((double) i) * 0.3d);
                        if (stars == 3) {
                            totalRating += rating;
                        } else if (stars == 2) {
                            totalRating += 0.9d * rating;
                        } else if (stars == 1) {
                            totalRating += 0.8d * rating;
                        }
                        totalStars += stars;
                        totalScore += dbGame.score;
                    }
                }
                int levelNumber = getChallengeLevelNumber(context);
                int startGame = 0;
                for (int i = 0; i < levelNumber; i++) {
                    startGame += ((ChallengeJsonLevel) challengeJsonLevels.get(i)).getGames();
                }
                this.challengeJsonGames = challengeJsonGames.subList(startGame, startGame + ((ChallengeJsonLevel) challengeJsonLevels.get(levelNumber)).getGames());
                this.totalChallengeRating = Double.valueOf(totalRating);
                this.totalChallengeStars = Integer.valueOf(totalStars);
                this.totalChallengeScore = Integer.valueOf(totalScore);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

"""
t = t[:start] + new_method + t[end:]
p.write_text(t, encoding="utf-8")
print("updateInformation rewritten")
print("done")
