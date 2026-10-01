#!/usr/bin/env python3
"""Third pass: generic/capture decompiler artifacts surfaced by JDK 17."""
import re
from pathlib import Path

JAVA = Path(r"D:\Code\workspace\android\memory-game\memory-game\src\main\java")

def patch(rel, subs, label):
    p = JAVA / rel
    t = p.read_text(encoding="utf-8")
    o = t
    for old, new in subs:
        if old not in t:
            print(f"  !! pattern missing in {label}: {old[:70]!r}")
            continue
        t = t.replace(old, new, 1)
    if t != o:
        p.write_text(t, encoding="utf-8")
        print(f"patched {label}")

# 1. Select(): add no-arg constructor to disambiguate the two varargs ctors
patch("com/activeandroid/query/Select.java", [
    ("    public Select(String... columns) {",
     "    public Select() {\n    }\n\n    public Select(String... columns) {"),
], "Select no-arg ctor")

# 2. Model(): explicit no-arg constructor (subclasses call new LocalGameStats() etc.)
patch("com/activeandroid/Model.java", [
    ("public abstract class Model {\n",
     "public abstract class Model {\n    public Model() {\n    }\n\n"),
    ("                        Class<? extends Model> entityType = fieldType;\n",
     "                        Class<? extends Model> entityType = (Class<? extends Model>) (Class) fieldType;\n"),
    ('entity = new Select().from(entityType).where(this.idName + "=?", Long.valueOf(entityId)).executeSingle();',
     'entity = new Select().from(entityType).where(this.idName + "=?", Long.valueOf(getId() == null ? 0L : getId().longValue())).executeSingle();'),
    ("                        value = Enum.valueOf(fieldType, cursor.getString(columnIndex));",
     "                        value = Enum.valueOf((Class<? extends Enum>) (Class) fieldType, cursor.getString(columnIndex));"),
], "Model")

# 3. From.java: nested JoinType
patch("com/activeandroid/query/From.java", [
    ("new Join(this, table, JoinType.LEFT)", "new Join(this, table, Join.JoinType.LEFT)"),
    ("new Join(this, table, JoinType.OUTER)", "new Join(this, table, Join.JoinType.OUTER)"),
    ("new Join(this, table, JoinType.INNER)", "new Join(this, table, Join.JoinType.INNER)"),
    ("new Join(this, table, JoinType.CROSS)", "new Join(this, table, Join.JoinType.CROSS)"),
], "From JoinType")

# 4. DatabaseHelper
patch("com/activeandroid/DatabaseHelper.java", [
    ('Log.w("Skipping invalidly named file: " + file, e);',
     'Log.w("Skipping invalidly named file.", e);'),
    ("Closeable buffer2 = new BufferedReader(reader2);",
     "BufferedReader buffer2 = new BufferedReader(reader2);"),
], "DatabaseHelper")

# 5. SqlParser
patch("com/activeandroid/util/SqlParser.java", [
    ("Closeable buffer = new BufferedInputStream(stream);",
     "InputStream buffer = new BufferedInputStream(stream);"),
], "SqlParser")

# 6. ReflectionUtils
patch("com/activeandroid/util/ReflectionUtils.java", [
    ("            if (ai.metaData != null) {\n                return ai.metaData.get(name);",
     "            if (ai.metaData != null) {\n                return (T) ai.metaData.get(name);"),
], "ReflectionUtils")

# 7. SQLiteUtils
patch("com/activeandroid/util/SQLiteUtils.java", [
    ("            return (Model) entities.get(0);",
     "            return (T) entities.get(0);"),
    ("                definition.append(Cache.getTableInfo(type).getTableName());",
     "                definition.append(Cache.getTableInfo((Class<? extends Model>) (Class) type).getTableName());"),
    ("                    entity = (Model) entityConstructor.newInstance(new Object[0]);",
     "                    entity = (T) entityConstructor.newInstance(new Object[0]);"),
], "SQLiteUtils")

# 8. ModelAdapter
patch("com/activeandroid/widget/ModelAdapter.java", [
    ("                add((Model) it.next());",
     "                add((T) it.next());"),
], "ModelAdapter")

# 9. ModelInfo
patch("com/activeandroid/ModelInfo.java", [
    ("                Class<? extends Model> modelClass = discoveredClass;",
     "                Class<? extends Model> modelClass = (Class<? extends Model>) (Class) discoveredClass;"),
], "ModelInfo")

# 10. LocalDataManager boolean switch artifact -> plain return
p = JAVA / "com/cube/memorygames/api/local/LocalDataManager.java"
t = p.read_text(encoding="utf-8")
boolean_switch = re.compile(r"switch \(z\) \{\n            case false:\n            case true:\n            case true:\n                return true;\n            default:\n                return false;\n        \}")
t2 = boolean_switch.sub("return true;", t)
if t2 != t:
    p.write_text(t2, encoding="utf-8")
    print("patched LocalDataManager boolean switch")
else:
    print("  !! boolean switch pattern not found")

# 11. ChallengeAdapter holder type
patch("com/cube/memorygames/ChallengeAdapter.java", [
    ("        ButtonBottomHolder holder;\n",
     "        ViewHolder holder;\n"),
], "ChallengeAdapter holder")

# 12. Gson capture conversions
patch("com/google/gson/Gson.java", [
    ("        TypeAdapter<?> cached = (TypeAdapter) this.typeTokenCache.get(type);\n        if (cached != null) {\n            return cached;\n        }",
     "        TypeAdapter<?> cached = (TypeAdapter) this.typeTokenCache.get(type);\n        if (cached != null) {\n            return (TypeAdapter<T>) cached;\n        }"),
    ("            T object = getAdapter(TypeToken.get(typeOfT)).read(reader);",
     "            T object = (T) getAdapter(TypeToken.get(typeOfT)).read(reader);"),
], "Gson casts")

# 13. ConstructorConstructor raw collections
p = JAVA / "com/google/gson/internal/ConstructorConstructor.java"
t = p.read_text(encoding="utf-8")
t2 = t.replace("                        return new TreeSet();",
               "                        return (T) new TreeSet();")
t2 = t2.replace("                    return new TreeSet();",
                "                    return (T) new TreeSet();")
if t2 != t:
    p.write_text(t2, encoding="utf-8")
    print("patched ConstructorConstructor TreeSet")
else:
    print("  !! TreeSet pattern not found")

print("done")
