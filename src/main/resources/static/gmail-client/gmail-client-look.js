/* gmail-client-look.js -- the Gmail Client's look (Story 5608): page themes, colors and flags.
 *
 * Kept apart from gmail-client.js so the client itself stays about mail. Everything here is
 * created by GmailClientLook.create(ctx) once the page has signed in; ctx gives the API base, the
 * per-user view-state prefix, and what to do when a flag or a color changes (redraw the list).
 *
 *   Themes      "I have themes for email messages, but no themes for the rest of the Gmail client.
 *               I would like more themes, including Sepia and other colors that speak of
 *               antiquity." Classic, Sepia, Parchment, Vellum, Library, Oxblood, Verdigris,
 *               Leather and Midnight. A theme is a set of CSS variables on <body>
 *               (gmail-client.css, the theme layer); the choice is remembered for Tony on every
 *               device. Messages are shown on the theme's paper, but a message's own colors win.
 *   Colors     a message's whole row in the list takes its sender's color, else its domain's
 *               (Tony's QA, 10/08/2026: "The colors should apply to the whole backcolor of the
 *               email in the list, not the flag. The flags should be one thing, the colors
 *               another."). Eighty-eight colors in seven families -- Antique, Neon, Metallic,
 *               Jewel, Bright, Pastel, Earth -- each grown into a ramp of five (pale, light, the
 *               color, deep, dark: about 440 in all; Tony, 2026-10-09, "about five times the
 *               number"), plus any color of your own ("we need far more
 *               colors ... no neon colors ... no metallic styled colors"). Metallic colors are
 *               drawn as a sheen, neon ones glow. The picker shows what is chosen on a sample row
 *               before it is saved; a color tapped twice is used at once ("I should be able to
 *               double click a color to use it"). The picker opens with
 *               a Used group on top -- the colors already on mail -- and every family rolled up.
 *   Flags       grouping labels on mail, each with its own chip color. The Flags dialog makes,
 *               renames, colors and deletes them, and shows a flag's mail. A flag's color is
 *               its chip's only -- it never colors a row.
 *
 * Nothing here is remembered in localStorage: the theme lives in UserViewState; flags and colors
 * in the database (a color is stored as #RRGGBB; its family, and so its sheen or glow, comes from
 * this palette).
 */
(function () {
    'use strict';

    var THEMES = [
        { key: 'classic',   name: 'Classic',   swatch: '#0A66C2', head: '#3C3C3C' },
        { key: 'sepia',     name: 'Sepia',     swatch: '#8B5A2B', head: '#4A3728' },
        { key: 'parchment', name: 'Parchment', swatch: '#7A4E1D', head: '#5C4022' },
        { key: 'vellum',    name: 'Vellum',    swatch: '#2F3E64', head: '#22283A' },
        { key: 'library',   name: 'Library',   swatch: '#2F5D3A', head: '#22392A' },
        { key: 'oxblood',   name: 'Oxblood',   swatch: '#7B1E28', head: '#4A1018' },
        { key: 'verdigris', name: 'Verdigris', swatch: '#2E7D6E', head: '#234C45' },
        { key: 'leather',   name: 'Leather',   swatch: '#D0A24E', head: '#1A120D' },
        { key: 'midnight',  name: 'Midnight',  swatch: '#60A5FA', head: '#020617' },
        // Story 5654: Echo's Outlook theme -- Outlook's look and its folder pane (gmail-client-echo.css / .js).
        { key: 'outlook',   name: 'Outlook',   swatch: '#FFFFFF', head: '#0F6CBD' }
    ];

    /** The color families. style: plain, neon (glows) or metal (a sheen). Every hex is unique. */
    var FAMILIES = [
        { key: 'antique', name: 'Antique', style: 'plain', colors: [
            ['#8B5A2B', 'Umber'], ['#A0522D', 'Sienna'], ['#B5651D', 'Russet'], ['#C8963E', 'Brass'],
            ['#B8860B', 'Old gold'], ['#7B1E28', 'Oxblood'], ['#D4A5A5', 'Dusty rose'], ['#9C6B98', 'Heather'],
            ['#6B4E71', 'Aubergine'], ['#22283A', 'Ink'], ['#2F4F6F', 'Slate blue'], ['#2E7D6E', 'Verdigris'],
            ['#2F5D3A', 'Bottle green'], ['#556B2F', 'Olive'], ['#708090', 'Ash grey'], ['#A67B5B', 'Tan']] },
        { key: 'neon', name: 'Neon', style: 'neon', colors: [
            ['#FF073A', 'Neon red'], ['#FF5F1F', 'Neon orange'], ['#FFF01F', 'Neon yellow'], ['#CCFF00', 'Electric lime'],
            ['#39FF14', 'Neon green'], ['#00FF9F', 'Spring neon'], ['#08F7FE', 'Electric cyan'], ['#1F51FF', 'Neon blue'],
            ['#7B2FFF', 'Ultraviolet'], ['#BC13FE', 'Neon purple'], ['#FF10F0', 'Neon pink'], ['#FF2E88', 'Laser pink']] },
        { key: 'metal', name: 'Metallic', style: 'metal', colors: [
            ['#D4AF37', 'Gold'], ['#B76E79', 'Rose gold'], ['#C0C0C0', 'Silver'], ['#E5E4E2', 'Platinum'],
            ['#CD7F32', 'Bronze'], ['#B87333', 'Copper'], ['#B5A642', 'Polished brass'], ['#F7E7CE', 'Champagne'],
            ['#8E9294', 'Pewter'], ['#878681', 'Titanium'], ['#4682B4', 'Blue steel'], ['#2A3439', 'Gunmetal']] },
        { key: 'jewel', name: 'Jewel', style: 'plain', colors: [
            ['#9B111E', 'Ruby'], ['#733635', 'Garnet'], ['#9966CC', 'Amethyst'], ['#0F52BA', 'Sapphire'],
            ['#50C878', 'Emerald'], ['#00A86B', 'Jade'], ['#FFC87C', 'Topaz'], ['#E4D00A', 'Citrine'],
            ['#40E0D0', 'Turquoise'], ['#B4C424', 'Peridot'], ['#7FFFD4', 'Aquamarine'], ['#353839', 'Onyx']] },
        { key: 'bright', name: 'Bright', style: 'plain', colors: [
            ['#E53935', 'Red'], ['#FB8C00', 'Orange'], ['#FFB300', 'Amber'], ['#FDD835', 'Yellow'],
            ['#7CB342', 'Lime'], ['#43A047', 'Green'], ['#00897B', 'Teal'], ['#039BE5', 'Sky'],
            ['#1E88E5', 'Blue'], ['#3949AB', 'Indigo'], ['#8E24AA', 'Purple'], ['#D81B60', 'Pink']] },
        { key: 'pastel', name: 'Pastel', style: 'plain', colors: [
            ['#F4C2C2', 'Blush'], ['#FBCEB1', 'Apricot'], ['#FFDAB9', 'Peach'], ['#FFF1A8', 'Butter'],
            ['#ACE1AF', 'Celadon'], ['#AAF0D1', 'Mint'], ['#9FE2BF', 'Seafoam'], ['#B0E0E6', 'Powder blue'],
            ['#CCCCFF', 'Periwinkle'], ['#E6E6FA', 'Lavender'], ['#C8A2C8', 'Lilac'], ['#F8D7E3', 'Pale rose']] },
        { key: 'earth', name: 'Earth', style: 'plain', colors: [
            ['#E2725B', 'Terracotta'], ['#B7410E', 'Rust'], ['#B66A50', 'Clay'], ['#C2B280', 'Sand'],
            ['#BDB76B', 'Khaki'], ['#8A9A5B', 'Moss'], ['#228B22', 'Forest'], ['#773F1A', 'Walnut'],
            ['#954535', 'Chestnut'], ['#AF8F6F', 'Driftwood'], ['#928E85', 'Stone'], ['#36454F', 'Charcoal']] }
    ];

    /**
     * Each family's colors grown into ramps: every color gets a pale, a light, a deep and a dark
     * tone, laid out tone by tone (all the pale ones, then the light ones, the colors themselves,
     * the deep ones, the dark ones), so a family reads light to dark. A tone that lands on a hex
     * already in the palette is left out, so every hex stays unique and a saved color keeps its name.
     */
    var TONES = [
        { prefix: 'Pale',  to: 255, amount: 0.6 },
        { prefix: 'Light', to: 255, amount: 0.3 },
        { prefix: '',      to: 0,   amount: 0 },
        { prefix: 'Deep',  to: 0,   amount: 0.25 },
        { prefix: 'Dark',  to: 0,   amount: 0.5 }
    ];

    /** Every palette color, flat: { hex, name, family, style }. */
    var PALETTE = [];
    var BY_HEX = {};
    function addSwatch(f, hex, name) {
        if (BY_HEX[hex]) return;
        var e = { hex: hex, name: name, family: f.name, style: f.style };
        PALETTE.push(e);
        BY_HEX[hex] = e;
        f.swatches.push([hex, name]);
    }
    FAMILIES.forEach(function (f) {
        f.swatches = [];
        f.colors.forEach(function (c) { BY_HEX[c[0]] = null; });   // the named colors win over any tone
    });
    FAMILIES.forEach(function (f) {
        TONES.forEach(function (t) {
            f.colors.forEach(function (c) {
                if (!t.prefix) { BY_HEX[c[0]] = undefined; addSwatch(f, c[0], c[1]); return; }
                var hex = mix(c[0], t.to, t.amount);
                if (BY_HEX[hex] === null) return;                     // a named color's own hex
                addSwatch(f, hex, t.prefix + ' ' + c[1].charAt(0).toLowerCase() + c[1].substr(1));
            });
        });
    });

    var HEX = /^#[0-9A-Fa-f]{6}$/;
    var TWICE_MS = 450;   // two taps on one color this close together use it at once

    function esc(s) {
        return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
    function plural(n, one, many) { return n + ' ' + (n === 1 ? one : (many || one + 's')); }
    function dialog() { return window.Picasso; }

    // ---- color arithmetic ------------------------------------------------------------------

    function rgbOf(hex) {
        return [parseInt(hex.substr(1, 2), 16), parseInt(hex.substr(3, 2), 16), parseInt(hex.substr(5, 2), 16)];
    }
    function hexOf(rgb) {
        return '#' + rgb.map(function (v) {
            var h = Math.max(0, Math.min(255, Math.round(v))).toString(16).toUpperCase();
            return h.length < 2 ? '0' + h : h;
        }).join('');
    }
    /** hex moved amount (0..1) of the way to white (to = 255) or black (to = 0). */
    function mix(hex, to, amount) {
        return hexOf(rgbOf(hex).map(function (v) { return v + (to - v) * amount; }));
    }
    /** WCAG relative luminance, 0 (black) to 1 (white). */
    function luminance(hex) {
        var c = rgbOf(hex).map(function (v) {
            v = v / 255;
            return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        });
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    /** A palette color by its hex, or null for a color of your own. */
    function entryOf(hex) { return hex ? BY_HEX[String(hex).toUpperCase()] || null : null; }
    function styleOf(hex) { var e = entryOf(hex); return e ? e.style : 'plain'; }
    /** What to call a color: its palette name, or "Your own (#123ABC)". */
    function nameOf(hex) {
        if (!hex) return 'No color';
        var e = entryOf(hex);
        return e ? e.name + (e.family === 'Antique' ? '' : ' (' + e.family.toLowerCase() + ')') : 'Your own (' + String(hex).toUpperCase() + ')';
    }

    /**
     * How a color paints a row or a swatch: bg (a color, or a sheen for a metallic one), ink
     * (dark or light text, whichever reads), edge (a darker line), style (plain | neon | metal).
     */
    function paintOf(hex) {
        if (!hex || !HEX.test(hex)) return null;
        hex = hex.toUpperCase();
        var style = styleOf(hex);
        var bg = style === 'metal'
            ? 'linear-gradient(135deg,' + mix(hex, 255, 0.55) + ' 0%,' + hex + ' 38%,' + mix(hex, 0, 0.3) + ' 64%,' + mix(hex, 255, 0.4) + ' 100%)'
            : hex;
        return { hex: hex, style: style, bg: bg, ink: luminance(hex) > 0.179 ? 'dark' : 'light', edge: mix(hex, 0, 0.3) };
    }

    /** The style attribute and classes for a swatch button of hex. */
    function swatchAttrs(hex) {
        var p = paintOf(hex);
        if (!p) return ' class="gc-swatch"';
        return ' class="gc-swatch' + (p.style !== 'plain' ? ' sw-' + p.style : '') + '" style="--sw:' + p.hex + ';--swbg:' + p.bg + '"';
    }

    function create(ctx) {
        var A = window.LWAuth;
        var API = ctx.api;
        var VS_THEME = ctx.vsPrefix + 'pageTheme';
        var state = { flags: [], colors: [], theme: 'classic' };

        // ---- themes ------------------------------------------------------------------------

        function themeOf(key) { return THEMES.filter(function (t) { return t.key === key; })[0] || THEMES[0]; }

        function applyTheme(key) {
            var t = themeOf(key);
            state.theme = t.key;
            document.body.setAttribute('data-gc-theme', t.key);
            var meta = document.querySelector('meta[name="theme-color"]');
            if (meta) meta.setAttribute('content', t.head);
            if (ctx.onTheme) ctx.onTheme(t.key);
        }

        function rememberTheme(key) {
            applyTheme(key);
            var v = window.PicassoViewState;
            if (v) v.set(VS_THEME, state.theme);
        }

        function recalledTheme() {
            var v = window.PicassoViewState;
            return (v && v.get(VS_THEME)) || 'classic';
        }

        /** The theme menu under the header's Theme button: a swatch and a name per theme. */
        function openThemeMenu(anchor) {
            var html = '<div class="gc-look-title">Theme</div>' + THEMES.map(function (t) {
                return '<button type="button" class="gc-theme-row' + (t.key === state.theme ? ' on' : '') + '" data-pick-theme="' + t.key + '"'
                    + ' aria-pressed="' + (t.key === state.theme) + '" data-testid="gmail-client-theme-' + t.key + '">'
                    + '<span class="gc-theme-dot" style="background:' + t.head + ';box-shadow:inset -8px 0 0 ' + t.swatch + '"></span>'
                    + esc(t.name) + '</button>';
            }).join('');
            popover(anchor, html, 'gmail-client-theme-menu', function (b) {
                var k = b.getAttribute('data-pick-theme');
                if (!k) return false;
                rememberTheme(k);
                return true;
            });
        }

        /**
         * The CSS a message frame starts from: the theme's paper and ink and the page font for
         * plain text -- not !important, so a message's own colors and fonts win.
         */
        function paperCss() {
            var cs = window.getComputedStyle(document.body);
            var paper = (cs.getPropertyValue('--gc-paper') || '#fff').trim();
            var ink = (cs.getPropertyValue('--gc-ink') || '#111').trim();
            var font = (cs.getPropertyValue('--gc-text-font') || '"Segoe UI", system-ui, sans-serif').trim();
            return 'html,body{background:' + paper + '}body{color:' + ink + ';font-family:' + font + '}';
        }

        // ---- a small popover (the theme menu), closed by its own choice, Escape, or a tap elsewhere
        // (A pick-list, not a modal: the modal rule exempts menus.)

        var openPop = null;

        function closePopover() {
            if (!openPop) return;
            openPop.el.remove();
            document.removeEventListener('mousedown', openPop.outside, true);
            document.removeEventListener('touchstart', openPop.outside, true);
            document.removeEventListener('keydown', openPop.key, true);
            var done = openPop.done;
            openPop = null;
            if (done) done();
        }

        function popover(anchor, html, testid, onClick, onClose) {
            closePopover();
            var el = document.createElement('div');
            el.className = 'gc-look-pop';
            el.setAttribute('data-testid', testid);
            el.innerHTML = html;
            document.body.appendChild(el);
            var r = anchor.getBoundingClientRect();
            var w = el.offsetWidth, h = el.offsetHeight;
            var left = Math.min(Math.max(8, r.right - w), window.innerWidth - w - 8);
            var top = r.bottom + 6 + h > window.innerHeight ? Math.max(8, r.top - h - 6) : r.bottom + 6;
            el.style.left = left + 'px';
            el.style.top = top + 'px';
            el.addEventListener('click', function (e) {
                var b = e.target.closest('button');
                if (b && onClick(b, el) !== false) closePopover();
            });
            var pop = {
                el: el, done: onClose || null,
                outside: function (e) { if (!el.contains(e.target) && e.target !== anchor) closePopover(); },
                key: function (e) { if (e.key === 'Escape') { e.stopPropagation(); closePopover(); } }
            };
            openPop = pop;
            setTimeout(function () {
                if (openPop !== pop) return;
                document.addEventListener('mousedown', pop.outside, true);
                document.addEventListener('touchstart', pop.outside, true);
                document.addEventListener('keydown', pop.key, true);
            }, 0);
            return el;
        }

        // ---- the color picker -------------------------------------------------------------
        // A modal (it closes only by Save, Cancel, Remove color or Escape): the families of
        // swatches, a color of your own, and a sample of what is chosen. One tap chooses and
        // shows it on the sample; two taps on one color use it at once.

        var pickDlg = null, pick = null;

        function ensurePicker() {
            if (pickDlg) return pickDlg;
            pickDlg = document.createElement('div');
            pickDlg.className = 'gc-modal gc-modal-top';
            pickDlg.hidden = true;
            pickDlg.setAttribute('data-testid', 'gmail-client-colorpick');
            pickDlg.innerHTML = '<div class="gc-dialog gc-colorpick" role="dialog" aria-modal="true" aria-labelledby="gcCpTitle">'
                + '<div class="gc-dialog-title"><h3 id="gcCpTitle">Choose a color</h3></div>'
                + '<div class="gc-cp-for" id="gcCpFor" data-testid="gmail-client-colorpick-for"></div>'
                + '<div class="gc-cp-preview" id="gcCpPreview" data-testid="gmail-client-colorpick-preview"></div>'
                + '<div class="gc-cp-foldall">'
                + '<button type="button" class="gc-btn" data-cp-all="open" data-testid="gmail-client-colorpick-expand-all">Expand all</button>'
                + '<button type="button" class="gc-btn" data-cp-all="fold" data-testid="gmail-client-colorpick-collapse-all">Collapse all</button>'
                + '</div>'
                + '<div class="gc-cp-groups" id="gcCpGroups">'
                + '<div class="gc-cp-group" id="gcCpUsed" data-testid="gmail-client-colorpick-used">'
                + '<div class="gc-cp-head">Used</div><div class="gc-palette" id="gcCpUsedSw"></div></div>'
                + FAMILIES.map(function (f) {
                    return '<div class="gc-cp-group gc-cp-folded" data-family="' + f.key + '" data-testid="gmail-client-colorpick-family">'
                        + '<button type="button" class="gc-cp-head gc-cp-fold" data-cp-fold="' + f.key + '" aria-expanded="false"'
                        + ' data-testid="gmail-client-colorpick-fold"><span class="gc-cp-chev" aria-hidden="true">&#9656;</span> '
                        + esc(f.name) + ' <span class="gc-cp-count">' + f.swatches.length + '</span></button>'
                        + '<div class="gc-palette">' + f.swatches.map(swatchHtml).join('') + '</div></div>';
                }).join('')
                + '<div class="gc-cp-group"><div class="gc-cp-head">Your own</div>'
                + '<div class="gc-hexrow"><input type="color" id="gcCpNative" value="#888888" aria-label="Pick your own color" data-testid="gmail-client-colorpick-native">'
                + '<input type="text" class="gc-hex" id="gcCpHex" maxlength="7" placeholder="#RRGGBB" autocomplete="off" aria-label="Your own color as #RRGGBB"'
                + ' data-testid="gmail-client-swatch-hex"></div></div>'
                + '</div>'
                + '<div class="gc-dialog-btns">'
                + '<button type="button" class="gc-btn danger" data-cp="none" data-testid="gmail-client-swatch-none">Remove color</button>'
                + '<span class="gc-grow"></span>'
                + '<button type="button" class="gc-btn" data-cp="cancel" data-testid="gmail-client-colorpick-cancel">Cancel</button>'
                + '<button type="button" class="gc-btn primary" data-cp="save" data-testid="gmail-client-swatch-use">Save</button>'
                + '</div></div>';
            document.body.appendChild(pickDlg);
            pickDlg.addEventListener('click', pickerClick);
            pickDlg.querySelector('#gcCpNative').addEventListener('input', function (e) {
                choose(String(e.target.value || '').toUpperCase(), false);
            });
            pickDlg.querySelector('#gcCpHex').addEventListener('input', function (e) {
                var v = e.target.value.trim();
                if (v && v.charAt(0) !== '#') v = '#' + v;
                e.target.classList.toggle('bad', !!v && !HEX.test(v));
                if (HEX.test(v)) choose(v.toUpperCase(), false);
            });
            return pickDlg;
        }

        /** One swatch button: [hex, name]. */
        function swatchHtml(c) {
            return '<button type="button"' + swatchAttrs(c[0]) + ' data-hex="' + c[0] + '"'
                + ' title="' + esc(c[1]) + '" aria-label="' + esc(c[1]) + '" aria-pressed="false"'
                + ' data-testid="gmail-client-swatch"></button>';
        }

        /** The Used group: every color already on mail (a sender's or a domain's), each once. */
        function drawUsed() {
            var seen = {}, list = [];
            rowColors().forEach(function (c) {
                var hex = String(c.color || '').toUpperCase();
                if (!HEX.test(hex) || seen[hex]) return;
                seen[hex] = true;
                list.push([hex, nameOf(hex)]);
            });
            pickDlg.querySelector('#gcCpUsedSw').innerHTML = list.map(swatchHtml).join('');
            pickDlg.querySelector('#gcCpUsed').hidden = !list.length;
        }

        /** Roll a family up or open it. Every family starts rolled up each time the page loads. */
        function toggleFamily(key) {
            var g = pickDlg.querySelector('[data-family="' + key + '"]');
            if (!g) return;
            var folded = g.classList.toggle('gc-cp-folded');
            g.querySelector('[data-cp-fold]').setAttribute('aria-expanded', String(!folded));
        }

        /** Expand all / Collapse all: every family at once. Used never rolls up, so it is left alone. */
        function setAllFamilies(folded) {
            Array.prototype.forEach.call(pickDlg.querySelectorAll('[data-family]'), function (g) {
                g.classList.toggle('gc-cp-folded', folded);
                g.querySelector('[data-cp-fold]').setAttribute('aria-expanded', String(!folded));
            });
            if (!folded) return;
            pickDlg.querySelector('#gcCpGroups').scrollTop = 0;
        }

        /** Choose hex (from a swatch, the color well or the box): mark it and paint the sample. */
        function choose(hex, fromSwatch) {
            if (!pick) return;
            pick.hex = hex || null;
            Array.prototype.forEach.call(pickDlg.querySelectorAll('[data-hex]'), function (b) {
                var on = !!hex && b.getAttribute('data-hex') === hex;
                b.classList.toggle('on', on);
                b.setAttribute('aria-pressed', String(on));
            });
            if (hex) {
                pickDlg.querySelector('#gcCpNative').value = hex.toLowerCase();
                // A swatch empties the "your own" box; the box and the color well keep what they hold.
                if (fromSwatch) { pickDlg.querySelector('#gcCpHex').value = ''; pickDlg.querySelector('#gcCpHex').classList.remove('bad'); }
            }
            drawPreview();
        }

        function drawPreview() {
            var hex = pick.hex, p = paintOf(hex);
            var name = '<b>' + esc(nameOf(hex)) + '</b>' + (hex ? ' <span class="gc-cp-twice">Tap a color twice to use it at once.</span>' : '');
            var sample;
            if (pick.chip) {
                sample = '<div class="gc-cp-chiprow"><span class="gc-flag" style="--fc:' + esc(hex || '#708090') + '">' + esc(pick.chip) + '</span></div>';
            } else {
                sample = '<div class="gc-card gc-cp-card' + (p ? ' tinted tint-' + p.style + ' ink-' + p.ink : '') + '"'
                    + (p ? ' style="--tint:' + p.hex + ';--tint-bg:' + p.bg + ';--tint-edge:' + p.edge + '"' : '') + '>'
                    + '<div class="gc-row1"><span class="gc-from">' + esc(pick.sampleFrom || 'Who it is from') + '</span>'
                    + '<span class="gc-when">10:42 am</span></div>'
                    + '<div class="gc-subject">' + esc(pick.sampleSubject || 'How the row looks in the list') + '</div></div>';
            }
            pickDlg.querySelector('#gcCpPreview').innerHTML = sample + '<div class="gc-cp-name" data-testid="gmail-client-colorpick-name">' + name + '</div>';
            pickDlg.querySelector('[data-cp="save"]').disabled = !hex;
        }

        function finish(result) {
            if (!pick) return;
            var done = pick.resolve;
            pick = null;
            pickDlg.hidden = true;
            done(result);
        }

        function pickerClick(e) {
            var sw = e.target.closest('[data-hex]');
            if (sw && pick) {
                var hex = sw.getAttribute('data-hex');
                var now = Date.now();
                var twice = pick.lastHex === hex && now - pick.lastAt < TWICE_MS;
                pick.lastHex = hex;
                pick.lastAt = now;
                choose(hex, true);
                if (twice) finish(hex);
                return;
            }
            var fold = e.target.closest('[data-cp-fold]');
            if (fold) { toggleFamily(fold.getAttribute('data-cp-fold')); return; }
            var all = e.target.closest('[data-cp-all]');
            if (all) { setAllFamilies(all.getAttribute('data-cp-all') === 'fold'); return; }
            var b = e.target.closest('[data-cp]');
            if (!b || b.disabled) return;
            var act = b.getAttribute('data-cp');
            if (act === 'cancel') finish(null);
            else if (act === 'none') finish('');
            else if (act === 'save' && pick && pick.hex) finish(pick.hex);
        }

        /**
         * The color picker. Resolves to '#RRGGBB', to '' for Remove color (only when allowNone),
         * or to null when cancelled. opts: forText (what is being colored), sampleFrom /
         * sampleSubject (the sample row's words), chip (a flag's name: the sample is its chip).
         * The anchor is kept for the callers' sake; the picker is a dialog now.
         */
        function pickColor(anchor, current, allowNone, opts) {
            opts = opts || {};
            ensurePicker();
            closePopover();
            if (pick) finish(null);
            return new Promise(function (resolve) {
                var cur = current && HEX.test(current) ? current.toUpperCase() : null;
                pick = { resolve: resolve, hex: null, lastHex: null, lastAt: 0, chip: opts.chip || null,
                         sampleFrom: opts.sampleFrom, sampleSubject: opts.sampleSubject };
                pickDlg.querySelector('#gcCpFor').textContent = opts.forText || '';
                pickDlg.querySelector('#gcCpFor').hidden = !opts.forText;
                pickDlg.querySelector('[data-cp="none"]').hidden = !allowNone;
                pickDlg.querySelector('#gcCpHex').value = cur && !entryOf(cur) ? cur : '';
                pickDlg.querySelector('#gcCpHex').classList.remove('bad');
                drawUsed();
                pickDlg.hidden = false;
                choose(cur, false);
                // The current color sits in a rolled-up family and is not in Used: open its family.
                var fam = cur && !pickDlg.querySelector('#gcCpUsedSw [data-hex].on') ? entryOf(cur) : null;
                if (fam) {
                    var famKey = FAMILIES.filter(function (f) { return f.name === fam.family; })[0].key;
                    if (pickDlg.querySelector('[data-family="' + famKey + '"].gc-cp-folded')) toggleFamily(famKey);
                }
                var on = pickDlg.querySelector('[data-hex].on');
                if (on && on.scrollIntoView) on.scrollIntoView({ block: 'nearest' });
                else pickDlg.querySelector('#gcCpGroups').scrollTop = 0;
            });
        }

        // ---- data ----------------------------------------------------------------------------

        function load() {
            return Promise.resolve(A.apiGet(API + '/look')).then(function (r) {
                if (!r || !r.ok) return false;
                state.flags = r.data.flags || [];
                state.colors = r.data.colors || [];
                return true;
            });
        }

        function setFlags(flags) { if (flags) state.flags = flags; }
        function setColors(colors) { if (colors) state.colors = colors; }
        function flags() { return state.flags; }
        function flagById(id) { return state.flags.filter(function (f) { return f.id === id; })[0] || null; }

        /** The colors that paint rows: by sender and by domain. (A flag's color is its chip's only.) */
        function rowColors() { return state.colors.filter(function (c) { return c.kind === 'sender' || c.kind === 'domain'; }); }

        /** A domain-ish part of an address: greg@mail.example.com -> mail.example.com */
        function hostOf(address) {
            var a = String(address || '');
            var at = a.lastIndexOf('@');
            return at >= 0 ? a.substring(at + 1).toLowerCase() : '';
        }

        /** A row's color: its sender's, else its domain's. Flags never color a row. */
        function colorFor(row) {
            if (!row) return null;
            var addr = String(row.senderAddress || '').toLowerCase();
            var key = String(row.senderKey || '').toLowerCase();
            var host = hostOf(addr);
            var c, i;
            for (i = 0; i < state.colors.length; i++) {
                c = state.colors[i];
                if (c.kind === 'sender' && c.match && (c.match === addr || c.match === key)) return c.color;
            }
            for (i = 0; i < state.colors.length; i++) {
                c = state.colors[i];
                if (c.kind === 'domain' && c.match && (c.match === key || c.match === host
                    || (host && host.length > c.match.length && host.slice(-c.match.length - 1) === '.' + c.match))) return c.color;
            }
            return null;
        }

        /** How a row is painted (paintOf its color), or null when it has none. */
        function tintFor(row) { return paintOf(colorFor(row)); }

        /** The flag chips on a card or in the reader: name on a tint of the flag's color. */
        function flagChipsHtml(ids, testid) {
            if (!ids || !ids.length) return '';
            return ids.map(function (id) {
                var f = flagById(id);
                if (!f) return '';
                var c = f.color || '#708090';
                return '<span class="gc-flag" style="--fc:' + c + '" data-testid="' + (testid || 'gmail-client-row-flag') + '">'
                    + esc(f.name) + '</span>';
            }).join('');
        }

        // ---- the Colors dialog ------------------------------------------------------------

        var colorsDlg = null, picked = {};

        function ensureColorsDialog() {
            if (colorsDlg) return colorsDlg;
            colorsDlg = document.createElement('div');
            colorsDlg.className = 'gc-modal';
            colorsDlg.hidden = true;
            colorsDlg.setAttribute('data-testid', 'gmail-client-colors');
            colorsDlg.innerHTML = '<div class="gc-dialog">'
                + '<div class="gc-dialog-title"><h3>Colors</h3></div>'
                + '<div class="gc-hint">A color fills a message\'s row in the list: its sender\'s, else its domain\'s. Flags keep their own colors, on their chips (More, then Flags).</div>'
                + '<div class="gc-color-tools">'
                + '<label class="gc-selall"><input type="checkbox" id="gcColAll" data-testid="gmail-client-colors-all"> <span id="gcColCount">None ticked</span></label>'
                + '<button type="button" class="gc-btn" data-col="recolor" data-testid="gmail-client-colors-recolor">Change color</button>'
                + '<button type="button" class="gc-btn danger" data-col="remove" data-testid="gmail-client-colors-remove">Remove</button>'
                + '</div>'
                + '<div id="gcColList" class="gc-color-list" data-testid="gmail-client-colors-list"></div>'
                + '<div class="gc-color-add">'
                + '<div class="gc-seg" id="gcColKind" role="group" aria-label="Color by" data-testid="gmail-client-colors-kind">'
                + '<button type="button" data-kind="sender" class="on" data-testid="gmail-client-colors-kind-sender">Sender</button>'
                + '<button type="button" data-kind="domain" data-testid="gmail-client-colors-kind-domain">Domain</button></div>'
                + '<input type="text" id="gcColMatch" autocomplete="off" placeholder="greg@example.com" data-testid="gmail-client-colors-match">'
                + '<button type="button" class="gc-btn primary" data-col="add" data-testid="gmail-client-colors-add">Choose color</button>'
                + '</div>'
                + '<div class="gc-dialog-err" id="gcColErr" hidden data-testid="gmail-client-colors-error"></div>'
                + '<div class="gc-dialog-btns">'
                + '<button type="button" class="gc-btn danger" data-col="clear" data-testid="gmail-client-colors-clear">Clear all colors</button>'
                + '<span class="gc-grow"></span>'
                + '<button type="button" class="gc-btn" data-col="close" data-testid="gmail-client-colors-close">Close</button>'
                + '</div></div>';
            document.body.appendChild(colorsDlg);
            colorsDlg.addEventListener('click', colorsClick);
            colorsDlg.querySelector('#gcColAll').addEventListener('change', function (e) {
                picked = {};
                if (e.target.checked) rowColors().forEach(function (c) { picked[c.id] = true; });
                drawColors();
            });
            return colorsDlg;
        }

        var addKind = 'sender';

        function openColors(prefill) {
            ensureColorsDialog();
            picked = {};
            addKind = prefill && prefill.kind === 'domain' ? 'domain' : 'sender';
            colorsDlg.querySelector('#gcColMatch').value = prefill && prefill.match ? prefill.match : '';
            colorsDlg.querySelector('#gcColErr').hidden = true;
            drawColors();
            colorsDlg.hidden = false;
            return load().then(drawColors);
        }

        function kindLabel(c) { return c.kind === 'sender' ? 'Sender' : 'Domain'; }

        function drawColors() {
            var d = colorsDlg;
            var list = rowColors();
            var n = Object.keys(picked).length;
            d.querySelector('#gcColCount').textContent = n ? n + ' ticked' : 'None ticked';
            d.querySelector('#gcColAll').checked = n > 0 && n === list.length;
            d.querySelector('[data-col="recolor"]').disabled = !n;
            d.querySelector('[data-col="remove"]').disabled = !n;
            d.querySelector('[data-col="clear"]').disabled = !list.length;
            d.querySelector('#gcColList').innerHTML = list.length ? list.map(function (c) {
                return '<label class="gc-color-row' + (picked[c.id] ? ' on' : '') + '" data-testid="gmail-client-colors-row">'
                    + '<input type="checkbox" data-col-id="' + c.id + '"' + (picked[c.id] ? ' checked' : '') + ' data-testid="gmail-client-colors-row-check">'
                    + '<button type="button"' + swatchAttrs(c.color) + ' data-col-swatch="' + c.id + '"'
                    + ' title="Change this color" aria-label="Change this color" data-testid="gmail-client-colors-row-swatch"></button>'
                    + '<span class="gc-color-kind">' + kindLabel(c) + '</span>'
                    + '<span class="gc-color-match">' + esc(c.match) + '</span>'
                    + '<span class="gc-color-name" data-testid="gmail-client-colors-row-name">' + esc(nameOf(c.color)) + '</span>'
                    + '</label>';
            }).join('') : '<div class="gc-hint">No colors yet. Color a sender or a domain below, or from any message.</div>';
            Array.prototype.forEach.call(d.querySelectorAll('#gcColKind [data-kind]'), function (b) {
                b.classList.toggle('on', b.getAttribute('data-kind') === addKind);
            });
            d.querySelector('#gcColMatch').placeholder = addKind === 'domain' ? 'example.com' : 'greg@example.com';
        }

        /** A refused request says why (LWAuth.errorText); r null = a plain sentence from this page. */
        function colorsError(r, d) {
            var e = colorsDlg.querySelector('#gcColErr');
            e.textContent = r ? A.errorText(r, d) : d;
            e.hidden = false;
        }

        function afterColors(r) {
            setColors(r.data.colors);
            colorsDlg.querySelector('#gcColErr').hidden = true;
            picked = {};
            drawColors();
            if (ctx.onChange) ctx.onChange();
        }

        function colorsClick(e) {
            var t = e.target;
            var box = t.closest('[data-col-id]');
            if (box) {
                var id = parseInt(box.getAttribute('data-col-id'), 10);
                if (box.checked) picked[id] = true; else delete picked[id];
                drawColors();
                return;
            }
            var kb = t.closest('#gcColKind [data-kind]');
            if (kb) { addKind = kb.getAttribute('data-kind'); drawColors(); return; }
            var sw = t.closest('[data-col-swatch]');
            if (sw) {
                e.preventDefault();
                var cid = parseInt(sw.getAttribute('data-col-swatch'), 10);
                var c = state.colors.filter(function (x) { return x.id === cid; })[0];
                if (!c) return;
                pickColor(sw, c.color, false, { forText: kindLabel(c) + ' ' + c.match, sampleFrom: c.match }).then(function (hex) {
                    if (!hex || hex === c.color) return;
                    Promise.resolve(A.apiPost(API + '/colors/set', { ids: [cid], color: hex })).then(function (r) {
                        if (!r || !r.ok) { colorsError(r, 'The color was not changed.'); return; }
                        afterColors(r);
                    });
                });
                return;
            }
            var b = t.closest('[data-col]');
            if (!b || b.disabled) return;
            var act = b.getAttribute('data-col');
            var ids = Object.keys(picked).map(Number);
            if (act === 'close') { colorsDlg.hidden = true; closePopover(); return; }
            if (act === 'add') { addColor(b); return; }
            if (act === 'recolor') {
                var chosen = rowColors().filter(function (c) { return picked[c.id]; });
                var same = chosen.length && chosen.every(function (c) { return c.color === chosen[0].color; }) ? chosen[0].color : null;
                pickColor(b, same, false, { forText: plural(chosen.length, 'color') + ': '
                    + chosen.slice(0, 3).map(function (c) { return c.match; }).join(', ') + (chosen.length > 3 ? ' ...' : '') }).then(function (hex) {
                    if (!hex) return;
                    b.disabled = true;
                    Promise.resolve(A.apiPost(API + '/colors/set', { ids: ids, color: hex })).then(function (r) {
                        b.disabled = false;
                        if (!r || !r.ok) { colorsError(r, 'The colors were not changed.'); return; }
                        afterColors(r);
                    });
                });
                return;
            }
            if (act === 'remove' || act === 'clear') {
                var all = act === 'clear';
                var which = all ? rowColors() : rowColors().filter(function (c) { return picked[c.id]; });
                var names = which.slice(0, 3).map(function (c) { return kindLabel(c) + ' ' + c.match + ' -- ' + nameOf(c.color); });
                var text = (all ? 'Clear all colors: ' + plural(which.length, 'color') + '?' : 'Remove ' + plural(which.length, 'color') + '?')
                    + '\n\n' + names.join('\n') + (which.length > 3 ? '\nand ' + (which.length - 3) + ' more' : '')
                    + '\n\nOnly the colors go -- every sender, domain and message stays, and flags keep their colors. This cannot be undone.';
                Promise.resolve(dialog().confirmDelete(text, { title: all ? 'Clear all colors' : 'Remove colors', yes: all ? 'Clear' : 'Remove' }))
                    .then(function (ok) {
                        if (!ok) return;
                        b.disabled = true;
                        // Clear all removes the row colors only (by id), never a flag's chip color.
                        Promise.resolve(A.apiPost(API + '/colors/delete', { ids: which.map(function (c) { return c.id; }) })).then(function (r) {
                            b.disabled = false;
                            if (!r || !r.ok) { colorsError(r, 'The colors were not removed.'); return; }
                            afterColors(r);
                        });
                    });
            }
        }

        function addColor(b) {
            var body = { kind: addKind, match: colorsDlg.querySelector('#gcColMatch').value.trim() };
            if (!body.match) { colorsError(null, addKind === 'domain' ? 'Type a domain, such as example.com.' : 'Type an email address.'); return; }
            var existing = state.colors.filter(function (c) { return c.kind === addKind && c.match === body.match.toLowerCase(); })[0];
            pickColor(b, existing ? existing.color : null, false, { forText: (addKind === 'domain' ? 'Everyone at ' : '') + body.match, sampleFrom: body.match })
                .then(function (hex) {
                    if (!hex) return;
                    body.color = hex;
                    b.disabled = true;
                    Promise.resolve(A.apiPost(API + '/colors', body)).then(function (r) {
                        b.disabled = false;
                        if (!r || !r.ok) { colorsError(r, 'The color was not saved.'); return; }
                        colorsDlg.querySelector('#gcColMatch').value = '';
                        afterColors(r);
                    });
                });
        }

        /** "Color this sender" / "Color this domain" from a message: the picker, then save. */
        function colorQuick(anchor, kind, match, sample) {
            var existing = state.colors.filter(function (c) { return c.kind === kind && c.match === String(match || '').toLowerCase(); })[0];
            return pickColor(anchor, existing ? existing.color : null, !!existing, {
                forText: (kind === 'domain' ? 'Everyone at ' : '') + match,
                sampleFrom: sample && sample.from, sampleSubject: sample && sample.subject
            }).then(function (hex) {
                if (hex === null) return false;
                if (existing && hex === existing.color) return false;
                var call = hex === '' ? A.apiPost(API + '/colors/delete', { ids: [existing.id] })
                    : A.apiPost(API + '/colors', { kind: kind, match: match, color: hex });
                return Promise.resolve(call).then(function (r) {
                    if (!r || !r.ok) { dialog().error(A.errorText(r, 'The color was not saved.')); return false; }
                    setColors(r.data.colors);
                    if (ctx.onChange) ctx.onChange();
                    if (ctx.flash) ctx.flash(hex === '' ? 'Color removed' : 'Colored ' + nameOf(hex));
                    return true;
                });
            });
        }

        // ---- the Flags dialog --------------------------------------------------------------

        var flagsDlg = null;

        function ensureFlagsDialog() {
            if (flagsDlg) return flagsDlg;
            flagsDlg = document.createElement('div');
            flagsDlg.className = 'gc-modal';
            flagsDlg.hidden = true;
            flagsDlg.setAttribute('data-testid', 'gmail-client-flags');
            flagsDlg.innerHTML = '<div class="gc-dialog">'
                + '<div class="gc-dialog-title"><h3>Flags</h3></div>'
                + '<div class="gc-hint">A flag is a label you put on mail -- open a message and type one under Flags, or select messages and choose Flag. The Flagged tab groups your mail by flag, and Flags on the main page shows only the flags you pick.</div>'
                + '<div id="gcFlagList" data-testid="gmail-client-flag-list"></div>'
                + '<div class="gc-add-inbox">'
                + '<input type="text" id="gcFlagNew" maxlength="60" autocomplete="off" placeholder="New flag, for example Taxes" data-testid="gmail-client-flag-new">'
                + '<button type="button" class="gc-btn primary" data-fl="add" data-testid="gmail-client-flag-new-save">Save</button></div>'
                + '<div class="gc-dialog-err" id="gcFlagErr" hidden data-testid="gmail-client-flags-error"></div>'
                + '<div class="gc-dialog-btns"><button type="button" class="gc-btn" data-fl="close" data-testid="gmail-client-flags-close">Close</button></div>'
                + '</div>';
            document.body.appendChild(flagsDlg);
            flagsDlg.addEventListener('click', flagsClick);
            flagsDlg.querySelector('#gcFlagNew').addEventListener('keydown', function (e) {
                if (e.key === 'Enter') { e.preventDefault(); flagsClick({ target: flagsDlg.querySelector('[data-fl="add"]') }); }
            });
            return flagsDlg;
        }

        function openFlags() {
            ensureFlagsDialog();
            flagsDlg.querySelector('#gcFlagErr').hidden = true;
            flagsDlg.querySelector('#gcFlagNew').value = '';
            drawFlags();
            flagsDlg.hidden = false;
            return load().then(drawFlags);
        }

        function drawFlags() {
            flagsDlg.querySelector('#gcFlagList').innerHTML = state.flags.length ? state.flags.map(function (f) {
                return '<div class="gc-inbox-row" data-flag-id="' + f.id + '" data-testid="gmail-client-flag-row">'
                    + '<button type="button"' + swatchAttrs(f.color || '#CBD5E1') + ' data-fl="color"'
                    + ' title="Color this flag" aria-label="Color this flag" data-testid="gmail-client-flag-color"></button>'
                    + '<input type="text" maxlength="60" value="' + esc(f.name) + '" aria-label="Flag name" data-testid="gmail-client-flag-name">'
                    + '<button type="button" class="gc-count" data-fl="show" title="Show the mail with this flag" data-testid="gmail-client-flag-show">'
                    + plural(f.messageCount, 'email') + '</button>'
                    + '<button type="button" class="gc-btn" data-fl="rename" data-testid="gmail-client-flag-rename">Save</button>'
                    + '<button type="button" class="gc-btn danger" data-fl="delete" data-testid="gmail-client-flag-delete">Delete</button>'
                    + '</div>';
            }).join('') : '<div class="gc-hint">No flags yet.</div>';
        }

        function flagsError(r, d) {
            var e = flagsDlg.querySelector('#gcFlagErr');
            e.textContent = r ? A.errorText(r, d) : d;
            e.hidden = false;
        }

        function afterFlags(r) {
            if (r.data.flags) setFlags(r.data.flags);
            flagsDlg.querySelector('#gcFlagErr').hidden = true;
            drawFlags();
            if (ctx.onChange) ctx.onChange(r.data.tabs);
        }

        function flagsClick(e) {
            var b = e.target.closest('[data-fl]');
            if (!b || b.disabled) return;
            var act = b.getAttribute('data-fl');
            if (act === 'close') { flagsDlg.hidden = true; closePopover(); return; }
            if (act === 'add') {
                var name = flagsDlg.querySelector('#gcFlagNew').value.trim();
                if (!name) { flagsError(null, 'Give the flag a name.'); return; }
                b.disabled = true;
                Promise.resolve(A.apiPost(API + '/flags', { name: name })).then(function (r) {
                    b.disabled = false;
                    if (!r || !r.ok) { flagsError(r, 'The flag was not saved.'); return; }
                    flagsDlg.querySelector('#gcFlagNew').value = '';
                    afterFlags(r);
                });
                return;
            }
            var row = b.closest('[data-flag-id]');
            if (!row) return;
            var id = parseInt(row.getAttribute('data-flag-id'), 10);
            var f = flagById(id);
            if (!f) return;
            if (act === 'show') {
                flagsDlg.hidden = true;
                if (ctx.showFlags) ctx.showFlags([f.id]); else if (ctx.showMail) ctx.showMail('flag:"' + f.name + '"');
                return;
            }
            if (act === 'color') {
                var existing = state.colors.filter(function (c) { return c.kind === 'flag' && c.flagId === id; })[0];
                pickColor(b, f.color, !!existing, { forText: 'Flag ' + f.name, chip: f.name }).then(function (hex) {
                    if (hex === null || (existing && hex === existing.color)) return;
                    var call = hex === '' ? A.apiPost(API + '/colors/delete', { ids: [existing.id] })
                        : A.apiPost(API + '/colors', { kind: 'flag', flagId: id, color: hex });
                    Promise.resolve(call).then(function (r) {
                        if (!r || !r.ok) { flagsError(r, 'The color was not saved.'); return; }
                        setColors(r.data.colors);
                        load().then(function () { drawFlags(); if (ctx.onChange) ctx.onChange(); });
                    });
                });
                return;
            }
            if (act === 'rename') {
                b.disabled = true;
                Promise.resolve(A.apiPost(API + '/flags/' + id, { name: row.querySelector('input').value }, 'PUT')).then(function (r) {
                    b.disabled = false;
                    if (!r || !r.ok) { flagsError(r, 'The flag was not renamed.'); return; }
                    afterFlags(r);
                    if (ctx.flash) ctx.flash('Flag renamed');
                });
                return;
            }
            if (act === 'delete') {
                Promise.resolve(dialog().confirmDelete('Delete Flag: ' + f.name + '?\n\nIt comes off ' + plural(f.messageCount, 'email')
                    + ', and its color goes with it. No email is deleted.\n\nThis cannot be undone.', { title: 'Delete flag' }))
                    .then(function (ok) {
                        if (!ok) return;
                        b.disabled = true;
                        Promise.resolve(A.apiPost(API + '/flags/' + id, null, 'DELETE')).then(function (r) {
                            b.disabled = false;
                            if (!r || !r.ok) { flagsError(r, 'The flag was not deleted.'); return; }
                            load().then(function () { drawFlags(); if (ctx.onChange) ctx.onChange(r.data.tabs); });
                        });
                    });
            }
        }

        function isOpen() {
            return !!((pickDlg && !pickDlg.hidden) || (colorsDlg && !colorsDlg.hidden) || (flagsDlg && !flagsDlg.hidden) || openPop);
        }

        /** Escape: the top-most thing this file opened. True when it closed something. */
        function escape() {
            if (openPop) { closePopover(); return true; }
            if (pickDlg && !pickDlg.hidden) { finish(null); return true; }
            if (colorsDlg && !colorsDlg.hidden) { colorsDlg.hidden = true; return true; }
            if (flagsDlg && !flagsDlg.hidden) { flagsDlg.hidden = true; return true; }
            return false;
        }

        applyTheme(recalledTheme());

        return {
            THEMES: THEMES,
            applyTheme: applyTheme, recalledTheme: recalledTheme, openThemeMenu: openThemeMenu, paperCss: paperCss,
            pickColor: pickColor, colorQuick: colorQuick, colorFor: colorFor, tintFor: tintFor, paintOf: paintOf, nameOf: nameOf,
            flagChipsHtml: flagChipsHtml,
            load: load, flags: flags, flagById: flagById, setFlags: setFlags, setColors: setColors,
            openColors: openColors, openFlags: openFlags, isOpen: isOpen, escape: escape, closePopover: closePopover
        };
    }

    window.GmailClientLook = { create: create, THEMES: THEMES, PALETTE: PALETTE, FAMILIES: FAMILIES };
})();
