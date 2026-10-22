/* =====================================================================
   Step-up gate -- Echo's version (Story 5653)
   ---------------------------------------------------------------------
   LeonardoWeb asks for a passkey before a page that hands out keys
   (Admin -> API Clients). Echo has no passkeys, so it asks for the
   signed-in person's password again instead, checked by
   POST /api/auth/verify-password. A yes is remembered for this browser
   tab for 15 minutes (sessionStorage), so moving around the page does
   not ask again.

   Same call as LeonardoWeb's: StepUpGate.requireFeature(name) -> Promise<bool>.
   ===================================================================== */
(function () {
    'use strict';

    var REMEMBER_MS = 15 * 60 * 1000;

    function rememberedAt(feature) {
        try { return parseInt(sessionStorage.getItem('echo.stepup.' + feature), 10) || 0; } catch (e) { return 0; }
    }
    function remember(feature) {
        try { sessionStorage.setItem('echo.stepup.' + feature, String(Date.now())); } catch (e) { /* private mode */ }
    }

    function ask(feature) {
        return new Promise(function (resolve) {
            var back = document.createElement('div');
            back.setAttribute('data-testid', 'stepup-overlay');
            back.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,.45);z-index:9000;display:flex;align-items:center;justify-content:center;padding:16px';
            back.innerHTML =
                '<div style="background:#fff;border-radius:6px;box-shadow:0 8px 30px rgba(0,0,0,.25);width:min(380px,100%);padding:20px 22px;font:14px \'Segoe UI\',sans-serif;color:#1a1a1a">' +
                '<h2 style="margin:0 0 6px;font-size:16px">Confirm it is you</h2>' +
                '<p style="margin:0 0 12px;line-height:1.45;color:#444">This page hands out the keys an agent uses to reach Echo. Type your password to go on.</p>' +
                '<input type="password" id="stepupPw" data-testid="stepup-password" autocomplete="current-password" style="width:100%;box-sizing:border-box;padding:8px 10px;font-size:14px;border:1px solid #C9CDD6;border-radius:4px">' +
                '<div id="stepupErr" data-testid="stepup-error" style="color:#C00000;min-height:18px;font-size:13px;margin:6px 0 4px"></div>' +
                '<div style="display:flex;gap:8px;justify-content:flex-end">' +
                '<button type="button" id="stepupCancel" data-testid="stepup-cancel" style="padding:7px 14px">Cancel</button>' +
                '<button type="button" id="stepupOk" data-testid="stepup-ok" style="padding:7px 14px;background:#3C3C3C;color:#fff;border:none;border-radius:3px">Continue</button>' +
                '</div></div>';
            document.body.appendChild(back);
            var pw = back.querySelector('#stepupPw'), err = back.querySelector('#stepupErr'), ok = back.querySelector('#stepupOk');
            function done(v) {
                document.removeEventListener('keydown', onKey, true);
                if (back.parentNode) back.parentNode.removeChild(back);
                resolve(v);
            }
            async function check() {
                if (!pw.value) { err.textContent = 'Type your password.'; return; }
                ok.disabled = true;
                var r = await window.LWAuth.apiPost('/api/auth/verify-password', { password: pw.value }, 'POST');
                ok.disabled = false;
                if (r && r.ok) { remember(feature); done(true); return; }
                err.textContent = (r && r.status === 400) ? 'That password is not right.' : window.LWAuth.errorText(r, 'Checking the password failed');
                pw.select();
            }
            function onKey(e) {
                if (e.key === 'Escape') { e.preventDefault(); done(false); }
                else if (e.key === 'Enter' && document.activeElement === pw) { e.preventDefault(); check(); }
            }
            document.addEventListener('keydown', onKey, true);
            back.querySelector('#stepupCancel').addEventListener('click', function () { done(false); });
            ok.addEventListener('click', check);
            setTimeout(function () { pw.focus(); }, 30);
        });
    }

    window.StepUpGate = {
        requireFeature: async function (feature) {
            if (Date.now() - rememberedAt(feature) < REMEMBER_MS) return true;
            var yes = await ask(feature);
            if (!yes) window.location.href = '/';
            return yes;
        }
    };
})();
