package com.cube.memorygames;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import com.cube.memorygames.api.local.challenge.ChallengeJsonGame;
import com.cube.memorygames.games.Game10OneAndOnlyActivity;
import com.cube.memorygames.games.Game11CorrectlyActivity;
import com.cube.memorygames.games.Game12MoreLessActivity;
import com.cube.memorygames.games.Game13AllTheSameActivity;
import com.cube.memorygames.games.Game14SortTheDigitsActivity;
import com.cube.memorygames.games.Game15PaperPlanesActivity;
import com.cube.memorygames.games.Game16LikePreviousActivity;
import com.cube.memorygames.games.Game17_248Activity;
import com.cube.memorygames.games.Game18PyramidsActivity;
import com.cube.memorygames.games.Game19FindAllActivity;
import com.cube.memorygames.games.Game1MemoryGridActivity;
import com.cube.memorygames.games.Game20ColorsActivity;
import com.cube.memorygames.games.Game21SymmetryActivity;
import com.cube.memorygames.games.Game22MirrorsActivity;
import com.cube.memorygames.games.Game23TableActivity;
import com.cube.memorygames.games.Game2RotatingGridActivity;
import com.cube.memorygames.games.Game3HexagonsActivity;
import com.cube.memorygames.games.Game4WhoNewActivity;
import com.cube.memorygames.games.Game5CountAllActivity;
import com.cube.memorygames.games.Game6FollowThePathActivity;
import com.cube.memorygames.games.Game7ImageVortexActivity;
import com.cube.memorygames.games.Game8CatchThemActivity;
import com.cube.memorygames.games.Game9FindThePictureActivity;
import com.cube.memorygames.model.CategoryInfo;
import com.cube.memorygames.model.GameInfo;
import com.facebook.appevents.AppEventsConstants;
import com.memory.brain.training.games.R;
import com.mopub.mobileads.resource.DrawableConstants.CtaButton;
import io.fabric.sdk.android.services.settings.SettingsJsonConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Games {
    public static final String SMART_PROMO_GAME_ID = "-1";
    public static final String UNLOCK_ALL_GAMES_ID = "-2";
    private static Games instance = new Games();
    private GameInfo allTheSame = new GameInfo(R.string.game_name_13, "All the Same", R.drawable.th13, 45, "12");
    private GameInfo catchThem = new GameInfo(R.string.game_name_8, "Catch Them", R.drawable.th8, 80, "7", CtaButton.WIDTH_DIPS);
    private CategoryInfo categoryAttention = new CategoryInfo(R.string.categoryAttention, AppEventsConstants.EVENT_PARAM_VALUE_YES, R.color.categoryAttention, R.color.attention_background, R.color.attention_background_grid);
    private CategoryInfo categoryFlexibility = new CategoryInfo(R.string.categoryFlexibility, "4", R.color.categoryFlexibility, R.color.flexibility_background, R.color.flexibility_background_grid);
    private CategoryInfo categoryImagination = new CategoryInfo(R.string.categoryImagination, "5", R.color.categoryImagination, R.color.imagination_background, R.color.imagination_background_grid);
    private CategoryInfo categoryMemory = new CategoryInfo(R.string.categoryMemory, AppEventsConstants.EVENT_PARAM_VALUE_NO, R.color.categoryMemory, R.color.memory_background, R.color.memory_background_grid);
    private CategoryInfo categoryProblemSolving = new CategoryInfo(R.string.categoryProblemSolving, "3", R.color.categoryProblemSolving, R.color.problem_solving_background, R.color.problem_solving_background_grid);
    private CategoryInfo categorySpeed = new CategoryInfo(R.string.categorySpeed, "2", R.color.categorySpeed, R.color.speed_background, R.color.speed_background_grid);
    private GameInfo colors = new GameInfo(R.string.game_name_20, "Colors", R.drawable.th20, 30, "19");
    private GameInfo correctly = new GameInfo(R.string.game_name_11, "Correctly?", R.drawable.th12, 60, "10");
    private GameInfo countAll = new GameInfo(R.string.game_name_5, "Count'em All", R.drawable.th5, 60, "4");
    private GameInfo findAll = new GameInfo(R.string.game_name_19, "Find All", R.drawable.th19, 60, "18", SettingsJsonConstants.ANALYTICS_FLUSH_INTERVAL_SECS_DEFAULT);
    private GameInfo findThePicture = new GameInfo(R.string.game_name_9, "Find the Picture", R.drawable.th9, 80, "8", 300);
    private GameInfo followThePath = new GameInfo(R.string.game_name_6, "Follow the Path", R.drawable.th6, 90, "5");
    private GameInfo game248 = new GameInfo(R.string.game_name_17, "248", R.drawable.th17, 60, "16", 1000);
    private GameInfo hexagons = new GameInfo(R.string.game_name_3, "Hexagons", R.drawable.th3, 80, "2");
    private GameInfo imageVortex = new GameInfo(R.string.game_name_7, "Image Vortex", R.drawable.th7, 45, "6");
    private GameInfo likePrevious = new GameInfo(R.string.game_name_16, "Like Previous?", R.drawable.th16, 30, "15");
    private GameInfo memoryGrid = new GameInfo(R.string.game_name_1, "Memory Grid", R.drawable.th1, 80, AppEventsConstants.EVENT_PARAM_VALUE_NO);
    private GameInfo mirrors = new GameInfo(R.string.game_name_22, "Laser", R.drawable.preview_laser, 30, "21", 300);
    private GameInfo moreLess = new GameInfo(R.string.game_name_12, "More, Less", R.drawable.th11, 60, "11", 500);
    private GameInfo oneAndOnly = new GameInfo(R.string.game_name_10, "One and Only", R.drawable.th10, 45, "9", Game1MemoryGridActivity.START_ANIMATION_DURATION);
    private GameInfo paperPlanes = new GameInfo(R.string.game_name_15, "Paper Planes", R.drawable.th15, 30, "14");
    private GameInfo pyramids = new GameInfo(R.string.game_name_18, "Pyramids", R.drawable.th18, 60, "17", 0, true);
    private GameInfo rotatingGrid = new GameInfo(R.string.game_name_2, "Rotating Grid", R.drawable.th2, 90, AppEventsConstants.EVENT_PARAM_VALUE_YES);
    private GameInfo sortTheDigits = new GameInfo(R.string.game_name_14, "Sort the Digits", R.drawable.th14, 45, "13", SettingsJsonConstants.ANALYTICS_FLUSH_INTERVAL_SECS_DEFAULT);
    private GameInfo symmetry = new GameInfo(R.string.game_name_21, "Symmetry", R.drawable.th21, 30, "20");
    private GameInfo table = new GameInfo(R.string.game_name_23, "Schultz Tables", R.drawable.preview_table, 30, "22", 300);
    private GameInfo whoSNew = new GameInfo(R.string.game_name_4, "Who's New?", R.drawable.th4, 60, "3");
    private Map<GameInfo, Class> gameActivities = new HashMap<GameInfo, Class>() {
        {
            put(Games.this.memoryGrid, Game1MemoryGridActivity.class);
            put(Games.this.rotatingGrid, Game2RotatingGridActivity.class);
            put(Games.this.hexagons, Game3HexagonsActivity.class);
            put(Games.this.whoSNew, Game4WhoNewActivity.class);
            put(Games.this.countAll, Game5CountAllActivity.class);
            put(Games.this.followThePath, Game6FollowThePathActivity.class);
            put(Games.this.imageVortex, Game7ImageVortexActivity.class);
            put(Games.this.catchThem, Game8CatchThemActivity.class);
            put(Games.this.findThePicture, Game9FindThePictureActivity.class);
            put(Games.this.oneAndOnly, Game10OneAndOnlyActivity.class);
            put(Games.this.correctly, Game11CorrectlyActivity.class);
            put(Games.this.moreLess, Game12MoreLessActivity.class);
            put(Games.this.allTheSame, Game13AllTheSameActivity.class);
            put(Games.this.sortTheDigits, Game14SortTheDigitsActivity.class);
            put(Games.this.paperPlanes, Game15PaperPlanesActivity.class);
            put(Games.this.likePrevious, Game16LikePreviousActivity.class);
            put(Games.this.game248, Game17_248Activity.class);
            put(Games.this.pyramids, Game18PyramidsActivity.class);
            put(Games.this.findAll, Game19FindAllActivity.class);
            put(Games.this.colors, Game20ColorsActivity.class);
            put(Games.this.symmetry, Game21SymmetryActivity.class);
            put(Games.this.mirrors, Game22MirrorsActivity.class);
            put(Games.this.table, Game23TableActivity.class);
        }
    };

    private Map<GameInfo, CategoryInfo> gameCategories = new HashMap<GameInfo, CategoryInfo>() {
        {
            put(Games.this.memoryGrid, Games.this.categoryMemory);
            put(Games.this.rotatingGrid, Games.this.categoryAttention);
            put(Games.this.hexagons, Games.this.categoryMemory);
            put(Games.this.whoSNew, Games.this.categoryMemory);
            put(Games.this.countAll, Games.this.categoryImagination);
            put(Games.this.followThePath, Games.this.categoryMemory);
            put(Games.this.imageVortex, Games.this.categoryMemory);
            put(Games.this.catchThem, Games.this.categoryAttention);
            put(Games.this.findThePicture, Games.this.categoryMemory);
            put(Games.this.oneAndOnly, Games.this.categorySpeed);
            put(Games.this.correctly, Games.this.categoryProblemSolving);
            put(Games.this.moreLess, Games.this.categoryProblemSolving);
            put(Games.this.allTheSame, Games.this.categorySpeed);
            put(Games.this.sortTheDigits, Games.this.categorySpeed);
            put(Games.this.paperPlanes, Games.this.categoryFlexibility);
            put(Games.this.likePrevious, Games.this.categoryFlexibility);
            put(Games.this.game248, Games.this.categoryProblemSolving);
            put(Games.this.pyramids, Games.this.categoryImagination);
            put(Games.this.findAll, Games.this.categorySpeed);
            put(Games.this.colors, Games.this.categoryFlexibility);
            put(Games.this.symmetry, Games.this.categoryProblemSolving);
            put(Games.this.mirrors, Games.this.categoryProblemSolving);
            put(Games.this.table, Games.this.categorySpeed);
        }
    };

    private List<GameInfo> sprintGames = new ArrayList<GameInfo>() {
        {
            add(Games.this.memoryGrid);
            add(Games.this.hexagons);
            add(Games.this.whoSNew);
            add(Games.this.followThePath);
            add(Games.this.imageVortex);
            add(Games.this.findThePicture);
            add(Games.this.rotatingGrid);
            add(Games.this.catchThem);
            add(Games.this.table);
            add(Games.this.oneAndOnly);
            add(Games.this.allTheSame);
            add(Games.this.sortTheDigits);
            add(Games.this.findAll);
            add(Games.this.paperPlanes);
            add(Games.this.likePrevious);
            add(Games.this.colors);
            add(Games.this.mirrors);
            add(Games.this.symmetry);
            add(Games.this.correctly);
            add(Games.this.moreLess);
            add(Games.this.game248);
            add(Games.this.countAll);
            add(Games.this.pyramids);
        }
    };

    private List<Integer> trophyStrings = new ArrayList<Integer>() {
        {
            add(Integer.valueOf(R.string.won_glass));
            add(Integer.valueOf(R.string.won_bronze));
            add(Integer.valueOf(R.string.won_silver));
            add(Integer.valueOf(R.string.won_gold));
        }
    };






    private Map<CategoryInfo, List<Integer>> trophyImages = new HashMap<CategoryInfo, List<Integer>>() {
        {
            put(Games.this.categoryMemory, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.cup_glass_1), Integer.valueOf(R.drawable.cup_bronze_1), Integer.valueOf(R.drawable.cup_silver_1), Integer.valueOf(R.drawable.cup_gold_1)}));
            put(Games.this.categoryAttention, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.cup_glass_2), Integer.valueOf(R.drawable.cup_bronze_2), Integer.valueOf(R.drawable.cup_silver_2), Integer.valueOf(R.drawable.cup_gold_2)}));
            put(Games.this.categorySpeed, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.cup_glass_3), Integer.valueOf(R.drawable.cup_bronze_3), Integer.valueOf(R.drawable.cup_silver_3), Integer.valueOf(R.drawable.cup_gold_3)}));
            put(Games.this.categoryImagination, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.cup_glass_4), Integer.valueOf(R.drawable.cup_bronze_4), Integer.valueOf(R.drawable.cup_silver_4), Integer.valueOf(R.drawable.cup_gold_4)}));
            put(Games.this.categoryProblemSolving, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.cup_glass_5), Integer.valueOf(R.drawable.cup_bronze_5), Integer.valueOf(R.drawable.cup_silver_5), Integer.valueOf(R.drawable.cup_gold_5)}));
            put(Games.this.categoryFlexibility, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.cup_glass_6), Integer.valueOf(R.drawable.cup_bronze_6), Integer.valueOf(R.drawable.cup_silver_6), Integer.valueOf(R.drawable.cup_gold_6)}));
        }
    };

    private Map<GameInfo, List<Integer>> trophyLevels = new HashMap<GameInfo, List<Integer>>() {
        {
            put(Games.this.memoryGrid, Arrays.asList(new Integer[]{Integer.valueOf(9), Integer.valueOf(13), Integer.valueOf(15), Integer.valueOf(18)}));
            put(Games.this.rotatingGrid, Arrays.asList(new Integer[]{Integer.valueOf(6), Integer.valueOf(8), Integer.valueOf(10), Integer.valueOf(15)}));
            put(Games.this.hexagons, Arrays.asList(new Integer[]{Integer.valueOf(8), Integer.valueOf(11), Integer.valueOf(13), Integer.valueOf(17)}));
            put(Games.this.whoSNew, Arrays.asList(new Integer[]{Integer.valueOf(10), Integer.valueOf(12), Integer.valueOf(16), Integer.valueOf(20)}));
            put(Games.this.countAll, Arrays.asList(new Integer[]{Integer.valueOf(8), Integer.valueOf(11), Integer.valueOf(13), Integer.valueOf(18)}));
            put(Games.this.followThePath, Arrays.asList(new Integer[]{Integer.valueOf(6), Integer.valueOf(8), Integer.valueOf(10), Integer.valueOf(12)}));
            put(Games.this.imageVortex, Arrays.asList(new Integer[]{Integer.valueOf(9), Integer.valueOf(13), Integer.valueOf(15), Integer.valueOf(18)}));
            put(Games.this.catchThem, Arrays.asList(new Integer[]{Integer.valueOf(8), Integer.valueOf(11), Integer.valueOf(13), Integer.valueOf(15)}));
            put(Games.this.findThePicture, Arrays.asList(new Integer[]{Integer.valueOf(9), Integer.valueOf(13), Integer.valueOf(15), Integer.valueOf(18)}));
            put(Games.this.oneAndOnly, Arrays.asList(new Integer[]{Integer.valueOf(9), Integer.valueOf(13), Integer.valueOf(15), Integer.valueOf(17)}));
            put(Games.this.correctly, Arrays.asList(new Integer[]{Integer.valueOf(10), Integer.valueOf(12), Integer.valueOf(16), Integer.valueOf(20)}));
            put(Games.this.moreLess, Arrays.asList(new Integer[]{Integer.valueOf(10), Integer.valueOf(12), Integer.valueOf(16), Integer.valueOf(20)}));
            put(Games.this.allTheSame, Arrays.asList(new Integer[]{Integer.valueOf(10), Integer.valueOf(16), Integer.valueOf(18), Integer.valueOf(23)}));
            put(Games.this.sortTheDigits, Arrays.asList(new Integer[]{Integer.valueOf(10), Integer.valueOf(12), Integer.valueOf(16), Integer.valueOf(20)}));
            put(Games.this.paperPlanes, Arrays.asList(new Integer[]{Integer.valueOf(20), Integer.valueOf(30), Integer.valueOf(36), Integer.valueOf(42)}));
            put(Games.this.likePrevious, Arrays.asList(new Integer[]{Integer.valueOf(20), Integer.valueOf(30), Integer.valueOf(36), Integer.valueOf(42)}));
            put(Games.this.game248, Arrays.asList(new Integer[]{Integer.valueOf(25), Integer.valueOf(25), Integer.valueOf(55), Integer.valueOf(75)}));
            put(Games.this.pyramids, Arrays.asList(new Integer[]{Integer.valueOf(9), Integer.valueOf(11), Integer.valueOf(14), Integer.valueOf(15)}));
            put(Games.this.findAll, Arrays.asList(new Integer[]{Integer.valueOf(9), Integer.valueOf(13), Integer.valueOf(15), Integer.valueOf(17)}));
            put(Games.this.colors, Arrays.asList(new Integer[]{Integer.valueOf(20), Integer.valueOf(25), Integer.valueOf(32), Integer.valueOf(38)}));
            put(Games.this.symmetry, Arrays.asList(new Integer[]{Integer.valueOf(8), Integer.valueOf(12), Integer.valueOf(15), Integer.valueOf(18)}));
            put(Games.this.mirrors, Arrays.asList(new Integer[]{Integer.valueOf(8), Integer.valueOf(12), Integer.valueOf(17), Integer.valueOf(22)}));
            put(Games.this.table, Arrays.asList(new Integer[]{Integer.valueOf(24), Integer.valueOf(32), Integer.valueOf(40), Integer.valueOf(48)}));
        }
    };

    private Map<GameInfo, List<Integer>> tutorialImages = new HashMap<GameInfo, List<Integer>>() {
        {
            put(Games.this.memoryGrid, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game1_1), Integer.valueOf(R.drawable.game1_2), Integer.valueOf(R.drawable.game1_3)}));
            put(Games.this.rotatingGrid, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game2_1), Integer.valueOf(R.drawable.game2_2), Integer.valueOf(R.drawable.game2_3)}));
            put(Games.this.hexagons, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game3_1), Integer.valueOf(R.drawable.game3_2), Integer.valueOf(R.drawable.game3_3)}));
            put(Games.this.whoSNew, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game4_1), Integer.valueOf(R.drawable.game4_2), Integer.valueOf(R.drawable.game4_3)}));
            put(Games.this.countAll, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game5_1), Integer.valueOf(R.drawable.game5_2)}));
            put(Games.this.followThePath, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game6_1), Integer.valueOf(R.drawable.game6_2), Integer.valueOf(R.drawable.game6_3)}));
            put(Games.this.imageVortex, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game7_1), Integer.valueOf(R.drawable.game7_2), Integer.valueOf(R.drawable.game7_3)}));
            put(Games.this.catchThem, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game8_1), Integer.valueOf(R.drawable.game8_2), Integer.valueOf(R.drawable.game8_3), Integer.valueOf(R.drawable.game8_4)}));
            put(Games.this.findThePicture, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game9_1), Integer.valueOf(R.drawable.game9_2), Integer.valueOf(R.drawable.game9_3)}));
            put(Games.this.oneAndOnly, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game10_1), Integer.valueOf(R.drawable.game10_2)}));
            put(Games.this.correctly, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game11_1), Integer.valueOf(R.drawable.game11_2), Integer.valueOf(R.drawable.game11_3)}));
            put(Games.this.moreLess, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game12_1), Integer.valueOf(R.drawable.game12_2), Integer.valueOf(R.drawable.game12_3)}));
            put(Games.this.allTheSame, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game13_1), Integer.valueOf(R.drawable.game13_2), Integer.valueOf(R.drawable.game13_3)}));
            put(Games.this.sortTheDigits, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game14_1), Integer.valueOf(R.drawable.game14_2), Integer.valueOf(R.drawable.game14_3)}));
            put(Games.this.paperPlanes, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game15_1), Integer.valueOf(R.drawable.game15_2), Integer.valueOf(R.drawable.game15_3)}));
            put(Games.this.likePrevious, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game16_1), Integer.valueOf(R.drawable.game16_2), Integer.valueOf(R.drawable.game16_3)}));
            put(Games.this.game248, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game17_1), Integer.valueOf(R.drawable.game17_2), Integer.valueOf(R.drawable.game17_3)}));
            put(Games.this.pyramids, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game18_1), Integer.valueOf(R.drawable.game18_2)}));
            put(Games.this.findAll, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game19_1), Integer.valueOf(R.drawable.game19_2)}));
            put(Games.this.colors, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game20_1), Integer.valueOf(R.drawable.game20_2)}));
            put(Games.this.symmetry, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.game21_1), Integer.valueOf(R.drawable.game21_2)}));
            put(Games.this.mirrors, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.tutorial_laser_1), Integer.valueOf(R.drawable.tutorial_laser_2)}));
            put(Games.this.table, Arrays.asList(new Integer[]{Integer.valueOf(R.drawable.tutorial_table_1), Integer.valueOf(R.drawable.tutorial_table_2), Integer.valueOf(R.drawable.tutorial_table_3)}));
        }
    };

    private Map<GameInfo, List<Integer>> tutorialStrings = new HashMap<GameInfo, List<Integer>>() {
        {
            put(Games.this.memoryGrid, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game1_slide1), Integer.valueOf(R.string.tutorial_game1_slide2), Integer.valueOf(R.string.tutorial_game1_slide3)}));
            put(Games.this.rotatingGrid, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game2_slide1), Integer.valueOf(R.string.tutorial_game2_slide2), Integer.valueOf(R.string.tutorial_game2_slide3)}));
            put(Games.this.hexagons, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game3_slide1), Integer.valueOf(R.string.tutorial_game3_slide2), Integer.valueOf(R.string.tutorial_game3_slide3)}));
            put(Games.this.whoSNew, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game4_slide1), Integer.valueOf(R.string.tutorial_game4_slide2), Integer.valueOf(R.string.tutorial_game4_slide3)}));
            put(Games.this.countAll, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game5_slide1), Integer.valueOf(R.string.tutorial_game5_slide2)}));
            put(Games.this.followThePath, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game6_slide1), Integer.valueOf(R.string.tutorial_game6_slide2), Integer.valueOf(R.string.tutorial_game6_slide3)}));
            put(Games.this.imageVortex, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game7_slide1), Integer.valueOf(R.string.tutorial_game7_slide2), Integer.valueOf(R.string.tutorial_game7_slide3)}));
            put(Games.this.catchThem, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game8_slide1), Integer.valueOf(R.string.tutorial_game8_slide2), Integer.valueOf(R.string.tutorial_game8_slide3), Integer.valueOf(R.string.tutorial_game8_slide4)}));
            put(Games.this.findThePicture, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game9_slide1), Integer.valueOf(R.string.tutorial_game9_slide2), Integer.valueOf(R.string.tutorial_game9_slide3)}));
            put(Games.this.oneAndOnly, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game10_slide1), Integer.valueOf(R.string.tutorial_game10_slide2)}));
            put(Games.this.correctly, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game11_slide1), Integer.valueOf(R.string.tutorial_game11_slide2), Integer.valueOf(R.string.tutorial_game11_slide3)}));
            put(Games.this.moreLess, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game12_slide1), Integer.valueOf(R.string.tutorial_game12_slide2), Integer.valueOf(R.string.tutorial_game12_slide3)}));
            put(Games.this.allTheSame, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game13_slide1), Integer.valueOf(R.string.tutorial_game13_slide2), Integer.valueOf(R.string.tutorial_game13_slide3)}));
            put(Games.this.sortTheDigits, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game14_slide1), Integer.valueOf(R.string.tutorial_game14_slide2), Integer.valueOf(R.string.tutorial_game14_slide3)}));
            put(Games.this.paperPlanes, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game15_slide1), Integer.valueOf(R.string.tutorial_game15_slide2), Integer.valueOf(R.string.tutorial_game15_slide3)}));
            put(Games.this.likePrevious, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game16_slide1), Integer.valueOf(R.string.tutorial_game16_slide2), Integer.valueOf(R.string.tutorial_game16_slide3)}));
            put(Games.this.game248, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game17_slide1), Integer.valueOf(R.string.tutorial_game17_slide2), Integer.valueOf(R.string.tutorial_game17_slide3)}));
            put(Games.this.pyramids, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game18_slide1), Integer.valueOf(R.string.tutorial_game18_slide2)}));
            put(Games.this.findAll, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game19_slide1), Integer.valueOf(R.string.tutorial_game19_slide2)}));
            put(Games.this.colors, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game20_slide1), Integer.valueOf(R.string.tutorial_game20_slide2)}));
            put(Games.this.symmetry, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game21_slide1), Integer.valueOf(R.string.tutorial_game21_slide2)}));
            put(Games.this.mirrors, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game22_slide1), Integer.valueOf(R.string.tutorial_game22_slide2)}));
            put(Games.this.table, Arrays.asList(new Integer[]{Integer.valueOf(R.string.tutorial_game23_slide1), Integer.valueOf(R.string.tutorial_game23_slide2), Integer.valueOf(R.string.tutorial_game23_slide3)}));
        }
    };

    private Games() {
    }

    public static Games get() {
        return instance;
    }

    public GameInfo getGameByName(String gameName) {
        if (TextUtils.isEmpty(gameName)) {
            return null;
        }
        for (GameInfo gameInfo : this.sprintGames) {
            if (gameName.equalsIgnoreCase(gameInfo.getAnalyticsName())) {
                return gameInfo;
            }
        }
        return null;
    }

    public List<Integer> getTrophyLevels(GameInfo gameInfo) {
        return (List) this.trophyLevels.get(gameInfo);
    }

    public Class getGameActivity(GameInfo gameInfo) {
        return (Class) this.gameActivities.get(gameInfo);
    }

    public List<Integer> getTutorialStrings(GameInfo gameInfo) {
        return (List) this.tutorialStrings.get(gameInfo);
    }

    public List<Integer> getTutorialImages(GameInfo gameInfo) {
        return (List) this.tutorialImages.get(gameInfo);
    }

    public List<Integer> getTrophyImages(GameInfo gameInfo) {
        return (List) this.trophyImages.get(this.gameCategories.get(gameInfo));
    }

    public CategoryInfo getCategoryInfo(GameInfo gameInfo) {
        return (CategoryInfo) this.gameCategories.get(gameInfo);
    }

    public List<Integer> getTrophyStrings() {
        return this.trophyStrings;
    }

    public List<GameInfo> getSprintGames() {
        return this.sprintGames;
    }

    public GameInfo getGameBiId(String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        for (GameInfo gameInfo : this.sprintGames) {
            if (id.equals(gameInfo.getId())) {
                return gameInfo;
            }
        }
        return null;
    }

    public GameInfo suggestRandomGame(GameInfo previousGameInfo) {
        Random random = new Random();
        while (true) {
            GameInfo suggestedGame = (GameInfo) this.sprintGames.get(random.nextInt(this.sprintGames.size()));
            if (!suggestedGame.hasLock() && !suggestedGame.getId().equals(previousGameInfo.getId()) && !suggestedGame.getId().equals(SMART_PROMO_GAME_ID) && !suggestedGame.getId().equals(UNLOCK_ALL_GAMES_ID)) {
                return suggestedGame;
            }
        }
    }

    public void startChallengeGame(Activity activity, ChallengeJsonGame challengeJsonGame, GameInfo gameInfo) {
        if (MemoryApplicationModel.getInstance().getLocalDataManager().wasChallengeTutorialShowed(activity, gameInfo.getAnalyticsName())) {
            forceStartChallengeGame(activity, challengeJsonGame, gameInfo);
        } else {
            forceStartChallengeTutorial(activity, challengeJsonGame, true);
        }
    }

    public void forceStartChallengeTutorial(Activity activity, ChallengeJsonGame challengeJsonGame, boolean forceTutorial) {
        Intent intent = new Intent(activity, StartGameActivity.class);
        intent.putExtra(StartGameActivity.EXTRA_CHALLENGE, true);
        intent.putExtra(MainMenuActivity.EXTRA_CHALLENGE_GAME_INFO, challengeJsonGame);
        intent.putExtra(StartGameActivity.EXTRA_FORCE_TUTORIAL, forceTutorial);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.slide_to_left, R.anim.no_change);
    }

    private void forceStartChallengeGame(Context context, ChallengeJsonGame challengeJsonGame, GameInfo gameInfo) {
        Intent intent = new Intent();
        intent.putExtra(StartGameActivity.EXTRA_CHALLENGE, true);
        intent.putExtra(StartGameActivity.EXTRA_WORKOUT, false);
        intent.putExtra(MainMenuActivity.EXTRA_CHALLENGE_GAME_INFO, challengeJsonGame);
        intent.putExtra(MainMenuActivity.EXTRA_GAME_INFO, gameInfo);
        intent.setClass(context, get().getGameActivity(gameInfo));
        context.startActivity(intent);
    }
}
