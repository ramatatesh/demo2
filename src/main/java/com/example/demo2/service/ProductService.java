package com.example.demo2.service;

import com.example.demo2.model.Product;
import com.example.demo2.repository.ProductRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class ProductService {
    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String PRODUCT_LIST_KEY = "products:all";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    // ─── [القسم الأول: الكاش] ──────────────────────────────────────────────────
    public List<Product> getAllProducts(boolean useCache) {
        if (!useCache) {
            System.out.println("بدون كاش: جاري جلب كل المنتجات من قاعدة البيانات");
            return productRepository.findAll();
        } else {
            System.out.println("مع كاش: محاولة الحصول على كل المنتجات من Redis");
            List<Product> products = (List<Product>) redisTemplate.opsForValue().get(PRODUCT_LIST_KEY);
            if (products != null) {
                System.out.println("مع كاش: تم جلب كل المنتجات من Redis ✅");
                return products;
            } else {
                System.out.println("مع كاش: لم يتم العثور على المنتجات في Redis. جاري جلبهم من قاعدة البيانات...");
                List<Product> dbProducts = productRepository.findAll();
                redisTemplate.opsForValue().set(PRODUCT_LIST_KEY, dbProducts);
                return dbProducts;
            }
        }
    }

    public Optional<Product> getProductById(Long id, boolean useCache) {
        String productKey = PRODUCT_KEY_PREFIX + id;
        if (!useCache) {
            return productRepository.findById(id);
        } else {
            Product cachedProduct = (Product) redisTemplate.opsForValue().get(productKey);
            if (cachedProduct != null) return Optional.of(cachedProduct);
            Optional<Product> dbProduct = productRepository.findById(id);
            dbProduct.ifPresent(product -> redisTemplate.opsForValue().set(productKey, product));
            return dbProduct;
        }
    }

    public Product addProduct(Product product) {
        Product saved = productRepository.save(product);
        redisTemplate.opsForValue().set(PRODUCT_KEY_PREFIX + saved.getId(), saved);
        redisTemplate.delete(PRODUCT_LIST_KEY);
        return saved;
    }

    public Product updateProduct(Long id, Product newData) {
        Product updated = productRepository.findById(id).map(prod -> {
            prod.setName(newData.getName());
            prod.setStockQuantity(newData.getStockQuantity());
            return productRepository.save(prod);
        }).orElseThrow(() -> new RuntimeException("Product not found"));
        redisTemplate.opsForValue().set(PRODUCT_KEY_PREFIX + updated.getId(), updated);
        redisTemplate.delete(PRODUCT_LIST_KEY);
        return updated;
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
        redisTemplate.delete(PRODUCT_KEY_PREFIX + id);
        redisTemplate.delete(PRODUCT_LIST_KEY);
    }


    // ─── [القسم الثاني: الشراء والتحكم بالتزامن (الحالتين معاً)] ────────────────────────

    /**
     * دالة الشراء المرنة: تدعم الشراء العادي (قبل الحل) والشراء الآمن بالـ Lock (بعد الحل)
     */
    public boolean buyProduct(Long productId, int quantity, boolean useLock) {

        // [الحالة الأولى: بدون قفل - قبل الحل] ❌
        if (!useLock) {
            System.out.println("⚠️ [بدون LOCK]: خيط معالجة يدخل مباشرة بدون حماية لتعديل المنتج: " + productId);
            return executePurchaseLogic(productId, quantity);
        }

        // [الحالة الثانية: باستخدام الـ Distributed Lock - بعد الحل] ✅
        String lockKey = "productLock:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            System.out.println("🔒 [مع LOCK]: محاولة حجز القفل الموزع للمنتج: " + productId);
            acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);

            if (!acquired) {
                System.out.println("❌ [مع LOCK]: فشل الحصول على القفل بسبب ضغط الطلبات التنافسية.");
                return false;
            }

            // تنفيذ الشراء داخل الحماية الموزعة
            return executePurchaseLogic(productId, quantity);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("❗ توقف تنفيذ الخيط.");
            return false;
        } finally {
            if (acquired) {
                lock.unlock(); // فتح القفل دائماً لتمرير الطلب التالي
                System.out.println("🔓 [مع LOCK]: تم تحرير القفل بنجاح.");
            }
        }
    }

    /**
     * دالة مساعدة داخلية لتنفيذ منطق الخصم الفعلي من المستودع وتحديث الكاش
     */
    private boolean executePurchaseLogic(Long productId, int quantity) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            System.out.println("❗ المنتج غير موجود!");
            return false;
        }

        Product product = productOpt.get();
        if (product.getStockQuantity() < quantity) {
            System.out.println("🛑 كمية المستودع الحالية (" + product.getStockQuantity() + ") أقل من المطلوب (" + quantity + ")");
            return false;
        }

        // خصم الكمية وحفظها
        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);

        // تزامن صحة البيانات مع الكاش
        redisTemplate.opsForValue().set(PRODUCT_KEY_PREFIX + product.getId(), product);
        redisTemplate.delete(PRODUCT_LIST_KEY);

        System.out.println("✨ نجاح التحديث في قاعدة البيانات. الكمية المتبقية الحالية: " + product.getStockQuantity());
        return true;
    }

    /**
     * decreaseStockInTransaction:
     * ────────────────────────────
     * هذه هي دالة "decreaseStock()" المُشار إليها في متطلب 8.
     * صُمِّمت خصيصاً لتُستدعى من داخل transaction خارجية في TransactionService.
     *
     * الفرق بينها وبين buyProduct():
     *   buyProduct()               → مع/بدون Redisson Lock (للمتطلب 7)
     *   decreaseStockInTransaction() → داخل DB Transaction فقط (للمتطلب 8)
     *
     * @Transactional(propagation = Propagation.REQUIRED):
     *   - إذا استُدعيت من داخل TransactionService.placeOrderWithTransaction()
     *     (التي هي @Transactional) → تنضم للـ transaction الخارجية.
     *   - إذا استُدعيت من خارج أي transaction → تُنشئ transaction خديمة خاصة بها.
     *
     * findByIdForUpdate(): موجودة بالفعل في ProductRepository مع PESSIMISTIC_WRITE.
     * هذا يقفل الصف أثناء الـ transaction لمنع التعارض مع threads أخرى.
     *
     * @param productId رقم المنتج
     * @param quantity الكمية المراد خصمها
     * @throws RuntimeException إذا كان المخزون غير كافٍ → يُشغّل ROLLBACK في الـ transaction الخارجية
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void decreaseStockInTransaction(Long productId, int quantity) {
        // findByIdForUpdate يستخدم SELECT ... FOR UPDATE لقفل الصف
        // هذا موجود بالفعل في ProductRepository
        var productOpt = productRepository.findByIdForUpdate(productId);

        if (productOpt.isEmpty()) {
            throw new RuntimeException("Product not found: id=" + productId);
        }

        var product = productOpt.get();

        if (product.getStockQuantity() < quantity) {
            throw new RuntimeException(
                    "Insufficient stock for productId=" + productId
                            + ". Available=" + product.getStockQuantity()
                            + ", Requested=" + quantity
            );
        }

        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);

        System.out.println("   📦 [ProductService] تم تخفيض المخزون: productId="
                + productId + ", remaining=" + product.getStockQuantity());
    }

}