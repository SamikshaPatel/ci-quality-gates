#!/usr/bin/env python3
"""Saves a screenshot of the provisioned dashboard, as proof of what it shows today."""
import sys
from playwright.sync_api import sync_playwright

grafana = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:3000"
out = sys.argv[2] if len(sys.argv) > 2 else "grafana-dashboard.png"
with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(viewport={"width": 1600, "height": 1300}, device_scale_factor=2)
    page.goto(f"{grafana}/d/ci-quality-gates?orgId=1&kiosk", wait_until="networkidle")
    page.wait_for_timeout(4000)   # let every panel finish its query and render
    page.screenshot(path=out, full_page=True)
    browser.close()
print(f"Saved {out}")
