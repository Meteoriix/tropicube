package fr.tropicube.core.guild;

import java.util.Locale;
import java.util.regex.Pattern;

/** Shared identity validation for private menu input and the SQL creation boundary. */
public final class GuildNames {
    private static final Pattern NAME = Pattern.compile("[\\p{L}0-9 _-]{3,32}");
    private static final Pattern TAG = Pattern.compile("[A-Z0-9]{2,8}");
    private GuildNames() { }

    /** Names accept spaces but no formatting markup or command characters. */
    public static boolean validName(String name) { return name != null && NAME.matcher(name.trim()).matches(); }

    /** Tags use the same uppercase representation in commands, storage and invitations. */
    public static String normalizeTag(String tag) { return tag == null ? "" : tag.trim().toUpperCase(Locale.ROOT); }

    public static boolean validTag(String tag) { return TAG.matcher(normalizeTag(tag)).matches(); }
}
