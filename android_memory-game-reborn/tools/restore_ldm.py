#!/usr/bin/env python3
"""Rebuild the truncated tail of LocalDataManager (lost to a bad in-place
edit): finish getWorkoutRating and restore the challenge/workout helpers
whose call sites are preserved across the game sources."""
from pathlib import Path

P = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main\java\com\cube\memorygames\api\local\LocalDataManager.java")
t = P.read_text(encoding="utf-8")

# the file currently ends with the unterminated second loop of getWorkoutRating
end_marker = "        float rating = 0.0f;\n        for (GameInfo gameInfo : Games.get().getSprintGames()) {\n"
idx = t.rindex(end_marker)
t = t[:idx] + """        float rating = 0.0f;
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
"""
P.write_text(t, encoding="utf-8")
print("LocalDataManager tail rebuilt; lines:", t.count("\n"))
