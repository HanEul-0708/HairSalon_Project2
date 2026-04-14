package com.hairsalonproject2.common.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sanitizes board HTML while preserving the inline formatting used by Summernote.
 */
public final class HtmlSanitizer {

    private static final Set<String> ALLOWED_IMAGE_CLASSES = Set.of(
            "board-image-align-left",
            "board-image-align-center",
            "board-image-align-right"
    );

    private static final Set<String> ALLOWED_STYLE_PROPERTIES = Set.of(
            "color", "background-color", "background", "font-size", "text-align"
    );

    private static final Pattern SAFE_COLOR = Pattern.compile(
            "^(#[0-9a-fA-F]{3,8}|rgb(a)?\\([0-9\\s.,%]+\\)|hsl(a)?\\([0-9\\s.,%]+\\)|[a-zA-Z]+)$"
    );

    private static final Pattern SAFE_FONT_SIZE = Pattern.compile(
            "^[0-9]+(\\.[0-9]+)?(px|pt|em|rem|%)$"
    );

    private static final Set<String> SAFE_FONT_SIZE_KEYWORDS = Set.of(
            "xx-small", "x-small", "small", "medium", "large", "x-large", "xx-large",
            "smaller", "larger"
    );

    private static final String LOCAL_EDITOR_IMAGE_PREFIX = "/files/images/";

    private HtmlSanitizer() {
    }

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Safelist safelist = Safelist.basicWithImages();
        safelist.addTags("span", "div", "font");
        safelist.addAttributes("a", "href", "title", "target", "rel");
        safelist.addAttributes("img", "src", "alt", "title", "width", "height", "class");
        safelist.addAttributes("font", "size", "color");
        safelist.addAttributes(":all", "style");
        safelist.removeTags("iframe");

        String cleanedHtml = Jsoup.clean(html, safelist);
        return sanitizeInlineStyles(html, cleanedHtml);
    }

    private static String sanitizeInlineStyles(String originalHtml, String cleanedHtml) {
        Document sourceDocument = Jsoup.parseBodyFragment(originalHtml);
        Document document = Jsoup.parseBodyFragment(cleanedHtml);

        for (Element element : document.body().select("[style]")) {
            String sanitizedStyle = Arrays.stream(element.attr("style").split(";"))
                    .map(String::trim)
                    .filter(style -> !style.isBlank())
                    .map(HtmlSanitizer::sanitizeStyleDeclaration)
                    .filter(style -> style != null && !style.isBlank())
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("");

            if (sanitizedStyle.isBlank()) {
                element.removeAttr("style");
                continue;
            }

            element.attr("style", sanitizedStyle);
        }

        for (Element image : document.body().select("img[class]")) {
            Set<String> allowedClasses = new HashSet<>();
            image.classNames().forEach(className -> {
                if (ALLOWED_IMAGE_CLASSES.contains(className)) {
                    allowedClasses.add(className);
                }
            });

            if (allowedClasses.isEmpty()) {
                image.removeAttr("class");
            } else {
                image.classNames(allowedClasses);
            }
        }

        restoreSafeImageSources(sourceDocument, document);
        sanitizeLinks(document);

        return document.body().html();
    }

    private static void sanitizeLinks(Document document) {
        for (Element link : document.body().select("a")) {
            String target = link.attr("target").trim().toLowerCase(Locale.ROOT);
            if (target.isBlank()) {
                continue;
            }
            if (!Set.of("_blank", "_self", "_parent", "_top").contains(target)) {
                link.removeAttr("target");
                link.removeAttr("rel");
                continue;
            }
            if ("_blank".equals(target)) {
                link.attr("rel", "noopener noreferrer");
            }
        }
    }

    private static void restoreSafeImageSources(Document sourceDocument, Document cleanedDocument) {
        var sourceImages = sourceDocument.body().select("img[src]");
        var cleanedImages = cleanedDocument.body().select("img");
        int count = Math.min(sourceImages.size(), cleanedImages.size());

        for (int i = 0; i < count; i++) {
            Element cleanedImage = cleanedImages.get(i);
            if (cleanedImage.hasAttr("src") && !cleanedImage.attr("src").isBlank()) {
                continue;
            }

            String source = sourceImages.get(i).attr("src").trim();
            if (isSafeImageSource(source)) {
                cleanedImage.attr("src", source);
            }
        }
    }

    private static boolean isSafeImageSource(String source) {
        if (source == null || source.isBlank()) {
            return false;
        }

        return source.startsWith(LOCAL_EDITOR_IMAGE_PREFIX)
                || source.startsWith("http://")
                || source.startsWith("https://");
    }

    private static String sanitizeStyleDeclaration(String declaration) {
        String[] parts = declaration.split(":", 2);
        if (parts.length != 2) {
            return null;
        }

        String property = parts[0].trim().toLowerCase(Locale.ROOT);
        String value = parts[1].trim();

        if (!ALLOWED_STYLE_PROPERTIES.contains(property)) {
            return null;
        }

        if (!isSafeStyleValue(property, value)) {
            return null;
        }

        return property + ": " + value;
    }

    private static boolean isSafeStyleValue(String property, String value) {
        return switch (property) {
            case "color", "background-color", "background" -> SAFE_COLOR.matcher(value).matches();
            case "font-size" -> SAFE_FONT_SIZE.matcher(value).matches()
                    || SAFE_FONT_SIZE_KEYWORDS.contains(value.toLowerCase(Locale.ROOT));
            case "text-align" -> Set.of("left", "center", "right", "justify")
                    .contains(value.toLowerCase(Locale.ROOT));
            default -> false;
        };
    }
}
