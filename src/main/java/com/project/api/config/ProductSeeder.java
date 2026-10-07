package com.project.api.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.project.api.model.Product;
import com.project.api.repository.ProductRepository;

@Component
public class ProductSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductSeeder.class);
    private static final int PRODUCT_COUNT = 100;
    private static final List<String> CATEGORIES = List.of("Electronics", "Books", "Home", "Sports", "Toys");
    private static final List<String> ADJECTIVES = List.of("Classic", "Compact", "Deluxe", "Eco", "Pro", "Smart");
    private static final List<String> NOUNS = List.of("Lamp", "Speaker", "Notebook", "Bottle", "Backpack", "Puzzle",
            "Headphones", "Chair", "Ball", "Kettle");

    private final ProductRepository repository;

    public ProductSeeder(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        Random random = new Random(42);
        List<Product> products = new ArrayList<>(PRODUCT_COUNT);
        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            String name = "%s %s %03d".formatted(pick(ADJECTIVES, random), pick(NOUNS, random), i);
            BigDecimal price = BigDecimal.valueOf(5 + random.nextDouble() * 495).setScale(2, RoundingMode.HALF_UP);
            int stock = random.nextInt(5) == 0 ? 0 : random.nextInt(200);
            BigDecimal rating = BigDecimal.valueOf(1 + random.nextDouble() * 4).setScale(1, RoundingMode.HALF_UP);
            products.add(new Product(name, pick(CATEGORIES, random), price, stock, rating));
        }
        repository.saveAll(products);
        log.info("Seeded {} products", PRODUCT_COUNT);
    }

    private static String pick(List<String> values, Random random) {
        return values.get(random.nextInt(values.size()));
    }
}
