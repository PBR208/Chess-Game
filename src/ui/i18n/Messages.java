package ui.i18n;

/*
 * Purpose: Messages is where every piece of text a player reads comes from, so the screens hold keys
 * instead of English sentences. It loads the bundle for the chosen language, falls back to English
 * when a language has no text of its own, and hands a missing key straight back rather than throwing,
 * because a screen with one untranslated label is still a usable screen while one that crashes is
 * not. The language is a setting of the whole window, so it lives here rather than being handed from
 * screen to screen.
 *
 * Owner: PBR208 - https://github.com/PBR208/
 * Version: 1.0
 */

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class Messages {

    // the bundle sits next to the sprite sheet, which is where the build copies resources to
    private static final String BUNDLE_NAME = "resources.messages";

    // the languages the game ships text for
    private static final List<Locale> SUPPORTED = List.of(Locale.ENGLISH, Locale.GERMAN);

    private static Locale locale = Locale.ENGLISH;
    private static ResourceBundle bundle = load(Locale.ENGLISH);

    private Messages() {
    }

    /**
     * Returns the text behind a key in the language that is currently chosen.
     * <p>
     * Every label, button and message on every screen comes through here. A key that no bundle has
     * comes back as itself, which makes the gap obvious on screen and in a test while leaving the
     * screen working, instead of taking the window down over a label.
     * <p>
     * Time complexity: O(1), the bundle is a map that was read once.
     * Space complexity: O(1).
     *
     * @param pKey the key of the text, such as menu.newGame; never null
     * @return the text for the current language, or the key itself when nothing has it
     */
    public static String get(String pKey) {
        try {
            return bundle.getString(pKey);
        } catch (MissingResourceException missing) {
            // a missing text must show up, not blow up
            return pKey;
        }
    }

    /**
     * Returns the text behind a key with the given values filled into it.
     * <p>
     * Some sentences name a player or a number, and word order differs between languages, so the
     * bundle holds the whole sentence with numbered places in it rather than pieces the code glues
     * together. I look the text up and let MessageFormat fill the places in.
     * <p>
     * Time complexity: O(n) in the length of the text. Space complexity: O(n) for the result.
     *
     * @param pKey  the key of the text, never null
     * @param pArgs the values for the numbered places, in order; may be empty
     * @return the finished sentence, never null
     */
    public static String format(String pKey, Object... pArgs) {
        return MessageFormat.format(get(pKey), pArgs);
    }

    /**
     * Switches the language every screen built from now on will use.
     * <p>
     * A player picks their language in the menu, and the screens read their text when they are
     * built, so switching takes effect as screens are rebuilt. A language the game ships no text for
     * falls back to English rather than to whatever the machine happens to be set to, which keeps
     * the game the same everywhere.
     * <p>
     * Time complexity: O(n) in the size of the bundle, which is read once per switch.
     * Space complexity: O(n) for the bundle.
     *
     * @param pLocale the language to use, or null for English
     */
    public static void setLocale(Locale pLocale) {
        locale = pLocale == null ? Locale.ENGLISH : pLocale;
        bundle = load(locale);
    }

    /**
     * Returns the language currently in use.
     * <p>
     * Time complexity: O(1). Space complexity: O(1).
     *
     * @return the chosen language, never null
     */
    public static Locale getLocale() {
        return locale;
    }

    /**
     * Returns the languages the game has text for.
     * <p>
     * The menu offers exactly these, so the list belongs next to the bundles rather than in the
     * menu, where it would drift away from what is actually shipped.
     * <p>
     * Time complexity: O(1). Space complexity: O(1), the list is shared and unmodifiable.
     *
     * @return the supported languages, never null and never empty
     */
    public static List<Locale> supportedLocales() {
        return SUPPORTED;
    }

    /**
     * Loads the bundle for a language without falling back to the machine's own language.
     * <p>
     * Java would otherwise answer a request for English on a German machine with the German bundle,
     * which would make the game read differently depending on where it is started. I turn that
     * fallback off, so a language either has its own bundle or gets the English base one.
     * <p>
     * Time complexity: O(n) in the size of the bundle. Space complexity: O(n).
     *
     * @param pLocale the language to load, never null
     * @return the bundle for that language, never null
     */
    private static ResourceBundle load(Locale pLocale) {
        return ResourceBundle.getBundle(BUNDLE_NAME, pLocale, Messages.class.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }
}
