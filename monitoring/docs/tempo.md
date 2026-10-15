# 개발 환경 Tempo 분산 추적

개발 백엔드의 HTTP 요청 trace를 모니터링 EC2의 Tempo에 전송하고 Grafana에서 조회한다.
운영 프로필은 trace 전송을 비활성화한다.

## 네트워크

- Tempo OTLP/HTTP `4318/TCP`: 개발 백엔드 EC2 보안 그룹에서만 허용한다.
- Tempo HTTP API `3200/TCP`: Tempo 설정에서 `127.0.0.1`에만 바인딩한다. Grafana가 같은 EC2에서 접근한다.
- OTLP/gRPC `4317/TCP`은 이 설정에서 사용하지 않는다.

## 개발 백엔드 설정

개발 백엔드 EC2에서 Tempo 주소를 사설 IP로 지정한다. 주소 끝에 `/v1/traces`를 포함한다.
Tempo가 수신할 수 없는 경우에도 백엔드가 기동되도록 `TEMPO_OTEL_ENABLED`는 기본값이
`false`이며, 개발 서버의 `daynomy-dev` systemd 서비스에서만 활성화한다.

서비스 drop-in을 연다.

```bash
sudo systemctl edit daynomy-dev
```

아래 내용을 입력하고 `<MONITORING_PRIVATE_IP>`를 모니터링 EC2의 사설 IP로 바꾼다.

```ini
[Service]
Environment="TEMPO_OTEL_ENABLED=true"
Environment="TEMPO_OTLP_ENDPOINT=http://<MONITORING_PRIVATE_IP>:4318/v1/traces"
```

적용 후 백엔드 서비스를 재시작한다.

```bash
sudo systemctl daemon-reload
sudo systemctl restart daynomy-dev
sudo systemctl status daynomy-dev --no-pager
```

개발 서버에서 API 요청을 발생시키면 trace가 Tempo로 전송된다. 기본 샘플링은 개발 요청의
100%이며 `TEMPO_OTEL_SAMPLING_PROBABILITY`로 조정할 수 있다.

## Grafana 데이터소스

Grafana의 **Connections → Data sources → Add data source**에서 `Tempo`를 선택한다.
URL은 `http://127.0.0.1:3200`으로 입력하고 저장한 뒤, **Explore**에서 Tempo 데이터소스를
선택해 서비스 `daynomy`의 trace를 조회한다. Grafana와 Tempo가 같은 EC2에 있고 Tempo의
HTTP API가 `127.0.0.1:3200`에 바인딩된 구성이다.

현재 구성은 Spring MVC 서버 요청 trace와 계측된 Spring 관측 정보를 수집한다. DB 쿼리의
개별 span이나 운영 trace 전송은 이 설정에 포함하지 않는다.
