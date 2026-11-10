"""실제 microsoft/DialogRPT-depth 로 점수 재현(기본 제외: `uv run --extra depth pytest -m depth_live -s`).

필요: `--extra depth`, 고정 revision 가중치(첫 실행 때 내려받음, 이후 HF_HUB_OFFLINE=1).
한국어 예시의 영어 변환은 OPENAI_API_KEY(또는 REPLY_AI_DOTENV)가 있을 때만 실제 변환 단계를 부른다.
"""
import os

import pytest

from reply_ai import depth
from reply_ai.config import Settings, load_dotenv_secret

pytestmark = pytest.mark.depth_live


@pytest.fixture(scope="module")
def model():
    pytest.importorskip("torch")
    from reply_ai.depth_service import DepthModel
    return DepthModel()  # 시작할 때 공식 코드 출력 5개를 1e-3 안에서 재현하지 못하면 SystemExit


def test_reproduces_official_scores_and_model_card_example(model):
    print("\nself_check", model.info["self_check"])
    scores = model.score(["I love NLP!"], ["Can anyone recommend a nice review paper?", "Me too!"])
    print("model card example", [round(s, 4) for s in scores])
    assert round(scores[1], 3) == 0.032                 # 모델 카드 "Me too!" 0.032
    assert scores[0] > scores[1]                        # 카드 표의 순서(0.724 > 0.032)는 같다
    assert model.info["max_seq_len"] == 50 and model.info["model_revision"] == "0fa8a8770e267801a5779788ccfd921192ae7f40"


def test_input_token_record_for_short_and_long_context(model):
    short = model.inputs(["Pulling an all-nighter for an assignment due tomorrow, laptop and coffee on the desk."],
                         ["What assignment is it?"])[0]
    long_ = model.inputs(["A laptop on a desk shows an open document with a coffee cup next to it. " * 4],
                         ["What assignment is it?"])[0]
    print("\nshort", short, "\nlong", long_)
    assert not short["truncated"] and short["used_tokens"] == short["input_tokens"] <= 50
    assert long_["truncated"] and long_["used_tokens"] == 50 and not long_["reply_truncated"]


def test_left_truncation_keeps_reply_score_for_long_context(model):
    long_turn = "We talked about many unrelated things today. " * 20
    short, long_ = model.score(["I love NLP!"], ["Me too!"]), model.score([long_turn, "I love NLP!"], ["Me too!"])
    print("\nshort", short, "long", long_)
    assert all(0.0 < s < 1.0 for s in short + long_)


class _Ctx:
    """번역 단계만 부르는 최소 작업 문맥(비용 예약 없이 실제 provider 를 바로 부른다)."""

    def __init__(self, provider, settings):
        self.provider, self.settings, self.stages = provider, settings, {}

    def model_call(self, key, request):
        return self.provider.call(request)


def test_korean_example_translated_then_scored(model):
    if os.environ.get("REPLY_AI_DOTENV"):
        load_dotenv_secret(os.environ["REPLY_AI_DOTENV"])
    if not os.environ.get("OPENAI_API_KEY"):
        pytest.skip("OPENAI_API_KEY not set (영어 변환은 실제 provider 로만 확인)")
    from reply_ai.provider import OpenAIProvider
    settings = Settings.from_env()
    context = [{"turn_id": "chat:1", "text": "내일까지 과제 내야 해서 오늘 밤샐 듯 ㅠㅠ"},
               {"turn_id": "video", "text": "과제 마감 D-1\n책상 위 노트북 화면에 문서가 열려 있고 옆에 커피잔이 있다."}]
    cards = [{"card_id": "c1", "text": "무슨 과제예요?"}, {"card_id": "c2", "text": "마감 화이팅이에요!"},
             {"card_id": "c3", "text": "커피 몇 잔째예요? 저도 어제 밤샜어요 ㅋㅋ"}]
    translated = depth._translate(_Ctx(OpenAIProvider(settings), settings), "live-depth", context, cards)
    context_en = translated["context_en"]
    replies_en = [translated["candidates"][c["card_id"]] for c in cards]
    scores = model.score(context_en, replies_en)
    print("\ncompressed C:", context_en)
    for card, en, score, used in zip(cards, replies_en, scores, model.inputs(context_en, replies_en)):
        print(f"{card['text']} → {en!r} → {score:.4f} {used}")
    assert len(scores) == 3
