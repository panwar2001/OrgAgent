package com.panwar2001.orgagent.core.support;

import java.util.Locale;
import java.util.regex.Pattern;

import com.panwar2001.orgagent.core.exception.BadRequestException;
import com.panwar2001.orgagent.core.exception.ErrorCode;

/**
 * Turns a human name into a URL-safe slug.
 *
 * <p>Slugs are how projects and organizations are addressed in URLs and how uniqueness is enforced,
 * so the transformation has to be deterministic and forgiving: "Acme Ltd. — HR (2026)" becomes
 * {@code acme-ltd-hr-2026} rather than an error.
 */
public final class Slugifier {

	private static final Pattern NON_SLUG_CHARACTERS = Pattern.compile("[^a-z0-9]+");

	private static final int MAX_LENGTH = 120;

	private Slugifier() {
	}

	/**
	 * @param value human-readable name
	 * @return a lowercase, dash-separated slug of at most 120 characters
	 * @throws BadRequestException if nothing usable is left, e.g. for a name made only of symbols
	 */
	public static String slugify(String value) {
		if (value == null) {
			throw new BadRequestException(ErrorCode.INVALID_PARAMETER, "A name is required to build a slug");
		}

		String slug = NON_SLUG_CHARACTERS.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("-");
		slug = trimDashes(slug);

		if (slug.isEmpty()) {
			throw new BadRequestException(ErrorCode.INVALID_PARAMETER,
					"'%s' does not contain any character a slug can be built from".formatted(value));
		}
		return slug.length() <= MAX_LENGTH ? slug : trimDashes(slug.substring(0, MAX_LENGTH));
	}

	/** True when the value already is a valid slug, i.e. slugifying it changes nothing. */
	public static boolean isSlug(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		try {
			return slugify(value).equals(value);
		}
		catch (BadRequestException ex) {
			return false;
		}
	}

	private static String trimDashes(String value) {
		int start = 0;
		int end = value.length();
		while (start < end && value.charAt(start) == '-') {
			start++;
		}
		while (end > start && value.charAt(end - 1) == '-') {
			end--;
		}
		return value.substring(start, end);
	}

}
