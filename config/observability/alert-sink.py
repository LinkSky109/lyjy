#!/usr/bin/env python3
"""lhjypt 告警接收器（P4-006）：接收 Alertmanager webhook 并打印，供本地验证。
生产可将其替换为钉钉/企业微信转发网关（LHJYPT_ALERT_WEBHOOK 指向网关）。"""
import http.server
import json


class Handler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length)
        try:
            payload = json.loads(raw or b"{}")
        except ValueError:
            payload = {}
        alerts = payload.get("alerts", [])
        for alert in alerts:
            labels = alert.get("labels", {})
            print("ALERT status=%s name=%s severity=%s instance=%s" % (
                alert.get("status"), labels.get("alertname"),
                labels.get("severity"), labels.get("instance")), flush=True)
        print("RECEIVED groupStatus=%s alerts=%d" % (payload.get("status"), len(alerts)), flush=True)
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    http.server.HTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
