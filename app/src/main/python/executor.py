"""
Python executor for Rikkahub.
Executes Python code with stdout capture, matplotlib auto-save,
and result file detection.

Available built-in functions (call these from your code):
  query_knowledge_base(query, limit=10)         - Search knowledge base
  add_knowledge_entry(title, content)           - Add entry to knowledge base
  update_knowledge_entry(id, title, content)    - Update knowledge entry
  delete_knowledge_entry(id)                    - Delete knowledge entry
  list_knowledge_entries(limit=20)               - List knowledge base entries
  list_conversations(limit=10)                   - List recent conversations
  get_conversation_messages(conv_id)             - Read conversation messages
  list_assistants()                              - List all assistants & their key settings
  get_assistant_settings(assistant_id)           - Read full assistant settings
  update_assistant_setting(id, key, value)       - Change any assistant setting
  get_setting(key)                               - Read global app setting
  update_setting(key, value)                     - Change global app setting
  get_app_info()                                 - App version & paths
"""

import sys
import json
import os
from io import StringIO
import traceback

# Bridge to Android services - set by Kotlin before execute() is called
_bridge = None

# ============================================================
# Bridge wrapper functions
# ============================================================

def query_knowledge_base(query, limit=10):
    """Search knowledge base. Returns matching entries."""
    if _bridge: return _bridge.queryKnowledgeBase(query, limit)
    return "Bridge not available"

def add_knowledge_entry(title, content, assistant_id=None):
    """Add an entry to the knowledge base."""
    if _bridge: return _bridge.addKnowledgeEntry(title, content, assistant_id)
    return "Bridge not available"

def list_knowledge_entries(limit=20):
    """List all knowledge base entries."""
    if _bridge: return _bridge.listKnowledgeEntries(limit)
    return "Bridge not available"

def list_conversations(limit=10):
    """List recent conversations."""
    if _bridge: return _bridge.listConversations(limit)
    return "Bridge not available"

def get_conversation_messages(conversation_id, limit=50):
    """Get messages from a conversation."""
    if _bridge: return _bridge.getConversationMessages(conversation_id, limit)
    return "Bridge not available"

def get_app_info():
    """Get app information."""
    if _bridge: return _bridge.getAppInfo()
    return "Bridge not available"

def list_assistants():
    """List all assistants with their settings."""
    if _bridge: return _bridge.listAssistants()
    return "Bridge not available"

def get_assistant_settings(assistant_id):
    """Get settings for a specific assistant."""
    if _bridge: return _bridge.getAssistantSettings(assistant_id)
    return "Bridge not available"

def update_assistant_setting(assistant_id, key, value):
    """Update an assistant setting. Keys: name, model, system_prompt,
    total_steps, tool_timeout, js_timeout, shell_timeout, temperature,
    enable_web_search, sub_agent."""
    if _bridge: return _bridge.updateAssistantSetting(assistant_id, key, value)
    return "Bridge not available"

def update_knowledge_entry(entry_id, title=None, content=None):
    """Update a knowledge base entry."""
    if _bridge: return _bridge.updateKnowledgeEntry(entry_id, title, content)
    return "Bridge not available"

def delete_knowledge_entry(entry_id):
    """Delete a knowledge base entry."""
    if _bridge: return _bridge.deleteKnowledgeEntry(entry_id)
    return "Bridge not available"

def get_setting(key):
    """Read a global app setting."""
    if _bridge: return _bridge.getSetting(key)
    return "Bridge not available"

def update_setting(key, value):
    """Change a global app setting."""
    if _bridge: return _bridge.updateSetting(key, value)
    return "Bridge not available"

# ============================================================
# Main executor
# ============================================================

def execute(code: str, workdir: str) -> str:
    """Execute Python code, return JSON with results."""
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    sys.stdout = StringIO()
    sys.stderr = StringIO()

    # List files before execution
    before = set()
    try:
        before = set(os.listdir(workdir))
    except Exception:
        pass

    result = None
    error = None
    output_files = []

    try:
        os.chdir(workdir)
    except Exception:
        pass

    # Pre-configure matplotlib
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
        plt.rcParams['figure.facecolor'] = 'white'
        plt.rcParams['axes.facecolor'] = 'white'
        plt.rcParams['savefig.facecolor'] = 'white'
    except ImportError:
        pass

    try:
        try:
            result = eval(code)
        except SyntaxError:
            exec(code)
            result = None

        # Auto-save matplotlib figures
        try:
            import matplotlib.pyplot as plt
            for i, fig_num in enumerate(plt.get_fignums()):
                fig = plt.figure(fig_num)
                fname = f"figure_{i+1}.png" if plt.get_fignums() else "figure.png"
                fig.savefig(os.path.join(workdir, fname), dpi=150,
                           bbox_inches='tight', facecolor='white', edgecolor='none')
                output_files.append(fname)
                plt.close(fig)
        except ImportError:
            pass

    except Exception as e:
        error = f"{type(e).__name__}: {e}\n{traceback.format_exc()}"

    finally:
        stdout = sys.stdout.getvalue()
        stderr = sys.stderr.getvalue()
        sys.stdout = old_stdout
        sys.stderr = old_stderr

        # Find new files
        try:
            after = set(os.listdir(workdir))
            for f in after - before:
                if not f.startswith('.'):
                    fpath = os.path.join(workdir, f)
                    if os.path.isfile(fpath) and os.path.getsize(fpath) > 0:
                        output_files.append(f)
        except Exception:
            pass

    resp = {}
    if error:
        resp["error"] = error
    if stdout:
        resp["stdout"] = stdout
    if stderr:
        resp["stderr"] = stderr
    if result is not None and not error:
        resp["result"] = str(result)
    if output_files:
        resp["files"] = list(set(output_files))
    if not resp:
        resp["result"] = "ok"
    return json.dumps(resp)
