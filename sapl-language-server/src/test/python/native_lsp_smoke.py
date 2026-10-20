#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Exercise a language server executable over stdio using only the standard library."""

import json
from pathlib import Path
from queue import Empty, Queue
import subprocess
import sys
import tempfile
from threading import Thread
import time


class LanguageServer:
    def __init__(self, command):
        self.command = command
        self.messages = Queue()
        self.request_id = 0

    def __enter__(self):
        self.stderr = tempfile.TemporaryFile()
        self.process = subprocess.Popen(
            self.command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=self.stderr
        )
        self.reader = Thread(target=self.read_messages, daemon=True)
        self.reader.start()
        return self

    def __exit__(self, exception_type, exception, traceback):
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=5)
        self.reader.join(timeout=5)
        self.process.stdin.close()
        self.process.stdout.close()
        self.stderr.seek(0)
        stderr = self.stderr.read().decode("utf-8", errors="replace")
        self.stderr.close()
        if exception_type:
            print(stderr, file=sys.stderr)
        else:
            for error in ("was never registered", "ClassNotFoundException", "MissingReflectionRegistrationError"):
                assert error not in stderr, stderr

    def read_messages(self):
        try:
            while True:
                headers = {}
                while True:
                    line = self.process.stdout.readline()
                    if not line:
                        raise EOFError("Language server closed stdout")
                    if line == b"\r\n":
                        break
                    name, value = line.decode("ascii").split(":", 1)
                    headers[name.lower()] = value.strip()
                body = self.process.stdout.read(int(headers["content-length"]))
                self.messages.put(json.loads(body))
        except Exception as error:
            self.messages.put(error)

    def send(self, message):
        body = json.dumps({"jsonrpc": "2.0", **message}).encode("utf-8")
        self.process.stdin.write(f"Content-Length: {len(body)}\r\n\r\n".encode("ascii") + body)
        self.process.stdin.flush()

    def notify(self, method, params):
        self.send({"method": method, "params": params})

    def request(self, method, params):
        self.request_id += 1
        self.send({"id": self.request_id, "method": method, "params": params})
        deadline = time.monotonic() + 30
        while True:
            try:
                message = self.messages.get(timeout=max(0, deadline - time.monotonic()))
            except Empty as error:
                raise TimeoutError(f"Timed out waiting for {method}") from error
            if isinstance(message, Exception):
                raise message
            if "method" in message:
                if "id" in message:
                    result = None
                    if message["method"] == "workspace/configuration":
                        result = [None] * len(message["params"]["items"])
                    self.send({"id": message["id"], "result": result})
                continue
            assert message.get("id") == self.request_id, message
            assert "error" not in message, f"{method}: {message}"
            assert "result" in message, message
            return message["result"]


def check_language_features(server):
    uri = "file:///native-smoke/t2.sapl"
    text = 'policy "test"\npermit\n  action == "read";\n  time.dayOfWeek(<time.now>) == "MONDAY";\n'
    server.notify("textDocument/didOpen", {
        "textDocument": {"uri": uri, "languageId": "sapl", "version": 1, "text": text}
    })
    for line, character, expected in ((1, 2, None), (3, 7, "dayOfWeek"), (3, 23, "time.now")):
        result = server.request("textDocument/hover", {
            "textDocument": {"uri": uri}, "position": {"line": line, "character": character}
        })
        if expected:
            assert result is not None and expected in json.dumps(result), result

    server.notify("textDocument/didChange", {
        "textDocument": {"uri": uri, "version": 2},
        "contentChanges": [{"text": 'policy "test"\npermit\n  time.\n'}]
    })
    result = server.request("textDocument/completion", {
        "textDocument": {"uri": uri}, "position": {"line": 2, "character": 7}
    })
    items = result if isinstance(result, list) else result["items"]
    assert items, "Completion returned no items"
    assert any("dayOfWeek" in item["label"] for item in items), items


def main(command):
    assert command, "Usage: native_lsp_smoke.py <executable> [arguments...]"
    fixture = Path(__file__).resolve().parent.parent / "resources/native/neovim-0.12-capabilities.json"
    capabilities = (
        {"textDocument": {"inlineCompletion": {"dynamicRegistration": False}}},
        {"textDocument": {"codeLens": {"resolveSupport": {"properties": ["command"]}}}},
        json.loads(fixture.read_text(encoding="utf-8")),
    )
    for index, client_capabilities in enumerate(capabilities):
        with LanguageServer(command) as server:
            result = server.request("initialize", {
                "processId": None, "rootUri": None, "capabilities": client_capabilities
            })
            assert result["capabilities"]["hoverProvider"], result
            server.notify("initialized", {})
            if index == len(capabilities) - 1:
                check_language_features(server)
            server.request("shutdown", None)
            server.notify("exit", None)
            assert server.process.wait(timeout=10) == 0
    print("Native LSP smoke tests passed: initialize, hover, completion")


if __name__ == "__main__":
    main(sys.argv[1:])
