#!/usr/bin/env python3
"""Synthetic JSON payload bytes only; NOT LLM token cost or gameplay evidence."""
import json


def size(value):
    return len(json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))


def fixtures(count):
    compact = {"chapter": "Create Basics", "quests": []}
    full = {"chapter": {"title": "Create Basics"}, "quests": []}
    for index in range(count):
        key = f"q{index}"
        c = {"id": key, "get": "create:andesite_alloy"}
        f = {
            "key": key,
            "title": "Obtain Andesite Alloy",
            "icon": "create:andesite_alloy",
            "tasks": [{"kind": "item", "item_id": "create:andesite_alloy", "count": 1}],
        }
        if index:
            c["after"] = f"q{index - 1}"
            f["depends_on"] = [f"q{index - 1}"]
        compact["quests"].append(c)
        full["quests"].append(f)
    return compact, full


def main():
    print("Synthetic Creator blueprint transport size; UTF-8 JSON bytes (NOT tokens)")
    print("Quests | Full blueprint | Compact blueprint | Full preview+apply | Compact preview+draft apply | Byte reduction")
    for count in (10, 30, 48):
        compact, full = fixtures(count)
        preview_full = {"blueprint": full}
        apply_full = {
            "blueprint": full, "preview_hash": "0" * 64,
            "expected_revision": "rev", "confirm": True,
        }
        preview_compact = {"blueprint": compact}
        apply_draft = {
            "draft_id": "0" * 36, "preview_hash": "0" * 64,
            "expected_revision": "rev", "confirm": True,
        }
        full_total = size(preview_full) + size(apply_full)
        compact_total = size(preview_compact) + size(apply_draft)
        reduction = (1 - compact_total / full_total) * 100
        print(f"{count:>6} | {size(full):>14} | {size(compact):>17} | "
              f"{full_total:>18} | {compact_total:>27} | {reduction:5.1f}%")
    print("Excludes MCP tool schema/context, tool results, registry queries, retries, "
          "prompt caching, model reasoning and runtime execution.")


if __name__ == "__main__":
    main()
