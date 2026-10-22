/* =====================================================================
   Quick Launch -- universal pin component + dashboard renderer
   ---------------------------------------------------------------------
   Auto-injects a "\uD83D\uDCCC Pin" button into the nav strip of every authenticated
   page. Clicking it opens the pin modal (label + icon picker) and POSTs to
   /api/quick-launch.

   Also exposes window.QuickLaunch for the dashboard and manage page to
   reuse the icon picker, modal, and reorder helpers.
   ===================================================================== */
(function () {
    'use strict';

    var CSS_HREF = '/quick-launch/quick-launch.css';
    var CSS_VER  = 17;   // keep in sync with the ?v=N on every quick-launch.css reference

    // ---- Curated icon set ------------------------------------------------
    // ~300 emoji organized by category. The modal scrolls; category headers
    // are sticky so you don't lose your place. Add to a category if you want
    // a new one -- keep individual categories under ~30 so they don't dwarf
    // the others.
    var ICONS = [
        // First thing the picker shows (Story 2464 rework): the DISTINCTIVE set.
        // Tony's complaint was that the picker leads with generic icons and the
        // one-of-a-kind glyphs are buried ten pages deep in the catalog. These
        // are chosen to be visually unmistakable at pin size -- no two alike.
        { cat: 'Distinctive', items: [
            '\u2696\uFE0F','\uD83E\uDDED','\u2693','\uD83E\uDDF2','\uD83D\uDEE1\uFE0F','\uD83D\uDDE1\uFE0F','\u2694\uFE0F','\uD83D\uDD31','\uD83C\uDFFA','\uD83E\uDEB6','\uD83D\uDDFF','\uD83D\uDC09','\uD83D\uDC32','\uD83E\uDD96','\uD83E\uDD95','\uD83E\uDD9A','\uD83C\uDF0B','\uD83E\uDE90','\u2604\uFE0F','\uD83D\uDEF0\uFE0F','\uD83E\uDDCA','\uD83E\uDE9E','\uD83E\uDE9C','\uD83E\uDE9D','\uD83E\uDDFF','\uD83E\uDEAC','\uD83C\uDFA0','\uD83C\uDFAA','\uD83C\uDFA1','\uD83E\uDDF8','\uD83E\uDE86','\u26F2','\uD83D\uDCEF','\uD83E\uDDF5','\uD83E\uDEA1','\uD83E\uDE81'
        ]},
        { cat: 'Faces & moods', items: [
            '\uD83D\uDE00','\uD83D\uDE03','\uD83D\uDE04','\uD83D\uDE01','\uD83D\uDE02','\uD83E\uDD23','\uD83D\uDE0A','\uD83D\uDE07','\uD83D\uDE42','\uD83D\uDE09','\uD83D\uDE0D','\uD83E\uDD70','\uD83D\uDE18','\uD83D\uDE0E','\uD83E\uDD29','\uD83E\uDD73','\uD83E\uDD14','\uD83D\uDE10','\uD83D\uDE34','\uD83D\uDE22','\uD83D\uDE2D','\uD83D\uDE24','\uD83D\uDE21','\uD83E\uDD2F','\uD83D\uDE31','\uD83E\uDD7A','\uD83D\uDE2C','\uD83D\uDE44','\uD83D\uDE0F','\uD83E\uDD2B'
        ]},
        { cat: 'Stars & hearts', items: [
            '\u2B50','\uD83C\uDF1F','\u2728','\uD83D\uDCAB','\uD83C\uDF20','\u2764\uFE0F','\uD83E\uDDE1','\uD83D\uDC9B','\uD83D\uDC9A','\uD83D\uDC99','\uD83D\uDC9C','\uD83D\uDDA4','\uD83E\uDD0D','\uD83E\uDD0E','\uD83D\uDC96','\uD83D\uDC97','\uD83D\uDC93','\uD83D\uDC9E','\uD83D\uDC95','\uD83D\uDC94'
        ]},
        { cat: 'Lists & tasks', items: [
            '\u2705','\u2611\uFE0F','\u2714\uFE0F','\u274E','\u274C','\u2716\uFE0F','\u2B1C','\u2B1B','\uD83D\uDD32','\uD83D\uDD33','\uD83D\uDCCB','\uD83D\uDCDD','\uD83D\uDCCC','\uD83D\uDCCD','\uD83D\uDDD2\uFE0F','\uD83D\uDDD3\uFE0F','\uD83D\uDCC7','\uD83D\uDCD1','\uD83D\uDCF0','\uD83D\uDCDC','\uD83D\uDCC4','\uD83D\uDCC3'
        ]},
        { cat: 'Time', items: [
            '\u23F0','\u23F1\uFE0F','\u23F2\uFE0F','\uD83D\uDD70\uFE0F','\u23F3','\u231B','\uD83D\uDCC5','\uD83D\uDCC6','\uD83C\uDF05','\uD83C\uDF04','\uD83C\uDF07','\uD83C\uDF06','\uD83C\uDF03','\uD83C\uDF0C','\uD83C\uDF1E','\uD83C\uDF1D','\uD83C\uDF1A'
        ]},
        { cat: 'Sleep & weather', items: [
            '\uD83C\uDF19','\uD83D\uDE34','\uD83D\uDCA4','\uD83D\uDECC','\uD83D\uDECF\uFE0F','\u2600\uFE0F','\uD83C\uDF24\uFE0F','\u26C5','\uD83C\uDF25\uFE0F','\u2601\uFE0F','\uD83C\uDF26\uFE0F','\uD83C\uDF27\uFE0F','\u26C8\uFE0F','\uD83C\uDF29\uFE0F','\uD83C\uDF28\uFE0F','\uD83C\uDF2A\uFE0F','\uD83C\uDF2B\uFE0F','\uD83C\uDF08','\u2614','\uD83D\uDCA7','\uD83C\uDF0A','\u2744\uFE0F','\u26C4'
        ]},
        { cat: 'Money & business', items: [
            '\uD83D\uDCB0','\uD83D\uDCB5','\uD83D\uDCB4','\uD83D\uDCB6','\uD83D\uDCB7','\uD83D\uDCB8','\uD83D\uDCB3','\uD83E\uDDFE','\uD83D\uDC8E','\uD83C\uDFE6','\uD83D\uDCCA','\uD83D\uDCC8','\uD83D\uDCC9','\uD83D\uDCB9','\uD83E\uDE99','\uD83D\uDCBC','\uD83D\uDDC3\uFE0F','\uD83D\uDCC7','\uD83D\uDDC4\uFE0F','\uD83C\uDFE7'
        ]},
        { cat: 'Communications', items: [
            '\uD83D\uDCE7','\uD83D\uDCE8','\uD83D\uDCE9','\u2709\uFE0F','\uD83D\uDCE4','\uD83D\uDCE5','\uD83D\uDCAC','\uD83D\uDDE8\uFE0F','\uD83D\uDDEF\uFE0F','\uD83D\uDCAD','\uD83D\uDCDE','\u260E\uFE0F','\uD83D\uDCF1','\uD83D\uDCF2','\uD83D\uDCE1','\uD83D\uDCDF','\uD83D\uDCE0','\uD83D\uDCE3','\uD83D\uDCE2','\uD83D\uDD14','\uD83D\uDD15'
        ]},
        { cat: 'Files & books', items: [
            '\uD83D\uDCC1','\uD83D\uDCC2','\uD83D\uDDC2\uFE0F','\uD83D\uDDC3\uFE0F','\uD83D\uDDC4\uFE0F','\uD83D\uDCC7','\uD83D\uDCD2','\uD83D\uDCD3','\uD83D\uDCD4','\uD83D\uDCD5','\uD83D\uDCD6','\uD83D\uDCD7','\uD83D\uDCD8','\uD83D\uDCD9','\uD83D\uDCDA','\uD83D\uDD16','\uD83D\uDCCE','\uD83D\uDD87\uFE0F','\uD83D\uDCD0','\uD83D\uDCCF','\u2702\uFE0F','\uD83D\uDCCC'
        ]},
        { cat: 'Home & places', items: [
            '\uD83C\uDFE0','\uD83C\uDFE1','\uD83C\uDFE2','\uD83C\uDFEC','\uD83C\uDFED','\uD83C\uDFE5','\uD83C\uDFEA','\uD83C\uDFEB','\uD83C\uDFDB\uFE0F','\uD83C\uDFEF','\uD83C\uDFF0','\u26EA','\uD83D\uDD4C','\uD83D\uDD4D','\uD83D\uDED5','\u26E9\uFE0F','\uD83D\uDDFC','\uD83D\uDDFD','\uD83C\uDFD7\uFE0F','\uD83D\uDED6','\uD83C\uDFDF\uFE0F','\uD83C\uDFE8'
        ]},
        { cat: 'Travel & transit', items: [
            '\uD83D\uDE97','\uD83D\uDE95','\uD83D\uDE99','\uD83D\uDE8C','\uD83D\uDE8E','\uD83C\uDFCE\uFE0F','\uD83D\uDE93','\uD83D\uDE91','\uD83D\uDE92','\uD83D\uDE90','\uD83D\uDE9A','\uD83D\uDE9B','\u2708\uFE0F','\uD83D\uDE82','\uD83D\uDE86','\uD83D\uDE87','\uD83D\uDE89','\uD83D\uDEB2','\uD83D\uDEF4','\uD83D\uDEF5','\u26F5','\uD83D\uDE80','\uD83D\uDEF8','\uD83D\uDE81'
        ]},
        { cat: 'Food & drink', items: [
            '\uD83C\uDF54','\uD83C\uDF55','\uD83C\uDF5F','\uD83C\uDF2D','\uD83E\uDD6A','\uD83C\uDF2E','\uD83C\uDF2F','\uD83E\uDD57','\uD83C\uDF7F','\uD83E\uDD68','\uD83E\uDD53','\uD83E\uDD69','\uD83C\uDF57','\uD83C\uDF56','\uD83C\uDF73','\uD83C\uDF5E','\uD83E\uDDC0','\uD83C\uDF4E','\uD83C\uDF4C','\uD83C\uDF53','\uD83C\uDF4A','\uD83E\uDD51','\uD83E\uDD55','\uD83C\uDF46','\uD83C\uDF45','\u2615','\uD83C\uDF75','\uD83C\uDF77','\uD83C\uDF7A','\uD83E\uDD64'
        ]},
        { cat: 'Activities & art', items: [
            '\uD83C\uDFAE','\uD83C\uDFB2','\uD83C\uDFAF','\uD83C\uDFB3','\uD83C\uDFB0','\uD83E\uDDE9','\u265F\uFE0F','\uD83C\uDFA8','\uD83C\uDFAD','\uD83C\uDFAC','\uD83C\uDFA4','\uD83C\uDFB5','\uD83C\uDFB6','\uD83C\uDFB7','\uD83C\uDFB8','\uD83C\uDFB9','\uD83C\uDFBA','\uD83C\uDFBB','\uD83E\uDD41','\uD83C\uDFBC','\uD83C\uDF99\uFE0F','\uD83C\uDFA7','\uD83D\uDCFB','\uD83C\uDF9F\uFE0F','\uD83C\uDFAB'
        ]},
        { cat: 'Health & body', items: [
            '\uD83C\uDFE5','\uD83D\uDC8A','\uD83D\uDC89','\uD83E\uDE7A','\uD83E\uDE79','\uD83E\uDE7B','\uD83E\uDDB7','\uD83E\uDDEC','\uD83E\uDDE0','\uD83E\uDEC0','\uD83E\uDEC1','\uD83E\uDDB4','\uD83E\uDE78','\u2695\uFE0F','\uD83D\uDE91','\uD83E\uDDF4','\uD83E\uDDFC','\uD83E\uDEA5','\uD83D\uDE37','\uD83E\uDD12'
        ]},
        { cat: 'Holidays & events', items: [
            '\uD83C\uDF89','\uD83C\uDF8A','\uD83C\uDF82','\uD83C\uDF81','\uD83C\uDF88','\uD83C\uDF84','\uD83C\uDF83','\uD83C\uDF86','\uD83C\uDF87','\uD83E\uDDE8','\uD83C\uDF8F','\uD83C\uDF90','\uD83C\uDF91','\uD83E\uDDE7','\uD83C\uDF80','\uD83D\uDD6F\uFE0F','\uD83E\uDD42','\uD83C\uDF7E','\uD83D\uDC8D','\uD83D\uDC70'
        ]},
        { cat: 'Sports & outdoors', items: [
            '\u26BD','\uD83C\uDFC0','\uD83C\uDFC8','\u26BE','\uD83E\uDD4E','\uD83C\uDFBE','\uD83C\uDFD0','\uD83C\uDFC9','\uD83C\uDFB1','\uD83C\uDFD3','\uD83C\uDFF8','\uD83E\uDD45','\u26F3','\uD83C\uDFF9','\uD83C\uDFA3','\uD83E\uDD4A','\uD83E\uDD4B','\u26F8\uFE0F','\uD83C\uDFBF','\uD83D\uDEF7','\uD83C\uDFC2','\uD83C\uDFC4','\uD83C\uDFCA','\uD83D\uDEB4','\uD83D\uDEB5','\uD83E\uDD38'
        ]},
        { cat: 'Tools & gear', items: [
            '\uD83D\uDD27','\uD83D\uDD28','\u2692\uFE0F','\uD83D\uDEE0\uFE0F','\u26CF\uFE0F','\uD83D\uDD29','\u2699\uFE0F','\uD83E\uDDF0','\uD83E\uDE9B','\uD83E\uDE9A','\uD83D\uDD0C','\uD83D\uDD0B','\uD83D\uDD26','\uD83D\uDCA1','\uD83D\uDD6F\uFE0F','\uD83E\uDE94','\uD83D\uDD0D','\uD83D\uDD0E','\uD83D\uDD11','\uD83D\uDDDD\uFE0F','\uD83D\uDD12','\uD83D\uDD13','\uD83D\uDD0F','\uD83D\uDD10'
        ]},
        { cat: 'Tech & devices', items: [
            '\uD83D\uDCBB','\uD83D\uDDA5\uFE0F','\uD83D\uDDA8\uFE0F','\u2328\uFE0F','\uD83D\uDDB1\uFE0F','\uD83D\uDCBE','\uD83D\uDCBF','\uD83D\uDCC0','\uD83D\uDD79\uFE0F','\uD83D\uDCF7','\uD83D\uDCF8','\uD83D\uDCF9','\uD83C\uDFA5','\uD83D\uDCFA','\uD83D\uDCFB','\u23EF\uFE0F','\u23EE\uFE0F','\u23ED\uFE0F','\uD83D\uDCE1','\uD83D\uDD2D','\uD83D\uDD2C'
        ]},
        { cat: 'Animals & nature', items: [
            '\uD83D\uDC36','\uD83D\uDC31','\uD83D\uDC2D','\uD83D\uDC39','\uD83D\uDC30','\uD83E\uDD8A','\uD83D\uDC3B','\uD83D\uDC3C','\uD83D\uDC28','\uD83D\uDC2F','\uD83E\uDD81','\uD83D\uDC2E','\uD83D\uDC37','\uD83D\uDC38','\uD83D\uDC35','\uD83D\uDC14','\uD83D\uDC27','\uD83D\uDC26','\uD83D\uDC24','\uD83E\uDD86','\uD83E\uDD89','\uD83E\uDD85','\uD83D\uDC3A','\uD83D\uDC34','\uD83E\uDD84','\uD83D\uDC1D','\uD83D\uDC22','\uD83D\uDC0D','\uD83D\uDC20','\uD83D\uDC19'
        ]},
        { cat: 'Plants & nature', items: [
            '\uD83C\uDF32','\uD83C\uDF33','\uD83C\uDF34','\uD83C\uDF35','\uD83C\uDF3E','\uD83C\uDF3F','\uD83C\uDF40','\uD83C\uDF41','\uD83C\uDF42','\uD83C\uDF43','\uD83C\uDF37','\uD83C\uDF39','\uD83C\uDF3A','\uD83C\uDF38','\uD83C\uDF3C','\uD83C\uDF3B','\uD83D\uDC90','\uD83E\uDEB4','\uD83C\uDF31','\uD83C\uDF44','\uD83C\uDF30','\uD83C\uDF0E','\uD83C\uDF0D','\uD83C\uDF0F'
        ]},
        { cat: 'People & body', items: [
            '\uD83D\uDC64','\uD83D\uDC65','\uD83E\uDDD1','\uD83D\uDC76','\uD83E\uDDD2','\uD83D\uDC66','\uD83D\uDC67','\uD83D\uDC68','\uD83D\uDC69','\uD83D\uDC74','\uD83D\uDC75','\uD83D\uDC6E','\uD83E\uDDD1\u200D\u2695\uFE0F','\uD83E\uDDD1\u200D\uD83C\uDF73','\uD83E\uDDD1\u200D\uD83C\uDF3E','\uD83E\uDDD1\u200D\uD83C\uDF93','\uD83E\uDDD1\u200D\uD83C\uDFEB','\uD83E\uDDD1\u200D\uD83D\uDCBC','\uD83E\uDDD1\u200D\uD83D\uDD27','\uD83D\uDC4B','\u270B','\uD83E\uDD1A','\u270A','\uD83D\uDC4A','\uD83D\uDC4D','\uD83D\uDC4E','\uD83D\uDE4F','\uD83D\uDC4C','\uD83D\uDCAA','\uD83E\uDDE0','\uD83D\uDC40','\uD83D\uDC41\uFE0F','\uD83D\uDC42','\uD83D\uDC43','\uD83E\uDD14','\uD83D\uDE00','\uD83D\uDE0A','\uD83D\uDE0E','\uD83E\uDD73','\uD83E\uDD29'
        ]},
        { cat: 'Symbols & flags', items: [
            '\u26A1','\uD83D\uDD25','\u2744\uFE0F','\uD83D\uDCA5','\uD83D\uDCA2','\uD83D\uDCA6','\uD83D\uDCA8','\uD83D\uDD73\uFE0F','\uD83D\uDCA3','\uD83D\uDCAF','\uD83C\uDD97','\uD83C\uDD95','\uD83C\uDD99','\uD83C\uDD92','\uD83C\uDD93','\uD83C\uDD96','\uD83C\uDD98','\u2139\uFE0F','\uD83D\uDEAB','\u26A0\uFE0F','\uD83D\uDEA7','\u26D4','\uD83D\uDED1','\u2733\uFE0F','\u2747\uFE0F','\u2753','\u2754','\u2755','\u203C\uFE0F','\u2049\uFE0F','\uD83D\uDD06','\uD83D\uDD05','\uD83D\uDD04','\uD83D\uDD01','\uD83D\uDD02','\uD83D\uDD00','\u25B6\uFE0F','\u25C0\uFE0F','\u23F8\uFE0F','\u23F9\uFE0F','\uD83C\uDFC1','\uD83D\uDEA9','\uD83C\uDFF4','\uD83C\uDFF3\uFE0F'
        ]}
    ];

    // ---- Generated icon pages 2-10 (ASCII escapes) ----------------------
    // Source: Unicode emoji-test.txt v15.1, fully-qualified emoji, skin-tone
    // variants excluded. Pure \uHHHH escapes -- no raw non-ASCII bytes.
    var ICON_PAGES_EXTRA = [
        { name: 'Smileys', cats: [
            { cat: 'Face smiling', items: ['\uD83D\uDE00','\uD83D\uDE03','\uD83D\uDE04','\uD83D\uDE01','\uD83D\uDE06','\uD83D\uDE05','\uD83E\uDD23','\uD83D\uDE02','\uD83D\uDE42','\uD83D\uDE43','\uD83E\uDEE0','\uD83D\uDE09','\uD83D\uDE0A','\uD83D\uDE07'] },
            { cat: 'Face affection', items: ['\uD83E\uDD70','\uD83D\uDE0D','\uD83E\uDD29','\uD83D\uDE18','\uD83D\uDE17','\u263A\uFE0F','\uD83D\uDE1A','\uD83D\uDE19','\uD83E\uDD72'] },
            { cat: 'Face tongue', items: ['\uD83D\uDE0B','\uD83D\uDE1B','\uD83D\uDE1C','\uD83E\uDD2A','\uD83D\uDE1D','\uD83E\uDD11'] },
            { cat: 'Face hand', items: ['\uD83E\uDD17','\uD83E\uDD2D','\uD83E\uDEE2','\uD83E\uDEE3','\uD83E\uDD2B','\uD83E\uDD14','\uD83E\uDEE1'] },
            { cat: 'Face neutral skeptical', items: ['\uD83E\uDD10','\uD83E\uDD28','\uD83D\uDE10','\uD83D\uDE11','\uD83D\uDE36','\uD83E\uDEE5','\uD83D\uDE36\u200D\uD83C\uDF2B\uFE0F','\uD83D\uDE0F','\uD83D\uDE12','\uD83D\uDE44','\uD83D\uDE2C','\uD83D\uDE2E\u200D\uD83D\uDCA8','\uD83E\uDD25','\uD83E\uDEE8','\uD83D\uDE42\u200D\u2194\uFE0F','\uD83D\uDE42\u200D\u2195\uFE0F'] },
            { cat: 'Face sleepy', items: ['\uD83D\uDE0C','\uD83D\uDE14','\uD83D\uDE2A','\uD83E\uDD24','\uD83D\uDE34'] },
            { cat: 'Face unwell', items: ['\uD83D\uDE37','\uD83E\uDD12','\uD83E\uDD15','\uD83E\uDD22','\uD83E\uDD2E','\uD83E\uDD27','\uD83E\uDD75','\uD83E\uDD76','\uD83E\uDD74','\uD83D\uDE35','\uD83D\uDE35\u200D\uD83D\uDCAB','\uD83E\uDD2F'] },
            { cat: 'Face hat', items: ['\uD83E\uDD20','\uD83E\uDD73','\uD83E\uDD78'] },
            { cat: 'Face glasses', items: ['\uD83D\uDE0E','\uD83E\uDD13','\uD83E\uDDD0'] },
            { cat: 'Face concerned', items: ['\uD83D\uDE15','\uD83E\uDEE4','\uD83D\uDE1F','\uD83D\uDE41','\u2639\uFE0F','\uD83D\uDE2E','\uD83D\uDE2F','\uD83D\uDE32','\uD83D\uDE33','\uD83E\uDD7A','\uD83E\uDD79','\uD83D\uDE26','\uD83D\uDE27','\uD83D\uDE28','\uD83D\uDE30','\uD83D\uDE25','\uD83D\uDE22','\uD83D\uDE2D','\uD83D\uDE31','\uD83D\uDE16','\uD83D\uDE23','\uD83D\uDE1E','\uD83D\uDE13','\uD83D\uDE29','\uD83D\uDE2B','\uD83E\uDD71'] },
            { cat: 'Face negative', items: ['\uD83D\uDE24','\uD83D\uDE21','\uD83D\uDE20','\uD83E\uDD2C','\uD83D\uDE08','\uD83D\uDC7F','\uD83D\uDC80','\u2620\uFE0F'] },
            { cat: 'Face costume', items: ['\uD83D\uDCA9','\uD83E\uDD21','\uD83D\uDC79','\uD83D\uDC7A','\uD83D\uDC7B','\uD83D\uDC7D','\uD83D\uDC7E','\uD83E\uDD16'] },
            { cat: 'Cat face', items: ['\uD83D\uDE3A','\uD83D\uDE38','\uD83D\uDE39','\uD83D\uDE3B','\uD83D\uDE3C','\uD83D\uDE3D','\uD83D\uDE40','\uD83D\uDE3F','\uD83D\uDE3E'] },
            { cat: 'Monkey face', items: ['\uD83D\uDE48','\uD83D\uDE49','\uD83D\uDE4A'] },
            { cat: 'Heart', items: ['\uD83D\uDC8C','\uD83D\uDC98','\uD83D\uDC9D','\uD83D\uDC96','\uD83D\uDC97','\uD83D\uDC93','\uD83D\uDC9E','\uD83D\uDC95','\uD83D\uDC9F','\u2763\uFE0F','\uD83D\uDC94','\u2764\uFE0F\u200D\uD83D\uDD25','\u2764\uFE0F\u200D\uD83E\uDE79','\u2764\uFE0F','\uD83E\uDE77','\uD83E\uDDE1','\uD83D\uDC9B','\uD83D\uDC9A','\uD83D\uDC99','\uD83E\uDE75','\uD83D\uDC9C','\uD83E\uDD0E','\uD83D\uDDA4','\uD83E\uDE76','\uD83E\uDD0D'] },
            { cat: 'Emotion', items: ['\uD83D\uDC8B','\uD83D\uDCAF','\uD83D\uDCA2','\uD83D\uDCA5','\uD83D\uDCAB','\uD83D\uDCA6','\uD83D\uDCA8','\uD83D\uDD73\uFE0F','\uD83D\uDCAC','\uD83D\uDC41\uFE0F\u200D\uD83D\uDDE8\uFE0F','\uD83D\uDDE8\uFE0F','\uD83D\uDDEF\uFE0F','\uD83D\uDCAD','\uD83D\uDCA4'] },
        ]},
        { name: 'People', cats: [
            { cat: 'Hand fingers open', items: ['\uD83D\uDC4B','\uD83E\uDD1A','\uD83D\uDD90\uFE0F','\u270B','\uD83D\uDD96','\uD83E\uDEF1','\uD83E\uDEF2','\uD83E\uDEF3','\uD83E\uDEF4','\uD83E\uDEF7','\uD83E\uDEF8'] },
            { cat: 'Hand fingers partial', items: ['\uD83D\uDC4C','\uD83E\uDD0C','\uD83E\uDD0F','\u270C\uFE0F','\uD83E\uDD1E','\uD83E\uDEF0','\uD83E\uDD1F','\uD83E\uDD18','\uD83E\uDD19'] },
            { cat: 'Hand single finger', items: ['\uD83D\uDC48','\uD83D\uDC49','\uD83D\uDC46','\uD83D\uDD95','\uD83D\uDC47','\u261D\uFE0F','\uD83E\uDEF5'] },
            { cat: 'Hand fingers closed', items: ['\uD83D\uDC4D','\uD83D\uDC4E','\u270A','\uD83D\uDC4A','\uD83E\uDD1B','\uD83E\uDD1C'] },
            { cat: 'Hands', items: ['\uD83D\uDC4F','\uD83D\uDE4C','\uD83E\uDEF6','\uD83D\uDC50','\uD83E\uDD32','\uD83E\uDD1D','\uD83D\uDE4F'] },
            { cat: 'Hand prop', items: ['\u270D\uFE0F','\uD83D\uDC85','\uD83E\uDD33'] },
            { cat: 'Body parts', items: ['\uD83D\uDCAA','\uD83E\uDDBE','\uD83E\uDDBF','\uD83E\uDDB5','\uD83E\uDDB6','\uD83D\uDC42','\uD83E\uDDBB','\uD83D\uDC43','\uD83E\uDDE0','\uD83E\uDEC0','\uD83E\uDEC1','\uD83E\uDDB7','\uD83E\uDDB4','\uD83D\uDC40','\uD83D\uDC41\uFE0F','\uD83D\uDC45','\uD83D\uDC44','\uD83E\uDEE6'] },
            { cat: 'Person', items: ['\uD83D\uDC76','\uD83E\uDDD2','\uD83D\uDC66','\uD83D\uDC67','\uD83E\uDDD1','\uD83D\uDC71','\uD83D\uDC68','\uD83E\uDDD4','\uD83E\uDDD4\u200D\u2642\uFE0F','\uD83E\uDDD4\u200D\u2640\uFE0F','\uD83D\uDC68\u200D\uD83E\uDDB0','\uD83D\uDC68\u200D\uD83E\uDDB1','\uD83D\uDC68\u200D\uD83E\uDDB3','\uD83D\uDC68\u200D\uD83E\uDDB2','\uD83D\uDC69','\uD83D\uDC69\u200D\uD83E\uDDB0','\uD83E\uDDD1\u200D\uD83E\uDDB0','\uD83D\uDC69\u200D\uD83E\uDDB1','\uD83E\uDDD1\u200D\uD83E\uDDB1','\uD83D\uDC69\u200D\uD83E\uDDB3','\uD83E\uDDD1\u200D\uD83E\uDDB3','\uD83D\uDC69\u200D\uD83E\uDDB2','\uD83E\uDDD1\u200D\uD83E\uDDB2','\uD83D\uDC71\u200D\u2640\uFE0F','\uD83D\uDC71\u200D\u2642\uFE0F','\uD83E\uDDD3','\uD83D\uDC74','\uD83D\uDC75'] },
            { cat: 'Person gesture', items: ['\uD83D\uDE4D','\uD83D\uDE4D\u200D\u2642\uFE0F','\uD83D\uDE4D\u200D\u2640\uFE0F','\uD83D\uDE4E','\uD83D\uDE4E\u200D\u2642\uFE0F','\uD83D\uDE4E\u200D\u2640\uFE0F','\uD83D\uDE45','\uD83D\uDE45\u200D\u2642\uFE0F','\uD83D\uDE45\u200D\u2640\uFE0F','\uD83D\uDE46','\uD83D\uDE46\u200D\u2642\uFE0F','\uD83D\uDE46\u200D\u2640\uFE0F','\uD83D\uDC81','\uD83D\uDC81\u200D\u2642\uFE0F','\uD83D\uDC81\u200D\u2640\uFE0F','\uD83D\uDE4B','\uD83D\uDE4B\u200D\u2642\uFE0F','\uD83D\uDE4B\u200D\u2640\uFE0F','\uD83E\uDDCF','\uD83E\uDDCF\u200D\u2642\uFE0F','\uD83E\uDDCF\u200D\u2640\uFE0F','\uD83D\uDE47','\uD83D\uDE47\u200D\u2642\uFE0F','\uD83D\uDE47\u200D\u2640\uFE0F','\uD83E\uDD26','\uD83E\uDD26\u200D\u2642\uFE0F','\uD83E\uDD26\u200D\u2640\uFE0F','\uD83E\uDD37','\uD83E\uDD37\u200D\u2642\uFE0F','\uD83E\uDD37\u200D\u2640\uFE0F'] },
            { cat: 'Person role', items: ['\uD83E\uDDD1\u200D\u2695\uFE0F','\uD83D\uDC68\u200D\u2695\uFE0F','\uD83D\uDC69\u200D\u2695\uFE0F','\uD83E\uDDD1\u200D\uD83C\uDF93','\uD83D\uDC68\u200D\uD83C\uDF93','\uD83D\uDC69\u200D\uD83C\uDF93','\uD83E\uDDD1\u200D\uD83C\uDFEB','\uD83D\uDC68\u200D\uD83C\uDFEB','\uD83D\uDC69\u200D\uD83C\uDFEB','\uD83E\uDDD1\u200D\u2696\uFE0F','\uD83D\uDC68\u200D\u2696\uFE0F','\uD83D\uDC69\u200D\u2696\uFE0F','\uD83E\uDDD1\u200D\uD83C\uDF3E','\uD83D\uDC68\u200D\uD83C\uDF3E','\uD83D\uDC69\u200D\uD83C\uDF3E','\uD83E\uDDD1\u200D\uD83C\uDF73','\uD83D\uDC68\u200D\uD83C\uDF73','\uD83D\uDC69\u200D\uD83C\uDF73','\uD83E\uDDD1\u200D\uD83D\uDD27','\uD83D\uDC68\u200D\uD83D\uDD27','\uD83D\uDC69\u200D\uD83D\uDD27','\uD83E\uDDD1\u200D\uD83C\uDFED','\uD83D\uDC68\u200D\uD83C\uDFED','\uD83D\uDC69\u200D\uD83C\uDFED','\uD83E\uDDD1\u200D\uD83D\uDCBC','\uD83D\uDC68\u200D\uD83D\uDCBC','\uD83D\uDC69\u200D\uD83D\uDCBC','\uD83E\uDDD1\u200D\uD83D\uDD2C','\uD83D\uDC68\u200D\uD83D\uDD2C','\uD83D\uDC69\u200D\uD83D\uDD2C','\uD83E\uDDD1\u200D\uD83D\uDCBB','\uD83D\uDC68\u200D\uD83D\uDCBB','\uD83D\uDC69\u200D\uD83D\uDCBB','\uD83E\uDDD1\u200D\uD83C\uDFA4','\uD83D\uDC68\u200D\uD83C\uDFA4','\uD83D\uDC69\u200D\uD83C\uDFA4','\uD83E\uDDD1\u200D\uD83C\uDFA8','\uD83D\uDC68\u200D\uD83C\uDFA8','\uD83D\uDC69\u200D\uD83C\uDFA8','\uD83E\uDDD1\u200D\u2708\uFE0F','\uD83D\uDC68\u200D\u2708\uFE0F','\uD83D\uDC69\u200D\u2708\uFE0F','\uD83E\uDDD1\u200D\uD83D\uDE80','\uD83D\uDC68\u200D\uD83D\uDE80','\uD83D\uDC69\u200D\uD83D\uDE80','\uD83E\uDDD1\u200D\uD83D\uDE92','\uD83D\uDC68\u200D\uD83D\uDE92','\uD83D\uDC69\u200D\uD83D\uDE92','\uD83D\uDC6E','\uD83D\uDC6E\u200D\u2642\uFE0F','\uD83D\uDC6E\u200D\u2640\uFE0F','\uD83D\uDD75\uFE0F','\uD83D\uDD75\uFE0F\u200D\u2642\uFE0F','\uD83D\uDD75\uFE0F\u200D\u2640\uFE0F','\uD83D\uDC82','\uD83D\uDC82\u200D\u2642\uFE0F','\uD83D\uDC82\u200D\u2640\uFE0F','\uD83E\uDD77','\uD83D\uDC77','\uD83D\uDC77\u200D\u2642\uFE0F','\uD83D\uDC77\u200D\u2640\uFE0F','\uD83E\uDEC5','\uD83E\uDD34','\uD83D\uDC78','\uD83D\uDC73','\uD83D\uDC73\u200D\u2642\uFE0F','\uD83D\uDC73\u200D\u2640\uFE0F','\uD83D\uDC72','\uD83E\uDDD5','\uD83E\uDD35','\uD83E\uDD35\u200D\u2642\uFE0F','\uD83E\uDD35\u200D\u2640\uFE0F','\uD83D\uDC70','\uD83D\uDC70\u200D\u2642\uFE0F','\uD83D\uDC70\u200D\u2640\uFE0F','\uD83E\uDD30','\uD83E\uDEC3','\uD83E\uDEC4','\uD83E\uDD31','\uD83D\uDC69\u200D\uD83C\uDF7C','\uD83D\uDC68\u200D\uD83C\uDF7C','\uD83E\uDDD1\u200D\uD83C\uDF7C'] },
            { cat: 'Person fantasy', items: ['\uD83D\uDC7C','\uD83C\uDF85','\uD83E\uDD36','\uD83E\uDDD1\u200D\uD83C\uDF84','\uD83E\uDDB8','\uD83E\uDDB8\u200D\u2642\uFE0F','\uD83E\uDDB8\u200D\u2640\uFE0F','\uD83E\uDDB9','\uD83E\uDDB9\u200D\u2642\uFE0F','\uD83E\uDDB9\u200D\u2640\uFE0F','\uD83E\uDDD9','\uD83E\uDDD9\u200D\u2642\uFE0F','\uD83E\uDDD9\u200D\u2640\uFE0F','\uD83E\uDDDA','\uD83E\uDDDA\u200D\u2642\uFE0F','\uD83E\uDDDA\u200D\u2640\uFE0F','\uD83E\uDDDB','\uD83E\uDDDB\u200D\u2642\uFE0F','\uD83E\uDDDB\u200D\u2640\uFE0F','\uD83E\uDDDC','\uD83E\uDDDC\u200D\u2642\uFE0F','\uD83E\uDDDC\u200D\u2640\uFE0F','\uD83E\uDDDD','\uD83E\uDDDD\u200D\u2642\uFE0F','\uD83E\uDDDD\u200D\u2640\uFE0F','\uD83E\uDDDE','\uD83E\uDDDE\u200D\u2642\uFE0F','\uD83E\uDDDE\u200D\u2640\uFE0F','\uD83E\uDDDF','\uD83E\uDDDF\u200D\u2642\uFE0F','\uD83E\uDDDF\u200D\u2640\uFE0F','\uD83E\uDDCC'] },
            { cat: 'Person activity', items: ['\uD83D\uDC86','\uD83D\uDC86\u200D\u2642\uFE0F','\uD83D\uDC86\u200D\u2640\uFE0F','\uD83D\uDC87','\uD83D\uDC87\u200D\u2642\uFE0F','\uD83D\uDC87\u200D\u2640\uFE0F','\uD83D\uDEB6','\uD83D\uDEB6\u200D\u2642\uFE0F','\uD83D\uDEB6\u200D\u2640\uFE0F','\uD83D\uDEB6\u200D\u27A1\uFE0F','\uD83D\uDEB6\u200D\u2640\uFE0F\u200D\u27A1\uFE0F','\uD83D\uDEB6\u200D\u2642\uFE0F\u200D\u27A1\uFE0F','\uD83E\uDDCD','\uD83E\uDDCD\u200D\u2642\uFE0F','\uD83E\uDDCD\u200D\u2640\uFE0F','\uD83E\uDDCE','\uD83E\uDDCE\u200D\u2642\uFE0F','\uD83E\uDDCE\u200D\u2640\uFE0F','\uD83E\uDDCE\u200D\u27A1\uFE0F','\uD83E\uDDCE\u200D\u2640\uFE0F\u200D\u27A1\uFE0F','\uD83E\uDDCE\u200D\u2642\uFE0F\u200D\u27A1\uFE0F','\uD83E\uDDD1\u200D\uD83E\uDDAF','\uD83E\uDDD1\u200D\uD83E\uDDAF\u200D\u27A1\uFE0F','\uD83D\uDC68\u200D\uD83E\uDDAF','\uD83D\uDC68\u200D\uD83E\uDDAF\u200D\u27A1\uFE0F','\uD83D\uDC69\u200D\uD83E\uDDAF','\uD83D\uDC69\u200D\uD83E\uDDAF\u200D\u27A1\uFE0F','\uD83E\uDDD1\u200D\uD83E\uDDBC','\uD83E\uDDD1\u200D\uD83E\uDDBC\u200D\u27A1\uFE0F','\uD83D\uDC68\u200D\uD83E\uDDBC','\uD83D\uDC68\u200D\uD83E\uDDBC\u200D\u27A1\uFE0F','\uD83D\uDC69\u200D\uD83E\uDDBC','\uD83D\uDC69\u200D\uD83E\uDDBC\u200D\u27A1\uFE0F','\uD83E\uDDD1\u200D\uD83E\uDDBD','\uD83E\uDDD1\u200D\uD83E\uDDBD\u200D\u27A1\uFE0F','\uD83D\uDC68\u200D\uD83E\uDDBD','\uD83D\uDC68\u200D\uD83E\uDDBD\u200D\u27A1\uFE0F','\uD83D\uDC69\u200D\uD83E\uDDBD','\uD83D\uDC69\u200D\uD83E\uDDBD\u200D\u27A1\uFE0F','\uD83C\uDFC3','\uD83C\uDFC3\u200D\u2642\uFE0F','\uD83C\uDFC3\u200D\u2640\uFE0F','\uD83C\uDFC3\u200D\u27A1\uFE0F','\uD83C\uDFC3\u200D\u2640\uFE0F\u200D\u27A1\uFE0F','\uD83C\uDFC3\u200D\u2642\uFE0F\u200D\u27A1\uFE0F','\uD83D\uDC83','\uD83D\uDD7A','\uD83D\uDD74\uFE0F','\uD83D\uDC6F','\uD83D\uDC6F\u200D\u2642\uFE0F','\uD83D\uDC6F\u200D\u2640\uFE0F','\uD83E\uDDD6','\uD83E\uDDD6\u200D\u2642\uFE0F','\uD83E\uDDD6\u200D\u2640\uFE0F','\uD83E\uDDD7','\uD83E\uDDD7\u200D\u2642\uFE0F','\uD83E\uDDD7\u200D\u2640\uFE0F'] },
            { cat: 'Person sport', items: ['\uD83E\uDD3A','\uD83C\uDFC7','\u26F7\uFE0F','\uD83C\uDFC2','\uD83C\uDFCC\uFE0F','\uD83C\uDFCC\uFE0F\u200D\u2642\uFE0F','\uD83C\uDFCC\uFE0F\u200D\u2640\uFE0F','\uD83C\uDFC4','\uD83C\uDFC4\u200D\u2642\uFE0F','\uD83C\uDFC4\u200D\u2640\uFE0F','\uD83D\uDEA3','\uD83D\uDEA3\u200D\u2642\uFE0F','\uD83D\uDEA3\u200D\u2640\uFE0F','\uD83C\uDFCA','\uD83C\uDFCA\u200D\u2642\uFE0F','\uD83C\uDFCA\u200D\u2640\uFE0F','\u26F9\uFE0F','\u26F9\uFE0F\u200D\u2642\uFE0F','\u26F9\uFE0F\u200D\u2640\uFE0F','\uD83C\uDFCB\uFE0F','\uD83C\uDFCB\uFE0F\u200D\u2642\uFE0F','\uD83C\uDFCB\uFE0F\u200D\u2640\uFE0F','\uD83D\uDEB4','\uD83D\uDEB4\u200D\u2642\uFE0F','\uD83D\uDEB4\u200D\u2640\uFE0F','\uD83D\uDEB5','\uD83D\uDEB5\u200D\u2642\uFE0F','\uD83D\uDEB5\u200D\u2640\uFE0F','\uD83E\uDD38','\uD83E\uDD38\u200D\u2642\uFE0F','\uD83E\uDD38\u200D\u2640\uFE0F','\uD83E\uDD3C','\uD83E\uDD3C\u200D\u2642\uFE0F','\uD83E\uDD3C\u200D\u2640\uFE0F','\uD83E\uDD3D','\uD83E\uDD3D\u200D\u2642\uFE0F','\uD83E\uDD3D\u200D\u2640\uFE0F','\uD83E\uDD3E','\uD83E\uDD3E\u200D\u2642\uFE0F','\uD83E\uDD3E\u200D\u2640\uFE0F','\uD83E\uDD39','\uD83E\uDD39\u200D\u2642\uFE0F','\uD83E\uDD39\u200D\u2640\uFE0F'] },
            { cat: 'Person resting', items: ['\uD83E\uDDD8','\uD83E\uDDD8\u200D\u2642\uFE0F','\uD83E\uDDD8\u200D\u2640\uFE0F','\uD83D\uDEC0','\uD83D\uDECC'] },
            { cat: 'Family', items: ['\uD83E\uDDD1\u200D\uD83E\uDD1D\u200D\uD83E\uDDD1','\uD83D\uDC6D','\uD83D\uDC6B','\uD83D\uDC6C','\uD83D\uDC8F','\uD83D\uDC69\u200D\u2764\uFE0F\u200D\uD83D\uDC8B\u200D\uD83D\uDC68','\uD83D\uDC68\u200D\u2764\uFE0F\u200D\uD83D\uDC8B\u200D\uD83D\uDC68','\uD83D\uDC69\u200D\u2764\uFE0F\u200D\uD83D\uDC8B\u200D\uD83D\uDC69','\uD83D\uDC91','\uD83D\uDC69\u200D\u2764\uFE0F\u200D\uD83D\uDC68','\uD83D\uDC68\u200D\u2764\uFE0F\u200D\uD83D\uDC68','\uD83D\uDC69\u200D\u2764\uFE0F\u200D\uD83D\uDC69','\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC66','\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67','\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66','\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC66\u200D\uD83D\uDC66','\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC67','\uD83D\uDC68\u200D\uD83D\uDC68\u200D\uD83D\uDC66','\uD83D\uDC68\u200D\uD83D\uDC68\u200D\uD83D\uDC67','\uD83D\uDC68\u200D\uD83D\uDC68\u200D\uD83D\uDC67\u200D\uD83D\uDC66','\uD83D\uDC68\u200D\uD83D\uDC68\u200D\uD83D\uDC66\u200D\uD83D\uDC66','\uD83D\uDC68\u200D\uD83D\uDC68\u200D\uD83D\uDC67\u200D\uD83D\uDC67','\uD83D\uDC69\u200D\uD83D\uDC69\u200D\uD83D\uDC66','\uD83D\uDC69\u200D\uD83D\uDC69\u200D\uD83D\uDC67','\uD83D\uDC69\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66','\uD83D\uDC69\u200D\uD83D\uDC69\u200D\uD83D\uDC66\u200D\uD83D\uDC66','\uD83D\uDC69\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC67','\uD83D\uDC68\u200D\uD83D\uDC66','\uD83D\uDC68\u200D\uD83D\uDC66\u200D\uD83D\uDC66','\uD83D\uDC68\u200D\uD83D\uDC67','\uD83D\uDC68\u200D\uD83D\uDC67\u200D\uD83D\uDC66','\uD83D\uDC68\u200D\uD83D\uDC67\u200D\uD83D\uDC67','\uD83D\uDC69\u200D\uD83D\uDC66','\uD83D\uDC69\u200D\uD83D\uDC66\u200D\uD83D\uDC66','\uD83D\uDC69\u200D\uD83D\uDC67','\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66','\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC67'] },
            { cat: 'Person symbol', items: ['\uD83D\uDDE3\uFE0F','\uD83D\uDC64','\uD83D\uDC65','\uD83E\uDEC2','\uD83D\uDC6A','\uD83E\uDDD1\u200D\uD83E\uDDD1\u200D\uD83E\uDDD2','\uD83E\uDDD1\u200D\uD83E\uDDD1\u200D\uD83E\uDDD2\u200D\uD83E\uDDD2','\uD83E\uDDD1\u200D\uD83E\uDDD2','\uD83E\uDDD1\u200D\uD83E\uDDD2\u200D\uD83E\uDDD2','\uD83D\uDC63'] },
        ]},
        { name: 'Animals', cats: [
            { cat: 'Animal mammal', items: ['\uD83D\uDC35','\uD83D\uDC12','\uD83E\uDD8D','\uD83E\uDDA7','\uD83D\uDC36','\uD83D\uDC15','\uD83E\uDDAE','\uD83D\uDC15\u200D\uD83E\uDDBA','\uD83D\uDC29','\uD83D\uDC3A','\uD83E\uDD8A','\uD83E\uDD9D','\uD83D\uDC31','\uD83D\uDC08','\uD83D\uDC08\u200D\u2B1B','\uD83E\uDD81','\uD83D\uDC2F','\uD83D\uDC05','\uD83D\uDC06','\uD83D\uDC34','\uD83E\uDECE','\uD83E\uDECF','\uD83D\uDC0E','\uD83E\uDD84','\uD83E\uDD93','\uD83E\uDD8C','\uD83E\uDDAC','\uD83D\uDC2E','\uD83D\uDC02','\uD83D\uDC03','\uD83D\uDC04','\uD83D\uDC37','\uD83D\uDC16','\uD83D\uDC17','\uD83D\uDC3D','\uD83D\uDC0F','\uD83D\uDC11','\uD83D\uDC10','\uD83D\uDC2A','\uD83D\uDC2B','\uD83E\uDD99','\uD83E\uDD92','\uD83D\uDC18','\uD83E\uDDA3','\uD83E\uDD8F','\uD83E\uDD9B','\uD83D\uDC2D','\uD83D\uDC01','\uD83D\uDC00','\uD83D\uDC39','\uD83D\uDC30','\uD83D\uDC07','\uD83D\uDC3F\uFE0F','\uD83E\uDDAB','\uD83E\uDD94','\uD83E\uDD87','\uD83D\uDC3B','\uD83D\uDC3B\u200D\u2744\uFE0F','\uD83D\uDC28','\uD83D\uDC3C','\uD83E\uDDA5','\uD83E\uDDA6','\uD83E\uDDA8','\uD83E\uDD98','\uD83E\uDDA1','\uD83D\uDC3E'] },
            { cat: 'Animal bird', items: ['\uD83E\uDD83','\uD83D\uDC14','\uD83D\uDC13','\uD83D\uDC23','\uD83D\uDC24','\uD83D\uDC25','\uD83D\uDC26','\uD83D\uDC27','\uD83D\uDD4A\uFE0F','\uD83E\uDD85','\uD83E\uDD86','\uD83E\uDDA2','\uD83E\uDD89','\uD83E\uDDA4','\uD83E\uDEB6','\uD83E\uDDA9','\uD83E\uDD9A','\uD83E\uDD9C','\uD83E\uDEBD','\uD83D\uDC26\u200D\u2B1B','\uD83E\uDEBF','\uD83D\uDC26\u200D\uD83D\uDD25'] },
            { cat: 'Animal amphibian', items: ['\uD83D\uDC38'] },
            { cat: 'Animal reptile', items: ['\uD83D\uDC0A','\uD83D\uDC22','\uD83E\uDD8E','\uD83D\uDC0D','\uD83D\uDC32','\uD83D\uDC09','\uD83E\uDD95','\uD83E\uDD96'] },
            { cat: 'Animal marine', items: ['\uD83D\uDC33','\uD83D\uDC0B','\uD83D\uDC2C','\uD83E\uDDAD','\uD83D\uDC1F','\uD83D\uDC20','\uD83D\uDC21','\uD83E\uDD88','\uD83D\uDC19','\uD83D\uDC1A','\uD83E\uDEB8','\uD83E\uDEBC'] },
            { cat: 'Animal bug', items: ['\uD83D\uDC0C','\uD83E\uDD8B','\uD83D\uDC1B','\uD83D\uDC1C','\uD83D\uDC1D','\uD83E\uDEB2','\uD83D\uDC1E','\uD83E\uDD97','\uD83E\uDEB3','\uD83D\uDD77\uFE0F','\uD83D\uDD78\uFE0F','\uD83E\uDD82','\uD83E\uDD9F','\uD83E\uDEB0','\uD83E\uDEB1','\uD83E\uDDA0'] },
            { cat: 'Plant flower', items: ['\uD83D\uDC90','\uD83C\uDF38','\uD83D\uDCAE','\uD83E\uDEB7','\uD83C\uDFF5\uFE0F','\uD83C\uDF39','\uD83E\uDD40','\uD83C\uDF3A','\uD83C\uDF3B','\uD83C\uDF3C','\uD83C\uDF37','\uD83E\uDEBB'] },
            { cat: 'Plant other', items: ['\uD83C\uDF31','\uD83E\uDEB4','\uD83C\uDF32','\uD83C\uDF33','\uD83C\uDF34','\uD83C\uDF35','\uD83C\uDF3E','\uD83C\uDF3F','\u2618\uFE0F','\uD83C\uDF40','\uD83C\uDF41','\uD83C\uDF42','\uD83C\uDF43','\uD83E\uDEB9','\uD83E\uDEBA','\uD83C\uDF44'] },
        ]},
        { name: 'Food', cats: [
            { cat: 'Food fruit', items: ['\uD83C\uDF47','\uD83C\uDF48','\uD83C\uDF49','\uD83C\uDF4A','\uD83C\uDF4B','\uD83C\uDF4B\u200D\uD83D\uDFE9','\uD83C\uDF4C','\uD83C\uDF4D','\uD83E\uDD6D','\uD83C\uDF4E','\uD83C\uDF4F','\uD83C\uDF50','\uD83C\uDF51','\uD83C\uDF52','\uD83C\uDF53','\uD83E\uDED0','\uD83E\uDD5D','\uD83C\uDF45','\uD83E\uDED2','\uD83E\uDD65'] },
            { cat: 'Food vegetable', items: ['\uD83E\uDD51','\uD83C\uDF46','\uD83E\uDD54','\uD83E\uDD55','\uD83C\uDF3D','\uD83C\uDF36\uFE0F','\uD83E\uDED1','\uD83E\uDD52','\uD83E\uDD6C','\uD83E\uDD66','\uD83E\uDDC4','\uD83E\uDDC5','\uD83E\uDD5C','\uD83E\uDED8','\uD83C\uDF30','\uD83E\uDEDA','\uD83E\uDEDB','\uD83C\uDF44\u200D\uD83D\uDFEB'] },
            { cat: 'Food prepared', items: ['\uD83C\uDF5E','\uD83E\uDD50','\uD83E\uDD56','\uD83E\uDED3','\uD83E\uDD68','\uD83E\uDD6F','\uD83E\uDD5E','\uD83E\uDDC7','\uD83E\uDDC0','\uD83C\uDF56','\uD83C\uDF57','\uD83E\uDD69','\uD83E\uDD53','\uD83C\uDF54','\uD83C\uDF5F','\uD83C\uDF55','\uD83C\uDF2D','\uD83E\uDD6A','\uD83C\uDF2E','\uD83C\uDF2F','\uD83E\uDED4','\uD83E\uDD59','\uD83E\uDDC6','\uD83E\uDD5A','\uD83C\uDF73','\uD83E\uDD58','\uD83C\uDF72','\uD83E\uDED5','\uD83E\uDD63','\uD83E\uDD57','\uD83C\uDF7F','\uD83E\uDDC8','\uD83E\uDDC2','\uD83E\uDD6B'] },
            { cat: 'Food asian', items: ['\uD83C\uDF71','\uD83C\uDF58','\uD83C\uDF59','\uD83C\uDF5A','\uD83C\uDF5B','\uD83C\uDF5C','\uD83C\uDF5D','\uD83C\uDF60','\uD83C\uDF62','\uD83C\uDF63','\uD83C\uDF64','\uD83C\uDF65','\uD83E\uDD6E','\uD83C\uDF61','\uD83E\uDD5F','\uD83E\uDD60','\uD83E\uDD61'] },
            { cat: 'Food marine', items: ['\uD83E\uDD80','\uD83E\uDD9E','\uD83E\uDD90','\uD83E\uDD91','\uD83E\uDDAA'] },
            { cat: 'Food sweet', items: ['\uD83C\uDF66','\uD83C\uDF67','\uD83C\uDF68','\uD83C\uDF69','\uD83C\uDF6A','\uD83C\uDF82','\uD83C\uDF70','\uD83E\uDDC1','\uD83E\uDD67','\uD83C\uDF6B','\uD83C\uDF6C','\uD83C\uDF6D','\uD83C\uDF6E','\uD83C\uDF6F'] },
            { cat: 'Drink', items: ['\uD83C\uDF7C','\uD83E\uDD5B','\u2615','\uD83E\uDED6','\uD83C\uDF75','\uD83C\uDF76','\uD83C\uDF7E','\uD83C\uDF77','\uD83C\uDF78','\uD83C\uDF79','\uD83C\uDF7A','\uD83C\uDF7B','\uD83E\uDD42','\uD83E\uDD43','\uD83E\uDED7','\uD83E\uDD64','\uD83E\uDDCB','\uD83E\uDDC3','\uD83E\uDDC9','\uD83E\uDDCA'] },
            { cat: 'Dishware', items: ['\uD83E\uDD62','\uD83C\uDF7D\uFE0F','\uD83C\uDF74','\uD83E\uDD44','\uD83D\uDD2A','\uD83E\uDED9','\uD83C\uDFFA'] },
        ]},
        { name: 'Travel', cats: [
            { cat: 'Place map', items: ['\uD83C\uDF0D','\uD83C\uDF0E','\uD83C\uDF0F','\uD83C\uDF10','\uD83D\uDDFA\uFE0F','\uD83D\uDDFE','\uD83E\uDDED'] },
            { cat: 'Place geographic', items: ['\uD83C\uDFD4\uFE0F','\u26F0\uFE0F','\uD83C\uDF0B','\uD83D\uDDFB','\uD83C\uDFD5\uFE0F','\uD83C\uDFD6\uFE0F','\uD83C\uDFDC\uFE0F','\uD83C\uDFDD\uFE0F','\uD83C\uDFDE\uFE0F'] },
            { cat: 'Place building', items: ['\uD83C\uDFDF\uFE0F','\uD83C\uDFDB\uFE0F','\uD83C\uDFD7\uFE0F','\uD83E\uDDF1','\uD83E\uDEA8','\uD83E\uDEB5','\uD83D\uDED6','\uD83C\uDFD8\uFE0F','\uD83C\uDFDA\uFE0F','\uD83C\uDFE0','\uD83C\uDFE1','\uD83C\uDFE2','\uD83C\uDFE3','\uD83C\uDFE4','\uD83C\uDFE5','\uD83C\uDFE6','\uD83C\uDFE8','\uD83C\uDFE9','\uD83C\uDFEA','\uD83C\uDFEB','\uD83C\uDFEC','\uD83C\uDFED','\uD83C\uDFEF','\uD83C\uDFF0','\uD83D\uDC92','\uD83D\uDDFC','\uD83D\uDDFD'] },
            { cat: 'Place religious', items: ['\u26EA','\uD83D\uDD4C','\uD83D\uDED5','\uD83D\uDD4D','\u26E9\uFE0F','\uD83D\uDD4B'] },
            { cat: 'Place other', items: ['\u26F2','\u26FA','\uD83C\uDF01','\uD83C\uDF03','\uD83C\uDFD9\uFE0F','\uD83C\uDF04','\uD83C\uDF05','\uD83C\uDF06','\uD83C\uDF07','\uD83C\uDF09','\u2668\uFE0F','\uD83C\uDFA0','\uD83D\uDEDD','\uD83C\uDFA1','\uD83C\uDFA2','\uD83D\uDC88','\uD83C\uDFAA'] },
            { cat: 'Transport ground', items: ['\uD83D\uDE82','\uD83D\uDE83','\uD83D\uDE84','\uD83D\uDE85','\uD83D\uDE86','\uD83D\uDE87','\uD83D\uDE88','\uD83D\uDE89','\uD83D\uDE8A','\uD83D\uDE9D','\uD83D\uDE9E','\uD83D\uDE8B','\uD83D\uDE8C','\uD83D\uDE8D','\uD83D\uDE8E','\uD83D\uDE90','\uD83D\uDE91','\uD83D\uDE92','\uD83D\uDE93','\uD83D\uDE94','\uD83D\uDE95','\uD83D\uDE96','\uD83D\uDE97','\uD83D\uDE98','\uD83D\uDE99','\uD83D\uDEFB','\uD83D\uDE9A','\uD83D\uDE9B','\uD83D\uDE9C','\uD83C\uDFCE\uFE0F','\uD83C\uDFCD\uFE0F','\uD83D\uDEF5','\uD83E\uDDBD','\uD83E\uDDBC','\uD83D\uDEFA','\uD83D\uDEB2','\uD83D\uDEF4','\uD83D\uDEF9','\uD83D\uDEFC','\uD83D\uDE8F','\uD83D\uDEE3\uFE0F','\uD83D\uDEE4\uFE0F','\uD83D\uDEE2\uFE0F','\u26FD','\uD83D\uDEDE','\uD83D\uDEA8','\uD83D\uDEA5','\uD83D\uDEA6','\uD83D\uDED1','\uD83D\uDEA7'] },
            { cat: 'Transport water', items: ['\u2693','\uD83D\uDEDF','\u26F5','\uD83D\uDEF6','\uD83D\uDEA4','\uD83D\uDEF3\uFE0F','\u26F4\uFE0F','\uD83D\uDEE5\uFE0F','\uD83D\uDEA2'] },
            { cat: 'Transport air', items: ['\u2708\uFE0F','\uD83D\uDEE9\uFE0F','\uD83D\uDEEB','\uD83D\uDEEC','\uD83E\uDE82','\uD83D\uDCBA','\uD83D\uDE81','\uD83D\uDE9F','\uD83D\uDEA0','\uD83D\uDEA1','\uD83D\uDEF0\uFE0F','\uD83D\uDE80','\uD83D\uDEF8'] },
            { cat: 'Hotel', items: ['\uD83D\uDECE\uFE0F','\uD83E\uDDF3'] },
            { cat: 'Time', items: ['\u231B','\u23F3','\u231A','\u23F0','\u23F1\uFE0F','\u23F2\uFE0F','\uD83D\uDD70\uFE0F','\uD83D\uDD5B','\uD83D\uDD67','\uD83D\uDD50','\uD83D\uDD5C','\uD83D\uDD51','\uD83D\uDD5D','\uD83D\uDD52','\uD83D\uDD5E','\uD83D\uDD53','\uD83D\uDD5F','\uD83D\uDD54','\uD83D\uDD60','\uD83D\uDD55','\uD83D\uDD61','\uD83D\uDD56','\uD83D\uDD62','\uD83D\uDD57','\uD83D\uDD63','\uD83D\uDD58','\uD83D\uDD64','\uD83D\uDD59','\uD83D\uDD65','\uD83D\uDD5A','\uD83D\uDD66'] },
            { cat: 'Sky & weather', items: ['\uD83C\uDF11','\uD83C\uDF12','\uD83C\uDF13','\uD83C\uDF14','\uD83C\uDF15','\uD83C\uDF16','\uD83C\uDF17','\uD83C\uDF18','\uD83C\uDF19','\uD83C\uDF1A','\uD83C\uDF1B','\uD83C\uDF1C','\uD83C\uDF21\uFE0F','\u2600\uFE0F','\uD83C\uDF1D','\uD83C\uDF1E','\uD83E\uDE90','\u2B50','\uD83C\uDF1F','\uD83C\uDF20','\uD83C\uDF0C','\u2601\uFE0F','\u26C5','\u26C8\uFE0F','\uD83C\uDF24\uFE0F','\uD83C\uDF25\uFE0F','\uD83C\uDF26\uFE0F','\uD83C\uDF27\uFE0F','\uD83C\uDF28\uFE0F','\uD83C\uDF29\uFE0F','\uD83C\uDF2A\uFE0F','\uD83C\uDF2B\uFE0F','\uD83C\uDF2C\uFE0F','\uD83C\uDF00','\uD83C\uDF08','\uD83C\uDF02','\u2602\uFE0F','\u2614','\u26F1\uFE0F','\u26A1','\u2744\uFE0F','\u2603\uFE0F','\u26C4','\u2604\uFE0F','\uD83D\uDD25','\uD83D\uDCA7','\uD83C\uDF0A'] },
        ]},
        { name: 'Activities', cats: [
            { cat: 'Event', items: ['\uD83C\uDF83','\uD83C\uDF84','\uD83C\uDF86','\uD83C\uDF87','\uD83E\uDDE8','\u2728','\uD83C\uDF88','\uD83C\uDF89','\uD83C\uDF8A','\uD83C\uDF8B','\uD83C\uDF8D','\uD83C\uDF8E','\uD83C\uDF8F','\uD83C\uDF90','\uD83C\uDF91','\uD83E\uDDE7','\uD83C\uDF80','\uD83C\uDF81','\uD83C\uDF97\uFE0F','\uD83C\uDF9F\uFE0F','\uD83C\uDFAB'] },
            { cat: 'Award medal', items: ['\uD83C\uDF96\uFE0F','\uD83C\uDFC6','\uD83C\uDFC5','\uD83E\uDD47','\uD83E\uDD48','\uD83E\uDD49'] },
            { cat: 'Sport', items: ['\u26BD','\u26BE','\uD83E\uDD4E','\uD83C\uDFC0','\uD83C\uDFD0','\uD83C\uDFC8','\uD83C\uDFC9','\uD83C\uDFBE','\uD83E\uDD4F','\uD83C\uDFB3','\uD83C\uDFCF','\uD83C\uDFD1','\uD83C\uDFD2','\uD83E\uDD4D','\uD83C\uDFD3','\uD83C\uDFF8','\uD83E\uDD4A','\uD83E\uDD4B','\uD83E\uDD45','\u26F3','\u26F8\uFE0F','\uD83C\uDFA3','\uD83E\uDD3F','\uD83C\uDFBD','\uD83C\uDFBF','\uD83D\uDEF7','\uD83E\uDD4C'] },
            { cat: 'Game', items: ['\uD83C\uDFAF','\uD83E\uDE80','\uD83E\uDE81','\uD83D\uDD2B','\uD83C\uDFB1','\uD83D\uDD2E','\uD83E\uDE84','\uD83C\uDFAE','\uD83D\uDD79\uFE0F','\uD83C\uDFB0','\uD83C\uDFB2','\uD83E\uDDE9','\uD83E\uDDF8','\uD83E\uDE85','\uD83E\uDEA9','\uD83E\uDE86','\u2660\uFE0F','\u2665\uFE0F','\u2666\uFE0F','\u2663\uFE0F','\u265F\uFE0F','\uD83C\uDCCF','\uD83C\uDC04','\uD83C\uDFB4'] },
            { cat: 'Arts & crafts', items: ['\uD83C\uDFAD','\uD83D\uDDBC\uFE0F','\uD83C\uDFA8','\uD83E\uDDF5','\uD83E\uDEA1','\uD83E\uDDF6','\uD83E\uDEA2'] },
        ]},
        { name: 'Objects', cats: [
            { cat: 'Clothing', items: ['\uD83D\uDC53','\uD83D\uDD76\uFE0F','\uD83E\uDD7D','\uD83E\uDD7C','\uD83E\uDDBA','\uD83D\uDC54','\uD83D\uDC55','\uD83D\uDC56','\uD83E\uDDE3','\uD83E\uDDE4','\uD83E\uDDE5','\uD83E\uDDE6','\uD83D\uDC57','\uD83D\uDC58','\uD83E\uDD7B','\uD83E\uDE71','\uD83E\uDE72','\uD83E\uDE73','\uD83D\uDC59','\uD83D\uDC5A','\uD83E\uDEAD','\uD83D\uDC5B','\uD83D\uDC5C','\uD83D\uDC5D','\uD83D\uDECD\uFE0F','\uD83C\uDF92','\uD83E\uDE74','\uD83D\uDC5E','\uD83D\uDC5F','\uD83E\uDD7E','\uD83E\uDD7F','\uD83D\uDC60','\uD83D\uDC61','\uD83E\uDE70','\uD83D\uDC62','\uD83E\uDEAE','\uD83D\uDC51','\uD83D\uDC52','\uD83C\uDFA9','\uD83C\uDF93','\uD83E\uDDE2','\uD83E\uDE96','\u26D1\uFE0F','\uD83D\uDCFF','\uD83D\uDC84','\uD83D\uDC8D','\uD83D\uDC8E'] },
            { cat: 'Sound', items: ['\uD83D\uDD07','\uD83D\uDD08','\uD83D\uDD09','\uD83D\uDD0A','\uD83D\uDCE2','\uD83D\uDCE3','\uD83D\uDCEF','\uD83D\uDD14','\uD83D\uDD15'] },
            { cat: 'Music', items: ['\uD83C\uDFBC','\uD83C\uDFB5','\uD83C\uDFB6','\uD83C\uDF99\uFE0F','\uD83C\uDF9A\uFE0F','\uD83C\uDF9B\uFE0F','\uD83C\uDFA4','\uD83C\uDFA7','\uD83D\uDCFB'] },
            { cat: 'Musical instrument', items: ['\uD83C\uDFB7','\uD83E\uDE97','\uD83C\uDFB8','\uD83C\uDFB9','\uD83C\uDFBA','\uD83C\uDFBB','\uD83E\uDE95','\uD83E\uDD41','\uD83E\uDE98','\uD83E\uDE87','\uD83E\uDE88'] },
            { cat: 'Phone', items: ['\uD83D\uDCF1','\uD83D\uDCF2','\u260E\uFE0F','\uD83D\uDCDE','\uD83D\uDCDF','\uD83D\uDCE0'] },
            { cat: 'Computer', items: ['\uD83D\uDD0B','\uD83E\uDEAB','\uD83D\uDD0C','\uD83D\uDCBB','\uD83D\uDDA5\uFE0F','\uD83D\uDDA8\uFE0F','\u2328\uFE0F','\uD83D\uDDB1\uFE0F','\uD83D\uDDB2\uFE0F','\uD83D\uDCBD','\uD83D\uDCBE','\uD83D\uDCBF','\uD83D\uDCC0','\uD83E\uDDEE'] },
            { cat: 'Light & video', items: ['\uD83C\uDFA5','\uD83C\uDF9E\uFE0F','\uD83D\uDCFD\uFE0F','\uD83C\uDFAC','\uD83D\uDCFA','\uD83D\uDCF7','\uD83D\uDCF8','\uD83D\uDCF9','\uD83D\uDCFC','\uD83D\uDD0D','\uD83D\uDD0E','\uD83D\uDD6F\uFE0F','\uD83D\uDCA1','\uD83D\uDD26','\uD83C\uDFEE','\uD83E\uDE94'] },
            { cat: 'Book paper', items: ['\uD83D\uDCD4','\uD83D\uDCD5','\uD83D\uDCD6','\uD83D\uDCD7','\uD83D\uDCD8','\uD83D\uDCD9','\uD83D\uDCDA','\uD83D\uDCD3','\uD83D\uDCD2','\uD83D\uDCC3','\uD83D\uDCDC','\uD83D\uDCC4','\uD83D\uDCF0','\uD83D\uDDDE\uFE0F','\uD83D\uDCD1','\uD83D\uDD16','\uD83C\uDFF7\uFE0F'] },
            { cat: 'Money', items: ['\uD83D\uDCB0','\uD83E\uDE99','\uD83D\uDCB4','\uD83D\uDCB5','\uD83D\uDCB6','\uD83D\uDCB7','\uD83D\uDCB8','\uD83D\uDCB3','\uD83E\uDDFE','\uD83D\uDCB9'] },
            { cat: 'Mail', items: ['\u2709\uFE0F','\uD83D\uDCE7','\uD83D\uDCE8','\uD83D\uDCE9','\uD83D\uDCE4','\uD83D\uDCE5','\uD83D\uDCE6','\uD83D\uDCEB','\uD83D\uDCEA','\uD83D\uDCEC','\uD83D\uDCED','\uD83D\uDCEE','\uD83D\uDDF3\uFE0F'] },
            { cat: 'Writing', items: ['\u270F\uFE0F','\u2712\uFE0F','\uD83D\uDD8B\uFE0F','\uD83D\uDD8A\uFE0F','\uD83D\uDD8C\uFE0F','\uD83D\uDD8D\uFE0F','\uD83D\uDCDD'] },
            { cat: 'Office', items: ['\uD83D\uDCBC','\uD83D\uDCC1','\uD83D\uDCC2','\uD83D\uDDC2\uFE0F','\uD83D\uDCC5','\uD83D\uDCC6','\uD83D\uDDD2\uFE0F','\uD83D\uDDD3\uFE0F','\uD83D\uDCC7','\uD83D\uDCC8','\uD83D\uDCC9','\uD83D\uDCCA','\uD83D\uDCCB','\uD83D\uDCCC','\uD83D\uDCCD','\uD83D\uDCCE','\uD83D\uDD87\uFE0F','\uD83D\uDCCF','\uD83D\uDCD0','\u2702\uFE0F','\uD83D\uDDC3\uFE0F','\uD83D\uDDC4\uFE0F','\uD83D\uDDD1\uFE0F'] },
            { cat: 'Lock', items: ['\uD83D\uDD12','\uD83D\uDD13','\uD83D\uDD0F','\uD83D\uDD10','\uD83D\uDD11','\uD83D\uDDDD\uFE0F'] },
            { cat: 'Tool', items: ['\uD83D\uDD28','\uD83E\uDE93','\u26CF\uFE0F','\u2692\uFE0F','\uD83D\uDEE0\uFE0F','\uD83D\uDDE1\uFE0F','\u2694\uFE0F','\uD83D\uDCA3','\uD83E\uDE83','\uD83C\uDFF9','\uD83D\uDEE1\uFE0F','\uD83E\uDE9A','\uD83D\uDD27','\uD83E\uDE9B','\uD83D\uDD29','\u2699\uFE0F','\uD83D\uDDDC\uFE0F','\u2696\uFE0F','\uD83E\uDDAF','\uD83D\uDD17','\u26D3\uFE0F\u200D\uD83D\uDCA5','\u26D3\uFE0F','\uD83E\uDE9D','\uD83E\uDDF0','\uD83E\uDDF2','\uD83E\uDE9C'] },
            { cat: 'Science', items: ['\u2697\uFE0F','\uD83E\uDDEA','\uD83E\uDDEB','\uD83E\uDDEC','\uD83D\uDD2C','\uD83D\uDD2D','\uD83D\uDCE1'] },
            { cat: 'Medical', items: ['\uD83D\uDC89','\uD83E\uDE78','\uD83D\uDC8A','\uD83E\uDE79','\uD83E\uDE7C','\uD83E\uDE7A','\uD83E\uDE7B'] },
            { cat: 'Household', items: ['\uD83D\uDEAA','\uD83D\uDED7','\uD83E\uDE9E','\uD83E\uDE9F','\uD83D\uDECF\uFE0F','\uD83D\uDECB\uFE0F','\uD83E\uDE91','\uD83D\uDEBD','\uD83E\uDEA0','\uD83D\uDEBF','\uD83D\uDEC1','\uD83E\uDEA4','\uD83E\uDE92','\uD83E\uDDF4','\uD83E\uDDF7','\uD83E\uDDF9','\uD83E\uDDFA','\uD83E\uDDFB','\uD83E\uDEA3','\uD83E\uDDFC','\uD83E\uDEE7','\uD83E\uDEA5','\uD83E\uDDFD','\uD83E\uDDEF','\uD83D\uDED2'] },
            { cat: 'Other object', items: ['\uD83D\uDEAC','\u26B0\uFE0F','\uD83E\uDEA6','\u26B1\uFE0F','\uD83E\uDDFF','\uD83E\uDEAC','\uD83D\uDDFF','\uD83E\uDEA7','\uD83E\uDEAA'] },
        ]},
        { name: 'Symbols', cats: [
            { cat: 'Transport sign', items: ['\uD83C\uDFE7','\uD83D\uDEAE','\uD83D\uDEB0','\u267F','\uD83D\uDEB9','\uD83D\uDEBA','\uD83D\uDEBB','\uD83D\uDEBC','\uD83D\uDEBE','\uD83D\uDEC2','\uD83D\uDEC3','\uD83D\uDEC4','\uD83D\uDEC5'] },
            { cat: 'Warning', items: ['\u26A0\uFE0F','\uD83D\uDEB8','\u26D4','\uD83D\uDEAB','\uD83D\uDEB3','\uD83D\uDEAD','\uD83D\uDEAF','\uD83D\uDEB1','\uD83D\uDEB7','\uD83D\uDCF5','\uD83D\uDD1E','\u2622\uFE0F','\u2623\uFE0F'] },
            { cat: 'Arrow', items: ['\u2B06\uFE0F','\u2197\uFE0F','\u27A1\uFE0F','\u2198\uFE0F','\u2B07\uFE0F','\u2199\uFE0F','\u2B05\uFE0F','\u2196\uFE0F','\u2195\uFE0F','\u2194\uFE0F','\u21A9\uFE0F','\u21AA\uFE0F','\u2934\uFE0F','\u2935\uFE0F','\uD83D\uDD03','\uD83D\uDD04','\uD83D\uDD19','\uD83D\uDD1A','\uD83D\uDD1B','\uD83D\uDD1C','\uD83D\uDD1D'] },
            { cat: 'Religion', items: ['\uD83D\uDED0','\u269B\uFE0F','\uD83D\uDD49\uFE0F','\u2721\uFE0F','\u2638\uFE0F','\u262F\uFE0F','\u271D\uFE0F','\u2626\uFE0F','\u262A\uFE0F','\u262E\uFE0F','\uD83D\uDD4E','\uD83D\uDD2F','\uD83E\uDEAF'] },
            { cat: 'Zodiac', items: ['\u2648','\u2649','\u264A','\u264B','\u264C','\u264D','\u264E','\u264F','\u2650','\u2651','\u2652','\u2653','\u26CE'] },
            { cat: 'Av symbol', items: ['\uD83D\uDD00','\uD83D\uDD01','\uD83D\uDD02','\u25B6\uFE0F','\u23E9','\u23ED\uFE0F','\u23EF\uFE0F','\u25C0\uFE0F','\u23EA','\u23EE\uFE0F','\uD83D\uDD3C','\u23EB','\uD83D\uDD3D','\u23EC','\u23F8\uFE0F','\u23F9\uFE0F','\u23FA\uFE0F','\u23CF\uFE0F','\uD83C\uDFA6','\uD83D\uDD05','\uD83D\uDD06','\uD83D\uDCF6','\uD83D\uDEDC','\uD83D\uDCF3','\uD83D\uDCF4'] },
            { cat: 'Gender', items: ['\u2640\uFE0F','\u2642\uFE0F','\u26A7\uFE0F'] },
            { cat: 'Math', items: ['\u2716\uFE0F','\u2795','\u2796','\u2797','\uD83D\uDFF0','\u267E\uFE0F'] },
            { cat: 'Punctuation', items: ['\u203C\uFE0F','\u2049\uFE0F','\u2753','\u2754','\u2755','\u2757','\u3030\uFE0F'] },
            { cat: 'Currency', items: ['\uD83D\uDCB1','\uD83D\uDCB2'] },
            { cat: 'Other symbol', items: ['\u2695\uFE0F','\u267B\uFE0F','\u269C\uFE0F','\uD83D\uDD31','\uD83D\uDCDB','\uD83D\uDD30','\u2B55','\u2705','\u2611\uFE0F','\u2714\uFE0F','\u274C','\u274E','\u27B0','\u27BF','\u303D\uFE0F','\u2733\uFE0F','\u2734\uFE0F','\u2747\uFE0F','\u00A9\uFE0F','\u00AE\uFE0F','\u2122\uFE0F'] },
            { cat: 'Keycap', items: ['\u0023\uFE0F\u20E3','\u002A\uFE0F\u20E3','\u0030\uFE0F\u20E3','\u0031\uFE0F\u20E3','\u0032\uFE0F\u20E3','\u0033\uFE0F\u20E3','\u0034\uFE0F\u20E3','\u0035\uFE0F\u20E3','\u0036\uFE0F\u20E3','\u0037\uFE0F\u20E3','\u0038\uFE0F\u20E3','\u0039\uFE0F\u20E3','\uD83D\uDD1F'] },
            { cat: 'Alphanum', items: ['\uD83D\uDD20','\uD83D\uDD21','\uD83D\uDD22','\uD83D\uDD23','\uD83D\uDD24','\uD83C\uDD70\uFE0F','\uD83C\uDD8E','\uD83C\uDD71\uFE0F','\uD83C\uDD91','\uD83C\uDD92','\uD83C\uDD93','\u2139\uFE0F','\uD83C\uDD94','\u24C2\uFE0F','\uD83C\uDD95','\uD83C\uDD96','\uD83C\uDD7E\uFE0F','\uD83C\uDD97','\uD83C\uDD7F\uFE0F','\uD83C\uDD98','\uD83C\uDD99','\uD83C\uDD9A','\uD83C\uDE01','\uD83C\uDE02\uFE0F','\uD83C\uDE37\uFE0F','\uD83C\uDE36','\uD83C\uDE2F','\uD83C\uDE50','\uD83C\uDE39','\uD83C\uDE1A','\uD83C\uDE32','\uD83C\uDE51','\uD83C\uDE38','\uD83C\uDE34','\uD83C\uDE33','\u3297\uFE0F','\u3299\uFE0F','\uD83C\uDE3A','\uD83C\uDE35'] },
            { cat: 'Geometric', items: ['\uD83D\uDD34','\uD83D\uDFE0','\uD83D\uDFE1','\uD83D\uDFE2','\uD83D\uDD35','\uD83D\uDFE3','\uD83D\uDFE4','\u26AB','\u26AA','\uD83D\uDFE5','\uD83D\uDFE7','\uD83D\uDFE8','\uD83D\uDFE9','\uD83D\uDFE6','\uD83D\uDFEA','\uD83D\uDFEB','\u2B1B','\u2B1C','\u25FC\uFE0F','\u25FB\uFE0F','\u25FE','\u25FD','\u25AA\uFE0F','\u25AB\uFE0F','\uD83D\uDD36','\uD83D\uDD37','\uD83D\uDD38','\uD83D\uDD39','\uD83D\uDD3A','\uD83D\uDD3B','\uD83D\uDCA0','\uD83D\uDD18','\uD83D\uDD33','\uD83D\uDD32'] },
        ]},
        { name: 'Flags', cats: [
            { cat: 'Flag', items: ['\uD83C\uDFC1','\uD83D\uDEA9','\uD83C\uDF8C','\uD83C\uDFF4','\uD83C\uDFF3\uFE0F','\uD83C\uDFF3\uFE0F\u200D\uD83C\uDF08','\uD83C\uDFF3\uFE0F\u200D\u26A7\uFE0F','\uD83C\uDFF4\u200D\u2620\uFE0F'] },
            { cat: 'Country flag', items: ['\uD83C\uDDE6\uD83C\uDDE8','\uD83C\uDDE6\uD83C\uDDE9','\uD83C\uDDE6\uD83C\uDDEA','\uD83C\uDDE6\uD83C\uDDEB','\uD83C\uDDE6\uD83C\uDDEC','\uD83C\uDDE6\uD83C\uDDEE','\uD83C\uDDE6\uD83C\uDDF1','\uD83C\uDDE6\uD83C\uDDF2','\uD83C\uDDE6\uD83C\uDDF4','\uD83C\uDDE6\uD83C\uDDF6','\uD83C\uDDE6\uD83C\uDDF7','\uD83C\uDDE6\uD83C\uDDF8','\uD83C\uDDE6\uD83C\uDDF9','\uD83C\uDDE6\uD83C\uDDFA','\uD83C\uDDE6\uD83C\uDDFC','\uD83C\uDDE6\uD83C\uDDFD','\uD83C\uDDE6\uD83C\uDDFF','\uD83C\uDDE7\uD83C\uDDE6','\uD83C\uDDE7\uD83C\uDDE7','\uD83C\uDDE7\uD83C\uDDE9','\uD83C\uDDE7\uD83C\uDDEA','\uD83C\uDDE7\uD83C\uDDEB','\uD83C\uDDE7\uD83C\uDDEC','\uD83C\uDDE7\uD83C\uDDED','\uD83C\uDDE7\uD83C\uDDEE','\uD83C\uDDE7\uD83C\uDDEF','\uD83C\uDDE7\uD83C\uDDF1','\uD83C\uDDE7\uD83C\uDDF2','\uD83C\uDDE7\uD83C\uDDF3','\uD83C\uDDE7\uD83C\uDDF4','\uD83C\uDDE7\uD83C\uDDF6','\uD83C\uDDE7\uD83C\uDDF7','\uD83C\uDDE7\uD83C\uDDF8','\uD83C\uDDE7\uD83C\uDDF9','\uD83C\uDDE7\uD83C\uDDFB','\uD83C\uDDE7\uD83C\uDDFC','\uD83C\uDDE7\uD83C\uDDFE','\uD83C\uDDE7\uD83C\uDDFF','\uD83C\uDDE8\uD83C\uDDE6','\uD83C\uDDE8\uD83C\uDDE8','\uD83C\uDDE8\uD83C\uDDE9','\uD83C\uDDE8\uD83C\uDDEB','\uD83C\uDDE8\uD83C\uDDEC','\uD83C\uDDE8\uD83C\uDDED','\uD83C\uDDE8\uD83C\uDDEE','\uD83C\uDDE8\uD83C\uDDF0','\uD83C\uDDE8\uD83C\uDDF1','\uD83C\uDDE8\uD83C\uDDF2','\uD83C\uDDE8\uD83C\uDDF3','\uD83C\uDDE8\uD83C\uDDF4','\uD83C\uDDE8\uD83C\uDDF5','\uD83C\uDDE8\uD83C\uDDF7','\uD83C\uDDE8\uD83C\uDDFA','\uD83C\uDDE8\uD83C\uDDFB','\uD83C\uDDE8\uD83C\uDDFC','\uD83C\uDDE8\uD83C\uDDFD','\uD83C\uDDE8\uD83C\uDDFE','\uD83C\uDDE8\uD83C\uDDFF','\uD83C\uDDE9\uD83C\uDDEA','\uD83C\uDDE9\uD83C\uDDEC','\uD83C\uDDE9\uD83C\uDDEF','\uD83C\uDDE9\uD83C\uDDF0','\uD83C\uDDE9\uD83C\uDDF2','\uD83C\uDDE9\uD83C\uDDF4','\uD83C\uDDE9\uD83C\uDDFF','\uD83C\uDDEA\uD83C\uDDE6','\uD83C\uDDEA\uD83C\uDDE8','\uD83C\uDDEA\uD83C\uDDEA','\uD83C\uDDEA\uD83C\uDDEC','\uD83C\uDDEA\uD83C\uDDED','\uD83C\uDDEA\uD83C\uDDF7','\uD83C\uDDEA\uD83C\uDDF8','\uD83C\uDDEA\uD83C\uDDF9','\uD83C\uDDEA\uD83C\uDDFA','\uD83C\uDDEB\uD83C\uDDEE','\uD83C\uDDEB\uD83C\uDDEF','\uD83C\uDDEB\uD83C\uDDF0','\uD83C\uDDEB\uD83C\uDDF2','\uD83C\uDDEB\uD83C\uDDF4','\uD83C\uDDEB\uD83C\uDDF7','\uD83C\uDDEC\uD83C\uDDE6','\uD83C\uDDEC\uD83C\uDDE7','\uD83C\uDDEC\uD83C\uDDE9','\uD83C\uDDEC\uD83C\uDDEA','\uD83C\uDDEC\uD83C\uDDEB','\uD83C\uDDEC\uD83C\uDDEC','\uD83C\uDDEC\uD83C\uDDED','\uD83C\uDDEC\uD83C\uDDEE','\uD83C\uDDEC\uD83C\uDDF1','\uD83C\uDDEC\uD83C\uDDF2','\uD83C\uDDEC\uD83C\uDDF3','\uD83C\uDDEC\uD83C\uDDF5','\uD83C\uDDEC\uD83C\uDDF6','\uD83C\uDDEC\uD83C\uDDF7','\uD83C\uDDEC\uD83C\uDDF8','\uD83C\uDDEC\uD83C\uDDF9','\uD83C\uDDEC\uD83C\uDDFA','\uD83C\uDDEC\uD83C\uDDFC','\uD83C\uDDEC\uD83C\uDDFE','\uD83C\uDDED\uD83C\uDDF0','\uD83C\uDDED\uD83C\uDDF2','\uD83C\uDDED\uD83C\uDDF3','\uD83C\uDDED\uD83C\uDDF7','\uD83C\uDDED\uD83C\uDDF9','\uD83C\uDDED\uD83C\uDDFA','\uD83C\uDDEE\uD83C\uDDE8','\uD83C\uDDEE\uD83C\uDDE9','\uD83C\uDDEE\uD83C\uDDEA','\uD83C\uDDEE\uD83C\uDDF1','\uD83C\uDDEE\uD83C\uDDF2','\uD83C\uDDEE\uD83C\uDDF3','\uD83C\uDDEE\uD83C\uDDF4','\uD83C\uDDEE\uD83C\uDDF6','\uD83C\uDDEE\uD83C\uDDF7','\uD83C\uDDEE\uD83C\uDDF8','\uD83C\uDDEE\uD83C\uDDF9','\uD83C\uDDEF\uD83C\uDDEA','\uD83C\uDDEF\uD83C\uDDF2','\uD83C\uDDEF\uD83C\uDDF4','\uD83C\uDDEF\uD83C\uDDF5','\uD83C\uDDF0\uD83C\uDDEA','\uD83C\uDDF0\uD83C\uDDEC','\uD83C\uDDF0\uD83C\uDDED','\uD83C\uDDF0\uD83C\uDDEE','\uD83C\uDDF0\uD83C\uDDF2','\uD83C\uDDF0\uD83C\uDDF3','\uD83C\uDDF0\uD83C\uDDF5','\uD83C\uDDF0\uD83C\uDDF7','\uD83C\uDDF0\uD83C\uDDFC','\uD83C\uDDF0\uD83C\uDDFE','\uD83C\uDDF0\uD83C\uDDFF','\uD83C\uDDF1\uD83C\uDDE6','\uD83C\uDDF1\uD83C\uDDE7','\uD83C\uDDF1\uD83C\uDDE8','\uD83C\uDDF1\uD83C\uDDEE','\uD83C\uDDF1\uD83C\uDDF0','\uD83C\uDDF1\uD83C\uDDF7','\uD83C\uDDF1\uD83C\uDDF8','\uD83C\uDDF1\uD83C\uDDF9','\uD83C\uDDF1\uD83C\uDDFA','\uD83C\uDDF1\uD83C\uDDFB','\uD83C\uDDF1\uD83C\uDDFE','\uD83C\uDDF2\uD83C\uDDE6','\uD83C\uDDF2\uD83C\uDDE8','\uD83C\uDDF2\uD83C\uDDE9','\uD83C\uDDF2\uD83C\uDDEA','\uD83C\uDDF2\uD83C\uDDEB','\uD83C\uDDF2\uD83C\uDDEC','\uD83C\uDDF2\uD83C\uDDED','\uD83C\uDDF2\uD83C\uDDF0','\uD83C\uDDF2\uD83C\uDDF1','\uD83C\uDDF2\uD83C\uDDF2','\uD83C\uDDF2\uD83C\uDDF3','\uD83C\uDDF2\uD83C\uDDF4','\uD83C\uDDF2\uD83C\uDDF5','\uD83C\uDDF2\uD83C\uDDF6','\uD83C\uDDF2\uD83C\uDDF7','\uD83C\uDDF2\uD83C\uDDF8','\uD83C\uDDF2\uD83C\uDDF9','\uD83C\uDDF2\uD83C\uDDFA','\uD83C\uDDF2\uD83C\uDDFB','\uD83C\uDDF2\uD83C\uDDFC','\uD83C\uDDF2\uD83C\uDDFD','\uD83C\uDDF2\uD83C\uDDFE','\uD83C\uDDF2\uD83C\uDDFF','\uD83C\uDDF3\uD83C\uDDE6','\uD83C\uDDF3\uD83C\uDDE8','\uD83C\uDDF3\uD83C\uDDEA','\uD83C\uDDF3\uD83C\uDDEB','\uD83C\uDDF3\uD83C\uDDEC','\uD83C\uDDF3\uD83C\uDDEE','\uD83C\uDDF3\uD83C\uDDF1','\uD83C\uDDF3\uD83C\uDDF4','\uD83C\uDDF3\uD83C\uDDF5','\uD83C\uDDF3\uD83C\uDDF7','\uD83C\uDDF3\uD83C\uDDFA','\uD83C\uDDF3\uD83C\uDDFF','\uD83C\uDDF4\uD83C\uDDF2','\uD83C\uDDF5\uD83C\uDDE6','\uD83C\uDDF5\uD83C\uDDEA','\uD83C\uDDF5\uD83C\uDDEB','\uD83C\uDDF5\uD83C\uDDEC','\uD83C\uDDF5\uD83C\uDDED','\uD83C\uDDF5\uD83C\uDDF0','\uD83C\uDDF5\uD83C\uDDF1','\uD83C\uDDF5\uD83C\uDDF2','\uD83C\uDDF5\uD83C\uDDF3','\uD83C\uDDF5\uD83C\uDDF7','\uD83C\uDDF5\uD83C\uDDF8','\uD83C\uDDF5\uD83C\uDDF9','\uD83C\uDDF5\uD83C\uDDFC','\uD83C\uDDF5\uD83C\uDDFE','\uD83C\uDDF6\uD83C\uDDE6','\uD83C\uDDF7\uD83C\uDDEA','\uD83C\uDDF7\uD83C\uDDF4','\uD83C\uDDF7\uD83C\uDDF8','\uD83C\uDDF7\uD83C\uDDFA','\uD83C\uDDF7\uD83C\uDDFC','\uD83C\uDDF8\uD83C\uDDE6','\uD83C\uDDF8\uD83C\uDDE7','\uD83C\uDDF8\uD83C\uDDE8','\uD83C\uDDF8\uD83C\uDDE9','\uD83C\uDDF8\uD83C\uDDEA','\uD83C\uDDF8\uD83C\uDDEC','\uD83C\uDDF8\uD83C\uDDED','\uD83C\uDDF8\uD83C\uDDEE','\uD83C\uDDF8\uD83C\uDDEF','\uD83C\uDDF8\uD83C\uDDF0','\uD83C\uDDF8\uD83C\uDDF1','\uD83C\uDDF8\uD83C\uDDF2','\uD83C\uDDF8\uD83C\uDDF3','\uD83C\uDDF8\uD83C\uDDF4','\uD83C\uDDF8\uD83C\uDDF7','\uD83C\uDDF8\uD83C\uDDF8','\uD83C\uDDF8\uD83C\uDDF9','\uD83C\uDDF8\uD83C\uDDFB','\uD83C\uDDF8\uD83C\uDDFD','\uD83C\uDDF8\uD83C\uDDFE','\uD83C\uDDF8\uD83C\uDDFF','\uD83C\uDDF9\uD83C\uDDE6','\uD83C\uDDF9\uD83C\uDDE8','\uD83C\uDDF9\uD83C\uDDE9','\uD83C\uDDF9\uD83C\uDDEB','\uD83C\uDDF9\uD83C\uDDEC','\uD83C\uDDF9\uD83C\uDDED','\uD83C\uDDF9\uD83C\uDDEF','\uD83C\uDDF9\uD83C\uDDF0','\uD83C\uDDF9\uD83C\uDDF1','\uD83C\uDDF9\uD83C\uDDF2','\uD83C\uDDF9\uD83C\uDDF3','\uD83C\uDDF9\uD83C\uDDF4','\uD83C\uDDF9\uD83C\uDDF7','\uD83C\uDDF9\uD83C\uDDF9','\uD83C\uDDF9\uD83C\uDDFB','\uD83C\uDDF9\uD83C\uDDFC','\uD83C\uDDF9\uD83C\uDDFF','\uD83C\uDDFA\uD83C\uDDE6','\uD83C\uDDFA\uD83C\uDDEC','\uD83C\uDDFA\uD83C\uDDF2','\uD83C\uDDFA\uD83C\uDDF3','\uD83C\uDDFA\uD83C\uDDF8','\uD83C\uDDFA\uD83C\uDDFE','\uD83C\uDDFA\uD83C\uDDFF','\uD83C\uDDFB\uD83C\uDDE6','\uD83C\uDDFB\uD83C\uDDE8','\uD83C\uDDFB\uD83C\uDDEA','\uD83C\uDDFB\uD83C\uDDEC','\uD83C\uDDFB\uD83C\uDDEE','\uD83C\uDDFB\uD83C\uDDF3','\uD83C\uDDFB\uD83C\uDDFA','\uD83C\uDDFC\uD83C\uDDEB','\uD83C\uDDFC\uD83C\uDDF8','\uD83C\uDDFD\uD83C\uDDF0','\uD83C\uDDFE\uD83C\uDDEA','\uD83C\uDDFE\uD83C\uDDF9','\uD83C\uDDFF\uD83C\uDDE6','\uD83C\uDDFF\uD83C\uDDF2','\uD83C\uDDFF\uD83C\uDDFC'] },
            { cat: 'Subdivision flag', items: ['\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F','\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC73\uDB40\uDC63\uDB40\uDC74\uDB40\uDC7F','\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC77\uDB40\uDC6C\uDB40\uDC73\uDB40\uDC7F'] },
        ]},
    ];
    // Page 1 is the curated set above; the pages after it are the catalog.
    // Built-in pages are the OFFLINE FALLBACK -- the moment the pin modal is
    // opened for the first time we lazy-load quick-launch-icons.js (the full
    // ~3,900-glyph catalog, every icon carrying a searchable name) and swap
    // it in. ICON_PAGES is mutated in place, never reassigned, so anything
    // holding the array (window.QuickLaunch.iconPages) sees the new pages.
    var ICON_PAGES = [{ name: 'Core', cats: ICONS }].concat(ICON_PAGES_EXTRA);

    var ICON_CATALOG_SRC = '/quick-launch/quick-launch-icons.js?v=1';
    var iconCatalogState = 'idle';   // idle | loading | loaded | failed

    // An icon entry is either a bare glyph (built-in pages) or a
    // [glyph, name] pair (catalog pages). These two read either shape.
    function iconGlyph(item) { return (typeof item === 'string') ? item : (item && item[0]) || ''; }
    function iconLabel(item) { return (typeof item === 'string') ? '' : (item && item[1]) || ''; }

    var iconCatalogWaiting = [];

    /**
     * Lazy-load the full icon catalog once. The callback fires only when the
     * page list actually changed, so a repeat open does not repaint for
     * nothing. A failure is silent by design -- the built-in pages above keep
     * the picker perfectly usable offline.
     */
    function ensureIconCatalog(onSwapped) {
        if (iconCatalogState === 'loaded' || iconCatalogState === 'failed') return;
        iconCatalogWaiting.push(onSwapped);
        if (iconCatalogState === 'loading') return;
        iconCatalogState = 'loading';

        function finish() {
            var waiting = iconCatalogWaiting;
            iconCatalogWaiting = [];
            if (iconCatalogState !== 'loaded') return;
            for (var i = 0; i < waiting.length; i++) waiting[i]();
        }
        var s = document.createElement('script');
        s.src = ICON_CATALOG_SRC;
        s.async = true;
        s.onload = function () {
            var cat = window.QuickLaunchIconCatalog;
            if (cat && cat.pages && cat.pages.length) {
                ICON_PAGES.length = 1;                    // keep 'Core', drop the fallback pages
                for (var i = 0; i < cat.pages.length; i++) ICON_PAGES.push(cat.pages[i]);
                iconCatalogState = 'loaded';
            } else {
                iconCatalogState = 'failed';
            }
            finish();
        };
        s.onerror = function () { iconCatalogState = 'failed'; finish(); };
        document.head.appendChild(s);
    }

    // ---- One-time CSS injection -----------------------------------------
    // Dedup by pathname so a page that already <link>s the stylesheet with a
    // version (e.g. index.html's ?v=N) is recognized and we don't inject a
    // second copy. On pages with no link at all, inject a versioned href so
    // the PWA cache busts when the stylesheet changes.
    //
    // Calls back only once the stylesheet is PARSED. The band used to be built
    // in the same tick the <link> was appended, so its buttons painted as a
    // bare stacked list until the CSS arrived a beat later -- the "garbled
    // mess, then it fixes itself" flash on every page (Tony, 2026-09-10). A
    // stylesheet that never answers cannot hold the band hostage either: after
    // CSS_WAIT_MS the band is built regardless.
    var CSS_WAIT_MS = 2500;
    var cssReady = false;
    function ensureCss(whenReady) {
        // The four in-sheet callers (Quick Launch sheet, menu search, url bar,
        // desk) pass no callback: they only need the <link> to exist. Only the
        // band build waits for the parse. A required callback here broke every
        // one of them ("whenReady is not a function") on 2026-09-10.
        if (typeof whenReady !== 'function') whenReady = function () {};
        if (cssReady) { whenReady(); return; }
        var link = null;
        var links = document.querySelectorAll('link[rel="stylesheet"]');
        for (var i = 0; i < links.length; i++) {
            var h = links[i].getAttribute('href') || '';
            if (h.indexOf(CSS_HREF) === 0) { link = links[i]; break; }
        }
        if (!link) {
            link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = CSS_HREF + '?v=' + CSS_VER;
            document.head.appendChild(link);
        }
        var fired = false;
        function fire() {
            if (fired) return;
            fired = true;
            cssReady = true;
            whenReady();
        }
        if (link.sheet) { fire(); return; }          // already parsed (a page-level <link>)
        link.addEventListener('load', fire);
        link.addEventListener('error', fire);        // unstyled beats absent
        setTimeout(fire, CSS_WAIT_MS);
    }

    // ---- Shared page-header locator -------------------------------------
    // Every authenticated page has either a dedicated nav strip or a colored
    // page header. We locate it so the icon band can be inserted as the row
    // directly above it (and so home / logout text in it can be retired).
    function findNavStrip() {
        // Selectors in priority order -- preferred targets (real nav strips)
        // first, page-specific colored headers as a fallback for pages that
        // don't have a dedicated nav strip (contacts list, vendors list,
        // checklists, lists, etc).
        var selectors = [
            '.lw-nav', '.hp-nav', '.vc-nav',                                  // dedicated nav strips
            '.picasso-header',                                                 // dashboard
            '.ct-header', '.ed-header',                                        // contacts list + entity detail
            '.cl-header', '.cld-header',                                       // checklists list + detail
            '.lh-header',                                                      // lists list (lists/lists.html)
            '.vd-header',                                                      // vendors list
            '.ad-header',                                                      // admin
            '.ff-header',                                                      // feature flags
            '.cv-header', '.cvd-header',                                       // conversations
            '.pk-header', '.hp-header', '.qe-header', '.qd-header',            // misc page headers
            '.vc-header',                                                      // voice
            '.wf-header',                                                      // writing filing cabinet
            '.ek-header', '.gs-header', '.lg-header',                          // admin: email keywords, gmail, logs
            '.sc-header', '.vs-header',                                        // admin: stewart control, voice settings
            '.cm-header',                                                      // code metrics
            '.fs-header',                                                      // folder shortcuts
            '.qr-header',                                                      // query runner
            '.tsk-header',                                                     // task search
            '.ud-header'                                                       // user defined entities
        ];
        var nav = null;
        for (var i = 0; i < selectors.length; i++) {
            nav = document.querySelector(selectors[i]);
            if (nav) break;
        }
        if (!nav) nav = findFallbackStrip();
        return nav;
    }

    // A page that invents its own header class (or has no header at all) used
    // to get NO icon band: no Back / Forward, no Pin, no Print, and no error
    // to say so. The list above can never stay complete, so when nothing in
    // it matches we fall back, in order, to:
    //   1. the first <header> element on the page,
    //   2. the first element whose class ends in "-header" (the house style),
    //   3. the first element child of <body> -- the band then lands at the
    //      very top of the page, which is where it belongs anyway.
    // Anything we pick must sit outside the band itself.
    function findFallbackStrip() {
        var el = document.querySelector('body header');
        if (el && !el.closest('#lwIconBand')) return el;

        var all = document.querySelectorAll('body [class]');
        for (var i = 0; i < all.length; i++) {
            var c = all[i].className;
            if (typeof c !== 'string') continue;              // svg elements
            if (!/(^|\s)[a-z0-9-]*-header(\s|$)/i.test(c)) continue;
            if (all[i].closest('#lwIconBand')) continue;
            return all[i];
        }

        var body = document.body;
        if (!body) return null;
        var kid = body.firstElementChild;
        while (kid) {
            var tag = kid.tagName;
            if (kid.id !== 'lwIconBand' && tag !== 'SCRIPT' && tag !== 'STYLE'
                && tag !== 'LINK' && tag !== 'TEMPLATE' && tag !== 'NOSCRIPT') {
                return kid;
            }
            kid = kid.nextElementSibling;
        }
        return null;
    }

    // ---- Pin button in the nav -----------------------------------------
    // When the current URL is on the Quick Launch row the button reads
    // "\uD83D\uDCCD Pinned", otherwise "\uD83D\uDCCC Pin". Either way a tap opens the pin
    // modal, which manages BOTH homes of the page -- the Quick Launch row and
    // the Links tab of Entity Manager IV (Story 3485) -- so unpinning is
    // unchecking a box there. State is refreshed from /api/quick-launch on
    // inject and after every save.
    function setPinButtonState(btn, pinned, pinId, pinLabel) {
        btn.dataset.pinned   = pinned ? '1' : '0';
        btn.dataset.pinId    = pinId ? String(pinId) : '';
        btn.dataset.pinLabel = pinLabel || '';
        /* Icon only -- no caption. Pin was the one control in the band that
         * carried a word, and at 68px it was more than twice the width of
         * every other icon; on a 360px phone with the Desk icon on, that
         * overflow alone is what pushed the band onto a second row (Story
         * 3478). The pushpin glyph is unchanged, the pinned state still reads
         * at a glance from the amber fill, and the wording lives on in the
         * title / aria-label for anything that speaks the control aloud. */
        if (pinned) {
            btn.innerHTML = '<span class="ql-pin-ico">&#128205;</span>';
            btn.title = 'This page is pinned. Tap to change where';
            btn.setAttribute('aria-label', 'Pinned. Tap to change where this page is pinned');
            btn.classList.add('is-pinned');
        } else {
            btn.innerHTML = '<span class="ql-pin-ico">&#128204;</span>';
            btn.title = 'Pin this page to Quick Launch or Entity IV Links';
            btn.setAttribute('aria-label', 'Pin this page');
            btn.classList.remove('is-pinned');
        }
    }

    async function refreshPinState() {
        var btn = document.getElementById('lwPinBtn');
        if (!btn) return;
        if (!window.LWAuth || !window.LWAuth.apiGet) return;
        var res;
        try { res = await window.LWAuth.apiGet('/api/quick-launch'); }
        catch (_) { return; }
        if (!res || !res.ok || !Array.isArray(res.data)) return;
        lastPins = res.data;   // cache for the Quick Launch sheet's instant open
        var curUrl = currentUrl();
        var match = null;
        for (var i = 0; i < res.data.length; i++) {
            if (res.data[i].url === curUrl) { match = res.data[i]; break; }
        }
        setPinButtonState(btn, !!match,
            match ? match.quickLaunchId : null,
            match ? match.label         : null);
    }

    // ---- Pinning one record off a list page ------------------------------
    // A list page whose cards each stand for one record (the Debates shelf,
    // the Research shelves) puts a Quick Launch button on every card. This is
    // the whole toggle behind that button, so the pages carry no pin logic of
    // their own. opts = { url, label, iconKey, onChange(pinned) }. If a pin
    // with that url already exists it is removed after a Cancel-default
    // confirm; otherwise the standard pin modal opens with the url fixed and
    // the label / icon pre-filled. Story 2428.
    async function togglePinFor(opts) {
        opts = opts || {};
        var url = normalizeUrl(opts.url || '');
        if (!url) return;
        if (!window.LWAuth || !window.LWAuth.apiPost) { toast('Quick Launch is not available.'); return; }
        var pins = await listPins(true);
        var match = null;
        for (var i = 0; i < pins.length; i++) {
            if (pins[i].url === url) { match = pins[i]; break; }
        }
        var notify = function (pinned) {
            lastPins = null;                        // the next open re-reads the list
            if (typeof opts.onChange === 'function') opts.onChange(pinned);
            refreshPinState();                      // the band's own Pin button may be this url
        };
        if (match) {
            var ok = await confirmAction({
                title: 'Remove this pin?',
                subtitle: '"' + (match.label || opts.label || url) + '" will be removed from Quick Launch.',
                confirmLabel: 'Yes, remove',
                cancelLabel: 'Cancel'
            });
            if (!ok) return;
            var res = await window.LWAuth.apiPost('/api/quick-launch/' + match.quickLaunchId, null, 'DELETE');
            if (res && res.ok) { toast('Removed.'); notify(false); }
            else toast('Remove failed.');
            return;
        }
        openPinModal({
            mode:    'pin-target',
            url:     url,
            label:   opts.label,
            iconKey: opts.iconKey || '',
            onSaved: function () { notify(true); }
        });
    }

    // The pin list -- the cached copy unless force is set or nothing is
    // cached yet. Resolves to an empty array when the API cannot be reached.
    async function listPins(force) {
        if (!force && Array.isArray(lastPins)) return lastPins;
        if (!window.LWAuth || !window.LWAuth.apiGet) return [];
        try {
            var res = await window.LWAuth.apiGet('/api/quick-launch');
            if (res && res.ok && Array.isArray(res.data)) { lastPins = res.data; return lastPins; }
        } catch (_) { /* fall through to whatever is cached */ }
        return Array.isArray(lastPins) ? lastPins : [];
    }

    // ---- The icon band (standard top row) -------------------------------
    // The standard LeonardoWeb page layout: a top row that holds ONLY icons
    // (back / forward / menu / quick launch / pin / page actions on the left;
    // logout alone on the right), with the page's own title and buttons
    // falling on the rows below it. The band is injected as the row directly
    // above the page header, inside the same flex shell, so a height:100vh
    // shell stays within the viewport instead of overflowing.
    // "ql-band" is the opt-back-IN class: a page that carries ql-no-pin (no Pin
    // button, because pinning it to itself is a no-op) can still ask for the
    // standard band. The dashboard uses that combination -- band yes, Pin no.
    // ROLLBACK: drop the ql-band class from index.html's <body> and the
    // dashboard goes back to its own header with the Sign out button.
    function ensureIconBand() {
        if (document.body
            && document.body.classList.contains('ql-no-pin')
            && !document.body.classList.contains('ql-band')) return null;
        var existing = document.getElementById('lwIconBand');
        if (existing) return existing;
        var header = findNavStrip();
        if (!header || !header.parentNode) return null;

        var band = document.createElement('div');
        band.id        = 'lwIconBand';
        band.className = 'lw-iconband';
        band.setAttribute('data-testid', 'icon-band');

        var left = document.createElement('div');
        left.id        = 'lwIconBandLeft';
        left.className = 'lw-iconband-left';
        band.appendChild(left);

        header.parentNode.insertBefore(band, header);
        return band;
    }

    // The band's left group -- where the nav cluster, pin, and page-action
    // icons live. Created by ensureIconBand.
    function bandLeft() {
        return document.getElementById('lwIconBandLeft');
    }

    function injectPinButton() {
        if (document.getElementById('lwPinBtn')) return;
        // ql-no-pin still means "no Pin button here" even when the page opted
        // back into the band via ql-band (the dashboard IS Quick Launch).
        if (document.body && document.body.classList.contains('ql-no-pin')) return;
        var left = bandLeft();
        if (!left) return;

        var btn = document.createElement('button');
        btn.id          = 'lwPinBtn';
        btn.type        = 'button';
        btn.className   = 'ql-pin-btn';
        setPinButtonState(btn, false, null, null);
        left.appendChild(btn);

        btn.addEventListener('click', function (e) {
            e.preventDefault();
            // One modal for both states: the Quick Launch box is pre-checked when
            // the page is already on the row, and unchecking it (then Save, then
            // the Cancel-default confirm) is the unpin.
            openPinModal({
                mode: 'pin-current',
                existingPin: btn.dataset.pinned === '1'
                    ? { quickLaunchId: parseInt(btn.dataset.pinId, 10) || 0, label: btn.dataset.pinLabel || '' }
                    : null,
                onSaved: function (result) {
                    var q = result && result.quickLaunch;
                    if (q && q.quickLaunchId) setPinButtonState(btn, true, q.quickLaunchId, q.label);
                    else setPinButtonState(btn, false, null, null);
                }
            });
        });

        // Initial state -- query the API once auth's settled.
        refreshPinState();
        setTimeout(refreshPinState, 600);
    }

    // ---- Global nav cluster (Back / Forward / Menu / Quick Launch) ------
    // Lives in the icon band's left group. Gives a one-tap path back,
    // forward, home to the main menu, and into the pinned Quick Launch pages
    // from anywhere in the app.
    function injectNavCluster() {
        if (document.getElementById('lwNavCluster')) return;
        var left = bandLeft();
        if (!left) return;

        var cluster = document.createElement('div');
        cluster.id        = 'lwNavCluster';
        cluster.className = 'lw-nav-cluster';

        // Back / Forward walk Leonardo's own navigation stack (picasso-nav.js,
        // Story 3482) so they always land on the page the user actually came
        // from -- not on whatever list page a header link happens to name. The
        // raw history calls stay as the fallback for the handful of pages that
        // never load auth.js, and so a missing picasso-nav.js degrades to the
        // old behaviour rather than a dead button.
        cluster.appendChild(makeNavButton('lwNavBack', 'nav-back', 'Go back', NAVBACK_SVG, function () {
            if (window.LeoNav && window.LeoNav.back()) return;
            if (window.history.length > 1) window.history.back();
            else window.location.href = '/';
        }));
        cluster.appendChild(makeNavButton('lwNavForward', 'nav-forward', 'Go forward', NAVFWD_SVG, function () {
            if (window.LeoNav && window.LeoNav.forward()) return;
            window.history.forward();
        }));
        cluster.appendChild(makeNavButton('lwNavMenu', 'nav-menu', 'Main menu', '&#9776;', function () {
            window.location.href = '/';
        }));
        cluster.appendChild(makeNavButton('lwNavQuickLaunch', 'nav-quicklaunch', 'Quick Launch', '&#9889;', function () {
            openQuickLaunchSheet();
        }));
        cluster.appendChild(makeNavButton('lwNavSearch', 'nav-menu-search', 'Search the menu', SEARCH_SVG, function () {
            openMenuSearchSheet();
        }));

        // Cluster is the first thing in the band's left group.
        left.insertBefore(cluster, left.firstChild);
    }

    // Back / Forward as matched, mirror-image SVG arrows. Tony preferred the
    // look of the forward arrow and asked the back arrow to match its size
    // and position (Story 2162). The old &#8592; / &#8594; font glyphs render
    // at slightly different sizes and baselines depending on the system font,
    // so the pair never looked identical. These two SVGs are exact mirrors of
    // one another at a fixed 18x18 box, so back and forward are pixel-for-pixel
    // the same size and vertically aligned regardless of font.
    var NAVBACK_SVG =
        '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" ' +
        'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>';
    var NAVFWD_SVG =
        '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" ' +
        'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>';

    // Monochrome SVGs (inherit color) so relocated page actions match the
    // cluster's line icons.
    var PRINT_SVG =
        '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true">' +
        '<path d="M6 3h12v4H6V3zm13 5H5a3 3 0 0 0-3 3v6h4v3h12v-3h4v-6a3 3 0 0 0-3-3z' +
        'm-3 11H8v-4h8v4zm3-6.5a1 1 0 1 1 0-2 1 1 0 0 1 0 2z"/></svg>';
    var LOGOUT_SVG =
        '<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" ' +
        'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>' +
        '<polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>';
    var SEARCH_SVG =
        '<svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" ' +
        'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>';

    // Move a standalone Print control from the page header into the band's
    // left group and restyle it as an icon. A Print option that lives inside
    // a vertical "..." overflow menu is NOT in the header strip, so it is left
    // alone (iconizing it would wreck the menu). Modal / in-dialog Print
    // buttons live outside the header too, so they are untouched.
    function relocatePrint() {
        // Idempotency is keyed off a data attribute, NOT the id: a page that
        // supplies its own Print button keeps its own id (pages wire handlers
        // by it), so an id-based guard never trips and the 250ms / 800ms
        // retry passes drag a SECOND Print control into the band. That is the
        // two-print-icons bug from Story 2388.
        if (document.querySelector('[data-lw-band-print]')) return;
        var left   = bandLeft();
        var header = findNavStrip();
        if (!left || !header) return;
        var els = header.querySelectorAll('a, button');
        for (var i = 0; i < els.length; i++) {
            var el = els[i];
            if (el.closest('#lwIconBand')) continue;
            var letters = (el.textContent || '').replace(/[^a-z]/gi, '').toLowerCase();
            if (letters === 'print' || letters === 'printpdf' || letters === 'printtopdf') {
                el.id = el.id || 'lwBandPrint';
                el.setAttribute('data-lw-band-print', '1');
                el.classList.add('lw-nav-btn');
                el.title = 'Print';
                el.setAttribute('aria-label', 'Print');
                el.setAttribute('data-testid', el.getAttribute('data-testid') || 'nav-print');
                el.innerHTML = '<span class="lw-nav-ico">' + PRINT_SVG + '</span>';
                left.appendChild(el);   // physically moves the node (handlers preserved)
                return;
            }
        }
    }

    // The logout icon -- alone on the right of the band so it is hard to
    // fat-finger. Replaces every page's "Logout" / "Sign out" text link.
    function injectLogoutIcon(band) {
        if (document.getElementById('lwBandLogout')) return;
        var btn = document.createElement('button');
        btn.id        = 'lwBandLogout';
        btn.type      = 'button';
        btn.className = 'lw-nav-btn lw-band-logout';
        btn.title     = 'Sign out';
        btn.setAttribute('aria-label', 'Sign out');
        btn.setAttribute('data-testid', 'nav-logout');
        btn.innerHTML = '<span class="lw-nav-ico">' + LOGOUT_SVG + '</span>';
        btn.addEventListener('click', async function (e) {
            e.preventDefault();
            try { await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' }); }
            catch (_) {}
            window.location.href = '/';
        });
        band.appendChild(btn);
    }

    // ---- The Leonardo Desk icon (Story 2450) -----------------------------
    // The Desk is the MDI shell at /desk/desk.html: it holds several Leonardo
    // screens open at once, which the browser cannot do for an installed PWA
    // because standalone display mode has no tabs. This icon carries whatever
    // page you are standing on into the Desk. It is OFF by default and lives
    // behind a per-device switch, because the Desk is an alternate way to work
    // and must never get in the way of the ordinary one. Toggle at
    // Admin -> Global Settings -> Leonardo Desk.
    var DESK_KEY  = 'leo.desk';
    var DESK_PAGE = '/desk/desk.html';

    window.LeoDesk = window.LeoDesk || (function () {
        var on = false;
        try { on = (window.localStorage && window.localStorage.getItem(DESK_KEY) === '1'); }
        catch (_) { /* private browsing etc - default off */ }
        return {
            key: DESK_KEY,
            enabled: on,
            set: function (v) {
                this.enabled = !!v;
                try {
                    if (window.localStorage) {
                        window.localStorage.setItem(DESK_KEY, this.enabled ? '1' : '0');
                    }
                } catch (_) {}
            }
        };
    })();

    function injectDeskIcon() {
        var existing = document.getElementById('lwDeskBtn');
        var wanted = !!(window.LeoDesk && window.LeoDesk.enabled)
                  && window.location.pathname.indexOf('/desk/') !== 0;
        if (!wanted) {
            if (existing && existing.parentNode) existing.parentNode.removeChild(existing);
            return;
        }
        if (existing) return;
        var left = bandLeft();
        if (!left) return;
        left.appendChild(makeNavButton('lwDeskBtn', 'nav-desk-open', 'Open this page in the Leonardo Desk',
            '&#9707;', function () {
                var here = window.location.pathname + window.location.search;
                window.location.href = DESK_PAGE + '?open=' + encodeURIComponent(here);
            }));
    }

    // Called by Global Settings the moment the switch flips, so the icon
    // appears or disappears without a reload.
    function refreshDeskIcon() {
        injectDeskIcon();
    }

    // ---- The page URL row (Story 2320) -----------------------------------
    // The PWA has no browser address bar, so there is no way to read or copy
    // the URL of the page you are standing on. When the per-device "Show page
    // URL" flag is on, this appends a full-width row to the icon band holding
    // a read-only text box with the current absolute URL plus a Copy button.
    //
    // It is a child of the band (not a sibling) on purpose: the band is a
    // wrapping flex row, so a child with flex-basis 100% falls onto its own
    // line and inherits the band's sticky positioning -- the URL row is
    // pinned chrome exactly like the icons above it, and never scrolls off.
    //
    // The flag normally lives on window.LeoUrlBar (picasso-utils.js). The
    // localStorage fallback below covers the handful of pages that pull in
    // quick-launch.js without picasso-utils.js.
    var URLBAR_KEY = 'leo.showUrl';

    function urlBarEnabled() {
        if (window.LeoUrlBar) return !!window.LeoUrlBar.enabled;
        try {
            return !!(window.localStorage && window.localStorage.getItem(URLBAR_KEY) === '1');
        } catch (_) { return false; }
    }

    function injectUrlBar(band) {
        if (!band) return;
        var row = document.getElementById('lwUrlBar');
        if (!urlBarEnabled()) {
            if (row && row.parentNode) row.parentNode.removeChild(row);
            return;
        }
        if (row) { syncUrlBar(); return; }

        row = document.createElement('div');
        row.id        = 'lwUrlBar';
        row.className = 'lw-urlbar';
        row.setAttribute('data-testid', 'url-bar');

        var box = document.createElement('input');
        box.id       = 'lwUrlBarText';
        box.type     = 'text';
        box.readOnly = true;
        box.className = 'lw-urlbar-text';
        box.setAttribute('aria-label', 'Address of this page');
        box.setAttribute('data-testid', 'url-bar-text');
        box.value = location.href;
        // Tap anywhere in the box selects the whole address, so a phone
        // long-press-copy grabs the entire URL rather than one word.
        box.addEventListener('focus', function () { box.select(); });
        box.addEventListener('click', function () { box.select(); });
        row.appendChild(box);

        var copy = document.createElement('button');
        copy.id        = 'lwUrlBarCopy';
        copy.type      = 'button';
        copy.className = 'lw-nav-btn lw-urlbar-copy';
        copy.title     = 'Copy this page address';
        copy.setAttribute('aria-label', 'Copy this page address');
        copy.setAttribute('data-testid', 'url-bar-copy');
        copy.textContent = 'Copy';
        copy.addEventListener('click', function (e) {
            e.preventDefault();
            copyUrlToClipboard(box.value);
        });
        row.appendChild(copy);

        band.appendChild(row);
        installUrlWatch();
    }

    // Keep the box in step with the address. Several pages rewrite their own
    // query string as the user moves around inside them (the Reading Nook's
    // tabs, the Library shelves), which fires no navigation event of its own.
    function syncUrlBar() {
        var box = document.getElementById('lwUrlBarText');
        if (box && box.value !== location.href) box.value = location.href;
    }

    // Watch every way the address can change without a page load: the two
    // history writers (wrapped once, chaining to the originals) plus the two
    // events the browser raises on its own. No timers.
    var urlWatchInstalled = false;
    function installUrlWatch() {
        if (urlWatchInstalled) return;
        urlWatchInstalled = true;
        var methods = ['pushState', 'replaceState'];
        for (var i = 0; i < methods.length; i++) {
            (function (name) {
                var orig = window.history[name];
                if (typeof orig !== 'function') return;
                window.history[name] = function () {
                    var r = orig.apply(this, arguments);
                    try { syncUrlBar(); } catch (_) {}
                    return r;
                };
            })(methods[i]);
        }
        window.addEventListener('popstate',   syncUrlBar);
        window.addEventListener('hashchange', syncUrlBar);
    }

    // Clipboard API first (it is the only path that works from a PWA on
    // iOS / Android); the select + execCommand path is the fallback for
    // older browsers and for insecure-origin loads where the API is absent.
    function copyUrlToClipboard(text) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(function () {
                toast('Address copied.');
            }, function () {
                if (legacyCopy()) toast('Address copied.');
                else toast('Copy failed - select the address and copy it.');
            });
            return;
        }
        if (legacyCopy()) toast('Address copied.');
        else toast('Copy failed - select the address and copy it.');

        function legacyCopy() {
            var box = document.getElementById('lwUrlBarText');
            if (!box) return false;
            try {
                box.focus();
                box.select();
                box.setSelectionRange(0, box.value.length);
                return document.execCommand('copy');
            } catch (_) { return false; }
        }
    }

    // Called by Global Settings the moment the toggle flips, so the row
    // appears or disappears without a reload.
    function refreshUrlBar() {
        injectUrlBar(document.getElementById('lwIconBand'));
    }

    // Retire redundant text affordances from the page header now that the
    // band owns them: hide "home" / back-to-root links (the Menu icon covers
    // it -- Tony: "nothing needs a home link anymore") and any "Logout" /
    // "Sign out" text link (the band's logout icon covers it). If hiding
    // those empties a slim .lw-nav strip, hide the whole strip so it doesn't
    // leave a blank row. Idempotent; safe to run on every inject retry.
    function tidyHeaders() {
        var strips = [];
        var header = findNavStrip();
        if (header) strips.push(header);
        var lwnav = document.querySelector('.lw-nav');
        if (lwnav && lwnav.id !== 'lwIconBand' && strips.indexOf(lwnav) < 0) strips.push(lwnav);

        for (var s = 0; s < strips.length; s++) {
            var strip = strips[s];
            if (strip.id === 'lwIconBand') continue;
            var els = strip.querySelectorAll('a, button');
            for (var i = 0; i < els.length; i++) {
                var el = els[i];
                if (el.closest('#lwIconBand')) continue;
                var txt     = (el.textContent || '').replace(/\s+/g, ' ').trim();
                var href    = el.getAttribute('href') || '';
                var letters = txt.replace(/[^a-z]/gi, '').toLowerCase();
                var isHome = el.getAttribute('data-testid') === 'nav-home'
                          || letters === 'home'
                          || (el.tagName === 'A' && (href === '/' || href === '/index.html'));
                if (isHome || letters === 'logout' || letters === 'signout') {
                    el.style.display = 'none';
                }
            }
        }

        // Hide an emptied slim nav strip (one that only held a home link).
        if (lwnav && lwnav.id !== 'lwIconBand') {
            var stillVisible = false;
            var kids = lwnav.querySelectorAll('a, button');
            for (var k = 0; k < kids.length; k++) {
                if (kids[k].closest('#lwIconBand')) continue;
                if (kids[k].style.display !== 'none') { stillVisible = true; break; }
            }
            if (!stillVisible) lwnav.style.display = 'none';
        }
    }

    function makeNavButton(id, testid, title, iconEntity, onClick) {
        var btn = document.createElement('button');
        btn.type      = 'button';
        btn.id        = id;
        btn.className = 'lw-nav-btn';
        btn.title     = title;
        btn.setAttribute('aria-label', title);
        btn.setAttribute('data-testid', testid);
        btn.innerHTML = '<span class="lw-nav-ico">' + iconEntity + '</span>';
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            onClick();
        });
        return btn;
    }

    // ---- Quick Launch sheet ----------------------------------------------
    // A lightweight top sheet listing the user's pinned pages so they can be
    // launched from any page without a round-trip through the dashboard.
    // Reuses the pin list from /api/quick-launch.
    var qlSheetEl = null;
    var lastPins  = null;   // last /api/quick-launch result, for instant sheet open

    function ensureQuickLaunchSheet() {
        if (qlSheetEl) return qlSheetEl;
        ensureCss();
        var bg = document.createElement('div');
        bg.className = 'ql-modal-bg lw-qlsheet-bg';
        bg.id = 'lwQuickLaunchSheet';
        bg.innerHTML =
            '<div class="lw-qlsheet" role="dialog" aria-modal="true" data-testid="nav-quicklaunch-sheet">' +
                '<div class="lw-qlsheet-head">' +
                    '<h2>Quick Launch</h2>' +
                    '<button type="button" class="lw-qlsheet-close" data-testid="nav-quicklaunch-close" title="Close" aria-label="Close">&#10005;</button>' +
                '</div>' +
                '<div class="lw-qlsheet-list" id="lwQuickLaunchList" data-testid="nav-quicklaunch-list"></div>' +
            '</div>';
        document.body.appendChild(bg);
        bg.addEventListener('click', function (e) {
            if (e.target === bg) closeQuickLaunchSheet();
        });
        bg.querySelector('.lw-qlsheet-close').addEventListener('click', closeQuickLaunchSheet);
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && bg.classList.contains('show')) closeQuickLaunchSheet();
        });
        qlSheetEl = bg;
        return bg;
    }

    function qlSheetRow(pin) {
        var a = document.createElement('a');
        a.className = 'lw-qlsheet-row';
        a.href      = pin.url;
        a.setAttribute('data-testid', 'nav-quicklaunch-row');
        var ico = document.createElement('span');
        ico.className = 'lw-qlsheet-ico';
        if (pin.iconKey && pin.iconKey.length > 0) {
            ico.textContent = pin.iconKey;
        } else {
            ico.classList.add('placeholder');
            ico.textContent = (pin.label || '?').trim().substring(0, 1).toUpperCase();
        }
        var lbl = document.createElement('span');
        lbl.className   = 'lw-qlsheet-label';
        lbl.textContent = pin.label || '';
        a.appendChild(ico);
        a.appendChild(lbl);
        return a;
    }

    // Render the sheet body. pins === null means "still loading"; an array
    // (possibly empty) means we have an answer. The Full dashboard row is
    // always appended so the sheet is useful even with no pins / a failure.
    function renderQuickLaunchList(pins) {
        var list = document.getElementById('lwQuickLaunchList');
        if (!list) return;
        list.innerHTML = '';
        if (pins === null) {
            var loading = document.createElement('div');
            loading.className   = 'lw-qlsheet-loading';
            loading.textContent = 'Loading your pins';
            list.appendChild(loading);
        } else {
            var arr = Array.isArray(pins) ? pins : [];
            for (var i = 0; i < arr.length; i++) list.appendChild(qlSheetRow(arr[i]));
            if (arr.length === 0) {
                var empty = document.createElement('div');
                empty.className   = 'lw-qlsheet-empty';
                empty.textContent = 'No pinned pages yet. Tap Pin on any page to add one.';
                list.appendChild(empty);
            }
        }
        // Always offer a jump to the full dashboard / main menu.
        var home = document.createElement('a');
        home.className = 'lw-qlsheet-row lw-qlsheet-home';
        home.href      = '/';
        home.setAttribute('data-testid', 'nav-quicklaunch-dashboard');
        home.innerHTML = '<span class="lw-qlsheet-ico">&#9776;</span><span class="lw-qlsheet-label">Full dashboard</span>';
        list.appendChild(home);

        // ...and a way into Manage Quick Launch, which is where reordering,
        // renaming, and the landing-page setting live. Without this row the
        // only route was a link buried on the dashboard.
        var manage = document.createElement('a');
        manage.className = 'lw-qlsheet-row lw-qlsheet-home';
        manage.href      = '/quick-launch/manage.html';
        manage.setAttribute('data-testid', 'nav-quicklaunch-manage');
        manage.innerHTML = '<span class="lw-qlsheet-ico">&#9881;</span>' +
                           '<span class="lw-qlsheet-label">Manage Quick Launch</span>';
        list.appendChild(manage);

        // Echo (Story 5653) has no Leonardo Desk, so the sheet carries no Desk switch.
    }

    /* The Leonardo Desk switch, two taps from anywhere (Story 3478).
     *
     * The switch itself has always lived at Admin -> Global Settings ->
     * Leonardo Desk, which is four screens away and a page Tony otherwise
     * has no reason to open -- so an occasional "put this page in the Desk"
     * cost more than it was worth. It belongs here rather than as another
     * band icon: the band is already the tightest real estate in the app
     * (the very thing the other half of this Story fixes), and a switch used
     * a few times a week should not permanently occupy a slot next to Back.
     *
     * Flipping it repaints the band in place -- no reload, and no round trip
     * to the server: the flag is per-device localStorage, exactly as the
     * Global Settings page writes it. Both surfaces read and write the same
     * window.LeoDesk, so they can never disagree. */
    function deskToggleRow() {
        var row = document.createElement('button');
        row.type      = 'button';
        row.className = 'lw-qlsheet-row lw-qlsheet-home lw-qlsheet-toggle';
        row.setAttribute('data-testid', 'nav-quicklaunch-desk-toggle');

        function paint() {
            var on = !!(window.LeoDesk && window.LeoDesk.enabled);
            row.setAttribute('aria-pressed', on ? 'true' : 'false');
            row.innerHTML =
                '<span class="lw-qlsheet-ico">&#9707;</span>' +
                '<span class="lw-qlsheet-label">Leonardo Desk icon</span>' +
                '<span class="lw-qlsheet-state' + (on ? ' is-on' : '') + '">' +
                (on ? 'On' : 'Off') + '</span>';
            row.title = on
                ? 'The Desk icon is showing in the tool bar strip. Tap to hide it'
                : 'Show the Desk icon in the tool bar strip so you can send this page to the Desk';
        }

        row.addEventListener('click', function (e) {
            e.preventDefault();
            if (!window.LeoDesk) return;
            window.LeoDesk.set(!window.LeoDesk.enabled);
            refreshDeskIcon();
            paint();
        });

        paint();
        return row;
    }

    // Time-boxed pin fetch that can never hang the sheet. Uses a direct
    // abortable fetch rather than LWAuth.apiGet, whose 401 path navigates
    // away (which would yank the sheet out from under the user). Returns the
    // pin array on success, or null on any failure / timeout.
    async function fetchPins() {
        var ctrl = new AbortController();
        var tid  = setTimeout(function () { ctrl.abort(); }, 6000);
        try {
            var headers = (window.LWAuth && window.LWAuth.bearerHeader) ? window.LWAuth.bearerHeader() : {};
            var res = await fetch('/api/quick-launch', {
                cache: 'no-store', credentials: 'same-origin',
                headers: headers, signal: ctrl.signal
            });
            if (!res.ok) return null;
            var data = await res.json();
            return Array.isArray(data) ? data : null;
        } catch (_) {
            return null;
        } finally {
            clearTimeout(tid);
        }
    }

    async function openQuickLaunchSheet() {
        var bg = ensureQuickLaunchSheet();
        bg.classList.add('show');
        // Render instantly from cache (or a brief loading state on the very
        // first open) so the sheet is useful immediately and never sits on a
        // spinner forever.
        renderQuickLaunchList(lastPins);
        var fresh = await fetchPins();
        if (!qlSheetEl || !qlSheetEl.classList.contains('show')) return;
        if (fresh) {
            lastPins = fresh;
            renderQuickLaunchList(fresh);
        } else if (lastPins === null) {
            // Fetch failed and we have nothing cached -- show the empty state
            // (with Full dashboard) instead of leaving the loading text up.
            renderQuickLaunchList([]);
        }
    }

    function closeQuickLaunchSheet() {
        if (qlSheetEl) qlSheetEl.classList.remove('show');
    }

    // ---- Menu search -----------------------------------------------------
    // A find-as-you-type overlay over the whole dashboard menu, reachable from
    // the nav cluster's magnifier (and Ctrl/Cmd+K) on every page. It answers
    // "the menu has too many things on it now": type a few letters, get a
    // ranked list of matching destinations, Enter or tap to jump. Unobtrusive
    // -- one small icon, nothing on screen until opened.
    //
    // The catalog IS the dashboard's own link list. On the dashboard we parse
    // the live DOM; everywhere else we fetch /index.html once and cache it. So
    // the search stays in lockstep with the menu with zero duplication -- add
    // a link to the dashboard and it becomes searchable automatically.
    var msSheetEl        = null;
    var msCatalog        = null;   // [{section,label,url,target}] once loaded
    var msCatalogPromise = null;
    var msResults        = [];     // current filtered + ranked entries
    var msActive         = -1;     // active row index for keyboard nav

    // Section title text only -- skip the SVG icon and collapse chevron that
    // some dashboard h2s carry as element children.
    function msSectionName(h2) {
        var t = '';
        for (var i = 0; i < h2.childNodes.length; i++) {
            var n = h2.childNodes[i];
            if (n.nodeType === 3) t += n.textContent;
        }
        return t.replace(/\s+/g, ' ').trim();
    }

    function msParseMenu(root) {
        var out  = [];
        var secs = root.querySelectorAll('.report-links');
        for (var s = 0; s < secs.length; s++) {
            var h2      = secs[s].querySelector('h2');
            var section = h2 ? msSectionName(h2) : '';
            var links   = secs[s].querySelectorAll('a');
            for (var i = 0; i < links.length; i++) {
                var a    = links[i];
                var href = a.getAttribute('href') || '';
                if (!href) continue;
                var label = (a.textContent || '').replace(/\s+/g, ' ').trim();
                if (!label) continue;
                out.push({
                    section: section,
                    label:   label,
                    url:     href,
                    target:  a.getAttribute('target') || ''
                });
            }
        }
        return out;
    }

    function getMenuCatalog() {
        if (msCatalog) return Promise.resolve(msCatalog);
        if (msCatalogPromise) return msCatalogPromise;
        // On the dashboard the menu is already in the DOM -- parse it directly.
        if (document.querySelector('.report-links')) {
            msCatalog = msParseMenu(document);
            return Promise.resolve(msCatalog);
        }
        // Elsewhere, fetch the dashboard shell once. index.html carries the
        // full (login-hidden) menu markup, so no auth is needed to read it.
        msCatalogPromise = fetch('/index.html', { credentials: 'same-origin' })
            .then(function (r) { return r.text(); })
            .then(function (html) {
                var doc = new DOMParser().parseFromString(html, 'text/html');
                msCatalog = msParseMenu(doc);
                return msCatalog;
            })
            .catch(function () { msCatalog = []; return msCatalog; });
        return msCatalogPromise;
    }

    function msEscape(s) {
        return String(s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // Escape the label, then wrap the first case-insensitive match of q in
    // <mark>. Escaping happens per-slice so the highlight can't inject markup.
    function msHighlight(label, q) {
        if (!q) return msEscape(label);
        var at = label.toLowerCase().indexOf(q);
        if (at < 0) return msEscape(label);
        return msEscape(label.substring(0, at)) +
               '<mark>' + msEscape(label.substring(at, at + q.length)) + '</mark>' +
               msEscape(label.substring(at + q.length));
    }

    // Score an entry against a lowercased query. -1 means no match. Higher is
    // better: exact label > label prefix > word-start in label > label
    // substring > section prefix > section substring.
    function msScore(e, q) {
        var label   = e.label.toLowerCase();
        var section = (e.section || '').toLowerCase();
        var inLabel = label.indexOf(q) >= 0;
        if (!inLabel && section.indexOf(q) < 0) return -1;
        if (label === q) return 100;
        if (label.indexOf(q) === 0) return 80;
        if ((' ' + label).indexOf(' ' + q) >= 0) return 60;
        if (inLabel) return 45;
        if (section.indexOf(q) === 0) return 25;
        return 12;
    }

    function msFilter(catalog, raw) {
        var q = (raw || '').trim().toLowerCase();
        if (!q) return catalog.slice(0, 60);   // empty query -> whole menu, in menu order
        var scored = [];
        for (var i = 0; i < catalog.length; i++) {
            var sc = msScore(catalog[i], q);
            if (sc >= 0) scored.push({ e: catalog[i], sc: sc, i: i });
        }
        scored.sort(function (a, b) { return (b.sc - a.sc) || (a.i - b.i); });
        var out = [];
        for (var j = 0; j < scored.length && j < 60; j++) out.push(scored[j].e);
        return out;
    }

    function ensureMenuSearchSheet() {
        if (msSheetEl) return msSheetEl;
        ensureCss();
        var bg = document.createElement('div');
        bg.className = 'ql-modal-bg lw-mssheet-bg';
        bg.id = 'lwMenuSearchSheet';
        bg.innerHTML =
            '<div class="lw-mssheet" role="dialog" aria-modal="true" data-testid="menu-search-sheet">' +
                '<div class="lw-mssheet-head">' +
                    '<span class="lw-mssheet-ico">' + SEARCH_SVG + '</span>' +
                    '<input type="text" class="lw-mssheet-input" id="lwMenuSearchInput" ' +
                        'data-testid="menu-search-input" placeholder="Search the menu" ' +
                        'autocomplete="off" autocapitalize="off" autocorrect="off" ' +
                        'spellcheck="false" data-voice-skip="true">' +
                    '<button type="button" class="lw-mssheet-close" data-testid="menu-search-close" ' +
                        'title="Close" aria-label="Close">&#10005;</button>' +
                '</div>' +
                '<div class="lw-mssheet-list" id="lwMenuSearchList" data-testid="menu-search-list"></div>' +
            '</div>';
        document.body.appendChild(bg);

        bg.addEventListener('click', function (e) {
            if (e.target === bg) closeMenuSearchSheet();
        });
        bg.querySelector('.lw-mssheet-close').addEventListener('click', closeMenuSearchSheet);

        var input = bg.querySelector('#lwMenuSearchInput');
        input.addEventListener('input', function () { renderMenuResults(input.value); });
        input.addEventListener('keydown', msInputKeydown);

        msSheetEl = bg;
        return bg;
    }

    function msRow(entry, q, idx) {
        var a = document.createElement('a');
        a.className = 'lw-mssheet-row';
        a.href = entry.url;
        a.setAttribute('data-testid', 'menu-search-row');
        a.dataset.idx = String(idx);
        if (entry.target) a.target = entry.target;

        var lbl = document.createElement('span');
        lbl.className = 'lw-mssheet-label';
        lbl.innerHTML = msHighlight(entry.label, q);
        a.appendChild(lbl);

        if (entry.section) {
            var sec = document.createElement('span');
            sec.className   = 'lw-mssheet-section';
            sec.textContent = entry.section;
            a.appendChild(sec);
        }
        a.addEventListener('mouseenter', function () { msSetActive(idx); });
        a.addEventListener('click', function () { closeMenuSearchSheet(); });
        return a;
    }

    function renderMenuResults(raw) {
        var list = document.getElementById('lwMenuSearchList');
        if (!list) return;
        var q = (raw || '').trim().toLowerCase();
        list.innerHTML = '';

        if (!msCatalog) {
            msResults = [];
            msActive  = -1;
            var loading = document.createElement('div');
            loading.className   = 'lw-mssheet-empty';
            loading.textContent = 'Loading menu';
            list.appendChild(loading);
            return;
        }

        msResults = msFilter(msCatalog, raw);
        msActive  = msResults.length ? 0 : -1;

        if (msResults.length === 0) {
            var empty = document.createElement('div');
            empty.className   = 'lw-mssheet-empty';
            empty.textContent = 'No menu items match "' + raw.trim() + '"';
            list.appendChild(empty);
            return;
        }
        for (var i = 0; i < msResults.length; i++) {
            list.appendChild(msRow(msResults[i], q, i));
        }
        msPaintActive();
    }

    function msSetActive(idx) {
        msActive = idx;
        msPaintActive();
    }

    function msPaintActive() {
        var list = document.getElementById('lwMenuSearchList');
        if (!list) return;
        var rows = list.querySelectorAll('.lw-mssheet-row');
        for (var i = 0; i < rows.length; i++) {
            var on = (i === msActive);
            rows[i].classList.toggle('active', on);
            if (on && rows[i].scrollIntoView) rows[i].scrollIntoView({ block: 'nearest' });
        }
    }

    function msNavigateActive() {
        if (msActive < 0 || msActive >= msResults.length) return;
        var entry = msResults[msActive];
        closeMenuSearchSheet();
        if (entry.target === '_blank') window.open(entry.url, '_blank', 'noopener');
        else                           window.location.href = entry.url;
    }

    function msInputKeydown(e) {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (msResults.length) { msActive = (msActive + 1) % msResults.length; msPaintActive(); }
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (msResults.length) { msActive = (msActive - 1 + msResults.length) % msResults.length; msPaintActive(); }
        } else if (e.key === 'Enter') {
            e.preventDefault();
            msNavigateActive();
        } else if (e.key === 'Escape') {
            e.preventDefault();
            closeMenuSearchSheet();
        }
    }

    async function openMenuSearchSheet() {
        var bg = ensureMenuSearchSheet();
        bg.classList.add('show');
        var input = document.getElementById('lwMenuSearchInput');
        if (input) input.value = '';
        renderMenuResults('');                       // "Loading menu" until the catalog resolves
        setTimeout(function () { if (input) input.focus(); }, 30);   // mobile needs a beat before focus
        await getMenuCatalog();
        if (!msSheetEl || !msSheetEl.classList.contains('show')) return;
        renderMenuResults(input ? input.value : '');
    }

    function closeMenuSearchSheet() {
        if (msSheetEl) msSheetEl.classList.remove('show');
    }

    // A dashboard-style page with no icon band still needs a jump-to search, so
    // drop one small magnifier into its header. Skipped when the band is
    // present -- the band's nav cluster already carries the search icon.
    function injectDashboardSearchButton() {
        if (document.getElementById('lwDashSearchBtn')) return;
        if (document.getElementById('lwIconBand')) return;
        if (!document.body || !document.body.classList.contains('ql-no-pin')) return;
        var header = document.querySelector('.picasso-header');
        if (!header) return;
        var btn = document.createElement('button');
        btn.id   = 'lwDashSearchBtn';
        btn.type = 'button';
        btn.title = 'Search the menu';
        btn.setAttribute('aria-label', 'Search the menu');
        btn.setAttribute('data-testid', 'menu-search-dash-btn');
        btn.innerHTML = SEARCH_SVG;
        btn.addEventListener('click', openMenuSearchSheet);
        var firstBtn = header.querySelector('button');
        if (firstBtn) header.insertBefore(btn, firstBtn);
        else          header.appendChild(btn);
    }

    // Ctrl/Cmd+K toggles menu search from anywhere -- bound once, globally, so
    // it works on the dashboard (no band) and every content page alike.
    var msKeyBound = false;
    function bindMenuSearchHotkey() {
        if (msKeyBound) return;
        msKeyBound = true;
        document.addEventListener('keydown', function (e) {
            if ((e.ctrlKey || e.metaKey) && !e.altKey && !e.shiftKey &&
                (e.key === 'k' || e.key === 'K')) {
                e.preventDefault();
                if (msSheetEl && msSheetEl.classList.contains('show')) closeMenuSearchSheet();
                else openMenuSearchSheet();
            }
        });
    }

    // Idempotent -- safe to call on every inject retry, band or no band.
    function wireMenuSearch() {
        bindMenuSearchHotkey();
        injectDashboardSearchButton();
    }

    // ---- Default label cleanup ------------------------------------------
    function defaultLabel() {
        var t = (document.title || '').trim();
        // Strip a " -- LeonardoWeb" or " - LeonardoWeb" tail if present.
        t = t.replace(/\s+[-\u2013\u2014]\s+LeonardoWeb\s*$/i, '').trim();
        return clampLabel(t) || 'Untitled page';
    }

    // Cap a suggested label at 80 chars to match the column length.
    function clampLabel(t) {
        t = (t || '').trim();
        if (t.length > 80) t = t.substring(0, 80);
        return t;
    }

    function currentUrl() {
        // Pin only the path + querystring -- host changes but the URL is
        // meaningful within LeonardoWeb regardless of how you reach it.
        return normalizeUrl(location.pathname + (location.search || ''));
    }

    // Treat the legacy "/reading-nook/reading-nook.html" (no query) as the
    // explicit "?view=inbox" URL the page now sets on load. That way Tony's
    // existing Inbox pin still matches after we started writing the view
    // into the URL -- without this, every nook tab would show "not pinned"
    // because no pin's URL equals the bare path anymore.
    function normalizeUrl(url) {
        if (!url) return url;
        if (url === '/reading-nook/reading-nook.html') {
            return '/reading-nook/reading-nook.html?view=inbox';
        }
        return url;
    }

    // ---- Pin modal -------------------------------------------------------
    // Used in two modes:
    //   - "Pin this page" -- URL pre-set from current page, not editable
    //   - "Edit pin" / "Add custom" (from manage page) -- URL editable
    var pinModalEl = null;
    var pinModalState = null;

    // ---- Icon picker paging ---------------------------------------------
    // The picker spans ICON_PAGES (page 1 = curated, 2-10 = full catalog).
    // selectedIconValue holds the chosen icon ('' = None) independently of
    // which page is currently rendered, so a pick on page 7 still saves even
    // after the user pages back to page 1.
    var selectedIconValue = '';
    var currentIconPage   = 0;

    // Build one picker tile. An empty value renders the "None" clear-tile.
    // nameLower is the glyph's own searchable name ('' for built-in pages).
    function buildIconTile(iconVal, catLower, nameLower) {
        var t = document.createElement('div');
        t.className = 'ql-icon-tile' + (iconVal === '' ? ' none' : '');
        t.textContent = (iconVal === '') ? 'None' : iconVal;
        t.dataset.icon = iconVal;
        t.dataset.cat  = catLower;
        t.dataset.name = nameLower || '';
        if (nameLower) t.title = nameLower;
        if (iconVal === selectedIconValue) t.classList.add('selected');
        return t;
    }

    // Render a single page's categories + tiles into the grid (replacing
    // whatever was there), then re-apply the active-page pill and the filter.
    function renderIconPage(idx) {
        var grid  = document.getElementById('qlIcons');
        var pager = document.getElementById('qlIconPager');
        if (!grid) return;
        currentIconPage = idx;
        grid.innerHTML = '';
        // A "None" tile leads every page so the icon can always be cleared.
        grid.appendChild(buildIconTile('', 'none'));
        var cats = (ICON_PAGES[idx] && ICON_PAGES[idx].cats) || [];
        for (var i = 0; i < cats.length; i++) {
            var catLower = (cats[i].cat || '').toLowerCase();
            var heading = document.createElement('div');
            heading.className = 'ql-icon-cat';
            heading.textContent = cats[i].cat;
            heading.dataset.cat = catLower;
            grid.appendChild(heading);
            var items = cats[i].items || [];
            for (var j = 0; j < items.length; j++) {
                grid.appendChild(buildIconTile(iconGlyph(items[j]), catLower, iconLabel(items[j]).toLowerCase()));
            }
        }
        if (pager) {
            var btns = pager.children;
            for (var b = 0; b < btns.length; b++) {
                btns[b].classList.toggle('active', String(idx) === btns[b].dataset.page);
            }
        }
    }

    // Search runs across EVERY page, not just the one on screen -- typing
    // "guitar" finds the guitar wherever it lives. A glyph matches on its own
    // name ("waving hand") or on its category ("hand fingers open"). Results
    // are capped so a one-letter query cannot lay out thousands of tiles.
    var ICON_SEARCH_MAX = 400;

    function renderIconSearch(q) {
        var grid = document.getElementById('qlIcons');
        if (!grid) return;
        grid.innerHTML = '';
        grid.appendChild(buildIconTile('', 'none', ''));
        var shown = 0;
        var seen = {};
        for (var p = 0; p < ICON_PAGES.length && shown < ICON_SEARCH_MAX; p++) {
            var cats = (ICON_PAGES[p] && ICON_PAGES[p].cats) || [];
            for (var i = 0; i < cats.length && shown < ICON_SEARCH_MAX; i++) {
                var catLower = (cats[i].cat || '').toLowerCase();
                var catHit = catLower.indexOf(q) >= 0;
                var items = cats[i].items || [];
                for (var j = 0; j < items.length && shown < ICON_SEARCH_MAX; j++) {
                    var glyph = iconGlyph(items[j]);
                    var nameLower = iconLabel(items[j]).toLowerCase();
                    if (!catHit && nameLower.indexOf(q) < 0) continue;
                    if (seen[glyph]) continue;
                    seen[glyph] = true;
                    grid.appendChild(buildIconTile(glyph, catLower, nameLower));
                    shown++;
                }
            }
        }
        var heading = document.createElement('div');
        heading.className = 'ql-icon-cat';
        heading.textContent = shown === 0
            ? 'No icons match ' + q
            : (shown >= ICON_SEARCH_MAX ? 'First ' + shown + ' matches -- keep typing to narrow' : shown + ' matches');
        grid.insertBefore(heading, grid.children[1] || null);
    }

    // One pill per page. Tapping switches the rendered page and clears any
    // search text (the results view is page-less, so leaving the box filled
    // would immediately paint over the page the user just asked for).
    // Rebuilt from scratch whenever the page list changes -- i.e. when the
    // full catalog finishes loading and replaces the built-in pages.
    function rebuildIconPager() {
        var pager = document.getElementById('qlIconPager');
        if (!pager) return;
        pager.innerHTML = '';
        for (var p = 0; p < ICON_PAGES.length; p++) {
            (function (idx) {
                var b = document.createElement('button');
                b.type = 'button';
                b.className = 'ql-page-btn';
                b.dataset.page = String(idx);
                b.dataset.testid = 'icon-page-' + (idx + 1);
                b.textContent = ICON_PAGES[idx].name;
                b.addEventListener('click', function () {
                    var filterInput = document.getElementById('qlIconFilter');
                    if (filterInput) filterInput.value = '';
                    renderIconPage(idx);
                });
                pager.appendChild(b);
            })(p);
        }
    }

    // Repaint the grid for whatever the filter box currently holds.
    function applyIconFilter() {
        var filterInput = document.getElementById('qlIconFilter');
        var q = ((filterInput && filterInput.value) || '').trim().toLowerCase();
        if (q) renderIconSearch(q); else renderIconPage(currentIconPage);
    }

    // Which page holds a given icon value (0 = page 1 / not found).
    function pageIndexForIcon(iconVal) {
        if (!iconVal) return 0;
        for (var i = 0; i < ICON_PAGES.length; i++) {
            var cats = (ICON_PAGES[i] && ICON_PAGES[i].cats) || [];
            for (var j = 0; j < cats.length; j++) {
                var items = cats[j].items || [];
                for (var k = 0; k < items.length; k++) {
                    if (iconGlyph(items[k]) === iconVal) return i;
                }
            }
        }
        return 0;
    }

    function ensurePinModal() {
        if (pinModalEl) return pinModalEl;

        var bg = document.createElement('div');
        bg.className = 'ql-modal-bg';
        bg.id = 'qlPinModal';
        bg.innerHTML =
            '<div class="ql-modal" role="dialog" aria-modal="true">' +
                '<h2 id="qlModalTitle">Pin to Quick Launch</h2>' +
                '<div class="ql-sub" id="qlModalSub">Tap an icon below and confirm.</div>' +
                '<form id="qlPinForm" autocomplete="off">' +
                    '<div>' +
                        '<label for="qlLabel">Label</label>' +
                        '<input type="text" id="qlLabel" maxlength="80" required>' +
                    '</div>' +
                    '<div id="qlUrlWrap">' +
                        '<label for="qlUrl">URL</label>' +
                        '<div class="ql-url" id="qlUrlReadonly"></div>' +
                        '<input type="text" id="qlUrl" maxlength="400" style="display:none">' +
                    '</div>' +
                    '<div id="qlDestWrap" data-testid="pin-destinations">' +
                        '<label>Pin to</label>' +
                        '<label class="ql-dest"><input type="checkbox" id="qlDestQuick" data-testid="pin-dest-quick-launch"> Quick Launch <span class="ql-dest-note">the row on the dashboard</span></label>' +
                        // Echo (Story 5653) has no Entity IV, so Quick Launch is the only place to pin to.
                    '</div>' +
                    '<div>' +
                        '<label>Icon</label>' +
                        '<div class="ql-icon-pager" id="qlIconPager" data-testid="icon-pager"></div>' +
                        '<input type="text" class="ql-icon-filter" id="qlIconFilter" data-testid="icon-search" placeholder="Search icons by name" autocomplete="off">' +
                        '<div class="ql-icons" id="qlIcons"></div>' +
                    '</div>' +
                '</form>' +
                '<div class="ql-actions">' +
                    // Cancel is the default-focused, primary-styled button on
                    // purpose -- Tony wants every pin/save to require a
                    // deliberate tap on the action button. Enter accidentally
                    // pressed dismisses the modal instead of committing.
                    '<button type="button" class="ql-btn ql-btn-secondary" id="qlSave">Save</button>' +
                    '<button type="button" class="ql-btn ql-btn-primary"   id="qlCancel">Cancel</button>' +
                '</div>' +
            '</div>';
        document.body.appendChild(bg);

        document.getElementById('qlCancel').addEventListener('click', closePinModal);
        document.getElementById('qlSave').addEventListener('click', submitPinModal);
        wireRecordDestination();

        // Build the paged icon picker. Page 1 is the curated set; pages 2-10
        // are the full emoji catalog. Only the active page is rendered into
        // the grid (lazy) so we never lay out ~2000 tiles at once.
        var grid   = document.getElementById('qlIcons');
        var filterInput = document.getElementById('qlIconFilter');

        rebuildIconPager();

        // Tap a tile -> remember the chosen icon and highlight it. Selection
        // is tracked in selectedIconValue (not the DOM) so it survives paging.
        grid.addEventListener('click', function (e) {
            var tile = e.target.closest('.ql-icon-tile');
            if (!tile) return;
            selectedIconValue = tile.dataset.icon || '';
            var prev = grid.querySelector('.ql-icon-tile.selected');
            if (prev) prev.classList.remove('selected');
            tile.classList.add('selected');
        });

        // Filter matches category names within the CURRENT page.
        filterInput.addEventListener('input', applyIconFilter);

        // Render the first page now; openPinModal re-targets as needed.
        renderIconPage(0);

        // Escape closes.
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && bg.classList.contains('show')) closePinModal();
        });

        pinModalEl = bg;
        return bg;
    }

    /**
     * Open the modal.
     * @param {object} opts
     *   - mode: 'pin-current' | 'add-custom' | 'edit'
     *   - id, label, url, iconKey for 'edit' / 'add-custom'
     *   - onSaved: function (savedPin) called after a successful save
     */
    function openPinModal(opts) {
        opts = opts || { mode: 'pin-current' };
        ensureCss();
        ensurePinModal();
        pinModalState = opts;

        var titleEl = document.getElementById('qlModalTitle');
        var subEl   = document.getElementById('qlModalSub');
        var labelIn = document.getElementById('qlLabel');
        var urlRo   = document.getElementById('qlUrlReadonly');
        var urlIn   = document.getElementById('qlUrl');
        var saveBtn = document.getElementById('qlSave');

        if (opts.mode === 'edit') {
            titleEl.textContent = 'Edit pin';
            subEl.textContent   = 'Update the label, icon, or URL.';
            labelIn.value       = opts.label || '';
            urlRo.style.display = 'none';
            urlIn.style.display = '';
            urlIn.value         = opts.url || '';
            saveBtn.textContent = 'Save';
            hideDestinations();
        } else if (opts.mode === 'add-custom') {
            titleEl.textContent = 'Add a custom pin';
            subEl.textContent   = 'Type a label and a relative URL (e.g. /checklists/checklists.html).';
            labelIn.value       = opts.label || '';
            urlRo.style.display = 'none';
            urlIn.style.display = '';
            urlIn.value         = opts.url || '';
            saveBtn.textContent = 'Save';
            hideDestinations();
        } else if (opts.mode === 'pin-target') {
            // One record off a list page (a debate, a research project): the
            // caller fixes the URL and suggests the label; the user still
            // picks the icon and can retitle it. Story 2428.
            titleEl.textContent = 'Pin to Quick Launch';
            subEl.textContent   = 'Pinned items appear at the top of the Echo main menu.';
            labelIn.value       = clampLabel(opts.label);
            urlIn.value         = opts.url || '';
            urlRo.textContent   = opts.url || '';
            urlRo.style.display = '';
            urlIn.style.display = 'none';
            saveBtn.textContent = 'Save';
            hideDestinations();
        } else {
            // The current page (Story 3485): choose where it lives. Both boxes
            // reflect what already exists; Save applies the difference.
            titleEl.textContent = 'Pin this page';
            subEl.textContent   = 'Tick where this page should be. Untick a box to remove it from there.';
            labelIn.value       = opts.existingPin && opts.existingPin.label ? clampLabel(opts.existingPin.label) : defaultLabel();
            urlIn.value         = currentUrl();
            urlRo.textContent   = currentUrl();
            urlRo.style.display = '';
            urlIn.style.display = 'none';
            saveBtn.textContent = 'Save';
            showDestinations(opts.existingPin || null);
        }

        // Set the chosen icon, clear any stale filter text, and render the
        // page that holds the icon so its highlight is visible on open.
        selectedIconValue = opts.iconKey || '';
        var filterInput = document.getElementById('qlIconFilter');
        if (filterInput) filterInput.value = '';
        var openedOnPage = pageIndexForIcon(selectedIconValue);
        renderIconPage(openedOnPage);
        // First open pulls in the full catalog; when it lands, rebuild the
        // pager and repaint so the extra pages and names are available. If
        // the user already paged or started searching in the meantime, leave
        // what they are looking at alone -- only the pills get rebuilt.
        ensureIconCatalog(function () {
            rebuildIconPager();
            var box = document.getElementById('qlIconFilter');
            var busy = (box && box.value) || currentIconPage !== openedOnPage;
            if (!busy) renderIconPage(pageIndexForIcon(selectedIconValue));
        });

        pinModalEl.classList.add('show');
        // Reset scroll inside the modal -- without this, focus() below
        // auto-scrolls the (bottom-anchored) Cancel button into view,
        // landing the user at the bottom of the icon grid instead of at
        // the top with the Label / URL fields visible.
        var scrollHost = pinModalEl.querySelector('.ql-modal');
        if (scrollHost) scrollHost.scrollTop = 0;
        // Default-focus Cancel so a stray Enter / button-press dismisses the
        // modal rather than committing. The user has to deliberately tap
        // Save to pin (or tap into the label/url input to edit). preventScroll
        // keeps the scroll-reset above intact.
        setTimeout(function () {
            var cancelBtn = document.getElementById('qlCancel');
            if (cancelBtn) {
                try { cancelBtn.focus({ preventScroll: true }); }
                catch (_) { cancelBtn.focus(); }
            }
            if (scrollHost) scrollHost.scrollTop = 0;
        }, 30);
    }

    function closePinModal() {
        if (pinModalEl) pinModalEl.classList.remove('show');
        pinModalState = null;
    }

    async function submitPinModal() {
        if (!pinModalState) return;
        var label = (document.getElementById('qlLabel').value || '').trim();
        var urlIn = document.getElementById('qlUrl');
        var url   = (urlIn.value || '').trim();
        var iconKey = selectedIconValue || '';

        if (!label) { toast('A label is required.'); return; }
        if (!url)   { toast('A URL is required.');   return; }

        var saveBtn = document.getElementById('qlSave');
        if (pinModalState.mode === 'pin-current') {
            saveBtn.disabled = true;
            try {
                if (!(await submitCurrentPage(label, url, iconKey))) return;
                var result = { quickLaunch: destState.quickLaunch, pageLink: destState.pageLink };
                var cbCur  = pinModalState.onSaved;
                lastPins = null;                       // the next open re-reads the list
                closePinModal();
                toast(destState.attachedTo ? 'Attached to ' + destState.attachedTo.name + '.'
                      : (result.quickLaunch || result.pageLink ? 'Saved.' : 'Removed.'));
                if (typeof cbCur === 'function') cbCur(result);
            } finally {
                saveBtn.disabled = false;
            }
            return;
        }

        var body = { label: label, url: url, iconKey: iconKey };
        var isEdit = pinModalState.mode === 'edit' && pinModalState.id;

        saveBtn.disabled = true;
        try {
            var res;
            if (isEdit) {
                res = await window.LWAuth.apiPost('/api/quick-launch/' + pinModalState.id, body, 'PUT');
            } else {
                res = await window.LWAuth.apiPost('/api/quick-launch', body, 'POST');
            }
            if (!res || !res.ok) {
                toast(window.LWAuth.errorText(res, 'Save failed.'));
                return;
            }
            var savedId = isEdit ? pinModalState.id : (res.data && res.data.quickLaunchId);
            var savedPin = { quickLaunchId: savedId, label: label, url: url, iconKey: iconKey };
            var cb = pinModalState.onSaved;
            closePinModal();
            toast(isEdit ? 'Pin updated.' : 'Pinned.');
            if (typeof cb === 'function') cb(savedPin);
        } finally {
            saveBtn.disabled = false;
        }
    }

    // ---- Where the current page is pinned (Story 3485) -------------------
    // The Pin button's modal manages BOTH homes of the current page: the Quick
    // Launch row on the dashboard and the Links tab of Entity Manager IV, where
    // a pin is a record (a Page Link entity) rather than a tile. Each box shows
    // membership when the modal opens; Save applies the difference, and every
    // removal still goes through the Cancel-default confirm.
    var destState = { quickLaunch: null, pageLink: null };   // what existed when the modal opened

    function destEls() {
        return {
            wrap:  document.getElementById('qlDestWrap'),
            quick: document.getElementById('qlDestQuick'),
            links: document.getElementById('qlDestLinks'),
            record: document.getElementById('qlDestRecord')
        };
    }

    function hideDestinations() {
        var d = destEls();
        if (d.wrap) d.wrap.style.display = 'none';
    }

    async function showDestinations(existingPin) {
        var d = destEls();
        if (!d.wrap) return;
        destState = { quickLaunch: existingPin || null, pageLink: null };
        d.wrap.style.display = 'block';
        d.quick.checked = !!existingPin;
        if (d.links) d.links.checked = false;
        resetRecordDestination();
        var link = await findPageLink(currentUrl());
        if (!pinModalState || pinModalState.mode !== 'pin-current') return;   // closed meanwhile
        destState.pageLink = link;
        if (d.links) d.links.checked = !!link;
    }

    // The one Page Link (if any) whose address is exactly this url.
    async function findPageLink(url) {
        if (!document.getElementById('qlDestLinks')) return null;   // Echo: no Page Links to look in
        if (!window.LWAuth || !window.LWAuth.apiGet) return null;
        try {
            var res = await window.LWAuth.apiGet('/api/page-links?url=' + encodeURIComponent(url));
            var rows = res && res.ok && res.data ? res.data.pageLinks : null;
            if (Array.isArray(rows) && rows.length) return rows[0];
        } catch (_) { /* read as: not pinned there */ }
        return null;
    }

    // ---- The third destination: one record's Links tab --------------------
    // The record is any entity Entity Manager IV browses (contact, vendor,
    // user-defined, matter, call log), found through the same paged read the
    // browse page uses, in the standard Picasso typeahead. The picker script
    // is pulled in on demand -- most pages do not load it.
    var recordDest = { items: [], selected: null, picker: null, timer: null, seq: 0 };
    var RECORD_TYPE_NAMES = { 5: 'Contact', 1: 'Vendor', 30: 'User-Defined', 217: 'Matter', 219: 'Call Log' };
    var PAGE_LINK_TYPE_ID = 220;   // a page cannot be attached to another page link

    function ensurePickerScript(whenReady) {
        if (window.PicassoPicker) { whenReady(); return; }
        if (!document.querySelector('link[href^="/picasso-picker.css"]')) {
            var css = document.createElement('link');
            css.rel = 'stylesheet'; css.href = '/picasso-picker.css?v=2';
            document.head.appendChild(css);
        }
        var s = document.querySelector('script[src^="/picasso-picker.js"]');
        if (!s) {
            s = document.createElement('script');
            s.src = '/picasso-picker.js?v=4';
            document.head.appendChild(s);
        }
        s.addEventListener('load', whenReady);
    }

    function wireRecordDestination() {
        var box   = document.getElementById('qlDestRecord');
        var wrap  = document.getElementById('qlDestRecordWrap');
        var input = document.getElementById('qlDestRecordInput');
        var dd    = document.getElementById('qlDestRecordDD');
        if (!box || !wrap || !input || !dd) return;
        box.addEventListener('change', function () {
            wrap.style.display = box.checked ? 'block' : 'none';
            if (box.checked) setTimeout(function () { try { input.focus(); } catch (_) {} }, 0);
        });
        input.addEventListener('input', function () {
            recordDest.selected = null;
            clearTimeout(recordDest.timer);
            recordDest.timer = setTimeout(function () { searchRecords(input.value); }, 200);
        });
        ensurePickerScript(function () {
            if (recordDest.picker || !window.PicassoPicker) return;
            recordDest.picker = window.PicassoPicker.attach({
                input: input, dropdown: dd,
                getItems: function () { return recordDest.items; },
                onSelect: function (item) {
                    recordDest.selected = (item && !item.cleared && item.id)
                        ? { entityId: parseInt(item.id, 10) || 0, name: item.name } : null;
                    if (recordDest.selected && !box.checked) { box.checked = true; wrap.style.display = 'block'; }
                }
            });
        });
    }

    function resetRecordDestination() {
        var box = document.getElementById('qlDestRecord');
        var wrap = document.getElementById('qlDestRecordWrap');
        var input = document.getElementById('qlDestRecordInput');
        recordDest.items = []; recordDest.selected = null;
        if (box) box.checked = false;
        if (wrap) wrap.style.display = 'none';
        if (input) input.value = '';
        destState.attachedTo = null;
    }

    async function searchRecords(needle) {
        if (!window.LWAuth || !window.LWAuth.apiGet) return;
        var seq = ++recordDest.seq;
        var res;
        try { res = await window.LWAuth.apiGet('/api/entity-browse?type=0&pageSize=25&q=' + encodeURIComponent((needle || '').trim())); }
        catch (_) { return; }
        if (seq !== recordDest.seq) return;   // a newer keystroke took over
        var rows = res && res.ok && res.data ? (res.data.rows || []) : [];
        recordDest.items = rows.filter(function (r) { return r.entityTypeId !== PAGE_LINK_TYPE_ID; }).map(function (r) {
            var kind = RECORD_TYPE_NAMES[r.entityTypeId] || '';
            return { id: r.entityId, name: r.displayName + (kind ? ' (' + kind + ')' : '') };
        });
        if (recordDest.picker) recordDest.picker.open();
    }

    // Applies the three checkboxes against what existed. Resolves true when every
    // change landed, false when the user cancelled a removal or a call failed.
    async function submitCurrentPage(label, url, iconKey) {
        var d = destEls();
        var wantQuick = !!(d.quick && d.quick.checked);
        var wantLinks = !!(d.links && d.links.checked);
        var hadQuick  = !!destState.quickLaunch;
        var hadLinks  = !!destState.pageLink;
        var wantRecord = !!(d.record && d.record.checked);
        if (wantRecord && !recordDest.selected) { toast('Pick the record to attach it to.'); return false; }
        if (!wantQuick && !wantLinks && !wantRecord && !hadQuick && !hadLinks) { toast('Tick where to pin it.'); return false; }

        if (hadQuick && !wantQuick) {
            var okQ = await confirmAction({
                title: 'Remove from Quick Launch?',
                subtitle: '"' + (destState.quickLaunch.label || label) + '" will leave the Quick Launch row. This cannot be undone.',
                confirmLabel: 'Yes, remove',
                cancelLabel: 'Cancel'
            });
            if (!okQ) return false;
            var delQ = await window.LWAuth.apiPost('/api/quick-launch/' + destState.quickLaunch.quickLaunchId, null, 'DELETE');
            if (!delQ || !delQ.ok) { toast(window.LWAuth.errorText(delQ, 'Remove failed.')); return false; }
            destState.quickLaunch = null;
        }
        if (hadLinks && !wantLinks) {
            var okL = await confirmAction({
                title: 'Remove from Entity IV Links?',
                subtitle: '"' + (destState.pageLink.title || label) + '" will leave the Links tab of Entity Manager IV. This cannot be undone.',
                confirmLabel: 'Yes, remove',
                cancelLabel: 'Cancel'
            });
            if (!okL) return false;
            var delL = await window.LWAuth.apiPost('/api/page-links/' + destState.pageLink.pageLinkId, null, 'DELETE');
            if (!delL || !delL.ok) { toast(window.LWAuth.errorText(delL, 'Remove failed.')); return false; }
            destState.pageLink = null;
        }
        if (wantQuick && !hadQuick) {
            var resQ = await window.LWAuth.apiPost('/api/quick-launch', { label: label, url: url, iconKey: iconKey }, 'POST');
            if (!resQ || !resQ.ok) { toast(window.LWAuth.errorText(resQ, 'Pin to Quick Launch failed.')); return false; }
            destState.quickLaunch = { quickLaunchId: resQ.data && resQ.data.quickLaunchId, label: label };
        }
        // A record attachment needs the page to BE a page link; make one if the
        // page is not one yet (it then shows on Entity IV Links as well -- the
        // attachment and the link are the same record).
        if ((wantLinks && !hadLinks) || (wantRecord && !destState.pageLink)) {
            var resL = await window.LWAuth.apiPost('/api/page-links', { title: label, url: url, iconKey: iconKey }, 'POST');
            if (!resL || !resL.ok) { toast(window.LWAuth.errorText(resL, 'Pin to Entity IV Links failed.')); return false; }
            destState.pageLink = resL.data;
        }
        if (wantRecord) {
            var rec = recordDest.selected;
            var resA = await window.LWAuth.apiPost('/api/entities/' + rec.entityId + '/page-links/' + destState.pageLink.pageLinkId, null, 'POST');
            if (!resA || !resA.ok) { toast('Attaching to ' + rec.name + ' failed.'); return false; }
            destState.attachedTo = rec;
        }
        return true;
    }

    // ---- Cancel-default confirm modal ------------------------------------
    // Used for deletes (and any other destructive action) on the manage
    // page. Cancel is the default-focused primary button; Enter & Escape
    // both fall through to Cancel.
    var confirmEl = null;
    function confirmAction(opts) {
        opts = opts || {};
        ensureCss();
        return new Promise(function (resolve) {
            if (!confirmEl) {
                confirmEl = document.createElement('div');
                confirmEl.className = 'ql-modal-bg';
                confirmEl.id = 'qlConfirmModal';
                confirmEl.innerHTML =
                    '<div class="ql-modal" role="dialog" aria-modal="true">' +
                        '<h2 id="qlConfirmTitle"></h2>' +
                        '<div class="ql-sub" id="qlConfirmSub"></div>' +
                        '<div class="ql-actions">' +
                            '<button type="button" class="ql-btn ql-btn-danger"  id="qlConfirmYes"></button>' +
                            '<button type="button" class="ql-btn ql-btn-primary" id="qlConfirmNo"></button>' +
                        '</div>' +
                    '</div>';
                document.body.appendChild(confirmEl);
            }
            document.getElementById('qlConfirmTitle').textContent = opts.title || 'Are you sure?';
            document.getElementById('qlConfirmSub').textContent   = opts.subtitle || '';
            var yesBtn = document.getElementById('qlConfirmYes');
            var noBtn  = document.getElementById('qlConfirmNo');
            yesBtn.textContent = opts.confirmLabel || 'Yes';
            noBtn.textContent  = opts.cancelLabel  || 'Cancel';

            // Re-bind handlers fresh each invocation.
            yesBtn.onclick = function () {
                confirmEl.classList.remove('show');
                if (confirmEl._resolve) { confirmEl._resolve(true); confirmEl._resolve = null; }
            };
            noBtn.onclick = function () {
                confirmEl.classList.remove('show');
                if (confirmEl._resolve) { confirmEl._resolve(false); confirmEl._resolve = null; }
            };
            // Esc + Enter both fall to Cancel.
            var keyHandler = function (e) {
                if (!confirmEl.classList.contains('show')) {
                    document.removeEventListener('keydown', keyHandler);
                    return;
                }
                if (e.key === 'Escape' || e.key === 'Enter') {
                    e.preventDefault();
                    noBtn.click();
                }
            };
            document.addEventListener('keydown', keyHandler);

            confirmEl._resolve = resolve;
            confirmEl.classList.add('show');
            setTimeout(function () { noBtn.focus(); }, 30);
        });
    }

    // ---- Messages: a Picasso Dialog that waits for OK (Story 5606) -------
    function toast(msg) {
        // A message waits for OK -- never a timer (Story 5606).
        if (window.Picasso && Picasso.toast) {
            Picasso.toast(msg);
            return;
        }
        window.alert(String(msg));
    }

    // ---- Reorderable container ------------------------------------------
    // Wire a container with `.draggable` child elements (dashboard tiles or
    // manage rows) so long-press / mouse-drag reorders them. Each child must
    // have a data-id attribute carrying the pin's QuickLaunchID. On drop we
    // call onReorder(orderedIds) for the caller to persist.
    function makeReorderable(container, opts) {
        opts = opts || {};
        // Idempotent: a container only ever gets one set of drag / touch
        // listeners. Manage Quick Launch calls makeReorderable on every
        // load() / save / delete / reorder commit; before this guard each
        // call piled on 7 more listeners (dragstart/dragover/dragend +
        // touchstart/touchmove/touchend/touchcancel). After a few edits
        // the touchmove listener with passive:false fires N times per
        // scroll event and the page locks up. We now attach the listeners
        // once per container and stash the latest onReorder callback so
        // subsequent calls take effect without leaking handlers.
        container._qlReorderOpts = opts;
        if (container._qlReorderInit) return;
        container._qlReorderInit = true;

        var LONG_PRESS_MS = 320;
        var dragging = null;
        var pressTimer = null;
        var startedFromTouch = false;
        var startX = 0, startY = 0;

        function children() {
            return Array.prototype.slice.call(container.querySelectorAll('.draggable'));
        }
        function clearMarkers() {
            children().forEach(function (c) {
                c.classList.remove('dragging');
                c.classList.remove('drop-target');
            });
        }
        function getOverChild(x, y) {
            var el = document.elementFromPoint(x, y);
            while (el && el !== container) {
                if (el.classList && el.classList.contains('draggable')) return el;
                el = el.parentElement;
            }
            return null;
        }
        function reorderTo(targetEl) {
            if (!dragging || !targetEl || targetEl === dragging) return;
            var sibs = children();
            var di = sibs.indexOf(dragging);
            var ti = sibs.indexOf(targetEl);
            if (di < 0 || ti < 0) return;
            if (di < ti) targetEl.parentNode.insertBefore(dragging, targetEl.nextSibling);
            else         targetEl.parentNode.insertBefore(dragging, targetEl);
        }
        function commit() {
            if (!dragging) return;
            var ordered = children().map(function (c) { return parseInt(c.getAttribute('data-id'), 10); })
                                    .filter(function (n) { return !isNaN(n) && n > 0; });
            clearMarkers();
            dragging = null;
            // Pull the callback from the container's stashed opts so we use
            // the latest one (a fresh load() after an edit replaces it).
            var latest = container._qlReorderOpts || opts;
            if (typeof latest.onReorder === 'function') latest.onReorder(ordered);
        }

        // ---- Desktop HTML5 drag -----
        // opts.canDrag (Echo, Story 5653): a page that arranges only in Arrange mode says so here.
        function mayDrag() {
            var o = container._qlReorderOpts || opts;
            return typeof o.canDrag !== 'function' || o.canDrag();
        }
        container.addEventListener('dragstart', function (e) {
            var t = e.target.closest('.draggable');
            if (!t || !container.contains(t) || !mayDrag()) return;
            dragging = t;
            t.classList.add('dragging');
            try { e.dataTransfer.setData('text/plain', t.getAttribute('data-id')); } catch (_) {}
            if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move';
        });
        container.addEventListener('dragover', function (e) {
            if (!dragging) return;
            e.preventDefault();
            var over = e.target.closest('.draggable');
            if (over && container.contains(over)) reorderTo(over);
        });
        container.addEventListener('dragend', commit);

        // ---- Touch long-press drag -----
        // Removed once on 2026-05-18 thinking the Manage Quick Launch scroll
        // lockup was due to the touch handler. The real causes were (a)
        // `touch-action: none` on .ql-row blocking scroll (fixed in CSS),
        // and (b) multiple makeReorderable calls piling up listeners (fixed
        // by the _qlReorderInit idempotent guard above). With BOTH of those
        // addressed, the touch handler is safe AND necessary - the main
        // menu's Quick Launch dashboard tile-reordering depends on it.
        container.addEventListener('touchstart', function (e) {
            var t = e.target.closest('.draggable');
            if (!t || !container.contains(t) || !mayDrag()) return;
            startedFromTouch = false;
            startX = e.touches[0].clientX;
            startY = e.touches[0].clientY;
            pressTimer = setTimeout(function () {
                startedFromTouch = true;
                dragging = t;
                t.classList.add('dragging');
                if (navigator.vibrate) { try { navigator.vibrate(15); } catch (_) {} }
            }, LONG_PRESS_MS);
        }, { passive: true });
        container.addEventListener('touchmove', function (e) {
            // If the long-press hasn't activated and the finger has moved
            // more than 8px, treat as scroll (don't preventDefault).
            if (pressTimer && !startedFromTouch) {
                var dx = Math.abs(e.touches[0].clientX - startX);
                var dy = Math.abs(e.touches[0].clientY - startY);
                if (dx > 8 || dy > 8) { clearTimeout(pressTimer); pressTimer = null; }
                return;
            }
            if (!dragging) return;
            e.preventDefault();
            var over = getOverChild(e.touches[0].clientX, e.touches[0].clientY);
            if (over) reorderTo(over);
        }, { passive: false });
        container.addEventListener('touchend', function () {
            if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; }
            if (dragging) commit();
        });
        container.addEventListener('touchcancel', function () {
            if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; }
            clearMarkers();
            dragging = null;
        });
    }

    // ---- Tile rendering helpers ------------------------------------------
    // A pin counts as a "notification pin" when its URL points at the
    // Notifications view (whether via the reading-nook ?view= param, a
    // future dedicated path, or anything else with "notification" in it).
    // Such tiles get a count chip and an amber tint when there are unread
    // notifications, refreshed by refreshNotificationPins below.
    function isNotificationPin(pin) {
        if (!pin || !pin.url) return false;
        return pin.url.toLowerCase().indexOf('notification') >= 0;
    }

    function parseDictateTarget(url) {
        if (!url) return null;
        var m;
        m = url.match(/\/checklists\/checklist\.html\?id=(\d+)/);
        if (m) return { type: 'checklist', id: parseInt(m[1], 10) };
        m = url.match(/\/tasks\/tasks\.html\?headerid=(\d+)/i);
        if (m) return { type: 'task', id: parseInt(m[1], 10) };
        // Story pin: no parent ID -- each dictation creates a new Story.
        if (/\/stories\/stories\.html(\?|$)/i.test(url)) {
            return { type: 'story', id: 0 };
        }
        return null;
    }

    // The Dictionary pin also gets a mic overlay, but unlike the dictate
    // targets above it does NOT route through Quick Dictate -- the Dictionary
    // page does its own speech recognition and lookup, so the mic just opens
    // it in listen mode for a fully voice-only word lookup.
    function isDictionaryPin(url) {
        return !!url && /\/dictionary\/dictionary\.html/i.test(url);
    }

    function renderTile(pin) {
        var a = document.createElement('a');
        a.className   = 'ql-tile draggable';
        a.href        = pin.url;
        a.setAttribute('data-id',  pin.quickLaunchId);
        a.setAttribute('data-url', pin.url);
        a.draggable   = true;
        var ico = document.createElement('div');
        if (pin.iconKey && pin.iconKey.length > 0) {
            ico.className = 'ql-tile-ico';
            ico.textContent = pin.iconKey;
        } else {
            ico.className = 'ql-tile-ico placeholder';
            ico.textContent = (pin.label || '?').trim().substring(0, 1).toUpperCase();
        }
        var lbl = document.createElement('div');
        lbl.className   = 'ql-tile-label';
        lbl.textContent = pin.label || '';
        a.appendChild(ico);
        a.appendChild(lbl);

        var dictTarget = parseDictateTarget(pin.url);
        if (dictTarget) {
            var mic = document.createElement('button');
            mic.type = 'button';
            mic.className = 'ql-tile-mic';
            mic.setAttribute('data-testid', 'ql-mic-' + pin.quickLaunchId);
            mic.setAttribute('aria-label', 'Dictate into ' + (pin.label || 'this'));
            mic.innerHTML = '<svg viewBox="0 0 24 24" width="13" height="13" fill="currentColor">'
                + '<path d="M12 14a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5a3 3 0 0 0 3 3z"/>'
                + '<path d="M19 11a1 1 0 0 0-2 0 5 5 0 0 1-10 0 1 1 0 0 0-2 0'
                + ' 7 7 0 0 0 6 6.92V21h-2a1 1 0 0 0 0 2h6a1 1 0 0 0 0-2h-2v-3.08'
                + 'A7 7 0 0 0 19 11z"/></svg>';
            mic.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                // One-shot target passed as a query param. Quick Dictate reads
                // it once at load and never persists it, so opening Quick
                // Dictate directly always falls back to the Reading Nook.
                var qs = 'target=' + encodeURIComponent(dictTarget.type)
                       + '&id=' + encodeURIComponent(String(dictTarget.id))
                       + '&name=' + encodeURIComponent(pin.label || '');
                window.location.href = '/transcribe/quick.html?' + qs;
            });
            a.appendChild(mic);
        } else if (isDictionaryPin(pin.url)) {
            var dmic = document.createElement('button');
            dmic.type = 'button';
            dmic.className = 'ql-tile-mic';
            dmic.setAttribute('data-testid', 'ql-mic-' + pin.quickLaunchId);
            dmic.setAttribute('aria-label', 'Look up a word by voice');
            dmic.innerHTML = '<svg viewBox="0 0 24 24" width="13" height="13" fill="currentColor">'
                + '<path d="M12 14a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5a3 3 0 0 0 3 3z"/>'
                + '<path d="M19 11a1 1 0 0 0-2 0 5 5 0 0 1-10 0 1 1 0 0 0-2 0'
                + ' 7 7 0 0 0 6 6.92V21h-2a1 1 0 0 0 0 2h6a1 1 0 0 0 0-2h-2v-3.08'
                + 'A7 7 0 0 0 19 11z"/></svg>';
            dmic.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                // Open the Dictionary already listening -- voice-only, no typing.
                var base = (pin.url || '').split('?')[0];
                window.location.href = base + '?listen=1';
            });
            a.appendChild(dmic);
        }

        if (isNotificationPin(pin)) {
            a.setAttribute('data-notif-pin', '1');
            var chip = document.createElement('div');
            chip.className = 'ql-tile-badge';
            chip.style.display = 'none';
            chip.setAttribute('data-role', 'notif-chip');
            a.appendChild(chip);
        }
        // Suppress navigation if the click ended a drag.
        a.addEventListener('click', function (e) {
            if (a.classList.contains('dragging')) { e.preventDefault(); }
        });
        return a;
    }

    // ---- Notification badge poller ---------------------------------------
    // Walks every .ql-tile[data-notif-pin="1"] on the page, fetches the
    // current unread count once, and updates every tile in lockstep. No-ops
    // when no notification tiles exist (most pages). The dashboard kicks
    // this off after its load+render finishes, and we re-fire every 30 s
    // while the page is open.
    var notifPollTimer = null;
    async function refreshNotificationPins() {
        var tiles = document.querySelectorAll('.ql-tile[data-notif-pin="1"]');
        if (!tiles.length) return;
        if (!window.LWAuth || !window.LWAuth.apiGet) return;
        var count = 0;
        try {
            var r = await window.LWAuth.apiGet('/api/notifications/unread-count');
            if (r && r.ok && r.data && typeof r.data.count === 'number') count = r.data.count;
        } catch (_) {}
        for (var i = 0; i < tiles.length; i++) {
            var tile = tiles[i];
            var chip = tile.querySelector('[data-role="notif-chip"]');
            var lbl  = tile.querySelector('.ql-tile-label');
            if (count > 0) {
                tile.classList.add('has-notifications');
                if (chip) { chip.textContent = String(count); chip.style.display = ''; }
                // Replace the label with the count for at-a-glance visibility
                // -- the icon already conveys "this is the notifications pin",
                // so the second piece of information is "how many are unread."
                if (lbl) lbl.textContent = count + (count === 1 ? ' unread' : ' unread');
            } else {
                tile.classList.remove('has-notifications');
                if (chip) chip.style.display = 'none';
                // Restore the original label saved at render time.
                if (lbl && tile.getAttribute('data-original-label') !== null) {
                    lbl.textContent = tile.getAttribute('data-original-label');
                }
            }
        }
    }

    function startNotificationPolling() {
        // Remember the original label on each notification tile the first
        // time we see it so refreshNotificationPins can restore it when
        // count drops back to zero. Idempotent.
        var tiles = document.querySelectorAll('.ql-tile[data-notif-pin="1"]');
        for (var i = 0; i < tiles.length; i++) {
            if (tiles[i].getAttribute('data-original-label') === null) {
                var lbl = tiles[i].querySelector('.ql-tile-label');
                tiles[i].setAttribute('data-original-label', lbl ? lbl.textContent : '');
            }
        }
        refreshNotificationPins();
        if (notifPollTimer) clearInterval(notifPollTimer);
        notifPollTimer = setInterval(refreshNotificationPins, 30_000);
    }

    // ---- Public surface --------------------------------------------------
    window.QuickLaunch = {
        ensureIconBand:            ensureIconBand,
        injectPinButton:           injectPinButton,
        injectNavCluster:          injectNavCluster,
        refreshUrlBar:             refreshUrlBar,
        refreshDeskIcon:           refreshDeskIcon,
        openQuickLaunchSheet:      openQuickLaunchSheet,
        openMenuSearchSheet:       openMenuSearchSheet,
        getMenuCatalog:            getMenuCatalog,
        refreshPinState:           refreshPinState,
        openPinModal:              openPinModal,
        togglePinFor:              togglePinFor,
        listPins:                  listPins,
        toast:                     toast,
        confirmAction:             confirmAction,
        icons:                     ICONS,
        iconPages:                 ICON_PAGES,
        ensureCss:                 ensureCss,
        makeReorderable:           makeReorderable,
        renderTile:                renderTile,
        startNotificationPolling:  startNotificationPolling,
        refreshNotificationPins:   refreshNotificationPins
    };
    // This script is injected after auth, so a page script that ran first can
    // wait on this instead of polling for window.QuickLaunch.
    try { document.dispatchEvent(new CustomEvent('quicklaunch:ready')); } catch (_) {}

    // ---- Wire on load ----------------------------------------------------
    // Build the icon band (the standard top row) and fill it: nav cluster +
    // pin + relocated page Print on the left, logout icon on the right. Then
    // retire the header's now-redundant home / logout text. Runs on
    // DOMContentLoaded and on a couple of retries so late-rendered headers
    // and the async-injected auth logout link are caught. All steps are
    // idempotent.
    function tryInject() {
        // Nothing is put on the page until its stylesheet is parsed, so the
        // band is styled from its very first paint.
        ensureCss(function () {
            var band = ensureIconBand();
            wireMenuSearch();          // works with or without the band; must run
                                       // AFTER the band so it can see it exists
            if (!band) return;
            injectNavCluster();
            injectPinButton();
            injectDeskIcon();
            relocatePrint();
            injectLogoutIcon(band);
            injectUrlBar(band);        // last -- it wraps onto its own row in the band
            tidyHeaders();
        });
    }
    if (document.readyState !== 'loading') tryInject();
    else document.addEventListener('DOMContentLoaded', tryInject);

    // Re-attempt after auth completes (auth.js injects logout asynchronously).
    setTimeout(tryInject, 250);
    setTimeout(tryInject, 800);
})();
