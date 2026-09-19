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
    src = MIGRATIONS.read_text()
    block = src[src.index(f"MIGRATION_{frm}_{to}"):]
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

    # Path A: old schema + migration
    a = sqlite3.connect(":memory:")
    for s in create_statements(old):
        a.execute(s)
    # a row that the migration is supposed to rewrite
    a.execute("INSERT INTO exercises (id,name,primaryMuscle,secondaryMuscles,equipment,pattern,"
              "isUnilateral,repLow,repHigh,loadIncrementKg,isCustom,isFavorite,archived) "
              "VALUES ('leg_press','Leg Press','QUADS','','MACHINE','SQUAT',0,8,20,5.0,0,0,0)")
    a.execute("INSERT INTO exercises (id,name,primaryMuscle,secondaryMuscles,equipment,pattern,"
              "isUnilateral,repLow,repHigh,loadIncrementKg,isCustom,isFavorite,archived) "
              "VALUES ('pec_deck','Pec Deck','CHEST','','MACHINE','ISOLATION',0,10,15,5.0,0,0,0)")
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
    rows = dict(a.execute("SELECT id, equipment FROM exercises"))
    if rows.get("leg_press") != "MACHINE_PLATE_LOADED":
        problems.append(f"leg_press equipment is {rows.get('leg_press')}, expected MACHINE_PLATE_LOADED")
    if rows.get("pec_deck") != "MACHINE_SELECTORIZED":
        problems.append(f"pec_deck equipment is {rows.get('pec_deck')}, expected MACHINE_SELECTORIZED")

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
