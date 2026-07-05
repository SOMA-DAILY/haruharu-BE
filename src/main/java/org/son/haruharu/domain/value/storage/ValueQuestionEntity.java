package org.son.haruharu.domain.value.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.value.implement.ValueQuestion;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "value_questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValueQuestionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String questionText;

    // 기존 행 마이그레이션 전 NULL 허용 (prod 반영 시 값 채운 뒤 NOT NULL 전환)
    // 네이밍 전략이 optionA를 optiona로 변환해 snake_case 컬럼명을 명시한다
    @Column(name = "option_a", length = 100)
    private String optionA;

    @Column(name = "option_b", length = 100)
    private String optionB;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean isActive;

    public static ValueQuestionEntity from(ValueQuestion valueQuestion) {
        var entity = new ValueQuestionEntity();
        entity.id = valueQuestion.id();
        entity.questionText = valueQuestion.questionText();
        entity.optionA = valueQuestion.optionA();
        entity.optionB = valueQuestion.optionB();
        entity.category = valueQuestion.category();
        entity.displayOrder = valueQuestion.displayOrder();
        entity.isActive = valueQuestion.isActive();
        return entity;
    }

    public ValueQuestion toDomain() {
        return new ValueQuestion(id, questionText, optionA, optionB, category, displayOrder, isActive);
    }
}
