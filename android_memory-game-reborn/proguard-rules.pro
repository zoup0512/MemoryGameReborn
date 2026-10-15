# R8 rules for the restored offline build + online PK mode.
# The codebase is a decompiled restore with heavy reflection:
#   - ActiveAndroid reflects over @Table/@Column models (TableInfo walks fields)
#   - Gson field-name reflection for workout/challenge models and the
#     OnlineMessages protocol DTOs (no @SerializedName annotations anywhere)
#   - ButterKnife 8.5.1 generated $$ViewBinder classes are looked up reflectively
# Rules start broad (keep whole vendored packages) and should be tightened
# only after a full regression pass; silent runtime breakage in a restored
# codebase is far more expensive than over-keeping.

# ---- Attributes needed for reflective correctness ----
# Single-line form on purpose: generic signatures must survive regardless of
# how the AGP default proguard file phrases its own requests. Gson reads
# generic signatures (the TypeToken sites in LocalDataManager) and
# @SerializedName; ActiveAndroid reads @Table/@Column annotations. Without
# Signature, TypeToken.getType() throws "Missing type parameter." at runtime.
-keepattributes Signature,*Annotation*,AnnotationDefault,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Reflection-sensitive models ----
# ActiveAndroid tables and Gson DTOs: field names are part of the DB schema
# (column names) and the wire/storage format (JSON keys).
-keep class com.cube.memorygames.api.local.model.** { *; }
-keep class com.cube.memorygames.api.local.challenge.** { *; }
-keep class com.cube.memorygames.api.local.workout.** { *; }
-keep class com.cube.memorygames.model.** { *; }
-keep class com.cube.memorygames.api.online.OnlineMessages { *; }
-keep class com.cube.memorygames.api.online.OnlineMessages$* { *; }
# REST and encrypted identity Gson models (including private fields/constructors).
-keep class com.cube.memorygames.api.online.AccountManager$Identity { *; }
-keep class com.cube.memorygames.api.online.AccountManager$UserProfile { *; }
-keep class com.cube.memorygames.api.online.AccountManager$AuthResponse { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- Reflective R.drawable access ----
# MemoryApplicationModel.getAllCellDrawableIds() collects R.drawable fields
# whose NAME contains "icg_" via reflection; R8 renaming them away empties the
# list and Game5CountAllActivity.startLevel() crashed mid-match on 2026-10-15
# (release-only; debug builds keep field names). Keep the names.
-keepclassmembers class com.memory.brain.training.games.R$drawable {
    public static <fields>;
}

# ---- ActiveAndroid (vendored source; no AAR rules travel with it) ----
-keep class com.activeandroid.** { *; }
-dontwarn com.activeandroid.**

# ---- ButterKnife 8.5.1 ----
-keep class **$$ViewBinder { *; }
-keep class **$$ViewBinder$InnerUnbinder { *; }
-keepclasseswithmembernames class * {
    @butterknife.* <fields>;
}
-keepclasseswithmembernames class * {
    @butterknife.* <methods>;
}
# Generated binders are resolved by the reflective suffix
# "<target class name>_ViewBinding"; the suffix mapping must stay intact.
-keep class **_ViewBinding { *; }

# ---- Gson 2.7 (ships no consumer rules; the 2.10+ rules applied manually) ----
# LocalDataManager creates TypeToken<List<DbLevelInfo>> anonymous subclasses
# and reads the superclass generic signature at runtime. Stripped signatures
# or R8 class merging breaks getType() with "Missing type parameter.".
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# ---- Vendored view libraries (decompiled sources, reflect little but keep
#      their public API so XML/Calligraphy inflation is unaffected) ----
-keep class com.squareup.picasso.** { *; }
-dontwarn com.squareup.picasso.**
-keep class com.github.florent37.viewanimator.** { *; }
-keep class com.github.mikephil.charting.** { *; }
-keep class com.roughike.bottombar.** { *; }
-keep class com.viewpagerindicator.** { *; }
-keep class com.wenchao.cardstack.** { *; }
-keep class uk.co.chrisjenx.calligraphy.** { *; }
-keep class de.hdodenhof.circleimageview.** { *; }
-keep class com.yalantis.ucrop.** { *; }
-dontwarn com.yalantis.ucrop.**

# ---- Java-WebSocket (online PK transport) ----
-dontwarn org.java_websocket.**
-keep class org.java_websocket.** { *; }

# ---- Calligraphy reads styleable attrs from XML ----
-keepclassmembers class * {
    public static ** parseXml(android.util.AttributeSet);
}

# ---- Obfuscation: flat packages + non-sequential dictionary ----
-repackageclasses ''
-allowaccessmodification
-obfuscationdictionary proguard-dict.txt
-classobfuscationdictionary proguard-dict.txt
-packageobfuscationdictionary proguard-dict.txt

# ---- Strip verbose logs from release (keep w/e for crash forensics) ----
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
