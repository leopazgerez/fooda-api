package com.fooda.api.core.domain.sales;

import com.fooda.api.shared.domain.ValidationException;

import java.time.DateTimeException;
import java.time.MonthDay;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Customer(Long id, String firstName, String lastName, String phone, String address, String birthday,
                       String notes, Long version) {

    private static final Pattern BIRTHDAY = Pattern.compile("(?:\\d{4}-)?(\\d{2})-(\\d{2})");

    public Customer {
        firstName = firstName == null ? "" : firstName.strip();
        if (firstName.isEmpty()) {
            throw new ValidationException("firstName", "Completá nombre");
        }
        lastName = lastName == null ? "" : lastName.strip();
        if (lastName.isEmpty()) {
            throw new ValidationException("lastName", "Completá apellido");
        }
        phone = strip(phone);
        address = strip(address);
        notes = strip(notes);
        birthday = normalize(birthday);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = BIRTHDAY.matcher(raw.strip());
        if (m.matches()) {
            try {
                // MonthDay accepts Feb 29
                return MonthDay.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))).toString().substring(2);
            } catch (DateTimeException ignored) {
                // falls through
            }
        }
        throw new ValidationException("birthday", "Cumpleaños inválido (MM-DD)");
    }
}
