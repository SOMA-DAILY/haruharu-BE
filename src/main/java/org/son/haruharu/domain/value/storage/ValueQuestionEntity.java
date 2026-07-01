package org.son.haruharu.domain.value.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.value.implement.ValueQuestion;

import java.util.UUID;

@Entity
@Table(name = "value_questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValueQuestionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 300)
    private String questionText;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean isActive;

    // 이 질문의 답변을 열쇠(room_members.point_sum)로 해금하는 비용(질문별 상이). 기본 10.
    @Column(nullable = false)
    private int unlockCost;

    public static ValueQuestionEntity from(ValueQuestion valueQuestion) {
        var entity = new ValueQuestionEntity();
        entity.id = valueQuestion.id();
        entity.questionText = valueQuestion.questionText();
        entity.category = valueQuestion.category();
        entity.displayOrder = valueQuestion.displayOrder();
        entity.isActive = valueQuestion.isActive();
        entity.unlockCost = valueQuestion.unlockCost();
        return entity;
    }

    public ValueQuestion toDomain() {
        return new ValueQuestion(id, questionText, category, displayOrder, isActive, unlockCost);
    }
}
