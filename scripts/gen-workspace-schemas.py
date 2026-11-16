#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Membuat ulang tools-workspace/src/main/resources/workspace-tools.json dari vscode_mcp.py upstream.

Pemakaian:  python3 scripts/gen-workspace-schemas.py path/ke/vscode_mcp.py
Skrip ini memuat modul Python upstream (tanpa menjalankan server) dan mengambil daftar TOOLS.
"""
import importlib.util
import json
import pathlib
import sys

if len(sys.argv) != 2:
    sys.exit("pemakaian: gen-workspace-schemas.py <path/vscode_mcp.py>")

source = pathlib.Path(sys.argv[1])
spec = importlib.util.spec_from_file_location("vscode_mcp_upstream", source)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

tools = module.TOOLS
assert len(tools) == 28, f"diharapkan 28 tool, ditemukan {len(tools)}"
target = pathlib.Path(__file__).resolve().parent.parent / "tools-workspace/src/main/resources/workspace-tools.json"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(tools, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(f"ditulis {len(tools)} tool ke {target}")
