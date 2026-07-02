package com.learning.learning.service;

import com.learning.learning.entity.CharityApplication;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server-side content validation for public charity applications.
 *
 * Browser-side HTML validation (required, type=email, maxlength) is trivially
 * bypassed by bots that POST directly, and does nothing about low-effort human
 * junk ("test", "asdf", one-word descriptions). This validator enforces
 * meaningful content on the server and throws {@link IllegalArgumentException}
 * with a user-facing message listing every problem found.
 */
@Component
public class CharityApplicationValidator {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern EIN = Pattern.compile("^\\d{2}-?\\d{7}$");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern CONTAINS_URL =
            Pattern.compile("https?://|www\\.", Pattern.CASE_INSENSITIVE);

    /** Whole-value filler tokens that are never a real name or description. */
    private static final Set<String> JUNK = Set.of(
            "test", "testing", "tester", "asdf", "asdfasdf", "asdfghjkl",
            "qwerty", "abc", "abcabc", "abcd", "xxx", "xxxx", "none",
            "n/a", "na", "nil", "null", "nothing", "blah", "blahblah");

    private static final int MIN_DESCRIPTION_CHARS = 40;
    private static final int MIN_DESCRIPTION_WORDS = 6;

    public void validate(CharityApplication app) {
        List<String> errors = new ArrayList<>();

        String name = trimToEmpty(app.getCharityName());
        if (name.length() < 3 || !HAS_LETTER.matcher(name).find() || isLowEffort(name)) {
            errors.add("Please enter a valid organization name.");
        } else if (CONTAINS_URL.matcher(name).find()) {
            errors.add("Organization name cannot contain a web address.");
        }

        String contact = trimToEmpty(app.getContactName());
        if (contact.length() < 2 || !HAS_LETTER.matcher(contact).find() || isLowEffort(contact)) {
            errors.add("Please enter the primary contact's full name.");
        }

        String email = trimToEmpty(app.getContactEmail());
        if (!EMAIL.matcher(email).matches()) {
            errors.add("Please enter a valid email address.");
        }

        String description = trimToEmpty(app.getDescription());
        if (description.length() < MIN_DESCRIPTION_CHARS
                || wordCount(description) < MIN_DESCRIPTION_WORDS
                || isLowEffort(description)) {
            errors.add("Please describe your organization in a sentence or two (at least "
                    + MIN_DESCRIPTION_CHARS + " characters).");
        } else if (description.equalsIgnoreCase(name)) {
            errors.add("The description should tell us about your organization, not just repeat its name.");
        }

        String ein = trimToEmpty(app.getEinTaxId());
        if (!ein.isEmpty() && !EIN.matcher(ein).matches()) {
            errors.add("EIN / Tax ID must be 9 digits (format XX-XXXXXXX).");
        }

        String website = trimToEmpty(app.getWebsite());
        if (!website.isEmpty() && !website.matches("(?i)^https?://.+\\..+")) {
            errors.add("Website must be a valid URL beginning with http:// or https://.");
        }

        Integer referrals = app.getEstimatedReferralsPerMonth();
        if (referrals != null && (referrals < 1 || referrals > 500)) {
            errors.add("Estimated referrals per month must be between 1 and 500.");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static int wordCount(String s) {
        return s.isBlank() ? 0 : s.trim().split("\\s+").length;
    }

    /**
     * Detects obvious filler: known junk tokens, a single character repeated
     * ("aaaa"), or a single 6+ letter word with no vowel (keyboard mash).
     */
    private static boolean isLowEffort(String value) {
        String collapsed = value.toLowerCase().replaceAll("\\s+", "");
        if (collapsed.isEmpty()) {
            return true;
        }
        if (collapsed.chars().distinct().count() <= 1) {
            return true; // e.g. "aaaa", "....."
        }
        if (JUNK.contains(collapsed)) {
            return true;
        }
        boolean singleWord = !value.trim().contains(" ");
        if (singleWord && collapsed.length() >= 6 && !collapsed.matches(".*[aeiouy].*")) {
            return true;
        }
        return false;
    }
}
