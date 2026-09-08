#!/usr/bin/env bash
# Postman olmadan Milestone 0 smoke: health + CRUD + 404/400.
# Servisler IntelliJ'den (veya docker --profile apps) ayakta olmalı.
set -euo pipefail

ORDER=http://localhost:8081
PAYMENT=http://localhost:8082
RESTAURANT=http://localhost:8083

json_field() {
  python3 -c "import json,sys; print(json.load(sys.stdin)${1})"
}

echo "== health =="
curl -sf "${ORDER}/actuator/health" | json_field "['status']"
curl -sf "${PAYMENT}/actuator/health" | json_field "['status']"
curl -sf "${RESTAURANT}/actuator/health" | json_field "['status']"

echo "== restaurant CRUD =="
REST=$(curl -sS -X POST "${RESTAURANT}/api/restaurants" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Adana Sofrası","address":"Kadıköy","cuisine":"Türk","open":true}')
echo "$REST"
RID=$(echo "$REST" | json_field "['id']")
curl -sf "${RESTAURANT}/api/restaurants/${RID}" >/dev/null
curl -sS -X PUT "${RESTAURANT}/api/restaurants/${RID}" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Adana Sofrası","address":"Kadıköy","cuisine":"Türk","open":false}' >/dev/null
curl -sS -o /dev/null -w "DELETE restaurant %{http_code}\n" -X DELETE "${RESTAURANT}/api/restaurants/${RID}"

echo "== order CRUD =="
ORD=$(curl -sS -X POST "${ORDER}/api/orders" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","restaurantId":"22222222-2222-2222-2222-222222222222","totalAmount":149.90}')
echo "$ORD"
OID=$(echo "$ORD" | json_field "['id']")
curl -sf "${ORDER}/api/orders/${OID}" >/dev/null
curl -sS -X PUT "${ORDER}/api/orders/${OID}" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","restaurantId":"22222222-2222-2222-2222-222222222222","totalAmount":149.90,"status":"CONFIRMED"}' >/dev/null

echo "== payment CRUD =="
PAY=$(curl -sS -X POST "${PAYMENT}/api/payments" \
  -H 'Content-Type: application/json' \
  -d "{\"orderId\":\"${OID}\",\"amount\":149.90}")
echo "$PAY"
PID=$(echo "$PAY" | json_field "['id']")
curl -sf "${PAYMENT}/api/payments/${PID}" >/dev/null
curl -sS -X PUT "${PAYMENT}/api/payments/${PID}" \
  -H 'Content-Type: application/json' \
  -d "{\"orderId\":\"${OID}\",\"amount\":149.90,\"status\":\"CAPTURED\"}" >/dev/null
curl -sS -o /dev/null -w "DELETE payment %{http_code}\n" -X DELETE "${PAYMENT}/api/payments/${PID}"
curl -sS -o /dev/null -w "DELETE order %{http_code}\n" -X DELETE "${ORDER}/api/orders/${OID}"

echo "== errors =="
curl -sS -o /dev/null -w "404 %{http_code}\n" "${ORDER}/api/orders/00000000-0000-0000-0000-000000000000"
curl -sS -o /dev/null -w "400 %{http_code}\n" -X POST "${ORDER}/api/orders" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"00000000-0000-0000-0000-000000000001","restaurantId":"00000000-0000-0000-0000-000000000002","totalAmount":-1}'

echo "OK"
