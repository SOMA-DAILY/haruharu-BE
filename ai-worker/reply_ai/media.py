"""영상 내려받기와 대표 프레임 추출.

- 출처: 설정한 S3 버킷 주소면 boto3 로, 아니면 REPLY_AI_MEDIA_ALLOWED_PREFIXES 로 시작하는 주소만 HTTP 로 받는다.
  그 밖의 주소는 받지 않는다(임의 사용자 URL 을 fetch 하지 않는다). 리다이렉트도 따라가지 않는다.
- 프레임: 재생 길이의 10·50·90% 를 회전 정보를 적용해 원래 해상도 그대로 뽑는다. 실제 디코드 시각·크기·해시를 남긴다.
"""
from __future__ import annotations

import json
import re
import subprocess
import tempfile
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlparse

import numpy as np

from . import errors
from .config import Settings
from .util import sha256_bytes

PERCENTAGES = (10, 50, 90)


@dataclass
class Frame:
    frame_id: str
    position_percent: int
    timestamp_ms: int
    width: int
    height: int
    sha256: str
    jpeg: bytes = field(repr=False)
    black: bool = False

    def meta(self) -> dict[str, Any]:
        return {"frame_id": self.frame_id, "position_percent": self.position_percent, "timestamp_ms": self.timestamp_ms,
                "width": self.width, "height": self.height, "sha256": self.sha256}


@dataclass
class Media:
    media_sha: str
    size_bytes: int
    source: str
    object_version: str | None
    duration_ms: int
    rotation: int
    source_width: int
    source_height: int
    tool_version: str
    frames: list[Frame]

    def record(self) -> dict[str, Any]:
        return {"media_sha": self.media_sha, "size_bytes": self.size_bytes, "source": self.source,
                "object_version": self.object_version, "duration_ms": self.duration_ms, "rotation": self.rotation,
                "source_width": self.source_width, "source_height": self.source_height, "tool_version": self.tool_version,
                "frames": [{**f.meta(), "black": f.black} for f in self.frames]}


class MediaFetcher:
    def __init__(self, settings: Settings, s3_client: Any | None = None, http_client: Any | None = None):
        self.settings = settings
        self._s3 = s3_client
        self._http = http_client

    def s3_prefixes(self) -> list[str]:
        bucket, region = self.settings.s3_bucket, self.settings.s3_region
        if not bucket:
            return []
        return [f"https://{bucket}.s3.{region}.amazonaws.com/", f"https://{bucket}.s3.amazonaws.com/"]

    def fetch(self, url: str) -> tuple[bytes, str, str | None]:
        """(바이트, 출처 종류, 객체 버전/ETag)."""
        for prefix in self.s3_prefixes():
            if url.startswith(prefix):
                return self._fetch_s3(unquote(url[len(prefix):].split("?", 1)[0]))
        parsed = urlparse(url)
        allowed = parsed.scheme in ("http", "https") and any(url.startswith(p) for p in self.settings.media_allowed_prefixes)
        if not allowed:
            raise errors.JobFailure(errors.MEDIA_SOURCE_NOT_ALLOWED, f"host {parsed.hostname or '?'}")
        return self._fetch_http(url)

    def _fetch_s3(self, key: str) -> tuple[bytes, str, str | None]:
        if self._s3 is None:
            import boto3
            self._s3 = boto3.client("s3", region_name=self.settings.s3_region)
        try:
            response = self._s3.get_object(Bucket=self.settings.s3_bucket, Key=key)
        except Exception as error:  # botocore 예외 종류가 많다. 키·URL 은 남기지 않는다.
            raise errors.JobFailure(errors.MEDIA_DOWNLOAD_FAILED, f"s3 {type(error).__name__}", retryable=True) from None
        if int(response.get("ContentLength") or 0) > self.settings.media_max_bytes:
            raise errors.JobFailure(errors.MEDIA_DOWNLOAD_FAILED, "too large")
        data = response["Body"].read(self.settings.media_max_bytes + 1)
        if len(data) > self.settings.media_max_bytes:
            raise errors.JobFailure(errors.MEDIA_DOWNLOAD_FAILED, "too large")
        return data, "s3", response.get("VersionId") or (response.get("ETag") or "").strip('"') or None

    def _fetch_http(self, url: str) -> tuple[bytes, str, str | None]:
        import httpx
        client = self._http or httpx.Client(timeout=30.0, follow_redirects=False)
        try:
            with client.stream("GET", url) as response:
                if response.status_code >= 500:
                    raise errors.JobFailure(errors.MEDIA_DOWNLOAD_FAILED, f"http {response.status_code}", retryable=True)
                if response.status_code != 200:
                    raise errors.JobFailure(errors.MEDIA_DOWNLOAD_FAILED, f"http {response.status_code}")
                chunks, total = [], 0
                for chunk in response.iter_bytes():
                    total += len(chunk)
                    if total > self.settings.media_max_bytes:
                        raise errors.JobFailure(errors.MEDIA_DOWNLOAD_FAILED, "too large")
                    chunks.append(chunk)
                return b"".join(chunks), "http", (response.headers.get("etag") or "").strip('"') or None
        except httpx.HTTPError as error:
            raise errors.JobFailure(errors.MEDIA_DOWNLOAD_FAILED, f"http {type(error).__name__}", retryable=True) from None
        finally:
            if self._http is None:
                client.close()


def _run(args: list[str], timeout: float = 60.0) -> subprocess.CompletedProcess:
    return subprocess.run(args, capture_output=True, timeout=timeout, check=False)


@lru_cache(maxsize=4)
def tool_version(ffmpeg: str) -> str:
    result = _run([ffmpeg, "-version"], timeout=10)
    first = (result.stdout.decode("utf-8", "replace").splitlines() or ["unknown"])[0]
    return first[:120]


def probe(ffprobe: str, path: Path) -> dict[str, Any]:
    result = _run([ffprobe, "-v", "error", "-print_format", "json", "-show_streams", "-show_format", str(path)], timeout=30)
    if result.returncode != 0:
        raise errors.JobFailure(errors.MEDIA_DECODE_FAILED, "ffprobe failed")
    data = json.loads(result.stdout or b"{}")
    video = next((s for s in data.get("streams", []) if s.get("codec_type") == "video"), None)
    if video is None:
        raise errors.JobFailure(errors.MEDIA_DECODE_FAILED, "no video stream")
    duration = video.get("duration") or data.get("format", {}).get("duration")
    try:
        duration_s = float(duration)
    except (TypeError, ValueError):
        raise errors.JobFailure(errors.MEDIA_DECODE_FAILED, "no duration") from None
    if duration_s <= 0:
        raise errors.JobFailure(errors.MEDIA_DECODE_FAILED, "zero duration")
    rotation = 0
    for side in video.get("side_data_list", []) or []:
        if "rotation" in side:
            rotation = int(round(float(side["rotation"])))
    if not rotation and (video.get("tags") or {}).get("rotate"):
        rotation = int((video.get("tags") or {})["rotate"])
    return {"duration_s": duration_s, "width": int(video.get("width") or 0), "height": int(video.get("height") or 0),
            "rotation": rotation % 360}


_PTS = re.compile(r"pts_time:\s*([0-9.]+)")


def _extract(ffmpeg: str, source: Path, seconds: float, out: Path) -> float | None:
    # -copyts: showinfo 가 원래 영상 기준 시각을 보고한다. 회전은 ffmpeg 기본 autorotate 로 적용된다.
    result = _run([ffmpeg, "-hide_banner", "-nostdin", "-loglevel", "info", "-copyts", "-ss", f"{seconds:.3f}",
                   "-i", str(source), "-frames:v", "1", "-vf", "showinfo", "-q:v", "2", "-y", str(out)], timeout=60)
    if result.returncode != 0 or not out.exists() or out.stat().st_size == 0:
        return None
    match = _PTS.search(result.stderr.decode("utf-8", "replace"))
    return float(match.group(1)) if match else seconds


def _gray_stats(ffmpeg: str, ffprobe: str, jpeg: Path) -> tuple[int, int, float, float]:
    result = _run([ffmpeg, "-hide_banner", "-loglevel", "error", "-i", str(jpeg), "-f", "rawvideo", "-pix_fmt", "gray", "-"],
                  timeout=30)
    if result.returncode != 0:
        raise errors.JobFailure(errors.MEDIA_DECODE_FAILED, "frame decode failed")
    probe_result = _run([ffprobe, "-v", "error", "-select_streams", "v:0", "-show_entries",
                         "stream=width,height", "-of", "json", str(jpeg)], timeout=30)
    info = json.loads(probe_result.stdout or b"{}").get("streams", [{}])[0]
    width, height = int(info.get("width") or 0), int(info.get("height") or 0)
    pixels = np.frombuffer(result.stdout, dtype=np.uint8)
    if width <= 0 or height <= 0 or pixels.size != width * height:
        raise errors.JobFailure(errors.MEDIA_DECODE_FAILED, "frame size unknown")
    return width, height, float(pixels.mean()), float(pixels.std())


def is_black(mean: float, std: float) -> bool:
    return mean < 10.0 and std < 6.0


def extract_frames(settings: Settings, data: bytes, source: str, object_version: str | None) -> Media:
    with tempfile.TemporaryDirectory(prefix="reply-ai-") as tmp:
        root = Path(tmp)
        video = root / "video"
        video.write_bytes(data)
        info = probe(settings.ffprobe, video)
        duration = info["duration_s"]
        frames: list[Frame] = []
        for pct in PERCENTAGES:
            target = duration * pct / 100
            out = root / f"frame-{pct}.jpg"
            actual = None
            # 짧은 영상 끝부분은 디코드되지 않을 수 있다. 조금씩 앞당겨 다시 시도하고 실제 시각을 남긴다.
            for back in (0.0, 0.1, 0.25, 0.5):
                actual = _extract(settings.ffmpeg, video, max(0.0, target - back), out)
                if actual is not None:
                    break
            if actual is None:
                raise errors.JobFailure(errors.MEDIA_DECODE_FAILED, f"frame {pct}% not decoded")
            jpeg = out.read_bytes()
            width, height, mean, std = _gray_stats(settings.ffmpeg, settings.ffprobe, out)
            frames.append(Frame(frame_id=f"frame-{pct}", position_percent=pct, timestamp_ms=int(round(actual * 1000)),
                                width=width, height=height, sha256=sha256_bytes(jpeg), jpeg=jpeg, black=is_black(mean, std)))
        if all(f.black for f in frames):
            raise errors.JobFailure(errors.MEDIA_BLACK_FRAMES, "all frames black")
        return Media(media_sha=sha256_bytes(data), size_bytes=len(data), source=source, object_version=object_version,
                     duration_ms=int(round(duration * 1000)), rotation=info["rotation"], source_width=info["width"],
                     source_height=info["height"], tool_version=tool_version(settings.ffmpeg), frames=frames)
