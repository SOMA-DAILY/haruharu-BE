package org.son.haruharu.domain.mission.implement;
import java.util.List;
import org.son.haruharu.domain.mission.implement.exception.InvalidOperatorMissionContentException;
public record OperatorMissionTemplate(String templateId, int dayNumber, int position, String content, String category) {
    public static final List<OperatorMissionTemplate> DEFAULTS = List.of(
            new OperatorMissionTemplate("DAY1_1", 1, 1, "지금 있는 곳 보여주기\n방이나 카페에서 지금 머무는 공간 한쪽을 3초 동안 보여주세요.", "자기소개"),
            new OperatorMissionTemplate("DAY1_2", 1, 2, "나를 소개할 물건 하나 보여주기\n나를 잘 보여주는 물건 하나를 가까이 들고 3초 동안 보여주세요.", "자기소개"),
            new OperatorMissionTemplate("DAY1_3", 1, 3, "가방 속 나다운 물건 세 개\n가방에서 꺼낸 나다운 물건 세 개를 놓고 3초 동안 보여주세요.", "자기소개"),
            new OperatorMissionTemplate("DAY1_4", 1, 4, "오늘 옷의 포인트 보여주기\n오늘 입은 옷이나 신발에서 마음에 드는 부분을 3초 동안 보여주세요.", "자기소개"),
            new OperatorMissionTemplate("DAY1_5", 1, 5, "나만의 작은 취향 보여주기\n좋아하는 소품 하나를 골라 취향이 보이게 3초 동안 보여주세요.", "자기소개"),
            new OperatorMissionTemplate("DAY1_6", 1, 6, "책상 위 오늘 보여주기\n지금 책상 위의 물건들을 한 화면에 담아 3초 동안 보여주세요.", "자기소개"),
            new OperatorMissionTemplate("DAY1_7", 1, 7, "오늘 듣는 음악의 분위기 보여주기\n이어폰이나 스피커와 함께 음악을 듣는 순간을 3초 동안 보여주세요.", "자기소개"),
            new OperatorMissionTemplate("DAY2_1", 2, 1, "지금 먹고 있는 것 보여주기\n먹고 있는 음식이나 간식을 가까이 담아 3초 동안 보여주세요.", "일상"),
            new OperatorMissionTemplate("DAY2_2", 2, 2, "오늘 잠깐 쉬는 순간 보여주기\n잠깐 쉬고 있는 자리나 편안한 장면을 3초 동안 보여주세요.", "일상"),
            new OperatorMissionTemplate("DAY2_3", 2, 3, "오늘 마신 음료 보여주기\n오늘 마신 음료를 손에 들거나 테이블 위에 놓고 3초 동안 보여주세요.", "일상"),
            new OperatorMissionTemplate("DAY2_4", 2, 4, "오늘 걸었던 길 보여주기\n오늘 걸었던 길에서 마음에 든 구간을 3초 동안 보여주세요.", "일상"),
            new OperatorMissionTemplate("DAY2_5", 2, 5, "오늘 하늘 보여주기\n창밖이나 길에서 보이는 오늘의 하늘을 3초 동안 보여주세요.", "일상"),
            new OperatorMissionTemplate("DAY2_6", 2, 6, "오늘 눈에 들어온 색 보여주기\n오늘 눈에 들어온 색 하나를 골라 가까이 3초 동안 보여주세요.", "일상"),
            new OperatorMissionTemplate("DAY2_7", 2, 7, "오늘 발견한 귀여운 것 보여주기\n귀엽다고 느낀 물건이나 동물을 골라 3초 동안 보여주세요.", "일상"),
            new OperatorMissionTemplate("DAY3_1", 3, 1, "오늘의 기분을 표정으로 보여주기\n오늘 기분이 느껴지는 표정을 짓고 3초 동안 보여주세요.", "취향"),
            new OperatorMissionTemplate("DAY3_2", 3, 2, "내가 좋아하는 풍경 보여주기\n마음에 드는 풍경 한 장면을 골라 3초 동안 보여주세요.", "취향"),
            new OperatorMissionTemplate("DAY3_3", 3, 3, "내 취미의 한 장면 보여주기\n즐기는 취미의 도구나 하고 있는 장면을 3초 동안 보여주세요.", "취향"),
            new OperatorMissionTemplate("DAY3_4", 3, 4, "요즘 함께하는 책 보여주기\n읽고 있는 책이나 잡지의 한 장면을 3초 동안 보여주세요.", "취향"),
            new OperatorMissionTemplate("DAY3_5", 3, 5, "오늘 나를 웃게 한 작은 것 보여주기\n오늘 보고 웃었던 작은 물건이나 장면을 3초 동안 보여주세요.", "취향"),
            new OperatorMissionTemplate("DAY3_6", 3, 6, "하루를 마무리하는 루틴 보여주기\n하루를 마무리할 때 하는 작은 습관을 3초 동안 보여주세요.", "취향"));
    public static OperatorMissionTemplate get(String id) { return DEFAULTS.stream().filter(item -> item.templateId().equals(id)).findFirst().orElseThrow(InvalidOperatorMissionContentException::new); }
    public String title() { return "오늘의 미션"; }
}
