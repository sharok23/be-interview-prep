package com.project.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.project.api.config.CacheConfig;
import com.project.api.contract.OrderItemRequest;
import com.project.api.contract.OrderRequest;
import com.project.api.contract.OrderResponse;
import com.project.api.exception.BadRequestException;
import com.project.api.exception.ConflictException;
import com.project.api.exception.NotFoundException;
import com.project.api.model.CurrentUser;
import com.project.api.model.OrderItem;
import com.project.api.model.Product;
import com.project.api.model.PurchaseOrder;
import com.project.api.repository.OrderRepository;
import com.project.api.repository.ProductRepository;

@Service
public class OrderService {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final OrderRepository orders;
    private final ProductRepository products;
    private final UserService userService;
    private final TransactionTemplate transaction;
    private final Cache productCache;

    public OrderService(OrderRepository orders, ProductRepository products, UserService userService,
                        TransactionTemplate transaction, CacheManager cacheManager) {
        this.orders = orders;
        this.products = products;
        this.userService = userService;
        this.transaction = transaction;
        this.productCache = cacheManager.getCache(CacheConfig.PRODUCTS);
    }

    public record PlaceResult(OrderResponse order, boolean created) {
    }

    public PlaceResult place(OrderRequest request, String idempotencyKey, CurrentUser user) {
        String key = validateKey(idempotencyKey);
        Map<Long, Integer> quantities = mergeByProduct(request);
        String requestHash = hash(quantities);

        Optional<PlaceResult> replay = findReplay(user, key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            OrderResponse created = transaction.execute(status -> reserveAndSave(user, key, requestHash, quantities));
            return new PlaceResult(created, true);
        } catch (DataIntegrityViolationException concurrentRetry) {
            return findReplay(user, key, requestHash).orElseThrow(() -> concurrentRetry);
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id, CurrentUser user) {
        return OrderResponse.from(find(id, user));
    }

    @Transactional
    public OrderResponse cancel(Long id, CurrentUser user) {
        PurchaseOrder order = find(id, user);
        if (orders.markCancelled(order.getId()) == 1) {
            for (OrderItem item : order.getItems()) {
                products.releaseStock(item.getProductId(), item.getQuantity());
                productCache.evict(item.getProductId());
            }
        }
        return OrderResponse.from(find(id, user));
    }

    private OrderResponse reserveAndSave(CurrentUser user, String key, String requestHash,
                                         Map<Long, Integer> quantities) {
        PurchaseOrder order = new PurchaseOrder(userService.require(user.username()), key, requestHash);
        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            Long productId = line.getKey();
            int quantity = line.getValue();
            if (products.reserveStock(productId, quantity) == 0) {
                Product product = products.findById(productId)
                        .orElseThrow(() -> new NotFoundException("Product", productId));
                throw new ConflictException("Insufficient stock for product %d (%s): requested %d, available %d"
                        .formatted(productId, product.getName(), quantity, product.getStock()));
            }
            productCache.evict(productId);
        }
        for (Product product : products.findAllById(quantities.keySet())) {
            order.addItem(product, quantities.get(product.getId()));
        }
        return OrderResponse.from(orders.saveAndFlush(order));
    }

    private Optional<PlaceResult> findReplay(CurrentUser user, String key, String requestHash) {
        return transaction.execute(status -> orders.findByOwnerUsernameAndIdempotencyKey(user.username(), key)
                .map(existing -> {
                    if (!existing.getRequestHash().equals(requestHash)) {
                        throw new ConflictException(
                                "Idempotency-Key '%s' was already used for a different order".formatted(key));
                    }
                    return new PlaceResult(OrderResponse.from(existing), false);
                }));
    }

    private PurchaseOrder find(Long id, CurrentUser user) {
        Optional<PurchaseOrder> order = user.admin()
                ? orders.findById(id)
                : orders.findByIdAndOwnerUsername(id, user.username());
        return order.orElseThrow(() -> new NotFoundException("Order", id));
    }

    private static String validateKey(String idempotencyKey) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > PurchaseOrder.IDEMPOTENCY_KEY_MAX) {
            throw new BadRequestException(IDEMPOTENCY_HEADER,
                    "Idempotency-Key header must be 1 to %d characters".formatted(PurchaseOrder.IDEMPOTENCY_KEY_MAX));
        }
        return key;
    }

    private static Map<Long, Integer> mergeByProduct(OrderRequest request) {
        return request.items().stream().collect(Collectors.toMap(
                OrderItemRequest::productId, OrderItemRequest::quantity, Integer::sum, TreeMap::new));
    }

    private static String hash(Map<Long, Integer> quantities) {
        String canonical = quantities.entrySet().stream()
                .map(e -> e.getKey() + "x" + e.getValue())
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
