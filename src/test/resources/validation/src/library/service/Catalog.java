package library.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import library.model.Item;

/**
 * The set of items owned by the library.
 */
public class Catalog {

    private final List<Item> items = new ArrayList<>();

    public void add(Item item) {
        items.add(item);
    }

    public void add(Item first, Item second) {
        add(first);
        add(second);
    }

    public Optional<Item> findById(String id) {
        for (Item item : items) {
            if (item.getId().equals(id)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public List<Item> available() {
        List<Item> result = new ArrayList<>();
        items.forEach(item -> {
            if (item.isAvailable()) {
                result.add(item);
            }
        });
        return result;
    }

    public int size() {
        return items.size();
    }
}
