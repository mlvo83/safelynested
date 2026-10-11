/*
 * SafelyNested in-app help assistant widget.
 * Pairs with templates/fragments/assistant-widget.html and POST /assistant/chat
 * (AssistantController), which streams Server-Sent Events: delta / done / error.
 * The conversation lives in sessionStorage so it survives page navigation within a tab
 * and disappears when the tab closes. No external dependencies.
 */
(function () {
    'use strict';

    var ENDPOINT = '/assistant/chat';
    var HISTORY_KEY = 'sna.history.v1';
    var OPEN_KEY = 'sna.open.v1';
    var MAX_HISTORY = 20;
    var STARTERS = [
        'What can I do in this app?',
        'How do I create a referral?',
        'Why do I see "access denied"?'
    ];

    var toggle = document.getElementById('snaToggle');
    var panel = document.getElementById('snaPanel');
    var log = document.getElementById('snaLog');
    var form = document.getElementById('snaForm');
    var input = document.getElementById('snaInput');
    var send = document.getElementById('snaSend');
    var closeBtn = document.getElementById('snaClose');
    var clearBtn = document.getElementById('snaClear');
    if (!toggle || !panel || !log || !form || !input || !send) { return; }

    var history = loadHistory();
    var busy = false;

    // ---------------------------------------------------------------- storage

    function loadHistory() {
        try {
            var raw = sessionStorage.getItem(HISTORY_KEY);
            var arr = raw ? JSON.parse(raw) : [];
            return Array.isArray(arr) ? arr : [];
        } catch (e) { return []; }
    }
    function saveHistory() {
        try { sessionStorage.setItem(HISTORY_KEY, JSON.stringify(history.slice(-MAX_HISTORY))); } catch (e) { /* ignore */ }
    }
    function rememberOpen(open) {
        try { sessionStorage.setItem(OPEN_KEY, open ? '1' : '0'); } catch (e) { /* ignore */ }
    }
    function wasOpen() {
        try { return sessionStorage.getItem(OPEN_KEY) === '1'; } catch (e) { return false; }
    }

    // ------------------------------------------------------------- rendering

    function escapeHtml(s) {
        return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // Tiny markdown subset: paragraphs, bullet/numbered lists, **bold**, `code`,
    // [text](/path) links and bare in-app paths. Everything is HTML-escaped first.
    function inline(s) {
        s = escapeHtml(s);
        s = s.replace(/`([^`]+)`/g, '<code>$1</code>');
        s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
        s = s.replace(/\[([^\]]+)\]\((\/[^)\s]*)\)/g, function (m, text, path) {
            return '<a href="' + path + '">' + text + '</a>';
        });
        // bare paths like /charity-partner/2/referrals (not inside an existing tag, no {placeholders})
        s = s.replace(/(^|[\s(>])(\/[a-z][A-Za-z0-9\-]*(?:\/[A-Za-z0-9\-_.?=&]+)*\/?)(?![^<]*<\/a>)/g, function (m, pre, path) {
            var trail = '';
            var t = path.match(/[.,;:)]+$/);
            if (t) { trail = t[0]; path = path.slice(0, -trail.length); }
            if (path.length < 2 || path.indexOf('{') >= 0) { return m; }
            return pre + '<a href="' + path + '">' + path + '</a>' + trail;
        });
        return s;
    }

    function renderMarkdown(src) {
        var lines = src.replace(/\r/g, '').split('\n');
        var html = '';
        var para = [];
        var list = null; // 'ul' | 'ol'

        function flushPara() {
            if (para.length) { html += '<p>' + inline(para.join(' ')) + '</p>'; para = []; }
        }
        function closeList() {
            if (list) { html += '</' + list + '>'; list = null; }
        }

        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            var m;
            if (!line.trim()) { flushPara(); closeList(); continue; }
            if ((m = line.match(/^\s*([-*•])\s+(.*)$/))) {
                flushPara();
                if (list !== 'ul') { closeList(); html += '<ul>'; list = 'ul'; }
                html += '<li>' + inline(m[2]) + '</li>';
                continue;
            }
            if ((m = line.match(/^\s*(\d+)[.)]\s+(.*)$/))) {
                flushPara();
                if (list !== 'ol') { closeList(); html += '<ol>'; list = 'ol'; }
                html += '<li>' + inline(m[2]) + '</li>';
                continue;
            }
            if ((m = line.match(/^\s*#{1,6}\s+(.*)$/))) {
                flushPara(); closeList();
                html += '<p><strong>' + inline(m[1]) + '</strong></p>';
                continue;
            }
            closeList();
            para.push(line.trim());
        }
        flushPara(); closeList();
        return html;
    }

    function addMessage(role, text) {
        var div = document.createElement('div');
        div.className = 'sna-msg ' + role;
        if (role === 'assistant') { div.innerHTML = renderMarkdown(text); }
        else { div.textContent = text; }
        log.appendChild(div);
        scrollDown();
        return div;
    }

    function addTyping() {
        var div = document.createElement('div');
        div.className = 'sna-msg assistant sna-typing';
        div.innerHTML = '<span></span><span></span><span></span>';
        log.appendChild(div);
        scrollDown();
        return div;
    }

    function scrollDown() { log.scrollTop = log.scrollHeight; }

    function renderAll() {
        log.innerHTML = '';
        if (!history.length) {
            addMessage('assistant', 'Hi! I can explain how to use SafelyNested: where to find things, what a status means, or the steps for a task. What would you like to do?');
            var chips = document.createElement('div');
            chips.className = 'sna-chips';
            STARTERS.forEach(function (q) {
                var b = document.createElement('button');
                b.type = 'button';
                b.textContent = q;
                b.addEventListener('click', function () { input.value = q; form.requestSubmit ? form.requestSubmit() : submit(); });
                chips.appendChild(b);
            });
            log.appendChild(chips);
            return;
        }
        history.forEach(function (t) { addMessage(t.role, t.content); });
    }

    // ------------------------------------------------------------ open/close

    function openPanel() {
        panel.hidden = false;
        toggle.setAttribute('aria-expanded', 'true');
        rememberOpen(true);
        renderAll();
        setTimeout(function () { input.focus(); }, 50);
    }
    function closePanel() {
        panel.hidden = true;
        toggle.setAttribute('aria-expanded', 'false');
        rememberOpen(false);
    }

    toggle.addEventListener('click', function () { panel.hidden ? openPanel() : closePanel(); });
    closeBtn.addEventListener('click', closePanel);
    clearBtn.addEventListener('click', function () {
        if (busy) { return; }
        history = [];
        saveHistory();
        renderAll();
        input.focus();
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && !panel.hidden) { closePanel(); toggle.focus(); }
    });
    input.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(); }
    });
    input.addEventListener('input', function () {
        input.style.height = 'auto';
        input.style.height = Math.min(input.scrollHeight, 120) + 'px';
    });
    form.addEventListener('submit', function (e) { e.preventDefault(); submit(); });

    // --------------------------------------------------------------- sending

    // The server renders the CSRF token and header name into #snaConfig (see the fragment).
    // That value is what Spring Security expects back; the raw cookie value is not.
    function csrfHeader() {
        var cfg = document.getElementById('snaConfig');
        var name = cfg && cfg.getAttribute('data-csrf-header');
        var token = cfg && cfg.getAttribute('data-csrf-token');
        if (name && token) { return { name: name, value: token }; }
        var m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        return m ? { name: 'X-XSRF-TOKEN', value: decodeURIComponent(m[1]) } : null;
    }

    function setBusy(b) {
        busy = b;
        send.disabled = b;
        input.disabled = b;
    }

    function submit() {
        var text = (input.value || '').trim();
        if (!text || busy) { return; }

        // first real message replaces the welcome + starter chips
        if (!history.length) { log.innerHTML = ''; }

        input.value = '';
        input.style.height = 'auto';
        addMessage('user', text);
        var priorHistory = history.slice(-MAX_HISTORY);
        history.push({ role: 'user', content: text });
        saveHistory();

        setBusy(true);
        var bubble = addTyping();
        var answer = '';
        var gotText = false;

        function showText() {
            if (!gotText) { gotText = true; bubble.className = 'sna-msg assistant'; }
            bubble.innerHTML = renderMarkdown(answer);
            scrollDown();
        }
        function showError(msg) {
            bubble.className = 'sna-msg error';
            bubble.textContent = msg;
            scrollDown();
        }

        var headers = { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' };
        var csrf = csrfHeader();
        if (csrf) { headers[csrf.name] = csrf.value; }

        fetch(ENDPOINT, {
            method: 'POST',
            headers: headers,
            credentials: 'same-origin',
            body: JSON.stringify({ message: text, history: priorHistory })
        }).then(function (res) {
            if (res.status === 401 || res.status === 403 || res.redirected) {
                throw new Error('Your session has ended. Please sign in again and retry.');
            }
            if (!res.ok || !res.body) {
                throw new Error('The assistant is unavailable right now (HTTP ' + res.status + ').');
            }
            var reader = res.body.getReader();
            var decoder = new TextDecoder();
            var buffer = '';
            var failed = null;

            function handleEvent(raw) {
                var name = 'message';
                var data = [];
                raw.split('\n').forEach(function (line) {
                    if (line.indexOf('event:') === 0) { name = line.slice(6).trim(); }
                    else if (line.indexOf('data:') === 0) { data.push(line.slice(5).replace(/^ /, '')); }
                });
                if (!data.length) { return; }
                var payload;
                try { payload = JSON.parse(data.join('\n')); } catch (e) { payload = { text: data.join('\n') }; }
                if (name === 'delta' && typeof payload.text === 'string') {
                    answer += payload.text;
                    showText();
                } else if (name === 'error') {
                    failed = payload.message || 'Something went wrong.';
                }
            }

            function pump() {
                return reader.read().then(function (r) {
                    if (r.done) {
                        if (buffer.trim()) { handleEvent(buffer); buffer = ''; }
                        return;
                    }
                    buffer += decoder.decode(r.value, { stream: true });
                    var idx;
                    while ((idx = buffer.indexOf('\n\n')) >= 0) {
                        handleEvent(buffer.slice(0, idx));
                        buffer = buffer.slice(idx + 2);
                    }
                    return pump();
                });
            }
            return pump().then(function () {
                if (failed) { throw new Error(failed); }
                if (!answer.trim()) { throw new Error('I did not get an answer back. Please try again.'); }
            });
        }).then(function () {
            history.push({ role: 'assistant', content: answer });
            saveHistory();
        }).catch(function (err) {
            if (answer.trim()) {
                // keep whatever streamed before the problem, and note it
                history.push({ role: 'assistant', content: answer });
                saveHistory();
                addMessage('error', (err && err.message) || 'Something went wrong.');
            } else {
                showError((err && err.message) || 'Something went wrong. Please try again.');
            }
        }).then(function () {
            setBusy(false);
            input.focus();
        });
    }

    // restore open state across page loads within the tab
    if (wasOpen()) { openPanel(); }
})();
