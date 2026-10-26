package com.echo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/**
 * Strips dangerous and privacy-leaking elements from inbound email HTML before
 * we render it in the Inbox reader pane.
 *
 * <p>Removes:
 * <ul>
 *   <li><code>script</code>, <code>style</code>, <code>link</code>,
 *       <code>iframe</code>, <code>object</code>, <code>embed</code> --
 *       active or remote-resource elements.</li>
 *   <li><code>img</code> -- drops the most common tracking-pixel vector.</li>
 *   <li><code>form</code>, <code>input</code>, <code>button</code> -- no email
 *       should be posting back to anywhere from our reader.</li>
 *   <li>All inline event handlers (<code>onclick</code>, <code>onload</code>,
 *       etc.) and <code>javascript:</code> URLs -- handled by jsoup's safelist
 *       attribute/protocol whitelisting.</li>
 * </ul>
 *
 * <p>Output is emitted as XHTML with {@link Entities.EscapeMode#xhtml} -- only
 * the five XML-mandatory entities (<code>&amp; &lt; &gt; &quot; &apos;</code>)
 * are escaped; everything else (including non-breaking spaces, smart quotes,
 * em-dashes, copyright symbols, etc.) becomes literal Unicode. This is what
 * makes the same string safe to drop into both the Reading Nook HTML save
 * <em>and</em> the openhtmltopdf XHTML renderer, which otherwise rejects
 * <code>&amp;nbsp;</code> and friends with "entity X was referenced but not
 * declared" because XXE protections block external DTD resolution.
 *
 * <p>Links survive but jsoup adds <code>rel="nofollow"</code>. The reader pane
 * additionally renders inside a sandboxed iframe, so even if a future change
 * relaxes the safelist, scripts and form posts still can't escape the box.
 */
public final class EmailHtmlSanitizer {

    private EmailHtmlSanitizer() {}

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) return "";
        Safelist sl = Safelist.relaxed()
            .removeTags("img", "iframe", "form", "input", "button", "object",
                        "embed", "link", "script", "style")
            .addAttributes("a", "target", "rel");
        Document dirty = Jsoup.parse(html);
        Document clean = new Cleaner(sl).clean(dirty);
        clean.outputSettings()
             .syntax(Document.OutputSettings.Syntax.xml)
             .escapeMode(Entities.EscapeMode.xhtml)
             .charset("UTF-8");
        return clean.body().html();
    }

    // ---- the Gmail Client reader (Story 5591) ---------------------------------------------

    /** Every attribute an email lays itself out with -- the ones relaxed() throws away. */
    private static final String[] LAYOUT_ATTRS = {
        "style", "class", "id", "align", "valign", "width", "height", "bgcolor", "border",
        "cellpadding", "cellspacing", "dir", "lang", "title", "role"
    };

    /** CSS that reaches outside the message or runs code. The frame blocks scripts anyway. */
    private static final java.util.regex.Pattern CSS_DANGER = java.util.regex.Pattern.compile(
        "@import[^;]*;?|expression\\s*\\(|javascript\\s*:|behavior\\s*:|-moz-binding",
        java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Email HTML for the Gmail Client reader, kept looking the way Gmail shows it. Unlike
     * {@link #sanitize}, this keeps the message's own styling: its &lt;style&gt; blocks (head
     * and body, media queries included), inline style attributes, table layout attributes,
     * font and center tags, and https images. Still removed: scripts, frames, forms, embeds,
     * remote stylesheets (link, @import), event handlers and javascript: URLs. The page shows
     * the result in a sandboxed frame with no scripts, under the site's own image policy.
     *
     * <p>Not for the PDF / Reading Nook path -- that needs {@link #sanitize}'s plain XHTML.
     */
    public static String sanitizeForReader(String html) {
        if (html == null || html.isBlank()) return "";
        Document dirty = Jsoup.parse(html);

        StringBuilder css = new StringBuilder();
        for (org.jsoup.nodes.Element st : dirty.select("style")) {
            String data = cleanCss(st.data());
            if (!data.isBlank()) css.append(data).append('\n');
        }

        Safelist sl = Safelist.relaxed()
            .removeTags("iframe", "form", "input", "button", "object", "embed", "link", "script", "style")
            .addTags("center", "font", "hr", "small", "big", "strike", "s", "abbr", "address")
            .addAttributes(":all", LAYOUT_ATTRS)
            .addAttributes("font", "color", "face", "size")
            .addAttributes("a", "target", "rel")
            .addAttributes("img", "src", "alt", "title", "width", "height", "border")
            .addProtocols("img", "src", "https");
        Document clean = new Cleaner(sl).clean(dirty);
        for (org.jsoup.nodes.Element e : clean.select("[style]")) e.attr("style", cleanCss(e.attr("style")));
        clean.outputSettings().escapeMode(Entities.EscapeMode.xhtml).charset("UTF-8").prettyPrint(false);

        // The body's own colour and style carry over on a wrapper, since the body tag itself does not.
        org.jsoup.nodes.Element body = dirty.body();
        String wrapStyle = cleanCss(body.attr("style"));
        if (!body.attr("bgcolor").isBlank()) wrapStyle = "background-color:" + body.attr("bgcolor") + ";" + wrapStyle;

        StringBuilder out = new StringBuilder();
        if (css.length() > 0) out.append("<style>").append(css.toString().replace("</", "<\\/")).append("</style>");
        if (!wrapStyle.isBlank()) out.append("<div style=\"").append(Entities.escape(wrapStyle)).append("\">");
        out.append(clean.body().html());
        if (!wrapStyle.isBlank()) out.append("</div>");
        return out.toString();
    }

    private static String cleanCss(String css) {
        return css == null ? "" : CSS_DANGER.matcher(css).replaceAll("");
    }
}
