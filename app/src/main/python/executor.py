"""
Python executor for Rikkahub.
Safely executes Python code with stdout capture and error handling.
"""
import sys
import json
from io import StringIO


def execute(code: str, workdir: str) -> str:
    """Execute Python code, return JSON with stdout/result/error."""
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    sys.stdout = StringIO()
    sys.stderr = StringIO()

    result = None
    error = None

    try:
        import os
        os.chdir(workdir)
    except Exception:
        pass

    try:
        result = eval(code)
    except SyntaxError:
        try:
            exec(code)
            result = "Executed successfully (no return value)"
        except Exception as e:
            error = f"{type(e).__name__}: {e}"
    except Exception as e:
        error = f"{type(e).__name__}: {e}"
    finally:
        stdout = sys.stdout.getvalue()
        stderr = sys.stderr.getvalue()
        sys.stdout = old_stdout
        sys.stderr = old_stderr

    resp = {}
    if error:
        resp["error"] = error
    if stdout:
        resp["stdout"] = stdout
    if stderr:
        resp["stderr"] = stderr
    if result is not None and error is None:
        resp["result"] = str(result)
    if not resp:
        resp["result"] = "ok"
    return json.dumps(resp)
