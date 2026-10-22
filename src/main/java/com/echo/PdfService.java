package com.echo;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders an XHTML string to a PDF byte[] via openhtmltopdf.
 *
 * No knowledge of paychecks, emails, storage, or auth -- strict
 * single-responsibility helper. Callers build the printable HTML (e.g.
 * {@link PaystubHtmlTemplate}) and hand it here.
 */
@Service
public class PdfService {

    /**
     * Render XHTML to PDF. The input must be reasonably well-formed XHTML --
     * openhtmltopdf's fast-mode parser is forgiving but not as forgiving as a
     * browser. Inline CSS only (external URLs resolve poorly).
     *
     * <p>Defensive pre-pass: any HTML named entity beyond the five XML-safe
     * ones (<code>&amp;amp; &amp;lt; &amp;gt; &amp;quot; &amp;apos;</code>)
     * is rewritten to a numeric character reference before the parser runs.
     * openhtmltopdf parses as strict XHTML with XXE protection -- without
     * external-DTD resolution it rejects <code>&amp;nbsp;</code>,
     * <code>&amp;middot;</code>, <code>&amp;mdash;</code>, and friends with
     * "the entity X was referenced, but not declared". Numeric refs are
     * part of XML core and parse without a DTD.
     */
    public byte[] htmlToPdf(String xhtml) {
        if (xhtml == null || xhtml.isEmpty()) {
            throw new IllegalArgumentException("xhtml is empty");
        }
        String safe = numericizeNamedEntities(xhtml);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(safe, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("PDF render failed: " + ex.getMessage(), ex);
        }
    }

    // ---- Named-entity -> numeric-ref translation ----------------------------
    //
    // Covers the HTML4 / XHTML 1.0 named-entity set excluding the five that
    // XML itself defines (amp / lt / gt / quot / apos). The table is hand-
    // curated rather than read from a DTD so the map is local and small;
    // unknown entities are left alone (openhtmltopdf will surface them in
    // the original error so the caller can fix the source).

    private static final Pattern NAMED_ENT = Pattern.compile("&([A-Za-z][A-Za-z0-9]+);");
    private static final Map<String, Integer> NAMED_TO_NUMERIC = buildNamedEntityMap();

    private static String numericizeNamedEntities(String html) {
        Matcher m = NAMED_ENT.matcher(html);
        StringBuffer sb = new StringBuffer(html.length());
        while (m.find()) {
            String name = m.group(1);
            String lc = name.toLowerCase();
            // The XML-safe five stay as-is.
            if (lc.equals("amp") || lc.equals("lt") || lc.equals("gt")
                    || lc.equals("quot") || lc.equals("apos")) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                continue;
            }
            Integer cp = NAMED_TO_NUMERIC.get(name);
            if (cp == null) cp = NAMED_TO_NUMERIC.get(lc);
            if (cp != null) {
                m.appendReplacement(sb, "&#" + cp + ";");
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Map<String, Integer> buildNamedEntityMap() {
        Map<String, Integer> e = new HashMap<>(280);
        // Latin-1 supplement
        e.put("nbsp",   160); e.put("iexcl",  161); e.put("cent",   162);
        e.put("pound",  163); e.put("curren", 164); e.put("yen",    165);
        e.put("brvbar", 166); e.put("sect",   167); e.put("uml",    168);
        e.put("copy",   169); e.put("ordf",   170); e.put("laquo",  171);
        e.put("not",    172); e.put("shy",    173); e.put("reg",    174);
        e.put("macr",   175); e.put("deg",    176); e.put("plusmn", 177);
        e.put("sup2",   178); e.put("sup3",   179); e.put("acute",  180);
        e.put("micro",  181); e.put("para",   182); e.put("middot", 183);
        e.put("cedil",  184); e.put("sup1",   185); e.put("ordm",   186);
        e.put("raquo",  187); e.put("frac14", 188); e.put("frac12", 189);
        e.put("frac34", 190); e.put("iquest", 191);
        // Latin-1 letters (uppercase + lowercase, accented)
        e.put("Agrave", 192); e.put("Aacute", 193); e.put("Acirc",  194);
        e.put("Atilde", 195); e.put("Auml",   196); e.put("Aring",  197);
        e.put("AElig",  198); e.put("Ccedil", 199); e.put("Egrave", 200);
        e.put("Eacute", 201); e.put("Ecirc",  202); e.put("Euml",   203);
        e.put("Igrave", 204); e.put("Iacute", 205); e.put("Icirc",  206);
        e.put("Iuml",   207); e.put("ETH",    208); e.put("Ntilde", 209);
        e.put("Ograve", 210); e.put("Oacute", 211); e.put("Ocirc",  212);
        e.put("Otilde", 213); e.put("Ouml",   214); e.put("times",  215);
        e.put("Oslash", 216); e.put("Ugrave", 217); e.put("Uacute", 218);
        e.put("Ucirc",  219); e.put("Uuml",   220); e.put("Yacute", 221);
        e.put("THORN",  222); e.put("szlig",  223); e.put("agrave", 224);
        e.put("aacute", 225); e.put("acirc",  226); e.put("atilde", 227);
        e.put("auml",   228); e.put("aring",  229); e.put("aelig",  230);
        e.put("ccedil", 231); e.put("egrave", 232); e.put("eacute", 233);
        e.put("ecirc",  234); e.put("euml",   235); e.put("igrave", 236);
        e.put("iacute", 237); e.put("icirc",  238); e.put("iuml",   239);
        e.put("eth",    240); e.put("ntilde", 241); e.put("ograve", 242);
        e.put("oacute", 243); e.put("ocirc",  244); e.put("otilde", 245);
        e.put("ouml",   246); e.put("divide", 247); e.put("oslash", 248);
        e.put("ugrave", 249); e.put("uacute", 250); e.put("ucirc",  251);
        e.put("uuml",   252); e.put("yacute", 253); e.put("thorn",  254);
        e.put("yuml",   255);
        // Latin Extended-A / Latin Extended-B / Spacing modifier letters
        e.put("OElig",  338); e.put("oelig",  339);
        e.put("Scaron", 352); e.put("scaron", 353); e.put("Yuml",   376);
        e.put("fnof",   402); e.put("circ",   710); e.put("tilde",  732);
        // Greek (the HTML4 subset)
        e.put("Alpha",  913); e.put("Beta",   914); e.put("Gamma",  915);
        e.put("Delta",  916); e.put("Epsilon",917); e.put("Zeta",   918);
        e.put("Eta",    919); e.put("Theta",  920); e.put("Iota",   921);
        e.put("Kappa",  922); e.put("Lambda", 923); e.put("Mu",     924);
        e.put("Nu",     925); e.put("Xi",     926); e.put("Omicron",927);
        e.put("Pi",     928); e.put("Rho",    929); e.put("Sigma",  931);
        e.put("Tau",    932); e.put("Upsilon",933); e.put("Phi",    934);
        e.put("Chi",    935); e.put("Psi",    936); e.put("Omega",  937);
        e.put("alpha",  945); e.put("beta",   946); e.put("gamma",  947);
        e.put("delta",  948); e.put("epsilon",949); e.put("zeta",   950);
        e.put("eta",    951); e.put("theta",  952); e.put("iota",   953);
        e.put("kappa",  954); e.put("lambda", 955); e.put("mu",     956);
        e.put("nu",     957); e.put("xi",     958); e.put("omicron",959);
        e.put("pi",     960); e.put("rho",    961); e.put("sigmaf", 962);
        e.put("sigma",  963); e.put("tau",    964); e.put("upsilon",965);
        e.put("phi",    966); e.put("chi",    967); e.put("psi",    968);
        e.put("omega",  969);
        // General punctuation
        e.put("ensp",  8194); e.put("emsp",  8195); e.put("thinsp",8201);
        e.put("zwnj",  8204); e.put("zwj",   8205); e.put("lrm",   8206);
        e.put("rlm",   8207); e.put("ndash", 8211); e.put("mdash", 8212);
        e.put("lsquo", 8216); e.put("rsquo", 8217); e.put("sbquo", 8218);
        e.put("ldquo", 8220); e.put("rdquo", 8221); e.put("bdquo", 8222);
        e.put("dagger",8224); e.put("Dagger",8225); e.put("bull",  8226);
        e.put("hellip",8230); e.put("permil",8240); e.put("prime", 8242);
        e.put("Prime", 8243); e.put("lsaquo",8249); e.put("rsaquo",8250);
        e.put("oline", 8254); e.put("frasl", 8260); e.put("euro",  8364);
        // Letter-like / arrows / math (the HTML4 essentials)
        e.put("image", 8465); e.put("weierp",8472); e.put("real",  8476);
        e.put("trade", 8482); e.put("alefsym",8501);
        e.put("larr",  8592); e.put("uarr",  8593); e.put("rarr",  8594);
        e.put("darr",  8595); e.put("harr",  8596); e.put("crarr", 8629);
        e.put("forall",8704); e.put("part",  8706); e.put("exist", 8707);
        e.put("empty", 8709); e.put("nabla", 8711); e.put("isin",  8712);
        e.put("notin", 8713); e.put("ni",    8715); e.put("prod",  8719);
        e.put("sum",   8721); e.put("minus", 8722); e.put("lowast",8727);
        e.put("radic", 8730); e.put("prop",  8733); e.put("infin", 8734);
        e.put("ang",   8736); e.put("and",   8743); e.put("or",    8744);
        e.put("cap",   8745); e.put("cup",   8746); e.put("int",   8747);
        e.put("there4",8756); e.put("sim",   8764); e.put("cong",  8773);
        e.put("asymp", 8776); e.put("ne",    8800); e.put("equiv", 8801);
        e.put("le",    8804); e.put("ge",    8805); e.put("sub",   8834);
        e.put("sup",   8835); e.put("nsub",  8836); e.put("sube",  8838);
        e.put("supe",  8839); e.put("oplus", 8853); e.put("otimes",8855);
        e.put("perp",  8869); e.put("sdot",  8901); e.put("lceil", 8968);
        e.put("rceil", 8969); e.put("lfloor",8970); e.put("rfloor",8971);
        e.put("lang",  9001); e.put("rang",  9002);
        e.put("loz",   9674); e.put("spades",9824); e.put("clubs", 9827);
        e.put("hearts",9829); e.put("diams", 9830);
        return e;
    }
}
