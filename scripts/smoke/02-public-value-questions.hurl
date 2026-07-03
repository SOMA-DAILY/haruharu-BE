# 공개 API: 가치관 질문 목록 — ApiResponse 래퍼 형식까지 검증
GET {{base_url}}/api/v1/value-questions
HTTP 200
[Asserts]
jsonpath "$.isSuccess" == true
jsonpath "$.data" exists
