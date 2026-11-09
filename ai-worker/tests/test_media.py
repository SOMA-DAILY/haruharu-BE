"""영상 출처 제한과 프레임 추출(회전·실제 해상도·검은 화면)."""
import io
import subprocess

import pytest

from reply_ai import errors, media
from reply_ai.config import Settings


class FakeS3:
    def __init__(self, data):
        self.data, self.calls = data, []

    def get_object(self, Bucket, Key):  # noqa: N803 - boto3 이름
        self.calls.append((Bucket, Key))
        return {"Body": io.BytesIO(self.data), "ContentLength": len(self.data), "VersionId": "v7", "ETag": '"e"'}


def test_s3_url_of_configured_bucket_uses_boto3_key():
    s3 = FakeS3(b"video")
    fetcher = media.MediaFetcher(Settings(s3_bucket="haru-media", s3_region="ap-northeast-2"), s3_client=s3)
    data, source, version = fetcher.fetch("https://haru-media.s3.ap-northeast-2.amazonaws.com/rooms/1/a%20b.mp4")
    assert (data, source, version) == (b"video", "s3", "v7") and s3.calls == [("haru-media", "rooms/1/a b.mp4")]


def test_other_hosts_and_other_buckets_are_never_fetched():
    fetcher = media.MediaFetcher(Settings(s3_bucket="haru-media", media_allowed_prefixes=("http://192.168.0.5:8090/",)))
    for url in ("https://other-bucket.s3.ap-northeast-2.amazonaws.com/x.mp4", "https://evil.example.com/x.mp4",
                "http://192.168.0.5:8091/x.mp4", "file:///etc/passwd"):
        with pytest.raises(errors.JobFailure) as info:
            fetcher.fetch(url)
        assert info.value.code == errors.MEDIA_SOURCE_NOT_ALLOWED


def _video(tmp_path, args):
    out = tmp_path / "v.mp4"
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args, str(out)], check=True, timeout=60)
    return out.read_bytes()


def test_frames_keep_real_resolution_and_apply_rotation(tmp_path):
    plain = _video(tmp_path, ["-f", "lavfi", "-i", "testsrc=size=320x180:rate=30:duration=2", "-pix_fmt", "yuv420p"])
    (tmp_path / "plain.mp4").write_bytes(plain)
    rotated = tmp_path / "rot.mp4"
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-display_rotation", "90", "-i",
                    str(tmp_path / "plain.mp4"), "-c", "copy", str(rotated)], check=True, timeout=60)
    settings = Settings.from_env()
    a = media.extract_frames(settings, plain, "http", None)
    b = media.extract_frames(settings, rotated.read_bytes(), "http", None)
    assert [(f.width, f.height) for f in a.frames] == [(320, 180)] * 3  # 확대하지 않는다
    assert [(f.width, f.height) for f in b.frames] == [(180, 320)] * 3 and b.rotation == 90
    assert [f.position_percent for f in a.frames] == [10, 50, 90] and len({f.sha256 for f in a.frames}) == 3
    assert a.frames[0].timestamp_ms < a.frames[1].timestamp_ms < a.frames[2].timestamp_ms


def test_all_black_video_is_its_own_failure(tmp_path):
    black = _video(tmp_path, ["-f", "lavfi", "-i", "color=c=black:s=160x120:d=1", "-pix_fmt", "yuv420p"])
    with pytest.raises(errors.JobFailure) as info:
        media.extract_frames(Settings.from_env(), black, "http", None)
    assert info.value.code == errors.MEDIA_BLACK_FRAMES


def test_undecodable_bytes_fail_as_decode_error():
    with pytest.raises(errors.JobFailure) as info:
        media.extract_frames(Settings.from_env(), b"not a video", "http", None)
    assert info.value.code == errors.MEDIA_DECODE_FAILED
