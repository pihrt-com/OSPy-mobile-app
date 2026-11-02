import tempfile
import unittest
from pathlib import Path

from publish_google_play import load_release_notes, publish, release_body


class Request:
    def __init__(self, result=None, failure=None):
        self.result = result or {}
        self.failure = failure

    def execute(self):
        if self.failure:
            raise self.failure
        return self.result


class FakeEdits:
    def __init__(self, fail_commit=False):
        self.calls = []
        self.fail_commit = fail_commit

    def insert(self, **kwargs):
        self.calls.append(("insert", kwargs))
        return Request({"id": "edit-1"})

    def bundles(self):
        return self

    def upload(self, **kwargs):
        self.calls.append(("upload", kwargs))
        return Request({"versionCode": 45})

    def tracks(self):
        return self

    def update(self, **kwargs):
        self.calls.append(("update", kwargs))
        return Request()

    def validate(self, **kwargs):
        self.calls.append(("validate", kwargs))
        return Request()

    def commit(self, **kwargs):
        self.calls.append(("commit", kwargs))
        failure = RuntimeError("review already active") if self.fail_commit else None
        return Request(failure=failure)

    def delete(self, **kwargs):
        self.calls.append(("delete", kwargs))
        return Request()


class FakeService:
    def __init__(self, edits):
        self.value = edits

    def edits(self):
        return self.value


class PublishGooglePlayTest(unittest.TestCase):
    def test_loads_localized_notes(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "cs-CZ.txt").write_text("Novinky", encoding="utf-8")
            Path(directory, "en-US.txt").write_text("What's new", encoding="utf-8")
            self.assertEqual(
                [
                    {"language": "cs-CZ", "text": "Novinky"},
                    {"language": "en-US", "text": "What's new"},
                ],
                load_release_notes(directory),
            )

    def test_rejects_note_over_google_play_limit(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "en-US.txt").write_text("x" * 501, encoding="utf-8")
            with self.assertRaises(ValueError):
                load_release_notes(directory)

    def test_publishes_validated_bundle_without_cancelling_review(self):
        edits = FakeEdits()
        notes = [{"language": "en-US", "text": "Update"}]
        version_code = publish(
            FakeService(edits),
            "com.pihrt.ospy.mobile",
            object(),
            "production",
            "OSPy Mobile 0.3.37",
            "completed",
            notes,
        )
        self.assertEqual(45, version_code)
        self.assertEqual(
            ["insert", "upload", "update", "validate", "commit"],
            [name for name, unused in edits.calls],
        )
        commit = edits.calls[-1][1]
        self.assertEqual("ERROR_IF_IN_REVIEW", commit["changesInReviewBehavior"])
        update = edits.calls[2][1]
        self.assertEqual(
            release_body("OSPy Mobile 0.3.37", 45, "completed", notes),
            update["body"],
        )

    def test_discards_edit_after_failed_commit(self):
        edits = FakeEdits(fail_commit=True)
        with self.assertRaisesRegex(RuntimeError, "review already active"):
            publish(
                FakeService(edits),
                "com.pihrt.ospy.mobile",
                object(),
                "production",
                "OSPy Mobile 0.3.37",
                "completed",
                [{"language": "en-US", "text": "Update"}],
            )
        self.assertEqual("delete", edits.calls[-1][0])


if __name__ == "__main__":
    unittest.main()
