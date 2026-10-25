#!/usr/bin/env python3
"""Interactively add one static Argon2id account to the local secrets file."""

import argparse
import getpass
import json
import os
import re
import tempfile
from pathlib import Path

from argon2 import PasswordHasher, Type

IDENTIFIER = re.compile(r"[A-Za-z0-9:._/-]{1,128}\Z")
USERNAME = re.compile(r"[A-Za-z0-9._@-]{1,128}\Z")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accounts-file", type=Path, default=Path("secrets/accounts.json"))
    parser.add_argument("--audience", action="append", required=True, help="Allowed token audience; may be repeated")
    parser.add_argument("--scope", action="append", required=True, help="Allowed scope; may be repeated")
    args = parser.parse_args()

    for value in [*args.audience, *args.scope]:
        if not IDENTIFIER.fullmatch(value):
            parser.error(f"invalid audience/scope identifier: {value!r}")

    username = input("Username: ").strip()
    if not USERNAME.fullmatch(username):
        parser.error("username must be 1–128 characters from letters, digits, dot, underscore, @, or hyphen")
    password = getpass.getpass("Password: ")
    if not password or password != getpass.getpass("Confirm password: "):
        parser.error("passwords must be non-empty and match")

    path = args.accounts_file
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    try:
        document = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"accounts": []}
    except (OSError, json.JSONDecodeError) as exc:
        parser.error(f"cannot read accounts file: {exc}")
    if not isinstance(document, dict) or not isinstance(document.get("accounts"), list):
        parser.error("accounts file must be a JSON object with an accounts array")
    if any(entry.get("username") == username for entry in document["accounts"] if isinstance(entry, dict)):
        parser.error("that username already exists; do not overwrite accounts implicitly")

    hasher = PasswordHasher(time_cost=3, memory_cost=65536, parallelism=1, hash_len=32, salt_len=16, type=Type.ID)
    document["accounts"].append({
        "username": username,
        "passwordHash": hasher.hash(password),
        "audiences": sorted(set(args.audience)),
        "scopes": sorted(set(args.scope)),
    })
    payload = json.dumps(document, indent=2, ensure_ascii=False) + "\n"

    fd, temporary_name = tempfile.mkstemp(prefix="accounts-", dir=path.parent)
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            output.write(payload)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary_name, path)
        os.chmod(path, 0o444)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)
    print(f"Added account {username!r} with an Argon2id hash to {path}.")


if __name__ == "__main__":
    main()
