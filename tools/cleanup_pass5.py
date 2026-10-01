#!/usr/bin/env python3
"""Fifth pass: inherited-scope shadowing, hoisted locals, misc artifacts."""
from pathlib import Path
import re

JAVA = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main\java")

def patch(rel, subs, label):
    p = JAVA / rel
    t = p.read_text(encoding="utf-8")
    o = t
    for old, new in subs:
        if old in t:
            t = t.replace(old, new)
        else:
            print(f"  !! missing in {label}: {old[:60]!r}")
    if t != o:
        p.write_text(t, encoding="utf-8")
        print("patched", label)
    else:
        print("no change:", label)

# 1. Game1
patch("com/cube/memorygames/games/Game1MemoryGridActivity.java", [
    ("        OnClickListener gameSuggestionClickListener = new OnClickListener() {",
     "        View.OnClickListener gameSuggestionClickListener = new View.OnClickListener() {"),
    ("        OnClickListener mainMenuClickListener = new OnClickListener() {",
     "        View.OnClickListener mainMenuClickListener = new View.OnClickListener() {"),
    ("        OnClickListener retryClickListener = new OnClickListener() {",
     "        View.OnClickListener retryClickListener = new View.OnClickListener() {"),
    ('            this.selectedAdType = savedInstanceState.getInt("selectedAdType", 10);\n            this.onlineProgressDelay = savedInstanceState.getInt(ONLINE_PROGRESS_DELAY, 0);\n',
     ""),
    ('            this.selectedAdType = 10;\n            this.onlineScore = 0;',
     '            this.onlineScore = 0;'),
    ("setOnShowListener(new OnShowListener(Game1MemoryGridActivity.this) {",
     "setOnShowListener(new DialogInterface.OnShowListener() {"),
    ("setOnDismissListener(new OnDismissListener(Game1MemoryGridActivity.this) {",
     "setOnDismissListener(new DialogInterface.OnDismissListener() {"),
], "Game1")

# 2. DialogTryWorkout
patch("com/cube/memorygames/DialogTryWorkout.java", [
    ("    private OnClickListener mConfirmListener;",
     "    private View.OnClickListener mConfirmListener;"),
], "DialogTryWorkout")

# 3. PauseDialog
patch("com/cube/memorygames/ui/PauseDialog.java", [
    ("    private OnClickListener exitClickListener;",
     "    private View.OnClickListener exitClickListener;"),
    ("    private OnClickListener replayClickListener;",
     "    private View.OnClickListener replayClickListener;"),
    ("    public PauseDialog(Activity activity, GameInfo gameInfo, OnClickListener exitClickListener, OnClickListener replayClickListener, OnDismissListener onDismissListener) {",
     "    public PauseDialog(Activity activity, GameInfo gameInfo, View.OnClickListener exitClickListener, View.OnClickListener replayClickListener, OnDismissListener onDismissListener) {"),
], "PauseDialog")

# 4. Element
patch("com/cube/memorygames/ui/Element.java", [
    ("public class Element {\n", "public class Element {\n    public Element() {\n    }\n\n"),
], "Element no-arg")

# 5. CardAnimator
patch("com/wenchao/cardstack/CardAnimator.java", [
    ("        it = this.mCardCollection.iterator();\n        while (it.hasNext()) {\n            v = (View) it.next();",
     "        View v;\n        it = this.mCardCollection.iterator();\n        while (it.hasNext()) {\n            v = (View) it.next();"),
], "CardAnimator v")

p = JAVA / "com/mopub/mobileads/resource/DrawableConstants.java"
t = p.read_text(encoding="utf-8")
if "TEXT_SIZE_SP = 20" not in t.split("class CloseButton")[1].split("}")[0]:
    t = t.replace("    public static class CloseButton {\n        public static final int STROKE_WIDTH = 3;",
                  "    public static class CloseButton {\n        public static final int STROKE_WIDTH = 3;\n        public static final int TEXT_SIZE_SP = 20;")
    p.write_text(t, encoding="utf-8")
    print("patched CloseButton TEXT_SIZE_SP")

# 6. Model
patch("com/activeandroid/Model.java", [
    ("                        field2 = null;\n", ""),
], "Model field2")

# 7. DatabaseHelper
patch("com/activeandroid/DatabaseHelper.java", [
    ("import java.io.InputStream;", "import java.io.InputStream;\nimport java.io.Reader;"),
], "DatabaseHelper Reader")

# 8. grid casts
for rel, cls in [("com/cube/memorygames/games/Game11CorrectlyActivity.java", "Game11Grid"),
                 ("com/cube/memorygames/games/Game12MoreLessActivity.java", "Game12Grid"),
                 ("com/cube/memorygames/games/Game16LikePreviousActivity.java", "Game16Grid"),
                 ("com/cube/memorygames/games/Game20ColorsActivity.java", "Game20Grid")]:
    patch(rel, [("= this.grid;", f"= ({cls}) this.grid;")], f"cast {cls}")

# 9. Game10
patch("com/cube/memorygames/games/Game10OneAndOnlyActivity.java", [
    ("        for (i = 0; i < groups.length; i++) {\n            for (j = 0; j < groups[i]; j++) {",
     "        int j;\n        for (i = 0; i < groups.length; i++) {\n            for (j = 0; j < groups[i]; j++) {"),
], "Game10 j")

# 10. Game14
patch("com/cube/memorygames/games/Game14SortTheDigitsActivity.java", [
    ("        while (i < result.size()) {\n            if (colorNumber == colors.size()) {",
     "        ElementGame14 elementGame14;\n        while (i < result.size()) {\n            if (colorNumber == colors.size()) {"),
], "Game14 hoist")

# 11. LocalDataManager
p = JAVA / "com/cube/memorygames/api/local/LocalDataManager.java"
t = p.read_text(encoding="utf-8")
old = """        if (localWorkoutGame != null) {
            List<DbLevelInfo> gameIds = (List) gson.fromJson(localWorkoutGame.games, new TypeToken<List<DbLevelInfo>>() {
            }.getType());
        } else {
            List<GameInfo> gameInfoList = new ArrayList(Games.get().getSprintGames());
            gameInfoList.remove(Games.get().getGameBiId(Games.SMART_PROMO_GAME_ID));
            gameInfoList.remove(Games.get().getGameBiId(Games.UNLOCK_ALL_GAMES_ID));
            Collections.shuffle(gameInfoList);
            gameInfoList = gameInfoList.subList(0, 4);
            Object gameIds2 = new ArrayList();
            for (GameInfo gameInfo : gameInfoList) {
                gameIds2.add(new DbLevelInfo(gameInfo.getId(), 0));
            }
            localWorkoutGame = new LocalWorkoutGame();
            localWorkoutGame.date = date;
            localWorkoutGame.games = gson.toJson(gameIds2);
            localWorkoutGame.save();
        }
        List<LevelInfo> result = new ArrayList();
        for (DbLevelInfo gameData : gameIds) {"""
new = """        List<DbLevelInfo> gameIds;
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
        for (DbLevelInfo gameData : gameIds) {"""
if old in t:
    t = t.replace(old, new, 1)
    print("patched getWorkoutGames")
else:
    print("  !! getWorkoutGames block missing")

old2 = """        float rating = 0.0f;
        for (GameInfo gameInfo : Games.get().getSprintGames()) {
            gameResults = (List) totalResults.get(gameInfo.getId());"""
if old2 in t:
    idx = t.index(old2)
    head = t[:idx + len("        float rating = 0.0f;\n        for (GameInfo gameInfo : Games.get().getSprintGames()) {\n")]
    tail = t[idx + len(head):]
    # find the end of this for-loop: replace gameResults with gameResults2 within the next ~1200 chars
    tail = tail.replace("gameResults", "gameResults2", 12)
    t = head + tail
    print("patched getWorkoutRating hoist")
else:
    print("  !! getWorkoutRating pattern missing")

t = t.replace("for (LocalGameStats stat : new Select().from(LocalGameStats.class).execute()) {",
              "for (LocalGameStats stat : (List<LocalGameStats>) new Select().from(LocalGameStats.class).execute()) {", 1)
p.write_text(t, encoding="utf-8")

# 12. Game15Grid enum VALUES/SIZE + onDraw left/top hoist
p = JAVA / "com/cube/memorygames/ui/Game15Grid.java"
t = p.read_text(encoding="utf-8")
enum_fix = re.compile(
    r"        private static final int SIZE = 0;\n        private static final List<\w+> VALUES = null;\n\n        static \{\n            VALUES = Collections\.unmodifiableList\(Arrays\.asList\(values\(\)\)\);\n            SIZE = VALUES\.size\(\);\n        \}")
t, n = enum_fix.subn("", t)
print(f"Game15Grid enum blocks removed: {n}")
t = t.replace("VALUES.get(GameRandom.nextInt(SIZE))", "values()[GameRandom.nextInt(values().length)]")
t = t.replace("""        float width = (float) getWidth();
        float height = (float) getHeight();""",
              """        float width = (float) getWidth();
        float height = (float) getHeight();
        float left;
        float top;""", 1)
t = t.replace("            float left = (float) ((Integer) cloud.first).intValue();\n            float top = (float) ((Integer) cloud.second).intValue();",
              "            left = (float) ((Integer) cloud.first).intValue();\n            top = (float) ((Integer) cloud.second).intValue();", 1)
p.write_text(t, encoding="utf-8")
print("patched Game15Grid")

# 13. Game22Grid hoist mirror-loop locals
p = JAVA / "com/cube/memorygames/ui/Game22Grid.java"
t = p.read_text(encoding="utf-8")
t = t.replace("""        List<Figure> mirrorFigures = new ArrayList();
        for (i = 0; i < this.mirrors.size(); i++) {""",
              """        List<Figure> mirrorFigures = new ArrayList();
        float left;
        float top;
        float right;
        float bottom;
        for (i = 0; i < this.mirrors.size(); i++) {""", 1)
p.write_text(t, encoding="utf-8")
print("patched Game22Grid")

print("done")
