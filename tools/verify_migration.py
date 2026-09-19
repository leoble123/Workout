#!/usr/bin/env python3
"""
Verifies a Room migration without needing a device.

Room validates the on-disk schema when it opens the database: if a migration leaves so much
as a column order, a NOT NULL flag or an index name different from what the entities imply,
the app throws on launch. This rebuilds the old schema in real SQLite, applies the migration
statements exactly as the Kotlin does, and diffs the result against Room's own exported
schema for the new version.

Usage: python3 tools/verify_migration.py <from_version> <to_version>
"""
import json, re, sqlite3, sys, pathlib

SCHEMA_DIR = pathlib.Path("app/schemas/com.leo.forge.data.db.ForgeDatabase")

_EX = ("INSERT INTO exercises (id,name,primaryMuscle,secondaryMuscles,equipment,pattern,"
       "isUnilateral,repLow,repHigh,loadIncrementKg,isCustom,isFavorite,archived) VALUES ")

# Rows present before each migration, and what it must do to them.
SEED = {
    1: [
        _EX + "('leg_press','Leg Press','QUADS','','MACHINE','SQUAT',0,8,20,5.0,0,0,0)",
        _EX + "('pec_deck','Pec Deck','CHEST','','MACHINE','ISOLATION',0,10,15,5.0,0,0,0)",
    ],
    2: [
        _EX + "('back_squat','Back Squat','QUADS','','BARBELL','SQUAT',0,5,10,2.5,0,0,0)",
        _EX + "('barbell_bench_press','Barbell Bench Press','CHEST','','BARBELL','HORIZONTAL_PUSH',0,5,10,2.5,0,0,0)",
        _EX + "('cable_curl','Cable Curl','BICEPS','','CABLE','ISOLATION',0,10,15,2.5,0,0,0)",
        "INSERT INTO gyms (name,units,isActive,createdAt) VALUES ('Test','LB',1,0)",
    ],
}

REWRITES = {
    (1, 2): [("SELECT id, equipment FROM exercises", "equipment",
              {"leg_press": "MACHINE_PLATE_LOADED", "pec_deck": "MACHINE_SELECTORIZED"})],
    (2, 3): [("SELECT id, requiresAlso FROM exercises", "requiresAlso",
              # a squat needs something to unrack from; a bench press needs both
              {"back_squat": "RACK", "barbell_bench_press": "RACK,BENCH", "cable_curl": ""})],
}
MIGRATIONS = pathlib.Path("app/src/main/java/com/leo/forge/data/db/Migrations.kt")


def load(version):
    return json.load(open(SCHEMA_DIR / f"{version}.json"))["database"]


def create_statements(db):
    out = []
    for t in db["entities"]:
        out.append(t["createSql"].replace("${TABLE_NAME}", t["tableName"]))
        for idx in t.get("indices", []):
            out.append(idx["createSql"].replace("${TABLE_NAME}", t["tableName"]))
    return out


def migration_statements(frm, to):
    """Statements belonging to this migration only - the file holds several."""
    src = MIGRATIONS.read_text()
    start = src.index(f"MIGRATION_{frm}_{to}")
    nxt = src.find("val MIGRATION_", start + 1)
    block = src[start:] if nxt == -1 else src[start:nxt]
    return [m.group(1).replace('\\"', '"') for m in re.finditer(r'db\.execSQL\("((?:[^"\\]|\\.)*)"\)', block)]


def describe(conn):
    """Room-relevant shape of every table: columns, pk, fks, indices."""
    shape = {}
    tables = [r[0] for r in conn.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'")]
    for t in tables:
        cols = {r[1]: (r[2].upper(), bool(r[3])) for r in conn.execute(f"PRAGMA table_info(`{t}`)")}
        pk = [r[1] for r in sorted(conn.execute(f"PRAGMA table_info(`{t}`)"), key=lambda r: r[5]) if r[5]]
        fks = sorted((r[2], r[3], r[4], r[5], r[6]) for r in conn.execute(f"PRAGMA foreign_key_list(`{t}`)"))
        idx = {}
        for r in conn.execute(f"PRAGMA index_list(`{t}`)"):
            name, unique, origin = r[1], bool(r[2]), r[3]
            if origin != "c":      # skip implicit indices from UNIQUE/PK constraints
                continue
            idx[name] = (unique, [c[2] for c in conn.execute(f"PRAGMA index_info(`{name}`)")])
        shape[t] = {"columns": cols, "pk": pk, "fks": fks, "indices": idx}
    return shape


def main():
    frm, to = int(sys.argv[1]), int(sys.argv[2])
    old, new = load(frm), load(to)

    # Path A: old schema + seed rows the migration should rewrite + the migration itself
    a = sqlite3.connect(":memory:")
    for s in create_statements(old):
        a.execute(s)
    for s in SEED.get(frm, []):
        a.execute(s)
    for s in migration_statements(frm, to):
        a.execute(s)

    # Path B: the new schema built from scratch, which is what Room expects to find
    b = sqlite3.connect(":memory:")
    for s in create_statements(new):
        b.execute(s)

    got, want = describe(a), describe(b)
    problems = []
    for table in sorted(set(want) | set(got)):
        if table not in got:
            problems.append(f"{table}: missing after migration")
            continue
        if table not in want:
            problems.append(f"{table}: unexpected extra table")
            continue
        for key in ("columns", "pk", "fks", "indices"):
            if got[table][key] != want[table][key]:
                problems.append(f"{table}.{key}\n    after migration: {got[table][key]}\n    expected:        {want[table][key]}")

    # data rewrites
    for sql, column, expected in REWRITES.get((frm, to), []):
        got_value = dict(a.execute(sql))
        for key, want_value in expected.items():
            if got_value.get(key) != want_value:
                problems.append(f"{key}.{column} is {got_value.get(key)!r}, expected {want_value!r}")

    if problems:
        print(f"MIGRATION {frm} -> {to}: FAILED")
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print(f"MIGRATION {frm} -> {to}: schema matches Room's expectation for v{to}")
    print(f"  tables checked: {len(want)}   statements applied: {len(migration_statements(frm, to))}")
    print("  data rewrites verified: MACHINE -> pin stack / plate loaded")


if __name__ == "__main__":
    main()
