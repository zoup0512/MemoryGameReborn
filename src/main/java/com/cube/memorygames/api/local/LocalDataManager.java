package com.cube.memorygames.api.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import com.activeandroid.query.Delete;
import com.activeandroid.query.Select;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.cube.memorygames.Games;
import com.cube.memorygames.MemoryApplicationModel;
import com.cube.memorygames.api.local.challenge.ChallengeDbGame;
import com.cube.memorygames.api.local.challenge.ChallengeJsonGame;
import com.cube.memorygames.api.local.challenge.ChallengeJsonLevel;
import com.cube.memorygames.api.local.model.LocalCoinsTransaction;
import com.cube.memorygames.api.local.model.LocalGameSession;
import com.cube.memorygames.api.local.model.LocalGameStats;
import com.cube.memorygames.api.local.model.LocalUser;
import com.cube.memorygames.api.local.model.LocalWorkoutGame;
import com.cube.memorygames.api.local.workout.DayInfo;
import com.cube.memorygames.api.local.workout.DbLevelInfo;
import com.cube.memorygames.api.local.workout.LevelInfo;

import com.cube.memorygames.model.GameInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.memory.brain.training.games.R;
import com.mopub.volley.DefaultRetryPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LocalDataManager {
    private static final int DAYS_IN_WEAK = 7;
    private static final int MAX_CHALLENGE_LEVEL = 10;
    private static final String PREFIX_IS_PLAYED = "is_played_challenge_";
    private static final String PREF_LEVEL_NUMBER = "challengeLevelNumberNew";
    private static final String PREF_NEED_CHALLENGE_SYNCHRONIZATION = "needChallengeSynchronization";
    public static final String TYPE_BUY = "BuyCoins";
    public static final String TYPE_BUY_GAME = "UnlockGame-";
    public static final String TYPE_BUY_LEVEL = "BuyLevel";
    public static final String TYPE_DRAW_LEVEL = "DrawOnline";
    public static final String TYPE_FACEBOOK = "Facebook";
    public static final String TYPE_FIRST_START = "FirstStart";
    public static final String TYPE_GAME_EARNED = "GameEarned";
    public static final String TYPE_PRO = "BecomePro";
    public static final String TYPE_SHARE_DIALOG_FB = "ShareDialogFb";
    public static final String TYPE_SHARE_DIALOG_VIDEO = "ShareDialogVideo";
    public static final String TYPE_START_ONLINE = "StartOnline";
    public static final String TYPE_USERNAME = "UserName";
    public static final String TYPE_WIN_ONLINE = "WinOnline";
    // Online PK settlement ledger entries (mirrored from the server, PRD 3.9).
    public static final String TYPE_LOSE_ONLINE = "LoseOnline";
    public static final int ONLINE_ENTRY_FEE = 30;
    public static final int ONLINE_WIN_COINS = 50;
    public static final int ONLINE_DRAW_COINS = 15;
    public static final int WORKOUT_GAME_COUNT = 4;
    private List<ChallengeJsonGame> challengeJsonGames;
    private Integer currentLevelNumber;
    private LocalUser localUser = ((LocalUser) new Select().from(LocalUser.class).executeSingle());
    private Double totalChallengeRating;
    private Integer totalChallengeScore;
    private Integer totalChallengeStars;

    public LocalDataManager() {
        if (this.localUser == null) {
            this.localUser = new LocalUser();
            this.localUser.createdAt = new Date();
            this.localUser.updatedAt = new Date();
            this.localUser.save();
            addCoinsTransaction(TYPE_FIRST_START, 60);
        }
    }

    public void addGameSession(Context context, LocalGameSession localGameSession) {
        if (localGameSession.createdAt == null) {
            localGameSession.createdAt = new Date();
        }
        if (localGameSession.updatedAt == null) {
            localGameSession.updatedAt = new Date();
        }
        localGameSession.save();
        LocalGameStats localGameStats = (LocalGameStats) new Select().from(LocalGameStats.class).where("game = ?", localGameSession.game).executeSingle();
        if (localGameStats == null) {
            localGameStats = new LocalGameStats();
            localGameStats.game = localGameSession.game;
            localGameStats.avgLevel = 0.0f;
            localGameStats.maxLevel = 0;
        }
        if (localGameSession.endLevel > localGameStats.maxLevel) {
            localGameStats.maxLevel = localGameSession.endLevel;
        }
        List<LocalGameSession> localGameSessions = new Select().from(LocalGameSession.class).where("game = ?", localGameSession.game).orderBy("createdAt DESC").limit(7).execute();
        float total = 0.0f;
        for (LocalGameSession session : localGameSessions) {
            total += (float) session.endLevel;
        }
        localGameStats.avgLevel = total / ((float) localGameSessions.size());
        localGameStats.save();
        float rating = 0.0f;
        for (LocalGameStats stat : (List<LocalGameStats>) new Select().from(LocalGameStats.class).<LocalGameStats>execute()) {
            rating += stat.avgLevel;
        }
        rating *= 2.0f;
        if (((double) this.localUser.challengeRating) < getTotalChallengeRating(context)) {
            this.localUser.challengeRating = (float) getTotalChallengeRating(context);
        }
        this.localUser.rating = this.localUser.challengeRating + rating;
        this.localUser.save();
    }

    public List<LocalGameSession> getLastGames(String gameId) {
        return new Select().from(LocalGameSession.class).where("game = ?", gameId).orderBy("createdAt DESC").limit(7).execute();
    }

    public Map<String, LocalGameStats> getLocalGameStats() {
        List<LocalGameStats> stats = new Select().from(LocalGameStats.class).execute();
        Map<String, LocalGameStats> result = new HashMap();
        for (LocalGameStats stat : stats) {
            result.put(stat.game, stat);
        }
        return result;
    }

    public void addCoinsTransaction(String item, int amount) {
        addCoinsTransaction(item, amount, null);
    }

    public void addCoinsTransaction(String item, int amount, String unlockedContent) {
        if (!(item.equals(TYPE_GAME_EARNED) || item.equals(TYPE_BUY_LEVEL))) {
            LocalCoinsTransaction coinsTransaction = new LocalCoinsTransaction();
            coinsTransaction.item = item;
            coinsTransaction.amount = amount;
            coinsTransaction.createdAt = new Date();
            coinsTransaction.updatedAt = new Date();
            if (!TextUtils.isEmpty(unlockedContent)) {
                this.localUser.addUnlockedContent(unlockedContent);
            }
            coinsTransaction.save();

        }
        LocalUser localUser = this.localUser;
        localUser.money += amount;
        this.localUser.save();
    }

    public LocalUser getLocalUser() {
        return this.localUser;
    }

    public void setDisplayName(String displayName) {
        this.localUser.displayName = displayName;
        this.localUser.updatedAt = new Date();
        this.localUser.save();
    }

    public void setFbId(String facebookId) {
        this.localUser.facebookId = facebookId;
        this.localUser.updatedAt = new Date();
        this.localUser.save();
    }

    public List<LocalGameSession> getNewGameSessions() {
        return new Select().from(LocalGameSession.class).where("uploaded = ?", Boolean.valueOf(false)).execute();
    }

    public List<LocalCoinsTransaction> getNewLocalCoinsTransaction() {
        return new Select().from(LocalCoinsTransaction.class).where("uploaded = ?", Boolean.valueOf(false)).execute();
    }

    public void chargeForOnline() {
        if (!getLocalUser().unlimitedOnline) {
            addCoinsTransaction(TYPE_START_ONLINE, -30);
        }
    }

    public void refundForOnline() {
        if (!getLocalUser().unlimitedOnline) {
            addCoinsTransaction(TYPE_START_ONLINE, 30);
        }
    }

    public int getGamesSessionCount() {
        return new Select().from(LocalGameSession.class).count();
    }

    public void resetWorkoutDay() {
        String date = getCurrentDate();
        new Delete().from(LocalWorkoutGame.class).where("date = ?", date).execute();
    }

    public void addWorkoutResult(String finishedGameId, int levelNumber) {
        List<DbLevelInfo> gameIds;
        Gson gson = new Gson();
        LocalWorkoutGame localWorkoutGame = (LocalWorkoutGame) new Select().from(LocalWorkoutGame.class).where("date = ?", getCurrentDate()).executeSingle();
        if (localWorkoutGame != null) {
            gameIds = (List<DbLevelInfo>) gson.fromJson(localWorkoutGame.games, new TypeToken<List<DbLevelInfo>>() {
            }.getType());
        } else {
            List<GameInfo> gameInfoList = new ArrayList(Games.get().getSprintGames());
            gameInfoList.remove(Games.get().getGameBiId(Games.SMART_PROMO_GAME_ID));
            gameInfoList.remove(Games.get().getGameBiId(Games.UNLOCK_ALL_GAMES_ID));
            Collections.shuffle(gameInfoList);
            gameInfoList = gameInfoList.subList(0, 4);
            gameIds = new ArrayList();
            for (GameInfo gameInfo : gameInfoList) {
                gameIds.add(new DbLevelInfo(gameInfo.getId(), 0));
            }
            localWorkoutGame = new LocalWorkoutGame();
            localWorkoutGame.date = getCurrentDate();
            localWorkoutGame.games = gson.toJson(gameIds);
            localWorkoutGame.save();
        }
        int i = 0;
        while (i < gameIds.size()) {
            DbLevelInfo gameData = (DbLevelInfo) gameIds.get(i);
            if (!gameData.getGameId().equals(finishedGameId)) {
                i++;
            } else if (levelNumber > gameData.getLevel()) {
                gameData.setLevel(levelNumber);
                localWorkoutGame.games = gson.toJson(gameIds);
                localWorkoutGame.save();
                return;
            } else {
                return;
            }
        }
    }

    public List<LevelInfo> getWorkoutGames() {
        Gson gson = new Gson();
        LocalWorkoutGame localWorkoutGame = (LocalWorkoutGame) new Select().from(LocalWorkoutGame.class).where("date = ?", getCurrentDate()).executeSingle();
        List<DbLevelInfo> gameIds;
        if (localWorkoutGame != null) {
            gameIds = (List<DbLevelInfo>) gson.fromJson(localWorkoutGame.games, new TypeToken<List<DbLevelInfo>>() {
            }.getType());
        } else {
            List<GameInfo> gameInfoList = new ArrayList(Games.get().getSprintGames());
            gameInfoList.remove(Games.get().getGameBiId(Games.SMART_PROMO_GAME_ID));
            gameInfoList.remove(Games.get().getGameBiId(Games.UNLOCK_ALL_GAMES_ID));
            Collections.shuffle(gameInfoList);
            gameInfoList = gameInfoList.subList(0, 4);
            gameIds = new ArrayList();
            for (GameInfo gameInfo : gameInfoList) {
                gameIds.add(new DbLevelInfo(gameInfo.getId(), 0));
            }
            localWorkoutGame = new LocalWorkoutGame();
            localWorkoutGame.date = getCurrentDate();
            localWorkoutGame.games = gson.toJson(gameIds);
            localWorkoutGame.save();
        }
        List<LevelInfo> result = new ArrayList();
        for (DbLevelInfo gameData : gameIds) {
            result.add(new LevelInfo(Games.get().getGameBiId(gameData.getGameId()), gameData.getLevel()));
        }
        return result;
    }

    public List<DayInfo> getCurrentWeekGames() {
        Gson gson = new Gson();
        GregorianCalendar calendar = new GregorianCalendar();
        while (calendar.get(7) != 2) {
            calendar.add(7, -1);
        }
        String query = "";
        String[] dates = new String[7];
        List<DayInfo> result = new ArrayList();
        String[] weekdays = new DateFormatSymbols().getShortWeekdays();
        for (int i = 0; i < dates.length; i++) {
            query = query + "date = ?";
            dates[i] = getFormattedDate(calendar.getTime());
            DayInfo dayInfo = new DayInfo();
            dayInfo.setDate(dates[i]);
            dayInfo.setDayOfWeek(weekdays[calendar.get(7)]);
            result.add(dayInfo);
            calendar.add(7, 1);
            if (i < dates.length - 1) {
                query = query + " or ";
            }
        }
        List<LocalWorkoutGame> localWorkoutGames = new Select().from(LocalWorkoutGame.class).where(query, (Object[]) dates).execute();
        if (localWorkoutGames == null) {
            localWorkoutGames = new ArrayList();
        }
        for (DayInfo dayInfo2 : result) {
            for (LocalWorkoutGame localWorkoutGame : localWorkoutGames) {
                if (dayInfo2.getDate().equals(localWorkoutGame.date)) {
                    List<DbLevelInfo> dbLevelInfoList = (List) gson.fromJson(localWorkoutGame.games, new TypeToken<List<DbLevelInfo>>() {
                    }.getType());
                    if (dbLevelInfoList == null) {
                        dbLevelInfoList = new ArrayList();
                    }
                    dayInfo2.setDayGames(dbLevelInfoList);
                }
            }
        }
        return result;
    }

    public boolean isWorkoutGameGenerated() {
        return new Select().from(LocalWorkoutGame.class).where("date = ?", getCurrentDate()).executeSingle() != null;
    }

    public float getWorkoutRating() {
        Gson gson = new Gson();
        List<LocalWorkoutGame> localWorkoutGames = new Select().from(LocalWorkoutGame.class).orderBy("date").execute();
        if (localWorkoutGames == null) {
            localWorkoutGames = new ArrayList();
        }
        Map<String, List<Integer>> totalResults = new HashMap();
        for (LocalWorkoutGame localWorkoutGame : localWorkoutGames) {
            List<DbLevelInfo> dbLevelInfoList = (List) gson.fromJson(localWorkoutGame.games, new TypeToken<List<DbLevelInfo>>() {
            }.getType());
            if (dbLevelInfoList == null) {
                dbLevelInfoList = new ArrayList();
            }
            for (DbLevelInfo levelInfo : dbLevelInfoList) {
                List<Integer> gameResults = (List) totalResults.get(levelInfo.getGameId());
                if (gameResults == null) {
                    gameResults = new ArrayList();
                    totalResults.put(levelInfo.getGameId(), gameResults);
                }
                if (levelInfo.getLevel() > 0) {
                    gameResults.add(Integer.valueOf(levelInfo.getLevel()));
                }
            }
        }
        float rating = 0.0f;
        for (GameInfo gameInfo : Games.get().getSprintGames()) {
            List<Integer> gameResults2 = (List<Integer>) totalResults.get(gameInfo.getId());
            if (gameResults2 != null) {
                gameResults2 = gameResults2.subList(Math.max(gameResults2.size() - 3, 0), gameResults2.size());
                if (!gameResults2.isEmpty()) {
                    float levelRating = 0.0f;
                    for (Integer integer : gameResults2) {
                        levelRating += (float) integer.intValue();
                    }
                    rating += 2.0f * (levelRating / ((float) gameResults2.size()));
                }
            }
        }
        return rating;
    }

    public float getAverageWorkoutLevel(String gameId) {
        Gson gson = new Gson();
        List<LocalWorkoutGame> localWorkoutGames = new Select().from(LocalWorkoutGame.class).orderBy("date").execute();
        if (localWorkoutGames == null) {
            return 0.0f;
        }
        float total = 0.0f;
        int count = 0;
        for (LocalWorkoutGame localWorkoutGame : localWorkoutGames) {
            List<DbLevelInfo> dbLevelInfoList = (List<DbLevelInfo>) gson.fromJson(localWorkoutGame.games, new TypeToken<List<DbLevelInfo>>() {
            }.getType());
            if (dbLevelInfoList == null) {
                continue;
            }
            for (DbLevelInfo levelInfo : dbLevelInfoList) {
                if (levelInfo.getGameId().equals(gameId) && levelInfo.getLevel() > 0) {
                    total += (float) levelInfo.getLevel();
                    count++;
                }
            }
        }
        if (count == 0) {
            return 0.0f;
        }
        return total / ((float) count);
    }

    public List<ChallengeJsonGame> getChallengeGames(Context context) {
        updateInformation(context);
        return this.challengeJsonGames;
    }

    private void updateInformation(Context context) {
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

    public int getChallengeLevelNumber(Context context) {
        if (this.currentLevelNumber == null) {
            this.currentLevelNumber = Integer.valueOf(PreferenceManager.getDefaultSharedPreferences(context).getInt(PREF_LEVEL_NUMBER, 0));
        }
        if (this.currentLevelNumber.intValue() > 10) {
            this.currentLevelNumber = Integer.valueOf(10);
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREF_LEVEL_NUMBER, this.currentLevelNumber.intValue()).apply();
        }
        return this.currentLevelNumber.intValue();
    }

    public String getCurrentChallengeLevelName(Context context) {
        int levelNumber = getChallengeLevelNumber(context);
        if (levelNumber > 10) {
            return null;
        }
        return context.getString(R.string.level_number_prefix) + " " + (levelNumber + 1);
    }

    public String getNextChallengeLevelName(Context context) {
        int levelNumber = getChallengeLevelNumber(context);
        if (levelNumber >= 10) {
            return null;
        }
        return context.getString(R.string.level_number_prefix) + " " + (levelNumber + 2);
    }

    public void prevChallengeLevelNumber(Context context) {
        int levelNumber = getChallengeLevelNumber(context);
        if (levelNumber > 0) {
            this.currentLevelNumber = Integer.valueOf(levelNumber - 1);
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREF_LEVEL_NUMBER, this.currentLevelNumber.intValue()).apply();
        }
        resetInformation();
    }

    public void nextChallengeLevelNumber(Context context) {
        int levelNumber = getChallengeLevelNumber(context);
        if (levelNumber < 10) {
            this.currentLevelNumber = Integer.valueOf(levelNumber + 1);
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(PREF_LEVEL_NUMBER, this.currentLevelNumber.intValue()).apply();
        }
        resetInformation();
    }

    public int addGameAndGetStars(Context context, int levelNumber, String gameId, int score, int goal, int level) {
        ChallengeDbGame challengeDbGame = (ChallengeDbGame) new Select().from(ChallengeDbGame.class).where("levelNumber = ?", Integer.valueOf(levelNumber)).executeSingle();
        if (challengeDbGame == null) {
            challengeDbGame = new ChallengeDbGame();
            challengeDbGame.createdAt = new Date();
            challengeDbGame.levelNumber = levelNumber;
        } else if (challengeDbGame.goal == goal && challengeDbGame.score >= score) {
            return getStars(score, goal, gameId, level);
        }
        challengeDbGame.gameId = gameId;
        challengeDbGame.updatedAt = new Date();
        challengeDbGame.score = score;
        challengeDbGame.goal = goal;
        challengeDbGame.save();
        resetInformation();
        setNeedChallengeSynchronization(true);
        if (this.localUser.challengeRating < ((float) getTotalChallengeRating(context))) {
            float sprintRating = this.localUser.rating - this.localUser.challengeRating;
            this.localUser.challengeRating = (float) getTotalChallengeRating(context);
            this.localUser.rating = this.localUser.challengeRating + sprintRating;
        }
        this.localUser.save();
        return getStars(score, goal, gameId, level);
    }

    public ChallengeJsonGame getGame(Context context, int levelNumber) {
        updateInformation(context);
        for (ChallengeJsonGame challengeJsonGame : this.challengeJsonGames) {
            if (challengeJsonGame.getLevelNumber() == levelNumber) {
                return challengeJsonGame;
            }
        }
        return null;
    }

    public boolean wasChallengeTutorialShowed(Context context, String analyticsName) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREFIX_IS_PLAYED + analyticsName, false);
    }

    public void setShowChallengeTutorial(Context context, String analyticsName) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREFIX_IS_PLAYED + analyticsName, true).apply();
    }

    private void resetInformation() {
        this.challengeJsonGames = null;
        this.totalChallengeRating = null;
        this.totalChallengeStars = null;
        this.totalChallengeScore = null;
    }

    public void setNeedChallengeSynchronization(boolean need) {
        PreferenceManager.getDefaultSharedPreferences(MemoryApplicationModel.getInstance()).edit().putBoolean(PREF_NEED_CHALLENGE_SYNCHRONIZATION, need).apply();
    }

    public double getTotalChallengeRating(Context context) {
        updateInformation(context);
        if (this.totalChallengeRating == null) {
            return 0.0d;
        }
        return this.totalChallengeRating.doubleValue();
    }

    private static int getStars(int score, int goal, String gameId, int level) {
        if (score >= goal) {
            return 3;
        }
        if (score >= (int) (((double) goal) * 0.75d)) {
            return 2;
        }
        if (score >= (int) (((double) goal) * 0.5d)) {
            return 1;
        }
        return 0;
    }

    private String getCurrentDate() {
        return getFormattedDate(new Date());
    }

    private String getFormattedDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date);
    }
}
