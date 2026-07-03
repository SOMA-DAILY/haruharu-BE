# 서버 + DB 헬스체크
GET {{base_url}}/actuator/health
HTTP 200
[Asserts]
jsonpath "$.status" == "UP"
