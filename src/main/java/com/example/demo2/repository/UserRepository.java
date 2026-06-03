package com.example.demo2.repository;

import com.example.demo2.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;

/**
 * ─────────────────────────────────────────────────
 * REQUIREMENT 8: Transaction Integrity / ACID
 * ─────────────────────────────────────────────────
 * Repository للتواصل مع جدول users.
 * يتبع نفس أسلوب ProductRepository.java الموجود:
 *   - extends JpaRepository
 *   - @Lock و @Query حسب الحاجة
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * خصم المبلغ من المحفظة بشكل ذري (atomic) على مستوى قاعدة البيانات.
     *
     * @Modifying  : يُخبر Spring أن هذا الاستعلام يُعدّل البيانات (UPDATE/DELETE).
     * @Query      : استعلام JPQL مخصص.
     *
     * الشرط "AND u.walletBalance >= :amount":
     *   - يمنع الرصيد من الوصول إلى ما دون الصفر.
     *   - إذا كان الرصيد غير كافٍ → يُؤثر على 0 صفوف → نكتشف الفشل.
     *
     * @return عدد الصفوف المُعدَّلة: 1 = نجح، 0 = رصيد غير كافٍ
     */
    @Modifying
    @Query("UPDATE User u SET u.walletBalance = u.walletBalance - :amount " +
           "WHERE u.id = :userId AND u.walletBalance >= :amount")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
