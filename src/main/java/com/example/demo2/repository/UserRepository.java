package com.example.demo2.repository;

import com.example.demo2.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("UPDATE User u SET u.walletBalance = u.walletBalance - :amount " +
           "WHERE u.id = :userId AND u.walletBalance >= :amount")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
