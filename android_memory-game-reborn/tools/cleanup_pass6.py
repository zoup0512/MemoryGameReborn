#!/usr/bin/env python3
"""Sixth pass: remaining shadowing/scoping/resource fixes."""
from pathlib import Path
import re

JAVA = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main\java")
RES = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main\res")

def patch(rel, subs, label):
    p = JAVA / rel
    t = p.read_text(encoding="utf-8")
    o = t
    for old, new in subs:
        if old in t:
            t = t.replace(old, new)
        else:
            print(f"  !! missing in {label}: {old[:70]!r}")
    if t != o:
        p.write_text(t, encoding="utf-8")
        print("patched", label)
    else:
        print("no change:", label)

# 1. TableInfo: add missing return
patch("com/activeandroid/TableInfo.java", [
    ("""            if (type.getSuperclass() != null) {
                return getIdField(type.getSuperclass());
            }
            return null;
        }
    }""",
     """            if (type.getSuperclass() != null) {
                return getIdField(type.getSuperclass());
            }
            return null;
        }
        return null;
    }"""),
], "TableInfo")

# 2. LocalDataManager: explicit witness + varargs + isWorkoutGameGenerated
patch("com/cube/memorygames/api/local/LocalDataManager.java", [
    ("for (LocalGameStats stat : (List<LocalGameStats>) new Select().from(LocalGameStats.class).execute()) {",
     "for (LocalGameStats stat : (List<LocalGameStats>) new Select().from(LocalGameStats.class).<LocalGameStats>execute()) {"),
    (".where(query, dates).execute();",
     ".where(query, (Object[]) dates).execute();"),
], "LocalDataManager")

# isWorkoutGameGenerated (lost in the truncation, still called by WorkoutAdapter)
p = JAVA / "com/cube/memorygames/api/local/LocalDataManager.java"
t = p.read_text(encoding="utf-8")
if "isWorkoutGameGenerated" not in t:
    t = t.replace("    public float getWorkoutRating() {",
"""    public boolean isWorkoutGameGenerated() {
        return new Select().from(LocalWorkoutGame.class).where("date = ?", getCurrentDate()).executeSingle() != null;
    }

    public float getWorkoutRating() {""", 1)
    p.write_text(t, encoding="utf-8")
    print("added isWorkoutGameGenerated")

# 3. DialogTryWorkout ctor param
patch("com/cube/memorygames/DialogTryWorkout.java", [
    ("    public DialogTryWorkout(Activity activity, OnClickListener confirmListener) {",
     "    public DialogTryWorkout(Activity activity, View.OnClickListener confirmListener) {"),
], "DialogTryWorkout ctor")

# 4. Game22Grid figure2 hoist
p = JAVA / "com/cube/memorygames/ui/Game22Grid.java"
t = p.read_text(encoding="utf-8")
i = t.rindex("figure2 = (Figure) ((List) this.cellFigure.get(point.x)).get(point.y);")
t = t[:i] + "        Figure figure2;\n" + t[i:]
p.write_text(t, encoding="utf-8")
print("patched Game22Grid figure2")

# 5. Game5: enclosing-instance + shadowing
p = JAVA / "com/cube/memorygames/games/Game5CountAllActivity.java"
t = p.read_text(encoding="utf-8")
t = t.replace("new OnClickListener(Game5CountAllActivity.this) {", "new View.OnClickListener() {")
p.write_text(t, encoding="utf-8")
print("patched Game5")

# 6. LastGameChartFragment: stats var + chart API v3
patch("com/cube/memorygames/LastGameChartFragment.java", [
    ("""        List<LocalGameSession> localGameSessions = MemoryApplicationModel.getInstance().getLocalDataManager().getLastGames(this.gameInfo.getId());
        this.maxValue.setText("" + ((LocalGameStats) MemoryApplicationModel.getInstance().getLocalDataManager().getLocalGameStats().get(this.gameInfo.getId())).maxLevel);
        this.averageValue.setText(String.format("%.1f", new Object[]{Float.valueOf(stats.avgLevel)}));""",
     """        List<LocalGameSession> localGameSessions = MemoryApplicationModel.getInstance().getLocalDataManager().getLastGames(this.gameInfo.getId());
        LocalGameStats stats = (LocalGameStats) MemoryApplicationModel.getInstance().getLocalDataManager().getLocalGameStats().get(this.gameInfo.getId());
        this.maxValue.setText("" + (stats != null ? stats.maxLevel : 0));
        this.averageValue.setText(String.format("%.1f", new Object[]{Float.valueOf(stats != null ? stats.avgLevel : 0.0f)}));"""),
    ("        this.chart.setDescriptionColor(0);",
     "        this.chart.getDescription().setTextColor(0);"),
], "LastGameChartFragment")

# 7. MenuAdapter cast
patch("com/cube/memorygames/ui/MenuAdapter.java", [
    ("            userViewHolder.userName.setTypeface(this.typeface);",
     "            ((UserViewHolder) userViewHolder).userName.setTypeface(this.typeface);"),
], "MenuAdapter cast")

# 8. WorkoutAdapter currentProgress
patch("com/cube/memorygames/ui/WorkoutAdapter.java", [
    ("getMaxScore(rating) - currentProgress)", "getMaxScore(rating) - getCurrentScore(rating))"),
], "WorkoutAdapter currentProgress")

# 9. TwoFoldGrid array order
p = JAVA / "com/cube/memorygames/ui/grids/TwoFoldGrid.java"
t = p.read_text(encoding="utf-8")
decl_colors = "    private static final int[] cellColors = new int[cellDrawables.length];\n"
decl_draw = re.search(r"    private static final int\[\] cellDrawables = new int\[\]\{[^\}]*\};\n", t).group(0)
t = t.replace(decl_colors, "", 1)
t = t.replace(decl_draw, decl_draw + decl_colors, 1)
p.write_text(t, encoding="utf-8")
print("patched TwoFoldGrid order")

# 10. picasso Utils: drop OkHttpLoaderCreator
p = JAVA / "com/squareup/picasso/Utils.java"
t = p.read_text(encoding="utf-8")
m = re.search(r"\n    private static class OkHttpLoaderCreator \{.*?\n    \}\n", t, flags=re.S)
if m:
    t = t.replace(m.group(0), "\n", 1)
    p.write_text(t, encoding="utf-8")
    print("patched picasso Utils")

# 11. viewpagerindicator FloatMath -> Math
p = JAVA / "com/viewpagerindicator/LinePageIndicator.java"
t = p.read_text(encoding="utf-8")
t = t.replace("FloatMath.ceil(", "Math.ceil(").replace("FloatMath.floor(", "Math.floor(")
p.write_text(t, encoding="utf-8")
print("patched LinePageIndicator FloatMath")

# 12. UnlockDialog / RateDialog ctor shadowing
p = JAVA / "com/cube/memorygames/MainMenuActivity.java"
t = p.read_text(encoding="utf-8")
t = t.replace("        public UnlockDialog(Context context, final OnClickListener onClickListener) {",
              "        public UnlockDialog(Context context, final View.OnClickListener onClickListener) {", 1)
p.write_text(t, encoding="utf-8")
print("patched UnlockDialog ctor")

patch("com/cube/memorygames/ui/RateDialog.java", [
    ("    public RateDialog(final Context context, int titleText, int buttonYesText, int buttonNoText, boolean showPlusButton, int lionImageRes, final OnClickListener listenerYes, final OnClickListener listenerNo) {",
     "    public RateDialog(final Context context, int titleText, int buttonYesText, int buttonNoText, boolean showPlusButton, int lionImageRes, final View.OnClickListener listenerYes, final View.OnClickListener listenerNo) {"),
], "RateDialog ctor")

# 13. bottombar missing resources
dimens = RES / "values/dimens.xml"
t = dimens.read_text(encoding="utf-8")
add = ""
for name, val in [("bb_default_elevation", "8.0dip"), ("bb_fake_shadow_height", "4.0dip")]:
    if f'"{name}"' not in t:
        add += f'    <dimen name="{name}">{val}</dimen>\n'
if add:
    t = t.replace("</resources>", add + "</resources>")
    dimens.write_text(t, encoding="utf-8")
    print("dimens added")

ids = RES / "values/ids.xml"
t = ids.read_text(encoding="utf-8")
add = ""
for name in ["bb_bottom_bar_appearance_id", "bb_bottom_bar_color_id"]:
    if f'"{name}"' not in t:
        add += f'    <item type="id" name="{name}" />\n'
if add:
    t = t.replace("</resources>", add + "</resources>")
    ids.write_text(t, encoding="utf-8")
    print("ids added")

bools = RES / "values/bools.xml"
t = bools.read_text(encoding="utf-8")
if '"bb_bottom_bar_is_portrait_mode"' not in t:
    t = t.replace("</resources>", '    <bool name="bb_bottom_bar_is_portrait_mode">true</bool>\n</resources>')
    bools.write_text(t, encoding="utf-8")
    print("bools added")

# selectableItemBackgroundBorderless -> appcompat
p = Path(r"D:\Code\workspace\android\memory-game\memory-game\tools\remap_ids.py")
t = p.read_text(encoding="utf-8")
if "selectableItemBackgroundBorderless" not in t:
    t = t.replace('    ("attr", "colorPrimary"): "android.support.v7.appcompat",',
                  '    ("attr", "colorPrimary"): "android.support.v7.appcompat",\n    ("attr", "selectableItemBackgroundBorderless"): "android.support.v7.appcompat",\n    ("attr", "selectableItemBackground"): "android.support.v7.appcompat",')
    p.write_text(t, encoding="utf-8")
    print("fallback added")

print("done")
