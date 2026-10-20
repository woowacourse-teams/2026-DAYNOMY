#!/usr/bin/env python3
"""개발 부하테스트 전용. 실제 검색 없이 검증 가능한 가상 응답을 반환한다."""

import argparse
import hashlib
import json
import math
import time
import uuid
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def analysis_output(body):
    # 최초 요청·보정·새 소식 확인 모두 첫 사용자 메시지에 대상 종목이 있다.
    content = next(item["content"] for item in body["input"] if item["role"] == "user")
    assets = json.loads(content)["assets"]
    day = (datetime.now(timezone.utc) - timedelta(days=1)).date().isoformat()
    impacts = {}
    citations = []
    for asset in assets:
        name = asset["assetName"]
        identifier = hashlib.sha256(name.encode()).hexdigest()[:20]
        url = "https://loadtest.invalid/announcements/" + identifier
        impacts[name] = {
            "direction": "NEUTRAL",
            "impactLevel": "LOW",
            "issueSummary": name + "의 부하테스트용 가상 자료를 확인했어요.",
            "expectedReaction": "부하테스트용 가상 분석 결과예요.",
            "outlook": "실제 투자 판단에 사용할 수 없는 가상 결과예요.",
            "reason": "부하테스트용으로 생성한 근거예요.",
            "sourceUrls": [url],
            "evidence": [{
                "url": url,
                "publishedOn": day,
                "occurredOn": day,
                "excerpt": "부하테스트용 가상 자료예요.",
                "factQuote": "This is synthetic evidence for load testing.",
            }],
        }
        citations.append({"type": "url_citation", "title": "부하테스트용 가상 자료", "url": url})
    schema = body["text"]["format"]["schema"]["properties"]["impacts"]
    if schema["type"] == "array":
        values = [{"assetName": name, **impact} for name, impact in impacts.items()]
    else:
        values = impacts
    return {"impacts": values}, citations


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        # Authorization·Cookie·요청 본문을 로그에 기록하지 않는다.
        pass

    def send_json(self, status, body):
        raw = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def read_body(self):
        limit = 1048576
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = bytearray()
            while True:
                size = int(self.rfile.readline(128).split(b";", 1)[0], 16)
                if size == 0:
                    if self.rfile.readline(128) != b"\r\n":
                        raise ValueError("unsupported_trailers")
                    return bytes(chunks)
                if size < 0 or len(chunks) + size > limit:
                    raise ValueError("body_too_large")
                chunk = self.rfile.read(size)
                if len(chunk) != size or self.rfile.read(2) != b"\r\n":
                    raise ValueError("invalid_chunk")
                chunks.extend(chunk)
        size = int(self.headers.get("Content-Length", "0"))
        if not 0 < size <= limit:
            raise ValueError("invalid_body_size")
        return self.rfile.read(size)

    def do_GET(self):
        if self.path not in ("/health", "/v1/models"):
            self.send_json(404, {"error": "not_found"})
            return
        self.send_json(200, {
            "fake_openai": True,
            "analysis_delay_seconds": self.server.analysis_delay,
            "summary_delay_seconds": self.server.summary_delay,
        })

    def do_POST(self):
        if self.path != "/v1/responses":
            self.send_json(404, {"error": "not_found"})
            return
        try:
            self.connection.settimeout(15)
            body = json.loads(self.read_body())
            properties = body["text"]["format"]["schema"]["properties"]
            if "impacts" in properties:
                value, citations = analysis_output(body)
                delay = self.server.analysis_delay
                output = [{"type": "web_search_call", "status": "completed"}]
                operation = "analysis"
            elif "impact" in properties and "priority" in properties:
                value = {
                    "impact": "부하테스트를 위해 생성한 가상 분석이에요. 실제 투자 판단의 근거로 사용할 수 없어요.",
                    "priority": "실제 정보는 별도로 확인해야 해요.",
                }
                citations = []
                delay = self.server.summary_delay
                output = []
                operation = "summary"
            else:
                raise ValueError("unsupported_schema")
        except (ValueError, KeyError, TypeError, StopIteration):
            self.send_json(400, {"error": "unsupported_portfolio_request"})
            return
        time.sleep(delay)
        output.append({"type": "message", "role": "assistant", "content": [{
            "type": "output_text", "text": json.dumps(value, ensure_ascii=False),
            "annotations": citations,
        }]})
        try:
            self.send_json(200, {"id": "resp_fake_" + uuid.uuid4().hex,
                                 "status": "completed", "output": output})
            print(json.dumps({"operation": operation, "delay_seconds": delay}), flush=True)
        except (BrokenPipeError, ConnectionResetError):
            pass


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=9099)
    parser.add_argument("--analysis-delay", type=float, default=40)
    parser.add_argument("--summary-delay", type=float, default=5)
    args = parser.parse_args()
    if any(not math.isfinite(n) or n < 0 for n in (args.analysis_delay, args.summary_delay)):
        parser.error("지연 시간은 0 이상의 유한한 숫자여야 합니다.")
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    server.analysis_delay = args.analysis_delay
    server.summary_delay = args.summary_delay
    print("Fake OpenAI listening on 127.0.0.1:" + str(args.port), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
