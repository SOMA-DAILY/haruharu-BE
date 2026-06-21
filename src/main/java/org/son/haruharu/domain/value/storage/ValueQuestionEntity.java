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

    public static ValueQuestionEntity from(ValueQuestion valueQuestion) {
        throw new UnsupportedOperationException("미구현");
    }

    public ValueQuestion toDomain() {
        throw new UnsupportedOperationException("미구현");
    }
}
