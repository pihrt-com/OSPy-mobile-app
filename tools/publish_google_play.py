#!/usr/bin/env python3
"""Upload an Android App Bundle and publish it through Google Play Edits API."""

import argparse
import json
import os
from pathlib import Path


ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
DEFAULT_PACKAGE = "com.pihrt.ospy.mobile"


def load_release_notes(directory):
    notes = []
    for path in sorted(Path(directory).glob("*.txt")):
        text = path.read_text(encoding="utf-8").strip()
        if not text:
            raise ValueError("Release note {} is empty".format(path))
        if len(text) > 500:
            raise ValueError(
                "Release note {} has {} characters; Google Play allows 500".format(
                    path, len(text)
                )
            )
        notes.append({"language": path.stem, "text": text})
    if not notes:
        raise ValueError("No release-note .txt files found in {}".format(directory))
    return notes


def release_body(name, version_code, status, notes):
    return {
        "releases": [
            {
                "name": name,
                "versionCodes": [str(version_code)],
                "status": status,
                "releaseNotes": notes,
            }
        ]
    }


def publish(service, package_name, bundle_upload, track, name, status, notes):
    edits = service.edits()
    edit_id = edits.insert(packageName=package_name, body={}).execute()["id"]
    committed = False
    try:
        bundle = edits.bundles().upload(
            packageName=package_name,
            editId=edit_id,
            media_body=bundle_upload,
        ).execute()
        version_code = int(bundle["versionCode"])
        edits.tracks().update(
            packageName=package_name,
            editId=edit_id,
            track=track,
            body=release_body(name, version_code, status, notes),
        ).execute()
        edits.validate(packageName=package_name, editId=edit_id).execute()
        edits.commit(
            packageName=package_name,
            editId=edit_id,
            changesInReviewBehavior="ERROR_IF_IN_REVIEW",
        ).execute()
        committed = True
        return version_code
    finally:
        if not committed:
            try:
                edits.delete(packageName=package_name, editId=edit_id).execute()
            except Exception:
                pass


def service_from_environment(variable):
    try:
        info = json.loads(os.environ[variable])
    except KeyError:
        raise RuntimeError("Required environment variable {} is missing".format(variable))
    except json.JSONDecodeError as error:
        raise RuntimeError("{} does not contain valid JSON".format(variable)) from error

    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    credentials = service_account.Credentials.from_service_account_info(
        info, scopes=[ANDROID_PUBLISHER_SCOPE]
    )
    return build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)


def arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--package", default=DEFAULT_PACKAGE)
    parser.add_argument("--track", default="production")
    parser.add_argument("--name", required=True)
    parser.add_argument(
        "--status",
        choices=("completed", "draft", "inProgress", "halted"),
        default="completed",
    )
    parser.add_argument("--notes", type=Path, required=True)
    parser.add_argument(
        "--credentials-env", default="GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"
    )
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args()


def main():
    args = arguments()
    if not args.bundle.is_file():
        raise SystemExit("AAB does not exist: {}".format(args.bundle))
    notes = load_release_notes(args.notes)
    if args.dry_run:
        print(json.dumps(release_body(args.name, 0, args.status, notes), ensure_ascii=False))
        return

    from googleapiclient.http import MediaFileUpload

    service = service_from_environment(args.credentials_env)
    upload = MediaFileUpload(
        str(args.bundle), mimetype="application/octet-stream", resumable=True
    )
    version_code = publish(
        service,
        args.package,
        upload,
        args.track,
        args.name,
        args.status,
        notes,
    )
    print("Published versionCode {} to {}".format(version_code, args.track))


if __name__ == "__main__":
    main()
