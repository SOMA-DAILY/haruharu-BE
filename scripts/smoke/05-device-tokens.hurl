# 디바이스 토큰 등록/해제: 보호 엔드포인트라 무토큰 401 (토큰 검증은 단위 테스트에서 수행)
POST {{base_url}}/api/v1/users/me/device-tokens
Content-Type: application/json
{"token":"smoke-test"}
HTTP 401

DELETE {{base_url}}/api/v1/users/me/device-tokens
Content-Type: application/json
{"token":"smoke-test"}
HTTP 401
