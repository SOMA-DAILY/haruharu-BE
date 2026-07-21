package org.son.haruharu.domain.chat.business;

import java.util.List;

/**
 * 데모 채팅 시드 대화 스크립트(#266). 앱스토어 심사 리젝 사유가 "pre-populated content(채팅)"였으므로,
 * 리뷰어가 채팅 화면에 들어오면 곧바로 자연스러운 대화가 보이도록 고정 스크립트를 시드한다.
 * <p>
 * 실제 유저 데이터가 아니라 심사용 더미이며, 개인정보·연락처·외부 링크를 포함하지 않는다.
 */
final class DemoChatScript {

    private DemoChatScript() {
    }

    /** 한 줄. fromPartner=true면 가상 상대가, false면 리뷰어(본인)가 보낸 것으로 시드한다. */
    record Line(boolean fromPartner, String content) {
    }

    static final List<Line> LINES = List.of(
            new Line(true, "안녕하세요! 매칭됐네요 ㅎㅎ 반가워요"),
            new Line(false, "안녕하세요~ 저도 반가워요!"),
            new Line(true, "3일 동안 영상 보면서 궁금한 게 많았어요"),
            new Line(false, "저도요 ㅋㅋ 특히 둘째 날 영상 인상 깊었어요"),
            new Line(true, "아 그거요? 그날 퇴근하고 바로 찍은 거였는데"),
            new Line(true, "표정 관리가 안 되더라고요 ㅋㅋㅋ"),
            new Line(false, "오히려 자연스러워서 좋았어요"),
            new Line(true, "다행이다 ㅎㅎ 혹시 평소에 취미 있으세요?"),
            new Line(false, "요즘은 러닝이요! 주말마다 한강 나가요"),
            new Line(true, "오 저도 걷는 거 좋아해요"),
            new Line(true, "한강 어느 쪽으로 가세요?"),
            new Line(false, "주로 뚝섬 쪽이요. 사람도 적당하고 좋더라고요"),
            new Line(true, "좋네요 저는 반포 쪽만 가봤는데"),
            new Line(false, "뚝섬도 한번 가보세요 추천이에요"),
            new Line(true, "그럼 다음에 같이 가보는 걸로 ㅎㅎ"),
            new Line(false, "좋아요! 날 풀리면 바로요"),
            new Line(true, "그때까지 카톡 말고 여기서 얘기해요"),
            new Line(false, "네 좋아요 ㅎㅎ"),
            new Line(true, "혹시 음식은 뭐 좋아하세요?"),
            new Line(false, "가리는 거 거의 없어요! 매운 것도 잘 먹고요"),
            new Line(true, "오 그럼 저랑 잘 맞겠는데요"),
            new Line(true, "제가 아는 곳 하나 있는데 나중에 같이 가요"),
            new Line(false, "좋아요, 기대할게요!")
    );
}
