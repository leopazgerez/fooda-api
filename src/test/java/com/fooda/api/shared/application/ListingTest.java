package com.fooda.api.shared.application;

import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingTest {

    record Item(long id, String name) {}

    @Test
    void sortsDescWithIdTieBreakAndSlices() {
        var items = List.of(new Item(1, "b"), new Item(2, "a"), new Item(3, "b"));
        Page<Item> p = Listing.page(items, new ListQuery(0, 2, "name", "desc"),
                Map.of("name", Comparator.comparing(Item::name)), "name", "asc", Comparator.comparingLong(Item::id));
        assertThat(p.items()).extracting(Item::id).containsExactly(1L, 3L);
        assertThat(p.total()).isEqualTo(3);
    }

    @Test
    void unknownSortIs400() {
        assertThatThrownBy(() -> Listing.page(List.<Item>of(), new ListQuery(0, 20, "x", null), Map.of(), "name", "asc", Comparator.comparingLong(Item::id)))
                .isInstanceOf(ValidationException.class).extracting("field").isEqualTo("sort");
    }

    @Test
    void invalidQueryIsRejected() {
        assertThatThrownBy(() -> new ListQuery(-1, 20, null, null)).extracting("field").isEqualTo("page");
        assertThatThrownBy(() -> new ListQuery(0, 201, null, null)).extracting("field").isEqualTo("size");
        assertThatThrownBy(() -> new ListQuery(0, 20, null, "up")).extracting("field").isEqualTo("dir");
    }

    @Test
    void pageBeyondEndIsEmpty() {
        var p = Listing.page(List.of(new Item(1, "a")), new ListQuery(5, 10, null, null),
                Map.of("name", Comparator.comparing(Item::name)), "name", "asc", Comparator.comparingLong(Item::id));
        assertThat(p.items()).isEmpty();
        assertThat(p.total()).isEqualTo(1);
    }
}
